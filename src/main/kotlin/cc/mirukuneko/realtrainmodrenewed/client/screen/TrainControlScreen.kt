// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.client.TrainControlKeyMappings
import cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.network.TrainControlPayload
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

open class TrainControlScreen(private val train: TrainEntity) : Screen(Component.literal("Train Control Panel")) {
    private var selectedTab = ControlTab.SETTING

    override fun init() {
        rebuildTabWidgets()
    }

    private fun rebuildTabWidgets() {
        clearWidgets()
        val left = leftPos()
        val top = topPos()
        if (selectedTab == ControlTab.SETTING) {
            addButton(left + 4, top + 4, 82, interiorLightLabel(), "toggle_interior_light", 0)
            addButton(left + 90, top + 4, 82, lightLabel(), "set_light_mode", nextLightMode())
            addButton(left + 4, top + 28, 82, pantographLabel(), "toggle_pantograph", 0)
            addButton(left + 90, top + 28, 27, "前", "set_reverser", 1).active = train.reverser != 1
            addButton(left + 117, top + 28, 28, "中", "set_reverser", 0).active = train.reverser != 0
            addButton(left + 145, top + 28, 27, "後", "set_reverser", -1).active = train.reverser != -1
            addArrowButton(left + 4, top + 52, "<", "noop")
            addButton(left + 28, top + 52, 120, "チャンクロード", "noop", 0)
            addArrowButton(left + 152, top + 52, ">", "noop")
            val directionSupported = supportsDirectionControl()
            addArrowButton(left + 4, top + 76, "<", "prev_destination").active = directionSupported
            addButton(left + 28, top + 76, 120, destinationLabel(), "next_destination", 0).active = directionSupported
            addArrowButton(left + 152, top + 76, ">", "next_destination").active = directionSupported
            addArrowButton(left + 4, top + 100, "<", "prev_sound")
            val announcementName = VehicleRegistry.getById(train.vehicleId)?.announcementNames
                ?.getOrNull(train.soundIndex)
                ?.takeIf { it.isNotBlank() }
                ?: "アナウンス ${train.soundIndex + 1}"
            addButton(left + 28, top + 100, 120, announcementName, "next_sound", 0)
            addArrowButton(left + 152, top + 100, ">", "next_sound")
        } else if (selectedTab == ControlTab.FUNCTION) {
            val definition = VehicleRegistry.getById(train.vehicleId)
            val options = resolveCustomButtonOptions(definition)
            val labels = resolveCustomButtonLabels(definition, options)
            for (i in 0 until min(18, labels.size)) {
                val x = left + 4 + (i % 3) * 56
                val y = top + 4 + (i / 3) * 24
                val value = train.getCustomButtonValue(i)
                val text = resolveCustomButtonDisplayText(labels[i], options, i, value)
                val packed = (i shl 8) or (value and 0xFF)
                addRenderableWidget(
                    Button.builder(Component.literal(text)) { send("cycle_custom_button", packed) }
                        .bounds(x, y, 52, 22)
                        .build(),
                )
            }
        }
        addDoorButton(left + PANEL_W + 20, top + 20, false)
        addDoorButton(left - 84, top + 20, true)
    }

    private fun addButton(x: Int, y: Int, width: Int, label: String, action: String, value: Int): Button {
        val button = Button.builder(Component.literal(label)) { send(action, value) }
            .bounds(x, y, width, 20)
            .build()
        if (action == "noop") {
            button.active = false
        }
        return addRenderableWidget(button)
    }

    private fun addArrowButton(x: Int, y: Int, label: String, action: String): Button {
        val button = Button.builder(Component.literal(label)) { send(action, 0) }
            .bounds(x, y, 20, 20)
            .build()
        if (action == "noop") {
            button.active = false
        }
        return addRenderableWidget(button)
    }

    private fun addDoorButton(x: Int, y: Int, leftDoor: Boolean) {
        addRenderableWidget(DoorButton(x, y, leftDoor))
    }

