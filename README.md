# Android用户行为数据收集器

基于CATIA论文实现的安卓用户行为数据收集系统，能够收集用户使用手机过程中的各种上下文信息并输出JSON格式的数据。

## 功能特性

### 数据收集类别

根据CATIA论文的设计，本程序收集以下类别的数据：

#### 1. 时间信息
- 当前日期时间 (`date_time`)
- 星期几 (`day_of_week`)
- 时间戳 (`timestamp`)

#### 2. 位置信息 (`location`)
- GPS坐标（纬度、经度）
- 位置精度
- 海拔高度
- 可读地址（地理编码）

#### 3. 活动识别 (`activity`)
- 静止 (still)
- 走路 (walking) 
- 跑步 (running)
- 骑车 (cycling)
- 其他活动 (others)
- 识别置信度

#### 4. 连接设备信息
- **蓝牙设备** (`bluetooth_devices`)
  - 设备名称
  - MAC地址
  - 设备类型
  - 连接状态
- **WiFi信息** (`wifi_info`)
  - SSID网络名称
  - BSSID
  - 信号强度(RSSI)
  - 连接速度
  - 频率

#### 5. 日历事件 (`calendar_events`)
- 过去3天和未来3天的事件
- 事件标题
- 开始/结束时间
- 描述
- 位置

#### 6. 屏幕内容 (`screen_content`)
- 屏幕文本内容
- 屏幕类型（聊天/普通屏幕）
- 应用包名
- 时间戳
- 内容稳定性检测

#### 7. 当前应用信息 (`current_app`)
- 应用包名
- 应用重要性等级

## 技术实现

### 核心组件

1. **AndroidDataCollector.java** - 主数据收集器
   - 协调各种数据源
   - 定时收集上下文数据
   - 权限管理

2. **ActivityRecognizer.java** - 活动识别器
   - 基于加速度计和陀螺仪数据
   - 机器学习特征提取
   - 实时活动分类

3. **ScreenContentCollector.java** - 屏幕内容收集器
   - 基于Accessibility Service
   - 屏幕稳定性检测
   - 内容去重算法

### 权限要求

程序需要以下Android权限：

```xml
<!-- 基本权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 位置权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- 蓝牙权限 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

<!-- WiFi权限 -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

<!-- 日历权限 -->
<uses-permission android:name="android.permission.READ_CALENDAR" />

<!-- 活动识别权限 -->
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

<!-- 无障碍服务权限 -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
```

## 使用方法

### 1. 初始化数据收集器

```java
AndroidDataCollector collector = new AndroidDataCollector();
```

### 2. 获取完整上下文数据

```java
JSONObject contextData = collector.getCompleteContextData();
```

### 3. 数据输出格式

程序输出的JSON数据格式如下：

```json
{
  "context_data": {
    "timestamp": 1703123456789,
    "date_time": "2024-12-21 14:30:45",
    "day_of_week": "星期四",
    "location": {
      "latitude": 39.9042,
      "longitude": 116.4074,
      "accuracy": 10.0,
      "altitude": 45.2,
      "readable_address": "北京市朝阳区"
    },
    "activity": {
      "activity": "walking",
      "confidence": 0.85,
      "timestamp": 1703123456789
    },
    "bluetooth_devices": [
      {
        "name": "AirPods Pro",
        "address": "AA:BB:CC:DD:EE:FF",
        "type": 1,
        "bond_state": 12
      }
    ],
    "wifi_info": {
      "ssid": "\"MyWiFi\"",
      "bssid": "00:11:22:33:44:55",
      "rssi": -45,
      "link_speed": 150,
      "frequency": 5180
    },
    "calendar_events": [
      {
        "title": "团队会议",
        "start_time": 1703140800000,
        "end_time": 1703144400000,
        "description": "讨论项目进展",
        "location": "会议室A"
      }
    ],
    "screen_content": [
      {
        "timestamp": 1703123456789,
        "type": "chat",
        "content": "你好，今天的会议取消了吗？",
        "app_package": "com.tencent.mm"
      }
    ],
    "current_app": {
      "package_name": "com.tencent.mm",
      "importance": 100
    }
  },
  "collection_time": 1703123456789
}
```

