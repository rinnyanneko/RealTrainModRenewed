package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.client.ClientItemHelper
import cc.mirukuneko.realtrainmodrenewed.formation.TrainFormation
import cc.mirukuneko.realtrainmodrenewed.formation.TrainFormationData
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

open class TrainFormationScreen(private val stack: ItemStack) :
    Screen(Component.translatable("screen.realtrainmodrenewed.train_formation.title")) {
    private var formation: TrainFormation = TrainFormationData.getFormation(stack)?.copy() ?: TrainFormation()
    private lateinit var formationList: FormationList
    private lateinit var addButton: Button
    private lateinit var removeButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    override fun init() {
        formationList = FormationList(minecraft, width, height - LIST_BOTTOM_MARGIN, LIST_TOP, ITEM_HEIGHT)
        addWidget(formationList)

        addButton = Button.builder(Component.literal("+")) { onAddVehicle() }
            .bounds(width - 100, height - LIST_BOTTOM_MARGIN + 8, 40, 20)
            .build()
        addRenderableWidget(addButton)

        removeButton = Button.builder(Component.literal("-")) { onRemoveVehicle() }
            .bounds(width - 55, height - LIST_BOTTOM_MARGIN + 8, 40, 20)
            .build()
        addRenderableWidget(removeButton)

        saveButton = Button.builder(Component.translatable("gui.done")) { onSave() }
            .bounds(width / 2 - 155, height - LIST_BOTTOM_MARGIN + 35, 150, 20)
            .build()
        addRenderableWidget(saveButton)

        cancelButton = Button.builder(Component.translatable("gui.cancel")) { onClose() }
            .bounds(width / 2 + 5, height - LIST_BOTTOM_MARGIN + 35, 150, 20)
            .build()
        addRenderableWidget(cancelButton)

        updateButtons()
    }

    private fun onAddVehicle() {
        ClientItemHelper.openTrainSelectScreen(this)
    }

    private fun onRemoveVehicle() {
        val selected = formationList.selected
        if (selected != null) {
            formation.removeVehicle(selected.index)
            formationList.refresh()
            updateButtons()
        }
    }

    private fun onSave() {
        if (formation.name.isEmpty()) {
            formation.name = "編成${System.currentTimeMillis()}"
        }
        TrainFormationData.setFormation(stack, formation)
        onClose()
    }

    open fun updateFormation(newFormation: TrainFormation) {
        formation = newFormation
        formationList.refresh()
        updateButtons()
    }

    open fun updateFormationWithVehicle(vehicleId: String) {
        formation.addVehicle(vehicleId)
        formationList.refresh()
        updateButtons()
    }

    private fun updateButtons() {
        addButton.active = !formation.isFull()
        removeButton.active = formationList.selected != null && !formation.isEmpty()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFF)
        graphics.text(
            font,
            Component.translatable("screen.realtrainmodrenewed.train_formation.name"),
            width / 2 - 150,
            35,
            0xFFFFFF,
        )

        if (formation.isEmpty()) {
            graphics.centeredText(
                font,
                Component.translatable("screen.realtrainmodrenewed.train_formation.empty"),
                width / 2,
                height / 2,
                0xAAAAAA,
            )
        }
    }

    override fun isPauseScreen(): Boolean = false

    private inner class FormationList(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
    ) : ObjectSelectionList<FormationList.FormationEntry>(minecraft, width, height, top, itemHeight) {
        init {
            refresh()
        }

        fun refresh() {
            clearEntries()
            for (i in 0 until formation.getCarCount()) {
                val vehicleId = formation.getVehicle(i)
                val definition = VehicleRegistry.getById(vehicleId)
                val displayName = definition?.displayName ?: (vehicleId ?: "")
                addEntry(FormationEntry(i, displayName))
            }
        }

        override fun scrollBarX(): Int = width - 8

        override fun getRowWidth(): Int = width - 20

        inner class FormationEntry(val index: Int, displayName: String) : Entry<FormationEntry>() {
            private val label: Component = Component.literal("${index + 1}F: $displayName")

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                partialTick: Float,
            ) {
                val left = x
                val top = y
                val width = width
                val height = height
                val color = if (hovered) 0xFFFF55 else 0xFFFFFF
                if (this@FormationList.selected === this) {
                    graphics.fill(left, top, left + width, top + height, 0x44FFFFFF)
                }
                graphics.text(font, label, left + 6, top + (height - 8) / 2, color)
            }

            override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
                if (event.button() == 0) {
                    this@FormationList.selected = this
                    updateButtons()
                    return true
                }
                return false
            }

            override fun getNarration(): Component = label
        }
    }

    private companion object {
        private const val LIST_TOP = 60
        private const val LIST_BOTTOM_MARGIN = 80
        private const val ITEM_HEIGHT = 30
    }
}
