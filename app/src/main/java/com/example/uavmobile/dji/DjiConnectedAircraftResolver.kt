package com.example.uavmobile.dji

import com.example.uavmobile.core.DjiAircraftFamily
import dji.sdk.keyvalue.value.product.ProductType

object DjiConnectedAircraftResolver {
    fun resolve(productType: ProductType): DjiAircraftResolution {
        return when (productType) {
            ProductType.DJI_MATRICE_400 -> DjiAircraftResolution.Supported(
                requestedFamily = DjiAircraftFamily.M400,
                resolvedProductTypeName = productType.name,
                resolvedAircraft = DjiWaylineAircraftType.PM440,
                message = "Resolved DJI_MATRICE_400 to WaylineDroneType.PM440",
            )

            ProductType.DJI_MATRICE_4_SERIES -> DjiAircraftResolution.Supported(
                requestedFamily = DjiAircraftFamily.MATRICE_4_SERIES,
                resolvedProductTypeName = productType.name,
                resolvedAircraft = DjiWaylineAircraftType.WA345,
                message = "Resolved DJI_MATRICE_4_SERIES to WaylineDroneType.WA345",
            )

            ProductType.DJI_MATRICE_4D_SERIES -> DjiAircraftResolution.Supported(
                requestedFamily = DjiAircraftFamily.MATRICE_4_SERIES,
                resolvedProductTypeName = productType.name,
                resolvedAircraft = DjiWaylineAircraftType.EA230,
                message = "Resolved DJI_MATRICE_4D_SERIES to WaylineDroneType.EA230",
            )

            else -> DjiAircraftResolution.Unsupported(
                requestedFamily = DjiAircraftFamily.AUTO,
                resolvedProductTypeName = productType.name,
                message = "UnsupportedAircraftType: connected DJI product $productType is not enabled for waypoint generation in this app yet.",
            )
        }
    }
}
