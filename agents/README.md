# DJI-App Agent Memory Index

This directory is the curated agent memory for `D:\ROS2Android\android-app\DJI-App`.

Use these files in this order:

1. `project-memory.md`
   High-level project purpose, repo boundaries, and non-negotiable decisions.
2. `android-app-memory.md`
   Final Android app structure, build/runtime rules, DJI/ROS integration details, and hidden developer entry.
3. `ros-mobile-contract.md`
   Stable `/mobile/*` ROS contract reference used by the Android app.
4. `current-status.md`
   Verified state, known limits, and the next things that still require real hardware.

Rules for future agents:

- Treat `D:\ROS2Android\android-app\DJI-App` as the only final Android app.
- Do not use `D:\ROS2Android\Mobile-SDK-Android-V5-dev-sdk-main` as business code.
- Do not treat `D:\ROS2Android\msdk` as the final app. It is historical reference only.
- Do not store real secrets in this folder. Reference their config locations instead.
- Before changing DJI registration behavior, verify the current `applicationId` and local App Key source in `local.properties`.
