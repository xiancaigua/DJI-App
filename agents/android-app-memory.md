# Android App Memory

## 1. App root

- Root: `D:\ROS2Android\android-app\DJI-App`
- Main module: `app`

## 2. Build and runtime facts

Current build facts from `app/build.gradle.kts`:

- compileSdk `34`
- targetSdk `34`
- minSdk `26`
- Kotlin/Java target `17`
- Compose enabled
- DJI SDK `5.17.0`
- DJI WPMZ SDK `1.0.4.0`

Current local config source:

- `D:\ROS2Android\android-app\DJI-App\local.properties`

Important local properties:

- `APP_APPLICATION_ID`
- `AIRCRAFT_API_KEY`
- `DJI_ENABLE_RUNTIME`

Do not copy the actual App Key into agent memory. Read it from `local.properties` when needed.

## 3. Application identity behavior

`app/build.gradle.kts` validates `APP_APPLICATION_ID` with a package-name regex.

Behavior:

- if `APP_APPLICATION_ID` is valid, Gradle uses it as runtime `applicationId`
- if invalid, it falls back to `com.example.uavmobile`

Current intended value is:

- `com.jzapp.mobile`

## 4. Main code entry points

- Application:
  - `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\MApplication.kt`
- Activity:
  - `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\MainActivity.kt`
- Main Compose shell:
  - `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\UavApp.kt`
- Main ViewModel:
  - `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\viewmodel\UavViewModel.kt`

## 5. Backend routing

The app supports two backends:

- `DroneBackend.SELF_ROS`
- `DroneBackend.DJI`

Routing is handled in `UavViewModel` through:

- `SelfDroneController`
- `DjiDroneController`

Mission actions already route through the controller layer:

- upload
- start
- pause
- stop
- resume
- return home
- land

Do not bypass this with direct UI-to-repository or UI-to-DJI-SDK calls.

## 6. Top status semantics

The top-right status chip is intentionally vehicle/aircraft-facing, not backend-facing.

Current implementation:

- Derived in `...\\ui\\viewmodel\\UavViewModel.kt`
  - `vehicleConnected`
  - `topStatusLabel`
  - `topStatusKind`
  - `resolveTopVehicleStatus()`
- Displayed in `...\\ui\\screen\\UavApp.kt`
- Broken out in `...\\ui\\screen\\ConnectionScreen.kt`
- Copied into `...\\debug\\DeveloperSnapshot.kt` and shown in `...\\ui\\screen\\DeveloperPanelScreen.kt`
- Unit tests live in `...\\src\\test\\java\\com\\example\\uavmobile\\ui\\viewmodel\\TopVehicleStatusTest.kt`

Rules:

- Never use a bare `Connected` label for the top chip.
- `SELF_ROS` only gets top connected state when `telemetry.connected == true`.
- `SELF_ROS` with only rosbridge/WebSocket connected must show `ROS Link Online`, not `Connected`.
- `DJI` only gets top connected state when `djiProductConnected == true`.
- DJI SDK `REGISTERED`, permissions granted, or backend init success must not show connected; use `DJI SDK Ready · Aircraft Offline` when registered but no product is connected.
- Green/`ConnectionStatus.CONNECTED` top-chip state must mean actual aircraft/product connected, not companion computer or SDK readiness.

Expected labels include:

- `Aircraft Connected`
- `DJI Aircraft Connected`
- `Aircraft Offline`
- `ROS Link Online`
- `ROS Connecting`
- `DJI SDK Ready · Aircraft Offline`
- `DJI Registering`
- `DJI Failed`

Keep the old `connectionStatus` field unless doing a complete replacement. It is still used for backend/button flow, but the top status must use the dedicated top-status fields.

## 7. DJI initialization and registration

Core files:

- `...\\dji\\DjiMsdkManager.kt`
- `...\\dji\\DjiRuntimeEnvironment.kt`
- `...\\dji\\DjiConnectionManager.kt`

Local DJI reference docs:

- Chinese index:
  - `D:\ROS2Android\android-app\DJI-App\agents\MSDKDOCS\Docs\Android_API\cn\index.html`
- English index:
  - `D:\ROS2Android\android-app\DJI-App\agents\MSDKDOCS\Docs\Android_API\en\index.html`

High-value doc areas inside `MSDKDOCS`:

