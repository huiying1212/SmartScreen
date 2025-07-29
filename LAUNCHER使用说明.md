# CATIA3 Launcher 使用说明

## 概述
CATIA3 Launcher 是基于您现有的Android数据收集应用开发的自定义启动器，可以替代Android系统的默认主屏幕，同时保留原有的数据收集功能。

## 功能特性

### 1. 主屏幕替代
- 替代Android原生主屏幕
- 用户按Home键或开机后将看到CATIA3 Launcher界面
- 显示所有已安装的应用程序图标

### 2. 应用管理
- 自动扫描并显示系统中安装的应用
- 4列网格布局，美观整洁
- 支持点击启动应用
- 过滤系统应用，只显示用户应用和常用系统应用

### 3. 数据收集集成
- 继承原有的CATIA3数据收集功能
- 记录用户应用启动行为
- 支持上下文感知数据分析

### 4. 界面功能
- **顶部状态栏**：显示Launcher名称和当前时间
- **应用网格**：4x N 网格显示所有应用
- **底部工具栏**：
  - 设置按钮：跳转到原始数据收集管理界面
  - 应用按钮：刷新应用列表
  - 数据按钮：查看当前数据收集状态
  - **退出按钮**：退出Launcher模式，返回系统默认启动器

### 5. 🆕 退出功能
- 红色"退出"按钮提供安全的退出方式
- 点击后会显示确认对话框
- 支持多种退出方式：
  - 自动打开系统默认应用设置
  - 尝试切换到其他已安装的启动器
  - 提供手动设置指导

## 安装和设置

### 1. 编译安装
```bash
# 在项目根目录执行
.\gradlew clean
.\gradlew assembleDebug
# 或者
.\gradlew assembleRelease
```

### 2. 设置为默认启动器
1. 安装APK后，按Home键
2. Android系统会询问选择启动器
3. 选择"CATIA3 Launcher"并设置为默认

### 3. 退出Launcher模式
**新增功能** - 现在可以通过以下方式安全退出：

#### 方法1：使用退出按钮（推荐）
1. 在CATIA3 Launcher主界面
2. 点击底部工具栏的红色"退出"按钮
3. 在确认对话框中点击"确定退出"
4. 系统会自动打开启动器设置页面
5. 选择其他启动器作为默认

#### 方法2：手动设置
1. 进入 设置 > 应用管理 > 默认应用
2. 找到"主屏幕应用"或"启动器"设置
3. 选择系统默认启动器或其他第三方启动器

#### 方法3：应用管理
1. 进入 设置 > 应用管理
2. 找到"CATIA3 Launcher"
3. 点击"清除默认设置"
4. 下次按Home键时重新选择启动器

## 文件结构

新增的主要文件：
```
app/src/main/java/com/datacollector/android/
├── LauncherActivity.java          # 主启动器活动
app/src/main/res/
├── layout/
│   ├── activity_launcher.xml      # 主界面布局
│   └── app_item.xml              # 应用项布局
├── drawable/
│   ├── launcher_button_bg.xml    # 按钮背景
│   ├── launcher_exit_button_bg.xml # 退出按钮背景
│   └── app_item_bg.xml           # 应用项背景
└── values/
    ├── colors.xml                # 新增Launcher颜色
    └── themes.xml                # 新增Launcher主题
```

## 自定义配置

### 1. 修改应用显示
在 `LauncherActivity.java` 中的 `isWhitelistedSystemApp()` 方法中，可以添加或移除要显示的系统应用：

```java
private boolean isWhitelistedSystemApp(String packageName) {
    return packageName.equals("com.android.settings") ||
           packageName.equals("com.android.calculator2") ||
           // 添加更多应用包名...
}
```

### 2. 修改界面样式
- 编辑 `colors.xml` 修改颜色主题
- 编辑 `activity_launcher.xml` 修改布局
- 编辑 `app_item.xml` 修改应用项显示

### 3. 修改网格列数
在 `activity_launcher.xml` 中修改 GridView 的 `android:numColumns` 属性：
```xml
<GridView
    android:numColumns="4"  <!-- 修改这里的数字 -->
    ... />
```

### 4. 自定义退出按钮样式
修改 `launcher_exit_button_bg.xml` 和相关颜色：
```xml
<!-- 在colors.xml中 -->
<color name="launcher_exit_bg">#FFCC4444</color>      <!-- 退出按钮背景色 -->
<color name="launcher_exit_pressed">#FFDD5555</color> <!-- 按下时颜色 -->
<color name="launcher_exit_text">#FFFFFFFF</color>    <!-- 文字颜色 -->
```

## 数据收集功能

### 应用启动追踪
每次用户启动应用时，Launcher会自动记录：
- 应用包名
- 应用标签名
- 启动时间戳
- 启动行为

这些数据会发送到原有的 `DataCollectionService` 进行处理。

### 查看数据状态
点击底部工具栏的"数据"按钮可以查看：
- 当前系统时间
- 已安装应用数量
- 数据收集状态

## 安全特性

### 1. 安全退出机制
- 退出按钮使用醒目的红色设计
- 双重确认机制防止意外退出
- 多种退出方式确保用户能够成功退出

### 2. 权限保护
- 退出功能会检查系统权限
- 提供用户友好的错误提示
- 备用退出方案确保可靠性

## 注意事项

1. **权限要求**：确保应用有必要的系统权限
2. **性能考虑**：Launcher会常驻内存，注意性能优化
3. **兼容性**：在不同Android版本上测试功能
4. **数据隐私**：确保用户了解数据收集行为
5. **⚠️ 退出安全**：退出按钮为红色醒目设计，使用前请确认

## 故障排除

### 常见问题
1. **应用不显示**：检查应用是否在白名单中
2. **启动失败**：确保应用有LAUNCHER权限
3. **界面异常**：检查布局文件和资源文件
4. **🆕 无法退出**：尝试多种退出方式或手动在设置中更改

### 退出问题解决
如果退出按钮不工作：
1. 进入手机设置 > 应用管理
2. 找到"CATIA3 Launcher"
3. 点击"清除默认设置"
4. 重新设置默认启动器

### 调试方法
```bash
# 查看日志
adb logcat | grep LauncherActivity

# 检查权限
adb shell dumpsys package com.datacollector.android

# 查看当前默认启动器
adb shell cmd package query-services --brief android.service.home
```

## 更新日志

### v1.1 (最新)
- ✅ 新增退出Launcher模式功能
- ✅ 红色退出按钮设计
- ✅ 多重退出机制
- ✅ 安全确认对话框
- ✅ 智能启动器检测和切换

### v1.0
- ✅ 基础Launcher功能
- ✅ 应用网格显示
- ✅ 数据收集集成
- ✅ 时间显示功能

## 技术支持

如有问题或需要功能扩展，请参考：
- Android Launcher开发文档
- CATIA3项目原始文档
- Android应用开发最佳实践

---

**注意**：此Launcher基于CATIA3数据收集系统开发，在使用过程中会继续收集用户行为数据用于研究目的。请确保用户已知悉并同意数据收集行为。退出功能确保用户可以随时安全地返回到系统默认启动器。 