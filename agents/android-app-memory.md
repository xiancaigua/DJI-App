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

## 6. DJI initialization and registration

Core files:

- `...\\dji\\DjiMsdkManager.kt`
- `...\\dji\\DjiRuntimeEnvironment.kt`
- `...\\dji\\DjiConnectionManager.kt`

Key rules:

- startup must gate DJI runtime before `Helper.install()` on unsupported environments
- emulator/virtual-device paths are intentionally allowed to skip DJI runtime
- `registerApp()` retries when network becomes available after SDK init

Known validated state:

- `registerApp()` has succeeded on real device for `applicationId=com.jzapp.mobile`

## 7. DJI aircraft family support

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

## 8. Hidden developer panel

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

## 9. Diagnostics and waypoint import

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

## 10. Documents worth checking before new work

- `D:\ROS2Android\docs\android-msdk-integration.md`
- `D:\ROS2Android\docs\android-app-developer-debug-guide-zh.md`
- `D:\ROS2Android\docs\android-app-training-manual-zh.md`

## 11. Build command

Use:

```powershell
cd D:\ROS2Android\android-app\DJI-App
.\gradlew.bat clean assembleDebug testDebugUnitTest
```
