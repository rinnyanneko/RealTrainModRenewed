// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.client.PackRequirementWarnings
import cc.mirukuneko.realtrainmodrenewed.modelpack.VehicleModelPackManager
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

@Mod(value = RealTrainModRenewed.MODID, dist = [Dist.CLIENT])
@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
open class RealTrainModRenewedClient(container: ModContainer) {
    init {
        container.registerExtensionPoint(IConfigScreenFactory::class.java, IConfigScreenFactory { modContainer, parent ->
            ConfigurationScreen(modContainer, parent)
        })
    }

    companion object {
        @SubscribeEvent
        @JvmStatic
        fun onClientSetup(event: FMLClientSetupEvent) {
            RealTrainModRenewed.LOGGER.info("HELLO FROM CLIENT SETUP")
            RealTrainModRenewed.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
            TrainScriptSystem.getInstance().initialize()
            VehicleModelPackManager.INSTANCE.initialize(Minecraft.getInstance().resourceManager)
            PackRequirementWarnings.refresh()
        }
    }
}
