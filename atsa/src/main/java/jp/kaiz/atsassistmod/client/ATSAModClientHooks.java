package jp.kaiz.atsassistmod.client;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity;
import jp.kaiz.atsassistmod.client.screen.GroundUnitScreen;
import jp.kaiz.atsassistmod.client.screen.IftttEditorScreen;
import jp.kaiz.atsassistmod.client.screen.TrainProtectionSelectorScreen;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Client-only screen openers. Only referenced behind {@code level.isClientSide()}
 * guards so the classes never load on a dedicated server.
 */
public final class ATSAModClientHooks {
    private ATSAModClientHooks() {}

    public static void openGroundUnit(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof GroundUnitBlockEntity be) {
            mc.setScreen(new GroundUnitScreen(be));
        }
    }

    public static void openIftttEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof IftttBlockEntity be) {
            mc.setScreen(new IftttEditorScreen(be));
        }
    }

    public static void openTrainProtectionSelector() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof TrainEntity train && RtmTrains.isControlCar(train)) {
            mc.setScreen(new TrainProtectionSelectorScreen(train));
        }
    }
}
