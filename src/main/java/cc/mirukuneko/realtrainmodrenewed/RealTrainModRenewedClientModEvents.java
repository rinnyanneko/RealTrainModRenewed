package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.client.renderer.RailCoreBlockEntityRenderer;
import cc.mirukuneko.realtrainmodrenewed.client.renderer.TrainBogieEntityRenderer;
import cc.mirukuneko.realtrainmodrenewed.client.renderer.TrainEntityRenderer;
import cc.mirukuneko.realtrainmodrenewed.client.renderer.TrainSeatEntityRenderer;
import cc.mirukuneko.realtrainmodrenewed.client.TrainControlKeyMappings;
import cc.mirukuneko.realtrainmodrenewed.client.renderer.CarRenderer;
import cc.mirukuneko.realtrainmodrenewed.client.renderer.InstalledObjectBlockEntityRenderer;
import cc.mirukuneko.realtrainmodrenewed.client.sound.ExternalSoundPackBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = Dist.CLIENT)
public final class RealTrainModRenewedClientModEvents {
    private RealTrainModRenewedClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // レールコアのブロックエンティティレンダラーを登録（MQOモデル描画）
        event.registerBlockEntityRenderer(
            RealTrainModRenewedBlockEntities.LARGE_RAIL_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            RealTrainModRenewedBlockEntities.INSTALLED_OBJECT.get(),
            InstalledObjectBlockEntityRenderer::new
        );
        if (RealTrainModRenewedEntities.TRAIN.isBound()) {
            event.registerEntityRenderer(
                RealTrainModRenewedEntities.TRAIN.get(),
                TrainEntityRenderer::new
            );
        }
        if (RealTrainModRenewedEntities.TRAIN_BOGIE.isBound()) {
            event.registerEntityRenderer(
                RealTrainModRenewedEntities.TRAIN_BOGIE.get(),
                TrainBogieEntityRenderer::new
            );
        }
        if (RealTrainModRenewedEntities.TRAIN_SEAT.isBound()) {
            event.registerEntityRenderer(
                RealTrainModRenewedEntities.TRAIN_SEAT.get(),
                TrainSeatEntityRenderer::new
            );
        }
        event.registerEntityRenderer(
            cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities.CAR.get(),
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

    // 本家RTM同様、テクスチャ(白の marker_0 等)は変えず tint 色だけ変える。
    // 普通マーカー=赤、分岐マーカー=青。
    private static final int MARKER_COLOR = 0xFF3B30;        // 赤
    private static final int MARKER_SWITCH_COLOR = 0x0028C8; // 濃い青(本家寄り)

    // TODO 26.1: marker tint registration moved away from the old RegisterColorHandlersEvent.Block/Item
    // event types on the compile classpath. Marker models still load, but their legacy red/blue tint is pending.

}
