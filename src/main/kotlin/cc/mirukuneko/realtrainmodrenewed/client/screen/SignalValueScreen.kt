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

open class SignalValueScreen(pos: BlockPos) : Screen(Component.literal("受信機(signal値)")) {
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
            valueBox = EditBox(font, x, baseY, boxWidth, 20, Component.literal("signal値"))
            valueBox.setMaxLength(10)
            addRenderableWidget(valueBox)
            setInitialFocus(valueBox)
        } else {
            channelBox = EditBox(font, x, baseY, boxWidth, 20, Component.literal("信号番号"))
            channelBox.setMaxLength(10)
            addRenderableWidget(channelBox)
            setInitialFocus(channelBox)
        }

        addRenderableWidget(
            Button.builder(Component.literal("決定")) { submit() }
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
                    Component.literal(
                        if (linkedChannel > 0) "signal値を入力してください" else "信号番号を入力してください"
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
                Component.literal("登録済み信号番号: $linkedChannel"),
                width / 2,
                height / 2 - 58,
                0xAAAAAA
            )
            graphics.centeredText(
                font,
                Component.literal("今回は signal値 のみ入力"),
                width / 2,
                height / 2 - 46,
                0xAAAAAA
            )
        } else {
            graphics.centeredText(
                font,
                Component.literal("先に信号番号を登録"),
                width / 2,
                height / 2 - 58,
                0xAAAAAA
            )
            graphics.centeredText(
                font,
                Component.literal("次回開いたときに signal値 を入力"),
                width / 2,
                height / 2 - 46,
                0xAAAAAA
            )
        }
    }

    override fun isPauseScreen(): Boolean = false
}
