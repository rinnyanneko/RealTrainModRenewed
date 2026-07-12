// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client

import jp.kaiz.atsassistmod.ATSAssistMod
import jp.kaiz.atsassistmod.client.render.GroundUnitBeamRenderer
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities
import net.minecraft.resources.Identifier
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers

/** Mod-bus client setup: HUD layer + key mappings. */
@EventBusSubscriber(modid = ATSAssistMod.MODID, value = [Dist.CLIENT])
object ATSAModClientModEvents {
    @SubscribeEvent
    @JvmStatic
    fun registerLayers(event: RegisterGuiLayersEvent) {
        event.registerAbove(
            VanillaGuiLayers.HOTBAR,
            Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "train_hud"),
            ATSAModHud,
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        event.register(ATSAModKeys.EMERGENCY_BRAKE)
    }

    @SubscribeEvent
    @JvmStatic
    fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(ATSAModBlockEntities.GROUND_UNIT.get(), ::GroundUnitBeamRenderer)
    }
}
