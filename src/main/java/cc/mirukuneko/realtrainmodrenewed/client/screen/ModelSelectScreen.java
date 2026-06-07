package cc.mirukuneko.realtrainmodrenewed.client.screen;

import cc.mirukuneko.realtrainmodrenewed.client.PackButtonTextureCache;
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader;
import cc.mirukuneko.realtrainmodrenewed.client.renderer.BogieRenderer;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry;
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ModelSelectScreen extends Screen {
    public record SelectionResult(String modelId, String dataMapValue) {}

    public record ModelInfo(String id, String displayName, String packName, String buttonTexture, String category) {
        public ModelInfo(String id, String displayName, String packName, String buttonTexture) {
            this(id, displayName, packName, buttonTexture, "");
        }
    }

    // RTM 本家どおり: ボタン高さ 32px 固定、ボタン幅 160px 固定
    private static final int BTN_W = 160;
    private static final int BTN_H = 32;
    private static final int LIST_TOP = 24;
    private static final int LIST_BOTTOM_MARGIN = 4;

    // 3D プレビューモデルキャッシュ (id → model or null)
    private static final Map<String, MqoModelLoader.MqoModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_MODEL_CACHE = ConcurrentHashMap.newKeySet();

    private final List<ModelInfo> models;
    private final Consumer<SelectionResult> onSelected;
    private final String initialSelectedId;
    private final String initialDataMapValue;

    private ModelList modelList;
    private EditBox dataMapBox;
    private String selectedId = null;

    // 3Dプレビューのマウス操作回転。ユーザーがドラッグするまでは自動回転。
    private float previewYaw = 0.0f;
    private float previewPitch = 15.0f;
    private boolean previewUserRotated = false;
    private boolean previewDragging = false;
    private double lastDragX, lastDragY;
    // マウスホイールでのズーム倍率(1.0=既定)。
    private float previewZoom = 1.0f;
    // プレビューでスクリプトを適用するためのワールド未追加の一時車両エンティティ(車両IDごとにキャッシュ)。
    private TrainEntity previewEntity;
    private String previewEntityId;

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected) {
        this(title, models, onSelected, null, "");
    }

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected,
                             String initialSelectedId, String initialDataMapValue) {
        super(title);
        this.models = models.stream()
            .sorted(Comparator
                .comparing((ModelInfo i) -> safe(i.category()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.displayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();
        this.onSelected = onSelected;
        this.initialSelectedId = initialSelectedId;
        this.initialDataMapValue = initialDataMapValue == null ? "" : initialDataMapValue;
    }

    private static String safe(String v) { return v == null ? "" : v; }

    // ---- レイアウト計算 ----
    private int listWidth() { return BTN_W + 16; }
    private int rightLeft() { return listWidth() + 4; }
    private int rightWidth() { return Math.max(100, width - rightLeft() - 4); }
    private int previewSize() { return Math.min(rightWidth(), height - LIST_TOP - 60); }

    private String fitText(String text, int maxWidth) {
        if (text == null || text.isBlank() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (maxWidth <= suffixWidth) {
            return font.plainSubstrByWidth(text, Math.max(0, maxWidth));
        }
        return font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix;
    }

    /** マウス座標が3Dプレビュー領域内か。 */
    private boolean isInPreviewArea(double mx, double my) {
        int rl = rightLeft();
        int rw = rightWidth();
        int ps = previewSize();
        return mx >= rl && mx <= rl + rw && my >= LIST_TOP && my <= LIST_TOP + ps;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0 && isInPreviewArea(mx, my)) {
            previewDragging = true;
            lastDragX = mx;
            lastDragY = my;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mx = event.x();
        double my = event.y();
        if (previewDragging && event.button() == 0) {
            previewUserRotated = true;
            previewYaw += (float) (mx - lastDragX) * 0.8f;
            previewPitch += (float) (my - lastDragY) * 0.8f;
            previewPitch = Mth.clamp(previewPitch, -89.0f, 89.0f);
            lastDragX = mx;
            lastDragY = my;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && previewDragging) {
            previewDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isInPreviewArea(mx, my)) {
            previewZoom = Mth.clamp(previewZoom * (scrollY > 0 ? 1.1f : 1.0f / 1.1f), 0.3f, 6.0f);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    protected void init() {
        int lw = listWidth();
        int listHeight = height - LIST_TOP - LIST_BOTTOM_MARGIN;
        modelList = new ModelList(minecraft, lw, listHeight, LIST_TOP, BTN_H);
        addRenderableWidget(modelList);

        int rl = rightLeft();
        int rw = rightWidth();
        int datamapY = LIST_TOP + previewSize() + 8;
        dataMapBox = new EditBox(font, rl, datamapY, rw, 20, Component.empty());
        dataMapBox.setValue(initialDataMapValue);
        addRenderableWidget(dataMapBox);

        int bw = Math.min(100, Math.max(60, (rw - 4) / 2));
        int btnY = datamapY + 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> {
            var sel = modelList.getSelected();
            if (sel != null && !sel.header) {
                onSelected.accept(new SelectionResult(sel.id, dataMapBox == null ? "" : dataMapBox.getValue()));
            }
            onClose();
        }).bounds(rl, btnY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
            .bounds(rl + bw + 4, btnY, bw, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, getTitle(), listWidth() / 2, 7, 0xFFFFFF);

        // 右パネル: 3D プレビュー
        int rl = rightLeft();
        int rw = rightWidth();
        int ps = previewSize();
        int previewLeft = rl;
        int previewTop = LIST_TOP;

        graphics.fill(previewLeft, previewTop, previewLeft + rw, previewTop + ps, 0x88000000);

        var sel = modelList.getSelected();
        if (sel != null && !sel.header) {
            renderPreviewPanel(graphics, previewLeft, previewTop, rw, ps, sel);
        }

        if (models.isEmpty()) {
            graphics.centeredText(font,
                Component.translatable("screen.realtrainmodrenewed.no_models"),
                previewLeft + rw / 2, previewTop + ps / 2, 0xAAAAAA);
        }
    }

    private void renderPreviewPanel(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
                                    ModelList.ModelEntry entry) {
        String name = entry.label.getString();
        graphics.text(font, Component.literal(fitText(name, width - 12)), left + 6, top + 6, 0xFFFFFFFF, false);

        int infoTop = top + height - 31;
        graphics.fill(left, infoTop - 4, left + width, top + height, 0xAA000000);
        graphics.text(font, Component.literal(fitText(entry.packName, width - 12)), left + 6, infoTop, 0xFFB8B8C8, false);
        graphics.text(font, Component.literal(fitText(entry.id, width - 12)), left + 6, infoTop + 11, 0xFF8888A0, false);

        int imageTop = top + 24;
        int imageHeight = Math.max(20, infoTop - imageTop - 8);
        if (entry.buttonTex != null) {
            int drawW = Math.min(width - 24, BTN_W * 2);
            int drawH = Math.max(BTN_H, Math.min(imageHeight, Math.round(drawW * (BTN_H / (float) BTN_W))));
            int drawX = left + (width - drawW) / 2;
            int drawY = imageTop + Math.max(0, (imageHeight - drawH) / 2);
            graphics.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0xFF101018);
            graphics.blit(RenderPipelines.GUI_TEXTURED, entry.buttonTex.location(),
                drawX, drawY, drawW, drawH,
                entry.buttonTex.sourceX(), entry.buttonTex.sourceY(),
                entry.buttonTex.sourceWidth(), entry.buttonTex.sourceHeight(),
                entry.buttonTex.width(), entry.buttonTex.height());
        } else {
            int boxW = Math.min(width - 24, 220);
            int boxH = Math.min(imageHeight, 64);
            int boxX = left + (width - boxW) / 2;
            int boxY = imageTop + Math.max(0, (imageHeight - boxH) / 2);
            graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF111118);
            graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF5A5A70);
            graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF5A5A70);
            graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF5A5A70);
            graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF5A5A70);
            graphics.centeredText(font, Component.literal("No preview image"), left + width / 2,
                imageTop + imageHeight / 2 - 4, 0xFFAAAAAA);
        }
    }

    private static float[] computePreviewBounds(MqoModelLoader.MqoModel model, VehicleDefinition vehicleDef) {
        float[] bounds = model.computeBounds();
        if (vehicleDef == null) {
            return bounds;
        }

        float scale = vehicleDef.getModelScale();
        Vec3 offset = vehicleDef.getModelOffset();
        float minX = (float) (bounds[0] * scale + offset.x);
        float minY = (float) (bounds[1] * scale + offset.y);
        float minZ = (float) (bounds[2] * scale + offset.z);
        float maxX = (float) (bounds[3] * scale + offset.x);
        float maxY = (float) (bounds[4] * scale + offset.y);
        float maxZ = (float) (bounds[5] * scale + offset.z);

        for (VehicleDefinition.BogieDefinition bogie : vehicleDef.getBogies()) {
            if (bogie == null || bogie.position() == null) {
                continue;
            }
            Vec3 pos = bogie.position();
            float x = (float) (pos.x * scale + offset.x);
            float y = (float) ((pos.y + 0.24D) * scale + offset.y);
            float z = (float) (pos.z * scale + offset.z);
            minX = Math.min(minX, x - 1.0F * scale);
            minY = Math.min(minY, y - 1.0F * scale);
            minZ = Math.min(minZ, z - 1.0F * scale);
            maxX = Math.max(maxX, x + 1.0F * scale);
            maxY = Math.max(maxY, y + 1.0F * scale);
            maxZ = Math.max(maxZ, z + 1.0F * scale);
        }

        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    private static void renderStablePreviewModel(MqoModelLoader.MqoModel model, VehicleDefinition vehicleDef,
                                                 PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                                 Object previewEnt) {
        poseStack.pushPose();
        try {
            if (vehicleDef != null) {
                Vec3 offset = vehicleDef.getModelOffset();
                poseStack.translate(offset.x, offset.y, offset.z);
                float modelScale = vehicleDef.getModelScale();
                poseStack.scale(modelScale, modelScale, modelScale);
            }

            MqoModelLoader.GroupPredicate previewFilter = ModelSelectScreen::shouldRenderPreviewGroup;
            boolean rendered = false;
            if (vehicleDef != null && model.hasRenderScript()) {
                try {
                    // スクリプトは entity を参照するため、プレビュー用の一時車両エンティティを渡す。
                    // null を渡すと多くのスクリプトが例外を投げてフォールバック(=非適用)になっていた。
                    MqoModelLoader.renderModel(model, poseStack, buffer, LightCoordsUtil.FULL_BRIGHT, previewFilter, null, previewEnt);
                    rendered = true;
                } catch (Throwable ignored) {
                    rendered = false;
                }
            }
            if (!rendered) {
                MqoModelLoader.renderModelWithoutScript(
                    model, poseStack, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    false, previewFilter, null);
                MqoModelLoader.renderModelWithoutScript(
                    model, poseStack, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    true, previewFilter, null);
            }

            if (vehicleDef != null) {
                boolean selfDrawsRunningGear = model.hasOwnWheelGroups();
                for (int i = 0; i < vehicleDef.getBogies().size(); i++) {
                    VehicleDefinition.BogieDefinition bogieDef = vehicleDef.getBogies().get(i);
                    if (shouldSkipPreviewBogie(selfDrawsRunningGear, bogieDef)) {
                        continue;
                    }
                    try {
                        BogieRenderer.renderBogie(
                            poseStack, i, bogieDef, vehicleDef,
                            null, buffer, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F);
                    } catch (Throwable ignored) {
                        // モデル選択画面では1つの台車失敗で車体プレビューまで消さない。
                    }
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static boolean shouldSkipPreviewBogie(boolean selfDrawsRunningGear, VehicleDefinition.BogieDefinition bogieDef) {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return true;
        }
        if (BogieRenderer.isDummyBogieModel(bogieDef.modelFile())) {
            return true;
        }
        return selfDrawsRunningGear && bogieDef.modelFile().toLowerCase(Locale.ROOT).endsWith(".class");
    }

    private static boolean shouldRenderPreviewGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        String lower = groupName.trim().toLowerCase(Locale.ROOT);
        return !lower.equals("shadow")
            && !lower.equals("_shadow")
            && !lower.equals("shadowplane")
            && !lower.equals("hitbox")
            && !lower.equals("collision")
            && !lower.equals("collider");
    }

    private MqoModelLoader.MqoModel getOrLoadModel(String id, String packName) {
        if (id == null || id.isBlank() || MISSING_MODEL_CACHE.contains(id)) return null;
        if (MODEL_CACHE.containsKey(id)) return MODEL_CACHE.get(id);
        MqoModelLoader.MqoModel model = null;
        try {
            // 1. 車両
            var vd = VehicleRegistry.getById(id);
            if (vd != null && vd.getModelFile() != null && !vd.getModelFile().isBlank()) {
                model = MqoModelLoader.loadModelForVehicle(vd);
            }
            if (model == null) {
                // 2. 設置物
                var iod = InstalledObjectRegistry.getById(id);
                if (iod != null && iod.getModelFile() != null && !iod.getModelFile().isBlank()) {
                    model = MqoModelLoader.loadModelFromPack(
                        iod.getPackName(), iod.getModelFile(), iod.getTextureOverrides(), null, iod.isSmoothing());
                }
            }
            if (model == null) {
                // 3. レール
                var rd = RailRegistry.getById(id);
                if (rd != null && rd.getModelFile() != null && !rd.getModelFile().isBlank()) {
                    model = MqoModelLoader.loadModelFromPack(
                        rd.getPackName(), rd.getModelFile(), rd.getTextureOverrides(), null, false);
                }
            }
        } catch (Exception ignored) {}
        if (model != null) {
            MODEL_CACHE.put(id, model);
        } else {
            MISSING_MODEL_CACHE.add(id);
        }
        return model;
    }

    /**
     * プレビューでスクリプトを適用するための、ワールド未追加の一時車両エンティティを返す。
     * 車両IDごとに1つキャッシュする。スクリプトは entity の状態(既定値)を参照して本体や
     * ドア・ライト等を本来の配置で描く。生成に失敗したら null(=スクリプト無し描画にフォールバック)。
     */
    private TrainEntity getOrCreatePreviewEntity(
            VehicleDefinition def, MqoModelLoader.MqoModel model) {
        if (def == null || def.getId() == null) {
            return null;
        }
        if (previewEntity != null && def.getId().equals(previewEntityId)) {
            return previewEntity;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            }
            TrainEntity e =
                TrainEntity.create(
                    mc.level, def.getId(), 0.0D, 0.0D, 0.0D, 0.0F, def.getTrainDistance());
            if (e == null) {
                return null;
            }
            if (model.getScriptEngine() != null) {
                e.setScriptEngine(model.getScriptEngine());
            }
            previewEntity = e;
            previewEntityId = def.getId();
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class ModelList extends ObjectSelectionList<ModelList.ModelEntry> {
        ModelList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            String lastCat = null;
            ModelEntry initialEntry = null;
            int initialRow = -1, row = 0;
            for (ModelInfo info : models) {
                String cat = info.category();
                if (cat != null && !cat.isBlank() && !cat.equals(lastCat)) {
                    addEntry(new ModelEntry(cat));
                    lastCat = cat;
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
                for (ModelEntry e : children()) {
                    if (!e.header) { initialEntry = e; initialRow = 0; break; }
                }
            }
            if (initialEntry != null) {
                setSelected(initialEntry);
                selectedId = initialEntry.id;
                setScrollAmount(Math.max(0.0, initialRow * (double) itemHeight - height * 0.5));
            }
        }

        @Override
        protected int scrollBarX() { return width - 6; }

        @Override
        public int getRowWidth() { return width - 8; }

        // 選択ハイライト(青い枠)は描画しない
        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, ModelEntry entry, int color) {}

        class ModelEntry extends ObjectSelectionList.Entry<ModelEntry> {
            public final String id;
            public final String packName;
            private final Component label;
            private final PackButtonTextureCache.ButtonTextureInfo buttonTex;
            public final boolean header;

            ModelEntry(String category) {
                this.id = ""; this.packName = "";
                this.label = Component.literal(category);
                this.buttonTex = null; this.header = true;
            }

            ModelEntry(String id, String displayName, String packName, String buttonTexturePath) {
                this.id = id; this.packName = packName;
                this.label = Component.literal(safe(displayName).isBlank() ? id : displayName);
                this.buttonTex = PackButtonTextureCache.get(packName, buttonTexturePath, id, displayName);
                this.header = false;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float pt) {
                int left = getX();
                int top = getY();
                if (header) {
                    graphics.text(ModelSelectScreen.this.font,
                        Component.literal(fitText(label.getString(), BTN_W - 8)),
                        left + 4, top + 11, 0xFFD0D0D0, false);
                    return;
                }
                boolean selected = this == modelList.getSelected();
                if (selected) {
                    graphics.fill(left, top, left + BTN_W, top + BTN_H, 0x663C7DFF);
                }
                if (buttonTex != null) {
                    // 透明ピクセルがあるテクスチャでもゲーム世界が透けないよう背景を先に塗る
                    graphics.fill(left, top, left + BTN_W, top + BTN_H, 0xFF1A1A2E);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, buttonTex.location(),
                        left, top, BTN_W, BTN_H,
                        buttonTex.sourceX(), buttonTex.sourceY(),
                        buttonTex.sourceWidth(), buttonTex.sourceHeight(),
                        buttonTex.width(), buttonTex.height());
                }
                String visibleLabel = fitText(label.getString(), BTN_W - 8);
                int labelY = top + BTN_H - 11;
                graphics.fill(left, labelY - 2, left + BTN_W, top + BTN_H, 0xAA000000);
                graphics.text(ModelSelectScreen.this.font, Component.literal(visibleLabel),
                    left + 4, labelY, buttonTex != null ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                if (selected) {
                    graphics.fill(left, top, left + BTN_W, top + 1, 0xFFFFFFFF);
                    graphics.fill(left, top + BTN_H - 1, left + BTN_W, top + BTN_H, 0xFFFFFFFF);
                    graphics.fill(left, top, left + 1, top + BTN_H, 0xFFFFFFFF);
                    graphics.fill(left + BTN_W - 1, top, left + BTN_W, top + BTN_H, 0xFFFFFFFF);
                    String selectedText = Component.translatable("screen.realtrainmodrenewed.selected").getString();
                    int selectedWidth = ModelSelectScreen.this.font.width(selectedText);
                    graphics.fill(left + BTN_W - selectedWidth - 8, top + 2, left + BTN_W - 2, top + 12, 0xCC000000);
                    graphics.text(ModelSelectScreen.this.font, Component.literal(selectedText),
                        left + BTN_W - selectedWidth - 5, top + 3, 0xFFFFFFFF, false);
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (header) return false;
                if (event.button() == 0) {
                    modelList.setSelected(this);
                    selectedId = this.id;
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() { return label; }
        }
    }
}
