package com.example.uavmobile.data.repository

import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.model.ConnectionStatus
import com.example.uavmobile.data.model.MobileEvent
import com.example.uavmobile.data.model.MissionSummary
import com.example.uavmobile.data.model.MissionWaypoint
import com.example.uavmobile.data.model.TelemetrySnapshot
import com.example.uavmobile.data.rosbridge.RosbridgeClient
import com.example.uavmobile.debug.DeveloperLogStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class UavRepository(
    private val rosbridgeClient: RosbridgeClient = RosbridgeClient(),
) {
    private companion object {
        private const val LOG_SOURCE = "UavRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientId = "android-${System.currentTimeMillis()}"

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    private val _events = MutableStateFlow<List<MobileEvent>>(emptyList())
    val events: StateFlow<List<MobileEvent>> = _events.asStateFlow()

    private val _missions = MutableStateFlow<List<MissionSummary>>(emptyList())
    val missions: StateFlow<List<MissionSummary>> = _missions.asStateFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = rosbridgeClient.connectionStatus

    private var heartbeatJob: Job? = null
    private var sessionPrimed = false

    init {
        scope.launch {
            rosbridgeClient.publications.collect { publication ->
                when (publication.topic) {
                    "/mobile/telemetry" -> _telemetry.value = publication.payload.toTelemetrySnapshot()
                    "/mobile/events" -> {
                        val event = publication.payload.toMobileEvent()
                        prependEvent(event)
                        DeveloperLogStore.info(
                            LOG_SOURCE,
                            "ROS event ${event.code}",
                            "${event.level}: ${event.message}",
                        )
                    }
                }
            }
        }

        scope.launch {
            rosbridgeClient.connectionStatus.collect { status ->
                when (status) {
                    ConnectionStatus.CONNECTED -> {
                        DeveloperLogStore.info(LOG_SOURCE, "ROS bridge connected")
                        if (!sessionPrimed) {
                            sessionPrimed = true
                            rosbridgeClient.subscribe("/mobile/telemetry", throttleRateMs = 200)
                            rosbridgeClient.subscribe("/mobile/events")
                            rosbridgeClient.advertise("/mobile/session/heartbeat", "mobile_contract/SessionHeartbeat")
                            startHeartbeatLoop()
                            refreshMissions()
                        }
                    }

                    ConnectionStatus.DISCONNECTED,
                    ConnectionStatus.FAILED,
                    ConnectionStatus.CONNECTING,
                    -> {
                        DeveloperLogStore.warn(LOG_SOURCE, "ROS bridge status changed", status.name)
                        if (status != ConnectionStatus.CONNECTED) {
                            sessionPrimed = false
                            heartbeatJob?.cancel()
                        }
                    }
                }
            }
        }
    }

    fun connect(config: ConnectionConfig) {
        DeveloperLogStore.info(LOG_SOURCE, "Connecting to ROS bridge", config.websocketUrl)
        rosbridgeClient.connect(config.websocketUrl)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        sessionPrimed = false
        DeveloperLogStore.info(LOG_SOURCE, "Disconnecting ROS bridge")
        rosbridgeClient.disconnect()
    }

    suspend fun refreshMissions(): ActionResult {
        DeveloperLogStore.debug(LOG_SOURCE, "Refreshing ROS mission list")
        val response = rosbridgeClient.callService("/mobile/mission/list")
        val result = response.toActionResult()
        if (result.success) {
            val values = response.optJSONObject("values") ?: JSONObject()
            val missionsJson = values.optJSONArray("missions") ?: JSONArray()
            _missions.value = missionsJson.toMissionList()
            DeveloperLogStore.info(LOG_SOURCE, "ROS mission list refreshed", "count=${_missions.value.size}")
        } else {
            DeveloperLogStore.warn(LOG_SOURCE, "ROS mission list refresh failed", result.message)
        }
        return result
    }

    suspend fun uploadMission(missionId: String, waypoints: List<MissionWaypoint>): ActionResult {
        DeveloperLogStore.info(LOG_SOURCE, "Uploading ROS mission", "missionId=$missionId, waypoints=${waypoints.size}")
        val payload = JSONObject()
            .put("mission_id", missionId)
            .put("waypoints", JSONArray().apply {
                waypoints.forEach { waypoint ->
                    put(
                        JSONObject()
                            .put("lat", waypoint.lat)
                            .put("lon", waypoint.lon)
                            .put("alt_m", waypoint.altM)
                            .put("hold_sec", waypoint.holdSec)
                            .put("yaw_deg", waypoint.yawDeg),
                    )
                }
            })
        val response = rosbridgeClient.callService("/mobile/mission/upload", payload)
        val result = response.toActionResult()
        if (result.success) {
            refreshMissions()
        } else {
            DeveloperLogStore.warn(LOG_SOURCE, "ROS mission upload failed", result.message)
        }
        return result
    }

    suspend fun startMission(missionId: String): ActionResult = missionCommand("/mobile/mission/start", missionId)

    suspend fun pauseMission(missionId: String): ActionResult = missionCommand("/mobile/mission/pause", missionId)

    suspend fun resumeMission(missionId: String): ActionResult = missionCommand("/mobile/mission/resume", missionId)

    suspend fun rtl(missionId: String): ActionResult = missionCommand("/mobile/mission/rtl", missionId)

    suspend fun land(missionId: String): ActionResult = missionCommand("/mobile/mission/land", missionId)

    private suspend fun missionCommand(serviceName: String, missionId: String): ActionResult {
        DeveloperLogStore.info(LOG_SOURCE, "Sending ROS mission command", "$serviceName missionId=$missionId")
        val response = rosbridgeClient.callService(
            service = serviceName,
            args = JSONObject().put("mission_id", missionId),
        )
        val result = response.toActionResult()
        if (result.success) {
            refreshMissions()
        } else {
            DeveloperLogStore.warn(LOG_SOURCE, "ROS mission command failed", "$serviceName -> ${result.message}")
        }
        return result
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                rosbridgeClient.publish(
                    "/mobile/session/heartbeat",
                    JSONObject()
                        .put("header", JSONObject())
                        .put("client_id", clientId),
                )
                delay(2000)
            }
        }
    }

    private fun prependEvent(event: MobileEvent) {
        _events.value = listOf(event) + _events.value.take(49)
    }

    private fun JSONObject.toTelemetrySnapshot(): TelemetrySnapshot {
        return TelemetrySnapshot(
            flightMode = optString("flight_mode", "UNKNOWN"),
            armed = optBoolean("armed", false),
            connected = optBoolean("connected", false),
            gpsFixType = optInt("gps_fix_type", 0),
            satellitesVisible = optInt("satellites_visible", 0),
            batteryPercent = optDouble("battery_percent", 0.0).toFloat(),
            batteryVoltage = optDouble("battery_voltage", 0.0).toFloat(),
            latitude = optDouble("latitude", 0.0),
            longitude = optDouble("longitude", 0.0),
            homeLatitude = optDouble("home_latitude", 0.0),
            homeLongitude = optDouble("home_longitude", 0.0),
            homeAvailable = optBoolean("home_available", false),
            relativeAltitudeM = optDouble("relative_altitude_m", 0.0),
            headingDeg = optDouble("heading_deg", 0.0).toFloat(),
            groundSpeedMps = optDouble("ground_speed_mps", 0.0).toFloat(),
            missionStage = optString("mission_stage", "IDLE"),
            missionProgress = optDouble("mission_progress", 0.0).toFloat(),
            sessionActive = optBoolean("session_active", false),
            latestAlert = optString("latest_alert", ""),
        )
    }

    private fun JSONObject.toMobileEvent(): MobileEvent {
        return MobileEvent(
            level = optString("level", "INFO"),
            code = optString("code", "UNKNOWN"),
            errorCode = optInt("error_code", 0),
            message = optString("message", ""),
            relatedMissionId = optString("related_mission_id", ""),
            receivedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
        )
    }

    private fun JSONArray.toMissionList(): List<MissionSummary> {
        return buildList {
            for (index in 0 until length()) {
                val mission = optJSONObject(index) ?: continue
                add(
                    MissionSummary(
                        missionId = mission.optString("mission_id"),
                        waypointCount = mission.optInt("waypoint_count"),
                        status = mission.optString("status"),
                        progress = mission.optDouble("progress", 0.0).toFloat(),
                    ),
                )
            }
        }
    }

    private fun JSONObject.toActionResult(): ActionResult {
        val values = optJSONObject("values") ?: JSONObject()
        return ActionResult(
            success = values.optBoolean("success", false),
            message = values.optString("message", "No message"),
            errorCode = values.optInt("error_code", -1),
        )
    }
}
