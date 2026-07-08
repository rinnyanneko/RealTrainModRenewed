// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.util

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compass direction test for the IFTTT "Train direction" condition. The original
 * compared front/back bogie positions; RTM's new bogie compat does not expose bogie
 * coordinates, so this derives the heading from the head car's yaw + train direction.
 */
enum class CardinalDirection(
    private val label: String,
    private val positive: Boolean,
    private val axis: Axis,
) {
    NORTH("NORTH", false, Axis.Z),
    EAST("EAST", true, Axis.X),
    SOUTH("SOUTH", true, Axis.Z),
    WEST("WEST", false, Axis.X);

    fun getName(): String = label

    fun isInDirection(train: TrainEntity): Boolean {
        val yaw = Math.toRadians(train.yRot.toDouble())
        var fx = -sin(yaw)
        var fz = cos(yaw)
        if (train.trainDirection != 0.0f) {
            fx = -fx
            fz = -fz
        }
        return when (axis) {
            Axis.X -> positive == (fx > 0)
            Axis.Z -> positive == (fz > 0)
        }
    }

    private enum class Axis {
        X,
        Z,
    }

    companion object {
        @JvmStatic
        fun getDirection(name: String): CardinalDirection {
            return try {
                valueOf(name.uppercase())
            } catch (_: IllegalArgumentException) {
                NORTH
            }
        }
    }
}
