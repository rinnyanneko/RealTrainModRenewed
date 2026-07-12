// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.entity.formation

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import java.util.stream.Stream

class Formation(@JvmField var id: String, size: Int) {
    @JvmField
    var entries: Array<FormationEntry?> = arrayOfNulls(size)

    private var direction: Int = 0 // 0 = entries[0] is front, 1 = entries[last] is front
    private var speed: Float = 0f

    init {
        FormationManager.instance.register(id, this)
    }

    constructor(id: Long, size: Int) : this(id.toString(), size)

    fun size(): Int = entries.size

    fun get(i: Int): FormationEntry? = entries[i]

    fun stream(): Stream<FormationEntry> = entries.filterNotNull().stream()

    fun trainStream(): Stream<TrainEntity> = stream().map { it.train }.filter { it != null }

    fun getEntry(obj: Any?): FormationEntry? {
        val train = asTrainEntity(obj) ?: return null
        return entries.filterNotNull().firstOrNull { train == it.train }
    }

    private fun asTrainEntity(obj: Any?): TrainEntity? {
        if (obj is TrainEntity) return obj
        if (obj == null) return null
        try {
            val cls = obj.javaClass
            val trainField = cls.getDeclaredField("train")
            trainField.isAccessible = true
            val inner = trainField.get(obj)
            return inner as? TrainEntity
        } catch (_: Exception) { }
        return null
    }

    fun getFront(): FormationEntry? {
        return if (direction == 0) entries.firstOrNull()
        else entries.lastOrNull()
    }

    fun getFrontEntry(): FormationEntry? = getFront()

    fun getRear(): FormationEntry? {
        return if (direction == 0) entries.lastOrNull()
        else entries.firstOrNull()
    }

    fun getRearEntry(): FormationEntry? = getRear()

    fun getTrainList(): Stream<TrainEntity> = trainStream()

    fun getAllTrains(): List<TrainEntity> = entries.filterNotNull().map { it.train }.filterNotNull()

    fun getDirection(): Int = direction

    fun setDirection(dir: Int) {
        direction = if (dir != 0) 1 else 0
    }

    fun getSpeed(): Float = speed

    fun setSpeed(s: Float) {
        speed = s
    }

    fun getFormationEntry(target: TrainEntity?): FormationEntry? {
        if (target == null) return null
        return entries.filterNotNull().firstOrNull { it.train === target }
    }

    fun getFormationNumber(): Int {
        var count = 0
        for (i in entries.indices) {
            if (entries[i] != null) count++
        }
        return count
    }

    fun setFormation(formation: Formation?) {
        if (formation == null) return
        for (i in formation.entries.indices) {
            if (i < entries.size) entries[i] = formation.entries[i]
        }
    }

    fun getLoadedFormationNumber(): Int {
        var count = 0
        for (entry in entries) {
            if (entry != null && entry.train != null) count++
        }
        return count
    }

    fun isLoaded(): Boolean = getLoadedFormationNumber() >= size()

    fun isFront(target: TrainEntity?): Boolean {
        val front = getFront() ?: return false
        return front.train === target
    }

    fun isFrontCar(target: TrainEntity?): Boolean = isFront(target)

    fun isRear(target: TrainEntity?): Boolean {
        val rear = getRear() ?: return false
        return rear.train === target
    }

    fun isRearCar(target: TrainEntity?): Boolean = isRear(target)

    fun getIndex(target: TrainEntity?): Int {
        if (target == null) return -1
        for (i in entries.indices) {
            val e = entries[i]
            if (e != null && e.train === target) return i
        }
        return -1
    }

    fun setEntry(index: Int, train: TrainEntity?, speed: Float, position: Float) {
        if (index < 0 || index >= entries.size) return
        val t = train ?: return
        entries[index] = FormationEntry(t, 0, 0)
    }

    fun removeEntry(index: Int) {
        if (index < 0 || index >= entries.size) return
        entries[index] = null
    }

    fun clear() {
        for (i in entries.indices) entries[i] = null
    }

    fun updateTrainMovement() {
        val frontTrain = getFront()?.train ?: return
        speed = frontTrain.speed
        if (direction == 0) {
            var leader = entries.firstOrNull()?.train ?: return
            for (index in 1 until entries.size) {
                val entry = entries[index] ?: continue
                entry.train.moveAsFormationFollower(leader, entry.leaderSide, entry.followerSide, speed)
                leader = entry.train
            }
        } else {
            var leader = entries.lastOrNull()?.train ?: return
            for (index in entries.lastIndex - 1 downTo 0) {
                val follower = entries[index]?.train ?: continue
                val connection = entries[index + 1] ?: continue
                follower.moveAsFormationFollower(
                    leader,
                    connection.followerSide,
                    connection.leaderSide,
                    speed
                )
                leader = follower
            }
        }
    }

    override fun toString(): String = "Formation{id='$id', size=${entries.size}}"
}

