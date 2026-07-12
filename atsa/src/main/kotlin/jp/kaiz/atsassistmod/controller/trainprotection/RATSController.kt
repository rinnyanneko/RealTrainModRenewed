// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity

/** R-ATS uses the signal aspect ahead of the train through RTM's legacy getSignal compatibility API. */
open class RATSController : TrainProtection() {
    private var limitSpeed = 0

    @Throws(Exception::class)
    override fun onTick(train: TrainEntity, distance: Double) {
        super.onTick(train, distance)
        limitSpeed = when (train.signal) {
            2 -> 30
            3 -> 45
            4 -> 65
            5 -> 95
            else -> Int.MAX_VALUE
        }
    }

    override fun getNotch(speedH: Float): Int {
        if (limitSpeed == Int.MAX_VALUE) {
            return 1
        }
        val overSpeed = speedH - limitSpeed
        return when {
            overSpeed > 5 -> -7
            overSpeed > 0 -> -4
            else -> 1
        }
    }

    override fun getType(): TrainProtectionType = TrainProtectionType.RATS

    override fun getDisplaySpeed(): Int = limitSpeed
}
