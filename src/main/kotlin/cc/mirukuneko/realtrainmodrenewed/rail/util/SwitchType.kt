// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.util

import net.minecraft.world.level.Level
import kotlin.math.abs

abstract class SwitchType(@JvmField val id: Byte) {
    @JvmField
    var allRailMap: Array<RailMapSwitch> = emptyArray()
    @JvmField
    var points: Array<Point> = emptyArray()

    abstract fun init(switchList: List<RailPosition>, normalList: List<RailPosition>): Boolean

    open fun onBlockChanged(level: Level?) {}

    open fun onUpdate(level: Level?) {
        for (point in points) {
            point?.onUpdate(level)
        }
    }

    fun getAllRailMap(): Array<RailMapSwitch> = allRailMap

    fun getPoints(): Array<Point> = points

    fun getOpenRailMaps(): List<RailMap> {
        if (id == 3.toByte()) return allRailMap.toList()
        val open = allRailMap.filter { it.isOpen() }
        return open.ifEmpty { allRailMap.toList() }
    }

    fun firstOpenRailIndex(): Int {
        if (id == 3.toByte()) return 0
        for (i in allRailMap.indices) {
            if (allRailMap[i].isOpen()) return i
        }
        return 0
    }

    fun getOpenRailIndices(): IntArray {
        if (id == 3.toByte()) return IntArray(allRailMap.size) { it }
        val indices = allRailMap.indices.filter { allRailMap[it].isOpen() }
        return if (indices.isEmpty()) intArrayOf(0) else indices.toIntArray()
    }

    class SwitchBasic : SwitchType(0) {
        override fun init(switchList: List<RailPosition>, normalList: List<RailPosition>): Boolean {
            if (switchList.size != 1 || normalList.size != 2) return false
            val root = switchList[0]
            val branch1 = normalList[0]
            val branch2 = normalList[1]
            val dir = root.getDir(branch1, branch2)
            allRailMap = arrayOf(
                RailMapSwitch(root, branch1, dir, RailDir.NONE),
                RailMapSwitch(root, branch2, dir.invert(), RailDir.NONE)
            )
            points = arrayOf(
                Point(root, allRailMap[0], allRailMap[1]),
                Point(branch1, allRailMap[0]),
                Point(branch2, allRailMap[1])
            )
            return true
        }

        override fun onBlockChanged(level: Level?) {
            if (level == null || allRailMap.size < 2) return
            if (allRailMap[0].startRP.checkRSInput(level)) {
                allRailMap[0].setState(false)
                allRailMap[1].setState(true)
            } else {
                allRailMap[0].setState(true)
                allRailMap[1].setState(false)
            }
        }
    }

    class SwitchSingleCross : SwitchType(1) {
        override fun init(switchList: List<RailPosition>, normalList: List<RailPosition>): Boolean {
            if (switchList.size != 2 || normalList.size != 2) return false
            val root1 = switchList[0]
            val root2 = switchList[1]
            val branch0 = findFirstDifferentDirection(root1, normalList, null) ?: return false
            val branch1 = findFirstDifferentDirection(root2, normalList, branch0) ?: return false

            val b0 = root1.getDir(root2, branch0)
            val b1 = root2.getDir(root1, branch1)
            val rails = arrayOf(
                RailMapSwitch(root1, branch0, b0.invert(), RailDir.NONE),
                RailMapSwitch(root2, branch1, b1.invert(), RailDir.NONE),
                RailMapSwitch(root1, root2, b0, b1)
            )
            allRailMap = rails
            points = arrayOf(
                Point(root1, rails[0], rails[2]),
                Point(root2, rails[1], rails[2]),
                Point(if (rails[0].startRP == root1) rails[0].endRP else rails[0].startRP, rails[0]),
                Point(if (rails[1].startRP == root2) rails[1].endRP else rails[1].startRP, rails[1])
            )
            return true
        }

        override fun onBlockChanged(level: Level?) {
            if (level == null || allRailMap.size < 3) return
            if (allRailMap[2].isGettingPowered(level)) {
                allRailMap[0].setState(false)
                allRailMap[1].setState(false)
                allRailMap[2].setState(true)
            } else {
                allRailMap[0].setState(true)
                allRailMap[1].setState(true)
                allRailMap[2].setState(false)
            }
        }
    }

