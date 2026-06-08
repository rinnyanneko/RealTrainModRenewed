package cc.mirukuneko.realtrainmodrenewed.util

import kotlin.test.Test
import kotlin.test.assertEquals

class UnitConverterTest {
    @Test
    fun convertsKilometresPerHourToBlocksPerTick() {
        assertEquals(0.05f, kph2bpt(3.6f), EPSILON)
        assertEquals(1.0f, kph2bpt(72.0f), EPSILON)
    }

    @Test
    fun convertsCentimetresToMetres() {
        assertEquals(1.0f, cm2m(100.0f), EPSILON)
        assertEquals(0.375f, cm2m(37.5f), EPSILON)
    }

    @Test
    fun convertsSecondsToTicks() {
        assertEquals(20.0f, s2t(1.0f), EPSILON)
        assertEquals(2.5f, s2t(0.125f), EPSILON)
    }

    @Test
    fun convertsMetresPerSecondSquaredToBlocksPerTickSquared() {
        assertEquals(0.01f, mpss2bpts(4.0f), EPSILON)
        assertEquals(0.0025f, mpss2bpts(1.0f), EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.000001f
    }
}
