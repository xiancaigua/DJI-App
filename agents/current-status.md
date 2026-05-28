# Current Status

## 1. Verified working state

These points have already been validated in the current branch/workspace:

- `DJI-App` is the final Android app root.
- package/applicationId has been switched to `com.jzapp.mobile`.
- local DJI App Key is configured in `local.properties`.
- Gradle build succeeds with:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

- DJI `registerApp()` has succeeded on device for `com.jzapp.mobile`.
- hidden developer panel exists and is opened by tapping the status chip 3 times or with an intent extra.
- M4 family support is enabled in code and no longer hard-blocked.
- top-right status chip semantics have been fixed: it now reports aircraft/product connection, not generic backend readiness.
- verified after that fix with:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

## 2. Things that caused real issues before

- old package `com.example.uavmobile` and new package `com.jzapp.mobile` were both installed at one point
- invalid package names such as `JZApp` or `jzapp` caused fallback or registration mismatch
- DJI Developer Center package name must match the actual runtime applicationId exactly
- emulator validation is limited to startup/build behavior, not real DJI connectivity
- ROS bridge/WebSocket connected previously made the top chip show `Connected`; this is now explicitly forbidden because it does not prove aircraft connection.
- DJI SDK `REGISTERED` previously looked too close to connected readiness; this is now shown as `DJI SDK Ready · Aircraft Offline` until a product is connected.

## 3. Known limits that still require real hardware

- actual aircraft connection for M400
- actual aircraft connection for Matrice 4 / Matrice 4D
- real upload/start/pause/stop/RTL/land mission execution on DJI hardware
- USB/RC environment behavior on the final controller hardware

## 4. Recommended first checks for future debugging

1. confirm the launched package name on device
2. confirm `APP_APPLICATION_ID` in `local.properties`
3. confirm DJI Developer Center package name and App Key match that package
4. confirm `DJI_ENABLE_RUNTIME=true`
5. confirm device networking if `registerApp()` is failing
6. use the developer panel before changing code blindly
7. if the issue touches DJI APIs or behavior, inspect local `MSDKDOCS` before changing code
8. for top-right status bugs, inspect `topStatusLabel`, `topStatusKind`, and `vehicleConnected` in `UavViewModel` / developer snapshot before changing UI text

## 5. DJI development discipline

For this repo, DJI-facing development must be document-driven.

Required rule for future agents:

- do not "write by impression" for DJI MSDK code
- check the local docs under `D:\ROS2Android\android-app\DJI-App\agents\MSDKDOCS`
- especially verify manager class names, callback contracts, key paths, product types, and waypoint-related support there first

## 6. Change-memory discipline

Every future code change must be reflected back into `agents` memory before finishing the task.

Minimum update content:

- purpose of the change
- files or modules touched
- important implementation details and new invariants
- verification command/result
- any new debug rule that future agents should preserve

## 7. Stable operational shortcuts

Open developer panel directly:

```powershell
adb shell am start -n com.jzapp.mobile/.MainActivity --ez openDeveloperPanel true
```

If package confusion returns, inspect installed packages:

```powershell
adb shell pm list packages | findstr jzapp
adb shell pm list packages | findstr uavmobile
```

## 8. Latest DJI obstacle-avoidance safety layer

Implemented on 2026-05-25 for DJI waypoint/KMZ execution safety.

Purpose:

- Before DJI waypoint `startMission(...)`, the app now configures/checks DJI aircraft platform obstacle avoidance through MSDK V5 perception APIs.
- This is not an app-side autonomous obstacle avoidance algorithm. The app enables/checks DJI aircraft perception/avoidance and adds UI/log visibility plus a conservative pause request when obstacle distance becomes abnormal.

Main code paths:

- `app/src/main/java/com/example/uavmobile/dji/ObstacleAvoidanceSafetyManager.kt`
- `app/src/main/java/com/example/uavmobile/dji/DjiWaypointMissionManager.kt`
- `app/src/main/java/com/example/uavmobile/core/ObstacleAvoidanceModels.kt`
- `app/src/main/java/com/example/uavmobile/ui/screen/ControlScreen.kt`
- `app/src/main/java/com/example/uavmobile/ui/screen/DeveloperPanelScreen.kt`
- `app/src/main/java/com/example/uavmobile/debug/DeveloperSnapshot.kt`

Important behavior:

- Default avoidance type is `BRAKE`.
- `BYPASS` is only reserved for future field-tested configuration and is not default.
- `CLOSE` before mission start triggers an attempt to set `BRAKE`.
- If the app cannot confirm `BRAKE` or `BYPASS`, DJI waypoint mission start is blocked.
- HORIZONTAL / UPWARD / DOWNWARD switch and distance API failures are warnings when the main mode is already safe; they must not crash the app.
- `ObstacleDataListener` updates nearest obstacle distance and safety state.
- During `ENTER_WAYLINE` / `EXECUTING`, repeated distance below the emergency threshold requests `pauseMission()` with debounce.
- Finished, stopped, interrupted, failed, and disconnect paths must clean up the obstacle listener.

Verification:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

Result: passed on 2026-05-25.

## 9. Latest compact UI pass

Implemented on 2026-05-25.

Purpose:

- Make Compose pages more compact without changing DJI/ROS/mission behavior.
- Dashboard metrics use a responsive compact grid.
- Mission waypoint editor groups latitude / longitude / altitude together on wide screens and groups hold / yaw together.
- Connection, Control, Event, top bar, and Developer Panel spacing were tightened.

