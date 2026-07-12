// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.registry

import jp.kaiz.atsassistmod.ATSAssistMod
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ATSAModBlockEntities {
    @JvmField
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ATSAssistMod.MODID)

    @JvmField
    val GROUND_UNIT: DeferredHolder<BlockEntityType<*>, BlockEntityType<GroundUnitBlockEntity>> =
        BLOCK_ENTITIES.register("groundunit", Supplier {
            BlockEntityType(::GroundUnitBlockEntity, ATSAModBlocks.GROUND_UNIT.get())
        })

    @JvmField
    val IFTTT: DeferredHolder<BlockEntityType<*>, BlockEntityType<IftttBlockEntity>> =
        BLOCK_ENTITIES.register("ifttt", Supplier {
            BlockEntityType(::IftttBlockEntity, ATSAModBlocks.IFTTT.get())
        })

    @JvmStatic
    fun register(bus: IEventBus) {
        BLOCK_ENTITIES.register(bus)
    }
}
