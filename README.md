# LiooTap · 轻点

LiooTap 是一款基于 Android 无障碍手势和系统悬浮窗实现的屏幕操作工具，支持单点连点、手势录制、脚本回放、自定义执行序列和悬浮快捷键。

## 功能

- 在其他应用上方放置点击目标点，配置点击间隔、按下时长和执行次数。
- 录制单指点击与滑动，保存操作间隔、轨迹点和手势时长。
- 录制与回放时显示点击脉冲或拖动轨迹，并可分别控制是否显示。
- 保存录制脚本，设置回放前延时、循环次数和循环间隔。
- 将多个录制组合为自定义执行序列，为每项设置最多 9999 次循环。
- 创建工具箱快捷键或独立悬浮快捷键，快速运行指定脚本。
- 在执行悬浮条中查看执行次数、已用时间，并进行暂停、继续、停止和轨迹切换。

## 运行原理

- `ClickAccessibilityService` 使用 `AccessibilityService.dispatchGesture` 注入点击和滑动，不读取界面文本或无障碍节点内容。
- `OverlayService` 以前台服务管理工具箱、录制控制栏和执行控制栏。
- 普通悬浮控件使用 `TYPE_APPLICATION_OVERLAY`；录制触摸区域使用受信任的 `TYPE_ACCESSIBILITY_OVERLAY`。
- 录制器先捕获完整手势，抬手后再将轨迹镜像到目标应用。Android 公开 API 无法让同一原始触摸同时交给录制层和目标应用。
- 用户设置与脚本数据保存在设备本地，应用不会上传录制内容。

## 环境要求

- Android 10（API 29）或更高版本
- Android SDK 35
- JDK 17 或更高版本

## 构建

```bash
./gradlew :app:assembleDebug
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建应通过 Gradle 命令行注入签名参数或使用本机私有配置。不要将 keystore、密码或签名后的安装包提交到仓库。

## 首次使用

1. 开启“LiooTap 点击服务”无障碍权限。
2. 授予“显示在其他应用上层”权限。
3. 根据系统提示关闭电池优化，以提高后台悬浮窗的稳定性。
4. 点击悬浮球打开工具箱，选择连点器、录制脚本、自定义序列或快捷键功能。

## 项目结构

```text
app/src/main/java/com/lioo/autoclick/
├── ClickAccessibilityService.java  # 点击与手势注入
├── OverlayService.java             # 悬浮窗生命周期和功能调度
├── OverlayController.java          # 主工具箱与连点器悬浮控件
├── GestureRecordingOverlay.java    # 手势捕获、录制试播和保存
├── ScriptPlaybackOverlay.java      # 脚本执行状态机与控制栏
├── ExecutionPlan.java              # 脚本和自定义序列标准化
├── Library.java                    # 本地脚本数据存储
└── Ui.java                         # 通用原生界面构建方法
```

## 权限说明

- 无障碍服务：仅用于用户主动启动的点击和滑动注入。
- 悬浮窗：显示工具箱、录制控制栏、执行控制栏和轨迹反馈。
- 前台服务：在切换到目标应用后维持悬浮控制。
- 开机启动：仅在用户此前保持悬浮功能开启时尝试恢复服务。
