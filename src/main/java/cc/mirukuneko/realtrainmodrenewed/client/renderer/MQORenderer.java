package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import cc.mirukuneko.realtrainmodrenewed.client.model.mqo.MQOModel;
import org.joml.Matrix4f;

/**
 * MQO model direct immediate/VBO rendering is currently disabled.
 * Models use the buffered path via MqoModelLoader instead.
 * See docs/PORTING-1.21.1-NEOFORGE.md "Known Compatibility Gaps".
 */
public final class MQORenderer {
    public static void render(MQOModel model, VertexConsumer buffer, Matrix4f matrix, int light) {
        throw new UnsupportedOperationException(
            "MQO direct rendering is disabled. Use the buffered path via MqoModelLoader.");
    }
}
