# CATIA3 — Android 用户行为数据收集系统

Android 用户行为数据收集系统。以前台 Service 为核心，每 10 分钟自动采集一次结构化上下文数据，并输出 JSON 文件；可选地通过 DeepSeek API 进行 LLM 健康分析，通过通义千问图像 API 生成隐喻壁纸。

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

此外，Service 根据活动类型 + 定位精度 + 时段推断 `location_context`（`通勤中` / `户外` / `室内` / `家` / `公司/学校` / `未知`）。

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

采集策略：通过 `WifiManager.getConnectionInfo()` 读取当前连接的 AP 信息。

---

### 5. 蓝牙设备 — `bluetooth_devices`

**Collector**：`BluetoothDataCollector`

#### `paired_devices`（已配对设备）

| 字段 | 说明 |
|---|---|
| `name` | 设备名称 |
| `mac_address` | MAC 地址 |
| `device_class` | 设备大类（见下表） |
| `bond_state` | 配对状态：`bonded` / `bonding` / `none` |

#### `nearby_devices`（周边发现设备，最多 20 条，去重）

在已配对设备字段基础上额外包含：

| 字段 | 说明 |
|---|---|
| `rssi` | 信号强度（dBm） |

#### 设备大类（`device_class`）

`audio_video` / `computer` / `phone` / `health` / `wearable` / `peripheral` / `imaging` / `networking` / `other`

采集策略：注册 `ACTION_FOUND` 广播被动接收发现事件，`doStartCollection` 时触发一次扫描。适配 Android 12+（`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` 权限分离）。

---

### 6. 日历事件 — `calendar`

**Collector**：`CalendarDataCollector`

默认查询范围：过去 7 天 + 未来 30 天，最多 50 条事件（均可通过 `CollectionConfig` 调整）。

#### `calendars`（日历账户列表）

| 字段 | 说明 |
|---|---|
| `id` | 日历 ID |
| `display_name` | 显示名称 |
| `account_name` | 账户名 |
| `account_type` | 账户类型 |
| `visible` | 是否可见 |
| `is_primary` | 是否为主日历 |

#### `events`（事件列表，按开始时间升序）

| 字段 | 说明 |
|---|---|
| `event_id` | 事件 ID |
| `title` | 标题 |
| `description` | 描述（最多 500 字符） |
| `location` | 地点 |
| `begin_datetime` / `end_datetime` | 可读时间 |
| `begin_timestamp` / `end_timestamp` | Unix 毫秒时间戳 |
| `duration_minutes` | 时长（分钟） |
| `is_past` | 是否已过去 |
| `minutes_until` | 距开始还有多少分钟（负值=已过去） |
| `all_day` | 是否全天事件 |
| `calendar_name` | 所属日历名称 |
| `organizer` | 组织者 |
| `status` | `confirmed` / `tentative` / `canceled` |
| `availability` | `busy` / `free` / `tentative` |
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
| `foreground_app_open_since` | 当前 App 本次打开时间 |
| `top_apps_today` | 今日使用时长 Top N 应用（含包名、分类、时长） |

App 分类由 `AppCategoryClassifier` 完成，基于包名规则映射到：社交、娱乐、生产力、工具、教育、健康、购物、财务等类别。

---

## 已规划但尚未实现的数据类别

| 类别 | JSON 字段 | 说明 |
|---|---|---|
| 屏幕文本内容 | `screen_content` | 需实现 `AccessibilityService`，采集屏幕可见文字、应用包名、聊天识别等 |
| 当前应用独立字段 | `current_app` | `foreground_app_package` 目前存于 `screen_usage` 内部，尚未单独输出为顶层字段 |

---

## 输出 JSON 格式示例

