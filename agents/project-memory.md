# Project Memory

## 1. Goal

The project target is one Android app that can control two backends:

- self-developed UAV backend over ROS/rosbridge
- DJI aircraft backend over DJI MSDK V5

The final app root is:

- `D:\ROS2Android\android-app\DJI-App`

This is the only Android app that should be extended going forward.

## 2. Repo boundaries

- `D:\ROS2Android\ros_ws`
  ROS workspace for the self-developed aircraft side. Keep this stable unless interface verification is required.
- `D:\ROS2Android\android-app\DJI-App`
  Final Android app. Contains both ROS client logic and DJI MSDK integration.
- `D:\ROS2Android\msdk`
  Historical standalone DJI prototype. Do not revive it as a second final app.
- `D:\ROS2Android\Mobile-SDK-Android-V5-dev-sdk-main`
  Official DJI sample/reference only. Do not merge sample code wholesale into the business app.
- `D:\ROS2Android\docs`
  User-facing and developer-facing documentation.

## 3. Final architecture

Android app responsibilities:

- ROS mode:
  - connect to rosbridge
  - call `/mobile/*` services
  - receive telemetry/events
- DJI mode:
  - initialize DJI MSDK
  - register the Android app with DJI
  - read DJI connection/state
  - build/upload/start waypoint missions

Control routing is unified in the Android app through `DroneController`:

- `SelfDroneController` for ROS
- `DjiDroneController` for DJI

The UI must not call DJI SDK details directly.

## 4. Important decisions already made

- Keep `DJI-App` as the only final Android app.
- Keep ROS `/mobile/*` contract intact.
- Inject DJI App Key through Gradle properties and manifest placeholders, not Kotlin source.
- Keep arm64-focused DJI packaging for real DJI hardware; emulator support is only for compile/startup validation.
- Developer diagnostics exist inside the app and should be extended there instead of scattering ad hoc debug UI.

## 5. Current identity

Current valid Android runtime identity:

- `applicationId = com.jzapp.mobile`

Important nuance:

- Gradle `namespace` is still `com.example.uavmobile`
- runtime `applicationId` is configurable and currently resolves to `com.jzapp.mobile`

Future agents must not assume namespace and applicationId are the same.

## 6. DJI registration constraints

DJI registration only succeeds when all of these match:

- Android `applicationId`
- DJI Developer Center `Package Name`
- App Key created for that exact Android package

The old package `com.example.uavmobile` caused confusion because both old and new packages were installed on device at one point.

If DJI registration fails again:

1. verify which package is actually installed and launched
2. verify `local.properties`
3. verify DJI Developer Center package name matches the runtime package exactly

## 7. References

- Main app memory: `android-app-memory.md`
- ROS contract: `ros-mobile-contract.md`
- Status snapshot: `current-status.md`
