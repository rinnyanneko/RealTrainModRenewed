// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.block

import com.mojang.serialization.MapCodec
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity
import jp.kaiz.atsassistmod.client.ATSAModClientHooks
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * IFTTT control block. Comparator output is preserved.
 */
open class IftttBlock @JvmOverloads constructor(
    properties: Properties = Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops(),
) : BaseEntityBlock(properties) {
    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = IftttBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (level.isClientSide) {
            return null
        }
        return createTickerHelper(type, ATSAModBlockEntities.IFTTT.get(), IftttBlockEntity::serverTick)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult {
        if (level.isClientSide) {
            ATSAModClientHooks.openIftttEditor(pos)
        }
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        direction: Direction,
    ): Int = (level.getBlockEntity(pos) as? IftttBlockEntity)?.getRedStoneOutput() ?: 0

    companion object {
        @JvmField
        val CODEC: MapCodec<IftttBlock> = simpleCodec { IftttBlock() }
    }
}
