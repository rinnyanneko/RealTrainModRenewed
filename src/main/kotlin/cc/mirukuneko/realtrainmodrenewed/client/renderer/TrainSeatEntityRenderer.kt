package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.Identifier

class TrainSeatEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<TrainSeatEntity, LegacyEntityRenderState<TrainSeatEntity>>(context) {

    fun getTextureLocation(entity: TrainSeatEntity): Identifier = EMPTY_TEXTURE

    override fun createRenderState(): LegacyEntityRenderState<TrainSeatEntity> =
        LegacyEntityRenderState()

    override fun extractRenderState(
        entity: TrainSeatEntity,
        state: LegacyEntityRenderState<TrainSeatEntity>,
        partialTick: Float,
    ) {
        super.extractRenderState(entity, state, partialTick)
        state.entity = entity
    }

    override fun shouldRender(
        entity: TrainSeatEntity,
        frustum: Frustum,
        camX: Double,
        camY: Double,
        camZ: Double,
    ): Boolean = true

    companion object {
        private val EMPTY_TEXTURE: Identifier =
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/misc/empty.png")
    }
}
