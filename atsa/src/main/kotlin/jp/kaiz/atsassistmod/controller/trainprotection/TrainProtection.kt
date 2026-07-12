// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity

open class TrainProtection {
    protected var train: TrainEntity? = null

    @Throws(Exception::class)
    open fun onTick(train: TrainEntity, distance: Double) {
        this.train = train
    }

    open fun getNotch(speedH: Float): Int = 1

    open fun getType(): TrainProtectionType = TrainProtectionType.NONE

    open fun getDisplaySpeed(): Int = Int.MAX_VALUE
}
