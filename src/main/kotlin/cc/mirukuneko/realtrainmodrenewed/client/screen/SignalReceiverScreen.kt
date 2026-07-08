// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.network.BindSignalReceiverPayload
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalStateBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

open class SignalReceiverScreen(pos: BlockPos) : Screen(Component.literal("受信機")) {

    private val pos: BlockPos = pos.immutable()
    private lateinit var channelBox: EditBox

    override fun init() {
        val boxWidth = 140
        val x = (width - boxWidth) / 2
        val y = height / 2 - 18
        channelBox = EditBox(font, x, y, boxWidth, 20, Component.literal("番号"))
        channelBox.setMaxLength(10)
        readCurrentValue()
        addRenderableWidget(channelBox)
        setInitialFocus(channelBox)

        addRenderableWidget(
            Button.builder(Component.literal("接続")) { submit() }
                .bounds(width / 2 - 75, y + 30, 70, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) { onClose() }
                .bounds(width / 2 + 5, y + 30, 70, 20)
                .build()
        )
    }

    private fun readCurrentValue() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val blockEntity = level.getBlockEntity(pos)
        when {
            blockEntity is SignalRemoteBlockEntity && blockEntity.linkedChannel > 0 ->
                channelBox.value = blockEntity.linkedChannel.toString()
            blockEntity is SignalStateBlockEntity && blockEntity.linkedChannel > 0 ->
                channelBox.value = blockEntity.linkedChannel.toString()
        }
    }

    private fun submit() {
        try {
            val channel = channelBox.value.trim().toInt()
            ClientNetworkHelper.sendToServer(BindSignalReceiverPayload(pos, channel))
            onClose()
        } catch (_: NumberFormatException) {
            Minecraft.getInstance().player?.sendOverlayMessage(
                Component.literal("番号を入力してください")
            )
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFF)
        graphics.centeredText(font, Component.literal("通信機で表示した番号を入力"), width / 2, height / 2 - 28, 0xAAAAAA)
    }

    override fun isPauseScreen(): Boolean = false
}
