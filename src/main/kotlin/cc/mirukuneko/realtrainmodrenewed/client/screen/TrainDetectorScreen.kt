package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.blockentity.TrainDetectorBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.network.ConfigureTrainDetectorPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

open class TrainDetectorScreen(pos: BlockPos) : Screen(Component.literal("電車検知ブロック")) {
    private val pos: BlockPos = pos.immutable()
    private lateinit var channelBox: EditBox
    private lateinit var rangeBox: EditBox
    private var linkedChannel: Int = -1
    private var detectionRange: Int = 3
    private var occupied: Boolean = false

    override fun init() {
        readState()
        val boxWidth = 150
        val x = (width - boxWidth) / 2
        val y = height / 2 - 36
        channelBox = EditBox(font, x, y, boxWidth, 20, Component.literal("信号番号"))
        channelBox.setMaxLength(10)
        channelBox.setValue(if (linkedChannel > 0) linkedChannel.toString() else "")
        addRenderableWidget(channelBox)

        rangeBox = EditBox(font, x, y + 30, boxWidth, 20, Component.literal("検知範囲"))
        rangeBox.setMaxLength(3)
        rangeBox.setValue(detectionRange.toString())
        addRenderableWidget(rangeBox)
        setInitialFocus(channelBox)

        addRenderableWidget(
            Button.builder(Component.literal("保存")) { submit() }
                .bounds(width / 2 - 80, y + 66, 75, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) { onClose() }
                .bounds(width / 2 + 5, y + 66, 75, 20)
                .build()
        )
    }

    private fun readState() {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) {
            return
        }
        val blockEntity = minecraft.level!!.getBlockEntity(pos)
        if (blockEntity is TrainDetectorBlockEntity) {
            linkedChannel = blockEntity.linkedChannel
            detectionRange = blockEntity.detectionRange
            occupied = blockEntity.isOccupied()
        }
    }

    private fun submit() {
        try {
            val channel = if (channelBox.value.trim().isEmpty()) -1
                else channelBox.value.trim().toInt()
            val range = rangeBox.value.trim().toInt()
            ClientNetworkHelper.sendToServer(ConfigureTrainDetectorPayload(pos, channel, range))
            onClose()
        } catch (ignored: NumberFormatException) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player!!.sendOverlayMessage(
                    Component.literal("数字で入力してください")
                )
            }
        }
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val centerY = height / 2
        graphics.centeredText(font, title, width / 2, centerY - 62, 0xFFFFFF)
        graphics.centeredText(
            font,
            Component.literal("占有中: " + if (occupied) "はい" else "いいえ"),
            width / 2,
            centerY - 50,
            if (occupied) 0xFF6666 else 0x66FF66
        )
        graphics.centeredText(
            font,
            Component.literal("信号番号を入れると occupied=停止 / clear=進行"),
            width / 2,
            centerY - 18,
            0xAAAAAA
        )
        graphics.centeredText(
            font,
            Component.literal("空欄なら赤石検知だけ使えます"),
            width / 2,
            centerY - 6,
            0xAAAAAA
        )
    }

    override fun isPauseScreen(): Boolean = false
}
