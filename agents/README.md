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
5. `MSDKDOCS\Docs\Android_API\cn\index.html` or `MSDKDOCS\Docs\Android_API\en\index.html`
   Local DJI MSDK reference docs. Use these before changing DJI-facing code.

Rules for future agents:

- Treat `D:\ROS2Android\android-app\DJI-App` as the only final Android app.
- Do not use `D:\ROS2Android\Mobile-SDK-Android-V5-dev-sdk-main` as business code.
- Do not treat `D:\ROS2Android\msdk` as the final app. It is historical reference only.
- Do not store real secrets in this folder. Reference their config locations instead.
- Before changing DJI registration behavior, verify the current `applicationId` and local App Key source in `local.properties`.
- Before writing or changing any DJI MSDK integration, consult the local docs under `MSDKDOCS`.
- Do not guess DJI enums, key paths, manager APIs, callback semantics, or waypoint support from memory.
- If the code and the local DJI docs appear inconsistent, prefer the local DJI docs and then verify against the current source tree.
- After every code change, update the relevant files in this `agents` directory with the purpose, key implementation details, verification result, and any new debugging rules.
- The top-right app status chip must only show an aircraft/product connected state when the vehicle itself is connected. ROS bridge connectivity and DJI SDK registration are not aircraft connection.
