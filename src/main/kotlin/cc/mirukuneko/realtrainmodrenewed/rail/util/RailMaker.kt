// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.util

import java.lang.reflect.Array as ReflectArray

class RailMaker private constructor(positions: List<RailPosition>, @Suppress("UNUSED_PARAMETER") collected: Unit) {
    private val rpList: MutableList<RailPosition>

    constructor(positions: List<RailPosition>) : this(positions, Unit)

    constructor(positions: Array<RailPosition?>) : this(
        positions.filterNotNull().toList(),
        Unit,
    )

    /**
     * Script compatibility constructor. Nashorn arrays implement Map rather than
     * Java List/Array, while other engines can expose Iterable or reflective arrays.
     */
    constructor(positions: Any?) : this(RailPositionContainers.collect(positions), Unit)

    /** Legacy RailMaker(world, positions, fixRTMRailMapVersion) signature. */
    constructor(
        @Suppress("UNUSED_PARAMETER") world: Any?,
        positions: Any?,
        @Suppress("UNUSED_PARAMETER") fixRTMRailMapVersion: Int,
    ) : this(positions)

    init {
        rpList = positions.toMutableList()
    }

    private fun getSwitchType(): SwitchType? {
        return when (rpList.size) {
            3 -> {
                val count = rpList.count { it.switchType == 1.toByte() }
                if (count == 1) SwitchType.SwitchBasic() else null
            }
            4 -> {
                val count = rpList.count { it.switchType == 1.toByte() }
                when {
                    count == 2 -> SwitchType.SwitchSingleCross()
                    count == 4 -> {
                        for (i in rpList.indices) {
                            for (j in i + 1 until rpList.size) {
                                if ((rpList[i].direction.toInt() and 7) == (rpList[j].direction.toInt() and 7)) {
                                    return SwitchType.SwitchScissorsCross()
                                }
                            }
                        }
                        SwitchType.SwitchDiamondCross()
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    fun getSwitch(): SwitchType? {
        val type = getSwitchType() ?: return null
        val switchList = rpList.filter { it.switchType == 1.toByte() }
        val normalList = rpList.filter { it.switchType != 1.toByte() }
        return if (type.init(switchList, normalList)) type else null
    }
}

internal object RailPositionContainers {
    private const val MAX_DEPTH = 4

    fun collect(source: Any?): List<RailPosition> {
        val result = ArrayList<RailPosition>()
        collectInto(source, result, 0)
        return result
    }

    private fun collectInto(source: Any?, result: MutableList<RailPosition>, depth: Int) {
        if (source == null || depth > MAX_DEPTH) return
        when (source) {
            is RailPosition -> result.add(source)
            is Map<*, *> -> source.values.forEach { collectInto(it, result, depth + 1) }
            is Iterable<*> -> source.forEach { collectInto(it, result, depth + 1) }
            else -> {
                if (!source.javaClass.isArray) return
                for (index in 0 until ReflectArray.getLength(source)) {
                    collectInto(ReflectArray.get(source, index), result, depth + 1)
                }
            }
        }
    }
}
