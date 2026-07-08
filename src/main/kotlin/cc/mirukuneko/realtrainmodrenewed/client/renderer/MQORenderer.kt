package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.client.model.mqo.MQOModel
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix4f

/**
 * MQO model direct immediate/VBO rendering is currently disabled.
 * Models use the buffered path via MqoModelLoader instead.
 * See docs/PORTING-1.21.1-NEOFORGE.md "Known Compatibility Gaps".
 */
object MQORenderer {
    @JvmStatic
    fun render(model: MQOModel, buffer: VertexConsumer, matrix: Matrix4f, light: Int) {
        throw UnsupportedOperationException(
            "MQO direct rendering is disabled. Use the buffered path via MqoModelLoader."
        )
    }
}
