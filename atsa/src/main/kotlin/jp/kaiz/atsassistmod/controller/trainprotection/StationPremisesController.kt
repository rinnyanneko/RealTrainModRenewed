// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

open class StationPremisesController : TrainProtection() {
    override fun getNotch(speedH: Float): Int = if (speedH > 25) -8 else 1

    override fun getType(): TrainProtectionType = TrainProtectionType.STATION_PREMISES

    override fun getDisplaySpeed(): Int = 25
}
