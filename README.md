# RI4SU — Reflective Intervention for Screen Use

Android 智能屏幕使用反思干预系统。通过前台 Service 周期性采集多维上下文数据（位置、活动、屏幕使用、日历、Wi-Fi、蓝牙等），结合 LLM 增量评分引擎与多维心情评估，以悬浮拟人表情图标和 AI 生成的反思提醒实时引导用户建立健康的手机使用习惯；可选地通过通义千问图像 API 生成隐喻壁纸。

---

## 核心功能

| 功能 | 说明 |
|---|---|
| 多维上下文采集 | 位置、活动识别、屏幕使用、日历、Wi-Fi、蓝牙、天气，每 2/10 分钟自动采集 |
| LLM 增量评分 | DeepSeek 驱动，根据前后快照差异输出 delta 分数，范围 [0, 100]，每日重置 |
| 无意识使用追踪 (UUT) | 基于解锁频率、时段权重、App 切换模式等学术指标量化"无意识刷手机"程度 |
| 多维心情评分 | 综合日使用时长、娱乐占比、会话强度 (UUT)、个人目标达成度四维度计算 stress 值 |
| 悬浮拟人表情 | `MoodFaceView` 根据 stress 值连续插值渲染表情，支持多种视觉风格 |
| AI 反思提醒 | 点击悬浮图标触发 LLM 生成个性化反思文案，通过气泡展示 |
| AI 隐喻壁纸 | 聚合数据 → LLM 提取关键词 → 通义千问生成壁纸，定时自动更换 |
| 个人目标设定 | 用户可设定每日屏幕时长上限、娱乐占比上限、免打扰时段等 |

---

## 数据收集类别

### 1. 时间信息（Service 直接写入）

| 字段 | 说明 |
|---|---|
| `timestamp` | Unix 毫秒时间戳 |
| `date_time` | 可读日期时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `day_of_week` | 星期几（本地语言） |
| `trigger_reason` | 触发原因：`periodic`（定时）/ `manual`（手动触发） |

---

### 2. 位置信息 — `location`

**Collector**：`LocationDataCollector`

| 字段 | 说明 |
|---|---|
| `latitude` / `longitude` | GPS 坐标 |
| `accuracy` | 定位精度（米） |
| `altitude` | 海拔高度（米） |
| `speed` | 速度（m/s） |
| `bearing` | 方位角（度） |
| `timestamp` | 定位时间戳 |
| `provider` | 定位提供者（`gps` 等） |
| `readable_address` | 坐标的可读字符串（`lat, lng` 格式） |

Service 根据活动类型 + 定位精度 + 时段推断 `location_context`（`通勤中` / `户外` / `室内` / `家` / `公司/学校` / `未知`）。

---

### 3. 活动识别 — `user_activity`

**Collector**：`ActivityRecognitionCollector` + `ActivityRecognizer`

基于 Wang et al., *StudentLife*, UbiComp 2014 描述的 Jigsaw 决策树分类器，仅使用加速度计数据，采样率 20 Hz，分类窗口约 2 秒。

#### 可识别活动类型

| 活动 | 说明 |
|---|---|
| `stationary` | 静止 |
| `walking` | 步行 |
| `running` | 跑步 |
| `driving` | 驾车 |
| `cycling` | 骑车 |

#### 输出字段

| 字段 | 说明 |
|---|---|
| `activity` | 当前活动类型（见上表） |
| `confidence` | 分类置信度（0~1） |
| `timestamp` | 最近一次分类时间戳 |
| `classifier` | 固定值 `decision_tree` |
| `sensor_status.accelerometer_available` | 加速度计是否可用 |

#### 决策树结构

```
              [variance < 0.3]
              /               \
        stationary      [peakFreq < 0.8]
                        /              \
             [variance < 2.5]    [variance < 8.0]
             /            \       /            \
          driving       cycling  walking      running
```

使用特征：均值、方差、能量、过零率、峰值频率（峰值计数法估算）、轴间 Pearson 相关系数。

---

### 4. Wi-Fi 信息 — `wifi_info`

**Collector**：`WifiDataCollector`

#### `connected_ap`（当前连接的 AP）

| 字段 | 说明 |
|---|---|
| `ssid` | 网络名称 |
| `bssid` | AP 的 MAC 地址 |
| `rssi` | 信号强度（dBm） |
| `link_speed_mbps` | 连接速率（Mbps） |
| `frequency_mhz` | 频率（MHz，2.4G ≈ 2437，5G ≈ 5180） |
| `ip_address` | 设备 IP 地址 |
| `wifi_standard` | Wi-Fi 标准（Android 10+ 可用，如 `Wi-Fi 5 (802.11ac)`） |

---

### 5. 蓝牙设备 — `bluetooth_devices`

