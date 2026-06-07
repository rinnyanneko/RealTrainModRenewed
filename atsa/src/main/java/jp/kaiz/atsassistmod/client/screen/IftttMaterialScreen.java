package jp.kaiz.atsassistmod.client.screen;

import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import jp.kaiz.atsassistmod.ifttt.IftttEditView;
import jp.kaiz.atsassistmod.util.CardinalDirection;
import jp.kaiz.atsassistmod.util.ComparisonManager;
import jp.kaiz.atsassistmod.util.DataType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edits a single IFTTT container (port of GUIIFTTTMaterial). Provides six generic
 * text fields read by {@link IFTTTContainer#setFromGui}, a per-type option cycle
 * button, and an "only once" toggle.
 */
public class IftttMaterialScreen extends Screen implements IftttEditView {
    private final Screen parent;
    private final IFTTTContainer container;
    private final Consumer<IFTTTContainer> onDone;
    private final List<EditBox> fields = new ArrayList<>();
    private Checkbox onceBox;

    public IftttMaterialScreen(Screen parent, IFTTTContainer container, Consumer<IFTTTContainer> onDone) {
        super(Component.translatable(container.getTitle()));
        this.parent = parent;
        this.container = container;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = 50;
        fields.clear();
        for (int i = 0; i < 6; i++) {
            EditBox box = new EditBox(this.font, cx - 100, top + i * 24, 200, 20, Component.empty());
            box.setMaxLength(64);
            fields.add(box);
            addRenderableWidget(box);
        }
        onceBox = Checkbox.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.211.0"), this.font)
                .pos(cx - 100, top + 6 * 24).selected(container.isOnce()).build();
        addRenderableWidget(onceBox);

        if (hasOption()) {
            addRenderableWidget(Button.builder(Component.literal("Option: " + optionLabel()), b -> {
                cycleOption();
                b.setMessage(Component.literal("Option: " + optionLabel()));
            }).bounds(cx - 100, top + 6 * 24 + 24, 200, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.91.1"), b -> done())
                .bounds(cx - 100, this.height - 28, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.990"), b -> back())
                .bounds(cx + 5, this.height - 28, 95, 20).build());
    }

    private void done() {
        container.setFromGui(this);
        container.setOnce(onceBox.selected());
        onDone.accept(container);
        this.minecraft.setScreen(parent);
    }

    private void back() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        this.extractBackground(g, mouseX, mouseY, partial);
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, Component.translatable(container.getTitle()), this.width / 2, 20, 0xFFFFFF);
        String[] exp = container.getExplanation();
        for (int i = 0; i < exp.length; i++) {
            g.text(font, exp[i], this.width / 2 - 100, 36 + i * 0, 0x888888);
        }
    }

    // ---- IftttEditView ----
    @Override
    public String getTextFieldText(int index) {
        return index >= 0 && index < fields.size() ? fields.get(index).getValue() : "";
    }

    @Override
    public int getTextFieldInt(int index) {
        try {
            return Integer.parseInt(getTextFieldText(index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int textFieldLength() {
        return fields.size();
    }

    // ---- per-type option cycling ----
    private boolean hasOption() {
        return container instanceof IFTTTContainer.This.Minecraft.RedStoneInput
                || container instanceof IFTTTContainer.This.RTM.SimpleDetectTrain
                || container instanceof IFTTTContainer.This.RTM.Cars
                || container instanceof IFTTTContainer.This.RTM.Speed
                || container instanceof IFTTTContainer.This.RTM.TrainDataMap
                || container instanceof IFTTTContainer.This.RTM.TrainDirection
                || container instanceof IFTTTContainer.That.Minecraft.RedStoneOutput
                || container instanceof IFTTTContainer.That.RTM.DataMap;
    }

    private String optionLabel() {
        if (container instanceof IFTTTContainer.This.Minecraft.RedStoneInput c) return c.getMode().name;
        if (container instanceof IFTTTContainer.This.RTM.SimpleDetectTrain c) return c.getDetectMode().name();
        if (container instanceof IFTTTContainer.This.RTM.Cars c) return c.getMode().getName();
        if (container instanceof IFTTTContainer.This.RTM.Speed c) return c.getMode().getName();
        if (container instanceof IFTTTContainer.This.RTM.TrainDataMap c) return c.getDataType().key;
        if (container instanceof IFTTTContainer.This.RTM.TrainDirection c) return c.getDirection().name();
        if (container instanceof IFTTTContainer.That.Minecraft.RedStoneOutput c) return c.isTrainCarsOutput() ? "cars" : "level";
        if (container instanceof IFTTTContainer.That.RTM.DataMap c) return c.getDataType().key;
        return "";
    }

    private void cycleOption() {
        if (container instanceof IFTTTContainer.This.Minecraft.RedStoneInput c) {
            var v = IFTTTContainer.This.Minecraft.RedStoneInput.ModeType.values();
            c.setMode(v[(c.getMode().ordinal() + 1) % v.length]);
        } else if (container instanceof IFTTTContainer.This.RTM.SimpleDetectTrain c) {
            var v = IFTTTContainer.This.RTM.SimpleDetectTrain.DetectMode.values();
            c.setDetectMode(v[(c.getDetectMode().ordinal() + 1) % v.length]);
        } else if (container instanceof IFTTTContainer.This.RTM.Cars c) {
            var v = ComparisonManager.Integer.values();
            c.setMode(v[(c.getMode().ordinal() + 1) % v.length]);
        } else if (container instanceof IFTTTContainer.This.RTM.Speed c) {
            var v = ComparisonManager.Integer.values();
            c.setMode(v[(c.getMode().ordinal() + 1) % v.length]);
        } else if (container instanceof IFTTTContainer.This.RTM.TrainDataMap c) {
            var v = DataType.values();
            c.setDataType(v[(c.getDataType().ordinal() + 1) % v.length]);
        } else if (container instanceof IFTTTContainer.This.RTM.TrainDirection c) {
            var v = CardinalDirection.values();
            c.setDirection(v[(c.getDirection().ordinal() + 1) % v.length]);
        } else if (container instanceof IFTTTContainer.That.Minecraft.RedStoneOutput c) {
            c.setTrainCarsOutput(!c.isTrainCarsOutput());
        } else if (container instanceof IFTTTContainer.That.RTM.DataMap c) {
            var v = DataType.values();
            c.setDataType(v[(c.getDataType().ordinal() + 1) % v.length]);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
