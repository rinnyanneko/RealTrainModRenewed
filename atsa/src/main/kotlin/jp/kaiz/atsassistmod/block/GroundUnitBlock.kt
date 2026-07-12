// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.block

import com.mojang.serialization.MapCodec
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity
import jp.kaiz.atsassistmod.client.ATSAModClientHooks
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * Single ground-unit block carrying the variant in the [TYPE] blockstate (0-15).
 */
open class GroundUnitBlock @JvmOverloads constructor(
    properties: Properties = Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops(),
) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(stateDefinition.any().setValue(TYPE, 0))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(TYPE)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = GroundUnitBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (level.isClientSide) {
            return null
        }
        return createTickerHelper(type, ATSAModBlockEntities.GROUND_UNIT.get(), GroundUnitBlockEntity::serverTick)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult {
        if (level.isClientSide) {
            ATSAModClientHooks.openGroundUnit(pos)
        }
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        direction: Direction,
    ): Int = (level.getBlockEntity(pos) as? GroundUnitBlockEntity)?.getRedStoneOutput() ?: 0

    companion object {
        @JvmField
        val CODEC: MapCodec<GroundUnitBlock> = simpleCodec { GroundUnitBlock() }

        @JvmField
        val TYPE: IntegerProperty = IntegerProperty.create("gu_type", 0, 15)
    }
}
