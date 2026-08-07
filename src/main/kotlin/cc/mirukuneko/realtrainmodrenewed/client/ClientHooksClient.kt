// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.screen.MarkerConfigScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.ScriptBlockScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalChangerScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalReceiverScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalValueScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.SpeakerScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.TrainDetectorScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalConverterScreen
import cc.mirukuneko.realtrainmodrenewed.client.sound.CrossingGateSoundManager
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object ClientHooksClient {
    @JvmStatic
    fun openRailSelectScreen(player: Player, stack: ItemStack) {
        ClientItemHelper.openRailSelectScreen(player, stack)
    }

    @JvmStatic
    fun openTrainSelectScreen(player: Player, stack: ItemStack, category: TrainItem.Category) {
        ClientItemHelper.openTrainSelectScreen(player, stack, category)
    }

    @JvmStatic
    fun openTrainSelectScreen(player: Player, stack: ItemStack) {
        ClientItemHelper.openTrainSelectScreen(player, stack)
    }

    @JvmStatic
    fun openVehicleFormationScreen(stack: ItemStack) {
        ClientItemHelper.openVehicleFormationScreen(stack)
    }

    @JvmStatic
    fun openCarSelectScreen(player: Player, stack: ItemStack) {
        ClientItemHelper.openCarSelectScreen(player, stack)
    }

    @JvmStatic
    fun openInstalledObjectSelectScreen(player: Player, stack: ItemStack, category: InstalledObjectCategory) {
        ClientItemHelper.openInstalledObjectSelectScreen(player, stack, category)
    }

    @JvmStatic
    fun openSignalChangerScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(SignalChangerScreen(pos))
    }

    @JvmStatic
    fun openSignalReceiverScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(SignalReceiverScreen(pos))
    }

    @JvmStatic
    fun openSignalValueScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(SignalValueScreen(pos))
    }

    @JvmStatic
    fun openTrainDetectorScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(TrainDetectorScreen(pos))
    }

    @JvmStatic
    fun openSignalConverterScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(SignalConverterScreen(pos))
    }

    @JvmStatic
    fun openMarkerConfigScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(MarkerConfigScreen(pos))
    }

    @JvmStatic
    fun openSpeakerScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(SpeakerScreen(pos))
    }

    @JvmStatic
    fun openScriptBlockScreen(pos: BlockPos) {
        Minecraft.getInstance().setScreen(ScriptBlockScreen(pos))
    }

    @JvmStatic
    fun stopCrossingGateSound(level: Level, pos: BlockPos) {
        CrossingGateSoundManager.stop(level, pos)
    }

    @JvmStatic
    fun tickCrossingGateSound(blockEntity: InstalledObjectBlockEntity) {
        CrossingGateSoundManager.tick(blockEntity)
    }

    @JvmStatic
    fun showScriptErrorMessage(message: String?) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (player == null || message.isNullOrBlank()) {
            return
        }
        player.sendSystemMessage(Component.translatable("message.realtrainmodrenewed.script.error", message))
    }
}
