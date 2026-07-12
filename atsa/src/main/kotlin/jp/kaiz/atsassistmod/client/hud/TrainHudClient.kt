// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.hud

import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType

/** Client-side mirror of a formation's controller state. */
open class TrainHudClient {
    private var atoSpeed = 0
    private var tascDistance = 0
    private var atcSpeed = 0
    private var tpLimit = 0
    private var tpType = 0
    private var ato = false
    private var tasc = false
    private var manual = false
    private var notShowHud = false

    fun set(
        ato: Boolean,
        tasc: Boolean,
        tpType: Int,
        atoSpeed: Int,
        tascDistance: Int,
        atcSpeed: Int,
        tpLimit: Int,
        manual: Boolean,
    ) {
        this.ato = ato
        this.tasc = tasc
        this.tpType = tpType
        this.atoSpeed = atoSpeed
        this.tascDistance = tascDistance
        this.atcSpeed = atcSpeed
        this.tpLimit = tpLimit
        this.manual = manual
    }

    fun isATO(): Boolean = ato

    fun isTASC(): Boolean = tasc

    fun setATO(value: Boolean) {
        ato = value
    }

    fun setTASC(value: Boolean) {
        tasc = value
    }

    fun isATACS(): Boolean = tpType == TrainProtectionType.ATACS.id

    fun getTrainProtectionType(): TrainProtectionType = TrainProtectionType.getType(tpType)

    fun setTrainProtectionType(type: TrainProtectionType) {
        tpType = type.id
    }

    fun getATOSpeed(): Int = atoSpeed

    fun getTASCDistance(): Int = tascDistance

    fun getATCSpeed(): Int = atcSpeed

    fun getTrainProtectionSpeed(): Int = tpLimit

    fun isManualDrive(): Boolean = manual

    fun isNotShowHud(): Boolean = notShowHud

    fun setNotShowHud(value: Boolean) {
        notShowHud = value
    }
}
