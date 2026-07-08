// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.util

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import kotlin.math.max

/**
 * Mirror of RTM's old `TrainState.TrainStateType` indices used by the
 * "Train State Set" ground unit. Values below [min] mean "leave unchanged".
 */
enum class TrainStateType(@JvmField val id: Int, @JvmField val min: Int) {
    State_Reverse(0, 0),
    State_Notch(1, -8),
    State_RailProgress(2, 0),
    /** index 3 is intentionally skipped by the ground unit. */
    State_Unused3(3, Int.MAX_VALUE),
    State_Door(4, 0),
    State_Light(5, 0),
    State_Pantograph(6, 0),
    State_TrainDir(7, 0),
    State_Destination(8, 0),
    State_Sound(9, 0),
    State_Unused10(10, Int.MAX_VALUE),
    State_InteriorLight(11, 0);

    companion object {
        @JvmStatic
        fun byId(id: Int): TrainStateType {
            return entries.firstOrNull { it.id == id } ?: State_Unused3
        }

        /** Applies state index [id] with [value] to the train. */
        @JvmStatic
        fun apply(train: TrainEntity, id: Int, value: Byte) {
            when (id) {
                0 -> train.reverser = if (value < 0) -1 else 1
                1 -> train.notch = value.toInt()
                2 -> train.railProgress = value.toFloat()
                4 -> {
                    train.isDoorRightOpen = value.toInt() and 1 != 0
                    train.isDoorLeftOpen = value.toInt() and 2 != 0
                }
                5 -> train.lightMode = value.toInt()
                6 -> train.isPantographUp = value > 0
                8 -> train.destinationIndex = max(0, value.toInt())
                9 -> train.soundIndex = max(0, value.toInt())
                11 -> train.isInteriorLightOn = value > 0
                // 7 (TrainDir), 3, 10: no safe equivalent - no-op.
            }
        }
    }
}