**Collector**：`BluetoothDataCollector`

#### `paired_devices`（已配对设备）

| 字段 | 说明 |
|---|---|
| `name` | 设备名称 |
| `mac_address` | MAC 地址 |
| `device_class` | 设备大类 |
| `bond_state` | 配对状态：`bonded` / `bonding` / `none` |

#### `nearby_devices`（周边发现设备，最多 20 条，去重）

额外包含 `rssi`（信号强度 dBm）。

设备大类：`audio_video` / `computer` / `phone` / `health` / `wearable` / `peripheral` / `imaging` / `networking` / `other`

---

### 6. 日历事件 — `calendar`

**Collector**：`CalendarDataCollector`

默认查询范围：过去 7 天 + 未来 30 天，最多 50 条事件。

#### `events`（事件列表，按开始时间升序）

| 字段 | 说明 |
|---|---|
| `event_id` | 事件 ID |
| `title` | 标题 |
| `description` | 描述（最多 500 字符） |
| `location` | 地点 |
| `begin_datetime` / `end_datetime` | 可读时间 |
| `duration_minutes` | 时长（分钟） |
| `is_past` | 是否已过去 |
| `minutes_until` | 距开始还有多少分钟（负值=已过去） |
| `all_day` | 是否全天事件 |
| `status` | `confirmed` / `tentative` / `canceled` |
| `is_recurring` | 是否为循环事件 |

---

### 7. 屏幕使用统计 — `screen_usage`

**Collector**：`ScreenUsageCollector`（基于 `UsageStatsManager`，需授权"使用情况统计"）

| 字段 | 说明 |
|---|---|
| `today_screen_time_ms` / `_minutes` / `_readable` | 今日累计屏幕使用时长 |
| `unlock_count_last_hour` | 过去一小时解锁次数 |
| `current_session_ms` / `_minutes` / `_readable` | 本次解锁后的持续使用时长 |
| `foreground_app_package` | 当前前台 App 包名 |
| `foreground_app_category` / `_en` | 当前 App 分类（中英文） |
| `foreground_app_today_ms` / `_readable` | 当前 App 今日总使用时长 |
| `foreground_app_current_open_ms` / `_readable` | 当前 App 本次打开持续时长 |
| `top_apps_today` | 今日使用时长 Top N 应用（含包名、分类、时长） |

App 分类由 `AppCategoryClassifier` 完成，基于包名规则映射到：社交、娱乐、生产力、工具、教育、健康、购物、财务等类别。

---

### 8. 天气信息

**Collector**：`WeatherDataCollector`

通过外部天气 API 获取当前天气数据，作为上下文信息辅助 LLM 评估。

---

## 反思干预引擎

### LLM 增量评分（`LLMScoringEngine`）

取代基于手工公式的评分，改由 DeepSeek LLM 根据完整采集快照进行增量评估：

- 每次评估时，将上一次快照 + 上一次分数 + 本次最新快照发送给 LLM
- LLM 返回 delta 值（增量），约束规则：
  - 生产力 App 使用 → delta = 0
  - 娱乐 App 持续使用 → delta = +1
  - 深夜 / 无意识使用迹象 → delta 最高 +5
  - 屏幕关闭 / 主动休息 → delta 可为负数，最低 -5
- 分数范围 [0, 100]，每日重置为 0
- 状态持久化到 SharedPreferences，支持进程重启恢复

### 无意识使用追踪（`UnconsciousUsageTracker`，UUT）

UUT 值范围 0-100，量化"无意识刷手机"程度。基于以下学术指标：

| 指标 | 学术依据 |
|---|---|
| 时段权重 | Duke & Montag 2017：深夜/睡前使用与焦虑和睡眠障碍强相关 |
| 解锁频率 | Harari et al. 2016, Montag et al. 2021：每小时解锁次数是核心指标 (r=0.52) |
| 每日疲劳效应 | Hartmann et al. 2021：日使用超 3 小时后认知控制力下降 |
| 方向性切换惩罚 | Baumgartner et al. 2018：生产力→娱乐切换更具无意识特征 |
| 冲动性短会话 | Billieux et al. 2015：短暂重复解锁（<90 秒）是成瘾性使用的强预测因子 |

### 多维心情评分（`MoodScoreEngine`）

综合四个维度计算最终 stress 值 [0, 1]：

| 维度 | 权重 | 说明 |
|---|---|---|
| D1 — dailyUsageScore | 0.20 | 全天屏幕时长评估 |
| D2 — entertainmentRatioScore | 0.20 | 娱乐占比评估 |
| D3 — sessionIntensityScore | 0.35 | 当前会话强度 (UUT) |
| D4 — goalComplianceScore | 0.25 | 个人目标达成度 |

### 悬浮拟人表情（`MoodFaceView` + `FloatingOverlayService`）

