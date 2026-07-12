// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.screen

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.client.ClientNetworkHelper
import jp.kaiz.atsassistmod.client.hud.TrainHudClient
import jp.kaiz.atsassistmod.client.hud.TrainHudClientManager
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.ManualDrive
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.TrainDriveMode
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.TrainProtectionSetter
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * On-ride driver SW panel.
 */
open class TrainProtectionSelectorScreen(private val train: TrainEntity) :
    Screen(Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.5")) {
    private val validTPList = ArrayList<TrainProtectionType>()
    private var tcc: TrainHudClient? = TrainHudClientManager.get(train)

    init {
        val tpList = try {
            train.resourceState.dataMap.getString("ATSAssist_TP")
        } catch (_: Throwable) {
            ""
        }
        if (tpList.isNullOrEmpty()) {
            validTPList.add(TrainProtectionType.ATACS)
            validTPList.add(TrainProtectionType.ATSPs)
            validTPList.add(TrainProtectionType.RATS)
            validTPList.add(TrainProtectionType.RnATS)
        } else {
            if (tpList.contains("ATACS")) validTPList.add(TrainProtectionType.ATACS)
            if (tpList.contains("ATS-Ps")) validTPList.add(TrainProtectionType.ATSPs)
            if (tpList.contains("R-ATS")) validTPList.add(TrainProtectionType.RATS)
            if (tpList.contains("Rn-ATS")) validTPList.add(TrainProtectionType.RnATS)
        }
    }

    private fun tcc(): TrainHudClient {
        if (tcc == null) {
            tcc = TrainHudClientManager.getOrCreate(train)
        }
        return tcc!!
    }

    override fun init() {
        val heightBase = height / 2 - 55
        val widthBaseL = width / 2 - 80
        val widthBaseR0 = width / 2 + 40
        val widthBaseR1 = width / 2 + 130

        addRenderableWidget(
            Checkbox.builder(Component.empty(), font)
                .pos(widthBaseL + 3, heightBase + 28)
                .selected(tcc != null && tcc!!.isManualDrive())
                .onValueChange { _, value -> ClientNetworkHelper.sendToServer(ManualDrive(value)) }
                .build(),
        )
        addRenderableWidget(
            Checkbox.builder(Component.empty(), font)
                .pos(widthBaseL + 3, heightBase + 103)
                .selected(tcc != null && tcc!!.isNotShowHud())
                .onValueChange { _, value -> tcc().setNotShowHud(value) }
                .build(),
        )

        addRenderableWidget(Button.builder(Component.literal("Manual")) { sendDriveMode(10) }
            .bounds(widthBaseL, heightBase + 50, 70, 20).build())
        addRenderableWidget(Button.builder(Component.literal("TASC")) { sendDriveMode(11) }
            .bounds(widthBaseL, heightBase + 72, 70, 20).build())
        addRenderableWidget(Button.builder(Component.literal("TASC/ATO")) { sendDriveMode(12) }
            .bounds(widthBaseL, heightBase + 94, 70, 20).build())

        addRenderableWidget(
            Button.builder(Component.translatable(TrainProtectionType.NONE.translationKey)) {
                sendTP(TrainProtectionType.NONE)
            }.bounds(widthBaseR0, heightBase, 60, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable(TrainProtectionType.STATION_PREMISES.translationKey)) {
                sendTP(TrainProtectionType.STATION_PREMISES)
            }.bounds(widthBaseR0, heightBase + 25, 60, 20).build(),
        )

        var y = heightBase
        for (type in validTPList) {
            addRenderableWidget(
                Button.builder(Component.translatable(type.translationKey)) {
                    sendTP(type)
                }.bounds(widthBaseR1, y, 60, 20).build(),
            )
            y += 25
        }
    }

    private fun sendDriveMode(mode: Int) {
        when (mode) {
            10 -> {
                tcc().setTASC(false)
                tcc().setATO(false)
            }
            11 -> tcc().setATO(false)
            else -> Unit
        }
        ClientNetworkHelper.sendToServer(TrainDriveMode(mode - 10))
    }

    private fun sendTP(type: TrainProtectionType) {
        tcc().setTrainProtectionType(type)
        ClientNetworkHelper.sendToServer(TrainProtectionSetter(type.id))
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        extractBackground(g, mouseX, mouseY, partial)
        super.extractRenderState(g, mouseX, mouseY, partial)

        val heightBase = height / 2 - 50
        val widthBaseL = width / 2 - 135
        val widthBaseR0 = width / 2 - 10

        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.0"), widthBaseL + 20, heightBase - 25, 0xFFFFFF)
        val modeIndex = if (tcc != null) {
            if (tcc!!.isATO()) 3 else if (tcc!!.isTASC()) 2 else 1
        } else {
            1
        }
        val mode = Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.$modeIndex")
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.7"), widthBaseL, heightBase, 0xFFFFFF)
        g.text(font, mode, widthBaseL + 55, heightBase, 0xFFFFFF)
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.6"), widthBaseL, heightBase + 25, 0xFFFFFF)
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.4"), widthBaseL, heightBase + 100, 0xFFFFFF)
        g.text(font, Component.translatable("atsassistmod.gui.TrainProtectionSelector.text.5"), widthBaseR0 + 50, heightBase - 25, 0xFFFFFF)
    }

    override fun isPauseScreen(): Boolean = false
}
