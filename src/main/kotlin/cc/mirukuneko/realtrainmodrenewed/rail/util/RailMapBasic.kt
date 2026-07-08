// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.util

import cc.mirukuneko.realtrainmodrenewed.rail.math.BezierCurve
import cc.mirukuneko.realtrainmodrenewed.rail.math.ILine
import cc.mirukuneko.realtrainmodrenewed.rail.math.StraightLine
import net.minecraft.world.level.Level
import kotlin.math.*

/**
 * Port of jp.legacy.legacy.rail.util.RailMapBasic.
 */
open class RailMapBasic(
    override val startRP: RailPosition,
    override val endRP: RailPosition
) : RailMap() {

    @JvmField
    var length: Double = 0.0
    protected var lineHorizontal: ILine? = null
    protected var lineVertical: ILine? = null

    init {
        endRP.cantCenter = startRP.cantCenter
        createLine()
    }

    protected fun createLine() {
        val x0 = startRP.posX
        val y0 = startRP.posY
        val z0 = startRP.posZ
        val x1 = endRP.posX
        val y1 = endRP.posY
        val z1 = endRP.posZ
        val eps = 1.0e-3
        val dz = z1 - z0
        val dx = x1 - x0

        val flag1 = (endRP.direction - startRP.direction) % 4 == 0
        val flag2 = abs(z0 - z1) < eps || abs(x0 - x1) < eps
        val flag3 = abs(abs(z0 - z1) - abs(x0 - x1)) < eps
            && startRP.direction.toInt() % 2 != 0 && endRP.direction.toInt() % 2 != 0

        if (!flag1 || (!flag2 && !flag3)) {
            val lenXZ1 = abs(z1 - z0)
            val ddx = abs(x1 - x0)
            val max = max(lenXZ1, ddx)
            val min = min(lenXZ1, ddx)

            if (startRP.anchorLengthHorizontal <= 0.0F) {
                val b0 = startRP.direction.toInt() % 2 == 0
                val d1 = if (b0) max else min
                startRP.anchorLengthHorizontal = (d1 * 0.5522847771644592).toFloat()
            }
            if (endRP.anchorLengthHorizontal <= 0.0F) {
                val b0 = endRP.direction.toInt() % 2 == 0
                val d1 = if (b0) max else min
                endRP.anchorLengthHorizontal = (d1 * 0.5522847771644592).toFloat()
            }

            val d1s = cos(startRP.anchorYaw.toDouble()) * startRP.anchorLengthHorizontal
            val d2s = sin(startRP.anchorYaw.toDouble()) * startRP.anchorLengthHorizontal
            val d3e = cos(endRP.anchorYaw.toDouble()) * endRP.anchorLengthHorizontal
            val d4e = sin(endRP.anchorYaw.toDouble()) * endRP.anchorLengthHorizontal
            lineHorizontal = BezierCurve(z0, x0, z0 + d1s, x0 + d2s, z1 + d3e, x1 + d4e, z1, x1)
        } else {
            lineHorizontal = StraightLine(z0, x0, z1, x1)
        }

        val lenXZ = sqrt(pow2(x1 - x0) + pow2(z1 - z0))
        if (startRP.anchorLengthVertical == 0.0F && endRP.anchorLengthVertical == 0.0F) {
            lineVertical = StraightLine(0.0, y0, lenXZ, y1)
        } else {
            val d1v = cos(startRP.anchorPitch.toDouble()) * startRP.anchorLengthVertical
            val d2v = sin(startRP.anchorPitch.toDouble()) * startRP.anchorLengthVertical
            val d3v = cos(endRP.anchorPitch.toDouble()) * endRP.anchorLengthVertical
            val d4v = sin(endRP.anchorPitch.toDouble()) * endRP.anchorLengthVertical
            lineVertical = BezierCurve(0.0, y0, d1v, y0 + d2v, lenXZ - d3v, y1 + d4v, lenXZ, y1)
        }
    }

    override fun getLength(): Double {
        if (length <= 0.0) {
            val height = endRP.posY - startRP.posY
            length = if (height == 0.0) {
                lineHorizontal!!.length
            } else {
                val d0 = lineHorizontal!!.length
                sqrt(d0 * d0 + height * height)
            }
        }
        return length
    }

    override val isStraightTrack: Boolean get() = lineHorizontal is StraightLine

    override fun getHorizontalPathLength(): Double =
        lineHorizontal?.length ?: getLength()

    override fun getNearlestPoint(par1: Int, par2: Double, par3: Double): Int =
        lineHorizontal!!.getNearlestPoint(par1, par2, par3)

    override fun getRailPos(par1: Int, par2: Int): DoubleArray =
        lineHorizontal!!.getPoint(par1, par2)

    override fun getRailHeight(par1: Int, par2: Int): Double {
        val railWidth = 3.0f
        var height = lineVertical!!.getPoint(par1, par2)[1]
        val cant = getCant(par1, par2)
        if (cant != 0.0F) {
            val h2 = abs(sin(Math.toRadians(cant.toDouble())) * railWidth * 0.5f)
            height += h2
        }
        return height
    }

    override fun getRailYaw(par1: Int, par2: Int): Float =
        Math.toDegrees(lineHorizontal!!.getSlope(par1, par2)).toFloat()

    override fun getRailPitch(par1: Int, par2: Int): Float =
        Math.toDegrees(lineVertical!!.getSlope(par1, par2)).toFloat()

    override fun getRailRoll(split: Int, t: Int): Float {
        val ft = 2.0f * t.toFloat() / split.toFloat()
        val c1 = if (ft <= 1.0f) (1.0f - ft) * startRP.cantEdge else (ft - 1.0f) * -endRP.cantEdge
        val c2 = if (ft <= 1.0f) ft * startRP.cantCenter else (2.0f - ft) * startRP.cantCenter
        return c1 + c2
    }

    fun hasPoint(x: Int, z: Int): Boolean =
        (startRP.blockX == x && startRP.blockZ == z) || (endRP.blockX == x && endRP.blockZ == z)

    fun isGettingPowered(level: Level): Boolean =
        startRP.checkRSInput(level) && endRP.checkRSInput(level)

    companion object {
        private fun sin(degrees: Float): Float = sin(degrees.toDouble()).toFloat()
        private fun cos(degrees: Float): Float = cos(degrees.toDouble()).toFloat()
        private fun sin(degrees: Double): Double = kotlin.math.sin(Math.toRadians(degrees))
        private fun cos(degrees: Double): Double = kotlin.math.cos(Math.toRadians(degrees))
        private fun pow2(v: Double): Double = v * v
    }
}
