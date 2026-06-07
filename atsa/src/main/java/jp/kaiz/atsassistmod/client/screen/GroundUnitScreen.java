package jp.kaiz.atsassistmod.client.screen;

import jp.kaiz.atsassistmod.block.GroundUnitType;
import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SaveGroundUnit;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SetGroundUnitType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ground-unit configuration screen (port of GUIGroundUnit). For an unconfigured
 * unit it shows the function-selection menu; for a configured one it shows that
 * variant's options. Confirm sends a {@link SaveGroundUnit} payload.
 */
public class GroundUnitScreen extends Screen {
    private final GroundUnitBlockEntity tile;
    private Checkbox linkRedstone;
    private EditBox speedField;
    private EditBox distanceField;
    private Checkbox autoBrakeBox;
    private Checkbox useTrainDistanceBox;
    private final List<StateButton> stateButtons = new ArrayList<>();
    private TrainProtectionButton tpButton;

    public GroundUnitScreen(GroundUnitBlockEntity tile) {
        super(Component.literal("Ground Unit"));
        this.tile = tile;
    }

    private GroundUnitType type() {
        return tile.guType();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        GroundUnitType type = type();

        if (type == GroundUnitType.None) {
            initSelectionMenu(cx, cy);
            return;
        }

        // common: link-redstone checkbox + reset/confirm/cancel
        linkRedstone = Checkbox.builder(Component.empty(), this.font)
                .pos(cx + 45, cy - 50).selected(tile.isLinkRedStone()).build();
        addRenderableWidget(linkRedstone);

        switch (type) {
            case ATC_SpeedLimit_Notice -> {
                speedField = addField(cx, cy - 30, 3, String.valueOf(tile.getSpeedLimit()));
                distanceField = addField(cx, cy - 5, 5, String.valueOf(tile.getDistance()));
                autoBrakeBox = Checkbox.builder(Component.empty(), this.font).pos(cx + 45, cy + 25).selected(tile.isAutoBrake()).build();
                useTrainDistanceBox = Checkbox.builder(Component.empty(), this.font).pos(cx + 45, cy + 50).selected(tile.isUseTrainDistance()).build();
                addRenderableWidget(autoBrakeBox);
                addRenderableWidget(useTrainDistanceBox);
            }
            case ATC_SpeedLimit_Cancel -> {
                useTrainDistanceBox = Checkbox.builder(Component.empty(), this.font).pos(cx + 45, cy - 25).selected(tile.isUseTrainDistance()).build();
                addRenderableWidget(useTrainDistanceBox);
            }
            case TASC_StopPotion_Notice, TASC_StopPotion_Correction -> {
                distanceField = addField(cx, cy - 30, 5, String.valueOf(tile.getDistance()));
                useTrainDistanceBox = Checkbox.builder(Component.empty(), this.font).pos(cx + 45, cy).selected(tile.isUseTrainDistance()).build();
                addRenderableWidget(useTrainDistanceBox);
            }
            case ATO_Departure_Signal, ATO_Change_Speed ->
                    speedField = addField(cx, cy - 30, 3, String.valueOf(tile.getSpeedLimit()));
            case CHANGE_TP -> {
                tpButton = new TrainProtectionButton(cx - 75, cy - 25, tile.getTPType());
                addRenderableWidget(tpButton);
            }
            case TrainState_Set -> initTrainState(cx, cy);
            default -> { /* TASC_Cancel / TASC_StopPotion / ATO_Cancel / ATACS_Disable / Reset: only common */ }
        }

        // confirm / cancel
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.21"),
                b -> confirm()).bounds(cx - 110, this.height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.20"),
                b -> onClose()).bounds(cx + 10, this.height - 30, 100, 20).build());
    }

    private void initSelectionMenu(int cx, int cy) {
        addSel(1, cx - 170, cy - 75);
        addSel(2, cx - 50, cy - 75);
        addSel(3, cx + 70, cy - 75);
        addSel(4, cx - 170, cy - 35);
        addSel(5, cx - 50, cy - 35);
        addSel(6, cx - 170, cy - 10);
        addSel(7, cx - 50, cy - 10);
        addSel(9, cx - 170, cy + 30);
        addSel(10, cx - 50, cy + 30);
        addSel(11, cx + 70, cy + 30);
        addSel(13, cx + 70, cy + 70);
        addSel(14, cx - 170, cy + 70);
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.0.button.20"),
                b -> onClose()).bounds(cx - 50, this.height - 25, 100, 20).build());
    }

    private void addSel(int id, int x, int y) {
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.0.button." + id),
                b -> selectType(id)).bounds(x, y, 100, 20).build());
    }

    private void selectType(int id) {
        jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(new SetGroundUnitType(tile.getBlockPos(), id));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var state = tile.getBlockState().setValue(GroundUnitBlock.TYPE, GroundUnitType.getType(id).id);
            mc.level.setBlock(tile.getBlockPos(), state, 3);
        }
        mc.setScreen(new GroundUnitScreen(tile));
    }

    private void initTrainState(int cx, int cy) {
        int[] indices = {0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11}; // index 3 is skipped (as in the original)
        byte[] states = tile.getStates();
        int slot = 0;
        for (int i : indices) {
            StateButton sb = new StateButton(cx - 160 + 170 * (slot % 2), cy - 75 + 25 * (slot / 2), i,
                    states.length == 12 ? states[i] : (byte) (StateSpec.of(i).min - 1));
            stateButtons.add(sb);
            addRenderableWidget(sb);
            slot++;
        }
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.21"),
                b -> confirm()).bounds(cx - 110, this.height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.20"),
                b -> onClose()).bounds(cx + 10, this.height - 30, 100, 20).build());
    }

    private EditBox addField(int x, int y, int maxLen, String value) {
        EditBox box = new EditBox(this.font, x, y, 100, 20, Component.empty());
        box.setMaxLength(maxLen);
        box.setValue(value);
        box.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
        addRenderableWidget(box);
        return box;
    }

    private void confirm() {
        boolean link = linkRedstone != null && linkRedstone.selected();
        int speed = speedField != null ? parseInt(speedField.getValue()) : tile.getSpeedLimit();
        double distance = distanceField != null ? parseDouble(distanceField.getValue()) : tile.getDistance();
        boolean autoBrake = autoBrakeBox != null ? autoBrakeBox.selected() : tile.isAutoBrake();
        boolean useTd = useTrainDistanceBox != null ? useTrainDistanceBox.selected() : tile.isUseTrainDistance();
        byte[] states = tile.getStates().clone();
        if (!stateButtons.isEmpty()) {
            states = new byte[12];
            java.util.Arrays.fill(states, (byte) -1);
            for (StateButton sb : stateButtons) {
                states[sb.index] = sb.value;
            }
        }
        int tpType = tpButton != null ? tpButton.value.id : tile.getTPType().id;
        jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(new SaveGroundUnit(tile.getBlockPos(), link, speed, distance, autoBrake, useTd, states, tpType));
        onClose();
    }

    private static int parseInt(String s) {
        try { return s == null || s.isEmpty() ? 0 : Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return s == null || s.isEmpty() ? 0d : Double.parseDouble(s); } catch (NumberFormatException e) { return 0d; }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        this.extractBackground(g, mouseX, mouseY, partial);
        super.extractRenderState(g, mouseX, mouseY, partial);
        GroundUnitType type = type();
        String titleKey = switch (type) {
            case None -> "atsassistmod.gui.GroundUnitMenu.0.title";
            case ATC_SpeedLimit_Notice -> "atsassistmod.gui.GroundUnitMenu.1.title";
            case ATC_SpeedLimit_Cancel -> "atsassistmod.gui.GroundUnitMenu.2.title";
            case ATC_SpeedLimit_Reset -> "atsassistmod.gui.GroundUnitMenu.3.title";
            case TASC_StopPotion_Notice -> "atsassistmod.gui.GroundUnitMenu.4.title";
            case TASC_Cancel -> "atsassistmod.gui.GroundUnitMenu.5.title";
            case TASC_StopPotion_Correction -> "atsassistmod.gui.GroundUnitMenu.6.title";
            case TASC_StopPotion -> "atsassistmod.gui.GroundUnitMenu.7.title";
            case ATO_Departure_Signal -> "atsassistmod.gui.GroundUnitMenu.9.title";
            case ATO_Cancel -> "atsassistmod.gui.GroundUnitMenu.10.title";
            case ATO_Change_Speed -> "atsassistmod.gui.GroundUnitMenu.11.title";
            case TrainState_Set -> "atsassistmod.gui.GroundUnitMenu.13.title";
            case CHANGE_TP -> "atsassistmod.gui.GroundUnitMenu.14.title";
            case ATACS_Disable -> "atsassistmod.gui.GroundUnitMenu.15.title";
        };
        g.text(font, Component.translatable(titleKey), this.width / 4, 20, 0xFFFFFF);
        if (type != GroundUnitType.None && type != GroundUnitType.TrainState_Set && type != GroundUnitType.TASC_StopPotion) {
            g.text(font, Component.translatable("atsassistmod.gui.GroundUnitMenu.common.text.0"),
                    this.width / 2 - 100, this.height / 2 - 50, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // -------- TrainState cycle button --------
    private record StateSpec(int displayKey, int min, int max, int dataCount) {
        static StateSpec of(int index) {
            return switch (index) {
                case 0 -> new StateSpec(0, 0, 2, 0);
                case 1 -> new StateSpec(1, -8, 5, 0);
                case 2 -> new StateSpec(2, 0, 9, 0);
                case 4 -> new StateSpec(3, 0, 3, 4);
                case 5 -> new StateSpec(4, 0, 2, 3);
                case 6 -> new StateSpec(5, 0, 1, 2);
                case 7 -> new StateSpec(6, 0, 1, 0);
                case 8 -> new StateSpec(7, 0, 9, 0);
                case 9 -> new StateSpec(8, 0, 9, 0);
                case 10 -> new StateSpec(9, 0, 2, 3);
                case 11 -> new StateSpec(10, 0, 2, 3);
                default -> new StateSpec(0, 0, 0, 0);
            };
        }
    }

    private static final class StateButton extends Button {
        final int index;
        final StateSpec spec;
        byte value;

        StateButton(int x, int y, int index, byte value) {
            super(x, y, 150, 20, Component.empty(), b -> {}, DEFAULT_NARRATION);
            this.index = index;
            this.spec = StateSpec.of(index);
            this.value = value;
            updateMessage();
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            int sentinel = spec.min - 1;
            value = (value >= spec.max) ? (byte) sentinel : (byte) (value + 1);
            if (value < sentinel) {
                value = (byte) sentinel;
            }
            updateMessage();
        }

        private void updateMessage() {
            int key = spec.displayKey;
            String state;
            // light has two state labels (4.state.0 / 4.state.1)
            if (key == 4 && value == 2) {
                state = Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.4.state.1").getString();
            } else if (key == 4) {
                state = Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.4.state.0").getString();
            } else {
                state = Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider." + key + ".state").getString();
            }
            String data;
            if (value < spec.min) {
                data = Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.notchange").getString();
            } else if (spec.dataCount > 0) {
                data = Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider." + key + ".data." + value).getString();
            } else {
                data = String.valueOf(value);
            }
            setMessage(Component.literal(state + ":" + data));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            g.centeredText(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFF);
        }
    }

    // -------- Train protection cycle button --------
    private static final class TrainProtectionButton extends Button {
        TrainProtectionType value;

        TrainProtectionButton(int x, int y, TrainProtectionType value) {
            super(x, y, 150, 20, Component.empty(), b -> {}, DEFAULT_NARRATION);
            this.value = value;
            updateMessage();
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            TrainProtectionType[] all = TrainProtectionType.values();
            value = all[(value.ordinal() + 1) % all.length];
            updateMessage();
        }

        private void updateMessage() {
            setMessage(Component.translatable(value.getTranslationKey()));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            g.centeredText(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFF);
        }
    }
}