Verification:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

Result: passed on 2026-05-25.

Device note:

- A debug APK was installed and launched on DJI RC Plus 2, but full visual validation could not continue because the device was powered down/offline.
- The launch logs showed a separate DJI waypoint runtime mismatch: `NoClassDefFoundError` for `dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener`. Treat that as a follow-up DJI SDK/runtime issue, not a compact UI issue.

## 10. Latest DJI camera stream preview

Implemented on 2026-05-26.

Purpose:

- Add real-time aircraft video preview to the DJI Control page.
- Use DJI MSDK V5 `CameraStreamManager` to display aircraft camera / FPV video through a Compose-hosted `TextureView`.
- Keep video failure as a warning only; it must not break existing DJI connection, waypoint upload/start/pause/stop, or obstacle-avoidance logic.

Main code paths:

- `app/src/main/java/com/example/uavmobile/core/CameraStreamModels.kt`
- `app/src/main/java/com/example/uavmobile/dji/AircraftCameraStreamManager.kt`
- `app/src/main/java/com/example/uavmobile/ui/viewmodel/UavViewModel.kt`
- `app/src/main/java/com/example/uavmobile/ui/screen/ControlScreen.kt`
- `app/src/main/java/com/example/uavmobile/debug/DeveloperSnapshot.kt`
- `app/src/main/java/com/example/uavmobile/ui/screen/DeveloperPanelScreen.kt`

Important behavior:

- UI does not call DJI SDK directly. It calls ViewModel callbacks; ViewModel delegates to `AircraftCameraStreamManager`.
- `AircraftCameraStreamManager` registers `addAvailableCameraUpdatedListener`, keeps SDK types inside the `dji` package, and exposes SDK-neutral `CameraStreamSnapshot`.
- Matrice 4 / M4T default source: non-Vision integrated gimbal/main camera; prefer `WIDE_CAMERA` when source range is readable.
- Matrice 400 default source: `ComponentIndexType.FPV` when available; otherwise first non-Vision source with a warning.
- Unknown aircraft: first non-Vision source with a mapping warning.
- Vision Assist is not selected as the Mission Flight supervision source. If only Vision Assist exists, show no supported mission-supervision video source.
- TextureView SurfaceTexture create/change wraps an Android `Surface` and binds `putCameraStreamSurface(..., CENTER_CROP)`.
- Surface destroy/page exit/backend switch/disconnect/ViewModel test clear release camera resources.
- Frame listener and raw stream listener APIs are reserved and opt-in; they are not enabled by default.

Diagnostics:

- Control page has a "机身摄像头回传" card with TextureView-backed preview, source, `cameraIndex`, aircraft model, status, refresh, and switch.
- Developer Panel and copied summary include `DJI Camera Stream Diagnostics`: available sources, selected source, Surface/display state, frame/raw listener flags, warning, error.

Tests added/updated:

- `AircraftCameraStreamManagerTest`
- `UavViewModelRoutingTest`
- `DeveloperSnapshotTest`

Verification:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon --stacktrace
```

Result: passed on 2026-05-26.

Real hardware still required:

- Confirm actual camera image on DJI RC Plus 2 with M4T/M400 after local DJI runtime config is restored.
- Confirm M4T camera source mapping for wide/zoom/thermal.
- Confirm M400 FPV mapping when no payload is mounted.
- Confirm manual source switching, disconnect/reconnect, and app background/foreground Surface rebinding.

Device validation on 2026-05-26:

- `.\gradlew.bat assembleDebug testDebugUnitTest --no-daemon --stacktrace` passed after switching the preview host from `SurfaceView` to `TextureView`.
- DJI RC Plus 2 was online as `model:DJI_RC_PLUS_2 product:rc701`; `adb install -r app\build\outputs\apk\debug\app-debug.apk` succeeded.
- Current pulled checkout's `local.properties` only contains `sdk.dir`; generated BuildConfig was `APPLICATION_ID=com.example.uavmobile` and `DJI_ENABLE_RUNTIME=false`.
- Because DJI runtime was disabled and no App Key/package config was present in this checkout, live DJI registration, ProductKey, and real camera stream could not be validated.
- App launched and stayed foreground on the RC Plus 2 with no `FATAL EXCEPTION`.
- Entering DJI Control showed the video card without full-window black overlay. UI tree reported `Surface=READY`, `可用源=0`, `显示=OFF`, and `暂无可用视频源`, which is the expected degraded state for runtime-disabled/no-source validation.
- The first `SurfaceView` version caused a full-screen black overlay on RC Plus 2. Keep the preview host as `TextureView` unless a future DJI SDK requirement proves otherwise.
- Follow-up UI validation found the camera preview card could still visually push/cover aircraft safety status. `ControlScreen` now shows the DJI obstacle/aircraft safety status before the camera card, adds bottom scroll padding, and uses a compact camera layout: 128dp TextureView preview on wide screens with the camera diagnostics grid beside it.
- After reinstall on RC Plus 2, the first Control screen showed full obstacle/aircraft safety state without the camera card covering it; scrolling down showed the camera preview and source diagnostics in a compact side-by-side layout.
- Separate known blocker remains in device logs: `NoClassDefFoundError` for `dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener`. It is caught/no-crash here, but it must be fixed before judging real waypoint execution on device.
