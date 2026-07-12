// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix4f

/** Writes transformed vertices without allocating a temporary Vector3f per vertex. */
internal object VertexWriter {
    fun addVertex(consumer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, z: Float): VertexConsumer =
        consumer.addVertex(
            matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30(),
            matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31(),
            matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32(),
        )
}
