// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.blockentity.ScriptBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.network.UpdateScriptBlockPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

open class ScriptBlockScreen(pos: BlockPos) : Screen(Component.literal("scriptブロック")) {
    private val pos: BlockPos = pos.immutable()
    private val lines = mutableListOf<EditBox>()
    private var runOnRedstone: Boolean = true
    private var lastError: String = ""

    companion object {
        private const val LINE_COUNT = 12
        private const val LINE_LENGTH = 120
    }

    override fun init() {
        readState()
        val boxWidth = minOf(420, width - 40)
        val x = (width - boxWidth) / 2
        val startY = 38
        val splitLines = splitScript(readScript())
        for (i in 0 until LINE_COUNT) {
            val line = EditBox(font, x, startY + i * 18, boxWidth, 16, Component.literal("line$i"))
            line.setMaxLength(LINE_LENGTH)
            line.setValue(if (i < splitLines.size) splitLines[i] else "")
            addRenderableWidget(line)
            lines.add(line)
        }
        if (lines.isNotEmpty()) {
            setInitialFocus(lines[0])
        }

        addRenderableWidget(CycleButton.onOffBuilder(runOnRedstone)
            .create(x, startY + LINE_COUNT * 18 + 8, 120, 20, Component.literal("赤石実行")) { _, value -> runOnRedstone = value })
        addRenderableWidget(Button.builder(Component.literal("貼り付け")) { pasteClipboard() }
            .bounds(x + 128, startY + LINE_COUNT * 18 + 8, 70, 20)
            .build())
        addRenderableWidget(Button.builder(Component.literal("保存")) { submit(false) }
            .bounds(x + 206, startY + LINE_COUNT * 18 + 8, 70, 20)
            .build())
        addRenderableWidget(Button.builder(Component.literal("保存して実行")) { submit(true) }
            .bounds(x + 284, startY + LINE_COUNT * 18 + 8, 110, 20)
            .build())
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel")) { onClose() }
            .bounds(x + boxWidth - 70, startY + LINE_COUNT * 18 + 32, 70, 20)
            .build())
    }

    private fun readScript(): String {
        val be = Minecraft.getInstance().level?.getBlockEntity(pos)
        if (be is ScriptBlockEntity) {
            runOnRedstone = be.runOnRedstone
            lastError = be.lastError
            return be.script
        }
        return ""
    }

    private fun readState() {
        val be = Minecraft.getInstance().level?.getBlockEntity(pos)
        if (be is ScriptBlockEntity) {
            runOnRedstone = be.runOnRedstone
            lastError = be.lastError
        }
    }

    private fun splitScript(script: String): List<String> {
        val result = mutableListOf<String>()
        if (script.isEmpty()) return result
        val rawLines = Pattern.compile("\n").split(script.replace("\r", ""), -1)
        var i = 0
        while (i < rawLines.size && result.size < LINE_COUNT) {
            var current = rawLines[i]
            while (current.length > LINE_LENGTH && result.size < LINE_COUNT) {
                result.add(current.substring(0, LINE_LENGTH))
                current = current.substring(LINE_LENGTH)
            }
            if (result.size < LINE_COUNT) {
                result.add(current)
            }
            i++
        }
        return result
    }

    private fun pasteClipboard() {
        val splitLines = splitScript(Minecraft.getInstance().keyboardHandler.clipboard)
        for (i in lines.indices) {
            lines[i].setValue(if (i < splitLines.size) splitLines[i] else "")
        }
    }

    private fun submit(executeNow: Boolean) {
        val builder = StringBuilder()
        for (i in lines.indices) {
            val value = lines[i].value
            if (value.isEmpty() && builder.isEmpty()) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(value)
        }
        ClientNetworkHelper.sendToServer(UpdateScriptBlockPayload(pos, builder.toString(), runOnRedstone, executeNow))
        onClose()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val x = width / 2
        graphics.centeredText(font, title, x, 12, 0xFFFFFF)
        graphics.centeredText(font, Component.literal("level / world / pos / x / y / z / powered / redstone / train を使えます"), x, 24, 0xAAAAAA)
        if (lastError.isNotBlank()) {
            graphics.centeredText(font, Component.literal("前回: $lastError"), x, height - 14, 0xFF7777)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
