// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.screen

import jp.kaiz.atsassistmod.ifttt.IFTTTContainer
import jp.kaiz.atsassistmod.ifttt.IftttEditView
import jp.kaiz.atsassistmod.util.CardinalDirection
import jp.kaiz.atsassistmod.util.ComparisonManager
import jp.kaiz.atsassistmod.util.DataType
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.Consumer

/**
 * Edits a single IFTTT container.
 */
open class IftttMaterialScreen(
    private val parent: Screen,
    private val container: IFTTTContainer,
    private val onDone: Consumer<IFTTTContainer>,
) : Screen(Component.translatable(container.getTitle())), IftttEditView {
    private val fields = ArrayList<EditBox>()
    private var onceBox: Checkbox? = null

    override fun init() {
        val cx = width / 2
        val top = 50
        fields.clear()
        for (i in 0 until 6) {
            val box = EditBox(font, cx - 100, top + i * 24, 200, 20, Component.empty())
            box.setMaxLength(64)
            fields.add(box)
            addRenderableWidget(box)
        }
        val once = Checkbox.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.211.0"), font)
            .pos(cx - 100, top + 6 * 24)
            .selected(container.isOnce())
            .build()
        onceBox = once
        addRenderableWidget(once)

        if (hasOption()) {
            addRenderableWidget(Button.builder(Component.literal("Option: ${optionLabel()}")) { button ->
                cycleOption()
                button.message = Component.literal("Option: ${optionLabel()}")
            }.bounds(cx - 100, top + 6 * 24 + 24, 200, 20).build())
        }

        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.91.1")) {
            done()
        }.bounds(cx - 100, height - 28, 95, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.990")) {
            back()
        }.bounds(cx + 5, height - 28, 95, 20).build())
    }

    private fun done() {
        container.setFromGui(this)
        container.setOnce(onceBox?.selected() == true)
        onDone.accept(container)
        minecraft!!.setScreen(parent)
    }

    private fun back() {
        minecraft!!.setScreen(parent)
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        extractBackground(g, mouseX, mouseY, partial)
        super.extractRenderState(g, mouseX, mouseY, partial)
        g.centeredText(font, Component.translatable(container.getTitle()), width / 2, 20, 0xFFFFFF)
        val explanation = container.getExplanation()
        for (i in explanation.indices) {
            g.text(font, explanation[i], width / 2 - 100, 36 + i * 0, 0x888888)
        }
    }

    override fun getTextFieldText(index: Int): String =
        if (index >= 0 && index < fields.size) fields[index].value else ""

    override fun getTextFieldInt(index: Int): Int =
        try {
            getTextFieldText(index).toInt()
        } catch (_: NumberFormatException) {
            0
        }

    override fun textFieldLength(): Int = fields.size

    private fun hasOption(): Boolean =
        container is IFTTTContainer.This.Minecraft.RedStoneInput ||
            container is IFTTTContainer.This.RTM.SimpleDetectTrain ||
            container is IFTTTContainer.This.RTM.Cars ||
            container is IFTTTContainer.This.RTM.Speed ||
            container is IFTTTContainer.This.RTM.TrainDataMap ||
            container is IFTTTContainer.This.RTM.TrainDirection ||
            container is IFTTTContainer.That.Minecraft.RedStoneOutput ||
            container is IFTTTContainer.That.RTM.DataMap

    private fun optionLabel(): String =
        when (val c = container) {
            is IFTTTContainer.This.Minecraft.RedStoneInput -> c.mode.name
            is IFTTTContainer.This.RTM.SimpleDetectTrain -> c.detectMode.name
            is IFTTTContainer.This.RTM.Cars -> c.mode.name
            is IFTTTContainer.This.RTM.Speed -> c.mode.name
            is IFTTTContainer.This.RTM.TrainDataMap -> c.dataType.key
            is IFTTTContainer.This.RTM.TrainDirection -> c.direction.name
            is IFTTTContainer.That.Minecraft.RedStoneOutput -> if (c.isTrainCarsOutput) "cars" else "level"
            is IFTTTContainer.That.RTM.DataMap -> c.dataType.key
            else -> ""
        }

    private fun cycleOption() {
        when (val c = container) {
            is IFTTTContainer.This.Minecraft.RedStoneInput -> {
                val values = IFTTTContainer.This.Minecraft.RedStoneInput.ModeType.entries
                c.mode = values[(c.mode.ordinal + 1) % values.size]
            }
            is IFTTTContainer.This.RTM.SimpleDetectTrain -> {
                val values = IFTTTContainer.This.RTM.SimpleDetectTrain.DetectMode.entries
                c.detectMode = values[(c.detectMode.ordinal + 1) % values.size]
            }
            is IFTTTContainer.This.RTM.Cars -> {
                val values = ComparisonManager.Integer.entries
                c.mode = values[(c.mode.ordinal + 1) % values.size]
            }
            is IFTTTContainer.This.RTM.Speed -> {
                val values = ComparisonManager.Integer.entries
                c.mode = values[(c.mode.ordinal + 1) % values.size]
            }
            is IFTTTContainer.This.RTM.TrainDataMap -> {
                val values = DataType.entries
                c.dataType = values[(c.dataType.ordinal + 1) % values.size]
            }
            is IFTTTContainer.This.RTM.TrainDirection -> {
                val values = CardinalDirection.entries
                c.direction = values[(c.direction.ordinal + 1) % values.size]
            }
            is IFTTTContainer.That.Minecraft.RedStoneOutput -> c.isTrainCarsOutput = !c.isTrainCarsOutput
            is IFTTTContainer.That.RTM.DataMap -> {
                val values = DataType.entries
                c.dataType = values[(c.dataType.ordinal + 1) % values.size]
            }
        }
    }

    override fun isPauseScreen(): Boolean = false
}
