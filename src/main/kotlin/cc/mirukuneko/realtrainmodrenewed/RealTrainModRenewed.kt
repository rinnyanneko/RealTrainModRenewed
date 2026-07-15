// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.compat.webctc.WebCtcCompat
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectPackLoader
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig
import cc.mirukuneko.realtrainmodrenewed.item.CrowbarItem
import cc.mirukuneko.realtrainmodrenewed.network.RealTrainModRenewedNetwork
import cc.mirukuneko.realtrainmodrenewed.network.SyncSpeakerSoundsPayload
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehiclePackLoader
import com.mojang.logging.LogUtils
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.block.BreakBlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.slf4j.Logger
import java.util.function.Supplier

@Mod(RealTrainModRenewed.MODID)
open class RealTrainModRenewed(
    modEventBus: IEventBus,
    modContainer: ModContainer,
    dist: Dist,
) {
    init {
        try {
            Configurator.setLevel("cc.mirukuneko.realtrainmodrenewed", Level.INFO)
        } catch (t: Throwable) {
            LOGGER.warn("Failed to set log level for rtmr: {}", t.toString())
        }

        modEventBus.addListener(this::commonSetup)
        modEventBus.addListener(this::registerNetwork)
        modEventBus.addListener(this::buildCreativeTabContents)

        RealTrainModRenewedBlocks.BLOCKS.register(modEventBus)
        RealTrainModRenewedItems.ITEMS.register(modEventBus)
        RealTrainModRenewedEntities.ENTITIES.register(modEventBus)
        cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities.ENTITY_TYPES.register(modEventBus)
        RealTrainModRenewedBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus)
        CREATIVE_MODE_TABS.register(modEventBus)
        RealTrainModRenewedComponents.REGISTRAR.register(modEventBus)

        NeoForge.EVENT_BUS.addListener { event: ServerStartedEvent -> WebCtcCompat.onServerStarted(event) }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppingEvent -> WebCtcCompat.onServerStopping(event) }
        NeoForge.EVENT_BUS.addListener { event: AttackEntityEvent -> CrowbarItem.onAttackEntity(event) }
        NeoForge.EVENT_BUS.addListener { event: BreakBlockEvent -> CrowbarItem.onBreakBlock(event) }
        NeoForge.EVENT_BUS.addListener(this::onStartTracking)
        NeoForge.EVENT_BUS.addListener { _: ServerStartingEvent -> SpeakerSoundConfig.load() }
        NeoForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedInEvent ->
            val player = event.entity
            if (player is ServerPlayer) {
                PacketDistributor.sendToPlayer(
                    player,
                    SyncSpeakerSoundsPayload(SpeakerSoundConfig.snapshot().toList()),
                )
            }
        }

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            BundledPackInstaller.installDefaultPacks()
            RailPackLoader.load()
            VehiclePackLoader.load()
            InstalledObjectPackLoader.load()
            TrainScriptSystem.getInstance().initialize()
        }
    }

    private fun registerNetwork(event: RegisterPayloadHandlersEvent) {
        RealTrainModRenewedNetwork.registerPayloadHandlers(event)
    }

    private fun onStartTracking(event: PlayerEvent.StartTracking) {
        val player = event.entity as? ServerPlayer ?: return
        val car = event.target as? CarEntity ?: return
        car.syncScriptDataTo(player)
    }

    private fun buildCreativeTabContents(event: BuildCreativeModeTabContentsEvent) {
        if (CreativeModeTabs.REDSTONE_BLOCKS == event.tabKey) {
            event.accept(RealTrainModRenewedItems.CROSSING_GATE_ITEM.get())
            event.accept(RealTrainModRenewedItems.SIGNAL_ITEM.get())
            event.accept(RealTrainModRenewedItems.SPEAKER_ITEM.get())
        }
    }

    companion object {
        const val MODID: String = "realtrainmodrenewed"

        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()

        @JvmField
        val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID)

        @JvmField
        val MAIN_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> =
            CREATIVE_MODE_TABS.register(
                "main_tab",
                Supplier {
                    CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.realtrainmodrenewed"))
                        .withTabsBefore(CreativeModeTabs.COMBAT)
                        .icon { RealTrainModRenewedItems.RAIL_ITEM.get().defaultInstance }
                        .displayItems { _, output ->
                            output.accept(RealTrainModRenewedItems.TRAIN_ITEM.get())
                            output.accept(RealTrainModRenewedItems.CAR_ITEM.get())
                            output.accept(RealTrainModRenewedItems.IC_CARD_ITEM.get())
                            output.accept(RealTrainModRenewedItems.RAIL_ITEM.get())
                            output.accept(RealTrainModRenewedItems.WIRE_ITEM.get())
                            output.accept(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                            output.accept(RealTrainModRenewedItems.WRENCH_ITEM.get())
                            output.accept(RealTrainModRenewedItems.CROSSING_GATE_ITEM.get())
                            output.accept(RealTrainModRenewedItems.MARKER_ITEM.get())
                            output.accept(RealTrainModRenewedItems.MARKER_DIAGONAL_ITEM.get())
                            output.accept(RealTrainModRenewedItems.MARKER_SWITCH_ITEM.get())
                            output.accept(RealTrainModRenewedItems.MARKER_SWITCH_DIAGONAL_ITEM.get())
                            output.accept(RealTrainModRenewedItems.LIGHT_ITEM.get())
                            output.accept(RealTrainModRenewedItems.INSULATOR_ITEM.get())
                            output.accept(RealTrainModRenewedItems.SIGNAL_ITEM.get())
                            output.accept(RealTrainModRenewedItems.OVERHEAD_LINE_POLE_ITEM.get())
                            output.accept(RealTrainModRenewedItems.TICKET_GATE_ITEM.get())
                            output.accept(RealTrainModRenewedItems.SPEAKER_ITEM.get())
                        }
                        .build()
                },
            )
    }
}
