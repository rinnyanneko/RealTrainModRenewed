// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.rail.util.Point
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMapBasic
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMaker
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMapSwitch
import cc.mirukuneko.realtrainmodrenewed.rail.util.SwitchType
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.util.Mth
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class LargeRailCoreBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.LARGE_RAIL_CORE.get(), pos, state) {

    companion object {
        @JvmStatic
        fun tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: LargeRailCoreBlockEntity) {
            blockEntity.switchType?.onUpdate(level)
            if (blockEntity.switchStateDirty) {
                blockEntity.refreshSwitchState()
            }
            if (blockEntity.switchProgress < 1.0f) {
                blockEntity.switchProgress = minOf(1.0f, blockEntity.switchProgress + 0.04f)
                if (level.isClientSide) {
                    blockEntity.requestModelDataUpdate()
                } else {
                    blockEntity.setChanged()
                }
            }
        }
    }

    private enum class SwitchLayout { NONE, BASIC, SINGLE_CROSS, SCISSORS, DIAMOND }

    @JvmField
    var railPositions: Array<RailPosition?>? = null
    private var railMap: RailMap? = null
    var railDefinitionId: String = ""; private set
    var activeSegmentIndex: Int = 0; private set
    var previousSegmentIndex: Int = 0; private set
    var switchProgress: Float = 1.0f; private set
    var lastSignalStrength: Int = -1; private set
    var switchStateDirty: Boolean = true
    private var cachedAllRailMaps: Array<RailMap> = emptyArray()
    private var railMapCacheDirty: Boolean = true
    var switchType: SwitchType? = null; private set
    private var cachedRenderBounds: AABB? = null
    private var renderBoundsDirty: Boolean = true

    val isLoaded: Boolean get() = railPositions != null && railPositions!!.size >= 2
    val allRailMaps: Array<RailMap> get() {
        if (!railMapCacheDirty) return cachedAllRailMaps.clone()
        cachedAllRailMaps = buildRailMaps(); railMapCacheDirty = false; return cachedAllRailMaps.clone()
    }
    val activeRailMaps: Array<RailMap> get() {
        if (switchStateDirty && level != null) refreshSwitchState()
        val maps = allRailMaps; if (maps.size <= 1) return maps
        if (switchType != null) return switchType!!.getOpenRailMaps().toTypedArray()
        val layout = detectSwitchLayout()
        return when (layout) {
            SwitchLayout.DIAMOND -> arrayOf(maps[0], maps[1])
            SwitchLayout.SINGLE_CROSS -> if (lastSignalStrength > 0 && maps.size >= 3) arrayOf(maps[2]) else arrayOf(maps[0], maps[1])
            SwitchLayout.SCISSORS -> {
                if (lastSignalStrength > 0 && isScissorsStraightSegment(activeSegmentIndex)) arrayOf(maps[activeSegmentIndex])
                else maps.filter { !isScissorsStraightSegment(maps.indexOf(it)) }.toTypedArray().ifEmpty { maps }
            }
            else -> arrayOf(maps[Mth.clamp(activeSegmentIndex, 0, maps.size - 1)])
        }
    }
    val firstRailPosition: RailPosition? get() = railPositions?.firstOrNull()?.let { RailPosition.readFromNBT(it.writeToNBT()) }
    val switchPoints: Array<Point>? get() = switchType?.points

    fun setRailPositions(positions: Array<RailPosition?>?) {
        if (positions == null || positions.size < 2) { clearRailData(); return }
        if (isSwitchMarkerLayout(positions)) {
            val copied = positions.filterNotNull().map { RailPosition.readFromNBT(it.writeToNBT())!! }
            railPositions = if (copied.size >= 2) copied.toTypedArray() else null
        } else {
            val sanitized = mutableListOf<RailPosition>()
            var i = 0
            while (i + 1 < positions.size) {
                val s = positions[i]; val e = positions[i + 1]
                if (s != null && e != null) { sanitized.add(RailPosition.readFromNBT(s.writeToNBT())!!); sanitized.add(RailPosition.readFromNBT(e.writeToNBT())!!) }
                i += 2
            }
            railPositions = if (sanitized.size >= 2) sanitized.toTypedArray() else null
        }
        invalidateCache(); clampActiveSegment()
    }

    fun getRailPositions(): Array<RailPosition?>? = railPositions

    /** Legacy RTM rail scripts treat both base and core tiles through this accessor. */
    fun getRailCore(): LargeRailCoreBlockEntity = this

    /** Legacy name used by SuperRailBuilder when inspecting switch points. */
    fun getSwitch(): SwitchType? = switchType

    /**
     * Legacy scripts pass a nullable direction argument even for ordinary rails.
     * Switch-aware callers use [allRailMaps], so this accessor stays read-only and
     * returns the first map without refreshing redstone state during rendering.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getRailMap(direction: Any?): RailMap? = railMap ?: allRailMaps.firstOrNull()

    fun getCachedRenderBounds(): AABB {
        if (renderBoundsDirty || cachedRenderBounds == null) {
            cachedRenderBounds = computeRenderBounds()
            renderBoundsDirty = false
        }
        return cachedRenderBounds!!
    }
    fun getSwitchProgress(partialTick: Float): Float = switchProgress

    fun setRailMaps(maps: Array<RailMap>?) {
        if (maps == null || maps.isEmpty()) {
            cachedAllRailMaps = emptyArray(); railMapCacheDirty = false
            cachedRenderBounds = null; renderBoundsDirty = true
            return
        }
        cachedAllRailMaps = maps.clone(); railMapCacheDirty = false
        railMap = maps[0]
        cachedRenderBounds = null; renderBoundsDirty = true
    }

    fun setRailDefinitionId(id: String?) { railDefinitionId = id ?: ""; setChanged() }
    fun setSwitchType(type: SwitchType?) { switchType = type; switchStateDirty = true; cachedRenderBounds = null; renderBoundsDirty = true }

    private fun clearRailData() {
        railPositions = null; railMap = null; switchStateDirty = true; cachedAllRailMaps = emptyArray()
        railMapCacheDirty = true; switchType = null; cachedRenderBounds = null; renderBoundsDirty = true; clampActiveSegment()
    }

    fun createRailMap() {
        railMap = null; cachedAllRailMaps = emptyArray(); railMapCacheDirty = true; switchType = null
        cachedRenderBounds = null; renderBoundsDirty = true
        if (railPositions != null && railPositions!!.size >= 2) {
            switchType = buildSwitchType()
            val maps = buildRailMaps()
            if (maps.isNotEmpty()) railMap = maps[0]
        }
        switchStateDirty = true; clampActiveSegment()
    }

    fun appendRailSegment(start: RailPosition?, end: RailPosition?) {
        if (start == null || end == null) return
        val oldLen = railPositions?.size ?: 0
        val next = arrayOfNulls<RailPosition>(oldLen + 2)
        railPositions?.let { System.arraycopy(it, 0, next, 0, it.size) }
        next[oldLen] = RailPosition.readFromNBT(start.writeToNBT())
        next[oldLen + 1] = RailPosition.readFromNBT(end.writeToNBT())
        setRailPositions(next); createRailMap(); setChanged()
    }

    fun requestSwitchStateRefresh() { switchStateDirty = true }

    fun refreshSwitchState() {
        if (level == null) return; switchStateDirty = false
        val maps = allRailMaps; if (maps.isEmpty()) return
        if (switchType != null) {
            switchType!!.onBlockChanged(level)
            val openIndices = switchType!!.getOpenRailIndices()
            val nextIdx = Mth.clamp(if (openIndices.isEmpty()) 0 else openIndices[0], 0, maps.size - 1)
            val strongest = readSignalAround(worldPosition)
            if (strongest != lastSignalStrength || nextIdx != activeSegmentIndex) {
                previousSegmentIndex = activeSegmentIndex; activeSegmentIndex = nextIdx
                switchProgress = if (nextIdx != previousSegmentIndex) 0f else switchProgress
                lastSignalStrength = strongest; setChanged(); level!!.sendBlockUpdated(worldPosition, blockState, blockState, 3)
            }
            return
        }
        val layout = detectSwitchLayout(); val segmentSignals = IntArray(maps.size) { readSegmentSignal(maps[it]) }
        val strongest = segmentSignals.maxOrNull() ?: 0
        val nextIdx = when (layout) {
            SwitchLayout.DIAMOND -> 0
            SwitchLayout.SINGLE_CROSS -> if (segmentSignals.size >= 3 && segmentSignals[2] > 0) 2 else 0
            SwitchLayout.SCISSORS -> if (strongest > 0) findPoweredScissorsStraightSegment(segmentSignals) else firstScissorsDiagonalSegment()
            SwitchLayout.BASIC -> if (strongest > 0) 1 else 0
            else -> if (strongest <= 0) 0 else Mth.clamp(strongest, 0, maps.size - 1)
        }
        if (strongest != lastSignalStrength || nextIdx != activeSegmentIndex) {
            previousSegmentIndex = activeSegmentIndex; activeSegmentIndex = Mth.clamp(nextIdx, 0, maxOf(0, maps.size - 1))
            switchProgress = if (nextIdx != previousSegmentIndex) 0f else switchProgress
            lastSignalStrength = strongest; setChanged(); level!!.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        }
    }

    fun updateSignalStrength(signalStrength: Int) {
        if (switchType != null) { lastSignalStrength = signalStrength; requestSwitchStateRefresh(); return }
        val segCount = getSegmentCount(); var nextIdx = activeSegmentIndex
        val layout = detectSwitchLayout()
        nextIdx = when (layout) {
            SwitchLayout.DIAMOND -> 0
            SwitchLayout.SINGLE_CROSS -> if (signalStrength > 0 && segCount >= 3) 2 else 0
            SwitchLayout.BASIC -> if (segCount <= 1 || signalStrength <= 0) 0 else 1
            SwitchLayout.SCISSORS -> if (signalStrength > 0) firstScissorsStraightSegment() else 0
            else -> if (segCount <= 1 || signalStrength <= 0) 0 else Mth.clamp(signalStrength, 0, segCount - 1)
        }
        lastSignalStrength = signalStrength
        if (nextIdx != activeSegmentIndex) { previousSegmentIndex = activeSegmentIndex; activeSegmentIndex = nextIdx; switchProgress = 0f; setChanged(); level?.sendBlockUpdated(worldPosition, blockState, blockState, 3) }
    }

    fun cycleSwitch(): Boolean {
        val maps = allRailMaps; if (maps.size < 2) return false
        previousSegmentIndex = activeSegmentIndex; activeSegmentIndex = (activeSegmentIndex + 1) % maps.size
        switchProgress = 0f; setChanged(); level?.sendBlockUpdated(worldPosition, blockState, blockState, 3); return true
    }

    fun getSegmentCount(): Int = maxOf(1, (railPositions?.size ?: 0) / 2)
    fun isPassiveCrossing(): Boolean = switchType?.id == 3.toByte() || detectSwitchLayout() == SwitchLayout.DIAMOND

    // --- Save/Load ---
    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        val rp = railPositions
        if (rp != null && isSwitchMarkerLayout(rp)) {
            tag.putByte("Size", rp.size.toByte())
            for (i in rp.indices) rp[i]?.let { tag.store("RP$i", CompoundTag.CODEC, it.writeToNBT()) }
        } else if (rp != null && rp.size >= 2) {
            val list = tag.childrenList("RailSegments")
            var i = 0
            while (i + 1 < rp.size) { val s = rp[i]; val e = rp[i + 1]; if (s != null && e != null) { val seg = list.addChild(); seg.store("StartRP", CompoundTag.CODEC, s.writeToNBT()); seg.store("EndRP", CompoundTag.CODEC, e.writeToNBT()) }; i += 2 }
            if (list.isEmpty) tag.discard("RailSegments")
        }
        tag.putString("RailDefinitionId", railDefinitionId)
        tag.putInt("ActiveSegmentIndex", activeSegmentIndex); tag.putInt("PreviousSegmentIndex", previousSegmentIndex)
        tag.putFloat("SwitchProgress", switchProgress); tag.putInt("LastSignalStrength", lastSignalStrength)
        tag.putBoolean("SwitchStateDirty", switchStateDirty)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        railPositions = null; railMap = null; cachedAllRailMaps = emptyArray(); railMapCacheDirty = true
        switchType = null; cachedRenderBounds = null; renderBoundsDirty = true
        val size = tag.getByteOr("Size", 0).toInt() and 0xFF
        if (size > 0) {
            val valid = (0 until size).mapNotNull { i -> tag.read("RP$i", CompoundTag.CODEC).map { RailPosition.readFromNBT(it) }.orElse(null) }
            if (valid.size >= 2) { railPositions = valid.toTypedArray(); createRailMap() }
        } else if (!tag.listOrEmpty("RailSegments", CompoundTag.CODEC).isEmpty) {
            val valid = mutableListOf<RailPosition>()
            for (seg in tag.listOrEmpty("RailSegments", CompoundTag.CODEC)) {
                val s = seg.getCompound("StartRP").map { RailPosition.readFromNBT(it) }.orElse(null)
                val e = seg.getCompound("EndRP").map { RailPosition.readFromNBT(it) }.orElse(null)
                if (s != null && e != null) { valid.add(s); valid.add(e) }
            }
            if (valid.size >= 2) { railPositions = valid.toTypedArray(); createRailMap() }
        } else {
            tag.read("StartRP", CompoundTag.CODEC).map { RailPosition.readFromNBT(it) }.ifPresent { s ->
                tag.read("EndRP", CompoundTag.CODEC).map { RailPosition.readFromNBT(it) }.ifPresent { e ->
                    railPositions = arrayOf(s, e); createRailMap()
                }
            }
        }
        railDefinitionId = tag.getStringOr("RailDefinitionId", "")
        activeSegmentIndex = tag.getIntOr("ActiveSegmentIndex", 0); previousSegmentIndex = tag.getIntOr("PreviousSegmentIndex", 0)
        switchProgress = tag.getFloatOr("SwitchProgress", 1f); lastSignalStrength = tag.getIntOr("LastSignalStrength", -1)
        switchStateDirty = true; clampActiveSegment()
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)

    // --- Helpers (condensed) ---
    private fun invalidateCache() { railMap = null; cachedAllRailMaps = emptyArray(); railMapCacheDirty = true; switchType = null; cachedRenderBounds = null; renderBoundsDirty = true; switchStateDirty = true }
    private fun clampActiveSegment() { val s = getSegmentCount(); if (activeSegmentIndex >= s) activeSegmentIndex = maxOf(0, s - 1) }
    private fun isSwitchMarkerLayout(rp: Array<RailPosition?>?): Boolean = rp?.any { it?.switchType == 1.toByte() } == true
    private fun buildSwitchType(): SwitchType? {
        val rp = railPositions ?: return null
        val maker = RailMaker(rp.filterNotNull())
        return maker.getSwitch()
    }
    private fun buildRailMaps(): Array<RailMap> {
        val sw = switchType
        if (sw != null) return sw.allRailMap.mapNotNull { it as? RailMap }.toTypedArray()
        val rp = railPositions ?: return emptyArray()
        val maps = mutableListOf<RailMap>()
        var i = 0
        while (i + 1 < rp.size) { val s = rp[i]; val e = rp[i + 1]; if (s != null && e != null) maps.add(RailMapBasic(s, e)); i += 2 }
        return maps.toTypedArray()
    }
    private fun computeRenderBounds(): AABB {
        val maps = allRailMaps
        val positions = railPositions?.filterNotNull()
        if (positions.isNullOrEmpty() && maps.isEmpty()) return AABB(worldPosition).inflate(4.0)

        var minX = worldPosition.x.toDouble()
        var minY = worldPosition.y.toDouble()
        var minZ = worldPosition.z.toDouble()
        var maxX = worldPosition.x.toDouble() + 1.0
        var maxY = worldPosition.y.toDouble() + 1.0
        var maxZ = worldPosition.z.toDouble() + 1.0
        for (rp in positions.orEmpty()) {
            minX = minOf(minX, rp.posX, rp.blockX.toDouble())
            minY = minOf(minY, rp.posY, rp.blockY.toDouble())
            minZ = minOf(minZ, rp.posZ, rp.blockZ.toDouble())
            maxX = maxOf(maxX, rp.posX, rp.blockX.toDouble() + 1.0)
            maxY = maxOf(maxY, rp.posY, rp.blockY.toDouble() + 1.0)
            maxZ = maxOf(maxZ, rp.posZ, rp.blockZ.toDouble() + 1.0)
        }
        for (map in maps) {
            val split = RailMap.curveSplitForLength(map.getHorizontalPathLength()).coerceIn(2, 512)
            for (i in 0..split) {
                val point = map.getRailPos(split, i)
                val y = map.getRailHeight(split, i)
                minX = minOf(minX, point[1])
                minY = minOf(minY, y)
                minZ = minOf(minZ, point[0])
                maxX = maxOf(maxX, point[1])
                maxY = maxOf(maxY, y)
                maxZ = maxOf(maxZ, point[0])
            }
        }
        return AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(6.0, 4.0, 6.0)
    }
    private fun detectSwitchLayout(): SwitchLayout {
        val rp = railPositions ?: return SwitchLayout.NONE
        val switchCount = rp.count { it?.switchType == 1.toByte() }
        return when {
            switchCount == 0 && rp.size == 4 -> SwitchLayout.DIAMOND
            switchCount == 2 && rp.size == 4 -> SwitchLayout.SINGLE_CROSS
            switchCount == 4 && rp.size == 4 -> SwitchLayout.SCISSORS
            switchCount == 1 && rp.size == 3 -> SwitchLayout.BASIC
            rp.size >= 6 -> SwitchLayout.NONE
            else -> SwitchLayout.NONE
        }
    }
    private fun readSignalAround(pos: BlockPos): Int {
        var max = 0
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) { val s = level?.getSignal(pos.offset(dx, dy, dz), net.minecraft.core.Direction.UP) ?: 0; if (s > max) max = s }
        return max
    }
    private fun readSegmentSignal(map: RailMap): Int {
        val s = map.startRP; val e = map.endRP
        return maxOf(level?.getSignal(BlockPos(s.blockX, s.blockY, s.blockZ), net.minecraft.core.Direction.UP) ?: 0,
            level?.getSignal(BlockPos(e.blockX, e.blockY, e.blockZ), net.minecraft.core.Direction.UP) ?: 0)
    }
    private fun isScissorsStraightSegment(idx: Int): Boolean = idx % 2 == 0
    private fun firstScissorsStraightSegment(): Int = 0
    private fun firstScissorsDiagonalSegment(): Int = 1
    private fun findPoweredScissorsStraightSegment(signals: IntArray): Int {
        for (i in signals.indices) if (isScissorsStraightSegment(i) && signals[i] > 0) return i
        return firstScissorsDiagonalSegment()
    }
}
