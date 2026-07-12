// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.registry

import jp.kaiz.atsassistmod.ATSAssistMod
import jp.kaiz.atsassistmod.block.GroundUnitType
import jp.kaiz.atsassistmod.item.DataMapEditorItem
import jp.kaiz.atsassistmod.item.GroundUnitItem
import jp.kaiz.atsassistmod.item.TrainProtectionSelectorItem
import net.minecraft.world.item.BlockItem
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister

object ATSAModItems {
    @JvmField
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(ATSAssistMod.MODID)

    /**
     * Single ground-unit item (blank / None). As in the original, you place one block
     * and choose its function via the right-click GUI -- there is no separate item per
     * variant.
     */
    @JvmField
    val GROUND_UNIT: DeferredItem<GroundUnitItem> = ITEMS.registerItem("groundunit_0") { props ->
        GroundUnitItem(ATSAModBlocks.GROUND_UNIT.get(), GroundUnitType.None, props)
    }

    @JvmField
    val IFTTT: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem("ifttt", ATSAModBlocks.IFTTT)

    @JvmField
    val STATION_ANNOUNCE: DeferredItem<BlockItem> =
        ITEMS.registerSimpleBlockItem("station_announce", ATSAModBlocks.STATION_ANNOUNCE)

    @JvmField
    val TRAIN_PROTECTION_SELECTOR: DeferredItem<TrainProtectionSelectorItem> =
        ITEMS.registerItem("train_protection_selector", ::TrainProtectionSelectorItem)

    @JvmField
    val DATA_MAP_EDITOR: DeferredItem<DataMapEditorItem> =
        ITEMS.registerItem("data_map_editor", ::DataMapEditorItem)

    @JvmStatic
    fun register(bus: IEventBus) {
        ITEMS.register(bus)
    }
}
