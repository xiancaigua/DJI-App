package com.example.uavmobile.dji

import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneConnectionState
import com.example.uavmobile.core.DroneState
import com.example.uavmobile.debug.DeveloperLogStore
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.DJIKeyInfo
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.Velocity3D
import dji.v5.manager.KeyManager
import kotlin.math.sqrt

object DjiAircraftStateReader {
    private const val TAG = "DjiAircraftStateReader"
    private const val SUMMARY_LOG_INTERVAL_MS = 15_000L

    @Volatile
    private var lastSummaryLog: String = ""

    @Volatile
    private var lastSummaryLogAt: Long = 0L

    fun getState(): Result<DroneState> {
        return runCatching {
            if (!DjiMsdkManager.isSdkPresent()) {
                return@runCatching DroneState(
                    backend = DroneBackend.DJI,
                    connectionState = DroneConnectionState.DISCONNECTED,
                    statusMessage = DjiMsdkManager.describeStatus(),
                )
            }

            val connectionSnapshot = DjiConnectionManager.connectionState.value
            if (!connectionSnapshot.connected) {
                return@runCatching DroneState(
                    backend = DroneBackend.DJI,
                    connectionState = DroneConnectionState.DISCONNECTED,
                    statusMessage = connectionSnapshot.statusMessage,
                )
            }

            val keyManager = KeyManager.getInstance()
            if (keyManager == null) {
                return@runCatching DroneState(
                    backend = DroneBackend.DJI,
                    connectionState = DroneConnectionState.DISCONNECTED,
                    statusMessage = "DJI KeyManager is not available",
                )
            }

            val telemetryErrors = mutableListOf<String>()
            val statusNotes = mutableListOf<String>()

            val location = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyAircraftLocation3D,
                keyName = "FlightControllerKey.KeyAircraftLocation3D",
                errorBucket = telemetryErrors,
            )
            val heading = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyCompassHeading,
                keyName = "FlightControllerKey.KeyCompassHeading",
                errorBucket = telemetryErrors,
            )
            val flightMode = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyFlightMode,
                keyName = "FlightControllerKey.KeyFlightMode",
                errorBucket = telemetryErrors,
            )?.toString().orEmpty()
            val motorsOn = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyAreMotorsOn,
                keyName = "FlightControllerKey.KeyAreMotorsOn",
                errorBucket = telemetryErrors,
            )
            val isFlying = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyIsFlying,
                keyName = "FlightControllerKey.KeyIsFlying",
                errorBucket = telemetryErrors,
            )
            val isSimulatorStarted = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyIsSimulatorStarted,
                keyName = "FlightControllerKey.KeyIsSimulatorStarted",
                errorBucket = telemetryErrors,
            )
            val isHomeLocationSet = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyIsHomeLocationSet,
                keyName = "FlightControllerKey.KeyIsHomeLocationSet",
                errorBucket = telemetryErrors,
            ) ?: false
            val homeLocation = if (isHomeLocationSet) {
                readKey(
                    keyManager = keyManager,
                    keyInfo = FlightControllerKey.KeyHomeLocation,
                    keyName = "FlightControllerKey.KeyHomeLocation",
                    errorBucket = telemetryErrors,
                )
            } else {
                null
            }
            val gpsSignalLevel = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyGPSSignalLevel,
                keyName = "FlightControllerKey.KeyGPSSignalLevel",
                errorBucket = telemetryErrors,
            )?.toString().orEmpty()
            val gpsSatelliteCount = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyGPSSatelliteCount,
                keyName = "FlightControllerKey.KeyGPSSatelliteCount",
                errorBucket = telemetryErrors,
            )
            val aircraftVelocity = readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyAircraftVelocity,
                keyName = "FlightControllerKey.KeyAircraftVelocity",
                errorBucket = telemetryErrors,
            )
            val batteryPercentValue = readKey(
                keyManager = keyManager,
                keyInfo = BatteryKey.KeyChargeRemainingInPercent,
                keyName = "BatteryKey.KeyChargeRemainingInPercent",
                errorBucket = telemetryErrors,
            ) ?: readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyBatteryPowerPercent,
                keyName = "FlightControllerKey.KeyBatteryPowerPercent",
                errorBucket = telemetryErrors,
            )
            val batteryVoltageValue = readKey(
                keyManager = keyManager,
                keyInfo = BatteryKey.KeyVoltage,
                keyName = "BatteryKey.KeyVoltage",
                errorBucket = telemetryErrors,
            ) ?: readKey(
                keyManager = keyManager,
                keyInfo = FlightControllerKey.KeyBatteryVoltage,
                keyName = "FlightControllerKey.KeyBatteryVoltage",
                errorBucket = telemetryErrors,
            )

            val locationReadError = when {
                location == null -> "DJI 已连接，但 KeyAircraftLocation3D 暂无数据"
                else -> ""
            }
            if (locationReadError.isNotBlank()) {
                statusNotes += locationReadError
            }

            val latitude = location?.latitude
            val longitude = location?.longitude
            val altitudeMeters = location?.altitude
            if (latitude == 0.0 && longitude == 0.0) {
                statusNotes += "位置值疑似无效：0,0"
            }
            if (isSimulatorStarted == true) {
                statusNotes += "当前为模拟器模式"
            }

            val batteryPercent = batteryPercentValue?.let { (it / 100f).coerceIn(0f, 1f) }
            val batteryVoltage = normalizeBatteryVoltage(batteryVoltageValue)
            val groundSpeedMps = aircraftVelocity?.let(::calculateGroundSpeed)
            val verticalSpeedMps = aircraftVelocity?.z?.toFloat()

            if (aircraftVelocity == null) {
                DeveloperLogStore.info(
                    TAG,
                    "速度 Key 暂无数据",
                    "FlightControllerKey.KeyAircraftVelocity 返回 null，groundSpeedMps 保持为空",
                )
            }

            val telemetryReadSucceeded = listOf(
                heading,
                motorsOn,
                isFlying,
                gpsSatelliteCount,
                gpsSignalLevel.takeIf { it.isNotBlank() },
                batteryPercentValue,
                batteryVoltageValue,
                aircraftVelocity,
            ).any { it != null }
            val telemetryReadError = telemetryErrors.joinToString(separator = "；")
            val statusMessage = buildList {
                add(DjiConnectionManager.describeStatus())
                addAll(statusNotes)
                if (telemetryReadError.isNotBlank()) {
                    add("部分 Key 读取失败：$telemetryReadError")
                }
            }.joinToString(separator = "；").ifBlank { "DJI 状态正常" }

            maybeLogStateSummary(
                "mode=${flightMode.ifBlank { "无" }}, " +
                    "lat=${latitude?.toString() ?: "null"}, " +
                    "lon=${longitude?.toString() ?: "null"}, " +
                    "alt=${altitudeMeters?.toString() ?: "null"}, " +
                    "heading=${heading?.toString() ?: "null"}, " +
                    "gpsSignalLevel=${gpsSignalLevel.ifBlank { "无" }}, " +
                    "gpsSatelliteCount=${gpsSatelliteCount?.toString() ?: "null"}, " +
                    "batteryPercent=${batteryPercent?.toString() ?: "null"}, " +
                    "batteryVoltage=${batteryVoltage?.toString() ?: "null"}, " +
                    "isFlying=${isFlying?.toString() ?: "null"}",
            )

            DroneState(
                backend = DroneBackend.DJI,
                connectionState = DroneConnectionState.CONNECTED,
                latitude = latitude,
                longitude = longitude,
                homeLatitude = homeLocation?.latitude?.takeIf { isHomeLocationSet },
                homeLongitude = homeLocation?.longitude?.takeIf { isHomeLocationSet },
                altitudeMeters = altitudeMeters,
                headingDegrees = heading?.toFloat(),
                flightMode = flightMode,
                motorsOn = motorsOn,
                isFlying = isFlying,
                isOnGround = isFlying?.not(),
                isSimulatorStarted = isSimulatorStarted,
                gpsSignalLevel = gpsSignalLevel,
                gpsSatelliteCount = gpsSatelliteCount,
                batteryPercent = batteryPercent,
                batteryVoltage = batteryVoltage,
                groundSpeedMps = groundSpeedMps,
                verticalSpeedMps = verticalSpeedMps,
                locationReadSucceeded = location != null,
                locationReadError = locationReadError,
                telemetryReadSucceeded = telemetryReadSucceeded,
                telemetryReadError = telemetryReadError,
                statusMessage = statusMessage,
            )
        }
    }

    private fun <T> readKey(
        keyManager: KeyManager,
        keyInfo: DJIKeyInfo<T>,
        keyName: String,
        errorBucket: MutableList<String>,
    ): T? {
        return runCatching {
            keyManager.getValue(KeyTools.createKey(keyInfo))
        }.onFailure { throwable ->
            val message = "$keyName 读取失败: ${throwable.message ?: throwable::class.java.simpleName}"
            errorBucket += message
            DeveloperLogStore.warn(TAG, "DJI Key 读取失败", message)
        }.getOrNull()
    }

    private fun calculateGroundSpeed(velocity3D: Velocity3D): Float? {
        val x = velocity3D.x ?: return null
        val y = velocity3D.y ?: return null
        return sqrt((x * x) + (y * y)).toFloat()
    }

    private fun normalizeBatteryVoltage(rawVoltage: Int?): Float? {
        rawVoltage ?: return null
        return if (rawVoltage >= 1000) rawVoltage / 1000f else rawVoltage.toFloat()
    }

    private fun maybeLogStateSummary(summary: String) {
        val now = System.currentTimeMillis()
        val shouldLog = summary != lastSummaryLog || now - lastSummaryLogAt >= SUMMARY_LOG_INTERVAL_MS
        if (!shouldLog) {
            return
        }
        lastSummaryLog = summary
        lastSummaryLogAt = now
        DeveloperLogStore.info(TAG, "DJI 基础状态读取成功", summary)
    }
}
