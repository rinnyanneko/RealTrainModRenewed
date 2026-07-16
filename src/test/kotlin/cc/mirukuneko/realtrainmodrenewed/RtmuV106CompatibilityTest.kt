// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.client.model.animatedGifFrameIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RtmuV106CompatibilityTest {
    @Test
    fun `vehicle definitions expose RTMU type sign fields`() {
        val bytes = classText("cc/mirukuneko/realtrainmodrenewed/vehicle/VehicleDefinition.class")

        assertTrue(bytes.contains("getTypeSignNames"))
        assertTrue(bytes.contains("getTypeSignTexture"))
        assertTrue(bytes.contains("getTypeSigns"))
        assertTrue(bytes.contains("setTypeSign"))
    }

    @Test
    fun `train state twelve is synchronized and persisted as type sign`() {
        val bytes = classText("cc/mirukuneko/realtrainmodrenewed/entity/TrainEntity.class")

        assertTrue(bytes.contains("getTypeSignIndex"))
        assertTrue(bytes.contains("setTypeSignIndexForFormation"))
        assertTrue(bytes.contains("TypeSignIndex"))
        val dataMap = classText("cc/mirukuneko/realtrainmodrenewed/entity/TrainEntity\$DataMapCompat.class")
        assertTrue(dataMap.contains("typeSignId"))
    }

    @Test
    fun `animated pack signs and ride camera hooks are present`() {
        val modelLoader = classText("cc/mirukuneko/realtrainmodrenewed/client/model/MqoModelLoader.class")
        val renderer = classText("cc/mirukuneko/realtrainmodrenewed/client/renderer/TrainEntityRenderer.class")
        val camera = classText("cc/mirukuneko/realtrainmodrenewed/client/RideCameraEvents.class")

        assertTrue(modelLoader.contains("resolvePackTextureByTick"))
        assertTrue(renderer.contains("renderConfiguredTypeSigns"))
        assertTrue(camera.contains("MouseScrollingEvent"))
        assertTrue(camera.contains("CalculateDetachedCameraDistanceEvent"))
    }

    @Test
    fun `legacy script state aliases include RTMU type state`() {
        val scriptSystem = classText("cc/mirukuneko/realtrainmodrenewed/script/TrainScriptSystem\$Companion.class")

        assertTrue(scriptSystem.contains("State_Type"))
        assertTrue(scriptSystem.contains("Type: 12"))
    }

    @Test
    fun `animated gif timing honors per-frame delays and loops`() {
        val cumulativeMs = intArrayOf(100, 350, 400)

        assertEquals(0, animatedGifFrameIndex(cumulativeMs, 0))
        assertEquals(0, animatedGifFrameIndex(cumulativeMs, 99))
        assertEquals(1, animatedGifFrameIndex(cumulativeMs, 100))
        assertEquals(1, animatedGifFrameIndex(cumulativeMs, 349))
        assertEquals(2, animatedGifFrameIndex(cumulativeMs, 350))
        assertEquals(0, animatedGifFrameIndex(cumulativeMs, 400))
        assertEquals(2, animatedGifFrameIndex(cumulativeMs, -1))
    }

    private fun classText(path: String): String =
        assertNotNull(javaClass.classLoader.getResourceAsStream(path)).use {
            it.readBytes().toString(Charsets.ISO_8859_1)
        }
}
