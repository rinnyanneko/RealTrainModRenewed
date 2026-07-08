// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.client.TrainControlKeyMappings
import cc.mirukuneko.realtrainmodrenewed.client.renderer.CarRenderer
import cc.mirukuneko.realtrainmodrenewed.client.renderer.InstalledObjectBlockEntityRenderer
import cc.mirukuneko.realtrainmodrenewed.client.renderer.RailCoreBlockEntityRenderer
import cc.mirukuneko.realtrainmodrenewed.client.renderer.TrainBogieEntityRenderer
import cc.mirukuneko.realtrainmodrenewed.client.renderer.TrainEntityRenderer
import cc.mirukuneko.realtrainmodrenewed.client.renderer.TrainSeatEntityRenderer
import cc.mirukuneko.realtrainmodrenewed.client.sound.ExternalSoundPackBridge
import net.minecraft.client.color.block.BlockTintSources
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.event.AddPackFindersEvent

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object RealTrainModRenewedClientModEvents {
    private const val MARKER_COLOR = 0xFF3B30
    private const val MARKER_SWITCH_COLOR = 0x0028C8

    @SubscribeEvent
    @JvmStatic
    fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        // レールコアのブロックエンティティレンダラーを登録（MQOモデル描画）
        event.registerBlockEntityRenderer(
            RealTrainModRenewedBlockEntities.LARGE_RAIL_CORE.get(),
            ::RailCoreBlockEntityRenderer,
        )
        event.registerBlockEntityRenderer(
            RealTrainModRenewedBlockEntities.INSTALLED_OBJECT.get(),
            ::InstalledObjectBlockEntityRenderer,
        )
        if (RealTrainModRenewedEntities.TRAIN.isBound) {
            event.registerEntityRenderer(
                RealTrainModRenewedEntities.TRAIN.get(),
                ::TrainEntityRenderer,
            )
        }
        if (RealTrainModRenewedEntities.TRAIN_BOGIE.isBound) {
            event.registerEntityRenderer(
                RealTrainModRenewedEntities.TRAIN_BOGIE.get(),
                ::TrainBogieEntityRenderer,
            )
        }
        if (RealTrainModRenewedEntities.TRAIN_SEAT.isBound) {
            event.registerEntityRenderer(
                RealTrainModRenewedEntities.TRAIN_SEAT.get(),
                ::TrainSeatEntityRenderer,
            )
        }
        event.registerEntityRenderer(
            cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities.CAR.get(),
            ::CarRenderer,
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        TrainControlKeyMappings.register(event)
    }

    @SubscribeEvent
    @JvmStatic
    fun registerPackFinders(event: AddPackFindersEvent) {
        ExternalSoundPackBridge.register(event)
    }

    // 本家RTM同様、テクスチャ(白の marker_0 等)は変えず tint 色だけ変える。
    // 普通マーカー=赤、分岐マーカー=青。
    @SubscribeEvent
    @JvmStatic
    fun registerBlockTints(event: RegisterColorHandlersEvent.BlockTintSources) {
        event.register(
            listOf(BlockTintSources.constant(MARKER_COLOR)),
            RealTrainModRenewedBlocks.MARKER.get(),
        )
        event.register(
            listOf(BlockTintSources.constant(MARKER_SWITCH_COLOR)),
            RealTrainModRenewedBlocks.MARKER_SWITCH.get(),
        )
    }
}
