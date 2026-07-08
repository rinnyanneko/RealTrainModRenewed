// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
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
    @Volatile
    private var reloadInProgress = false

    @JvmStatic
    @SubscribeEvent
    fun onScreenInit(event: ScreenEvent.Init.Post) {
        if (event.screen !is TitleScreen) {
            return
        }
        val screen = event.screen
        val x = min(screen.width - RELOAD_BUTTON_WIDTH - 8, screen.width / 2 + 104)
        val y = 8
        event.addListener(
            Button.builder(Component.translatable("button.realtrainmodrenewed.reload_packs")) { button ->
                button.active = false
                reloadPacks()
            }.bounds(max(8, x), y, RELOAD_BUTTON_WIDTH, RELOAD_BUTTON_HEIGHT).build().also { button ->
                button.active = !reloadInProgress
            }
        )
    }

    @JvmStatic
    @SubscribeEvent
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if (event.screen !is TitleScreen) {
            return
        }
        val minecraft = Minecraft.getInstance()
        val graphics: GuiGraphicsExtractor = event.guiGraphics
        val x = 8
        var y = RELOAD_BUTTON_HEIGHT + 14
        val status = reloadStatus
        if (status != null && System.currentTimeMillis() <= reloadStatusUntil) {
            drawLines(graphics, minecraft, listOf(status.string), x, y, reloadStatusColor)
            y += minecraft.font.lineHeight + 8
        }
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

    private fun reloadPacks() {
        if (reloadInProgress) {
            return
        }
        reloadInProgress = true
        showReloadStatus(Component.translatable("message.realtrainmodrenewed.reload_packs.reloading"), 0xFFFF66)
        val minecraft = Minecraft.getInstance()
        PackButtonTextureCache.clear()
        CompletableFuture.runAsync {
            RailPackLoader.reload()
            VehiclePackLoader.reload()
            InstalledObjectPackLoader.reload()
            VehicleModelPackManager.INSTANCE.onResourceManagerReload(minecraft.resourceManager)
            PackRequirementWarnings.refresh()
        }.whenComplete { _, throwable ->
            minecraft.execute {
                reloadInProgress = false
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