- `Components\\SDKManager`
- `Components\\ISDKManager`
- `Components\\KeyManager` / `Components\\IKeyManager`
- `Components\\IWaypointMissionManager`
- `Components\\VirtualStickManager`

Agent rule:

- for DJI manager APIs, key paths, callback signatures, product types, and waypoint support, read the local MSDK docs first
- do not guess enum mappings or key availability from prior memory

Key rules:

- startup must gate DJI runtime before `Helper.install()` on unsupported environments
- emulator/virtual-device paths are intentionally allowed to skip DJI runtime
- `registerApp()` retries when network becomes available after SDK init

Known validated state:

- `registerApp()` has succeeded on real device for `applicationId=com.jzapp.mobile`

## 8. DJI aircraft family support

Current supported wayline mappings:

- `M400 / Matrice 400 -> WaylineDroneType.PM440`
- `Matrice 4 Series -> WaylineDroneType.WA345`
- `Matrice 4D Series -> WaylineDroneType.EA230`

Main mapping files:

- `...\\dji\\DjiAircraftResolver.kt`
- `...\\dji\\DjiConnectedAircraftResolver.kt`
- `...\\dji\\DjiWaylineAircraftTypeMapper.kt`

This is no longer in the old "M4 blocked" state.

Real-aircraft mission execution for M4 still needs device-side verification even though code and tests pass.

If future work changes these mappings, agents must validate the target enum/class in local `MSDKDOCS` before editing code.

## 9. Hidden developer panel

The app contains an internal developer panel.

Entry methods:

- tap the top-right connection status chip 3 times quickly
- or launch Activity with:

```powershell
adb shell am start -n com.jzapp.mobile/.MainActivity --ez openDeveloperPanel true
```

Relevant files:

- `...\\MainActivity.kt`
- `...\\ui\\screen\\UavApp.kt`
- `...\\ui\\screen\\DeveloperPanelScreen.kt`

The old long-press entry was removed. Do not document or rely on it.

## 10. DJI waypoint obstacle-avoidance safety

The app has a DJI waypoint/KMZ safety layer for obstacle avoidance.

Core rule:

- Describe this feature as: the app configures and checks DJI aircraft platform perception/obstacle-avoidance capability through DJI MSDK before route execution, listens to obstacle data during execution, and requests pause/alerts on abnormal distance.
- Do not describe it as app-side autonomous obstacle avoidance or app-side path planning.

Main files:

- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\dji\ObstacleAvoidanceSafetyManager.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\dji\DjiWaypointMissionManager.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\core\ObstacleAvoidanceModels.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\debug\DeveloperSnapshot.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\ControlScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\DeveloperPanelScreen.kt`

MSDK APIs verified locally for SDK 5.17.0:

- `PerceptionManager.getInstance()`
- `setObstacleAvoidanceType` / `getObstacleAvoidanceType`
- `setObstacleAvoidanceEnabled` / `getObstacleAvoidanceEnabled`
- `setObstacleAvoidanceWarningDistance` / `getObstacleAvoidanceWarningDistance`
- `setObstacleAvoidanceBrakingDistance` / `getObstacleAvoidanceBrakingDistance`
- `addObstacleDataListener` / `removeObstacleDataListener`

Mission-start invariant:

- `DjiWaypointMissionManager.startMission()` must run existing aircraft/location/Home prechecks first, then `ObstacleAvoidanceSafetyManager.prepareForWaypointMission(...)`, then DJI `startMission(...)`.
- If mode is `CLOSE`, try to set `BRAKE`.
- If final mode is not `BRAKE` or `BYPASS`, block route start.
- Direction switch/distance unsupported failures are warnings only when main mode is safe.

Runtime invariant:

- `BRAKE` is the default conservative policy.
- `BYPASS` must not be enabled by default.
- Obstacle listener converts DJI millimeter distances to meters.
- Consecutive emergency readings below threshold can request DJI `pauseMission()`, with debounce.
- Listener cleanup is required on mission finish/stop/failure/interruption/disconnect.

Diagnostics:

- Control page shows obstacle mode, direction switches, nearest obstacle distance, safety state, listener state, and latest message.
- Developer Panel shows `DJI Obstacle Avoidance Diagnostics`.
- Developer snapshot summary includes the same diagnostics for copy/paste debugging.

Tests:

- `ObstacleAvoidanceSafetyManagerTest` covers `CLOSE -> BRAKE`, hard block on failed safe mode, partial unsupported warnings, distance conversion, emergency pause debounce, and listener cleanup.
- `DeveloperSnapshotTest` covers obstacle diagnostics summary output.
- `UavViewModelRoutingTest` uses `runCurrent()` and `clearForTest()` so the DJI polling loop does not make JVM unit tests hang.

## 11. Diagnostics and waypoint import

Implemented diagnostics:

- app-internal ring-buffer developer logs
- backend-aware current aircraft state snapshot
- developer snapshot summary in ViewModel

Implemented mission authoring helper:

- "Import Current Position" adds a new waypoint using current backend aircraft latitude/longitude

Relevant files:

- `...\\debug\\DeveloperLogStore.kt`
- `...\\debug\\DeveloperSnapshot.kt`
- `...\\ui\\viewmodel\\WaypointImportSupport.kt`
- `...\\ui\\screen\\MissionScreen.kt`

## 12. Documents worth checking before new work

- `D:\ROS2Android\docs\android-msdk-integration.md`
- `D:\ROS2Android\docs\android-app-developer-debug-guide-zh.md`
- `D:\ROS2Android\docs\android-app-training-manual-zh.md`
- `D:\ROS2Android\android-app\DJI-App\agents\MSDKDOCS\Docs\Android_API\cn\index.html`
- `D:\ROS2Android\android-app\DJI-App\agents\MSDKDOCS\Docs\Android_API\en\index.html`

## 13. Build command

Use:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

## 14. Compact Compose UI pass

Implemented on 2026-05-25.

Purpose:

- Make the Android Compose UI denser and easier to scan on DJI RC / tablet-like screens.
- Keep this as a layout-only change: no DJI, ROS, connection, waypoint, or obstacle-avoidance business logic should change.

Main files:

- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\CompactInfoGrid.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\DashboardScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\MissionScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\ConnectionScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\ControlScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\EventScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\UavApp.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\DeveloperPanelScreen.kt`

