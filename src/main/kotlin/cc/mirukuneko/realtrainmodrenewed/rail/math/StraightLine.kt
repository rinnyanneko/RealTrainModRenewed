// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
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

    override fun getPoint(split: Int, index: Int): DoubleArray {
        val i0 = if (index < 0) 0 else (if (index > split) split else index)
        val d0 = i0.toDouble() / split.toDouble()
        val x = startX + (endX - startX) * d0
        val y = startY + (endY - startY) * d0
        return doubleArrayOf(x, y)
    }

    override fun getNearlestPoint(split: Int, x: Double, z: Double): Int {
        val t: Double = if (slope.isNaN()) {
            (x - startY) / (endY - startY)
        } else {
            val a21 = 1.0 / (slope * slope + 1.0)
            val x0 = (z + slope * x - slope * intercept) * a21
            (x0 - startX) / (endX - startX)
        }
        return CurveMath.floor(t * split.toDouble())
    }

    override fun getSlope(split: Int, index: Int): Double = slopeAngle
}
