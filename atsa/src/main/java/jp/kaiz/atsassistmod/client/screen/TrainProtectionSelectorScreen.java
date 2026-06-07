package jp.kaiz.atsassistmod.client.screen;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.client.hud.TrainHudClient;
import jp.kaiz.atsassistmod.client.hud.TrainHudClientManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.ManualDrive;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.TrainDriveMode;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.TrainProtectionSetter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * On-ride driver SW panel (port of GUITrainProtectionSelector). Lets the driver pick
 * drive mode (Manual / TASC / TASC+ATO), toggle manual-drive lock and HUD visibility,
 * and select the train-protection device.
 */
public class TrainProtectionSelectorScreen extends Screen {
    private final TrainEntity train;
    private final List<TrainProtectionType> validTPList = new ArrayList<>();
    private TrainHudClient tcc;

    public TrainProtectionSelectorScreen(TrainEntity train) {
        super(Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.5"));
        this.train = train;
        this.tcc = TrainHudClientManager.get(train);
        String tpList;
        try {
            tpList = train.getResourceState().getDataMap().getString("ATSAssist_TP");
        } catch (Throwable t) {
            tpList = "";
        }
        if (tpList == null || tpList.isEmpty()) {
            validTPList.add(TrainProtectionType.ATACS);
            validTPList.add(TrainProtectionType.ATSPs);
            validTPList.add(TrainProtectionType.RATS);
            validTPList.add(TrainProtectionType.RnATS);
        } else {
            if (tpList.contains("ATACS")) validTPList.add(TrainProtectionType.ATACS);
            if (tpList.contains("ATS-Ps")) validTPList.add(TrainProtectionType.ATSPs);
            if (tpList.contains("R-ATS")) validTPList.add(TrainProtectionType.RATS);
            if (tpList.contains("Rn-ATS")) validTPList.add(TrainProtectionType.RnATS);
        }
    }

    private TrainHudClient tcc() {
        if (tcc == null) {
            tcc = TrainHudClientManager.getOrCreate(train);
        }
        return tcc;
    }

    @Override
    protected void init() {
        int heightBase = this.height / 2 - 55;
        int widthBaseL = this.width / 2 - 80;
        int widthBaseR0 = this.width / 2 + 40;
        int widthBaseR1 = this.width / 2 + 130;

        // manual-drive lock checkbox
        addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(widthBaseL + 3, heightBase + 28)
                .selected(tcc != null && tcc.isManualDrive())
                .onValueChange((cb, v) -> jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(new ManualDrive(v)))
                .build());
        // hide HUD checkbox
        addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(widthBaseL + 3, heightBase + 103)
                .selected(tcc != null && tcc.isNotShowHud())
                .onValueChange((cb, v) -> tcc().setNotShowHud(v))
                .build());

        // drive mode buttons (Manual / TASC / TASC+ATO)
        addRenderableWidget(Button.builder(Component.literal("Manual"), b -> sendDriveMode(10))
                .bounds(widthBaseL, heightBase + 50, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("TASC"), b -> sendDriveMode(11))
                .bounds(widthBaseL, heightBase + 72, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("TASC/ATO"), b -> sendDriveMode(12))
                .bounds(widthBaseL, heightBase + 94, 70, 20).build());

        // train protection buttons
        addRenderableWidget(Button.builder(Component.translatable(TrainProtectionType.NONE.getTranslationKey()),
                b -> sendTP(TrainProtectionType.NONE))
                .bounds(widthBaseR0, heightBase, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(TrainProtectionType.STATION_PREMISES.getTranslationKey()),
                b -> sendTP(TrainProtectionType.STATION_PREMISES))
                .bounds(widthBaseR0, heightBase + 25, 60, 20).build());

        int y = heightBase;
        for (TrainProtectionType type : validTPList) {
            addRenderableWidget(Button.builder(Component.translatable(type.getTranslationKey()), b -> sendTP(type))
                    .bounds(widthBaseR1, y, 60, 20).build());
            y += 25;
        }
    }

    private void sendDriveMode(int mode) {
        switch (mode) {
            case 10 -> { tcc().setTASC(false); tcc().setATO(false); }
            case 11 -> tcc().setATO(false);
            default -> { }
        }
        jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(new TrainDriveMode(mode - 10));
    }

    private void sendTP(TrainProtectionType type) {
        tcc().setTrainProtectionType(type);
        jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(new TrainProtectionSetter(type.id));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        this.extractBackground(g, mouseX, mouseY, partial);
        super.extractRenderState(g, mouseX, mouseY, partial);

        int heightBase = this.height / 2 - 50;
        int widthBaseL = this.width / 2 - 135;
        int widthBaseR0 = this.width / 2 - 10;

        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.0"), widthBaseL + 20, heightBase - 25, 0xFFFFFF);
        Component mode = Component.translatable("atsassistmod.gui.TrainProtectionSelector.text."
                + (tcc != null ? (tcc.isATO() ? 3 : tcc.isTASC() ? 2 : 1) : 1));
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.7"), widthBaseL, heightBase, 0xFFFFFF);
        g.text(font, mode, widthBaseL + 55, heightBase, 0xFFFFFF);
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.6"), widthBaseL, heightBase + 25, 0xFFFFFF);
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.4"), widthBaseL, heightBase + 100, 0xFFFFFF);
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.5"), widthBaseR0 + 50, heightBase - 25, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
