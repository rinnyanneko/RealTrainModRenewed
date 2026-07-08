// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.registry

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object RealTrainModRenewedEntities {
    @JvmField
    val ENTITY_TYPES: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, RealTrainModRenewed.MODID)

    @JvmField
    val CAR: Supplier<EntityType<CarEntity>> = ENTITY_TYPES.register(
        "car",
        Supplier {
            EntityType.Builder.of(::CarEntity, MobCategory.MISC)
                .sized(2.0f, 2.0f)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build(key("car"))
        }
    )

    private fun key(path: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, path))
}
