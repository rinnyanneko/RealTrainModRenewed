// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.blockentity.TrainDetectorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class TrainDetectorBlock : BaseEntityBlock {
    companion object { val POWERED: BooleanProperty = BlockStateProperties.POWERED }

    private val codec: MapCodec<TrainDetectorBlock> = simpleCodec { TrainDetectorBlock() }

    constructor() : this(Properties.of().sound(SoundType.METAL).strength(1.2f, 6.0f))
    constructor(properties: BlockBehaviour.Properties) : super(properties) {
        registerDefaultState(stateDefinition.any().setValue(POWERED, false))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = codec
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(POWERED) }
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape =
        box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = TrainDetectorBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        return createTickerHelper(type, RealTrainModRenewedBlockEntities.TRAIN_DETECTOR.get()) { _, tickerPos, tickerState, be ->
            if (level is ServerLevel) TrainDetectorBlockEntity.serverTick(level, tickerPos, tickerState, be)
        }
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) ClientHooks.openTrainDetectorScreen(pos)
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) ClientHooks.openTrainDetectorScreen(pos)
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true
    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos, direction: Direction): Int =
        if (state.getValue(POWERED)) 15 else 0
    override fun isSignalSource(state: BlockState): Boolean = true
    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
        if (state.getValue(POWERED)) 15 else 0
}
