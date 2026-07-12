// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.block.entity

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import com.mojang.serialization.Codec
import jp.kaiz.atsassistmod.block.GroundUnitBlock
import jp.kaiz.atsassistmod.block.GroundUnitType
import jp.kaiz.atsassistmod.controller.SpeedOrder
import jp.kaiz.atsassistmod.controller.TrainControllerManager
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities
import jp.kaiz.atsassistmod.rtm.RtmTrains
import jp.kaiz.atsassistmod.util.TrainStateType
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import java.nio.ByteBuffer

/**
 * Consolidated ground-unit block entity. Behaviour switches on [GroundUnitBlock.TYPE].
 */
open class GroundUnitBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ATSAModBlockEntities.GROUND_UNIT.get(), pos, state) {
    private var formationKey = 0L
    private var linkRedStone = false
    private var redStoneOutput = 0
    private var speedLimit = 0
    private var distance = 0.0
    private var autoBrake = false
    private var useTrainDistance = false
    private var version: Byte = 1
    private var states = defaultStates()
    private var tpType = TrainProtectionType.NONE.id

    fun guType(): GroundUnitType = GroundUnitType.getType(blockState.getValue(GroundUnitBlock.TYPE))

    private fun tick(level: Level, pos: BlockPos, state: BlockState) {
        when (val type = guType()) {
            GroundUnitType.ATC_SpeedLimit_Cancel,
            GroundUnitType.ATC_SpeedLimit_Reset,
            -> tickCancel(level, pos, type)
            GroundUnitType.TASC_StopPotion -> tickStopPosition(level, pos)
            GroundUnitType.TrainState_Set -> tickTrainStateSet(level, pos)
            GroundUnitType.CHANGE_TP -> tickChangeTP(level, pos)
            GroundUnitType.ATACS_Disable -> convertToChangeTP(level, pos)
            GroundUnitType.None -> Unit
            else -> tickDefaultDetect(level, pos, type)
        }
    }

    private fun tickDefaultDetect(level: Level, pos: BlockPos, type: GroundUnitType) {
        if (
            (type == GroundUnitType.TASC_StopPotion_Notice || type == GroundUnitType.TASC_StopPotion_Correction) &&
            version.toInt() == 0
        ) {
            distance -= 2.0
            version = 1
            setChanged()
            sync()
        }
        if (linkRedStone && !level.hasNeighborSignal(pos)) {
            formationKey = 0
            return
        }
        val train = firstTrain(level, pos, 3)
        if (train != null && RtmTrains.isControlCar(train)) {
            val key = RtmTrains.formationKey(train)
            if (formationKey != key) {
                applyOnPass(train, type)
                formationKey = key
            }
            return
        }
        formationKey = 0
    }

    private fun applyOnPass(train: TrainEntity, type: GroundUnitType) {
        val trainDistance = RtmTrains.trainDistance(train)
        when (type) {
            GroundUnitType.ATC_SpeedLimit_Notice -> {
                val targetDistance = if (useTrainDistance) distance - trainDistance else distance
                TrainControllerManager.getTrainController(train)
                    .addSpeedOrder(SpeedOrder(speedLimit, targetDistance, autoBrake))
            }
            GroundUnitType.TASC_StopPotion_Notice ->
                TrainControllerManager.getTrainController(train).tascController
                    .enable(if (useTrainDistance) distance + 1.5 - trainDistance else distance + 1.5)
            GroundUnitType.TASC_StopPotion_Correction ->
                TrainControllerManager.getTrainController(train).tascController
                    .setStopDistance(if (useTrainDistance) distance + 1.5 - trainDistance else distance + 1.5)
            GroundUnitType.TASC_Cancel -> TrainControllerManager.getTrainController(train).tascController.disable()
            GroundUnitType.ATO_Departure_Signal -> TrainControllerManager.getTrainController(train).enableATO(speedLimit)
            GroundUnitType.ATO_Cancel -> TrainControllerManager.getTrainController(train).disableATO()
            GroundUnitType.ATO_Change_Speed -> TrainControllerManager.getTrainController(train).setMaxSpeed(speedLimit)
            else -> Unit
        }
    }

    private fun tickCancel(level: Level, pos: BlockPos, type: GroundUnitType) {
        if (linkRedStone && !level.hasNeighborSignal(pos)) {
            formationKey = 0
            return
        }
        val train = firstTrain(level, pos, 4)
        if (train != null) {
            val trigger = if (useTrainDistance) {
                RtmTrains.formationSize(train) == 1 ||
                    (!RtmTrains.isControlCar(train) &&
                        (RtmTrains.connected(train, 0) == null || RtmTrains.connected(train, 1) == null))
            } else {
                RtmTrains.isControlCar(train)
            }
            if (trigger) {
                val key = RtmTrains.formationKey(train)
                if (formationKey != key) {
                    if (type == GroundUnitType.ATC_SpeedLimit_Cancel) {
                        TrainControllerManager.getTrainController(train).removeSpeedLimit()
                    } else {
                        TrainControllerManager.getTrainController(train).removeAllSpeedLimit()
                    }
                    formationKey = key
                }
                return
            }
        }
        formationKey = 0
    }

    private fun tickStopPosition(level: Level, pos: BlockPos) {
        val train = firstTrain(level, pos, 3)
        if (train != null && (linkRedStone || RtmTrains.isControlCar(train))) {
            setRedStoneOutput(if (RtmTrains.speed(train) == 0f) RtmTrains.formationSize(train) else 0)
            return
        }
        setRedStoneOutput(0)
    }

