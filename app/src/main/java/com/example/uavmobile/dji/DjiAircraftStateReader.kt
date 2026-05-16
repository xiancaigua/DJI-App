package com.example.uavmobile.dji

import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneConnectionState
import com.example.uavmobile.core.DroneState
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.manager.KeyManager

object DjiAircraftStateReader {
    fun getState(): Result<DroneState> {
        return runCatching {
            if (!DjiMsdkManager.isSdkPresent()) {
                return@runCatching DroneState(
                    backend = DroneBackend.DJI,
                    connectionState = DroneConnectionState.DISCONNECTED,
                    statusMessage = DjiMsdkManager.describeStatus(),
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

            val connected = keyManager.getValue(KeyTools.createKey(ProductKey.KeyConnection)) ?: false
            if (!connected) {
                return@runCatching DroneState(
                    backend = DroneBackend.DJI,
                    connectionState = DroneConnectionState.DISCONNECTED,
                    statusMessage = DjiConnectionManager.describeStatus(),
                )
            }

            val location = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D))
                ?: LocationCoordinate3D()
            val heading = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyCompassHeading)) ?: 0.0
            val flightMode = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyFlightMode))?.toString().orEmpty()
            val motorsOn = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyAreMotorsOn))
            val isFlying = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyIsFlying))
            val isHomeLocationSet = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyIsHomeLocationSet)) ?: false
            val homeLocation = if (isHomeLocationSet) {
                keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyHomeLocation))
            } else {
                null
            } ?: LocationCoordinate2D()
            val gpsSignalLevel = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel))
                ?.toString()
                .orEmpty()
            val gpsSatelliteCount = keyManager.getValue(KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount))

            DroneState(
                backend = DroneBackend.DJI,
                connectionState = DroneConnectionState.CONNECTED,
                latitude = location.latitude,
                longitude = location.longitude,
                homeLatitude = homeLocation.latitude.takeIf { isHomeLocationSet },
                homeLongitude = homeLocation.longitude.takeIf { isHomeLocationSet },
                altitudeMeters = location.altitude,
                headingDegrees = heading.toFloat(),
                flightMode = flightMode,
                motorsOn = motorsOn,
                isFlying = isFlying,
                isOnGround = isFlying?.not(),
                gpsSignalLevel = gpsSignalLevel,
                gpsSatelliteCount = gpsSatelliteCount,
                statusMessage = DjiConnectionManager.describeStatus(),
            )
        }
    }
}
