package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.client.PackRequirementWarnings;
import com.portofino.realtrainmodunofficial.modelpack.VehicleModelPackManager;
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
@Mod(value = RealTrainModUnofficial.MODID, dist = Dist.CLIENT)
// EventBusSubscriber を使用すると、@SubscribeEvent でアノテーションされたクラス内のすべての静的メソッドを自動的に登録できます。
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public class RealTrainModUnofficialClient {
    public RealTrainModUnofficialClient(ModContainer container) {
        // NeoForgeがこのMODのコンフィグ画面を作成できるようにします。
        // コンフィグ画面は、Mods画面＞自分のModをクリック＞コンフィグをクリックで表示されます。
        // 設定オプションの翻訳をen_us.jsonファイルに追加することを忘れないでください。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // クライアントのセットアップ・コード
        RealTrainModUnofficial.LOGGER.info("HELLO FROM CLIENT SETUP");
        RealTrainModUnofficial.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        com.portofino.realtrainmodunofficial.script.TrainScriptSystem.getInstance().initialize();
        VehicleModelPackManager.INSTANCE.initialize(Minecraft.getInstance().getResourceManager());
        PackRequirementWarnings.refresh();
    }
}
