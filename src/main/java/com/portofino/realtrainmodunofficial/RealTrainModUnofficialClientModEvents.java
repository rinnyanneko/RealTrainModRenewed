package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.client.renderer.RailCoreBlockEntityRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.TrainBogieEntityRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.TrainEntityRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.TrainSeatEntityRenderer;
import com.portofino.realtrainmodunofficial.client.TrainControlKeyMappings;
import com.portofino.realtrainmodunofficial.client.renderer.CarRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.InstalledObjectBlockEntityRenderer;
import com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RealTrainModUnofficialClientModEvents {
    private RealTrainModUnofficialClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // レールコアのブロックエンティティレンダラーを登録（MQOモデル描画）
        event.registerBlockEntityRenderer(
            RealTrainModUnofficialBlockEntities.LARGE_RAIL_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get(),
            InstalledObjectBlockEntityRenderer::new
        );
        if (RealTrainModUnofficialEntities.TRAIN.isBound()) {
            event.registerEntityRenderer(
                RealTrainModUnofficialEntities.TRAIN.get(),
                TrainEntityRenderer::new
            );
        }
        if (RealTrainModUnofficialEntities.TRAIN_BOGIE.isBound()) {
            event.registerEntityRenderer(
                RealTrainModUnofficialEntities.TRAIN_BOGIE.get(),
                TrainBogieEntityRenderer::new
            );
        }
        if (RealTrainModUnofficialEntities.TRAIN_SEAT.isBound()) {
            event.registerEntityRenderer(
                RealTrainModUnofficialEntities.TRAIN_SEAT.get(),
                TrainSeatEntityRenderer::new
            );
        }
        event.registerEntityRenderer(
            com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.CAR.get(),
            CarRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        TrainControlKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void registerPackFinders(AddPackFindersEvent event) {
        ExternalSoundPackBridge.register(event);
    }

    /** 一旦ティントを切って、両方とも白マーカーへ戻す。 */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
            (state, tintGetter, pos, tintIndex) -> 0xFFFFFF,
            RealTrainModUnofficialBlocks.MARKER.get()
        );
        event.register(
            (state, tintGetter, pos, tintIndex) -> 0xFFFFFF,
            RealTrainModUnofficialBlocks.MARKER_SWITCH.get()
        );
    }

    /** アイテム側も白へ戻す。 */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
            (stack, tintIndex) -> 0xFFFFFF,
            RealTrainModUnofficialItems.MARKER_ITEM.get()
        );
        event.register(
            (stack, tintIndex) -> 0xFFFFFF,
            RealTrainModUnofficialItems.MARKER_SWITCH_ITEM.get()
        );
    }

}
