// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.math

import kotlin.math.*

object CurveMath {
    @JvmStatic
    fun floor(value: Float): Int = kotlin.math.floor(value.toDouble()).toInt()

    @JvmStatic
    fun floor(value: Double): Int = kotlin.math.floor(value).toInt()

    @JvmStatic
    fun wrapAngle(angle: Float): Float {
        var a = angle % 360f
        if (a < 0f) a += 360f
        return a
    }

    @JvmStatic
    fun wrapAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0.0) a += 360.0
        return a
    }

    @JvmStatic
    fun approxEqual(a: Float, b: Float, epsilon: Float = 1e-4f): Boolean = abs(a - b) < epsilon

    @JvmStatic
    fun approxEqual(a: Double, b: Double, epsilon: Double = 1e-8): Boolean = abs(a - b) < epsilon
}