    class SwitchScissorsCross : SwitchType(2) {
        override fun init(switchList: List<RailPosition>, normalList: List<RailPosition>): Boolean {
            if (switchList.size != 4) return false
            val rails = arrayOfNulls<RailMapSwitch>(4)
            val pairs = arrayOfNulls<Array<RailPosition>>(4)
            var pairCount = 0
            for (i in 0 until 4) {
                for (j in i + 1 until 4) {
                    var dirDiff = abs((switchList[i].direction.toInt() and 7) - (switchList[j].direction.toInt() and 7))
                    if (dirDiff > 4) dirDiff = 8 - dirDiff
                    if (dirDiff > 2 && pairCount < 4) {
                        pairs[pairCount++] = arrayOf(switchList[i], switchList[j])
                    }
                }
            }
            if (pairCount != 4) return false

            allRailMap = Array(4) { i ->
                var dir0 = RailDir.NONE
                var dir1 = RailDir.NONE
                for (j in 0 until 4) {
                    if (i == j) continue
                    val pi = pairs[i]!!
                    val pj = pairs[j]!!
                    when {
                        pi[0] == pj[0] -> dir0 = pi[0].getDir(pi[1], pj[1])
                        pi[0] == pj[1] -> dir0 = pi[0].getDir(pi[1], pj[0])
                        pi[1] == pj[0] -> dir1 = pi[1].getDir(pi[0], pj[1])
                        pi[1] == pj[1] -> dir1 = pi[1].getDir(pi[0], pj[0])
                    }
                }
                RailMapSwitch(pairs[i]!![0], pairs[i]!![1], dir0, dir1)
            }

            points = Array(4) { i ->
                val rp = switchList[i]
                var rms1: RailMapSwitch? = null
                var rms2: RailMapSwitch? = null
                for (railMap in allRailMap) {
                    if (railMap.startRP == rp || railMap.endRP == rp) {
                        if (rms1 == null) rms1 = railMap
                        else { rms2 = railMap; break }
                    }
                }
                Point(rp, rms1!!, rms2!!)
            }
            return true
        }

        override fun onBlockChanged(level: Level?) {
            if (level == null) return
            var openRms: RailMapSwitch? = null
            for (phase in 0..1) {
                for (rms in allRailMap) {
                    if (rms.startDir == rms.endDir) {
                        when (phase) {
                            0 -> if (rms.isGettingPowered(level)) { openRms = rms; break }
                            1 -> rms.setState(rms == openRms)
                        }
                    } else if (phase == 1) {
                        rms.setState(openRms == null)
                    }
                }
            }
        }
    }

    class SwitchDiamondCross : SwitchType(3) {
        override fun init(switchList: List<RailPosition>, normalList: List<RailPosition>): Boolean {
            val all = mutableListOf<RailPosition>()
            all.addAll(switchList)
            all.addAll(normalList)
            if (all.size != 4) return false
            val rails = arrayOfNulls<RailMapSwitch>(2)
            var k = 0
            for (i in 0 until 4) {
                for (j in i + 1 until 4) {
                    if (abs((all[i].direction.toInt() and 7) - (all[j].direction.toInt() and 7)) == 4) {
                        rails[k++] = RailMapSwitch(all[i], all[j], RailDir.NONE, RailDir.NONE).setState(true)
                        if (k >= 2) {
                            allRailMap = rails.requireNoNulls()
                            points = arrayOf(
                                Point(allRailMap[0].startRP, allRailMap[0]),
                                Point(allRailMap[0].endRP, allRailMap[0]),
                                Point(allRailMap[1].startRP, allRailMap[1]),
                                Point(allRailMap[1].endRP, allRailMap[1])
                            )
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

    companion object {
        private fun findFirstDifferentDirection(
            switchMarker: RailPosition,
            normalMarkers: List<RailPosition>,
            used: RailPosition?
        ): RailPosition? {
            var fallback: RailPosition? = null
            for (normal in normalMarkers) {
                if (used != null && sameEndpoint(used, normal)) continue
                if ((switchMarker.direction.toInt() and 7) != (normal.direction.toInt() and 7)) return normal
                if (fallback == null) fallback = normal
            }
            return fallback
        }

        private fun sameEndpoint(a: RailPosition?, b: RailPosition?): Boolean =
            a != null && b != null && a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ
    }
}
