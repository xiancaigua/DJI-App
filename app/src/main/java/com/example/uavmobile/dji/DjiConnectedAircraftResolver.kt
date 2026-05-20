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
                message = "已将 DJI_MATRICE_400 解析为 WaylineDroneType.PM440",
            )

            ProductType.DJI_MATRICE_4_SERIES -> DjiAircraftResolution.Supported(
                requestedFamily = DjiAircraftFamily.MATRICE_4_SERIES,
                resolvedProductTypeName = productType.name,
                resolvedAircraft = DjiWaylineAircraftType.EA220,
                message = "已将 DJI_MATRICE_4_SERIES 解析为 WaylineDroneType.EA220",
            )

            ProductType.DJI_MATRICE_4D_SERIES -> DjiAircraftResolution.Supported(
                requestedFamily = DjiAircraftFamily.MATRICE_4_SERIES,
                resolvedProductTypeName = productType.name,
                resolvedAircraft = DjiWaylineAircraftType.EA230,
                message = "已将 DJI_MATRICE_4D_SERIES 解析为 WaylineDroneType.EA230",
            )

            else -> DjiAircraftResolution.Unsupported(
                requestedFamily = DjiAircraftFamily.AUTO,
                resolvedProductTypeName = productType.name,
                message = "当前连接的 DJI 产品 $productType 暂未在本应用中启用航点生成。",
            )
        }
    }
}
