package com.example.uavmobile.dji

import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneConnectionState
import com.example.uavmobile.core.DroneState
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
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

            DroneState(
                backend = DroneBackend.DJI,
                connectionState = DroneConnectionState.CONNECTED,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = location.altitude,
                headingDegrees = heading.toFloat(),
                statusMessage = DjiConnectionManager.describeStatus(),
            )
        }
    }
}
