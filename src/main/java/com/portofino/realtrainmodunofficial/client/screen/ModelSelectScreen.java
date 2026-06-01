package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.PackButtonTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ModelSelectScreen extends Screen {
    public record SelectionResult(String modelId, String dataMapValue) {}

    public record ModelInfo(String id, String displayName, String packName, String buttonTexture, String category) {
        public ModelInfo(String id, String displayName, String packName, String buttonTexture) {
            this(id, displayName, packName, buttonTexture, "");
        }
    }

    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM_MARGIN = 16;
    private static final int ITEM_HEIGHT_MIN = 38;
    private static final int ITEM_HEIGHT_MAX = 72;

    private final List<ModelInfo> models;
    private final Consumer<SelectionResult> onSelected;
    private final String initialSelectedId;
    private final String initialDataMapValue;
    private ModelList modelList;
    private EditBox dataMapBox;

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected) {
        this(title, models, onSelected, null, "");
    }

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected, String initialSelectedId, String initialDataMapValue) {
        super(title);
        this.models = models.stream()
            .sorted(Comparator
                .comparing((ModelInfo info) -> safe(info.category()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(info -> safe(info.displayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(info -> safe(info.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();
        this.onSelected = onSelected;
        this.initialSelectedId = initialSelectedId;
        this.initialDataMapValue = initialDataMapValue == null ? "" : initialDataMapValue;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void init() {
        int listWidth = Mth.clamp(width / 2 - 28, 220, 360);
        int itemHeight = computeItemHeight(listWidth);
        modelList = new ModelList(minecraft, listWidth, height - LIST_BOTTOM_MARGIN, LIST_TOP, itemHeight);
        addRenderableWidget(modelList);

        int rightLeft = listWidth + 22;
        int rightWidth = Math.max(180, width - rightLeft - 18);
        dataMapBox = new EditBox(font, rightLeft, LIST_TOP + 6, rightWidth, 20, Component.literal("no_name"));
        dataMapBox.setValue(initialDataMapValue);
        addRenderableWidget(dataMapBox);
        setInitialFocus(dataMapBox);

        int buttonWidth = Math.min(150, Math.max(72, (rightWidth - 6) / 2));
        int buttonsLeft = rightLeft;
        int buttonsTop = dataMapBox.getY() + dataMapBox.getHeight() + 8;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> {
            ModelList.ModelEntry selected = modelList.getSelected();
            if (selected != null) {
                onSelected.accept(new SelectionResult(selected.id, dataMapBox == null ? "" : dataMapBox.getValue()));
            }
            onClose();
        }).bounds(buttonsLeft, buttonsTop, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
            .bounds(buttonsLeft + buttonWidth + 6, buttonsTop, buttonWidth, 20)
            .build());
    }

    private int computeItemHeight(int listWidth) {
        int itemHeight = ITEM_HEIGHT_MIN;
        for (ModelInfo info : models) {
            PackButtonTextureCache.ButtonTextureInfo buttonTexture = PackButtonTextureCache.get(info.packName(), info.buttonTexture());
            if (buttonTexture == null) {
                continue;
            }
            int sourceHeight = Math.max(1, buttonTexture.sourceHeight());
            itemHeight = Math.max(itemHeight, Mth.clamp(sourceHeight + 4, ITEM_HEIGHT_MIN, ITEM_HEIGHT_MAX));
        }
        return itemHeight;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, getTitle(), width / 2, 10, 0xFFFFFF);
        if (dataMapBox != null) {
            graphics.drawString(font, Component.literal("no_name"), dataMapBox.getX(), dataMapBox.getY() - 12, 0xFFFFFF, false);
        }
        if (models.isEmpty()) {
            graphics.drawCenteredString(font,
                Component.translatable("screen.realtrainmodunofficial.no_models"),
                width / 2, height / 2, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ModelList extends ObjectSelectionList<ModelList.ModelEntry> {
        ModelList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            setRenderHeader(false, 0);
            String lastCategory = null;
            ModelEntry initialEntry = null;
            int initialRow = -1;
            int row = 0;
            for (ModelInfo info : models) {
                // packName（zipファイル名）を category fallback に使わない。
                // vehicleType が無い項目はヘッダーなしでフラットに並べる。
                String category = info.category();
                if (category != null && !category.isBlank() && !category.equals(lastCategory)) {
                    addEntry(new ModelEntry(category));
                    lastCategory = category;
                    row++;
                }
                ModelEntry entry = new ModelEntry(info.id(), info.displayName(), info.packName(), info.buttonTexture());
                addEntry(entry);
                if (initialSelectedId != null && initialSelectedId.equals(info.id())) {
                    initialEntry = entry;
                    initialRow = row;
                }
                row++;
            }
            if (initialEntry == null) {
                for (ModelEntry entry : children()) {
                    if (!entry.header) {
                        initialEntry = entry;
                        initialRow = 0;
                        break;
                    }
                }
            }
            if (initialEntry != null) {
                setSelected(initialEntry);
                setScrollAmount(Math.max(0.0D, initialRow * (double) itemHeight - height * 0.5D));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return width - 8;
        }

        @Override
        public int getRowWidth() {
            return width - 20;
        }

        class ModelEntry extends ObjectSelectionList.Entry<ModelEntry> {
            public final String id;
            private final Component label;
            private final PackButtonTextureCache.ButtonTextureInfo buttonTexture;
            private final boolean header;

            ModelEntry(String category) {
                this.id = "";
                this.label = Component.literal(category);
                this.buttonTexture = null;
                this.header = true;
            }

            ModelEntry(String id, String displayName, String packName, String buttonTexturePath) {
                this.id = id;
                String text = (displayName != null && !displayName.isBlank()) ? displayName : id;
                this.label = Component.literal(text);
                this.buttonTexture = PackButtonTextureCache.get(packName, buttonTexturePath);
                this.header = false;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width,
                               int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                if (header) {
                    graphics.drawString(ModelSelectScreen.this.font, label, left + 8, top + 14, 0xFFD8D8D8, false);
                    return;
                }
                int previewLeft = left;
                int previewTop = top;
                int previewHeight = height;
                int previewWidth = Math.max(1, width);
                if (buttonTexture != null) {
                    int sourceX = buttonTexture.sourceX();
                    int sourceY = buttonTexture.sourceY();
                    int sourceWidth = Math.max(1, buttonTexture.sourceWidth());
                    int sourceHeight = Math.max(1, buttonTexture.sourceHeight());
                    // Fit within cell: scale to fit both dimensions, maintain aspect ratio
                    float scaleH = (float) previewHeight / sourceHeight;
                    float scaleW = (float) previewWidth / sourceWidth;
                    float scale = Math.min(scaleH, scaleW);
                    int destWidth = Math.max(1, Math.round(sourceWidth * scale));
                    int destHeight = Math.max(1, Math.round(sourceHeight * scale));
                    int offsetX = (previewWidth - destWidth) / 2;
                    int offsetY = (previewHeight - destHeight) / 2;
                    graphics.blit(
                        buttonTexture.location(),
                        previewLeft + offsetX,
                        previewTop + offsetY,
                        destWidth,
                        destHeight,
                        sourceX,
                        sourceY,
                        sourceWidth,
                        sourceHeight,
                        buttonTexture.width(),
                        buttonTexture.height()
                    );
                } else {
                    graphics.drawCenteredString(ModelSelectScreen.this.font, label, previewLeft + (previewWidth / 2), top + (height - 8) / 2, 0xAAAAAA);
                }
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (header) {
                    return false;
                }
                if (button == 0) {
                    ModelList.this.setSelected(this);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return label;
            }
        }
    }
}
