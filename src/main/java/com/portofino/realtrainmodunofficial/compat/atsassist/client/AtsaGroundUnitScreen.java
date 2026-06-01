package com.portofino.realtrainmodunofficial.compat.atsassist.client;

import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaGroundUnitBlockEntity;
import com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaGroundUnitPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AtsaGroundUnitScreen extends Screen {
    private final BlockPos pos;
    private EditBox typeBox;
    private EditBox speedBox;
    private EditBox stopDistanceBox;
    private EditBox protectionBox;
    private EditBox statesBox;
    private Checkbox tascBox;
    private Checkbox linkRedstoneBox;
    private Checkbox autoBrakeBox;
    private Checkbox useTrainDistanceBox;
    private int redstoneOutput;

    public AtsaGroundUnitScreen(BlockPos pos) {
        super(Component.literal("ATS Assist - Ground Unit"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        int type = 0;
        int speed = 45;
        int distance = 80;
        boolean tasc = true;
        boolean linkRedstone = false;
        boolean autoBrake = true;
        boolean useTrainDistance = false;
        String protection = "ATS-P";
        byte[] states = new byte[]{-1, -9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AtsaGroundUnitBlockEntity blockEntity) {
            type = blockEntity.getUnitType();
            speed = blockEntity.getSpeedLimitKmh();
            distance = blockEntity.getStopDistance();
            tasc = blockEntity.isTascEnabled();
            protection = blockEntity.getTrainProtection();
            linkRedstone = blockEntity.isLinkRedstone();
            autoBrake = blockEntity.isAutoBrake();
            useTrainDistance = blockEntity.isUseTrainDistance();
            states = blockEntity.getTrainStates();
            redstoneOutput = blockEntity.getRedstoneOutput();
        }

        int x = width / 2 - 110;
        int y = height / 2 - 94;
        typeBox = box(x, y, "type", Integer.toString(type));
        speedBox = box(x, y + 28, "speed", Integer.toString(speed));
        stopDistanceBox = box(x, y + 56, "distance", Integer.toString(distance));
        protectionBox = box(x, y + 84, "protection", protection);
        statesBox = box(x, y + 112, "states", joinStates(states));
        tascBox = Checkbox.builder(Component.literal("TASC"), font).pos(x + 148, y + 28).selected(tasc).build();
        addRenderableWidget(tascBox);
        linkRedstoneBox = Checkbox.builder(Component.literal("RS"), font).pos(x + 148, y + 52).selected(linkRedstone).build();
        addRenderableWidget(linkRedstoneBox);
        autoBrakeBox = Checkbox.builder(Component.literal("AutoB"), font).pos(x + 148, y + 76).selected(autoBrake).build();
        addRenderableWidget(autoBrakeBox);
        useTrainDistanceBox = Checkbox.builder(Component.literal("CarLen"), font).pos(x + 148, y + 100).selected(useTrainDistance).build();
        addRenderableWidget(useTrainDistanceBox);
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> submit()).bounds(width / 2 - 80, y + 148, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(width / 2 + 5, y + 148, 75, 20).build());
    }

    private EditBox box(int x, int y, String label, String value) {
        EditBox editBox = new EditBox(font, x, y, 130, 20, Component.literal(label));
        editBox.setValue(value);
        addRenderableWidget(editBox);
        return editBox;
    }

    private void submit() {
        try {
            PacketDistributor.sendToServer(new ConfigureAtsaGroundUnitPayload(
                pos,
                Integer.parseInt(typeBox.getValue().trim()),
                Integer.parseInt(speedBox.getValue().trim()),
                Integer.parseInt(stopDistanceBox.getValue().trim()),
                tascBox.selected(),
                protectionBox.getValue().trim(),
                linkRedstoneBox.selected(),
                autoBrakeBox.selected(),
                useTrainDistanceBox.selected(),
                parseStates(statesBox.getValue())
            ));
            onClose();
        } catch (NumberFormatException ignored) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("数字で入力してください"), true);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 110;
        int y = height / 2 - 96;
        graphics.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        graphics.drawString(font, "Type", x, y + 28, 0xD8D8D8);
        graphics.drawString(font, "Speed Limit km/h", x, y + 56, 0xD8D8D8);
        graphics.drawString(font, "Stop Distance", x, y + 84, 0xD8D8D8);
        graphics.drawString(font, "Protection", x, y + 112, 0xD8D8D8);
        graphics.drawString(font, "States", x, y + 140, 0xD8D8D8);
        graphics.drawString(font, "RS " + redstoneOutput, x + 148, y + 124, redstoneOutput > 0 ? 0xFF6666 : 0x66FF66);
        graphics.drawString(font, "0 none / 1 ATC / 2 cancel / 3 reset / 4 TASC / 7 stop / 9 ATO / 13 state / 14 TP", x, y + 190, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String joinStates(byte[] states) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < states.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(states[i]);
        }
        return builder.toString();
    }

    private static byte[] parseStates(String value) {
        byte[] states = new byte[]{-1, -9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        String[] parts = value.split(",");
        for (int i = 0; i < Math.min(states.length, parts.length); i++) {
            try {
                states[i] = Byte.parseByte(parts[i].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return states;
    }
}
