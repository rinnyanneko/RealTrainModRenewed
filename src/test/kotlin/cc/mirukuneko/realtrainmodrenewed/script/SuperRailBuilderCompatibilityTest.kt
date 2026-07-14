// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SuperRailBuilderCompatibilityTest {
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
