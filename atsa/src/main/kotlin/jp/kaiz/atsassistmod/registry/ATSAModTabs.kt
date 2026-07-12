// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.registry

import jp.kaiz.atsassistmod.ATSAssistMod
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ATSAModTabs {
    @JvmField
    val TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ATSAssistMod.MODID)

    @JvmField
    val MAIN: DeferredHolder<CreativeModeTab, CreativeModeTab> = TABS.register("utils", Supplier {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.atsassistmod.utils"))
            .icon { ItemStack(ATSAModItems.IFTTT.get()) }
            .displayItems { _, output ->
                output.accept(ATSAModItems.GROUND_UNIT.get())
                output.accept(ATSAModItems.IFTTT.get())
                output.accept(ATSAModItems.STATION_ANNOUNCE.get())
                output.accept(ATSAModItems.TRAIN_PROTECTION_SELECTOR.get())
                output.accept(ATSAModItems.DATA_MAP_EDITOR.get())
            }
            .build()
    })

    @JvmStatic
    fun register(bus: IEventBus) {
        TABS.register(bus)
    }
}
