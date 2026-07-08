// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import kotlin.math.max

class TrainBogieEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<TrainBogieEntity, LegacyEntityRenderState<TrainBogieEntity>>(context) {

    init {
        shadowRadius = 0.0f
    }

    fun getTextureLocation(entity: TrainBogieEntity): Identifier =
        Identifier.withDefaultNamespace("missingno")

    override fun createRenderState(): LegacyEntityRenderState<TrainBogieEntity> =
        LegacyEntityRenderState()

    override fun extractRenderState(
        entity: TrainBogieEntity,
        state: LegacyEntityRenderState<TrainBogieEntity>,
        partialTick: Float,
    ) {
        super.extractRenderState(entity, state, partialTick)
        state.entity = entity
    }

    override fun shouldRender(
        entity: TrainBogieEntity,
        frustum: Frustum,
        camX: Double,
        camY: Double,
        camZ: Double,
    ): Boolean {
        if (entity.isRemoved) return false
        val train = entity.train
        if (frustum.isVisible(entity.boundingBoxForCulling.inflate(2.0))) return true
        if (train != null) {
            val inflate = max(3.0, train.trainDistance.toDouble() + 3.0)
            if (frustum.isVisible(train.boundingBox.inflate(inflate, 3.0, inflate))) return true
        }
        return false
    }

    override fun submit(
        state: LegacyEntityRenderState<TrainBogieEntity>,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        // Hitbox-only entity. Visual bogies are rendered from TrainEntityRenderer
        // so they inherit the same carbody transform and only correct their rail height.
    }
}
