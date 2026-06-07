package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.client.PackRequirementWarnings;
import cc.mirukuneko.realtrainmodrenewed.modelpack.VehicleModelPackManager;
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// このクラスは専用サーバーではロードされません。ここからクライアント側のコードにアクセスしても安全です。
@Mod(value = RealTrainModRenewed.MODID, dist = Dist.CLIENT)
// EventBusSubscriber を使用すると、@SubscribeEvent でアノテーションされたクラス内のすべての静的メソッドを自動的に登録できます。
@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = Dist.CLIENT)
public class RealTrainModRenewedClient {
    public RealTrainModRenewedClient(ModContainer container) {
        // NeoForgeがこのMODのコンフィグ画面を作成できるようにします。
        // コンフィグ画面は、Mods画面＞自分のModをクリック＞コンフィグをクリックで表示されます。
        // 設定オプションの翻訳をen_us.jsonファイルに追加することを忘れないでください。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // クライアントのセットアップ・コード
        RealTrainModRenewed.LOGGER.info("HELLO FROM CLIENT SETUP");
        RealTrainModRenewed.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        TrainScriptSystem.getInstance().initialize();
        VehicleModelPackManager.INSTANCE.initialize(Minecraft.getInstance().getResourceManager());
        PackRequirementWarnings.refresh();
    }
}
