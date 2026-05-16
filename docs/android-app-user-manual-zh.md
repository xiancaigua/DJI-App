# Android App 中文说明书

本文档对应工程：

- Android 主工程：`D:\ROS2Android\android-app\DJI-App`
- 应用名称：`PX4 Mobile Task Client`

当前应用是一个统一控制入口，支持两类后端：

- `Self ROS`：连接自研无人机的 ROS 系统
- `DJI MSDK`：连接 DJI 遥控器 / 飞机，走 DJI Mobile SDK

## 1. 应用整体结构

应用顶部和底部是固定区域，中间是当前页面内容区。

### 1.1 顶部标题栏

顶部会显示以下信息：

- `PX4 Mobile Task Client`
  - 应用主标题
- `Active backend: ...`
  - 当前激活的后端
  - 可能是 `Self ROS` 或 `DJI MSDK`
- 一行状态文字
  - 用于显示当前最近一次操作结果、错误提示或连接提示

右上角有一个状态标签，不是操作按钮，主要用于显示连接状态：

- `Connected`
  - 当前已连接
- `Connecting`
  - 当前正在连接或初始化
- `Failed`
  - 当前连接或初始化失败
- `Offline`
  - 当前未连接

### 1.2 底部导航栏

底部共有 5 个主页面按钮：

1. `Connect`
   - 连接设置与后端切换页面
2. `Dashboard`
   - 飞行状态总览页面
3. `Mission`
   - 航点任务编辑与上传页面
4. `Control`
   - 任务执行控制页面
5. `Events`
   - 系统事件与错误记录页面

## 2. Connect 页面

`Connect` 页面用于选择后端、配置连接方式、初始化 DJI、查看后端状态。

### 2.1 Backend 选择区

这里有两个切换按钮：

- `Self ROS`
  - 选择自研无人机 ROS 后端
  - 选中后，下面显示 ROS 连接配置
- `DJI MSDK`
  - 选择 DJI 后端
  - 选中后，下面显示 DJI 配置和权限状态

### 2.2 Self ROS 模式下的控件

当选择 `Self ROS` 时，会显示：

- `Host / IP`
  - 输入 ROS 侧 `rosbridge` 服务的主机地址
  - 默认值是 `192.168.1.10`
- `Port`
  - 输入 ROS 侧 `rosbridge` 服务端口
  - 默认值是 `9090`

### 2.3 DJI MSDK 模式下的控件

当选择 `DJI MSDK` 时，会显示：

- `Auto`
  - 目标机型自动模式
  - 程序会尽量根据当前连接的 DJI 产品判断机型
- `M400`
  - 手动指定目标机型为 `M400 / Matrice 400`
- `Matrice 4`
  - 手动指定目标机型为 `Matrice 4 系列`
  - 当前版本仅保留选择入口，真实航点上传仍受限于 DJI Wayline 机型确认结果

权限区会显示：

- 当前 DJI 权限状态文字
  - 用来说明是否已经授予定位、蓝牙等运行时权限
- `Android applicationId: ...`
  - 显示当前 APK 的真实包名
  - 这个值必须和 DJI Developer Center 的 `Package Name` 完全一致
- 一行说明文字
  - 提示 DJI 平台上的 `Package Name` 必须与应用包名完全相同

如果权限还没有授予，会出现按钮：

- `Grant DJI Permissions`
  - 发起 DJI 运行时权限申请
  - 主要包括定位、蓝牙等权限

### 2.4 页面操作按钮

这一行有 3 个按钮：

- `Connect`
  - 仅在 `Self ROS` 模式下显示为 `Connect`
  - 用于连接 ROS `rosbridge`
- `Init DJI`
  - 仅在 `DJI MSDK` 模式下显示为 `Init DJI`
  - 用于初始化 DJI MSDK 并触发 `registerApp()`
- `Disconnect`
  - 断开当前后端
  - 对 ROS 是主动断开连接
  - 对 DJI 是请求结束当前逻辑连接状态
- `Sync Missions`
  - 仅在 `Self ROS` 模式下显示
  - 从 ROS 侧同步任务列表
- `Refresh State`
  - 仅在 `DJI MSDK` 模式下显示
  - 刷新 DJI 当前任务状态和连接状态

### 2.5 Backend status 卡片

这个区域不是按钮，主要用于显示当前后端状态。

在 `Self ROS` 模式下，会显示：

- `WebSocket URL`
  - 当前 ROS WebSocket 地址
- `ROS connection`
  - ROS 连接状态
- `Session active`
  - 当前移动端会话是否有效
- `Latest alert`
  - 最近一次告警

在 `DJI MSDK` 模式下，会显示：

- DJI SDK 状态信息
  - 例如初始化、注册、注册失败原因
- DJI 产品连接状态
  - 例如是否连接到遥控器 / 飞机
- `Permissions granted`
  - 当前权限是否授予
- 一段说明文字
  - 提醒模拟器只验证初始化和日志，不验证真实飞行器连接

## 3. Dashboard 页面

`Dashboard` 页面是飞行状态总览页，全部是展示项，没有操作按钮。

页面包含以下信息卡片：

- `Mode`
  - 飞行模式
- `Mission`
  - 当前任务阶段
- `Battery`
  - 电池百分比
- `Voltage`
  - 当前电压
- `GPS`
  - GPS Fix 类型
