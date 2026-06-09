package cc.mirukuneko.realtrainmodrenewed.rail.util

import net.minecraft.world.level.Level

class Point {
    @JvmField
    val rpRoot: RailPosition

    @JvmField
    val rmMain: RailMapSwitch

    @JvmField
    val rmBranch: RailMapSwitch?

    @JvmField
    val branchDir: RailDir

    @JvmField
    val mainDirIsPositive: Boolean

    @JvmField
    val branchDirIsPositive: Boolean

    private var moveCount = 0

    constructor(railPos: RailPosition, rms1: RailMapSwitch, rms2: RailMapSwitch) {
        rpRoot = railPos
        val mainFirst = rms1.length <= rms2.length
        rmMain = if (mainFirst) rms1 else rms2
        rmBranch = if (mainFirst) rms2 else rms1
        branchDir = getDir(rpRoot, rmMain, rmBranch)
        mainDirIsPositive = rmMain.startRP === rpRoot
        branchDirIsPositive = rmBranch.startRP === rpRoot
    }

    constructor(railPos: RailPosition, rms1: RailMapSwitch) {
        rpRoot = railPos
        rmMain = rms1
        rmBranch = null
        branchDir = RailDir.NONE
        mainDirIsPositive = rms1.startRP === railPos
        branchDirIsPositive = false
    }

    fun onUpdate(level: Level?) {
        if (level == null) {
            return
        }
        if (rpRoot.checkRSInput(level)) {
            if (moveCount < MAX_COUNT) {
                ++moveCount
            }
        } else if (moveCount > 0) {
            --moveCount
        }
    }

    fun getMovement(): Float = moveCount.toFloat() / MAX_COUNT.toFloat()

    fun getActiveRailMap(level: Level): RailMap {
        return if (branchDir == RailDir.NONE || rmBranch == null) {
            rmMain
        } else if (rpRoot.checkRSInput(level)) {
            rmBranch
        } else {
            rmMain
        }
    }

    companion object {
        private const val MAX_COUNT = 80

        private fun getDir(root: RailPosition, rms1: RailMapSwitch, rms2: RailMapSwitch): RailDir {
            val rp1 = if (rms1.startRP === root) rms1.endRP else rms1.startRP
            val rp2 = if (rms2.startRP === root) rms2.endRP else rms2.startRP
            return root.getDir(rp1, rp2)
        }
    }
}
