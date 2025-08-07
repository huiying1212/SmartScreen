# 已安装应用列表集成功能

## 功能概述

此功能实现了在应用首次启动时检查设备上所有已安装的应用，并在每次向LLM发送请求时包含这个应用列表，确保LLM不会推荐设备上不存在的应用。

## 实现详情

### 1. 新增文件

#### `InstalledAppsManager.java`
- **位置**: `app/src/main/java/com/datacollector/android/InstalledAppsManager.java`
- **功能**: 管理已安装应用列表的工具类
- **特性**:
  - 单例模式，确保全局只有一个实例
  - 自动缓存机制，5分钟内重复请求使用缓存数据
  - 支持强制刷新功能
  - 生成JSON格式和文本格式的应用列表
  - 过滤系统应用，保留用户常用的系统应用（如设置、相机等）

### 2. 修改的文件

#### `LauncherActivity.java`
- **修改内容**:
  - 在 `onCreate()` 方法中初始化 `InstalledAppsManager` 并立即刷新应用列表
  - 在 `onResume()` 方法中刷新应用列表，以防在后台时有新应用安装

#### `DeepSeekApiClient.java` 
- **修改内容**:
  - `callDeepSeekWithLatestData()` 方法：在构建请求内容时包含已安装应用列表
  - `analyzeAndTriggerLLM()` 方法：在自动分析时也包含已安装应用列表
  - 在请求中添加重要提示，指导LLM只推荐已安装的应用

#### `GeminiApiClient.java`
- **修改内容**:
  - `callGeminiWithLatestData()` 方法：在构建请求内容时包含已安装应用列表
  - 在请求中添加重要提示，指导LLM只推荐已安装的应用

## 工作流程

1. **应用启动时**：
   - `LauncherActivity.onCreate()` 被调用
   - 初始化 `InstalledAppsManager` 并刷新应用列表
   - 扫描设备上所有可启动的应用
   - 过滤掉不必要的系统应用
   - 生成JSON格式的应用列表

2. **应用恢复时**：
   - `LauncherActivity.onResume()` 被调用
   - 刷新 `InstalledAppsManager` 的应用列表
   - 检查是否有新安装的应用

3. **LLM请求时**：
   - 获取最新的已安装应用列表
   - 将应用列表添加到请求内容中
   - 包含明确指导，要求LLM只推荐已安装的应用

## JSON格式示例

```json
{
  "device_apps_count": 45,
  "installed_apps": [
    {
      "name": "设置",
      "package": "com.android.settings"
    },
    {
      "name": "微信",
      "package": "com.tencent.mm"
    },
    {
      "name": "Chrome",
      "package": "com.android.chrome"
    }
  ]
}
```

## 缓存机制

- **缓存时长**: 5分钟
- **自动更新**: 超过缓存时长时自动刷新
- **手动刷新**: 调用 `refreshAppsList()` 方法强制刷新
- **内存管理**: 只保存必要的应用信息（名称、包名、类名）

## 系统应用过滤

### 被过滤的应用
- 大部分系统内置应用
- 系统服务类应用

### 白名单应用（会被保留）
- 设置 (com.android.settings)
- 计算器 (com.android.calculator2)
- 相机 (com.android.camera)
- 图库 (com.android.gallery3d)
- 音乐 (com.android.music)
- 浏览器 (com.android.browser)
- Chrome (com.android.chrome)
- 联系人 (com.android.contacts)
- 电话 (com.android.phone)
- 短信 (com.android.mms)
- 以及其他常用应用（WhatsApp、微信、Gmail等）

## 日志输出

应用会在以下情况输出日志：
- 应用列表更新时
- JSON生成完成时
- 出现错误时

日志标签：`InstalledAppsManager`

## 权限要求

需要以下权限（已在 AndroidManifest.xml 中声明）：
- `QUERY_ALL_PACKAGES` - 查询所有已安装的包

## 使用方法

```java
// 获取单例实例
InstalledAppsManager manager = InstalledAppsManager.getInstance(context);

// 获取JSON格式的应用列表（供LLM使用）
String appsJson = manager.getInstalledAppsListJson();

// 获取应用列表对象
List<InstalledAppsManager.AppInfo> apps = manager.getInstalledAppsList();

// 强制刷新应用列表
manager.refreshAppsList();
```

## 性能优化

- 使用单例模式避免重复初始化
- 缓存机制减少重复扫描
- 后台线程处理，不阻塞UI
- 只保存必要的应用信息，减少内存占用 