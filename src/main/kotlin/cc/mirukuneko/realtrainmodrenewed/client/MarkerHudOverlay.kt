// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.Config
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import cc.mirukuneko.realtrainmodrenewed.rail.util.MarkerSearch
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object MarkerHudOverlay {
    private const val CYAN = 0xFF00EAEA.toInt()
    private const val GREEN = 0xD800E000.toInt()
    private const val GREEN_DARK = 0xD800B800.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val RED = 0xFFFF2020.toInt()
    private const val BLACK = 0xAA000000.toInt()
    private var cachedOrigin: BlockPos? = null
    private var cachedAtMs = 0L
    private var cachedMarkers: List<MarkerDistance> = emptyList()
    private val menuOptions = MarkerHudOption.entries.toTypedArray()
    private var selectedIndex = 0
    private var fitNeighborEnabled = true
    private var distanceEnabled = true
    private var gridEnabled = false
    private var line1Enabled = true
    private var line2Enabled = false
    private var anchor21Enabled = false

    @JvmStatic
    fun isLine1Enabled(): Boolean = line1Enabled

    @JvmStatic
    fun isLine2Enabled(): Boolean = line2Enabled

    @JvmStatic
    fun isGridEnabled(): Boolean = gridEnabled

    @JvmStatic
    fun isDistanceEnabled(): Boolean = distanceEnabled

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val previousClicks = drainClicks(TrainControlKeyMappings.MARKER_HUD_PREVIOUS)
        val nextClicks = drainClicks(TrainControlKeyMappings.MARKER_HUD_NEXT)
        val toggleClicks = drainClicks(TrainControlKeyMappings.MARKER_HUD_TOGGLE)
        if (!isMarkerTargeted(minecraft)) return

        repeat(previousClicks) {
            selectedIndex = (selectedIndex + menuOptions.size - 1) % menuOptions.size
        }
        repeat(nextClicks) {
            selectedIndex = (selectedIndex + 1) % menuOptions.size
        }
        repeat(toggleClicks) {
            toggleSelected()
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) {
            return
        }

        val hit = minecraft.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return

        val marker = level.getBlockEntity(hit.blockPos) as? MarkerBlockEntity ?: return
        val markerPosition = marker.markerRP ?: return
        val nearby = cachedNearbyMarkers(level, hit.blockPos, markerPosition)

        val graphics = event.guiGraphics
        val font = minecraft.font
        val screenW = minecraft.window.guiScaledWidth
        val screenH = minecraft.window.guiScaledHeight
        val panelX = max(10, screenW / 2 - 190)
        val panelY = max(16, screenH / 2 - 98)

        drawMarkerMenu(graphics, font, panelX, panelY, marker, nearby)
        if (distanceEnabled) {
            drawDistanceHints(graphics, font, screenW, screenH, nearby)
        }
    }

    private fun drawMarkerMenu(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        marker: MarkerBlockEntity,
        nearby: List<MarkerDistance>
    ) {
        val lines = menuOptions.mapIndexed { index, option ->
            val cursor = if (index == selectedIndex) "> " else "  "
            cursor + option.label + " : " + state(option.isEnabled())
        }
        var width = 0
        for (line in lines) {
            width = max(width, font.width(line))
        }
        width += 18
        val rowHeight = font.lineHeight + 8
        val height = lines.size * rowHeight + 8

        graphics.fill(x - 4, y - 4, x + width + 4, y + height + 4, CYAN)
        graphics.fill(x, y, x + width, y + height, GREEN_DARK)
        for (i in lines.indices) {
            val rowY = y + 4 + i * rowHeight
            val selected = i == selectedIndex
            graphics.fill(x + 4, rowY, x + width - 4, rowY + rowHeight - 2, if (selected) CYAN else GREEN)
            if (selected) {
                graphics.fill(x + 7, rowY + 3, x + width - 7, rowY + rowHeight - 5, GREEN)
            }
            graphics.text(font, lines[i], x + 9, rowY + 4, WHITE, true)
        }
        val hint = "UP/DOWN select  ENTER toggle"
        val hintY = y + height + 8
        graphics.fill(x, hintY - 2, x + font.width(hint) + 8, hintY + font.lineHeight + 3, BLACK)
        graphics.text(font, hint, x + 4, hintY, WHITE, true)
    }

    private fun drawDistanceHints(
        graphics: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        screenH: Int,
        nearby: List<MarkerDistance>
    ) {
        if (nearby.isEmpty()) {
            return
        }

        val nearest = nearby.first()
        val label = "${nearest.meters.roundToInt()}m"
        val centerY = screenH / 2 + 30
        graphics.text(font, label, screenW / 2 + 28, centerY, RED, true)

        val list = nearby.take(5)
        var width = 0
        val lines = list.map {
            val d = it.meters.roundToInt()
            "${d}m  ${relativeBlockLabel(it)}"
        }
        for (line in lines) {
            width = max(width, font.width(line))
        }

        val x = min(screenW - width - 12, screenW / 2 + 64)
        val y = centerY + 14
        graphics.fill(x - 4, y - 3, x + width + 6, y + lines.size * (font.lineHeight + 2) + 3, BLACK)
        for (i in lines.indices) {
            graphics.text(font, lines[i], x, y + i * (font.lineHeight + 2), RED, true)
        }
    }

    private fun cachedNearbyMarkers(level: Level, origin: BlockPos, originRp: RailPosition): List<MarkerDistance> {
        val now = System.currentTimeMillis()
        if (origin == cachedOrigin && now - cachedAtMs < 250L) {
            return cachedMarkers
        }
        cachedOrigin = origin
        cachedAtMs = now
        cachedMarkers = collectNearbyMarkers(level, origin, originRp)
        return cachedMarkers
    }

    private fun collectNearbyMarkers(level: Level, origin: BlockPos, originRp: RailPosition): List<MarkerDistance> {
        val result = ArrayList<MarkerDistance>()
        val candidateColumns = HashSet<Long>()
        val yawRadians = Math.toRadians(originRp.anchorYaw.toDouble())
        val stepX = kotlin.math.sin(yawRadians)
        val stepZ = kotlin.math.cos(yawRadians)
        val searchRange = Config.RAIL_MARKER_SEARCH_RANGE.get()
        for (meters in 1..searchRange) {
            candidateColumns.add(horizontalKey(origin.x + (stepX * meters).roundToInt(), origin.z + (stepZ * meters).roundToInt()))
            if (line2Enabled) {
                candidateColumns.add(horizontalKey(origin.x - (stepX * meters).roundToInt(), origin.z - (stepZ * meters).roundToInt()))
            }
        }

        MarkerSearch.forEachInRange(level, origin) { pos, marker ->
            if (pos == origin || horizontalKey(pos.x, pos.z) !in candidateColumns) return@forEachInRange
            val rp = marker.markerRP ?: return@forEachInRange
            val distance = distance(originRp, rp)
            result.add(MarkerDistance(pos, pos.x - origin.x, pos.y - origin.y, pos.z - origin.z, distance))
        }
        result.sortBy { it.meters }
        return result
    }

    private fun horizontalKey(x: Int, z: Int): Long =
        (x.toLong() shl 32) xor (z.toLong() and 0xFFFF_FFFFL)

    private fun distance(a: RailPosition, b: RailPosition): Double {
        val dx = b.posX - a.posX
        val dy = b.posY - a.posY
        val dz = b.posZ - a.posZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun relativeBlockLabel(marker: MarkerDistance): String =
        "(${signed(marker.dx)}, ${signed(marker.dy)}, ${signed(marker.dz)})"

    private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

    private fun state(enabled: Boolean): String = if (enabled) "ON" else "OFF"

    private fun isMarkerTargeted(minecraft: Minecraft): Boolean {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui) {
            return false
        }
        val hit = minecraft.hitResult as? BlockHitResult ?: return false
        return hit.type == HitResult.Type.BLOCK && minecraft.level!!.getBlockEntity(hit.blockPos) is MarkerBlockEntity
    }

    private fun toggleSelected() {
        when (menuOptions[selectedIndex]) {
            MarkerHudOption.FIT_NEIGHBOR -> fitNeighborEnabled = !fitNeighborEnabled
            MarkerHudOption.DISTANCE -> distanceEnabled = !distanceEnabled
            MarkerHudOption.GRID -> gridEnabled = !gridEnabled
            MarkerHudOption.LINE1 -> line1Enabled = !line1Enabled
            MarkerHudOption.LINE2 -> line2Enabled = !line2Enabled
            MarkerHudOption.ANCHOR21 -> anchor21Enabled = !anchor21Enabled
        }
    }

    private fun drainClicks(mapping: net.minecraft.client.KeyMapping): Int {
        var count = 0
        while (mapping.consumeClick()) {
            count++
        }
        return count
    }

    private fun MarkerHudOption.isEnabled(): Boolean =
        when (this) {
            MarkerHudOption.FIT_NEIGHBOR -> fitNeighborEnabled
            MarkerHudOption.DISTANCE -> distanceEnabled
            MarkerHudOption.GRID -> gridEnabled
            MarkerHudOption.LINE1 -> line1Enabled
            MarkerHudOption.LINE2 -> line2Enabled
            MarkerHudOption.ANCHOR21 -> anchor21Enabled || isCurrentMarkerConfigured()
        }

    private fun isCurrentMarkerConfigured(): Boolean {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return false
        val hit = minecraft.hitResult as? BlockHitResult ?: return false
        return (level.getBlockEntity(hit.blockPos) as? MarkerBlockEntity)?.isConfigured() == true
    }

    private enum class MarkerHudOption(val label: String) {
        FIT_NEIGHBOR("FIT_NEIGHBOR"),
        DISTANCE("DISTANCE"),
        GRID("GRID"),
        LINE1("LINE1"),
        LINE2("LINE2"),
        ANCHOR21("ANCHOR21"),
    }

    private data class MarkerDistance(
        val blockPos: BlockPos,
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val meters: Double
    )
}
