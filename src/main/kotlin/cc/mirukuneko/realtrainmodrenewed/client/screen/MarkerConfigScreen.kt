// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.screen

import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.network.ConfigureMarkerPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

/**
 * マーカー設定GUI (RTM の GuiRailMarker 相当)。
 * カント(傾き)とアンカー(曲線制御)を設定する。設定後にレールアイテムで右クリックすると反映される。
 */
open class MarkerConfigScreen(pos: BlockPos) : Screen(Component.literal("マーカー設定 (カント/曲線)")) {
    private val pos: BlockPos = pos.immutable()
    private lateinit var cantCenterBox: EditBox
    private lateinit var cantEdgeBox: EditBox
    private lateinit var cantRandomBox: EditBox
    private lateinit var anchorYawBox: EditBox
    private lateinit var anchorPitchBox: EditBox
    private lateinit var anchorLenHBox: EditBox
    private lateinit var anchorLenVBox: EditBox

    private var anchorYaw: Float = 0f
    private var anchorPitch: Float = 0f
    private var anchorLenH: Float = -1.0f
    private var anchorLenV: Float = 0f
    private var cantCenter: Float = 0f
    private var cantEdge: Float = 0f
    private var cantRandom: Float = 0f

    override fun init() {
        readState()
        val boxW = 90
        val x = width / 2 - boxW / 2
        val y = height / 2 - 105

        cantCenterBox = labeledBox(x, y, boxW, cantCenter.toString())
        cantEdgeBox = labeledBox(x, y + 34, boxW, cantEdge.toString())
        cantRandomBox = labeledBox(x, y + 68, boxW, cantRandom.toString())
        anchorYawBox = labeledBox(x, y + 102, boxW, anchorYaw.toString())
        anchorPitchBox = labeledBox(x, y + 136, boxW, anchorPitch.toString())
        anchorLenHBox = labeledBox(x, y + 170, boxW, anchorLenH.toString())
        anchorLenVBox = labeledBox(x, y + 204, boxW, anchorLenV.toString())
        setInitialFocus(cantCenterBox)

        addRenderableWidget(Button.builder(Component.literal("保存")) { submit() }
            .bounds(width / 2 - 122, y + 240, 75, 20).build())
        addRenderableWidget(Button.builder(Component.literal("リセット")) { resetValues() }
            .bounds(width / 2 - 38, y + 240, 75, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel")) { onClose() }
            .bounds(width / 2 + 46, y + 240, 75, 20).build())
    }

    private fun labeledBox(x: Int, y: Int, w: Int, value: String): EditBox {
        val box = EditBox(font, x, y + 12, w, 18, Component.empty())
        box.setMaxLength(12)
        box.setValue(value)
        addRenderableWidget(box)
        return box
    }

    private fun readState() {
        val be = Minecraft.getInstance().level?.getBlockEntity(pos)
        if (be is MarkerBlockEntity) {
            anchorYaw = be.anchorYaw
            anchorPitch = be.anchorPitch
            anchorLenH = be.anchorLengthHorizontal
            anchorLenV = be.anchorLengthVertical
            cantCenter = be.cantCenter
            cantEdge = be.cantEdge
            cantRandom = be.cantRandom
        }
    }

    private fun parse(box: EditBox, def: Float): Float {
        return try {
            val s = box.value.trim()
            if (s.isEmpty()) def else s.toFloat()
        } catch (_: NumberFormatException) {
            def
        }
    }

    private fun submit() {
        cantCenter = parse(cantCenterBox, cantCenter)
        cantEdge = parse(cantEdgeBox, cantEdge)
        cantRandom = parse(cantRandomBox, cantRandom)
        anchorYaw = parse(anchorYawBox, anchorYaw)
        anchorPitch = parse(anchorPitchBox, anchorPitch)
        anchorLenH = parse(anchorLenHBox, anchorLenH)
        anchorLenV = parse(anchorLenVBox, anchorLenV)
        ClientNetworkHelper.sendToServer(ConfigureMarkerPayload(pos, anchorYaw, anchorPitch,
            anchorLenH, anchorLenV, cantCenter, cantEdge, cantRandom))
        onClose()
    }

    private fun resetValues() {
        cantCenterBox.setValue("0.0")
        cantEdgeBox.setValue("0.0")
        cantRandomBox.setValue("0.0")
        anchorYawBox.setValue("0.0")
        anchorPitchBox.setValue("0.0")
        anchorLenHBox.setValue("-1.0")
        anchorLenVBox.setValue("0.0")
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, pt: Float) {
        super.extractRenderState(g, mx, my, pt)
        val x = width / 2 - 45
        val y = height / 2 - 105
        g.centeredText(font, title, width / 2, y - 24, 0xFFFFFF)
        g.text(font, Component.literal("カント中心 (度)"), x, y, 0xFFFFFF)
        g.text(font, Component.literal("カント端 (度)"), x, y + 34, 0xFFFFFF)
        g.text(font, Component.literal("カント揺らぎ"), x, y + 68, 0xFFFFFF)
        g.text(font, Component.literal("アンカー方位 (度)"), x, y + 102, 0xFFFFFF)
        g.text(font, Component.literal("アンカー勾配 (度)"), x, y + 136, 0xFFFFFF)
        g.text(font, Component.literal("アンカー水平長 (-1=直線)"), x, y + 170, 0xAAAAAA)
        g.text(font, Component.literal("アンカー垂直長"), x, y + 204, 0xAAAAAA)
    }

    override fun isPauseScreen(): Boolean = false
}