    private fun resolveCustomButtonLabels(
        definition: VehicleDefinition?,
        options: List<List<String>>,
    ): MutableList<String> {
        val labels = mutableListOf<String>()
        if (options.isNotEmpty()) {
            for (optionList in options) {
                labels.add(if (optionList.isEmpty()) "" else optionList[0])
            }
        }
        if (definition != null && labels.isEmpty()) {
            labels.addAll(definition.getCustomButtonNames())
        }
        if (labels.isEmpty()) {
            for (i in 0 until 16) {
                val value = firstNonBlank(
                    train.getScriptDataValue("ButtonName$i"),
                    train.getScriptDataValue("buttonName$i"),
                    train.getScriptDataValue("customButtonName$i"),
                    train.getScriptDataValue("CustomButtonName$i"),
                )
                if (value.isNotBlank()) {
                    labels.add(value)
                }
            }
        }
        if (labels.isEmpty()) {
            var count = 0
            for (i in 0 until 31) {
                if (train.getCustomButtonValue(i) != 0 || train.getScriptDataValue("Button$i").orEmpty().isNotBlank()) {
                    count = i + 1
                }
            }
            for (i in 0 until count) {
                labels.add("カスタム${i + 1}")
            }
        }
        return labels
    }

    private fun resolveCustomButtonOptions(definition: VehicleDefinition?): List<List<String>> {
        if (definition != null && definition.getCustomButtonOptions().isNotEmpty()) {
            return definition.getCustomButtonOptions()
        }
        return emptyList()
    }

    private fun resolveCustomButtonDisplayText(
        fallbackLabel: String,
        options: List<List<String>>,
        index: Int,
        value: Int,
    ): String {
        if (index >= 0 && index < options.size) {
            val optionList = options[index]
            if (optionList.isNotEmpty()) {
                val current = Math.floorMod(value, optionList.size)
                return optionList[current]
            }
        }
        return fallbackLabel + if (value != 0) " ON" else " OFF"
    }

    private fun lightLabel(): String =
        when (train.lightMode) {
            1 -> "前照灯"
            2 -> "前照灯・尾灯"
            else -> "消灯"
        }

    private fun interiorLightLabel(): String =
        if (train.isInteriorLightOn) "室内灯 ON" else "室内灯 OFF"

    private fun nextLightMode(): Int =
        when (train.lightMode) {
            0 -> 1
            1 -> 2
            else -> 0
        }

    private fun pantographLabel(): String =
        if (train.isPantographUp) "パンタ 上" else "パンタ 下"

    private fun destinationLabel(): String {
        if (!supportsDirectionControl()) return "方向幕 非対応"
        val rollsignNames = train.resourceState.resourceSet.config.rollsignNames ?: emptyArray()
        val count = max(1, rollsignNames.size)
        val name = if (rollsignNames.isEmpty()) "なし" else rollsignNames[Math.floorMod(train.destinationIndex, count)]
        return "方向幕 $name"
    }

    private fun supportsDirectionControl(): Boolean {
        val definition = VehicleRegistry.getById(train.vehicleId) ?: return false
        if (definition.hasScript() || train.scriptEngine != null) return true
        return definition.rollsignNames.isNotEmpty() &&
            definition.rollsignTexture.isNotBlank() &&
            definition.rollsigns.isNotEmpty()
    }

