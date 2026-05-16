# Android App 开发调试说明

适用工程：`D:\ROS2Android\android-app\DJI-App`

## 1. 目的

本说明面向开发和联调人员，重点说明：

- 如何进入隐藏式开发面板
- 当前应用能看到哪些 ROS / DJI 状态
- 应用内日志从哪里来
- 如何使用新的“导入当前经纬度”为航点功能

## 2. 开发面板入口

默认入口：

- 长按主界面右上角的连接状态标签

辅助入口：

- 可通过 `adb` 冷启动时附加参数打开

示例命令：

```powershell
adb shell am force-stop com.example.uavmobile
adb shell am start -n com.example.uavmobile/.MainActivity --ez openDeveloperPanel true
```

说明：

- 这个 `adb` 入口只是为了开发和截图自动化方便。
- 普通用户流程仍然是长按右上角状态标签。

## 3. 开发面板包含的状态

### 3.1 App Identity

- `applicationId`
- `versionName`
- 当前 `activeBackend`
- 当前 `selectedDjiAircraftFamily`

### 3.2 ROS Status

- `websocketUrl`
- `rosConnectionStatus`
- `rosSessionActive`
- `rosMissionCacheCount`
- `rosLatestAlert`

### 3.3 DJI Status

- `djiSdkInitState`
- `djiSdkStatusMessage`
- `djiProductConnected`
- `djiProductId`
- `djiProductType`
- `djiProductStatusMessage`

### 3.4 Position and Home

- 当前后端统一后的 `latitude / longitude`
- `altitudeMeters`
- `headingDegrees`
- `homeLatitude / homeLongitude`
- 当前状态附加说明 `stateMessage`

### 3.5 Permissions and Mission

- `djiPermissionsGranted`
- `djiMissingPermissions`
- 当前选中的任务 ID
- 当前显示的任务数量
- 当前选中任务状态和进度

## 4. 应用内日志来源

应用内日志使用 ring buffer 保存最近 `150` 条记录。

当前接入来源：

- `UavViewModel`
  - 后端切换
  - 连接、断开、刷新、上传、启动、暂停、停止、返航、降落
  - 导入当前位置为航点
- `UavRepository`
  - ROS 连接状态变化
  - ROS 任务刷新
  - ROS 任务上传
  - ROS 事件到达
- `DjiMsdkManager`
  - DJI 初始化
  - `registerApp`
  - 网络恢复
  - 初始化和注册失败原因
- `DjiConnectionManager`
  - 产品连接、断开、变更
- `DjiWaypointMissionManager`
  - 任务准备
  - 任务上传
  - 任务启动、暂停、停止
  - 执行进度
  - 中断和失败

日志级别：

- `DEBUG`
- `INFO`
- `WARN`
- `ERROR`

## 5. 开发面板按钮用途

- `Refresh Snapshot`
  - 立即刷新当前统一状态快照
- `Copy Diagnostic Summary`
  - 复制一份适合发给开发者的问题摘要
- `Copy Recent Logs`
  - 复制最近的应用内诊断日志
- `Clear Logs`
  - 清空当前应用内日志

建议：

1. 复现问题
2. 打开开发面板
3. 先 `Copy Diagnostic Summary`
4. 再 `Copy Recent Logs`
5. 最后再考虑 `Clear Logs`

## 6. 当前飞机位置统一逻辑

### 6.1 Self ROS 模式

来源：

- `UavRepository.telemetry`

转换方式：

- `TelemetrySnapshot -> DroneState`

有效条件：

- 已有有效遥测
- 经纬度不为空且不为默认零值

### 6.2 DJI MSDK 模式

来源：

- `DjiAircraftStateReader.getState()`

有效条件：

- DJI SDK 已初始化到可读状态
- 产品已连接
- 当前有有效飞机位置

失败时：

- `stateMessage` 会保留当前原因
- `Import Current Position` 会拒绝新增航点

## 7. 一键导入当前经纬度

入口：

- `Mission` 页面中的 `Import Current Position`

行为：

1. 读取当前后端统一后的飞机位置
2. 自动新增一个航点
3. 把当前 `Latitude / Longitude` 写入该航点
4. 从最后一个已有航点复制 `Altitude / Hold / Yaw`
5. 如果没有可复制航点，则使用默认模板值

失败条件：

- ROS 遥测未就绪
- DJI 未连接
- 当前没有有效经纬度

典型排查：

- 在开发面板先看 `Position and Home`
- 如果 `latitude / longitude` 为空或 `0.0`，说明当前数据源尚未就绪

## 8. 常见联调问题

### 8.1 DJI registerApp 失败

优先检查：

- 遥控器是否联网
- DJI Developer Center 中的 `Package Name` 是否与当前 `applicationId` 完全一致
- `AIRCRAFT_API_KEY` 是否对应这条应用记录

### 8.2 切到 DJI 模式但没有位置

优先检查：

- `djiSdkInitState`
- `djiProductConnected`
- `djiProductType`
- 权限是否已授予

### 8.3 ROS 已连接但任务列表为空

优先检查：

- `rosConnectionStatus`
- `rosSessionActive`
- `Sync Missions` 调用后是否有 ROS 侧返回
- `Events` 页面是否出现错误反馈

## 9. 当前实现边界

- 应用内日志不是系统 `logcat` 的镜像，而是应用内关键诊断事件的摘要流。
- 开发面板目前只做查看、复制和清空，不提供直接修改底层配置的能力。
- 当前统一位置状态已接入 `Mission Canvas`、`Import Current Position` 和开发面板；后续如果需要，可以继续扩展到更多页面。
