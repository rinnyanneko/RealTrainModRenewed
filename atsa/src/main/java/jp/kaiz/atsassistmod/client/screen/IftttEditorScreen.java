package jp.kaiz.atsassistmod.client.screen;

import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import jp.kaiz.atsassistmod.ifttt.IFTTTType;
import jp.kaiz.atsassistmod.ifttt.IFTTTUtil;
import jp.kaiz.atsassistmod.ifttt.IftttFactory;
import jp.kaiz.atsassistmod.network.payload.IftttPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * IFTTT block editor (port of the GUIIFTTTMaterial main view). Lists the THIS
 * (condition) and THAT (action) rules, supports add/edit/delete, the any/all match
 * toggle, and saves to the server.
 */
public class IftttEditorScreen extends Screen {
    private final BlockPos pos;
    private final List<IFTTTContainer> thisList = new ArrayList<>();
    private final List<IFTTTContainer> thatList = new ArrayList<>();
    private boolean anyMatch;

    /** When non-null, the screen is in "pick a type to add" mode. */
    private Boolean addingThis;

    public IftttEditorScreen(IftttBlockEntity tile) {
        super(Component.literal("IFTTT"));
        this.pos = tile.getBlockPos();
        this.anyMatch = tile.isAnyMatch();
        // deep-copy via serialization so edits stay local until saved
        for (IFTTTContainer c : tile.getThisList()) {
            IFTTTContainer copy = roundTrip(c);
            if (copy != null) thisList.add(copy);
        }
        for (IFTTTContainer c : tile.getThatList()) {
            IFTTTContainer copy = roundTrip(c);
            if (copy != null) thatList.add(copy);
        }
    }

    private static IFTTTContainer roundTrip(IFTTTContainer c) {
        byte[] b = IFTTTUtil.toBytes(c);
        return b == null ? null : IFTTTUtil.fromBytes(b);
    }

    @Override
    protected void init() {
        if (addingThis != null) {
            initPicker(addingThis);
            return;
        }
        int colL = this.width / 2 - 160;
        int colR = this.width / 2 + 10;
        int top = 50;

        // THIS column
        for (int i = 0; i < thisList.size(); i++) {
            final int idx = i;
            IFTTTContainer c = thisList.get(i);
            addRenderableWidget(Button.builder(Component.literal(label(c)), b -> editEntry(c, idx, true))
                    .bounds(colL, top + i * 22, 130, 20).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> { thisList.remove(idx); rebuild(); })
                    .bounds(colL + 132, top + i * 22, 18, 20).build());
        }
        if (thisList.size() < 6) {
            addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.91.0"),
                    b -> { addingThis = true; rebuild(); }).bounds(colL, top + thisList.size() * 22, 150, 20).build());
        }

        // THAT column
        for (int i = 0; i < thatList.size(); i++) {
            final int idx = i;
            IFTTTContainer c = thatList.get(i);
            addRenderableWidget(Button.builder(Component.literal(label(c)), b -> editEntry(c, idx, false))
                    .bounds(colR, top + i * 22, 130, 20).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> { thatList.remove(idx); rebuild(); })
                    .bounds(colR + 132, top + i * 22, 18, 20).build());
        }
        if (thatList.size() < 6) {
            addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.91.0"),
                    b -> { addingThis = false; rebuild(); }).bounds(colR, top + thatList.size() * 22, 150, 20).build());
        }

        // any/all match toggle
        addRenderableWidget(Checkbox.builder(Component.literal("Any match (OR)"), this.font)
                .pos(this.width / 2 - 75, this.height - 78).selected(anyMatch)
                .onValueChange((cb, v) -> anyMatch = v).build());

        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.21"), b -> save())
                .bounds(this.width / 2 - 100, this.height - 52, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.20"), b -> onClose())
                .bounds(this.width / 2 + 5, this.height - 52, 95, 20).build());
    }

    private void initPicker(boolean isThis) {
        List<Integer> types = isThis ? IftttFactory.THIS_TYPES : IftttFactory.THAT_TYPES;
        int top = 40;
        for (int i = 0; i < types.size(); i++) {
            int typeId = types.get(i);
            IFTTTType.IFTTTEnumBase type = IFTTTType.getType(typeId);
            String label = type == null ? String.valueOf(typeId) : Component.translatable(type.getTranslationKey()).getString();
            addRenderableWidget(Button.builder(Component.literal(label), b -> pickType(typeId, isThis))
                    .bounds(this.width / 2 - 100, top + i * 22, 200, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.990"),
                b -> { addingThis = null; rebuild(); }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private void pickType(int typeId, boolean isThis) {
        IFTTTContainer created = IftttFactory.create(typeId);
        addingThis = null;
        if (created == null) {
            rebuild();
            return;
        }
        this.minecraft.setScreen(new IftttMaterialScreen(this, created, c -> {
            if (isThis) {
                if (thisList.size() < 6) thisList.add(c);
            } else {
                if (thatList.size() < 6) thatList.add(c);
            }
        }));
    }

    private void editEntry(IFTTTContainer c, int idx, boolean isThis) {
        this.minecraft.setScreen(new IftttMaterialScreen(this, c, edited -> {
            if (isThis) thisList.set(idx, edited); else thatList.set(idx, edited);
        }));
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private void save() {
        List<byte[]> thisData = new ArrayList<>();
        for (IFTTTContainer c : thisList) {
            byte[] b = IFTTTUtil.toBytes(c);
            if (b != null) thisData.add(b);
        }
        List<byte[]> thatData = new ArrayList<>();
        for (IFTTTContainer c : thatList) {
            byte[] b = IFTTTUtil.toBytes(c);
            if (b != null) thatData.add(b);
        }
        jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(new IftttPayloads.SaveIfttt(pos, anyMatch, thisData, thatData));
        onClose();
    }

    private static String label(IFTTTContainer c) {
        String[] exp = c.getExplanation();
        String head = Component.translatable(c.getTitle()).getString();
        return exp.length > 0 && !exp[0].isEmpty() ? head + " (" + exp[0] + ")" : head;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        this.extractBackground(g, mouseX, mouseY, partial);
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (addingThis == null) {
            g.text(font, "IF (THIS)", this.width / 2 - 160, 38, 0xFFFFFF);
            g.text(font, "THEN (THAT)", this.width / 2 + 10, 38, 0xFFFFFF);
        } else {
            g.centeredText(font, "Select type", this.width / 2, 24, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
