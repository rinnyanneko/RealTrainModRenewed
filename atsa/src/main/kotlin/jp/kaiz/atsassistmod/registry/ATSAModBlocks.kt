// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.registry

import jp.kaiz.atsassistmod.ATSAssistMod
import jp.kaiz.atsassistmod.block.GroundUnitBlock
import jp.kaiz.atsassistmod.block.IftttBlock
import jp.kaiz.atsassistmod.block.StationAnnounceBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Function
import java.util.function.Supplier

object ATSAModBlocks {
    @JvmField
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(ATSAssistMod.MODID)

    @JvmField
    val GROUND_UNIT: DeferredBlock<GroundUnitBlock> = BLOCKS.registerBlock(
        "groundunit",
        Function(::GroundUnitBlock),
        Supplier {
        BlockBehaviour.Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops()
        },
    )

    @JvmField
    val IFTTT: DeferredBlock<IftttBlock> = BLOCKS.registerBlock(
        "ifttt",
        Function(::IftttBlock),
        Supplier {
            BlockBehaviour.Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops()
        },
    )

    @JvmField
    val STATION_ANNOUNCE: DeferredBlock<StationAnnounceBlock> = BLOCKS.registerBlock(
        "station_announce",
        Function(::StationAnnounceBlock),
        Supplier {
            BlockBehaviour.Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops()
        },
    )

    @JvmStatic
    fun register(bus: IEventBus) {
        BLOCKS.register(bus)
    }
}
