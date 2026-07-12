// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity

/** Rn-ATS uses the signal aspect ahead of the train through RTM's legacy getSignal compatibility API. */
open class RnATSController : TrainProtection() {
    private var limitSpeed = 0

    @Throws(Exception::class)
    override fun onTick(train: TrainEntity, distance: Double) {
        super.onTick(train, distance)
        limitSpeed = when (train.signal) {
            1 -> 0
            2 -> 15
            3 -> 25
            4 -> 35
            5 -> 45
            6 -> 55
            7 -> 65
            8 -> 75
            9 -> 85
            10 -> 95
            11 -> 100
            12 -> 110
            13 -> 120
            14 -> 130
            else -> 25
        }
    }

    override fun getNotch(speedH: Float): Int {
        val overSpeed = speedH - limitSpeed
        return when {
            overSpeed > 5 -> -7
            overSpeed > 0 || train?.signal == 1 -> -4
            else -> 1
        }
    }

    override fun getType(): TrainProtectionType = TrainProtectionType.RnATS

    override fun getDisplaySpeed(): Int = limitSpeed
}
