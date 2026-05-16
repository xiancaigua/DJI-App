# ROS Mobile Contract

This file is the agent-facing contract summary for the self-developed aircraft backend.

## 1. Scope

Android must use the stable `/mobile/*` ROS surface. It should not directly depend on raw MAVROS topics/services for normal mobile control flows.

The Android client side implementation currently lives under:

- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\data`

The ROS side workspace lives under:

- `D:\ROS2Android\ros_ws`

## 2. Topics

### `/mobile/telemetry`

Type: `mobile_contract/Telemetry`

Important fields used by Android:

- `flight_mode`
- `armed`
- `connected`
- `gps_fix_type`
- `satellites_visible`
- `battery_percent`
- `battery_voltage`
- `latitude`
- `longitude`
- `home_latitude`
- `home_longitude`
- `home_available`
- `relative_altitude_m`
- `heading_deg`
- `ground_speed_mps`
- `mission_stage`
- `mission_progress`
- `session_active`
- `latest_alert`

### `/mobile/events`

Type: `mobile_contract/Event`

Important fields:

- `level`
- `code`
- `error_code`
- `message`
- `related_mission_id`

Typical event codes:

- `MISSION_UPLOADED`
- `MISSION_STARTED`
- `MISSION_PAUSED`
- `MISSION_RESUMED`
- `RTL_TRIGGERED`
- `LAND_TRIGGERED`
- `MISSION_START_REJECTED`
- `MISSION_RESUME_REJECTED`
- `SESSION_TIMEOUT`
- `SESSION_STALE`

### `/mobile/session/heartbeat`

Type: `mobile_contract/SessionHeartbeat`

Behavior:

- Android sends heartbeat every 2 seconds
- `mobile_gateway` times out around 5 seconds
- control actions should be blocked if the session is stale

## 3. Services

### `/mobile/mission/upload`

Type: `mobile_contract/UploadMission`

Request:

- `mission_id`
- `waypoints`

Waypoint fields:

- `lat`
- `lon`
- `alt_m`
- `hold_sec`
- `yaw_deg`

Current validation rules expected by Android:

- mission id required
- mission id length <= 64
- mission id unique
- at least 1 waypoint
- latitude in `[-90, 90]`
- longitude in `[-180, 180]`
- altitude > 0
- hold >= 0
- yaw in `[-180, 360]`

### `/mobile/mission/list`

Type: `mobile_contract/ListMissions`

Response returns:

- `missions`
- `success`
- `error_code`
- `message`

### `/mobile/mission/start`
### `/mobile/mission/pause`
### `/mobile/mission/resume`
### `/mobile/mission/rtl`
### `/mobile/mission/land`

Type: `mobile_contract/MissionCommand`

Behavior summary:

- `start`: uploads/activates mission flow and switches PX4 into mission mode
- `pause`: should move PX4 into a safe loiter-style hold
- `resume`: should re-check safety gating before resuming
- `rtl`: requires valid session and safety pass
- `land`: requires valid session and safety pass

## 4. Android rosbridge assumptions

The Android repository currently assumes rosbridge service responses expose:

- `values.success`
- `values.error_code`
- `values.message`

And for mission listing:

- `values.missions`

If rosbridge response wrapping changes, update:

- `D:\ROS2Android\android-app\DJI-App\app\src\main\java\com\example\uavmobile\data\repository\UavRepository.kt`

## 5. Error code reference

- `0`: success
- `1001`: invalid request
- `1002`: duplicate mission id
- `1003`: mission not found
- `2001`: flight-control link unavailable
- `2002`: GPS not ready
- `2003`: home not available
- `2004`: battery below threshold
- `2005`: mobile session expired
- `2006`: mode rejected
- `3001`: MAVROS call failed

## 6. Compatibility rules

- Android should not bypass `/mobile/*` by talking to `/uav/*` directly.
- Telemetry field semantics should remain stable once released to the mobile client.
- If a v2 contract is needed, prefer additive versioning instead of silently changing existing field meaning.
