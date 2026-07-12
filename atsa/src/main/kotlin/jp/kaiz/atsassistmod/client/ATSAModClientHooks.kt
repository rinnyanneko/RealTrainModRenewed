// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity
import jp.kaiz.atsassistmod.client.screen.GroundUnitScreen
import jp.kaiz.atsassistmod.client.screen.IftttEditorScreen
import jp.kaiz.atsassistmod.client.screen.TrainProtectionSelectorScreen
import jp.kaiz.atsassistmod.rtm.RtmTrains
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

/**
 * Client-only screen openers. Only referenced behind level.isClientSide guards so
 * the classes never load on a dedicated server.
 */
object ATSAModClientHooks {
    @JvmStatic
    fun openGroundUnit(pos: BlockPos) {
        val minecraft = Minecraft.getInstance()
        val blockEntity = minecraft.level?.getBlockEntity(pos)
        if (blockEntity is GroundUnitBlockEntity) {
            minecraft.setScreen(GroundUnitScreen(blockEntity))
        }
    }

    @JvmStatic
    fun openIftttEditor(pos: BlockPos) {
        val minecraft = Minecraft.getInstance()
        val blockEntity = minecraft.level?.getBlockEntity(pos)
        if (blockEntity is IftttBlockEntity) {
            minecraft.setScreen(IftttEditorScreen(blockEntity))
        }
    }

    @JvmStatic
    fun openTrainProtectionSelector() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val vehicle = player.vehicle
        if (vehicle is TrainEntity && RtmTrains.isControlCar(vehicle)) {
            minecraft.setScreen(TrainProtectionSelectorScreen(vehicle))
        }
    }
}
