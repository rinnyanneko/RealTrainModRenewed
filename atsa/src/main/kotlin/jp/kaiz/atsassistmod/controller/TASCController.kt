// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller

open class TASCController {
    private var stopDistance = DISABLE_DISTANCE
    private var enable = false
    private var breaking = false

    fun changeTargetDistance(movedDistance: Double) {
        stopDistance = maxOf(stopDistance - movedDistance, DISABLE_DISTANCE)
    }

    fun setStopDistance(stopDistance: Double) {
        this.stopDistance = stopDistance
    }

    fun getStopDistance(): Double = stopDistance

    fun enable(targetDistance: Double) {
        stopDistance = targetDistance
        enable = true
    }

    fun disable() {
        breaking = false
        stopDistance = DISABLE_DISTANCE
        enable = false
    }

    fun isEnable(): Boolean = enable

    fun isBreaking(): Boolean = breaking

    fun isStopPosition(): Boolean = stopDistance < 1.0

    fun getNeedNotch(nowSpeedH: Float): Int {
        val deceleration = getReqDeceleration(nowSpeedH)

        if (isStopPosition()) {
            breaking = true
            return if (deceleration <= 0) -7 else 5
        }

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
            deceleration > 0.8 && breaking -> -5
            deceleration > 0.6 && breaking -> -4
            deceleration >= 0 && breaking -> {
                breaking = false
                1
            }
            breaking -> -6
            else -> 0
        }
    }

    private fun getReqDeceleration(nowSpeedH: Float): Double =
        Math.pow(nowSpeedH.toDouble(), 2.0) / (stopDistance * 7.2) / 3.6

    fun setBraking(value: Boolean) {
        breaking = value
    }

    companion object {
        private const val DISABLE_DISTANCE = -1.0
    }
}