Implementation notes:

- `CompactInfoGrid` / `CompactInfoItem` is the shared UI helper for responsive compact label/value cells.
- Dashboard core metrics now use a compact grid that can place up to four items per row when width allows.
- Mission waypoint editing places latitude / longitude / altitude in one row on wide layouts, with a fallback for narrow screens; hold time and yaw share one row.
- Connection and Control status cards group diagnostic fields into compact grids instead of one text line per field.
- Event cards collapse metadata into one single-line row with ellipsis.
- Top app bar now uses two compact lines and ellipsis for long status text.

Important invariants:

- Top-right status semantics were not changed. `DJI 飞机已连接` / aircraft-connected wording must still only represent a real aircraft/product connection.
- UI compactness must not reduce safety-critical button availability or make DJI SDK calls from UI composables.

Verification:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

Result: passed on 2026-05-25.

Device validation:

- `app-debug.apk` installed and `com.example.uavmobile/.MainActivity` was launched once on DJI RC Plus 2 before the device went offline due to power.
- Full visual walkthrough on the physical device was not completed because the device was powered down.
- Launch log exposed a separate runtime issue unrelated to compact UI: `NoClassDefFoundError` for `dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener` from `DjiWaypointMissionManager`. Future DJI waypoint debugging should resolve that SDK/runtime class mismatch before judging UI or mission behavior on device.

## 15. DJI camera stream preview

Implemented on 2026-05-26.

Purpose:

- Add real-time aircraft video return/display capability for DJI MSDK V5 without changing mission upload/start/pause/stop or obstacle-avoidance business logic.
- Use DJI platform video sources through `MediaDataCenter.getInstance().getCameraStreamManager()` and `putCameraStreamSurface(...)`.
- This is for pilot takeover assistance, waypoint mission supervision, and flight situational awareness. It is not AI recognition, app-side video decoding, livestream forwarding, or autonomous obstacle avoidance.

Main files:

- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\core\CameraStreamModels.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\dji\AircraftCameraStreamManager.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\viewmodel\UavViewModel.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\ControlScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\debug\DeveloperSnapshot.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\ui\screen\DeveloperPanelScreen.kt`
- `D:\ROS2Android\android-app\DJI-App\app\src\test\java\com\example\uavmobile\dji\AircraftCameraStreamManagerTest.kt`

Implementation notes:

- `CameraStreamModels.kt` is SDK-neutral. UI and developer snapshot do not expose DJI SDK classes.
- `AircraftCameraStreamManager` wraps DJI `ICameraStreamManager`, available camera listener, Surface binding/unbinding, source switching, and reserved frame/raw stream listener entry points.
- `ControlScreen` adds a DJI-only "机身摄像头回传" card with a TextureView-backed preview, current source, `cameraIndex`, aircraft model, video status, refresh, and manual switch.
- `UavViewModel` owns preview lifecycle callbacks: enter/exit preview, surface ready/destroyed, refresh sources, and switch source. UI composables still do not call DJI SDK directly.
- Developer Panel and copied diagnostic summary now include `DJI Camera Stream Diagnostics`.

Default source rules:

- Vision Assist is never selected as the mission supervision default source.
- Matrice 4 / Matrice 4D / M4T targets prefer non-Vision integrated gimbal/main camera, with `WIDE_CAMERA` preferred when `KeyCameraVideoStreamSourceRange` is readable.
- Matrice 400 targets prefer `ComponentIndexType.FPV`; if FPV cannot be identified, use the first non-Vision source and log a warning that field confirmation is required.
- Unknown aircraft use the first non-Vision source and warn that `cameraIndex` mapping requires real-device confirmation.
- If only Vision Assist is available, the UI reports no mission-supervision video source instead of using it.

Lifecycle and failure behavior:

- Entering DJI Control page initializes the stream module, registers `addAvailableCameraUpdatedListener`, refreshes available sources, and selects a default.
- TextureView `SurfaceTexture` creation/change wraps an Android `Surface` and binds through `putCameraStreamSurface(cameraIndex, surface, width, height, CENTER_CROP)`.
- Surface destroy, page exit, backend switch away from DJI, disconnect, and ViewModel clear release the Surface/listeners and reserved frame/raw stream listeners.
- Video unavailable or display failure logs warnings and updates UI; it does not block mission start. `startMission()` only emits a warning if there is no usable/displaying video.

Tests:

- `AircraftCameraStreamManagerTest` covers M4T wide/default selection, M400 FPV selection, unknown fallback, Vision Assist exclusion, Surface bind/unbind, switch failure restore, and opt-in frame/raw listeners.
- `UavViewModelRoutingTest` covers DJI Control page preview init and confirms missing video warns but does not block `startMission`.
- `DeveloperSnapshotTest` covers copied camera diagnostics.

Verification:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon --stacktrace
```

Result: passed on 2026-05-26.

Real-device follow-up:

- 2026-05-26 RC Plus 2 validation installed and launched `app-debug.apk`; the app stayed foreground with no `FATAL EXCEPTION`.
- Current pulled checkout only has `sdk.dir` in `local.properties`, so generated BuildConfig was `APPLICATION_ID=com.example.uavmobile` and `DJI_ENABLE_RUNTIME=false`; live DJI registration/ProductKey/camera stream could not be validated until App Key/package/runtime config is restored.
- The first `SurfaceView` preview caused a full-screen black overlay on RC Plus 2. It was replaced with `TextureView`; after reinstall, the full-window black overlay was gone and the Control page showed only the preview pane black with `Surface=READY`, `可用源=0`, `显示=OFF`, `暂无可用视频源`.
- Follow-up validation found the camera card still consumed too much of the Control page and could hide aircraft/obstacle status. Keep `ObstacleAvoidanceStatusCard` before `AircraftCameraPreviewCard`, keep Control page bottom padding for the bottom navigation bar, and keep the camera card compact: wide layouts use a 128dp TextureView preview beside the camera diagnostics grid instead of a full-width 16:9 block.
- After reinstall on RC Plus 2, aircraft/obstacle safety status was visible first, and the camera preview appeared below in a compact side-by-side layout without covering state text.
- On DJI RC Plus 2 with M4T/M400 connected and DJI runtime enabled, still verify: available camera list callbacks, actual `cameraIndex` labels, M4T wide/zoom/thermal mapping, M400 FPV mapping with no payload, live image display, manual switching, disconnect/reconnect, background/foreground release/rebind.
- If M4T/M400 camera mapping differs, adjust only `AircraftCameraStreamManager.selectDefaultCameraIndex(...)` and label/source mapping, not mission execution flow.
- Separate device-side blocker still appears in logs: `NoClassDefFoundError` for `dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener`. It is caught/no-crash in this validation, but real waypoint execution should not be judged until that SDK/runtime class mismatch is resolved.