- 根据 stress 值连续插值渲染面部表情（无离散阶段）
- 支持多种视觉风格（经典、暖阳、清凉、森林、星空、像素）
- 点击触发 LLM 生成个性化反思文案，通过 `SpeechBubbleDrawable` 气泡展示
- 屏幕亮起时自动刷新状态

---

## 技术架构

### 核心组件

| 类 | 职责 |
|---|---|
| `DataCollectionService` | 前台 Service，轻量采集 2 分钟 / 全量采集 10 分钟，协调所有 Collector |
| `DataCollectorManager` | Collector 注册、生命周期管理 |
| `LLMScoringEngine` | LLM 驱动的增量评分引擎 |
| `UnconsciousUsageTracker` | 无意识使用时间 (UUT) 追踪 |
| `MoodScoreEngine` | 多维心情评分引擎 |
| `FloatingOverlayService` | 悬浮窗服务，承载 MoodFaceView + 气泡提醒 |
| `MoodFaceView` | stress 驱动的连续动画表情视图 |
| `SpeechBubbleDrawable` | 气泡文字绘制 |
| `DeepSeekApiClient` | DeepSeek 聊天接口调用 |
| `QwenImageApiClient` | 通义千问图像生成接口 |
| `WallpaperGenerationManager` | 聚合数据 → LLM 提取关键词 → 生成壁纸 |
| `DataAggregator` | 多次采集文件聚合为时间窗口摘要 |
| `AppCategoryClassifier` | 基于包名的 App 分类器 |
| `AppForegroundTracker` | 前台 App 切换追踪 |
| `WifiFingerprint` | Wi-Fi 指纹辅助位置推断 |
| `DataCleanupManager` | 定期清理过期数据文件 |
| `DataEncryptor` | AES 加密存储 |
| `CollectionConfig` | 所有运行时参数的 SharedPreferences 统一管理 |
| `RetryHelper` | API 调用重试工具 |
| `ErrorCollector` | 错误收集与统计 |

### Collectors

| Collector | 数据 |
|---|---|
| `LocationDataCollector` | GPS 位置 |
| `ActivityRecognitionCollector` + `ActivityRecognizer` | 基于加速度计的活动识别 |
| `ScreenUsageCollector` | 屏幕时长、解锁次数、前台 App |
| `CalendarDataCollector` | 系统日历事件 |
| `WifiDataCollector` | Wi-Fi 连接信息 |
| `BluetoothDataCollector` | 蓝牙设备 |
| `WeatherDataCollector` | 天气数据 |

### 数据流

```
传感器 / 系统 API
       ↓
各 DataCollector（Location / Activity / WiFi / Bluetooth / Calendar / ScreenUsage / Weather）
       ↓
DataCollectionService.collectCurrentContextData()
  ├─ mergeCollectorData()        — 字段映射 + 位置上下文推断
  ├─ saveContextData()           — 写入加密/压缩/明文 JSON 文件
  │
  ├─ LLMScoringEngine.assess()  — LLM 增量评分
  ├─ UnconsciousUsageTracker     — UUT 实时追踪
  ├─ MoodScoreEngine             — 多维 stress 计算
  │     └─ FloatingOverlayService → MoodFaceView 表情更新
  │
  └─ [可选] WallpaperGenerationManager.generateAndSetWallpaper()
        ├─ DataAggregator 聚合 + DeepSeek 提取关键词
        └─ QwenImageApiClient 生成并设置壁纸
```

---

## 界面

### 主控制面板（`MainActivity`）

- LLM 评分进度条（实时显示 0-100 分数，颜色随分数变化）
- AI 反思提醒文案展示
- 悬浮图标开关
- AI 壁纸开关
- 个人设置 / 系统设置入口

### 个人设置（`PersonalSettingsActivity`）

- 壁纸风格选择
- 壁纸生成时间设定
- 图标风格选择（经典、暖阳、清凉、森林、星空、像素）
- 个人目标设定（每日屏幕时长上限、娱乐占比上限等）

### 系统设置（`SystemSettingsActivity`）

- 数据收集参数配置
- 系统权限管理
- 壁纸历史查看
- 开发者测试工具

---

## 数据存储

- **目录**：应用外部私有目录（`getExternalFilesDir(null)`）下的 `data/` 子目录
- **采集文件**：`context_data_<timestamp>.json`（始终保存一份明文副本，主文件可加密/压缩）
- **分析结果**：`analysis/analysis_result_<timestamp>.json`
- **壁纸缓存**：`wallpapers/`

### 存储格式选项（`CollectionConfig`）