- `Satellites`
  - 可见卫星数量
- `Altitude`
  - 相对高度
- `Ground Speed`
  - 地速

页面下方还有一个 `Position` 卡片，显示：

- 飞机坐标
- Home 点坐标
- 航向角
- 任务进度
- 当前移动端会话状态

## 4. Mission 页面

`Mission` 页面用于编辑任务、填写航点并上传。

### 4.1 页面说明区

页面顶部会根据当前后端显示不同说明：

- `Self ROS`
  - 上传任务会走 ROS 后端
- `DJI MSDK`
  - 上传任务会走 DJI MSDK

如果当前处于 `DJI MSDK` 且机型选中了 `Matrice 4`，还会显示一条限制说明，提示该机型当前仍受 DJI Wayline 类型确认限制。

### 4.2 Mission Canvas

`Mission Canvas` 是航点简图展示区域，不是操作按钮。

它会把以下点位画在一个简化画布里：

- 已填写的任务航点
- Home 点
- 当前飞机位置

它的作用是辅助你检查航点的大致相对位置。

### 4.3 Mission 基本控件

- `Mission ID`
  - 输入任务编号
  - 这个编号会作为本次任务的标识符

### 4.4 页面主按钮

- `Add Waypoint`
  - 新增一个航点输入卡片
- `Upload Mission`
  - 上传当前任务
  - 在 ROS 模式下走 ROS 上传链路
  - 在 DJI 模式下走 DJI 航点任务准备与上传链路

### 4.5 每个航点卡片

每个航点卡片包含：

- `Waypoint N`
  - 当前航点编号
- 右上角删除图标
  - 删除当前航点
- `Latitude`
  - 输入纬度
- `Longitude`
  - 输入经度
- `Altitude (m)`
  - 输入航点高度，单位米
- `Hold (s)`
  - 输入悬停时间，单位秒
- `Yaw (deg)`
  - 输入该航点期望偏航角，单位度

## 5. Control 页面

`Control` 页面用于查看任务列表、选中任务并执行控制动作。

### 5.1 页面说明

顶部说明文字会根据当前后端不同而变化：

- `Self ROS`
  - 任务控制通过 ROS `/mobile/*` 服务执行
- `DJI MSDK`
  - 任务控制通过 DJI MSDK 执行
  - 在当前 DJI 路径里，`Stop` 会替代 `Resume`

### 5.2 页面按钮

- `Refresh Mission List`
  - 仅在 `Self ROS` 模式显示
  - 刷新 ROS 任务列表
- `Refresh Mission State`
  - 仅在 `DJI MSDK` 模式显示
  - 刷新 DJI 当前任务状态

### 5.3 任务卡片列表

如果当前没有任务：

- ROS 模式会提示还没有上传任务
- DJI 模式会提示还没有准备并上传 DJI 任务

如果已有任务：

- 点击某个任务卡片
  - 选中该任务
- 每张卡片会显示：
  - 任务编号
  - 航点数量
  - 当前状态
  - 任务进度

### 5.4 任务控制按钮

第一行按钮：

- `Start`
  - 启动当前选中的任务
- `Pause`
  - 暂停当前任务
- `Resume`
  - 仅在 `Self ROS` 模式下出现
  - 恢复当前任务
- `Stop`
  - 仅在 `DJI MSDK` 模式下出现
  - 停止当前 DJI 任务

第二行按钮：

- `RTL`
  - Return To Launch / Return To Home
  - 请求返航
- `Land`
  - 请求降落

## 6. Events 页面

`Events` 页面用于查看系统事件和错误记录，没有操作按钮。

页面会显示：

- `System Events`
  - 事件页标题
- 一段说明文字
  - 提示这里显示任务响应、心跳问题和安全门控拒绝记录

如果当前没有事件：

- 会显示 `No events received yet.`

如果已有事件，每条事件会显示：

- 事件级别
- 事件代码
- 事件说明文字
- 错误码
- 关联任务编号
- 接收时间

## 7. 当前版本使用建议

### 7.1 如果你要连接自研无人机

建议按以下顺序操作：

1. 进入 `Connect`
2. 选择 `Self ROS`
3. 填写 `Host / IP` 和 `Port`
4. 点击 `Connect`
5. 点击 `Sync Missions`
6. 进入 `Mission` 或 `Control` 页面继续操作

### 7.2 如果你要连接 DJI

建议按以下顺序操作：

1. 进入 `Connect`
2. 选择 `DJI MSDK`
3. 点击 `Grant DJI Permissions`
4. 检查页面显示的 `Android applicationId`
5. 确认 DJI Developer Center 中的 `Package Name` 与它完全一致
6. 点击 `Init DJI`
7. 初始化成功后，再进入 `Mission` 上传任务
8. 最后到 `Control` 页面执行 `Start / Pause / Stop / RTL / Land`

## 8. 当前版本的重要限制

- `Matrice 4` 路径当前仍保留限制，原因是 DJI Wayline 机型类型仍需进一步确认
- 模拟器主要用于编译和基础页面验证，不用于真实 DJI 飞行器控制验证
- DJI 的 `registerApp()` 强依赖：
  - 正确的 App Key
  - 正确的 Android 包名
  - 遥控器联网
- 当前 DJI 平台上配置的 `Package Name` 必须和 APK 的 `applicationId` 完全一致，否则会注册失败
