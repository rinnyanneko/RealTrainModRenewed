// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.math

import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import net.minecraft.util.Mth
import kotlin.math.*

/**
 * Port of jp.legacy.legacylib.math.BezierCurve (cubic Bezier on Z/X plane).
 */
class BezierCurve(
    p1: Double, p2: Double, p3: Double, p4: Double,
    p5: Double, p6: Double, p7: Double, p8: Double
) : ILine {
    companion object {
        const val QUANTIZE: Int = 32
        /** レンダラの max と同一にし、max > split による重複サンプル（スパイク）を防ぐ。 */
        const val MAX_CURVE_RENDER_SPLIT: Int = 384

        /**
         * 弧長に応じた分割数。[RailMap.curveSplitForLength] と同じ値を返す。
         */
        @JvmStatic
        fun splitForLength(arcLength: Double): Int {
            if (arcLength < 1.0e-4) return 32
            val raw = (arcLength * 32.0).toInt()
            val s = Mth.clamp(maxOf(raw, 8), 8, 4096)
            return minOf(s, MAX_CURVE_RENDER_SPLIT)
        }
    }

    @JvmField val sp: DoubleArray = doubleArrayOf(p1, p2)
    @JvmField val cpS: DoubleArray = doubleArrayOf(p3, p4)
    @JvmField val cpE: DoubleArray = doubleArrayOf(p5, p6)
    @JvmField val ep: DoubleArray = doubleArrayOf(p7, p8)

    private var normalizedParameters: FloatArray? = null
    override val length: Double
    val split: Int

    init {
        length = calcLength()
        split = splitForLength(length)
    }

    override fun getPoint(par1: Int, par2: Int): DoubleArray =
        getPointFromParameter(getHomogenizedParameter(par1, par2).toDouble())

    private fun getPointFromParameter(par1: Double): DoubleArray {
        val t = par1.coerceIn(0.0, 1.0)
        val tp = 1.0 - t
        val d0 = t * t * t
        val d1 = 3.0 * t * t * tp
        val d2 = 3.0 * t * tp * tp
        val d3 = tp * tp * tp
        val x = d0 * ep[0] + d1 * cpE[0] + d2 * cpS[0] + d3 * sp[0]
        val y = d0 * ep[1] + d1 * cpE[1] + d2 * cpS[1] + d3 * sp[1]
        return doubleArrayOf(x, y)
    }

    override fun getNearlestPoint(par1: Int, par2: Double, par3: Double): Int {
        var i = 0
        var pd = Double.MAX_VALUE
        for (j in 0 until par1) {
            val point = getPoint(par1, j)
            val dx = par2 - point[1]
            val dy = par3 - point[0]
            val distance = dx * dx + dy * dy
            if (distance < pd) {
                pd = distance
                i = j
            }
        }
        return if (pd < Double.MAX_VALUE) i else -1
    }

    override fun getSlope(par1: Int, par2: Int): Double =
        getSlopeFromParameter(getHomogenizedParameter(par1, par2).toDouble())

    private fun getSlopeFromParameter(par1: Double): Double {
        val t = par1.coerceIn(0.0, 1.0)
        val tp = 1.0 - t
        val d0 = t * t
        val d1 = 2.0 * t * tp
        val d2 = tp * tp
        val dx = 3.0 * (d0 * (ep[0] - cpE[0]) + d1 * (cpE[0] - cpS[0]) + d2 * (cpS[0] - sp[0]))
        val dy = 3.0 * (d0 * (ep[1] - cpE[1]) + d1 * (cpE[1] - cpS[1]) + d2 * (cpS[1] - sp[1]))
        return atan2(dy, dx)
    }

    private fun getHomogenizedParameter(n: Int, par2: Int): Float {
        return when {
            n < 4 -> 0.0f
            par2 <= 0 -> 0.0f
            par2 >= n -> 1.0f
            else -> {
                val np = normalizedParameters ?: initNP().also { normalizedParameters = it }
                val i0 = CurveMath.floor(par2.toFloat() * split.toFloat() / n.toFloat())
                    .coerceIn(0, np.size - 1)
                np[i0]
            }
        }
    }

    private fun initNP(): FloatArray {
        if (split < 1) return floatArrayOf(0.0f)
        val np = FloatArray(split)
        val ni = 1.0f / split.toFloat()
        var tt = 0.0f
        var p = sp
        var q: DoubleArray
        val dd = FloatArray(split + 1)
        dd[0] = 0.0f
        for (i in 1..split) {
            tt += ni
            q = getPointFromParameter(tt.toDouble())
            dd[i] = dd[i - 1] + getDistance(p[0], q[0], p[1], q[1]).toFloat()
            p = q
        }
        val total = dd[split]
        if (total < 1.0e-8f) {
            for (i in 0 until split) {
                np[i] = i.toFloat() / split.toFloat()
            }
            return np
        }
        for (i in 1..split) {
            dd[i] /= total
        }
        for (i in 0 until split) {
            val t = i.toFloat() / split.toFloat()
            var k = 0
            while (k < split - 1 && !(dd[k] <= t && t <= dd[k + 1])) k++
            val denom = dd[k + 1] - dd[k]
            val x: Float = if (abs(denom) < 1.0e-8f) {
                k.toFloat() / split.toFloat()
            } else {
                ((t - dd[k]) / denom).let { v ->
                    (k.toFloat() * (1.0f - v) + (1 + k).toFloat() * v) * (1.0f / split.toFloat())
                }
            }
            np[i] = x
        }
        return np
    }

    private fun calcLength(): Double {
        val x0 = sp[0] - ep[0]
        val y0 = sp[1] - ep[1]
        val l0 = sqrt(x0 * x0 + y0 * y0)
        var n = CurveMath.floor(l0 * 2.0)
        if (n < 1) n = 1
        val ni = 1.0f / n.toFloat()
        var tt = 0.0f
        var p = sp
        var q: DoubleArray
        val dd = DoubleArray(n + 1)
        dd[0] = 0.0
        for (i in 1..n) {
            tt += ni
            q = getPointFromParameter(tt.toDouble())
            dd[i] = dd[i - 1] + getDistance(p[0], q[0], p[1], q[1])
            p = q
        }
        return dd[n]
    }

    private fun getDistance(par1: Double, par2: Double, par3: Double, par4: Double): Double {
        val xDis = abs(par1 - par2)
        val yDis = abs(par3 - par4)
        return sqrt(xDis * xDis + yDis * yDis)
    }
}
