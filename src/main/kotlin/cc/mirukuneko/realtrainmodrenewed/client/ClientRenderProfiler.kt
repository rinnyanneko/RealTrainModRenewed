// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiEvent
import java.util.Locale
import kotlin.math.max

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object ClientRenderProfiler {
    private val categoryNames = arrayOf("Rail", "Train", "Object")
    private const val CATEGORY_RAIL = 0
    private const val CATEGORY_TRAIN = 1
    private const val CATEGORY_OBJECT = 2

    private val totalsNs = LongArray(categoryNames.size)
    private val counts = IntArray(categoryNames.size)
    private val displayTotalsNs = LongArray(categoryNames.size)
    private val displayCounts = IntArray(categoryNames.size)

    private var lastSnapshotNs = System.nanoTime()
    private var overlayEnabled = false

    @JvmStatic
    fun toggleOverlay() {
        overlayEnabled = !overlayEnabled
    }

    @JvmStatic
    fun begin(): Long = System.nanoTime()

    @JvmStatic
    fun endRail(startNs: Long) {
        record(CATEGORY_RAIL, startNs)
    }

    @JvmStatic
    fun endTrain(startNs: Long) {
        record(CATEGORY_TRAIN, startNs)
    }

    @JvmStatic
    fun endInstalledObject(startNs: Long) {
        record(CATEGORY_OBJECT, startNs)
    }

    @Synchronized
    private fun record(category: Int, startNs: Long) {
        val elapsed = System.nanoTime() - startNs
        totalsNs[category] += elapsed
        counts[category]++
        snapshotIfNeeded()
    }

    private fun snapshotIfNeeded() {
        val now = System.nanoTime()
        if (now - lastSnapshotNs < 1_000_000_000L) {
            return
        }
        totalsNs.copyInto(displayTotalsNs)
        counts.copyInto(displayCounts)
        totalsNs.fill(0L)
        counts.fill(0)
        lastSnapshotNs = now
    }

    @JvmStatic
    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.screen != null || !overlayEnabled) {
            return
        }

        snapshotIfNeeded()

        val graphics: GuiGraphicsExtractor = event.guiGraphics
        val font = minecraft.font
        val x = 8
        val y = 8
        val lineHeight = font.lineHeight + 2
        val lines = Array(categoryNames.size + 1) { index ->
            if (index == 0) {
                "Profiler [F8]"
            } else {
                val category = index - 1
                val totalMs = displayTotalsNs[category] / 1_000_000.0
                val avgMs = if (displayCounts[category] > 0) totalMs / displayCounts[category] else 0.0
                categoryNames[category] + ": " +
                    String.format(Locale.ROOT, "%.2f ms", totalMs) +
                    " / " +
                    String.format(Locale.ROOT, "%.2f avg", avgMs) +
                    " (" + displayCounts[category] + ")"
            }
        }

        var width = 0
        for (line in lines) {
            width = max(width, font.width(line))
        }

        graphics.fill(x - 4, y - 4, x + width + 6, y + lineHeight * lines.size + 2, 0x90000000.toInt())
        for (i in lines.indices) {
            graphics.text(font, lines[i], x, y + i * lineHeight, 0xFFFFFF, false)
        }
    }
}
