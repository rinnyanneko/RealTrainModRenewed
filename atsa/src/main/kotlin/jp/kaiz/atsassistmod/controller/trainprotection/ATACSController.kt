// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.rtm.RtmTrains
import net.minecraft.world.phys.AABB
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ATACS (moving block) train protection.
 *
 * The original walked RTM's large-rail graph; this port scans for the nearest
 * train ahead and feeds the gap into the original braking-pattern formulas.
 */
open class ATACSController : TrainProtection() {
    /** [display, pattern, emergency] km/h limits. */
    private val speed = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)

    @Throws(Exception::class)
    override fun onTick(train: TrainEntity, distance: Double) {
        super.onTick(train, distance)

        val necessaryDistance = getBreakingDistance(RtmTrains.speed(train).toFloat())
        val gap = getAnotherTrainDistance(train, necessaryDistance + SEARCH_MARGIN)

        if (gap < 0.0) {
            speed[0] = Int.MAX_VALUE
            speed[1] = Int.MAX_VALUE
            speed[2] = Int.MAX_VALUE
        } else {
            setPatternSpeed(gap)
        }
    }

    private fun setPatternSpeed(trainDistance: Double) {
        if (trainDistance > 100.0) {
            speed[0] = getPattern(trainDistance - 120.0).toInt()
            speed[1] = getPattern(trainDistance - 110.0).toInt()
            speed[2] = getPattern(trainDistance - 100.0).toInt()
        } else {
            speed[0] = 0
            speed[1] = 0
            speed[2] = 0
        }
    }

    override fun getDisplaySpeed(): Int = speed[0]

    fun getPatternSpeed(): Int = speed[1]

    fun getEmergencySpeed(): Int = speed[2]

    override fun getNotch(speedH: Float): Int =
        when {
            speedH > getEmergencySpeed() -> -8
            speedH > getPatternSpeed() -> -7
            getDisplaySpeed() == 0 -> -5
            else -> 1
        }

    override fun getType(): TrainProtectionType = TrainProtectionType.ATACS

    /**
     * Distance to the nearest train ahead of this formation, or -1 if none within
     * searchDistance. "Ahead" is determined by the head car's facing direction.
     */
    private fun getAnotherTrainDistance(train: TrainEntity, searchDistance: Double): Double {
        val head = RtmTrains.head(train)
        val origin = head.position()
        val yaw = head.yRot
        val fx = -sin(Math.toRadians(yaw.toDouble()))
        val fz = cos(Math.toRadians(yaw.toDouble()))

        val box = AABB(origin, origin).inflate(searchDistance, 8.0, searchDistance)
        val candidates = head.level().getEntitiesOfClass(TrainEntity::class.java, box)

        val selfKey = RtmTrains.formationKey(train)
        var best = -1.0
        for (other in candidates) {
            if (RtmTrains.formationKey(other) == selfKey) {
                continue
            }
            val delta = other.position().subtract(origin)
            val along = delta.x * fx + delta.z * fz
            if (along <= 0.0) {
                continue
            }
            val lateral = abs(delta.x * fz - delta.z * fx)
            if (lateral > 4.0) {
                continue
            }
            if (along <= searchDistance && (best < 0.0 || along < best)) {
                best = along
            }
        }

        return if (best < 0.0) {
            -1.0
        } else {
            max(0.0, best - RtmTrains.trainDistance(train))
        }
    }

    private fun getPattern(distance: Double): Double =
        sqrt((1.4f * 3.6f * 7.2f * distance).toDouble())

    private fun getBreakingDistance(trainSpeedT: Float): Double {
        val trainSpeedH = trainSpeedT * 72f + 20f
        return Math.pow(trainSpeedH.toDouble(), 2.0) / (0.8f * 3.6f * 7.2f).toDouble()
    }

    companion object {
        private const val SEARCH_MARGIN = 100.0
    }
}
