// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod

import com.mojang.logging.LogUtils
import jp.kaiz.atsassistmod.controller.TrainControllerManager
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities
import jp.kaiz.atsassistmod.registry.ATSAModBlocks
import jp.kaiz.atsassistmod.registry.ATSAModItems
import jp.kaiz.atsassistmod.registry.ATSAModTabs
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger

/**
 * ATSAssistMod main entry point (NeoForge 1.21.1 port).
 */
@Mod(ATSAssistMod.MODID)
class ATSAssistMod(modBus: IEventBus) {
    init {
        ATSAModBlocks.register(modBus)
        ATSAModItems.register(modBus)
        ATSAModBlockEntities.register(modBus)
        ATSAModTabs.register(modBus)

        NeoForge.EVENT_BUS.register(this)
        LOGGER.info("[ATSAssist] initialised for {}", MODID)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        TrainControllerManager.onTick(event.server)
    }

    companion object {
        const val MODID: String = "atsassistmod"

        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
