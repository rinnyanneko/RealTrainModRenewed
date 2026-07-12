// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.block.entity

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import com.mojang.serialization.Codec
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer
import jp.kaiz.atsassistmod.ifttt.IFTTTUtil
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities
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
 * IFTTT block entity. Holds THIS and THAT rule lists and evaluates them each server tick.
 */
open class IftttBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ATSAModBlockEntities.IFTTT.get(), pos, state) {
    private var redStoneOutput = 0
    private var notFirst = false
    private var anyMatch = false
    private var thisList: MutableList<IFTTTContainer> = ArrayList()
    private var thatList: MutableList<IFTTTContainer> = ArrayList()

    private fun tick() {
        val level = level
        if (level == null || level.isClientSide || thisList.isEmpty() || thatList.isEmpty()) {
            return
        }
        val detect = AABB(
            worldPosition.x - 1.0,
            worldPosition.y.toDouble(),
            worldPosition.z - 1.0,
            worldPosition.x + 2.0,
            worldPosition.y + 4.0,
            worldPosition.z + 2.0,
        )
        val trains = level.getEntitiesOfClass(TrainEntity::class.java, detect)
        val train = trains.firstOrNull()

        val match = if (anyMatch) {
            thisList.any { (it as IFTTTContainer.This).isCondition(this, train) }
        } else {
            thisList.all { (it as IFTTTContainer.This).isCondition(this, train) }
        }

        if (match) {
            val first = !notFirst
            for (container in thatList) {
                (container as IFTTTContainer.That).doThat(this, train, first)
            }
            notFirst = true
        } else if (notFirst) {
            for (container in thatList) {
                (container as IFTTTContainer.That).finish(this, train)
            }
            setRedStoneOutput(0)
            notFirst = false
        }
    }

    fun getRedStoneOutput(): Int = redStoneOutput

    fun setRedStoneOutput(power: Int) {
        if (redStoneOutput != power) {
            redStoneOutput = power
            setChanged()
            level?.updateNeighbourForOutputSignal(worldPosition, blockState.block)
        }
    }

    fun isAnyMatch(): Boolean = anyMatch

    fun setAnyMatch(anyMatch: Boolean) {
        this.anyMatch = anyMatch
    }

    fun getThisList(): List<IFTTTContainer> = thisList

    fun getThatList(): List<IFTTTContainer> = thatList

    fun addIFTTT(container: IFTTTContainer) {
        when (container) {
            is IFTTTContainer.This -> if (thisList.size < 6) thisList.add(container)
            is IFTTTContainer.That -> if (thatList.size < 6) thatList.add(container)
        }
    }

    fun setIFTTT(container: IFTTTContainer, index: Int) {
        when (container) {
            is IFTTTContainer.This -> if (thisList.size > index) thisList[index] = container else addIFTTT(container)
            is IFTTTContainer.That -> if (thatList.size > index) thatList[index] = container else addIFTTT(container)
        }
    }

    fun removeIFTTT(container: IFTTTContainer, index: Int) {
        when (container) {
            is IFTTTContainer.This -> if (index >= 0 && index < thisList.size) thisList.removeAt(index)
            is IFTTTContainer.That -> if (index >= 0 && index < thatList.size) thatList.removeAt(index)
        }
    }

    fun replaceLists(newThis: List<IFTTTContainer>, newThat: List<IFTTTContainer>, anyMatch: Boolean) {
        thisList = ArrayList(newThis)
        thatList = ArrayList(newThat)
        this.anyMatch = anyMatch
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putInt("redStoneOutput", redStoneOutput)
        tag.putBoolean("notFirst", notFirst)
        tag.putBoolean("anyMatch", anyMatch)
        saveList(tag, "iftttThisList", thisList)
        saveList(tag, "iftttThatList", thatList)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        redStoneOutput = tag.getIntOr("redStoneOutput", 0)
        notFirst = tag.getBooleanOr("notFirst", false)
        anyMatch = tag.getBooleanOr("anyMatch", false)
        thisList = loadList(tag, "iftttThisList").toMutableList()
        thatList = loadList(tag, "iftttThatList").toMutableList()
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    companion object {
        @JvmStatic
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, blockEntity: IftttBlockEntity) {
            blockEntity.tick()
        }

        @JvmStatic
        private fun saveList(output: ValueOutput, key: String, list: List<IFTTTContainer>) {
            val tag = output.list(key, Codec.BYTE_BUFFER)
            for (container in list) {
                val bytes = IFTTTUtil.toBytes(container)
                if (bytes != null) {
                    tag.add(ByteBuffer.wrap(bytes))
                }
            }
        }

        @JvmStatic
        private fun loadList(input: ValueInput, key: String): List<IFTTTContainer> {
            val list = ArrayList<IFTTTContainer>()
            for (buffer in input.listOrEmpty(key, Codec.BYTE_BUFFER)) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val container = IFTTTUtil.fromBytes(bytes)
                if (container != null) {
                    list.add(container)
                }
            }
            return list
        }
    }
}