```json
{
  "context_data": {
    "timestamp": 1703123456789,
    "date_time": "2024-12-21 14:30:45",
    "day_of_week": "Friday",
    "trigger_reason": "periodic",
    "location_context": "公司/学校",
    "location": {
      "latitude": 39.9042,
      "longitude": 116.4074,
      "accuracy": 10.0,
      "altitude": 45.2,
      "speed": 0.0,
      "bearing": 0.0,
      "provider": "gps",
      "readable_address": "39.904200, 116.407400"
    },
    "user_activity": {
      "activity": "stationary",
      "confidence": 0.95,
      "timestamp": 1703123456789,
      "classifier": "decision_tree",
      "sensor_status": { "accelerometer_available": true }
    },
    "wifi_info": {
      "connected_ap": {
        "ssid": "OfficeWiFi",
        "bssid": "00:11:22:33:44:55",
        "rssi": -52,
        "link_speed_mbps": 300,
        "frequency_mhz": 5180,
        "ip_address": "192.168.1.42",
        "wifi_standard": "Wi-Fi 5 (802.11ac)"
      }
    },
    "bluetooth_devices": {
      "paired_devices": [
        { "name": "AirPods Pro", "mac_address": "AA:BB:CC:DD:EE:FF",
          "device_class": "audio_video", "bond_state": "bonded" }
      ],
      "paired_device_count": 1,
      "nearby_devices": [],
      "nearby_device_count": 0,
      "is_discovering": false
    },
    "calendar": {
      "calendar_count": 2,
      "event_count": 3,
      "past_days": 7,
      "future_days": 30,
      "events": [
        {
          "event_id": 12345,
          "title": "团队会议",
          "begin_datetime": "2024-12-21 15:00:00",
          "end_datetime": "2024-12-21 16:00:00",
          "duration_minutes": 60,
          "is_past": false,
          "minutes_until": 30,
          "location": "会议室A",
          "status": "confirmed",
          "is_recurring": false
        }
      ]
    },
    "screen_usage": {
      "today_screen_time_ms": 7200000,
      "today_screen_time_minutes": 120,
      "today_screen_time_readable": "2h 0m",
      "unlock_count_last_hour": 5,
      "current_session_ms": 900000,
      "current_session_readable": "15m",
      "foreground_app_package": "com.tencent.mm",
      "foreground_app_category": "社交",
      "foreground_app_category_en": "social",
      "foreground_app_today_ms": 1800000,
      "foreground_app_today_readable": "30m",
      "top_apps_today": [
        { "package_name": "com.tencent.mm", "category": "社交",
          "usage_ms": 1800000, "usage_readable": "30m" }
      ]
    },
    "collectors_status": {
      "location": "available",
      "activity_recognition": "available",
      "screen_usage": "available",
      "calendar": "available",
      "wifi_info": "available",
      "bluetooth_devices": "available"
    }
  },
  "collection_time": 1703123456789
}
```

---

## 技术架构

### 核心组件

| 类 | 职责 |
|---|---|
| `DataCollectionService` | 前台 Service，每 10 分钟调度一次采集，协调所有 Collector |
| `DataCollectorManager` | Collector 注册、生命周期管理（启动/停止/数据获取） |
| `LocationDataCollector` | GPS 位置采集 |
| `ActivityRecognitionCollector` | 传感器监听，调用 `ActivityRecognizer` 分类 |
| `ActivityRecognizer` | 基于决策树的活动识别（StudentLife/Jigsaw 算法） |
| `WifiDataCollector` | Wi-Fi 连接信息与周边 AP 扫描 |
| `BluetoothDataCollector` | 已配对设备与周边发现设备 |
| `CalendarDataCollector` | 系统日历事件读取 |
| `ScreenUsageCollector` | 屏幕时长、解锁次数、前台 App（UsageStatsManager） |
| `DataAggregator` | 将多次采集文件聚合为时间窗口摘要，供 LLM 分析使用 |
| `DeepSeekApiClient` | 调用 DeepSeek 聊天接口进行健康分析 |
| `WallpaperGenerationManager` | 聚合数据 → LLM 提取关键词 → 通义千问生成壁纸 |
| `DataCleanupManager` | 定期清理过期数据文件（默认保留 7 天） |
| `DataEncryptor` | AES 加密存储 |
| `CollectionConfig` | 所有运行时参数的 SharedPreferences 统一管理 |

### 数据流

```
传感器 / 系统 API
       ↓
各 DataCollector（Location / Activity / WiFi / Bluetooth / Calendar / ScreenUsage）
       ↓
DataCollectionService.collectCurrentContextData()
  ├─ mergeCollectorData()        — 字段映射 + 位置上下文推断
  ├─ saveContextData()           — 写入加密/压缩/明文 JSON 文件
  │     └─ 明文副本 context_data_<ts>.json
  │
  └─ [可选] WallpaperGenerationManager.generateAndSetWallpaper()
        ├─ DataAggregator 聚合 + DeepSeek 提取关键词
        └─ QwenImageApiClient 生成并设置壁纸
```

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
| `collection_interval_ms` | 600000（10 分钟）| 周期采集间隔 |
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
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- 位置（Wi-Fi 扫描也需要） -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Wi-Fi -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- 蓝牙 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />    <!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> <!-- Android 12+ -->

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
| DeepSeek Chat | 图标提醒文案生成、壁纸关键词提取 | `ApiConfig.DEEPSEEK_API_URL` |
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
2. **屏幕内容采集**：`screen_content` 字段（无障碍服务）尚未实现，需用户手动启用 AccessibilityService
3. **蓝牙扫描功耗**：主动扫描耗电较高，已改为被动监听策略
4. **Wi-Fi 扫描限速**：Android 10+ 系统对主动扫描频率有限制，实际扫描结果依赖系统调度
5. **电池优化**：长期后台运行建议将应用加入电池优化白名单（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
6. **隐私合规**：所有数据本地存储，LLM 分析需联网，使用前需获得用户明确同意

---

## 参考文献

- Wang et al., *StudentLife: Assessing Mental Health, Academic Performance and Behavioral Trends of College Students using Smartphones*, UbiComp 2014
- Lu et al., *SoundSense: Scalable Sound Sensing for People-Centric Applications on Mobile Phones*, MobiSys 2009
- Huckins et al., Beiwe Research Platform, *JMIR mHealth* 2020
- 论文《Investigating Context-Aware Collaborative Text Entry on Smartphones using Large Language Models》（CATIA）
