// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.network.SetSignalAspectPayload
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

open class SignalChangerScreen(pos: BlockPos) : Screen(Component.translatable("block.realtrainmodrenewed.signal_changer")) {

    private val pos: BlockPos = pos.immutable()
    private var titleY: Int = 0

    companion object {
        private const val TITLE_TOP = 18
        private const val TITLE_BUTTON_GAP = 18
        private const val BOTTOM_MARGIN = 20
    }

    override fun init() {
        val aspects = SignalAspect.entries
        val count = aspects.size
        val widthButton = minOf(220, width - 40)
        val availableHeight = maxOf(140, height - TITLE_TOP - TITLE_BUTTON_GAP - BOTTOM_MARGIN)
        val gap = Mth.clamp((availableHeight - count * 20) / maxOf(1, count - 1), 6, 10)
        val heightButton = Mth.clamp((availableHeight - gap * maxOf(0, count - 1)) / count, 20, 24)
        val totalHeight = count * heightButton + maxOf(0, count - 1) * gap
        val startX = (width - widthButton) / 2
        val startY = maxOf(TITLE_TOP + TITLE_BUTTON_GAP, (height - totalHeight) / 2)
        titleY = maxOf(8, startY - TITLE_BUTTON_GAP)

        aspects.forEachIndexed { index, aspect ->
            val y = startY + index * (heightButton + gap)
            addRenderableWidget(
                Button.builder(
                    Component.translatable(aspect.translationKey),
                ) {
                    ClientNetworkHelper.sendToServer(SetSignalAspectPayload(pos, aspect.id))
                    onClose()
                }.bounds(startX, y, widthButton, heightButton).build()
            )
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, titleY, 0xFFFFFF)
    }

    override fun isPauseScreen(): Boolean = false
}
