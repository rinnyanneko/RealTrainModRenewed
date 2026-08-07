// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalConverterBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.electric.SignalComparator
import cc.mirukuneko.realtrainmodrenewed.electric.SignalConverterType
import cc.mirukuneko.realtrainmodrenewed.network.ConfigureSignalConverterPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

class SignalConverterScreen(pos: BlockPos) : Screen(Component.translatable("block.realtrainmodrenewed.signal_converter")) {
    private val pos = pos.immutable()
    private lateinit var firstBox: EditBox
    private lateinit var secondBox: EditBox
    private lateinit var comparatorButton: Button
    private var converterType = SignalConverterType.RS_INPUT
    private var comparator = SignalComparator.EQUAL
    private var signalOnTrue = 15
    private var signalOnFalse = 0

    override fun init() {
        readState()
        val x = width / 2 - 75
        val y = height / 2 - 38
        firstBox = EditBox(font, x, y, 150, 20, firstLabel())
        firstBox.setMaxLength(11)
        firstBox.setValue(signalOnTrue.toString())
        addRenderableWidget(firstBox)

        secondBox = EditBox(font, x, y + 30, 150, 20, secondLabel())
        secondBox.setMaxLength(11)
        secondBox.setValue(signalOnFalse.toString())
        addRenderableWidget(secondBox)

        comparatorButton = Button.builder(comparatorLabel()) {
            comparator = SignalComparator.entries[(comparator.ordinal + 1) % SignalComparator.entries.size]
            comparatorButton.message = comparatorLabel()
        }.bounds(x, y + 60, 150, 20).build()
        comparatorButton.visible = converterType == SignalConverterType.RS_OUTPUT
        addRenderableWidget(comparatorButton)

        addRenderableWidget(Button.builder(Component.translatable("button.realtrainmodrenewed.save")) { submit() }
            .bounds(width / 2 - 80, y + 90, 75, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel")) { onClose() }
            .bounds(width / 2 + 5, y + 90, 75, 20).build())
        setInitialFocus(firstBox)
    }

    private fun readState() {
        val blockEntity = Minecraft.getInstance().level?.getBlockEntity(pos) as? SignalConverterBlockEntity ?: return
        converterType = blockEntity.converterType
        comparator = blockEntity.comparator
        signalOnTrue = blockEntity.signalOnTrue
        signalOnFalse = blockEntity.signalOnFalse
    }

    private fun submit() {
        try {
            ClientNetworkHelper.sendToServer(
                ConfigureSignalConverterPayload(
                    pos,
                    firstBox.value.trim().toInt(),
                    secondBox.value.trim().toInt(),
                    comparator.id,
                )
            )
            onClose()
        } catch (_: NumberFormatException) {
            Minecraft.getInstance().player?.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.number_required"))
        }
    }

    private fun firstLabel(): Component = Component.translatable(
        if (converterType == SignalConverterType.WIRELESS)
            "screen.realtrainmodrenewed.signal_converter.channel"
        else "screen.realtrainmodrenewed.signal_converter.signal_on_true"
    )

    private fun secondLabel(): Component = Component.translatable(
        if (converterType == SignalConverterType.WIRELESS)
            "screen.realtrainmodrenewed.signal_converter.chunk_range"
        else "screen.realtrainmodrenewed.signal_converter.signal_on_false"
    )

    private fun comparatorLabel(): Component = Component.translatable(
        "screen.realtrainmodrenewed.signal_converter.comparator",
        comparator.operator,
    )

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val centerY = height / 2
        graphics.centeredText(font, title, width / 2, centerY - 66, 0xFFFFFF)
        graphics.centeredText(font, firstLabel(), width / 2, centerY - 50, 0xAAAAAA)
        graphics.centeredText(font, secondLabel(), width / 2, centerY - 20, 0xAAAAAA)
    }

    override fun isPauseScreen(): Boolean = false
}
