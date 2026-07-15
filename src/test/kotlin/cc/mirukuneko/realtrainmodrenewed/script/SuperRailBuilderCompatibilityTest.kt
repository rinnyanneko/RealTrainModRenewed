// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.script

import cc.mirukuneko.realtrainmodrenewed.entity.DATA_MAP_ALL_FLAGS
import cc.mirukuneko.realtrainmodrenewed.entity.DATA_MAP_SAVE_FLAG
import cc.mirukuneko.realtrainmodrenewed.entity.DATA_MAP_SYNC_FLAG
import cc.mirukuneko.realtrainmodrenewed.entity.dataMapBoolean
import cc.mirukuneko.realtrainmodrenewed.entity.dataMapDouble
import cc.mirukuneko.realtrainmodrenewed.entity.dataMapInt
import cc.mirukuneko.realtrainmodrenewed.entity.dataMapString
import cc.mirukuneko.realtrainmodrenewed.entity.shouldSaveDataMap
import cc.mirukuneko.realtrainmodrenewed.entity.shouldSyncDataMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SuperRailBuilderCompatibilityTest {
    @Test
    fun `car exposes legacy resource state accessors`() {
        val loader = javaClass.classLoader
        val carClass = assertNotNull(
            loader.getResourceAsStream("cc/mirukuneko/realtrainmodrenewed/entity/CarEntity.class")
        ).use { it.readBytes().toString(Charsets.ISO_8859_1) }
        val resourceStateClass = assertNotNull(
            loader.getResourceAsStream(
                "cc/mirukuneko/realtrainmodrenewed/entity/CarEntity\$ResourceStateCompat.class"
            )
        ).use { it.readBytes().toString(Charsets.ISO_8859_1) }
        val dataMapClass = assertNotNull(
            loader.getResourceAsStream(
                "cc/mirukuneko/realtrainmodrenewed/entity/CarEntity\$DataMapCompat.class"
            )
        ).use { it.readBytes().toString(Charsets.ISO_8859_1) }

        assertTrue(carClass.contains("getResourceState"))
        assertTrue(resourceStateClass.contains("getDataMap"))
        assertTrue(resourceStateClass.contains("getResourceName"))
        assertTrue(dataMapClass.contains("(Ljava/lang/String;Ljava/lang/Object;I)V"))
    }

    @Test
    fun `data map sync and save flags remain independent`() {
        assertEquals(1, DATA_MAP_SYNC_FLAG)
        assertEquals(2, DATA_MAP_SAVE_FLAG)
        assertTrue(shouldSyncDataMap(DATA_MAP_SYNC_FLAG))
        assertFalse(shouldSaveDataMap(DATA_MAP_SYNC_FLAG))
        assertFalse(shouldSyncDataMap(DATA_MAP_SAVE_FLAG))
        assertTrue(shouldSaveDataMap(DATA_MAP_SAVE_FLAG))
        assertTrue(shouldSyncDataMap(DATA_MAP_ALL_FLAGS))
        assertTrue(shouldSaveDataMap(DATA_MAP_ALL_FLAGS))
    }

    @Test
    fun `legacy data map accepts script number and boolean values`() {
        assertEquals("7", dataMapString(7))
        assertEquals("1200", dataMapString(1200L))
        assertEquals("", dataMapString(null))
        assertTrue(dataMapBoolean(1))
        assertFalse(dataMapBoolean(0))
        assertEquals(2, dataMapInt(2.6))
        assertEquals(2.5, dataMapDouble("2.5"))
    }

    @Test
    fun `server script receives native bridge overrides`() {
        val script =
            """
            var SuperRailBuilderVersion = "1.4";
            function buildNormalRail() {}
            function buildBranchRail() {}
            function deleteRail() {}
            function deleteRailRP() {}
            function setBlock() {}
            function getSelectedSlotItem() {}
            function hasPlayerMarker() {}
            function getPlayerRail() {}
            function doFollowing() {}
            function getTileEntity() {}
            function getTileEntityPos() {}
            """.trimIndent()

        val compatible = assertNotNull(TrainScriptSystem.appendSuperRailBuilderOverrides(script))
        assertTrue(compatible.lastIndexOf("buildNormalRail = function") > script.length)
        assertTrue(compatible.lastIndexOf("buildBranchRail = function") > script.length)
        assertTrue(compatible.lastIndexOf("deleteRail = function") > script.length)
        assertTrue(compatible.lastIndexOf("setBlock = function") > script.length)
        assertTrue(compatible.lastIndexOf("getTileEntity = function") > script.length)
    }

    @Test
    fun `rail position bridge preserves SuperRailBuilder geometry`() {
        val railPosition = SrbRailBridge().createRailPosition(
            blockX = 12,
            blockY = 64,
            blockZ = -8,
            markerDir = 3,
            switchType = 1.0,
            anchorLength = 24.5,
            anchorPitch = 2.25,
            anchorYaw = 135.0,
            cantCenter = 1.5,
            cantEdge = -3.0,
            height = 4.0,
        )

        assertEquals(12, railPosition.blockX)
        assertEquals(64, railPosition.blockY)
        assertEquals(-8, railPosition.blockZ)
        assertEquals(3, railPosition.direction.toInt())
        assertEquals(1, railPosition.switchType.toInt())
        assertEquals(24.5f, railPosition.anchorLengthHorizontal)
        assertEquals(24.5f, railPosition.anchorLengthVertical)
        assertEquals(2.25f, railPosition.anchorPitch)
        assertEquals(135.0f, railPosition.anchorYaw)
        assertEquals(1.5f, railPosition.cantCenter)
        assertEquals(-3.0f, railPosition.cantEdge)
        assertEquals(64.3125, railPosition.posY)
    }
}
