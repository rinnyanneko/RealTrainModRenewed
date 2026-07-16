// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.network.BindSignalReceiverPayload
import cc.mirukuneko.realtrainmodrenewed.network.SetSignalValuePayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

open class SignalValueScreen(pos: BlockPos) : Screen(Component.translatable("block.realtrainmodrenewed.signal_value_receiver")) {
    private val pos: BlockPos = pos.immutable()
    private lateinit var channelBox: EditBox
    private lateinit var valueBox: EditBox
    private var linkedChannel: Int = -1

    override fun init() {
        linkedChannel = readLinkedChannel()
        val boxWidth = 150
        val x = (width - boxWidth) / 2
        val baseY = height / 2 - 30

        if (linkedChannel > 0) {
            valueBox = EditBox(font, x, baseY, boxWidth, 20, Component.translatable("screen.realtrainmodrenewed.signal.value"))
            valueBox.setMaxLength(10)
            addRenderableWidget(valueBox)
            setInitialFocus(valueBox)
        } else {
            channelBox = EditBox(font, x, baseY, boxWidth, 20, Component.translatable("screen.realtrainmodrenewed.signal.channel"))
            channelBox.setMaxLength(10)
            addRenderableWidget(channelBox)
            setInitialFocus(channelBox)
        }

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { submit() }
                .bounds(width / 2 - 75, baseY + 40, 70, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) { onClose() }
                .bounds(width / 2 + 5, baseY + 40, 70, 20)
                .build()
        )
    }

    private fun submit() {
        try {
            if (linkedChannel > 0) {
                val signalValue = valueBox.value.trim().toInt()
                ClientNetworkHelper.sendToServer(SetSignalValuePayload(pos, signalValue))
            } else {
                val channel = channelBox.value.trim().toInt()
                ClientNetworkHelper.sendToServer(BindSignalReceiverPayload(pos, channel))
            }
            onClose()
        } catch (ignored: NumberFormatException) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player!!.sendOverlayMessage(
                    Component.translatable(
                        if (linkedChannel > 0) {
                            "message.realtrainmodrenewed.signal.enter_value"
                        } else {
                            "message.realtrainmodrenewed.signal.enter_channel"
                        },
                    )
                )
            }
        }
    }

    private fun readLinkedChannel(): Int {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) {
            return -1
        }
        val blockEntity = minecraft.level!!.getBlockEntity(pos)
        if (blockEntity is SignalRemoteBlockEntity) {
            return blockEntity.linkedChannel
        }
        return -1
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, height / 2 - 70, 0xFFFFFF)
        if (linkedChannel > 0) {
            graphics.centeredText(
                font,
                Component.translatable("screen.realtrainmodrenewed.signal_value.registered_channel", linkedChannel),
                width / 2,
                height / 2 - 58,
                0xAAAAAA
            )
            graphics.centeredText(
                font,
                Component.translatable("screen.realtrainmodrenewed.signal_value.enter_value_now"),
                width / 2,
                height / 2 - 46,
                0xAAAAAA
            )
        } else {
            graphics.centeredText(
                font,
                Component.translatable("screen.realtrainmodrenewed.signal_value.register_channel_first"),
                width / 2,
                height / 2 - 58,
                0xAAAAAA
            )
            graphics.centeredText(
                font,
                Component.translatable("screen.realtrainmodrenewed.signal_value.enter_value_next_time"),
                width / 2,
                height / 2 - 46,
                0xAAAAAA
            )
        }
    }

    override fun isPauseScreen(): Boolean = false
}
