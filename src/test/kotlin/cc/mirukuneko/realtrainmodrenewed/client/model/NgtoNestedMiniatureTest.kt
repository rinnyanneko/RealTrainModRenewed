// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.client.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NgtoNestedMiniatureTest {
    @Test
    fun `composes legacy miniature scale and yaw in OpenGL order`() {
        val transform = NgtoModelGeometry.AffineTransform.translation(10f, 2f, 3f)
            .compose(NgtoModelGeometry.AffineTransform.rotateY(90f))
            .compose(NgtoModelGeometry.AffineTransform.scale(0.5f))

        val point = transform.point(2f, 0f, 0f)
        assertEquals(10f, point[0], 0.0001f)
        assertEquals(2f, point[1], 0.0001f)
        assertEquals(2f, point[2], 0.0001f)
    }
}
