// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object EntityTypeHelper {
    @JvmStatic
    fun createTrainType(): EntityType<TrainEntity> =
        EntityType.Builder.of(::TrainEntity, MobCategory.MISC)
            .sized(2.0f, 2.0f)
            .fireImmune()
            .clientTrackingRange(10)
            .build(entityKey("train"))

    @JvmStatic
    fun createBogieType(): EntityType<TrainBogieEntity> =
        EntityType.Builder.of(::TrainBogieEntity, MobCategory.MISC)
            .sized(1.4f, 1.6f)
            .fireImmune()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(entityKey("train_bogie"))

    @JvmStatic
    fun createSeatType(): EntityType<TrainSeatEntity> =
        EntityType.Builder.of(::TrainSeatEntity, MobCategory.MISC)
            .sized(0.9f, 0.25f)
            .fireImmune()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(entityKey("train_seat"))

    private fun entityKey(path: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, path))
}
