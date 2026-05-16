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
