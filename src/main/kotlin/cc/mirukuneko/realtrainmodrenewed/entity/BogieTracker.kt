package cc.mirukuneko.realtrainmodrenewed.entity

import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import kotlin.math.abs
import kotlin.math.sqrt

class BogieTracker {
    companion object {
        const val SPLIT_PER_METER = 32
        private const val MAX_ENDPOINT_DIST_SQ = 0.49
        private const val MAX_ENDPOINT_YAW_DIFF = 35.0f
        private const val MAX_WALK_GUARDS = 24
    }

    var core: LargeRailCoreBlockEntity? = null
    var map: RailMap? = null
    var split: Int = 0
    var index: Double = 0.0
    var direction: Int = 1
    var worldX: Double = 0.0
    var worldY: Double = 0.0
    var worldZ: Double = 0.0
    var yaw: Float = 0f
    var pitch: Float = 0f
    var roll: Float = 0f

    fun isValid(): Boolean = map != null && split > 0

    fun advance(meters: Double, level: Level) {
        if (!isValid()) return
        walkInPlace(meters, level)
        sampleState()
    }

    fun positionFrom(source: BogieTracker, offset: Double, level: Level) {
        core = source.core; map = source.map; split = source.split
        index = source.index; direction = source.direction
        if (!isValid()) return
        walkInPlace(offset, level)
        sampleState()
    }

    fun sampleState() {
        if (!isValid()) return
        val map = map!!
        val pos = map.getRailPos(split, index.toInt().coerceIn(0, split))
        worldX = pos[1]; worldZ = pos[0]
        worldY = map.getRailHeight(split, index.toInt().coerceIn(0, split))
        yaw = map.getRailYaw(split, index.toInt().coerceIn(0, split))
        pitch = map.getRailPitch(split, index.toInt().coerceIn(0, split))
        roll = map.getRailRoll(split, index.toInt().coerceIn(0, split))
    }

    private fun walkInPlace(meters: Double, level: Level) {
        val map = map ?: return
        val stepMeters = 1.0 / SPLIT_PER_METER.toDouble()
        var remaining = abs(meters)
        val step = if (meters >= 0) direction * stepMeters else -direction * stepMeters
        var guards = 0
        while (remaining > 0 && guards < MAX_WALK_GUARDS) {
            guards++
            index += step
            remaining -= stepMeters
            if (index < 0 || index > split) {
                val end = if (index < 0) map.startRP else map.endRP
                val next = findAdjacentRailMap(level, end)
                if (next == null) {
                    index = index.coerceIn(0.0, split.toDouble())
                    break
                }
                this.map = next.first; split = next.second; core = next.third
                index = if (index < 0) split.toDouble() else 0.0
            }
        }
    }

    private fun findAdjacentRailMap(level: Level, endpoint: cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition): Triple<RailMap, Int, LargeRailCoreBlockEntity?>? {
        val pos = BlockPos(endpoint.blockX, endpoint.blockY, endpoint.blockZ)
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            val scanPos = pos.offset(dx, dy, dz)
            val be = level.getBlockEntity(scanPos)
            if (be is LargeRailCoreBlockEntity && be.isLoaded) {
                for (candidate in be.allRailMaps) {
                    val s = candidate.startRP; val e = candidate.endRP
                    if (isNearEndpoint(endpoint, s) || isNearEndpoint(endpoint, e)) {
                        val sp = RailMap.curveSplitForLength(candidate.getHorizontalPathLength())
                        return Triple(candidate, maxOf(8, sp * 2), be)
                    }
                }
            }
            if (be is RailCollisionBlockEntity) {
                val corePos = be.corePos ?: continue
                val core = level.getBlockEntity(corePos) as? LargeRailCoreBlockEntity ?: continue
                if (!core.isLoaded) continue
                for (candidate in core.allRailMaps) {
                    val s = candidate.startRP; val e = candidate.endRP
                    if (isNearEndpoint(endpoint, s) || isNearEndpoint(endpoint, e)) {
                        val sp = RailMap.curveSplitForLength(candidate.getHorizontalPathLength())
                        return Triple(candidate, maxOf(8, sp * 2), core)
                    }
                }
            }
        }
        return null
    }

    private fun isNearEndpoint(a: cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition, b: cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition): Boolean {
        val dx = a.posX - b.posX; val dz = a.posZ - b.posZ
        return (dx * dx + dz * dz) < MAX_ENDPOINT_DIST_SQ && abs(a.anchorYaw - b.anchorYaw) < MAX_ENDPOINT_YAW_DIFF
    }
}
