package com.portofino.realtrainmodunofficial.compat.atsassist.client;

import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaSimpleBlockEntity;
import com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaSimpleBlockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AtsaSimpleBlockScreen extends Screen {
    private final BlockPos pos;
    private EditBox conditionBox;
    private EditBox actionBox;
    private EditBox announceBox;
    private Checkbox anyMatchBox;

    public AtsaSimpleBlockScreen(BlockPos pos, Component title) {
        super(title);
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        String condition = "train";
        String action = "redstone=15";
        String announce = "";
        boolean anyMatch = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AtsaSimpleBlockEntity blockEntity) {
            condition = blockEntity.getCondition();
            action = blockEntity.getAction();
            announce = blockEntity.getAnnounce();
            anyMatch = blockEntity.isAnyMatch();
        }
        int x = width / 2 - 150;
        int y = height / 2 - 74;
        conditionBox = new EditBox(font, x, y, 300, 20, Component.literal("condition"));
        conditionBox.setMaxLength(512);
        conditionBox.setValue(condition);
        addRenderableWidget(conditionBox);
        actionBox = new EditBox(font, x, y + 32, 300, 20, Component.literal("action"));
        actionBox.setMaxLength(512);
        actionBox.setValue(action);
        addRenderableWidget(actionBox);
        announceBox = new EditBox(font, x, y + 64, 300, 20, Component.literal("announce"));
        announceBox.setMaxLength(256);
        announceBox.setValue(announce);
        addRenderableWidget(announceBox);
        anyMatchBox = Checkbox.builder(Component.literal("Any"), font).pos(x, y + 92).selected(anyMatch).build();
        addRenderableWidget(anyMatchBox);
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> submit())
            .bounds(width / 2 - 80, y + 122, 75, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 5, y + 122, 75, 20)
            .build());
    }

    private void submit() {
        PacketDistributor.sendToServer(new ConfigureAtsaSimpleBlockPayload(
            pos,
            conditionBox.getValue(),
            actionBox.getValue(),
            announceBox.getValue(),
            anyMatchBox.selected()
        ));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 150;
        int y = height / 2 - 98;
        graphics.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        graphics.drawString(font, "This", x, y + 24, 0xD8D8D8);
        graphics.drawString(font, "That", x, y + 56, 0xD8D8D8);
        graphics.drawString(font, "Announce", x, y + 88, 0xD8D8D8);
        graphics.drawString(font, "Examples: train; speed>40; redstone / redstone=15; notch=-3; data:key=value", x, y + 142, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
