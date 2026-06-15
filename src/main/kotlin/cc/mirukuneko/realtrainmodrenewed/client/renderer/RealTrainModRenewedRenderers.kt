package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities
import net.neoforged.neoforge.client.event.EntityRenderersEvent

object RealTrainModRenewedRenderers {
    @JvmStatic
    fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(RealTrainModRenewedEntities.CAR.get(), ::CarRenderer)
    }
}
