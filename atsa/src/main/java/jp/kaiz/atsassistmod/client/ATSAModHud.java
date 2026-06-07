package jp.kaiz.atsassistmod.client;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.client.hud.TrainHudClient;
import jp.kaiz.atsassistmod.client.hud.TrainHudClientManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Driver HUD overlay (port of TrainGuiRender). Shows ATO / TASC / Limit / train-
 * protection state when riding a control car. The original chose a layout from the
 * cab config; RTM's new API does not expose that, so the bottom-left layout is used.
 */
public final class ATSAModHud implements GuiLayer {
    public static final ATSAModHud INSTANCE = new ATSAModHud();

    private ATSAModHud() {}

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) {
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof TrainEntity train) || !RtmTrains.isControlCar(train)) {
            return;
        }
        TrainHudClient tcc = TrainHudClientManager.get(train);
        if (tcc == null || tcc.isNotShowHud()) {
            return;
        }

        String atoSpeed = tcc.isATO() ? String.valueOf(tcc.getATOSpeed()) : "off";
        String tascSpeed = tcc.isTASC() ? String.valueOf(tcc.getTASCDistance()) : "off";
        int atc = tcc.getATCSpeed();
        String limit = atc == Integer.MAX_VALUE ? "---" : String.valueOf(atc);
        int tp = tcc.getTrainProtectionSpeed();
        String tpSpeed = tp == Integer.MAX_VALUE ? "---" : String.valueOf(tp);
        TrainProtectionType tpType = tcc.getTrainProtectionType();

        int h = g.guiHeight();
        int manualColor = tcc.isManualDrive() ? 0xFFFF0000 : 0xFFFFFFFF;
        int fix = 50;
        if (tpType != TrainProtectionType.NONE) {
            g.text(mc.font, Component.translatable(tpType.getTranslationKey()).getString() + " : " + tpSpeed,
                    2, h - (fix += 10), 0xFFFFFFFF);
        }
        g.text(mc.font, "Limit : " + limit, 2, h - (fix += 10), 0xFFFFFFFF);
        g.text(mc.font, "TASC : " + tascSpeed, 2, h - (fix += 10), manualColor);
        g.text(mc.font, "ATO : " + atoSpeed, 2, h - (fix += 10), manualColor);
    }
}
