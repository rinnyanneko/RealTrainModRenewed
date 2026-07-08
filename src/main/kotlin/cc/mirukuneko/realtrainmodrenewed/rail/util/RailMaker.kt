// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.util

class RailMaker(positions: List<RailPosition>) {
    private val rpList: MutableList<RailPosition>

    constructor(positions: Array<RailPosition?>) : this(
        positions.filterNotNull().toList()
    )

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
