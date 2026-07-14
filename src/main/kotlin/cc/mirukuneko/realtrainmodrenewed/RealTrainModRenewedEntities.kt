// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainFloorEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object RealTrainModRenewedEntities {
    @JvmField
    val ENTITIES: DeferredRegister<EntityType<*>> = DeferredRegister.create(Registries.ENTITY_TYPE, RealTrainModRenewed.MODID)

    @JvmField
    val TRAIN: DeferredHolder<EntityType<*>, EntityType<TrainEntity>> =
        ENTITIES.register("train", Supplier { EntityTypeHelper.createTrainType() })

    @JvmField
    val TRAIN_BOGIE: DeferredHolder<EntityType<*>, EntityType<TrainBogieEntity>> =
        ENTITIES.register("train_bogie", Supplier { EntityTypeHelper.createBogieType() })

    @JvmField
    val TRAIN_SEAT: DeferredHolder<EntityType<*>, EntityType<TrainSeatEntity>> =
        ENTITIES.register("train_seat", Supplier { EntityTypeHelper.createSeatType() })

    @JvmField
    val TRAIN_FLOOR: DeferredHolder<EntityType<*>, EntityType<TrainFloorEntity>> =
        ENTITIES.register("train_floor", Supplier { EntityTypeHelper.createFloorType() })

    private fun key(path: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, path))
}
