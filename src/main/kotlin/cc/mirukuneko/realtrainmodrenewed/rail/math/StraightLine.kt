package cc.mirukuneko.realtrainmodrenewed.rail.math

import kotlin.math.*

/**
 * Port of jp.legacy.legacylib.math.StraightLine (XZ plane: first coord = Z, second = X).
 */
class StraightLine(
    @JvmField val startX: Double,
    @JvmField val startY: Double,
    @JvmField val endX: Double,
    @JvmField val endY: Double
) : ILine {
    private val slope: Double
    private val intercept: Double
    override val length: Double
    private val slopeAngle: Double

    init {
        val dx = endX - startX
        val dy = endY - startY
        if (dx == 0.0) {
            slope = Double.NaN
            intercept = startX
        } else {
            slope = dy / dx
            intercept = startY - slope * startX
        }
        length = sqrt(dx * dx + dy * dy)
        slopeAngle = atan2(dy, dx)
    }

    override fun getPoint(par1: Int, par2: Int): DoubleArray {
        val i0 = if (par2 < 0) 0 else (if (par2 > par1) par1 else par2)
        val d0 = i0.toDouble() / par1.toDouble()
        val x = startX + (endX - startX) * d0
        val y = startY + (endY - startY) * d0
        return doubleArrayOf(x, y)
    }

    override fun getNearlestPoint(par1: Int, y: Double, x: Double): Int {
        val t: Double = if (slope.isNaN()) {
            (y - startY) / (endY - startY)
        } else {
            val a21 = 1.0 / (slope * slope + 1.0)
            val x0 = (x + slope * y - slope * intercept) * a21
            (x0 - startX) / (endX - startX)
        }
        return CurveMath.floor(t * par1.toDouble())
    }

    override fun getSlope(par1: Int, par2: Int): Double = slopeAngle
}
