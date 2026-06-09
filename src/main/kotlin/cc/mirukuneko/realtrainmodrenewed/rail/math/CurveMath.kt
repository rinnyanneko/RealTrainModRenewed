@file:JvmName("CurveMath")

package cc.mirukuneko.realtrainmodrenewed.rail.math

import kotlin.math.floor as kotlinFloor

/** Minimal math helpers matching legacy rail code usage. */
fun wrapAngle(angle: Float): Float {
    var wrapped = angle % 360.0f
    if (wrapped >= 180.0f) wrapped -= 360.0f
    if (wrapped < -180.0f) wrapped += 360.0f
    return wrapped
}

fun toRadians(degrees: Float): Double {
    return Math.toRadians(degrees.toDouble())
}

fun floor(v: Double): Int {
    return kotlinFloor(v).toInt()
}