    private fun tickTrainStateSet(level: Level, pos: BlockPos) {
        if (!level.hasNeighborSignal(pos)) {
            return
        }
        val box = AABB(
            pos.x - 1.0,
            pos.y.toDouble(),
            pos.z - 1.0,
            pos.x + 2.0,
            pos.y + 3.0,
            pos.z + 2.0,
        )
        val train = first(level.getEntitiesOfClass(TrainEntity::class.java, box)) ?: return
        if (linkRedStone || RtmTrains.isControlCar(train)) {
            for (i in 0 until 12) {
                if (i == 3) {
                    continue
                }
                if (states[i] < TrainStateType.byId(i).min) {
                    continue
                }
                TrainStateType.apply(train, i, states[i])
            }
        }
    }

    private fun tickChangeTP(level: Level, pos: BlockPos) {
        if (version.toInt() == 0) {
            tpType = TrainProtectionType.ATACS.id
            version = 1
            setChanged()
            return
        }
        tickDefaultDetectTP(level, pos)
    }

    private fun tickDefaultDetectTP(level: Level, pos: BlockPos) {
        if (linkRedStone && !level.hasNeighborSignal(pos)) {
            formationKey = 0
            return
        }
        val train = firstTrain(level, pos, 3)
        if (train != null && RtmTrains.isControlCar(train)) {
            val key = RtmTrains.formationKey(train)
            if (formationKey != key) {
                TrainControllerManager.getTrainController(train).setTrainProtection(TrainProtectionType.getType(tpType))
                formationKey = key
            }
            return
        }
        formationKey = 0
    }

    private fun convertToChangeTP(level: Level, pos: BlockPos) {
        val changed = blockState.setValue(GroundUnitBlock.TYPE, GroundUnitType.CHANGE_TP.id)
        level.setBlock(pos, changed, 3)
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity is GroundUnitBlockEntity) {
            blockEntity.tpType = TrainProtectionType.NONE.id
            blockEntity.setChanged()
            blockEntity.sync()
        }
    }

    private fun firstTrain(level: Level, pos: BlockPos, height: Int): TrainEntity? {
        val box = AABB(
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            pos.x + 1.0,
            pos.y + height.toDouble(),
            pos.z + 1.0,
        )
        return first(level.getEntitiesOfClass(TrainEntity::class.java, box))
    }

    fun getRedStoneOutput(): Int = redStoneOutput

    fun setRedStoneOutput(power: Int) {
        if (redStoneOutput != power) {
            redStoneOutput = power
            setChanged()
            level?.updateNeighbourForOutputSignal(blockPos, blockState.block)
        }
    }

    fun isLinkRedStone(): Boolean = linkRedStone

    fun setLinkRedStone(linkRedStone: Boolean) {
        this.linkRedStone = linkRedStone
    }

    fun getSpeedLimit(): Int = speedLimit

    fun setSpeedLimit(value: Int) {
        speedLimit = value
    }

    fun getDistance(): Double = distance

    fun setDistance(value: Double) {
        distance = value
    }

    fun isAutoBrake(): Boolean = autoBrake

    fun setAutoBrake(value: Boolean) {
        autoBrake = value
    }

    fun isUseTrainDistance(): Boolean = useTrainDistance

    fun setUseTrainDistance(value: Boolean) {
        useTrainDistance = value
    }

    fun getStates(): ByteArray = states

    fun setStates(value: ByteArray) {
        states = value
    }

    fun getTPType(): TrainProtectionType = TrainProtectionType.getType(tpType)

    fun setTPType(type: TrainProtectionType) {
        tpType = type.id
    }

    private fun sync() {
        val level = level
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(blockPos, blockState, blockState, 3)
        }
    }

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putLong("formationID", formationKey)
        tag.putBoolean("linkRedStone", linkRedStone)
        tag.putInt("redStoneOutput", redStoneOutput)
        tag.putInt("speedLimit", speedLimit)
        tag.putDouble("distance", distance)
        tag.putBoolean("autoBrake", autoBrake)
        tag.putBoolean("trainDistance", useTrainDistance)
        tag.putByte("version", version)
        tag.store("state", Codec.BYTE_BUFFER, ByteBuffer.wrap(states))
        tag.putInt("tpType", tpType)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        formationKey = tag.getLongOr("formationID", 0L)
        linkRedStone = tag.getBooleanOr("linkRedStone", false)
        redStoneOutput = tag.getIntOr("redStoneOutput", 0)
        speedLimit = tag.getIntOr("speedLimit", 0)
        distance = tag.getDoubleOr("distance", 0.0)
        autoBrake = tag.getBooleanOr("autoBrake", false)
        useTrainDistance = tag.getBooleanOr("trainDistance", false)
        version = tag.getByteOr("version", 1.toByte())
        tag.read("state", Codec.BYTE_BUFFER).ifPresent { buffer ->
            val loadedStates = ByteArray(buffer.remaining())
            buffer.get(loadedStates)
            if (loadedStates.size == 12) {
                states = loadedStates
            }
        }
        tpType = tag.getIntOr("tpType", TrainProtectionType.NONE.id)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    companion object {
        private fun defaultStates(): ByteArray = byteArrayOf(-1, -9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)

        @JvmStatic
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, blockEntity: GroundUnitBlockEntity) {
            blockEntity.tick(level, pos, state)
        }

        @JvmStatic
        private fun first(list: List<TrainEntity>): TrainEntity? = list.firstOrNull()
    }
}
