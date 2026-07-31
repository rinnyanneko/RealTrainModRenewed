// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LegacyColorMetadataTest {
    @Test
    fun `canonicalizes coloured families with dye metadata`() {
        assertEquals("white_wool" to 14, LegacyColorMetadata.decodeBlockPath("red_wool"))
        assertEquals("white_stained_glass" to 11, LegacyColorMetadata.decodeBlockPath("blue_stained_glass"))
        assertEquals("stone" to 0, LegacyColorMetadata.decodeBlockPath("stone"))
    }
}
