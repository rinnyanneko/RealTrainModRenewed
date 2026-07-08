// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller

open class SpeedOrder @JvmOverloads constructor(
    private val targetSpeedH: Int,
    private var targetDistance: Double,
    private val autoBrake: Boolean = false,
) {
    private var enable = false
    private var breaking = false

    fun getTargetSpeed(): Int = targetSpeedH

    fun moveDistance(movedDistance: Double) {
        if (!enable) {
            if (targetDistance <= 0) {
                targetDistance = 0.0
                enable = true
            } else {
                targetDistance -= movedDistance
            }
        }
    }

    fun isEnable(): Boolean = enable

    fun isAutoBrake(): Boolean = autoBrake

    fun getNeedNotch(nowSpeedH: Float): Int {
        val deceleration = getReqDeceleration(nowSpeedH)
        return when {
            deceleration > 4 -> {
                breaking = true
                -8
            }
            deceleration > 1.4 -> {
                breaking = true
                -8
            }
            deceleration > 1.2 -> {
                breaking = true
                -7
            }
            deceleration > 1 -> {
                breaking = true
                -6
            }
            deceleration > 0.8 -> {
                breaking = true
                -5
            }
            deceleration > 0.6 && breaking -> -4
            breaking -> 0
            else -> 1
        }
    }

    private fun getReqDeceleration(nowSpeedH: Float): Double {
        if (targetSpeedH - 2 > nowSpeedH) {
            return 0.0
        }
        val downSpeed1 = (nowSpeedH - (targetSpeedH - 2)) / 3.6f
        val downSpeed2 = (nowSpeedH + (targetSpeedH - 2)) / 3.6f
        val decelerationSecond = (targetDistance - 10) / (downSpeed2 / 2f)
        return downSpeed1 / decelerationSecond
    }
}
