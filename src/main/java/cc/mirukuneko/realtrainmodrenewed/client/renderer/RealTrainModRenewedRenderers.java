package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class RealTrainModRenewedRenderers {
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RealTrainModRenewedEntities.CAR.get(), CarRenderer::new);
    }
}
