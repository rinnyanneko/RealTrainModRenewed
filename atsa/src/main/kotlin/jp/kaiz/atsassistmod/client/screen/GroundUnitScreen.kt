// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.screen

import jp.kaiz.atsassistmod.block.GroundUnitBlock
import jp.kaiz.atsassistmod.block.GroundUnitType
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity
import jp.kaiz.atsassistmod.client.ClientNetworkHelper
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SaveGroundUnit
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SetGroundUnitType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.Component
import java.util.Arrays

/**
 * Ground-unit configuration screen.
 */
open class GroundUnitScreen(private val tile: GroundUnitBlockEntity) : Screen(Component.literal("Ground Unit")) {
    private var linkRedstone: Checkbox? = null
    private var speedField: EditBox? = null
    private var distanceField: EditBox? = null
    private var autoBrakeBox: Checkbox? = null
    private var useTrainDistanceBox: Checkbox? = null
    private val stateButtons = ArrayList<StateButton>()
    private var tpButton: TrainProtectionButton? = null

    private fun type(): GroundUnitType = tile.guType()

    override fun init() {
        val cx = width / 2
        val cy = height / 2
        val type = type()

        if (type == GroundUnitType.None) {
            initSelectionMenu(cx, cy)
            return
        }

        val linkRedstoneBox = Checkbox.builder(Component.empty(), font)
            .pos(cx + 45, cy - 50)
            .selected(tile.isLinkRedStone())
            .build()
        linkRedstone = linkRedstoneBox
        addRenderableWidget(linkRedstoneBox)

        when (type) {
            GroundUnitType.ATC_SpeedLimit_Notice -> {
                speedField = addField(cx, cy - 30, 3, tile.getSpeedLimit().toString())
                distanceField = addField(cx, cy - 5, 5, tile.getDistance().toString())
                val autoBrake = Checkbox.builder(Component.empty(), font)
                    .pos(cx + 45, cy + 25).selected(tile.isAutoBrake()).build()
                val useTrainDistance = Checkbox.builder(Component.empty(), font)
                    .pos(cx + 45, cy + 50).selected(tile.isUseTrainDistance()).build()
                autoBrakeBox = autoBrake
                useTrainDistanceBox = useTrainDistance
                addRenderableWidget(autoBrake)
                addRenderableWidget(useTrainDistance)
            }
            GroundUnitType.ATC_SpeedLimit_Cancel -> {
                val useTrainDistance = Checkbox.builder(Component.empty(), font)
                    .pos(cx + 45, cy - 25).selected(tile.isUseTrainDistance()).build()
                useTrainDistanceBox = useTrainDistance
                addRenderableWidget(useTrainDistance)
            }
            GroundUnitType.TASC_StopPotion_Notice,
            GroundUnitType.TASC_StopPotion_Correction,
            -> {
                distanceField = addField(cx, cy - 30, 5, tile.getDistance().toString())
                val useTrainDistance = Checkbox.builder(Component.empty(), font)
                    .pos(cx + 45, cy).selected(tile.isUseTrainDistance()).build()
                useTrainDistanceBox = useTrainDistance
                addRenderableWidget(useTrainDistance)
            }
            GroundUnitType.ATO_Departure_Signal,
            GroundUnitType.ATO_Change_Speed,
            -> speedField = addField(cx, cy - 30, 3, tile.getSpeedLimit().toString())
            GroundUnitType.CHANGE_TP -> {
                val button = TrainProtectionButton(cx - 75, cy - 25, tile.getTPType())
                tpButton = button
                addRenderableWidget(button)
            }
            GroundUnitType.TrainState_Set -> initTrainState(cx, cy)
            else -> Unit
        }

        addRenderableWidget(
            Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.21")) {
                confirm()
            }.bounds(cx - 110, height - 30, 100, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.20")) {
                onClose()
            }.bounds(cx + 10, height - 30, 100, 20).build(),
        )
    }

    private fun initSelectionMenu(cx: Int, cy: Int) {
        addSel(1, cx - 170, cy - 75)
        addSel(2, cx - 50, cy - 75)
        addSel(3, cx + 70, cy - 75)
        addSel(4, cx - 170, cy - 35)
        addSel(5, cx - 50, cy - 35)
        addSel(6, cx - 170, cy - 10)
        addSel(7, cx - 50, cy - 10)
        addSel(9, cx - 170, cy + 30)
        addSel(10, cx - 50, cy + 30)
        addSel(11, cx + 70, cy + 30)
        addSel(13, cx + 70, cy + 70)
        addSel(14, cx - 170, cy + 70)
        addRenderableWidget(
            Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.0.button.20")) {
                onClose()
            }.bounds(cx - 50, height - 25, 100, 20).build(),
        )
    }

    private fun addSel(id: Int, x: Int, y: Int) {
        addRenderableWidget(
            Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.0.button.$id")) {
                selectType(id)
            }.bounds(x, y, 100, 20).build(),
        )
    }

    private fun selectType(id: Int) {
        ClientNetworkHelper.sendToServer(SetGroundUnitType(tile.blockPos, id))
        val minecraft = Minecraft.getInstance()
        if (minecraft.level != null) {
            val state = tile.blockState.setValue(GroundUnitBlock.TYPE, GroundUnitType.getType(id).id)
            minecraft.level!!.setBlock(tile.blockPos, state, 3)
        }
        minecraft.setScreen(GroundUnitScreen(tile))
    }

    private fun initTrainState(cx: Int, cy: Int) {
        val indices = intArrayOf(0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11)
        val states = tile.getStates()
        var slot = 0
        for (index in indices) {
            val value = if (states.size == 12) states[index] else (StateSpec.of(index).min - 1).toByte()
            val button = StateButton(cx - 160 + 170 * (slot % 2), cy - 75 + 25 * (slot / 2), index, value)
            stateButtons.add(button)
            addRenderableWidget(button)
            slot++
        }
        addRenderableWidget(
            Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.21")) {
                confirm()
            }.bounds(cx - 110, height - 30, 100, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.20")) {
                onClose()
            }.bounds(cx + 10, height - 30, 100, 20).build(),
        )
    }

    private fun addField(x: Int, y: Int, maxLen: Int, value: String): EditBox {
        val box = EditBox(font, x, y, 100, 20, Component.empty())
        box.setMaxLength(maxLen)
        box.setValue(value)
        box.setFilter { it.matches(Regex("-?[0-9]*\\.?[0-9]*")) }
        addRenderableWidget(box)
        return box
    }

    private fun confirm() {
        val link = linkRedstone?.selected() == true
        val speed = speedField?.value?.let(::parseInt) ?: tile.getSpeedLimit()
        val distance = distanceField?.value?.let(::parseDouble) ?: tile.getDistance()
        val autoBrake = autoBrakeBox?.selected() ?: tile.isAutoBrake()
        val useTrainDistance = useTrainDistanceBox?.selected() ?: tile.isUseTrainDistance()
        var states = tile.getStates().clone()
        if (stateButtons.isNotEmpty()) {
            states = ByteArray(12)
            Arrays.fill(states, (-1).toByte())
            for (button in stateButtons) {
                states[button.index] = button.value
            }
        }
        val tpType = tpButton?.value?.id ?: tile.getTPType().id
        ClientNetworkHelper.sendToServer(
            SaveGroundUnit(tile.blockPos, link, speed, distance, autoBrake, useTrainDistance, states, tpType),
        )
        onClose()
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        extractBackground(g, mouseX, mouseY, partial)
        super.extractRenderState(g, mouseX, mouseY, partial)
        val type = type()
        val titleKey = when (type) {
            GroundUnitType.None -> "atsassistmod.gui.GroundUnitMenu.0.title"
            GroundUnitType.ATC_SpeedLimit_Notice -> "atsassistmod.gui.GroundUnitMenu.1.title"
            GroundUnitType.ATC_SpeedLimit_Cancel -> "atsassistmod.gui.GroundUnitMenu.2.title"
            GroundUnitType.ATC_SpeedLimit_Reset -> "atsassistmod.gui.GroundUnitMenu.3.title"
            GroundUnitType.TASC_StopPotion_Notice -> "atsassistmod.gui.GroundUnitMenu.4.title"
            GroundUnitType.TASC_Cancel -> "atsassistmod.gui.GroundUnitMenu.5.title"
            GroundUnitType.TASC_StopPotion_Correction -> "atsassistmod.gui.GroundUnitMenu.6.title"
            GroundUnitType.TASC_StopPotion -> "atsassistmod.gui.GroundUnitMenu.7.title"
            GroundUnitType.ATO_Departure_Signal -> "atsassistmod.gui.GroundUnitMenu.9.title"
            GroundUnitType.ATO_Cancel -> "atsassistmod.gui.GroundUnitMenu.10.title"
            GroundUnitType.ATO_Change_Speed -> "atsassistmod.gui.GroundUnitMenu.11.title"
            GroundUnitType.TrainState_Set -> "atsassistmod.gui.GroundUnitMenu.13.title"
            GroundUnitType.CHANGE_TP -> "atsassistmod.gui.GroundUnitMenu.14.title"
            GroundUnitType.ATACS_Disable -> "atsassistmod.gui.GroundUnitMenu.15.title"
        }
        g.text(font, Component.translatable(titleKey), width / 4, 20, 0xFFFFFF)
        if (type != GroundUnitType.None && type != GroundUnitType.TrainState_Set && type != GroundUnitType.TASC_StopPotion) {
            g.text(
                font,
                Component.translatable("atsassistmod.gui.GroundUnitMenu.common.text.0"),
                width / 2 - 100,
                height / 2 - 50,
                0xFFFFFF,
            )
        }
    }

    override fun isPauseScreen(): Boolean = false

    private data class StateSpec(val displayKey: Int, val min: Int, val max: Int, val dataCount: Int) {
        companion object {
            fun of(index: Int): StateSpec =
                when (index) {
                    0 -> StateSpec(0, 0, 2, 0)
                    1 -> StateSpec(1, -8, 5, 0)
                    2 -> StateSpec(2, 0, 9, 0)
                    4 -> StateSpec(3, 0, 3, 4)
                    5 -> StateSpec(4, 0, 2, 3)
                    6 -> StateSpec(5, 0, 1, 2)
                    7 -> StateSpec(6, 0, 1, 0)
                    8 -> StateSpec(7, 0, 9, 0)
                    9 -> StateSpec(8, 0, 9, 0)
                    10 -> StateSpec(9, 0, 2, 3)
                    11 -> StateSpec(10, 0, 2, 3)
                    else -> StateSpec(0, 0, 0, 0)
                }
        }
    }

    private class StateButton(
        x: Int,
        y: Int,
        val index: Int,
        var value: Byte,
    ) : Button(x, y, 150, 20, Component.empty(), {}, DEFAULT_NARRATION) {
        private val spec = StateSpec.of(index)

        init {
            updateMessage()
        }

        override fun onPress(input: InputWithModifiers) {
            val sentinel = spec.min - 1
            value = if (value >= spec.max) sentinel.toByte() else (value + 1).toByte()
            if (value < sentinel) {
                value = sentinel.toByte()
            }
            updateMessage()
        }

        private fun updateMessage() {
            val key = spec.displayKey
            val state = if (key == 4 && value.toInt() == 2) {
                Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.4.state.1").string
            } else if (key == 4) {
                Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.4.state.0").string
            } else {
                Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.$key.state").string
            }
            val data = if (value < spec.min) {
                Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.notchange").string
            } else if (spec.dataCount > 0) {
                Component.translatable("atsassistmod.gui.GroundUnitMenu.13.slider.$key.data.$value").string
            } else {
                value.toString()
            }
            message = Component.literal("$state:$data")
        }

        override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
            g.centeredText(
                Minecraft.getInstance().font,
                message,
                x + width / 2,
                y + (height - 8) / 2,
                0xFFFFFF,
            )
        }
    }

    private class TrainProtectionButton(
        x: Int,
        y: Int,
        var value: TrainProtectionType,
    ) : Button(x, y, 150, 20, Component.empty(), {}, DEFAULT_NARRATION) {
        init {
            updateMessage()
        }

        override fun onPress(input: InputWithModifiers) {
            val all = TrainProtectionType.entries
            value = all[(value.ordinal + 1) % all.size]
            updateMessage()
        }

        private fun updateMessage() {
            message = Component.translatable(value.translationKey)
        }

        override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
            g.centeredText(
                Minecraft.getInstance().font,
                message,
                x + width / 2,
                y + (height - 8) / 2,
                0xFFFFFF,
            )
        }
    }

    companion object {
        private fun parseInt(value: String?): Int =
            try {
                if (value.isNullOrEmpty()) 0 else value.toInt()
            } catch (_: NumberFormatException) {
                0
            }

        private fun parseDouble(value: String?): Double =
            try {
                if (value.isNullOrEmpty()) 0.0 else value.toDouble()
            } catch (_: NumberFormatException) {
                0.0
            }
    }
}
