// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.client.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NgtoFormatTest {
    @Test
    fun `recognizes both NGTO container formats`() {
        assertTrue(NgtoFormat.isModelPath("models/builder.ngto"))
        assertTrue(NgtoFormat.isModelPath("models/parts.NGTZ"))
        assertFalse(NgtoFormat.isModelPath("models/body.mqo"))
    }

    @Test
    fun `restores NGTO signed byte palette offset`() {
        assertArrayEquals(intArrayOf(0, 1, 127, 128, 255), NgtoFormat.decodeLegacyByteIds(byteArrayOf(-128, -127, -1, 0, 127)))
    }
}
