// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedEntities
// ClientHooks import not available
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import java.util.Locale

class TrainVehicleItem(properties: Properties) : Item(properties) {
    constructor() : this(Properties().stacksTo(1))

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val level = context.level
        val stack = context.itemInHand

        if (level.isClientSide) {
            // ClientHooks screen call removed
            return InteractionResult.SUCCESS
        }

        val selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
        val def = VehicleRegistry.getById(selectedId)
        if (def == null) return InteractionResult.PASS

        val spawnPos = findSpawnPosition(level, player)
        if (spawnPos == null) {
            player.sendSystemMessage(Component.translatable("message.realtrainmodrenewed.train.must_be_on_rail"))
            return InteractionResult.FAIL
        }

        val entity = TrainEntity(RealTrainModRenewedEntities.TRAIN.get(), level)
        entity.setPos(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5)
        entity.yRot = player.yRot
        entity.vehicleId = selectedId
        level.addFreshEntity(entity)

        if (!player.abilities.instabuild) stack.shrink(1)

        return InteractionResult.SUCCESS
    }

    private fun findSpawnPosition(level: Level, player: Player): BlockPos? {
        val start = player.getEyePosition(1.0f)
        val end = start.add(player.getViewVector(1.0f).scale(5.0))
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        return if (hit.type == HitResult.Type.BLOCK) {
            hit.blockPos.above()
        } else {
            player.blockPosition()
        }
    }
}
