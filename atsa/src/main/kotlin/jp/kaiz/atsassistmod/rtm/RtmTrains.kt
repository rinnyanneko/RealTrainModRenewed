// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.rtm

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * Adapter bridging ATSAssist's old `EntityTrainBase` usage onto the real
 * RealTrainModRenewed [TrainEntity] API.
 */
object RtmTrains {
    /** Speed in blocks/tick, as the old `getSpeed()` returned. */
    @JvmStatic
    fun speed(train: TrainEntity): Float = train.speed

    /** Speed in km/h (old code multiplied `getSpeed()` by 72). */
    @JvmStatic
    fun speedKmh(train: TrainEntity): Float = train.speed * 72f

    /** The head (driving) car of the train's formation; never null. */
    @JvmStatic
    fun head(train: TrainEntity): TrainEntity = train.formationHead

    /** True when [train] is the formation's driving/control car. */
    @JvmStatic
    fun isControlCar(train: TrainEntity): Boolean = head(train) === train

    /**
     * Stable per-formation key. The old code used `Formation.id`; we use the
     * head car's entity id, which is stable while the formation exists.
     */
    @JvmStatic
    fun formationKey(train: TrainEntity): Long = head(train).id.toLong()

    /** Number of cars in the formation (old `getFormation().size()`). */
    @JvmStatic
    fun formationSize(train: TrainEntity): Int {
        val cars = train.formationTrainsForDisplay
        return if (cars.isEmpty()) 1 else cars.size
    }

    /** All cars of the formation. */
    @JvmStatic
    fun cars(train: TrainEntity): MutableList<TrainEntity?> = train.formationTrainsForDisplay

    /** Connected train at end [dir] (0/1), or null. */
    @JvmStatic
    fun connected(train: TrainEntity, dir: Int): TrainEntity? = train.getConnectedTrain(dir)

    /** Center-to-end distance; replaces `getModelSet().getConfig().trainDistance`. */
    @JvmStatic
    fun trainDistance(train: TrainEntity): Double = train.trainDistance.toDouble()

    @JvmStatic
    fun pos(train: TrainEntity): Vec3 = train.position()

    /** The entity controlling/riding this car, or null (old `riddenByEntity`). */
    @JvmStatic
    fun rider(train: TrainEntity): Entity? {
        val controlling = train.controllingPassenger
        return controlling ?: train.passengers.firstOrNull()
    }
}
