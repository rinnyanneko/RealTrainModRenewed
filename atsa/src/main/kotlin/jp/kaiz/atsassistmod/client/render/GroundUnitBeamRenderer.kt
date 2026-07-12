// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.render

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * Draws a translucent locator beam above a ground unit while the player holds the
 * RTM crowbar.
 */
open class GroundUnitBeamRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context,
) : BlockEntityRenderer<GroundUnitBlockEntity, LegacyBlockEntityRenderState<GroundUnitBlockEntity>> {
    override fun createRenderState(): LegacyBlockEntityRenderState<GroundUnitBlockEntity> =
        LegacyBlockEntityRenderState()

    override fun extractRenderState(
        blockEntity: GroundUnitBlockEntity,
        state: LegacyBlockEntityRenderState<GroundUnitBlockEntity>,
        partialTick: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?,
    ) {
        super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress)
        state.blockEntity = blockEntity
        state.partialTick = partialTick
    }

    override fun submit(
        state: LegacyBlockEntityRenderState<GroundUnitBlockEntity>,
        pose: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        val blockEntity = state.blockEntity ?: return
        val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
        renderLegacy(blockEntity, state.partialTick, pose, buffers)
        buffers.endBatch()
    }

    private fun renderLegacy(
        blockEntity: GroundUnitBlockEntity,
        partialTick: Float,
        pose: PoseStack,
        buffers: MultiBufferSource,
    ) {
        val player = Minecraft.getInstance().player ?: return
        val crowbar = player.mainHandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get()) ||
            player.offhandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
        if (!crowbar) {
            return
        }

        val vertexConsumer = buffers.getBuffer(RenderTypes.lines())
        val matrix = pose.last().pose()
        val lo = 0.5f - HALF
        val hi = 0.5f + HALF

        face(vertexConsumer, matrix, lo, lo, hi, lo)
        face(vertexConsumer, matrix, hi, lo, hi, hi)
        face(vertexConsumer, matrix, hi, hi, lo, hi)
        face(vertexConsumer, matrix, lo, hi, lo, lo)
    }

    companion object {
        private const val R = 0.0f
        private const val G = 190f / 255f
        private const val B = 246f / 255f
        private const val A = 0.5f
        private const val HALF = 0.15f
        private const val HEIGHT = 64.0f

        private fun face(vertexConsumer: VertexConsumer, matrix: Matrix4f, x1: Float, z1: Float, x2: Float, z2: Float) {
            line(vertexConsumer, matrix, x1, z1)
            line(vertexConsumer, matrix, x2, z2)
        }

        private fun line(vertexConsumer: VertexConsumer, matrix: Matrix4f, x: Float, z: Float) {
            vertexConsumer.addVertex(matrix, x, 0.0f, z).setColor(R, G, B, A).setNormal(0.0f, 1.0f, 0.0f)
            vertexConsumer.addVertex(matrix, x, HEIGHT, z).setColor(R, G, B, A).setNormal(0.0f, 1.0f, 0.0f)
        }
    }
}
