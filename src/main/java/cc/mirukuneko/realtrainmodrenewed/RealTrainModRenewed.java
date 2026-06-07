package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.compat.webctc.WebCtcCompat;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectPackLoader;
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig;
import cc.mirukuneko.realtrainmodrenewed.network.RealTrainModRenewedNetwork;
import cc.mirukuneko.realtrainmodrenewed.network.SyncSpeakerSoundsPayload;
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader;
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehiclePackLoader;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RealTrainModRenewed.MODID)
public class RealTrainModRenewed {
    public static final String MODID = "realtrainmodrenewed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
        CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realtrainmodunofficial"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> RealTrainModRenewedItems.RAIL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(RealTrainModRenewedItems.TRAIN_ITEM.get());
                output.accept(RealTrainModRenewedItems.CAR_ITEM.get());
                output.accept(RealTrainModRenewedItems.IC_CARD_ITEM.get());
                output.accept(RealTrainModRenewedItems.RAIL_ITEM.get());
                output.accept(RealTrainModRenewedItems.WIRE_ITEM.get());
                output.accept(RealTrainModRenewedItems.CROWBAR_ITEM.get());
                output.accept(RealTrainModRenewedItems.WRENCH_ITEM.get());
                output.accept(RealTrainModRenewedItems.CROSSING_GATE_ITEM.get());
                output.accept(RealTrainModRenewedItems.MARKER_ITEM.get());
                output.accept(RealTrainModRenewedItems.MARKER_DIAGONAL_ITEM.get());
                output.accept(RealTrainModRenewedItems.MARKER_SWITCH_ITEM.get());
                output.accept(RealTrainModRenewedItems.MARKER_SWITCH_DIAGONAL_ITEM.get());
                output.accept(RealTrainModRenewedItems.LIGHT_ITEM.get());
                output.accept(RealTrainModRenewedItems.INSULATOR_ITEM.get());
                output.accept(RealTrainModRenewedItems.SIGNAL_ITEM.get());
                output.accept(RealTrainModRenewedItems.OVERHEAD_LINE_POLE_ITEM.get());
                output.accept(RealTrainModRenewedItems.TICKET_GATE_ITEM.get());
                output.accept(RealTrainModRenewedItems.SPEAKER_ITEM.get());
            }).build());

    public RealTrainModRenewed(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        // 軽量化: 既定のログレベルは INFO に固定する(描画には無関係)。
        // 以前はバグ追跡のため DEBUG を強制していたが、毎tick/毎フレームの DEBUG ログが
        // 文字列整形・I/O コストになり負荷源になるため INFO に下げる(調査時は手動で DEBUG に上げる)。
        try {
            org.apache.logging.log4j.core.config.Configurator.setLevel(
                "cc.mirukuneko.realtrainmodrenewed",
                org.apache.logging.log4j.Level.INFO
            );
        } catch (Throwable t) {
            LOGGER.warn("Failed to set log level for rtmr: {}", t.toString());
        }

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerNetwork);
        modEventBus.addListener(this::buildCreativeTabContents);

        RealTrainModRenewedBlocks.BLOCKS.register(modEventBus);
        RealTrainModRenewedItems.ITEMS.register(modEventBus);
        RealTrainModRenewedEntities.ENTITIES.register(modEventBus);
        cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities.ENTITY_TYPES.register(modEventBus);
        RealTrainModRenewedBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        RealTrainModRenewedComponents.REGISTRAR.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            WebCtcCompat::onServerStarted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            WebCtcCompat::onServerStopping);
        // スピーカー音源マッピングをサーバー起動時にロードし、プレイヤー接続時に同期する。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartingEvent e) ->
                SpeakerSoundConfig.load());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
                if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new SyncSpeakerSoundsPayload(
                            java.util.Arrays.asList(
                                SpeakerSoundConfig.snapshot())));
                }
            });

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BundledPackInstaller.installDefaultPacks();
            RailPackLoader.load();
            VehiclePackLoader.load();
            InstalledObjectPackLoader.load();
            TrainScriptSystem.getInstance().initialize();
        });
    }

    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        RealTrainModRenewedNetwork.registerPayloadHandlers(event);
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.REDSTONE_BLOCKS.equals(event.getTabKey())) {
            event.accept(RealTrainModRenewedItems.CROSSING_GATE_ITEM.get());
            event.accept(RealTrainModRenewedItems.SIGNAL_ITEM.get());
            event.accept(RealTrainModRenewedItems.SPEAKER_ITEM.get());
        }
    }
}
