package jp.kaiz.atsassistmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems;
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a translucent locator beam above a ground unit while the player holds the
 * RTM crowbar (port of TileEntityBeamRenderer). The original used the beacon-beam
 * texture; this uses an additive coloured column for the same locator effect.
 */
public class GroundUnitBeamRenderer implements BlockEntityRenderer<GroundUnitBlockEntity, LegacyBlockEntityRenderState<GroundUnitBlockEntity>> {
    private static final float R = 0.0F, G = 190F / 255F, B = 246F / 255F, A = 0.5F;
    private static final float HALF = 0.15F;
    private static final float HEIGHT = 64.0F;

    public GroundUnitBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public LegacyBlockEntityRenderState<GroundUnitBlockEntity> createRenderState() {
        return new LegacyBlockEntityRenderState<>();
    }

    @Override
    public void extractRenderState(GroundUnitBlockEntity blockEntity,
                                   LegacyBlockEntityRenderState<GroundUnitBlockEntity> state,
                                   float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(LegacyBlockEntityRenderState<GroundUnitBlockEntity> state, PoseStack pose,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.blockEntity == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        renderLegacy(state.blockEntity, state.partialTick, pose, buffers);
        buffers.endBatch();
    }

    private void renderLegacy(GroundUnitBlockEntity be, float partialTick, PoseStack pose,
                              MultiBufferSource buffers) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean crowbar = player.getMainHandItem().is(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                || player.getOffhandItem().is(RealTrainModRenewedItems.CROWBAR_ITEM.get());
        if (!crowbar) {
            return;
        }

        VertexConsumer vc = buffers.getBuffer(RenderTypes.lines());
        Matrix4f m = pose.last().pose();
        float lo = 0.5F - HALF;
        float hi = 0.5F + HALF;

        // four vertical edges of a thin locator column
        face(vc, m, lo, lo, hi, lo);
        face(vc, m, hi, lo, hi, hi);
        face(vc, m, hi, hi, lo, hi);
        face(vc, m, lo, hi, lo, lo);
    }

    private static void face(VertexConsumer vc, Matrix4f m, float x1, float z1, float x2, float z2) {
        line(vc, m, x1, z1);
        line(vc, m, x2, z2);
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x, float z) {
        vc.addVertex(m, x, 0.0F, z).setColor(R, G, B, A).setNormal(0.0F, 1.0F, 0.0F);
        vc.addVertex(m, x, HEIGHT, z).setColor(R, G, B, A).setNormal(0.0F, 1.0F, 0.0F);
    }
}
