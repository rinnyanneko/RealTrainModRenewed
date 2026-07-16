// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.signal

import com.mojang.serialization.Codec
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

/**
 * 信号番号と現示をワールド保存するテーブルです。
 */
class SignalNetworkSavedData private constructor() : SavedData() {
    companion object {
        private val CODEC: Codec<SignalNetworkSavedData> = CompoundTag.CODEC.xmap(
            { tag -> load(tag) },
            { data -> data.saveTag() }
        )
        private val TYPE: SavedDataType<SignalNetworkSavedData> = SavedDataType(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "signal_network"),
            { SignalNetworkSavedData() },
            CODEC,
            DataFixTypes.LEVEL
        )

        @JvmStatic
        fun get(level: ServerLevel): SignalNetworkSavedData =
            level.dataStorage.computeIfAbsent(TYPE)

        private fun load(tag: CompoundTag): SignalNetworkSavedData {
            val data = SignalNetworkSavedData()
            data.nextChannel = maxOf(1000, NbtCompat.getInt(tag, "NextChannel"))
            val list = NbtCompat.getList(tag, "Entries")
            for (raw in list) {
                if (raw !is CompoundTag) continue
                val channel = NbtCompat.getInt(raw, "Channel")
                val dimensionId = NbtCompat.getString(raw, "Dimension")
                val aspect = SignalAspect.byId(NbtCompat.getInt(raw, "Aspect"))
                val pos = BlockPos(
                    NbtCompat.getInt(raw, "X"),
                    NbtCompat.getInt(raw, "Y"),
                    NbtCompat.getInt(raw, "Z")
                )
                if (channel > 0 && dimensionId.isNotBlank()) {
                    data.entries[channel] = SignalEntry(dimensionId, pos, aspect)
                }
            }
            return data
        }

        private fun dimensionId(level: ServerLevel): String =
            level.dimension().identifier().toString()
    }

    private data class SignalEntry(
        val dimensionId: String,
        val pos: BlockPos,
        val aspect: SignalAspect
    )

    private val entries: MutableMap<Int, SignalEntry> = HashMap()
    private var nextChannel: Int = 1000

    private fun saveTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("NextChannel", nextChannel)
        val list = ListTag()
        for ((channel, entry) in entries) {
            val entryTag = CompoundTag()
            entryTag.putInt("Channel", channel)
            entryTag.putString("Dimension", entry.dimensionId)
            entryTag.putInt("X", entry.pos.x)
            entryTag.putInt("Y", entry.pos.y)
            entryTag.putInt("Z", entry.pos.z)
            entryTag.putInt("Aspect", entry.aspect.id)
            list.add(entryTag)
        }
        tag.put("Entries", list)
        return tag
    }

    fun assignNewChannel(level: ServerLevel, pos: BlockPos, previousChannel: Int, currentAspect: SignalAspect): Int {
        if (previousChannel > 0) entries.remove(previousChannel)
        val channel = nextChannel++
        entries[channel] = SignalEntry(dimensionId(level), pos.immutable(), currentAspect)
        setDirty()
        applyAspectIfLoaded(level.server, channel)
        return channel
    }

    fun hasChannel(channel: Int): Boolean = entries.containsKey(channel)

    fun getAspect(channel: Int): SignalAspect = entries[channel]?.aspect ?: SignalAspect.STOP

    fun setAspect(server: MinecraftServer, channel: Int, aspect: SignalAspect) {
        val entry = entries[channel] ?: return
        entries[channel] = entry.copy(aspect = aspect)
        setDirty()
        applyAspectIfLoaded(server, channel)
    }

    fun removeSignal(level: ServerLevel, pos: BlockPos, channel: Int) {
        val entry = entries[channel] ?: return
        if (entry.pos == pos && entry.dimensionId == dimensionId(level)) {
            entries.remove(channel)
            setDirty()
        }
    }

    fun syncLoadedSignal(level: ServerLevel, blockEntity: InstalledObjectBlockEntity) {
        val channel = blockEntity.signalChannel
        if (channel <= 0) return
        val entry = entries[channel] ?: return
        if (entry.dimensionId != dimensionId(level) || entry.pos != blockEntity.blockPos) return
        blockEntity.setSignalAspect(entry.aspect, false)
    }

    private fun applyAspectIfLoaded(server: MinecraftServer, channel: Int) {
        val entry = entries[channel] ?: return
        val dimensionLocation = Identifier.tryParse(entry.dimensionId) ?: return
        val levelKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation)
        val targetLevel = server.getLevel(levelKey) ?: return
        if (!targetLevel.hasChunk(SectionPos.blockToSectionCoord(entry.pos.x), SectionPos.blockToSectionCoord(entry.pos.z))) return
        val be = targetLevel.getBlockEntity(entry.pos)
        if (be is InstalledObjectBlockEntity) {
            be.setSignalAspect(entry.aspect, true)
        }
    }
}
