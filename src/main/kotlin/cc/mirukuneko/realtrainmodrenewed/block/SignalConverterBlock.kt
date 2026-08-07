// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.block

import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalConverterBlockEntity
import cc.mirukuneko.realtrainmodrenewed.electric.ElectricSignalNetwork
import cc.mirukuneko.realtrainmodrenewed.electric.SignalConverterType
import cc.mirukuneko.realtrainmodrenewed.item.WireItem
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult

class SignalConverterBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {
    companion object {
        val CODEC: MapCodec<SignalConverterBlock> = simpleCodec(::SignalConverterBlock)
        val TYPE: IntegerProperty = IntegerProperty.create("type", 0, 4)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(TYPE, SignalConverterType.RS_INPUT.id))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(TYPE) }
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SignalConverterBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        return createTickerHelper(type, RealTrainModRenewedBlockEntities.SIGNAL_CONVERTER.get()) { current, pos, blockState, be ->
            if (current is ServerLevel) SignalConverterBlockEntity.serverTick(current, pos, blockState, be)
        }
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult =
        if (stack.item is WireItem) InteractionResult.PASS else openConfiguration(state, level, pos)

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult =
        openConfiguration(state, level, pos)

    private fun openConfiguration(state: BlockState, level: Level, pos: BlockPos): InteractionResult {
        val type = SignalConverterType.byId(state.getValue(TYPE))
        if (type == SignalConverterType.INCREMENT || type == SignalConverterType.DECREMENT) {
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        if (level.isClientSide) ClientHooks.openSignalConverterScreen(pos)
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    override fun isSignalSource(state: BlockState): Boolean =
        state.getValue(TYPE) == SignalConverterType.RS_OUTPUT.id

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
        (level.getBlockEntity(pos) as? SignalConverterBlockEntity)?.getRsOutput() ?: 0

    override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
        getSignal(state, level, pos, direction)

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean, player: Player): ItemStack =
        ItemStack(itemForType(state))

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> =
        listOf(ItemStack(itemForType(state)))

    private fun itemForType(state: BlockState): Item = when (SignalConverterType.byId(state.getValue(TYPE))) {
        SignalConverterType.RS_INPUT -> RealTrainModRenewedItems.SIGNAL_CONVERTER_ITEM.get()
        SignalConverterType.RS_OUTPUT -> RealTrainModRenewedItems.SIGNAL_CONVERTER_RS_ITEM.get()
        SignalConverterType.INCREMENT -> RealTrainModRenewedItems.SIGNAL_CONVERTER_INCREMENT_ITEM.get()
        SignalConverterType.DECREMENT -> RealTrainModRenewedItems.SIGNAL_CONVERTER_DECREMENT_ITEM.get()
        SignalConverterType.WIRELESS -> RealTrainModRenewedItems.SIGNAL_CONVERTER_WIRELESS_ITEM.get()
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        orientation: net.minecraft.world.level.redstone.Orientation?,
        isMoving: Boolean,
    ) {
        if (!level.isClientSide && state.getValue(TYPE) == SignalConverterType.RS_INPUT.id) {
            (level.getBlockEntity(pos) as? SignalConverterBlockEntity)?.refreshRsInput()
        }
        super.neighborChanged(state, level, pos, block, orientation, isMoving)
    }

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, isMoving: Boolean) {
        ElectricSignalNetwork.removeWiresAtEndpoint(level, pos)
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving)
    }
}
