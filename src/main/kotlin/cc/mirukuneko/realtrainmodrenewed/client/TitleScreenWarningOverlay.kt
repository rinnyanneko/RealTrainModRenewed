// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectPackLoader
import cc.mirukuneko.realtrainmodrenewed.modelpack.VehicleModelPackManager
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehiclePackLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object TitleScreenWarningOverlay {
    private const val RELOAD_BUTTON_WIDTH = 120
    private const val RELOAD_BUTTON_HEIGHT = 20
    private const val STATUS_VISIBLE_MS = 5000L
    private var reloadStatus: Component? = null
    private var reloadStatusColor = 0xAAFFAA
    private var reloadStatusUntil = 0L
    private var reloadButton: Button? = null
    @Volatile
    private var reloadInProgress = false

    @JvmStatic
    @SubscribeEvent
    fun onScreenInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? TitleScreen ?: return
        val x = min(screen.width - RELOAD_BUTTON_WIDTH - 8, screen.width / 2 + 104)
        val y = 8
        val button = Button.builder(Component.translatable("button.realtrainmodrenewed.reload_packs")) { button ->
            button.active = false
            reloadPacks()
        }.bounds(max(8, x), y, RELOAD_BUTTON_WIDTH, RELOAD_BUTTON_HEIGHT).build()
        button.active = !reloadInProgress
        reloadButton = button
        event.addListener(button)
    }

    @JvmStatic
    @SubscribeEvent
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? TitleScreen ?: return
        val minecraft = Minecraft.getInstance()
        val graphics: GuiGraphicsExtractor = event.guiGraphics
        reloadButton?.active = !reloadInProgress
        val reloadStatusBottom = drawReloadStatus(screen, graphics, minecraft)
        val x = 8
        val y = max(RELOAD_BUTTON_HEIGHT + 14, reloadStatusBottom + 4)
        val warnings = PackRequirementWarnings.getWarnings()
        if (warnings.isEmpty()) {
            return
        }
        drawLines(graphics, minecraft, warnings, x, y, 0xFFFF66)
    }

    private fun drawLines(
        graphics: GuiGraphicsExtractor,
        minecraft: Minecraft,
        lines: List<String>,
        x: Int,
        y: Int,
        color: Int
    ) {
        var maxWidth = 0
        for (line in lines) {
            maxWidth = max(maxWidth, minecraft.font.width(line))
        }
        val height = lines.size * (minecraft.font.lineHeight + 2) + 6
        graphics.fill(x - 4, y - 4, x + maxWidth + 6, y + height, 0xB0200000.toInt())
        var lineY = y
        for (line in lines) {
            graphics.text(minecraft.font, line, x, lineY, color, false)
            lineY += minecraft.font.lineHeight + 2
        }
    }

    private fun drawReloadStatus(screen: TitleScreen, graphics: GuiGraphicsExtractor, minecraft: Minecraft): Int {
        val status = reloadStatus ?: return 0
        if (System.currentTimeMillis() > reloadStatusUntil) {
            return 0
        }
        val rawText = status.string
        if (rawText.isBlank()) {
            return 0
        }
        val buttonX = max(8, min(screen.width - RELOAD_BUTTON_WIDTH - 8, screen.width / 2 + 104))
        val buttonY = 8
        val maxBoxWidth = max(32, screen.width - 16)
        val text = truncateText(minecraft, rawText, maxBoxWidth - 10)
        val textWidth = minecraft.font.width(text)
        val boxWidth = min(maxBoxWidth, max(RELOAD_BUTTON_WIDTH, textWidth + 10))
        val x = max(8, min(screen.width - boxWidth - 8, buttonX + RELOAD_BUTTON_WIDTH - boxWidth))
        val y = buttonY + RELOAD_BUTTON_HEIGHT + 4
        graphics.fill(x, y, x + boxWidth, y + minecraft.font.lineHeight + 8, 0xB0200000.toInt())
        graphics.text(minecraft.font, text, x + (boxWidth - textWidth) / 2, y + 4, reloadStatusColor, false)
        return y + minecraft.font.lineHeight + 8
    }

    private fun truncateText(minecraft: Minecraft, text: String, maxWidth: Int): String {
        if (minecraft.font.width(text) <= maxWidth) {
            return text
        }
        val suffix = "..."
        val suffixWidth = minecraft.font.width(suffix)
        if (maxWidth <= suffixWidth) {
            return minecraft.font.plainSubstrByWidth(text, max(0, maxWidth))
        }
        return minecraft.font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix
    }

    private fun reloadPacks() {
        if (reloadInProgress) {
            return
        }
        reloadInProgress = true
        showReloadStatus(Component.translatable("message.realtrainmodrenewed.reload_packs.reloading"), 0xFFFF66)
        val minecraft = Minecraft.getInstance()
        PackButtonTextureCache.clear()
        MqoModelLoader.clearPackCaches()
        CompletableFuture.runAsync {
            RailPackLoader.reload()
            VehiclePackLoader.reload()
            InstalledObjectPackLoader.reload()
            VehicleModelPackManager.INSTANCE.onResourceManagerReload(minecraft.resourceManager)
            PackRequirementWarnings.refresh()
        }.whenComplete { _, throwable ->
            minecraft.execute {
                reloadInProgress = false
                reloadButton?.active = true
                if (throwable == null) {
                    showReloadStatus(Component.translatable("message.realtrainmodrenewed.reload_packs.done"), 0xAAFFAA)
                    RealTrainModRenewed.LOGGER.info("Reloaded RTM add-on packs from title screen")
                } else {
                    showReloadStatus(Component.translatable("message.realtrainmodrenewed.reload_packs.failed"), 0xFF8888)
                    RealTrainModRenewed.LOGGER.warn("Failed to reload RTM add-on packs from title screen", throwable)
                }
            }
        }
    }

    private fun showReloadStatus(component: Component, color: Int) {
        reloadStatus = component
        reloadStatusColor = color
        reloadStatusUntil = System.currentTimeMillis() + STATUS_VISIBLE_MS
    }
}