| 配置 Key | 默认值 | 说明 |
|---|---|---|
| `data_encryption_enabled` | `true` | AES 加密，保存为 `.enc` |
| `data_compression_enabled` | `true` | GZIP 压缩，保存为 `.json.gz` |
| `data_retention_days` | `7` | 采集文件保留天数 |
| `analysis_retention_days` | `3` | 分析结果保留天数 |
| `max_storage_mb` | `200` | 最大占用存储空间 |

---

## 采集配置参数

所有参数通过 `CollectionConfig`（SharedPreferences）管理，支持运行时修改。

| 配置 Key | 默认值 | 说明 |
|---|---|---|
| `collection_interval_ms` | 600000（10 分钟）| 全量采集间隔 |
| `location_enabled` | `true` | 位置采集开关 |
| `location_update_interval_ms` | 60000 | 位置更新间隔 |
| `location_min_distance_m` | 10 | 位置更新最小距离（米） |
| `activity_recognition_enabled` | `true` | 活动识别开关 |
| `screen_usage_enabled` | `true` | 屏幕使用统计开关 |
| `screen_usage_top_apps_count` | 10 | Top N 应用数量 |
| `calendar_enabled` | `true` | 日历采集开关 |
| `calendar_past_days` | 7 | 日历查询过去天数 |
| `calendar_future_days` | 30 | 日历查询未来天数 |
| `calendar_max_events` | 50 | 最多返回事件数 |
| `wifi_enabled` | `true` | Wi-Fi 采集开关 |
| `bluetooth_enabled` | `true` | 蓝牙采集开关 |
| `aggregation_window_hours` | 6 | LLM 数据聚合时间窗口 |

---

## 权限要求

```xml
<!-- 网络 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 存储 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- 通知（Android 13+） -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 位置（Wi-Fi 扫描也需要） -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Wi-Fi -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- 蓝牙 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- 日历 -->
<uses-permission android:name="android.permission.READ_CALENDAR" />

<!-- 传感器与活动识别 -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

<!-- 屏幕使用统计（需用户在设置中手动授权） -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

<!-- 悬浮窗与壁纸 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.SET_WALLPAPER" />

<!-- 开机启动 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

> **注意**：`PACKAGE_USAGE_STATS` 为特殊权限，需用户在「设置 → 应用 → 特殊应用访问权限 → 使用情况访问权限」中手动开启。

---

## API 配置

API 密钥通过 `local.properties` 注入 BuildConfig，不硬编码在源码中。

在项目根目录的 `local.properties` 中添加：

```properties
DEEPSEEK_API_KEY=your_deepseek_key_here
QWEN_IMAGE_API_KEY=your_qwen_key_here
```

| API | 用途 | 配置类 |
|---|---|---|
| DeepSeek Chat | LLM 增量评分、反思文案生成、壁纸关键词提取 | `ApiConfig.DEEPSEEK_API_URL` |
| 通义千问图像生成 | 壁纸生成（`wallpaper_generation_enabled=true` 时定时调用） | `ApiConfig.QWEN_IMAGE_API_URL` |

---

## 系统要求

- Android 6.0（API 23）及以上
- 支持 GPS 定位
- 加速度计传感器（活动识别必需）
- 蓝牙 4.0+（蓝牙采集可选）
- Target SDK：34，Min SDK：23

---

## 注意事项

1. **权限申请**：首次运行需用户逐项授权，`PACKAGE_USAGE_STATS` 需手动在系统设置中开启
2. **蓝牙扫描功耗**：主动扫描耗电较高，已改为被动监听策略
3. **Wi-Fi 扫描限速**：Android 10+ 系统对主动扫描频率有限制
4. **电池优化**：长期后台运行建议将应用加入电池优化白名单
5. **隐私合规**：所有数据本地存储，LLM 分析需联网，使用前需获得用户明确同意

---

## 参考文献

- Wang et al., *StudentLife: Assessing Mental Health, Academic Performance and Behavioral Trends of College Students using Smartphones*, UbiComp 2014
- Duke & Montag, *Smartphone addiction, daily interruptions and self-reported productivity*, Addictive Behaviors Reports 2017
- Harari et al., *Using Smartphones to Collect Behavioral Data in Psychological Science*, Perspectives on Psychological Science 2016
- Montag et al., *On the Relationship Between Smartphone Usage and Problematic Smartphone Use*, Technology in Society 2021
- Hartmann et al., *Smartphone Use and Cognitive Control*, Computers in Human Behavior 2021
- Baumgartner et al., *The Relationship Between Media Multitasking and Executive Function*, Journal of Communication 2018
- Billieux et al., *Can Disordered Mobile Phone Use Be Considered a Behavioral Addiction?*, Current Addiction Reports 2015
- Lu et al., *SoundSense: Scalable Sound Sensing for People-Centric Applications on Mobile Phones*, MobiSys 2009
- Huckins et al., Beiwe Research Platform, *JMIR mHealth* 2020