## 数据收集策略

### 收集频率
- **传感器数据**: 20Hz采样率
- **位置数据**: 每分钟或移动10米时更新
- **屏幕内容**: 每200ms检测变化，稳定400ms后记录
- **完整上下文**: 每30秒收集一次

### 数据存储
- JSON文件保存到外部存储
- 文件名格式: `context_data_[timestamp].json`
- 内存中维护实时数据结构

### 隐私保护
- 本地数据处理，不上传到云端
- 用户可随时停止数据收集
- 敏感数据脱敏处理

## 活动识别算法

基于传感器数据的活动识别：

### 特征提取
- 加速度均值、标准差、最大值、最小值
- 陀螺仪数据统计特征
- 总加速度变化
- 步频检测
- 方向变化检测

### 分类规则
- **静止**: 加速度标准差 < 0.5 且总变化 < 2.0
- **走路**: 中等加速度变化(1.0-4.0) 且步频0.5-2.5 Hz
- **跑步**: 高加速度变化(>3.0) 且步频>2.0 Hz  
- **骑车**: 中等加速度变化且低方向变化
- **其他**: 不符合以上模式的活动

## 屏幕内容处理

### 稳定性检测
- 每200ms截图检测变化
- 内容稳定400ms后记录
- 相似度阈值0.8去重

### 聊天识别
- 检测关键词：发送、聊天、消息、回复
- 特殊处理聊天界面布局
- 提取发送者和消息内容

### 队列管理
- 维护最近20个屏幕内容
- 按时间顺序排列
- 支持时间范围查询

## 部署要求

### 系统要求
- Android 6.0 (API 23) 及以上
- 支持蓝牙4.0+
- GPS/网络定位
- 加速度计和陀螺仪传感器

### 开发环境
- Android Studio 4.0+
- Gradle 6.0+
- Target SDK: 33
- Min SDK: 23

## 注意事项

1. **权限申请**: 首次运行需要用户授权各种权限
2. **无障碍服务**: 需要用户手动启用屏幕内容收集功能
3. **电池优化**: 长期运行可能影响电池续航
4. **存储空间**: 大量数据需要足够的存储空间
5. **隐私合规**: 使用前需要获得用户明确同意

## 扩展功能

### 可选增强
- 机器学习模型优化活动识别
- 图像识别增强屏幕内容理解
- 服务器端数据分析
- 用户行为模式分析
- 个性化推荐引擎

### 研究应用
- 人机交互研究
- 移动设备使用模式分析
- 上下文感知计算
- 智能文本输入系统
- 移动用户行为建模

## 许可证

本项目基于Apache 2.0许可证开源，详见LICENSE文件。

## 参考文献

基于论文《Investigating Context-Aware Collaborative Text Entry on Smartphones using Large Language Models》的设计理念和技术方案。 

# 配置Gemini API

## 获取API密钥

1. 访问 [Google AI Studio](https://aistudio.google.com/)
2. 创建或登录您的Google账户
3. 获取Gemini API密钥

## 配置API密钥

在 `app/src/main/java/com/datacollector/android/GeminiApiClient.java` 文件中：

```java
private static final String API_KEY = "YOUR_API_KEY_HERE"; // 请替换为您的Gemini API密钥
```

将 `YOUR_API_KEY_HERE` 替换为您从Google AI Studio获取的实际API密钥。

## 使用方法

1. 启动应用
2. 点击"启动数据收集"开始收集用户行为数据
3. 等待数据收集一段时间
4. 点击"调用Gemini AI分析"按钮
5. 系统将自动使用最新收集的数据文件和prompt.txt进行AI分析
6. 分析结果将显示在屏幕上

## 注意事项

- 确保设备有网络连接
- API调用可能需要几秒钟时间
- 确保您的API密钥有足够的配额
- prompt.txt文件已包含在应用的assets目录中 