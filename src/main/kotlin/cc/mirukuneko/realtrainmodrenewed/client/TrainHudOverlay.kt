// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiEvent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object TrainHudOverlay {
    private val CAB_TEXTURE: Identifier =
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/gui/rtm_cab.png")
    private const val TEX_SIZE = 512
    private const val CAB_W = 416
    private const val CAB_H = 48
    private const val HUD_GREEN = 0xFF40FF40.toInt()
    private const val HUD_RED = 0xFFE02020.toInt()
    private const val HUD_DARK = 0xFF303030.toInt()
    private var cabHidden = false

    @JvmStatic
    fun toggleCabHidden() {
        cabHidden = !cabHidden
    }

    @JvmStatic
    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return
        }
        val train = getControlledTrain(mc)
        if (train == null || !train.isDriverPassenger(mc.player)) {
            return
        }

        val graphics = event.guiGraphics
        val font = mc.font
        val screenW = mc.window.guiScaledWidth
        val screenH = mc.window.guiScaledHeight
        val definition = VehicleRegistry.getById(train.vehicleId)
        val showCabOverlay = definition == null || !definition.isNotDisplayCab()

        if (!cabHidden && showCabOverlay) {
            renderDefaultRtmCab(graphics, font, train, definition, screenW, screenH)
        }
    }

    private fun renderDefaultRtmCab(
        graphics: GuiGraphicsExtractor,
        font: Font,
        train: TrainEntity,
        definition: VehicleDefinition?,
        screenW: Int,
        screenH: Int,
    ) {
        val scale = min(1.0f, screenW / CAB_W.toFloat())
        val x = ((screenW - CAB_W * scale) * 0.5f).roundToInt()
        val y = (screenH - CAB_H * scale).roundToInt()
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            CAB_TEXTURE,
            x,
            y,
            0.0f,
            0.0f,
            (CAB_W * scale).roundToInt(),
            (CAB_H * scale).roundToInt(),
            TEX_SIZE,
            TEX_SIZE,
        )
        drawGaugeNeedle(graphics, x, y, scale, 32, 19, 13.0f, getGaugeAngle(240.0f * getBrakeRatio(train)), HUD_RED)
        drawGaugeNeedle(graphics, x, y, scale, 32, 19, 9.0f, getGaugeAngle(240.0f * getBrakeCommandRatio(train)), HUD_DARK)
        drawGaugeNeedle(graphics, x, y, scale, 72, 19, 13.0f, getGaugeAngle(getSpeedNeedleRotation(train, definition)), HUD_RED)
        drawLever(graphics, x, y, scale, train)
        drawWatch(graphics, x, y, scale, train)
        drawCenteredText(graphics, font, getSpeedKmh(train).toString(), x, y, scale, 72, 37)
        drawCenteredText(graphics, font, max(0, -train.notch).toString(), x, y, scale, 32, 37)
        graphics.text(font, getWorldTime().toString(), scaledX(x, scale, 338), scaledY(y, scale, 8), HUD_GREEN, true)
        graphics.text(font, getClockText(), scaledX(x, scale, 338), scaledY(y, scale, 18), HUD_GREEN, true)
    }

    private fun getControlledTrain(mc: Minecraft): TrainEntity? {
        val player = mc.player ?: return null
        val vehicle = player.vehicle
        if (vehicle is TrainEntity) {
            return vehicle
        }
        if (vehicle is TrainSeatEntity) {
            return vehicle.getTrain()
        }
        return null
    }

    private fun drawLever(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, train: TrainEntity) {
        val offset = 3.0f * train.notch
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, 100, (27 + offset).roundToInt(), 0.0f, 80.0f, 8, 3, TEX_SIZE, TEX_SIZE)
        pose.popMatrix()
    }

    private fun drawWatch(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, train: TrainEntity) {
        val startX = 320
        val startY = 32
        val t0 = getWorldTime(train)
        val hour12 = (t0 / 1000 + 6) % 12
        drawMeter(graphics, x, y, scale, startX, startY, 32, 96, 48, 360.0f * hour12 / 12.0f + 135.0f)
        val minute = ((t0 % 1000) * 0.06f).toInt()
        drawMeter(graphics, x, y, scale, startX, startY, 32, 128, 48, 360.0f * minute / 60.0f + 135.0f)
    }

    private fun drawMeter(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        scale: Float,
        localX: Int,
        localY: Int,
        size: Int,
        u: Int,
        v: Int,
        rotation: Float,
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x + localX * scale, y + localY * scale)
        pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
        pose.scale(scale, scale)
        val offset = -(size / 2)
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, offset, offset, u.toFloat(), v.toFloat(), size, size, TEX_SIZE, TEX_SIZE)
        pose.popMatrix()
    }

    private fun drawGaugeNeedle(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        scale: Float,
        localX: Int,
        localY: Int,
        length: Float,
        angleDegrees: Float,
        color: Int,
    ) {
        val cx = scaledX(x, scale, localX)
        val cy = scaledY(y, scale, localY)
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = cos(radians)
        val dy = sin(radians)
        val steps = max(1, (length * scale).roundToInt())
        for (i in 0..steps) {
            val t = i / steps.toDouble()
            val px = (cx + dx * length * scale * t).roundToInt()
            val py = (cy + dy * length * scale * t).roundToInt()
            graphics.fill(px, py, px + 1, py + 1, color)
        }
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFE8E8E8.toInt())
    }

    private fun getGaugeAngle(rotation: Float): Float = 150.0f + rotation

    private fun scaledX(x: Int, scale: Float, localX: Int): Int =
        (x + localX * scale).roundToInt()

    private fun scaledY(y: Int, scale: Float, localY: Int): Int =
        (y + localY * scale).roundToInt()

    private fun drawCenteredText(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        scale: Float,
        localX: Int,
        localY: Int,
    ) {
        val textX = scaledX(x, scale, localX) - font.width(text) / 2
        val textY = scaledY(y, scale, localY)
        graphics.fill(textX - 2, textY - 1, textX + font.width(text) + 2, textY + font.lineHeight, 0xCC000000.toInt())
        graphics.text(font, text, textX, textY, HUD_GREEN, true)
    }

    private fun getSpeedKmh(train: TrainEntity): Int =
        (abs(train.speed) * 72.0f).roundToInt()

    private fun getSpeedNeedleRotation(train: TrainEntity, definition: VehicleDefinition?): Float {
        var maxSpeed = 120.0f
        if (definition != null && definition.notchMaxSpeeds.isNotEmpty()) {
            for (speed in definition.notchMaxSpeeds) {
                maxSpeed = max(maxSpeed, speed)
            }
        }
        return min(270.0f, 270.0f * getSpeedKmh(train) / max(1.0f, maxSpeed))
    }

    private fun getBrakeRatio(train: TrainEntity): Float =
        min(1.0f, max(0.0f, train.brakeCylinderPressure / 480.0f))

    private fun getBrakeCommandRatio(train: TrainEntity): Float =
        min(1.0f, max(0.0f, -train.notch.toFloat()) / max(1, train.maxBrakeNotch).toFloat())

    private fun getWorldTime(): Int {
        val mc = Minecraft.getInstance()
        return (mc.level?.levelData?.gameTime?.rem(24000L) ?: 0L).toInt()
    }

    private fun getWorldTime(train: TrainEntity): Int =
        (train.level().levelData.gameTime % 24000L).toInt()

    private fun getClockText(): String {
        val t0 = getWorldTime()
        val hour = (t0 / 1000 + 6) % 24
        val minute = ((t0 % 1000) * 0.06f).toInt()
        return "$hour:$minute"
    }
}
