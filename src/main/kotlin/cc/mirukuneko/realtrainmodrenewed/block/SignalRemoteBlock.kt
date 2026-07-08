// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class SignalRemoteBlock(properties: BlockBehaviour.Properties, val mode: Mode) : BaseEntityBlock(properties) {
    enum class Mode { RECEIVER, CHANGER, VALUE_INPUT }

    companion object {
        private val CODEC_RECEIVER: MapCodec<SignalRemoteBlock> = simpleCodec { props -> SignalRemoteBlock(props, Mode.RECEIVER) }
        private val CODEC_CHANGER: MapCodec<SignalRemoteBlock> = simpleCodec { props -> SignalRemoteBlock(props, Mode.CHANGER) }
        private val CODEC_VALUE_INPUT: MapCodec<SignalRemoteBlock> = simpleCodec { props -> SignalRemoteBlock(props, Mode.VALUE_INPUT) }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = when (mode) {
        Mode.RECEIVER -> CODEC_RECEIVER
        Mode.CHANGER -> CODEC_CHANGER
        Mode.VALUE_INPUT -> CODEC_VALUE_INPUT
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        SignalRemoteBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(state: BlockState, level: net.minecraft.world.level.BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape =
        net.minecraft.world.phys.shapes.Shapes.block()

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        null

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val be = level.getBlockEntity(pos) as? SignalRemoteBlockEntity ?: return InteractionResult.FAIL
        if (player is ServerPlayer) {
            val channel = be.linkedChannel
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Channel: $channel"))
        }
        return InteractionResult.SUCCESS
    }
}
