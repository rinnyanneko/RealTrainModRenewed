// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.block

/**
 * Ground-unit variants. The original used block metadata 0-15; on 1.21 the variant
 * is stored in the [GroundUnitBlock.TYPE] blockstate property and the
 * `GroundUnitBlockEntity` branches on it.
 */
enum class GroundUnitType(@JvmField val id: Int) {
    None(0),
    ATC_SpeedLimit_Notice(1),
    ATC_SpeedLimit_Cancel(2),
    ATC_SpeedLimit_Reset(3),
    TASC_StopPotion_Notice(4),
    TASC_Cancel(5),
    TASC_StopPotion_Correction(6),
    TASC_StopPotion(7),
    ATO_Departure_Signal(9),
    ATO_Cancel(10),
    ATO_Change_Speed(11),
    TrainState_Set(13),
    CHANGE_TP(14),
    ATACS_Disable(15);

    companion object {
        @JvmStatic
        fun getType(id: Int): GroundUnitType {
            return entries.firstOrNull { it.id == id } ?: None
        }
    }
}
