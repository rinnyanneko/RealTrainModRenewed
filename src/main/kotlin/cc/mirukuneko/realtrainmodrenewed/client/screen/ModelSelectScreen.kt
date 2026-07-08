package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.client.PackButtonTextureCache
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.client.renderer.BogieRenderer
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

open class ModelSelectScreen @JvmOverloads constructor(
    title: Component,
    models: List<ModelInfo>,
    private val onSelected: Consumer<SelectionResult>,
    private val initialSelectedId: String? = null,
    initialDataMapValue: String? = "",
) : Screen(title) {
    @JvmRecord
    data class SelectionResult(val modelId: String?, val dataMapValue: String?)

    @JvmRecord
    data class ModelInfo @JvmOverloads constructor(
        val id: String?,
        val displayName: String?,
        val packName: String?,
        val buttonTexture: String?,
        val category: String? = "",
    )

    private val models: List<ModelInfo> = models.sortedWith(
        compareBy<ModelInfo, String>(CASE_INSENSITIVE_ORDER) { safe(it.category) }
            .thenBy(CASE_INSENSITIVE_ORDER) { safe(it.displayName) }
            .thenBy(CASE_INSENSITIVE_ORDER) { safe(it.id) },
    )
    private val initialDataMapValue: String = initialDataMapValue ?: ""

    private lateinit var modelList: ModelList
    private var dataMapBox: EditBox? = null
    private var selectedId: String? = null

    private var previewYaw = 0.0f
    private var previewPitch = 15.0f
    private var previewUserRotated = false
    private var previewDragging = false
    private var lastDragX = 0.0
    private var lastDragY = 0.0
    private var previewZoom = 1.0f
    private var previewEntity: TrainEntity? = null
    private var previewEntityId: String? = null

    private fun listWidth(): Int = BTN_W + 16
    private fun rightLeft(): Int = listWidth() + 4
    private fun rightWidth(): Int = max(100, width - rightLeft() - 4)
    private fun previewSize(): Int = min(rightWidth(), height - LIST_TOP - 60)

    private fun fitText(text: String?, maxWidth: Int): String {
        if (text.isNullOrBlank() || font.width(text) <= maxWidth) {
            return text ?: ""
        }
        val suffix = "..."
        val suffixWidth = font.width(suffix)
        if (maxWidth <= suffixWidth) {
            return font.plainSubstrByWidth(text, max(0, maxWidth))
        }
        return font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix
    }

    private fun isInPreviewArea(mx: Double, my: Double): Boolean {
        val rl = rightLeft()
        val rw = rightWidth()
        val ps = previewSize()
        return mx >= rl && mx <= rl + rw && my >= LIST_TOP && my <= LIST_TOP + ps
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x()
        val my = event.y()
        if (event.button() == 0 && isInPreviewArea(mx, my)) {
            previewDragging = true
            lastDragX = mx
            lastDragY = my
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val mx = event.x()
        val my = event.y()
        if (previewDragging && event.button() == 0) {
            previewUserRotated = true
            previewYaw += (mx - lastDragX).toFloat() * 0.8f
            previewPitch += (my - lastDragY).toFloat() * 0.8f
            previewPitch = Mth.clamp(previewPitch, -89.0f, 89.0f)
            lastDragX = mx
            lastDragY = my
            return true
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0 && previewDragging) {
            previewDragging = false
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mx: Double, my: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isInPreviewArea(mx, my)) {
            previewZoom = Mth.clamp(previewZoom * if (scrollY > 0) 1.1f else 1.0f / 1.1f, 0.3f, 6.0f)
            return true
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY)
    }

    override fun init() {
        val lw = listWidth()
        val listHeight = height - LIST_TOP - LIST_BOTTOM_MARGIN
        modelList = ModelList(minecraft, lw, listHeight, LIST_TOP, BTN_H)
        addRenderableWidget(modelList)

        val rl = rightLeft()
        val rw = rightWidth()
        val datamapY = LIST_TOP + previewSize() + 8
        val dataMap = EditBox(font, rl, datamapY, rw, 20, Component.empty())
        dataMap.value = initialDataMapValue
        dataMapBox = dataMap
        addRenderableWidget(dataMap)

        val buttonWidth = min(100, max(60, (rw - 4) / 2))
        val buttonY = datamapY + 28
        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) {
                val selected = modelList.selected
                if (selected != null && !selected.header) {
                    onSelected.accept(SelectionResult(selected.id, dataMapBox?.value ?: ""))
                }
                onClose()
            }.bounds(rl, buttonY, buttonWidth, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) { onClose() }
                .bounds(rl + buttonWidth + 4, buttonY, buttonWidth, 20)
                .build(),
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, listWidth() / 2, 7, 0xFFFFFF)

        val rl = rightLeft()
        val rw = rightWidth()
        val ps = previewSize()
        val previewLeft = rl
        val previewTop = LIST_TOP

        graphics.fill(previewLeft, previewTop, previewLeft + rw, previewTop + ps, -0x78000000)

        val selected = modelList.selected
        if (selected != null && !selected.header) {
            renderPreviewPanel(graphics, previewLeft, previewTop, rw, ps, selected)
        }

        if (models.isEmpty()) {
            graphics.centeredText(
                font,
                Component.translatable("screen.realtrainmodrenewed.no_models"),
                previewLeft + rw / 2,
                previewTop + ps / 2,
                0xAAAAAA,
            )
        }
    }

    private fun renderPreviewPanel(graphics: GuiGraphicsExtractor, left: Int, top: Int, width: Int, height: Int, entry: ModelList.ModelEntry) {
        val name = entry.label.string
        graphics.text(font, Component.literal(fitText(name, width - 12)), left + 6, top + 6, -0x1, false)

        val infoTop = top + height - 31
        graphics.fill(left, infoTop - 4, left + width, top + height, -0x56000000)
        graphics.text(font, Component.literal(fitText(entry.packName, width - 12)), left + 6, infoTop, -0x474738, false)
        graphics.text(font, Component.literal(fitText(entry.id, width - 12)), left + 6, infoTop + 11, -0x777760, false)

        val imageTop = top + 24
        val imageHeight = max(20, infoTop - imageTop - 8)
        if (entry.buttonTex != null) {
            val drawW = min(width - 24, BTN_W * 2)
            val drawH = max(BTN_H, min(imageHeight, Math.round(drawW * (BTN_H / BTN_W.toFloat()))))
            val drawX = left + (width - drawW) / 2
            val drawY = imageTop + max(0, (imageHeight - drawH) / 2)
            graphics.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, -0xefefe8)
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                entry.buttonTex.location,
                drawX,
                drawY,
                entry.buttonTex.sourceX.toFloat(),
                entry.buttonTex.sourceY.toFloat(),
                drawW,
                drawH,
                entry.buttonTex.sourceWidth,
                entry.buttonTex.sourceHeight,
                entry.buttonTex.width,
                entry.buttonTex.height,
            )
        } else {
            val boxW = min(width - 24, 220)
            val boxH = min(imageHeight, 64)
            val boxX = left + (width - boxW) / 2
            val boxY = imageTop + max(0, (imageHeight - boxH) / 2)
            graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, -0xeeee8)
            graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, -0xa5a590)
            graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, -0xa5a590)
            graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, -0xa5a590)
            graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, -0xa5a590)
            graphics.centeredText(font, Component.literal("No preview image"), left + width / 2, imageTop + imageHeight / 2 - 4, -0x555556)
        }
    }

    private fun getOrLoadModel(id: String?, packName: String?): MqoModelLoader.MqoModel? {
        if (id.isNullOrBlank() || MISSING_MODEL_CACHE.contains(id)) return null
        if (MODEL_CACHE.containsKey(id)) return MODEL_CACHE[id]
        var model: MqoModelLoader.MqoModel? = null
        try {
            val vehicleDefinition = VehicleRegistry.getById(id)
            if (vehicleDefinition != null && vehicleDefinition.getModelFile() != null && vehicleDefinition.getModelFile().isNotBlank()) {
                model = MqoModelLoader.loadModelForVehicle(vehicleDefinition)
            }
            if (model == null) {
                val installedObjectDefinition = InstalledObjectRegistry.getById(id)
                if (installedObjectDefinition != null && installedObjectDefinition.modelFile != null && installedObjectDefinition.modelFile.isNotBlank()) {
                    model = MqoModelLoader.loadModelFromPack(
                        installedObjectDefinition.packName,
                        installedObjectDefinition.modelFile,
                        installedObjectDefinition.textureOverrides,
                        null,
                        installedObjectDefinition.isSmoothing(),
                    )
                }
            }
            if (model == null) {
                val railDefinition = RailRegistry.getById(id)
                if (railDefinition != null && railDefinition.modelFile != null && railDefinition.modelFile.isNotBlank()) {
                    model = MqoModelLoader.loadModelFromPack(
                        railDefinition.packName,
                        railDefinition.modelFile,
                        railDefinition.textureOverrides,
                        null,
                        false,
                    )
                }
            }
        } catch (ignored: Exception) {
        }
        if (model != null) {
            MODEL_CACHE[id] = model
        } else {
            MISSING_MODEL_CACHE.add(id)
        }
        return model
    }

    private fun getOrCreatePreviewEntity(definition: VehicleDefinition?, model: MqoModelLoader.MqoModel): TrainEntity? {
        if (definition == null || definition.id == null) {
            return null
        }
        if (previewEntity != null && definition.id == previewEntityId) {
            return previewEntity
        }
        try {
            val minecraft = Minecraft.getInstance()
            val level = minecraft.level
            if (level == null) {
                return null
            }
            val entity = TrainEntity.create(level, definition.id, 0.0, 0.0, 0.0, 0.0f, definition.trainDistance)
                ?: return null
            if (model.getScriptEngine() != null) {
                entity.scriptEngine = model.getScriptEngine()
            }
            previewEntity = entity
            previewEntityId = definition.id
            return entity
        } catch (throwable: Throwable) {
            return null
        }
    }

    override fun isPauseScreen(): Boolean = false

    private inner class ModelList(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
    ) : ObjectSelectionList<ModelList.ModelEntry>(minecraft, width, height, top, itemHeight) {
        init {
            var lastCategory: String? = null
            var initialEntry: ModelEntry? = null
            var initialRow = -1
            var row = 0
            for (info in models) {
                val category = info.category
                if (!category.isNullOrBlank() && category != lastCategory) {
                    addEntry(ModelEntry(category))
                    lastCategory = category
                    row++
                }
                val entry = ModelEntry(info.id, info.displayName, info.packName, info.buttonTexture)
                addEntry(entry)
                if (initialSelectedId != null && initialSelectedId == info.id) {
                    initialEntry = entry
                    initialRow = row
                }
                row++
            }
            if (initialEntry == null) {
                for (entry in children()) {
                    if (!entry.header) {
                        initialEntry = entry
                        initialRow = 0
                        break
                    }
                }
            }
            if (initialEntry != null) {
                selected = initialEntry
                selectedId = initialEntry.id
                setScrollAmount(max(0.0, initialRow * itemHeight.toDouble() - height * 0.5))
            }
        }

        override fun scrollBarX(): Int = width - 6

        override fun getRowWidth(): Int = width - 8

        override fun extractSelection(graphics: GuiGraphicsExtractor, entry: ModelEntry, color: Int) {
        }

        inner class ModelEntry : Entry<ModelEntry> {
            val id: String
            val packName: String
            val label: Component
            val buttonTex: PackButtonTextureCache.ButtonTextureInfo?
            val header: Boolean

            constructor(category: String) {
                id = ""
                packName = ""
                label = Component.literal(category)
                buttonTex = null
                header = true
            }

            constructor(id: String?, displayName: String?, packName: String?, buttonTexturePath: String?) {
                this.id = id ?: ""
                this.packName = packName ?: ""
                label = Component.literal(if (safe(displayName).isBlank()) this.id else safe(displayName))
                buttonTex = PackButtonTextureCache.get(this.packName, buttonTexturePath, this.id, displayName)
                header = false
            }

            override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
                val left = x
                val top = y
                if (header) {
                    graphics.text(this@ModelSelectScreen.font, Component.literal(fitText(label.string, BTN_W - 8)), left + 4, top + 11, -0x2f2f30, false)
                    return
                }
                val selected = this === modelList.selected
                if (selected) {
                    graphics.fill(left, top, left + BTN_W, top + BTN_H, 0x663C7DFF)
                }
                if (buttonTex != null) {
                    graphics.fill(left, top, left + BTN_W, top + BTN_H, -0xe5e5d2)
                    graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        buttonTex.location,
                        left,
                        top,
                        buttonTex.sourceX.toFloat(),
                        buttonTex.sourceY.toFloat(),
                        BTN_W,
                        BTN_H,
                        buttonTex.sourceWidth,
                        buttonTex.sourceHeight,
                        buttonTex.width,
                        buttonTex.height,
                    )
                }
                val visibleLabel = fitText(label.string, BTN_W - 8)
                val labelY = top + BTN_H - 11
                graphics.fill(left, labelY - 2, left + BTN_W, top + BTN_H, -0x56000000)
                graphics.text(this@ModelSelectScreen.font, Component.literal(visibleLabel), left + 4, labelY, if (buttonTex != null) -0x1 else -0x555556, false)
                if (selected) {
                    graphics.fill(left, top, left + BTN_W, top + 1, -0x1)
                    graphics.fill(left, top + BTN_H - 1, left + BTN_W, top + BTN_H, -0x1)
                    graphics.fill(left, top, left + 1, top + BTN_H, -0x1)
                    graphics.fill(left + BTN_W - 1, top, left + BTN_W, top + BTN_H, -0x1)
                    val selectedText = Component.translatable("screen.realtrainmodrenewed.selected").string
                    val selectedWidth = this@ModelSelectScreen.font.width(selectedText)
                    graphics.fill(left + BTN_W - selectedWidth - 8, top + 2, left + BTN_W - 2, top + 12, -0x34000000)
                    graphics.text(this@ModelSelectScreen.font, Component.literal(selectedText), left + BTN_W - selectedWidth - 5, top + 3, -0x1, false)
                }
            }

            override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
                if (header) return false
                if (event.button() == 0) {
                    modelList.selected = this
                    selectedId = id
                    return true
                }
                return false
            }

            override fun getNarration(): Component = label
        }
    }

    private companion object {
        private const val BTN_W = 160
        private const val BTN_H = 32
        private const val LIST_TOP = 24
        private const val LIST_BOTTOM_MARGIN = 4

        private val MODEL_CACHE = ConcurrentHashMap<String, MqoModelLoader.MqoModel>()
        private val MISSING_MODEL_CACHE = ConcurrentHashMap.newKeySet<String>()

        private fun safe(value: String?): String = value ?: ""

        private fun computePreviewBounds(model: MqoModelLoader.MqoModel, vehicleDef: VehicleDefinition?): FloatArray {
            val bounds = model.computeBounds()
            if (vehicleDef == null) {
                return bounds
            }

            val scale = vehicleDef.getModelScale()
            val offset = vehicleDef.getModelOffset()
            var minX = (bounds[0] * scale + offset.x).toFloat()
            var minY = (bounds[1] * scale + offset.y).toFloat()
            var minZ = (bounds[2] * scale + offset.z).toFloat()
            var maxX = (bounds[3] * scale + offset.x).toFloat()
            var maxY = (bounds[4] * scale + offset.y).toFloat()
            var maxZ = (bounds[5] * scale + offset.z).toFloat()

            for (bogie in vehicleDef.getBogies()) {
                if (bogie == null || bogie.position() == null) {
                    continue
                }
                val pos = bogie.position()
                val x = (pos.x * scale + offset.x).toFloat()
                val y = ((pos.y + 0.24) * scale + offset.y).toFloat()
                val z = (pos.z * scale + offset.z).toFloat()
                minX = min(minX, x - 1.0f * scale)
                minY = min(minY, y - 1.0f * scale)
                minZ = min(minZ, z - 1.0f * scale)
                maxX = max(maxX, x + 1.0f * scale)
                maxY = max(maxY, y + 1.0f * scale)
                maxZ = max(maxZ, z + 1.0f * scale)
            }

            return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        }

        private fun renderStablePreviewModel(
            model: MqoModelLoader.MqoModel,
            vehicleDef: VehicleDefinition?,
            poseStack: PoseStack,
            buffer: MultiBufferSource.BufferSource,
            previewEnt: Any?,
        ) {
            poseStack.pushPose()
            try {
                if (vehicleDef != null) {
                    val offset = vehicleDef.getModelOffset()
                    poseStack.translate(offset.x, offset.y, offset.z)
                    val modelScale = vehicleDef.getModelScale()
                    poseStack.scale(modelScale, modelScale, modelScale)
                }

                val previewFilter = MqoModelLoader.GroupPredicate(::shouldRenderPreviewGroup)
                var rendered = false
                if (vehicleDef != null && model.hasRenderScript()) {
                    try {
                        MqoModelLoader.renderModel(model, poseStack, buffer, LightCoordsUtil.FULL_BRIGHT, previewFilter, null, previewEnt)
                        rendered = true
                    } catch (ignored: Throwable) {
                        rendered = false
                    }
                }
                if (!rendered) {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false, previewFilter, null)
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true, previewFilter, null)
                }

                if (vehicleDef != null) {
                    val selfDrawsRunningGear = model.hasOwnWheelGroups()
                    val bogies = vehicleDef.getBogies()
                    for (i in bogies.indices) {
                        val bogieDef = bogies[i]
                        if (shouldSkipPreviewBogie(selfDrawsRunningGear, bogieDef)) {
                            continue
                        }
                        try {
                            BogieRenderer.renderBogie(poseStack, i, bogieDef, vehicleDef, null, buffer, LightCoordsUtil.FULL_BRIGHT, 0.0f, 1.0f)
                        } catch (ignored: Throwable) {
                        }
                    }
                }
            } finally {
                poseStack.popPose()
            }
        }

        private fun shouldSkipPreviewBogie(selfDrawsRunningGear: Boolean, bogieDef: VehicleDefinition.BogieDefinition?): Boolean {
            if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
                return true
            }
            if (BogieRenderer.isDummyBogieModel(bogieDef.modelFile())) {
                return true
            }
            return selfDrawsRunningGear && bogieDef.modelFile().lowercase(Locale.ROOT).endsWith(".class")
        }

        private fun shouldRenderPreviewGroup(groupName: String?): Boolean {
            if (groupName.isNullOrBlank()) {
                return true
            }
            val lower = groupName.trim().lowercase(Locale.ROOT)
            return lower != "shadow" &&
                lower != "_shadow" &&
                lower != "shadowplane" &&
                lower != "hitbox" &&
                lower != "collision" &&
                lower != "collider"
        }
    }
}

