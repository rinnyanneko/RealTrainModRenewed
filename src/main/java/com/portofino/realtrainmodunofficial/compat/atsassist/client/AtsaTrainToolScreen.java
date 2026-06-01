package com.portofino.realtrainmodunofficial.compat.atsassist.client;

import com.portofino.realtrainmodunofficial.compat.atsassist.network.AtsaTrainToolPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AtsaTrainToolScreen extends Screen {
    private final String mode;
    private EditBox keyBox;
    private EditBox valueBox;

    public AtsaTrainToolScreen(String mode) {
        super(Component.literal("ATS Assist - " + mode));
        this.mode = mode == null ? "datamap" : mode;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        int y = height / 2 - 38;
        keyBox = new EditBox(font, x, y, 220, 20, Component.literal("key"));
        keyBox.setValue("ATSAssist_Custom");
        addRenderableWidget(keyBox);
        valueBox = new EditBox(font, x, y + 30, 220, 20, Component.literal("value"));
        valueBox.setValue("protection".equals(mode) ? "ATS-P" : "");
        addRenderableWidget(valueBox);
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> submit(mode)).bounds(width / 2 - 80, y + 64, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("EB"), button -> submit("eb")).bounds(width / 2 + 5, y + 64, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(width / 2 - 37, y + 90, 75, 20).build());
    }

    private void submit(String submitMode) {
        PacketDistributor.sendToServer(new AtsaTrainToolPayload(submitMode, keyBox.getValue(), valueBox.getValue()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int y = height / 2 - 62;
        graphics.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("近くの列車に適用"), width / 2, y + 18, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
