package jp.kaiz.atsassistmod.client;

import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.client.render.GroundUnitBeamRenderer;
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Mod-bus client setup: HUD layer + key mappings. */
@EventBusSubscriber(modid = ATSAssistMod.MODID, value = Dist.CLIENT)
public final class ATSAModClientModEvents {
    private ATSAModClientModEvents() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "train_hud"),
                ATSAModHud.INSTANCE);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ATSAModKeys.EMERGENCY_BRAKE);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ATSAModBlockEntities.GROUND_UNIT.get(), GroundUnitBeamRenderer::new);
    }
}
