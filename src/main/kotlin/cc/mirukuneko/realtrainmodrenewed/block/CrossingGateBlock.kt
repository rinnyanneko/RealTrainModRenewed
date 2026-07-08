// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class CrossingGateBlock : Block {
    companion object {
        val CODEC: MapCodec<CrossingGateBlock> = simpleCodec { CrossingGateBlock() }
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val POWERED: BooleanProperty = BlockStateProperties.POWERED

        private val BASE_SHAPE = box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0)
        private val ARM_UP_NORTH = box(8.0, 10.0, 8.0, 10.0, 26.0, 10.0)
        private val ARM_UP_EAST = box(8.0, 10.0, 8.0, 10.0, 26.0, 10.0)
        private val ARM_DOWN_NORTH = box(7.0, 10.0, 0.0, 9.0, 12.0, 16.0)
        private val ARM_DOWN_EAST = box(0.0, 10.0, 7.0, 16.0, 12.0, 9.0)
    }

    constructor() : this(Properties.of().sound(SoundType.METAL).strength(1.0f, 6.0f).noOcclusion())
    constructor(properties: BlockBehaviour.Properties) : super(properties) {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false))
    }

    override fun codec(): MapCodec<out Block> = CODEC
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(FACING, POWERED) }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection)
            .setValue(POWERED, context.level.hasNeighborSignal(context.clickedPos))

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        if (!level.isClientSide) updatePoweredState(level, pos, state)
        super.onPlace(state, level, pos, oldState, isMoving)
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: net.minecraft.world.level.redstone.Orientation?, isMoving: Boolean) {
        if (!level.isClientSide) updatePoweredState(level, pos, state)
        super.neighborChanged(state, level, pos, block, orientation, isMoving)
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        updatePoweredState(level, pos, state)
    }

    private fun updatePoweredState(level: Level, pos: BlockPos, state: BlockState) {
        val powered = level.hasNeighborSignal(pos)
        if (powered != state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = getGateShape(state)
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = getGateShape(state)

    private fun getGateShape(state: BlockState): VoxelShape {
        val facing = state.getValue(FACING)
        val powered = state.getValue(POWERED)
        val northSouth = facing == Direction.NORTH || facing == Direction.SOUTH
        val arm = if (powered)
            (if (northSouth) ARM_DOWN_NORTH else ARM_DOWN_EAST)
        else
            (if (northSouth) ARM_UP_NORTH else ARM_UP_EAST)
        return Shapes.or(BASE_SHAPE, arm)
    }
}
