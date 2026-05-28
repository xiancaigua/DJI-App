package com.example.uavmobile.dji

import com.example.uavmobile.core.DjiAircraftFamily
import dji.sdk.keyvalue.value.product.ProductType

enum class DjiWaylineAircraftType {
    PM440,
    EA220,
    WA345,
    EA230,
}

sealed interface DjiAircraftResolution {
    val message: String

    data class Supported(
        val requestedFamily: DjiAircraftFamily,
        val resolvedProductTypeName: String?,
        val resolvedAircraft: DjiWaylineAircraftType,
        override val message: String,
    ) : DjiAircraftResolution

    data class Unsupported(
        val requestedFamily: DjiAircraftFamily,
        val resolvedProductTypeName: String?,
        override val message: String,
    ) : DjiAircraftResolution

    data class Missing(
        val requestedFamily: DjiAircraftFamily,
        val resolvedProductTypeName: String?,
        override val message: String,
    ) : DjiAircraftResolution
}

object DjiAircraftResolver {
    fun resolve(
        currentProductType: ProductType?,
        selectedAircraftFamily: DjiAircraftFamily,
    ): DjiAircraftResolution {
        return if (currentProductType != null) {
            DjiConnectedAircraftResolver.resolve(currentProductType)
        } else {
            resolveFromManualSelection(selectedAircraftFamily)
        }
    }

    fun resolveFromSelection(
        selectedAircraftFamily: DjiAircraftFamily,
    ): DjiAircraftResolution {
        return resolveFromManualSelection(selectedAircraftFamily)
    }

    private fun resolveFromManualSelection(
        selectedAircraftFamily: DjiAircraftFamily,
    ): DjiAircraftResolution {
        return when (selectedAircraftFamily) {
            DjiAircraftFamily.AUTO -> DjiAircraftResolution.Missing(
                requestedFamily = selectedAircraftFamily,
                resolvedProductTypeName = null,
                message = "DJI target aircraft is AUTO but no connected product type is available. Connect the aircraft or manually choose the target family.",
            )

            DjiAircraftFamily.M400 -> DjiAircraftResolution.Supported(
                requestedFamily = selectedAircraftFamily,
                resolvedProductTypeName = null,
                resolvedAircraft = DjiWaylineAircraftType.PM440,
                message = "Using manual DJI aircraft selection M400 -> WaylineDroneType.PM440",
            )

            DjiAircraftFamily.MATRICE_4_SERIES -> DjiAircraftResolution.Supported(
                requestedFamily = selectedAircraftFamily,
                resolvedProductTypeName = null,
                resolvedAircraft = DjiWaylineAircraftType.WA345,
                message = "Using manual DJI aircraft selection Matrice 4 Series -> WaylineDroneType.WA345",
            )
        }
    }
}
