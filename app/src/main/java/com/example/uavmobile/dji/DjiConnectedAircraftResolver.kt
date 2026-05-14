package com.example.uavmobile.dji

import com.example.uavmobile.core.DjiAircraftFamily
import dji.sdk.keyvalue.value.product.ProductType

object DjiConnectedAircraftResolver {
    const val MATRICE_4_TODO =
        "UnsupportedAircraftType: Matrice 4 Series needs a confirmed WaylineDroneType from DJI SDK/API reference and real hardware before mission generation can be enabled."

    fun resolve(productType: ProductType): DjiAircraftResolution {
        return when (productType) {
            ProductType.DJI_MATRICE_400 -> DjiAircraftResolution.Supported(
                requestedFamily = DjiAircraftFamily.M400,
                resolvedProductTypeName = productType.name,
                resolvedAircraft = DjiWaylineAircraftType.PM440,
                message = "Resolved DJI_MATRICE_400 to WaylineDroneType.PM440",
            )

            ProductType.DJI_MATRICE_4_SERIES,
            ProductType.DJI_MATRICE_4D_SERIES,
            -> DjiAircraftResolution.Unsupported(
                requestedFamily = DjiAircraftFamily.MATRICE_4_SERIES,
                resolvedProductTypeName = productType.name,
                message = MATRICE_4_TODO,
            )

            else -> DjiAircraftResolution.Unsupported(
                requestedFamily = DjiAircraftFamily.AUTO,
                resolvedProductTypeName = productType.name,
                message = "UnsupportedAircraftType: connected DJI product $productType is not enabled for waypoint generation in this app yet.",
            )
        }
    }
}
