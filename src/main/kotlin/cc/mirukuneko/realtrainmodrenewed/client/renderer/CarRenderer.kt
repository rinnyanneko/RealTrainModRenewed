package cc.mirukuneko.realtrainmodrenewed.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import cc.mirukuneko.realtrainmodrenewed.client.ScriptClientCompat
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier

class CarRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<CarEntity, LegacyEntityRenderState<CarEntity>>(context) {

    companion object {
        private val FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath(
            RealTrainModRenewed.MODID,
            "textures/car/toyota_prius-phv.png",
        )
    }

    // getTextureLocation removed - CarEntity is an Entity, not EntityType-based renderer
    // override fun getTextureLocation(entity: CarEntity): net.minecraft.resources.ResourceLocation = FALLBACK_TEXTURE
    override fun createRenderState(): LegacyEntityRenderState<CarEntity> = LegacyEntityRenderState()

    override fun extractRenderState(entity: CarEntity, state: LegacyEntityRenderState<CarEntity>, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        state.entity = entity; state.entityYaw = entity.yRot
    }

    override fun submit(state: LegacyEntityRenderState<CarEntity>, poseStack: PoseStack,
                        submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
        val entity = state.entity ?: return
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        renderLegacy(entity, state.entityYaw, state.partialTick, poseStack, bufferSource, state.lightCoords)
        bufferSource.endBatch()
        super.submit(state, poseStack, submitNodeCollector, camera)
    }

    private fun renderLegacy(entity: CarEntity, entityYaw: Float, partialTick: Float, poseStack: PoseStack,
                             bufferSource: MultiBufferSource, packedLight: Int) {
        ScriptClientCompat.currentRenderPartialTick = partialTick
        val def = VehicleRegistry.getById(entity.vehicleId) ?: return
        val model = MqoModelLoader.loadModelForVehicle(def) ?: return
        poseStack.pushPose()
        try {
            poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw))
            poseStack.translate(def.modelOffset.x, def.modelOffset.y, def.modelOffset.z)
            poseStack.scale(def.modelScale, def.modelScale, def.modelScale)
            MqoModelLoader.renderModel(model, poseStack, bufferSource, packedLight, null, null, entity)
        } catch (_: Throwable) { } finally { poseStack.popPose() }
    }
}
