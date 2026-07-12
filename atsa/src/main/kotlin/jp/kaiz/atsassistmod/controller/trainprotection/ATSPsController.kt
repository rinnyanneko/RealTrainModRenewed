// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

open class ATSPsController : TrainProtection() {
    override fun getType(): TrainProtectionType = TrainProtectionType.ATSPs
}
