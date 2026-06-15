package cc.mirukuneko.realtrainmodrenewed.rail.util

import net.minecraft.world.level.Level

class RailMapSwitch(
    start: RailPosition,
    end: RailPosition,
    @JvmField val startDir: RailDir,
    @JvmField val endDir: RailDir
) : RailMapBasic(start, end) {

    var open: Boolean = false
        private set

    fun setState(open: Boolean): RailMapSwitch {
        this.open = open
        return this
    }

    fun isOpen(): Boolean = open

    // isGettingPowered inherited from RailMapBasic, cannot override final member
}
