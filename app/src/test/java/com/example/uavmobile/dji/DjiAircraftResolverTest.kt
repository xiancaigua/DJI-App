package com.example.uavmobile.dji

import com.example.uavmobile.core.DjiAircraftFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiAircraftResolverTest {
    @Test
    fun `manual m400 selection resolves to pm440`() {
        val resolution = DjiAircraftResolver.resolveFromSelection(DjiAircraftFamily.M400)

        assertTrue(resolution is DjiAircraftResolution.Supported)
        resolution as DjiAircraftResolution.Supported
        assertEquals(DjiWaylineAircraftType.PM440, resolution.resolvedAircraft)
    }

    @Test
    fun `manual matrice 4 series selection returns unsupported aircraft type`() {
        val resolution = DjiAircraftResolver.resolveFromSelection(DjiAircraftFamily.MATRICE_4_SERIES)

        assertTrue(resolution is DjiAircraftResolution.Unsupported)
        assertTrue(resolution.message.contains("UnsupportedAircraftType"))
    }

    @Test
    fun `auto without connected product returns missing target aircraft`() {
        val resolution = DjiAircraftResolver.resolveFromSelection(DjiAircraftFamily.AUTO)

        assertTrue(resolution is DjiAircraftResolution.Missing)
        assertTrue(resolution.message.contains("no connected product type", ignoreCase = true))
    }
}
