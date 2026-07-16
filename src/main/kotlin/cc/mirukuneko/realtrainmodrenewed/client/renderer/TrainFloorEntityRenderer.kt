// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.entity.TrainFloorEntity
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider

class TrainFloorEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<TrainFloorEntity, LegacyEntityRenderState<TrainFloorEntity>>(context) {

    override fun createRenderState(): LegacyEntityRenderState<TrainFloorEntity> = LegacyEntityRenderState()

    override fun extractRenderState(
        entity: TrainFloorEntity,
        state: LegacyEntityRenderState<TrainFloorEntity>,
        partialTick: Float,
    ) {
        super.extractRenderState(entity, state, partialTick)
        state.entity = entity
    }
}