    private fun send(action: String, value: Int) {
        if (action == "noop") {
            return
        }
        if (shouldPlayLeverClick(action)) {
            LegacyScriptSoundManager.playLeverClick()
        }
        applyLocal(action, value)
        ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, action, value))
        rebuildTabWidgets()
    }

    private fun applyLocal(action: String, value: Int) {
        when (action) {
            "set_light_mode" -> train.lightMode = value
            "toggle_interior_light" -> train.isInteriorLightOn = !train.isInteriorLightOn
            "toggle_door" -> train.isDoorOpen = !train.isDoorOpen
            "toggle_door_left" -> train.isDoorLeftOpen = !train.isDoorLeftOpen
            "toggle_door_right" -> train.isDoorRightOpen = !train.isDoorRightOpen
            "toggle_pantograph" -> train.isPantographUp = !train.isPantographUp
            "set_reverser" -> train.reverser = value
            "mascon_neutral" -> train.notch = 0
            "mascon_power" -> train.stepMascon(1)
            "mascon_brake" -> train.stepMascon(-1)
            "next_destination" -> {
                val count = max(1, (train.resourceState.resourceSet.config.rollsignNames ?: emptyArray()).size)
                train.destinationIndex = (train.destinationIndex + 1) % count
            }
            "prev_destination" -> {
                val count = max(1, (train.resourceState.resourceSet.config.rollsignNames ?: emptyArray()).size)
                train.destinationIndex = Math.floorMod(train.destinationIndex - 1, count)
            }
            "next_sound" -> train.soundIndex = resolveNextSoundIndex(1)
            "prev_sound" -> train.soundIndex = resolveNextSoundIndex(-1)
            "toggle_custom_button" -> train.toggleCustomButton(value)
            "cycle_custom_button" -> {
                val index = (value ushr 8) and 0xFF
                val currentValue = value and 0xFF
                val definition = VehicleRegistry.getById(train.vehicleId)
                val options = resolveCustomButtonOptions(definition)
                val nextValue = if (index >= 0 && index < options.size && options[index].isNotEmpty()) {
                    (currentValue + 1) % options[index].size
                } else {
                    if (currentValue == 0) 1 else 0
                }
                train.setCustomButtonValue(index, nextValue)
            }
        }
    }

    private fun resolveNextSoundIndex(delta: Int): Int {
        val definition = VehicleRegistry.getById(train.vehicleId)
        val size = definition?.getAnnouncementSounds()?.size ?: 0
        if (size <= 0) {
            return max(0, train.soundIndex + delta)
        }
        return Math.floorMod(train.soundIndex + delta, size)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        if (event.button() == 0) {
            val tab = tabAt(mouseX, mouseY)
            if (tab != null) {
                selectedTab = tab
                rebuildTabWidgets()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val minecraft = minecraft
        if (event.key() == GLFW.GLFW_KEY_ESCAPE ||
            minecraft.options.keyInventory.matches(event) ||
            TrainControlKeyMappings.OPEN_CONTROL.matches(event)
        ) {
            closeFromKey()
            return true
        }
        return super.keyPressed(event)
    }

    private fun closeFromKey() {
        while (TrainControlKeyMappings.OPEN_CONTROL.consumeClick()) {
        }
        onClose()
    }

    private fun tabAt(mouseX: Double, mouseY: Double): ControlTab? {
        val left = leftPos()
        val top = topPos()
        for (tab in ControlTab.entries) {
            val x = tabX(left, tab)
            val y = if (tab.isTop) top - 28 else top + PANEL_H - 4
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H) {
                return tab
            }
        }
        return null
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val left = leftPos()
        val top = topPos()
        renderTabs(graphics, left, top, false)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            selectedTab.background,
            left - 1,
            top - 1,
            0f,
            0f,
            PANEL_W,
            PANEL_H,
            256,
            256,
        )
        renderTabs(graphics, left, top, true)
        for (renderable: Renderable in renderables) {
            renderable.extractRenderState(graphics, mouseX, mouseY, partialTick)
        }
        renderTabContents(graphics, left, top)
        renderPlayerInventory(graphics, left, top)
    }

    private fun renderTabContents(graphics: GuiGraphicsExtractor, left: Int, top: Int) {
        if (selectedTab != ControlTab.FORMATION) {
            return
        }
        val trains = train.formationTrainsForDisplay
        val count = max(1, trains.size)
        graphics.text(font, Component.literal("${count}両編成"), left + 8, top + 10, 0x404040, false)
        for (i in 0 until min(6, count)) {
            val entry = if (i < trains.size) trains[i] else train
            val definition = VehicleRegistry.getById(entry?.vehicleId)
            val name = definition?.getDisplayName() ?: entry?.vehicleId.orEmpty()
            val y = top + 30 + i * 18
            graphics.fakeItem(ItemStack(RealTrainModRenewedItems.TRAIN_ITEM.get()), left + 10, y - 4)
            graphics.text(font, Component.literal("${i + 1}  $name"), left + 30, y, 0x404040, false)
        }
    }

    private fun renderPlayerInventory(graphics: GuiGraphicsExtractor, left: Int, top: Int) {
        val player = minecraft.player ?: return
        val inventory = player.inventory
        if (selectedTab == ControlTab.INVENTORY) {
            for (row in 0 until 3) {
                for (col in 0 until 9) {
                    val index = 9 + row * 9 + col
                    renderInventoryItem(graphics, inventory, index, left + 8 + col * 18, top + 84 + row * 18)
                }
            }
        }
        for (col in 0 until 9) {
            renderInventoryItem(graphics, inventory, col, left + 8 + col * 18, top + 142)
        }
    }

    private fun renderInventoryItem(
        graphics: GuiGraphicsExtractor,
        inventory: net.minecraft.world.entity.player.Inventory,
        index: Int,
        x: Int,
        y: Int,
    ) {
        if (index < 0 || index >= inventory.containerSize) {
            return
        }
        val stack = inventory.getItem(index)
        if (stack.isEmpty) {
            return
        }
        graphics.item(stack, x, y)
        graphics.itemDecorations(font, stack, x, y)
    }

    private fun renderTabs(graphics: GuiGraphicsExtractor, left: Int, top: Int, selectedOnly: Boolean) {
        for (tab in ControlTab.entries) {
            if ((tab == selectedTab) != selectedOnly) {
                continue
            }
            val x = tabX(left, tab)
            val y = if (tab.isTop) top - 28 else top + PANEL_H - 4
            val background = -0x8f8f90
            val light = -0x656566
            val shadow = -0xb5b5b6
            graphics.fill(x, y, x + TAB_W, y + TAB_H, -0x1)
            graphics.fill(x + 2, y + 2, x + TAB_W - 2, y + TAB_H - 2, background)
            graphics.fill(x + 2, y + 2, x + TAB_W - 2, y + 3, light)
            graphics.fill(x + 2, y + 2, x + 3, y + TAB_H - 2, light)
            graphics.fill(x + 2, y + TAB_H - 3, x + TAB_W - 2, y + TAB_H - 2, shadow)
            graphics.fill(x + TAB_W - 3, y + 2, x + TAB_W - 2, y + TAB_H - 2, shadow)
            graphics.fakeItem(tab.icon, x + 6, y + if (tab.isTop) 8 else 7)
        }
    }

    private fun tabX(left: Int, tab: ControlTab): Int {
        val column = tab.ordinal % 6
        if (column == 5) {
            return left + PANEL_W - TAB_W
        }
        return left + 28 * column + if (column > 0) column else 0
    }

    private fun leftPos(): Int = (width - PANEL_W) / 2

    private fun topPos(): Int = (height - PANEL_H) / 2

    override fun isPauseScreen(): Boolean = false

    private enum class ControlTab(
        val label: String,
        val background: Identifier,
        val icon: ItemStack,
        val isTop: Boolean,
    ) {
        SETTING("Setting", TAB_SETTING_TEXTURE, ItemStack(RealTrainModRenewedItems.WRENCH_ITEM.get()), true),
        FUNCTION("Function", TAB_SETTING_TEXTURE, ItemStack(RealTrainModRenewedItems.CROWBAR_ITEM.get()), true),
        FORMATION("Formation", TAB_FORMATION_TEXTURE, ItemStack(RealTrainModRenewedItems.TRAIN_ITEM.get()), true),
        INVENTORY("Player Inventory", TAB_INVENTORY_TEXTURE, ItemStack(Blocks.CHEST), true),
    }

    private inner class DoorButton(x: Int, y: Int, private val leftDoor: Boolean) : Button(
        x,
        y,
        64,
        80,
        Component.empty(),
        { send(if (leftDoor) "toggle_door_left" else "toggle_door_right", 0) },
        DEFAULT_NARRATION,
    ) {
        override fun extractContents(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            partialTick: Float,
        ) {
            val opened = if (leftDoor) train.isDoorLeftOpen else train.isDoorRightOpen
            val sliderOffset = if (opened) -10 else -4
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TAB_INVENTORY_TEXTURE,
                x + 25,
                y + sliderOffset,
                242f,
                80f,
                14,
                100,
                256,
                256,
            )
            graphics.blit(RenderPipelines.GUI_TEXTURED, TAB_INVENTORY_TEXTURE, x, y, 192f, 0f, 64, 80, 256, 256)
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TAB_INVENTORY_TEXTURE,
                x + 44,
                y + 48,
                224f,
                if (opened) 80f else 88f,
                8,
                8,
                256,
                256,
            )
        }
    }

    private companion object {
        private val TAB_INVENTORY_TEXTURE: Identifier =
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/gui/tab_inventory.png")
        private val TAB_SETTING_TEXTURE: Identifier =
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/gui/tab_setting.png")
        private val TAB_FORMATION_TEXTURE: Identifier =
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/gui/tab_formation.png")

        private const val PANEL_W = 176
        private const val PANEL_H = 166
        private const val TAB_W = 28
        private const val TAB_H = 32

        private fun firstNonBlank(vararg values: String?): String {
            for (value in values) {
                if (value != null && value.isNotBlank()) {
                    return value
                }
            }
            return ""
        }

        private fun shouldPlayLeverClick(action: String?): Boolean =
            action != null && (action.startsWith("mascon_") || action == "set_reverser")
    }
}

