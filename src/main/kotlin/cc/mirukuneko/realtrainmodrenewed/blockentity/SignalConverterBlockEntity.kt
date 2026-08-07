// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.block.SignalConverterBlock
import cc.mirukuneko.realtrainmodrenewed.electric.ElectricSignalNetwork
import cc.mirukuneko.realtrainmodrenewed.electric.ElectricSignalNode
import cc.mirukuneko.realtrainmodrenewed.electric.SignalComparator
import cc.mirukuneko.realtrainmodrenewed.electric.SignalConverterLogic
import cc.mirukuneko.realtrainmodrenewed.electric.SignalConverterType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class SignalConverterBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.SIGNAL_CONVERTER.get(), pos, state), ElectricSignalNode {

    var comparator: SignalComparator = SignalComparator.EQUAL
        private set
    var signalOnTrue: Int = 15
        private set
    var signalOnFalse: Int = 0
        private set
    private var signal: Int = 0
    private var lastInputSignal: Int = 0
    private var previousRsInput: Int = Int.MIN_VALUE

    val converterType: SignalConverterType
        get() = SignalConverterType.byId(blockState.getValue(SignalConverterBlock.TYPE))

    companion object {
        @JvmStatic
        fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState, be: SignalConverterBlockEntity) {
            if (be.converterType == SignalConverterType.RS_INPUT) be.refreshRsInput()
        }
    }

    fun refreshRsInput() {
        if (level == null || level!!.isClientSide || converterType != SignalConverterType.RS_INPUT) return
        val output = getElectricity()
        if (output == previousRsInput) return
        previousRsInput = output
        ElectricSignalNetwork.propagate(level, worldPosition, output)
    }

    override fun getElectricity(): Int = when (converterType) {
        SignalConverterType.RS_INPUT -> if (isPoweredBySide()) signalOnTrue else signalOnFalse
        SignalConverterType.RS_OUTPUT -> 0
        else -> signal
    }

    private fun isPoweredBySide(): Boolean {
        val currentLevel = level ?: return false
        return Direction.entries.any { side ->
            currentLevel.getSignal(worldPosition.relative(side), side) > 0
        }
    }

    override fun receiveElectricity(level: Int): Int {
        val previousSignal = signal
        val previousInput = lastInputSignal
        lastInputSignal = level
        when (converterType) {
            SignalConverterType.RS_INPUT -> Unit
            SignalConverterType.RS_OUTPUT -> {
                val next = if (comparator.test(level, signalOnTrue)) 1 else 0
                if (next != signal) {
                    signal = next
                    this.level?.updateNeighborsAt(worldPosition, blockState.block)
                }
            }
            SignalConverterType.INCREMENT, SignalConverterType.DECREMENT ->
                signal = SignalConverterLogic.transform(converterType, level)
            SignalConverterType.WIRELESS -> {
                signal = level
                ElectricSignalNetwork.broadcastWireless(this.level, signalOnTrue, worldPosition, level)
            }
        }
        if (signal != previousSignal || lastInputSignal != previousInput) setChanged()
        return SignalConverterLogic.transform(converterType, level)
    }

    fun receiveWireless(level: Int) {
        signal = level
        lastInputSignal = level
        setChanged()
    }

    fun clearWiredSignal() {
        lastInputSignal = 0
        val hadRsOutput = converterType == SignalConverterType.RS_OUTPUT && signal != 0
        signal = 0
        setChanged()
        if (hadRsOutput) level?.updateNeighborsAt(worldPosition, blockState.block)
    }

    fun getRsOutput(): Int =
        if (converterType == SignalConverterType.RS_OUTPUT && signal == 1) 15 else 0

    fun setSignalProperties(onTrue: Int, onFalse: Int, comparator: SignalComparator) {
        val currentLevel = level
        if (converterType == SignalConverterType.WIRELESS) {
            ElectricSignalNetwork.unregisterWireless(currentLevel, worldPosition)
        }
        signalOnTrue = onTrue
        signalOnFalse = onFalse
        this.comparator = comparator
        previousRsInput = Int.MIN_VALUE
        if (converterType == SignalConverterType.WIRELESS) {
            ElectricSignalNetwork.registerWireless(currentLevel, signalOnTrue, worldPosition)
        } else if (converterType == SignalConverterType.RS_OUTPUT) {
            receiveElectricity(lastInputSignal)
        } else if (converterType == SignalConverterType.RS_INPUT) {
            refreshRsInput()
        }
        setChanged()
        if (currentLevel != null && !currentLevel.isClientSide) {
            currentLevel.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        }
    }

    override fun onLoad() {
        super.onLoad()
        if (converterType == SignalConverterType.WIRELESS) {
            ElectricSignalNetwork.registerWireless(level, signalOnTrue, worldPosition)
        }
        if (converterType == SignalConverterType.RS_INPUT) previousRsInput = Int.MIN_VALUE
    }

    override fun setRemoved() {
        ElectricSignalNetwork.unregisterWireless(level, worldPosition)
        super.setRemoved()
    }

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putInt("comparatorIndex", comparator.id)
        tag.putInt("signal_0", signalOnTrue)
        tag.putInt("signal_1", signalOnFalse)
        tag.putInt("RuntimeSignal", signal)
        tag.putInt("LastInputSignal", lastInputSignal)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        comparator = SignalComparator.byId(
            tag.getInt("comparatorIndex").orElse(tag.getIntOr("comparator", 0))
        )
        signalOnTrue = tag.getInt("signal_0").orElse(
            tag.getInt("signalOnTrue").orElse(15)
        )
        signalOnFalse = tag.getInt("signal_1").orElse(tag.getIntOr("signalOnFalse", 0))
        signal = tag.getIntOr("RuntimeSignal", 0)
        lastInputSignal = tag.getIntOr("LastInputSignal", signal)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}
