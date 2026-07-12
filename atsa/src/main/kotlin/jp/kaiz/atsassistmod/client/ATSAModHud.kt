// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.client.hud.TrainHudClientManager
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType
import jp.kaiz.atsassistmod.rtm.RtmTrains
import net.minecraft.client.CameraType
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.gui.GuiLayer

/**
 * Driver HUD overlay (port of TrainGuiRender).
 */
object ATSAModHud : GuiLayer {
    override fun render(g: GuiGraphicsExtractor, delta: DeltaTracker) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.options.cameraType != CameraType.FIRST_PERSON) {
            return
        }
        val vehicle = player.vehicle
        if (vehicle !is TrainEntity || !RtmTrains.isControlCar(vehicle)) {
            return
        }
        val controller = TrainHudClientManager.get(vehicle)
        if (controller == null || controller.isNotShowHud()) {
            return
        }

        val atoSpeed = if (controller.isATO()) controller.getATOSpeed().toString() else "off"
        val tascSpeed = if (controller.isTASC()) controller.getTASCDistance().toString() else "off"
        val atc = controller.getATCSpeed()
        val limit = if (atc == Int.MAX_VALUE) "---" else atc.toString()
        val tp = controller.getTrainProtectionSpeed()
        val tpSpeed = if (tp == Int.MAX_VALUE) "---" else tp.toString()
        val tpType = controller.getTrainProtectionType()

        val height = g.guiHeight()
        val manualColor = if (controller.isManualDrive()) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt()
        var fix = 50
        if (tpType != TrainProtectionType.NONE) {
            g.text(
                minecraft.font,
                Component.translatable(tpType.translationKey).string + " : " + tpSpeed,
                2,
                height - (fix + 10).also { fix = it },
                0xFFFFFFFF.toInt(),
            )
        }
        g.text(minecraft.font, "Limit : $limit", 2, height - (fix + 10).also { fix = it }, 0xFFFFFFFF.toInt())
        g.text(minecraft.font, "TASC : $tascSpeed", 2, height - (fix + 10).also { fix = it }, manualColor)
        g.text(minecraft.font, "ATO : $atoSpeed", 2, height - (fix + 10).also { fix = it }, manualColor)
    }
}
