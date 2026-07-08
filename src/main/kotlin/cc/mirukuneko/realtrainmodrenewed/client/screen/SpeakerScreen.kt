package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig
import cc.mirukuneko.realtrainmodrenewed.network.ConfigureSpeakerPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import java.util.Locale

open class SpeakerScreen(pos: BlockPos) : Screen(Component.literal("スピーカー設定")) {
    private val pos: BlockPos = pos.immutable()
    private lateinit var rangeBox: EditBox
    private lateinit var slotBox: EditBox
    private lateinit var soundBox: EditBox
    private var searchBox: EditBox? = null
    private var speakerRange = 32
    private var listTop = 0
    private var leftX = 0
    private val candidateButtons = mutableListOf<Button>()

    override fun init() {
        readState()
        leftX = (width - BOX_W) / 2
        val x = leftX
        // 画面上端から絶対配置にして、GUIスケールが大きくても見切れないようにする。
        val y = 22

        rangeBox = EditBox(font, x, y, 120, 18, Component.literal("可聴範囲(ブロック)"))
        rangeBox.setMaxLength(4)
        rangeBox.value = speakerRange.toString()
        addRenderableWidget(rangeBox)
        addRenderableWidget(
            Button.builder(Component.literal("範囲を設定")) { submitRange() }
                .bounds(x + 124, y, BOX_W - 124, 18)
                .build(),
        )

        val y2 = y + 28
        slotBox = EditBox(font, x, y2, 50, 18, Component.literal("信号強度(1-15)"))
        slotBox.setMaxLength(2)
        slotBox.value = "15"
        addRenderableWidget(slotBox)

        soundBox = EditBox(font, x + 56, y2, BOX_W - 56, 18, Component.literal("サウンドID"))
        soundBox.setMaxLength(128)
        addRenderableWidget(soundBox)

        addRenderableWidget(
            Button.builder(Component.literal("この信号レベルに割当")) { submitSound() }
                .bounds(x, y2 + 20, BOX_W, 18)
                .build(),
        )

        val y3 = y2 + 44
        val newSearchBox = EditBox(font, x, y3, BOX_W, 18, Component.literal("音を検索"))
        searchBox = newSearchBox
        newSearchBox.setMaxLength(64)
        newSearchBox.setResponder { rebuildCandidates() }
        addRenderableWidget(newSearchBox)

        listTop = y3 + 22

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { onClose() }
                .bounds(width / 2 - 50, height - 26, 100, 20)
                .build(),
        )

        rebuildCandidates()
    }

    private fun readState() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val blockEntity = level?.getBlockEntity(pos)
        if (blockEntity is InstalledObjectBlockEntity) {
            speakerRange = blockEntity.speakerRange
        }
    }

    private fun rebuildCandidates() {
        for (button in candidateButtons) {
            removeWidget(button)
        }
        candidateButtons.clear()

        val minecraft = Minecraft.getInstance()
        if (minecraft.soundManager == null) {
            return
        }
        val query = searchBox?.value?.trim()?.lowercase(Locale.ROOT) ?: ""

        var shown = 0
        for (id in minecraft.soundManager.availableSounds) {
            if (shown >= MAX_CANDIDATES) {
                break
            }
            val idString = id.toString()
            if (query.isNotEmpty() && !idString.lowercase(Locale.ROOT).contains(query)) {
                continue
            }
            val chosen = idString
            val button = Button.builder(Component.literal(idString)) {
                soundBox.value = chosen
            }
                .bounds(leftX, listTop + shown * ROW_H, BOX_W, ROW_H - 1)
                .build()
            addRenderableWidget(button)
            candidateButtons.add(button)
            shown++
        }
    }

    private fun submitRange() {
        try {
            val range = rangeBox.value.trim().toInt()
            ClientNetworkHelper.sendToServer(ConfigureSpeakerPayload(pos, 0, "", range.coerceAtLeast(1)))
            speakerRange = range.coerceAtLeast(1)
            toast("範囲を $speakerRange に設定しました")
        } catch (ignored: NumberFormatException) {
            notifyNumber()
        }
    }

    private fun submitSound() {
        try {
            val slot = slotBox.value.trim().toInt()
            val sound = soundBox.value.trim()
            if (slot < 1 || slot > 15) {
                toast("信号強度は 1〜15 で入力してください")
                return
            }
            ClientNetworkHelper.sendToServer(ConfigureSpeakerPayload(pos, slot, sound, 0))
            toast(
                if (sound.isEmpty()) {
                    "信号強度 $slot の割り当てを解除しました"
                } else {
                    "信号強度 $slot → $sound を割り当てました"
                },
            )
        } catch (ignored: NumberFormatException) {
            notifyNumber()
        }
    }

    private fun notifyNumber() {
        toast("数字で入力してください")
    }

    private fun toast(message: String) {
        Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(message))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFF)

        var py = listTop + MAX_CANDIDATES * ROW_H + 4
        graphics.text(font, Component.literal("現在の割当 (信号強度=音)"), leftX, py, 0xAAAAAA, false)
        py += 11
        val line = StringBuilder()
        var perLine = 0
        for (i in 1..15) {
            val sound = SpeakerSoundConfig.getSound(i) ?: continue
            val shortSound = if (sound.length > 26) sound.substring(0, 25) + "…" else sound
            line.append(i).append("=").append(shortSound).append("   ")
            if (++perLine >= 1) {
                graphics.text(font, Component.literal(line.toString()), leftX, py, 0x88FF88, false)
                py += 10
                line.setLength(0)
                perLine = 0
                if (py > height - 32) {
                    break
                }
            }
        }
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        private const val MAX_CANDIDATES = 6
        private const val BOX_W = 220
        private const val ROW_H = 16
    }
}
