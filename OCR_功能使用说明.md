# CATIA3 OCR功能使用说明

## 📱 功能概述

我们已经成功为CATIA3项目添加了**OCR（光学字符识别）功能**，现在可以：

1. 🖼️ **自动截图** - 定期捕获屏幕截图
2. 🔤 **文字识别** - 使用Google ML Kit识别图片中的中英文文字
3. 📊 **数据融合** - 将OCR识别的文字与无障碍服务获取的文字结合
4. 📈 **统计分析** - 提供OCR识别效果的统计信息

## 🔧 技术实现

### 核心组件

1. **OcrProcessor.java** - OCR文字识别处理器
   - 支持中英文混合识别
   - 使用Google ML Kit Text Recognition API
   - 提供置信度评估

2. **ScreenshotCapture.java** - 屏幕截图工具
   - 支持View截图和全屏截图
   - 自动保存截图文件
   - 处理不同Android版本兼容性

3. **ScreenContentData.java** - 增强的数据模型
   - 新增OCR文本字段
   - 支持截图路径存储
   - 提供文本内容合并功能

4. **ScreenContentCollector.java** - 增强的屏幕内容收集器
   - 集成OCR识别流程
   - 智能去重算法
   - 性能优化控制

## 📦 依赖库

已添加到 `app/build.gradle`:

```gradle
// Google ML Kit OCR依赖
implementation 'com.google.mlkit:text-recognition:16.0.0'
implementation 'com.google.mlkit:text-recognition-chinese:16.0.0'
```

## 🔐 权限要求

已添加到 `AndroidManifest.xml`:

```xml
<!-- 截图和媒体投影权限 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.CAPTURE_VIDEO_OUTPUT" />
<uses-permission android:name="android.permission.CAPTURE_SECURE_VIDEO_OUTPUT" />

<!-- 相机权限（可选） -->
<uses-permission android:name="android.permission.CAMERA" />
```

## 🎯 使用方法

### 1. 启动数据收集

在主界面点击"启动数据收集"按钮，OCR功能会自动启用。

### 2. 查看OCR统计

点击"查看OCR统计"按钮，可以看到：

```json
{
  "total_items": 15,           // 总屏幕内容数量
  "ocr_items": 8,             // 包含OCR数据的数量
  "ocr_coverage": 0.53,       // OCR覆盖率
  "average_confidence": 0.87,  // 平均识别置信度
  "ocr_enabled": true         // OCR功能是否启用
}
```

### 3. 获取屏幕内容数据

获取到的JSON数据现在包含OCR信息：

```json
{
  "timestamp": 1703123456789,
  "type": "screen",
  "content": "无障碍服务获取的文本",
  "app_package": "com.example.app",
  "ocr_text": "OCR识别的文本内容",
  "ocr_confidence": 0.85,
  "screenshot_path": "/path/to/screenshot.png",
  "full_text_content": "无障碍文本: xxx\nOCR文本: xxx"
}
```

## ⚙️ 配置参数

在 `ScreenContentCollector.java` 中可以调整：

```java
// OCR相关常量
private static final boolean ENABLE_OCR = true;           // OCR功能开关
private static final float OCR_CONFIDENCE_THRESHOLD = 0.3f; // 置信度阈值
private static final int OCR_INTERVAL = 2000;             // OCR处理间隔(ms)
```

## 🚀 性能优化特性

1. **智能触发** - 只在屏幕稳定时进行OCR识别
2. **间隔控制** - 避免过于频繁的OCR处理
3. **异步处理** - OCR识别在后台线程进行
4. **内存管理** - 及时释放Bitmap资源
5. **去重算法** - 避免重复存储相似内容

## 📊 数据流程

```
屏幕变化 → 无障碍服务获取文本 → 屏幕稳定检测 → 截图 → OCR识别 → 数据合并 → 存储到队列
```

## 🔍 故障排除

### 常见问题

1. **OCR识别率低**
   - 确保屏幕内容清晰
   - 调整OCR_CONFIDENCE_THRESHOLD参数
   - 检查Google Play Services是否最新

2. **截图失败**
   - 检查是否授予了截图权限
   - 确认无障碍服务已启用
   - 检查存储权限

3. **性能影响**
   - 增加OCR_INTERVAL间隔
   - 降低截图频率
   - 关闭OCR功能（设置ENABLE_OCR = false）

### 日志调试

查看Logcat中的相关标签：
- `ScreenContentCollector` - 主要收集逻辑
- `OcrProcessor` - OCR识别过程
- `ScreenshotCapture` - 截图相关

## 🎉 效果预期

启用OCR功能后，你将获得：

- ✅ **更完整的文本内容** - 结合无障碍服务和OCR识别
- ✅ **图片文字识别** - 识别图片、广告、游戏中的文字
- ✅ **多语言支持** - 支持中英文混合识别
- ✅ **可视化数据** - 保存截图便于后续分析
- ✅ **统计信息** - 了解OCR识别效果

## 📝 注意事项

1. **隐私保护** - 截图和OCR数据仅本地处理，不上传云端
2. **存储空间** - 截图会占用存储空间，建议定期清理
3. **电池消耗** - OCR处理会增加电池消耗
4. **权限要求** - 需要用户手动授予相关权限

## 🔄 后续扩展

可以进一步添加：
- 实时OCR识别
- 特定区域OCR
- 文字翻译功能
- OCR结果缓存
- 云端OCR API集成 