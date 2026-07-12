// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.entity

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3

open class TrainSeatEntity(type: EntityType<out TrainSeatEntity>, level: Level) : Entity(type, level) {
    @JvmField
    var train: TrainEntity? = null
    private var cachedTrainId: Int = -1

    init {
        noPhysics = true
        setNoGravity(true)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(SEAT_INDEX, -1)
        builder.define(TRAIN_ID, -1)
    }

    open fun setTrain(train: TrainEntity?, seatIndex: Int) {
        this.train = train
        cachedTrainId = train?.id ?: -1
        entityData.set(TRAIN_ID, cachedTrainId)
        entityData.set(SEAT_INDEX, seatIndex)
    }

    open fun getTrain(): TrainEntity? {
        train?.let { return it }
        val vehicle = vehicle
        if (vehicle is TrainEntity) {
            train = vehicle
            return vehicle
        }
        val trainId = entityData.get(TRAIN_ID)
        if (trainId >= 0) {
            cachedTrainId = trainId
            val resolved = level().getEntity(trainId)
            if (resolved is TrainEntity) {
                train = resolved
                return resolved
            }
        }
        return null
    }

    open fun getSeatIndex(): Int = entityData.get(SEAT_INDEX)

    open fun getPassengersRidingOffset(): Double = 0.7

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        if (player.isCrouching) return InteractionResult.PASS
        val train = getTrain()
        if (train != null) {
            return train.rideSeat(player, getSeatIndex())
        }
        if (!level().isClientSide) {
            player.startRiding(this, true, false)
            return InteractionResult.CONSUME
        }
        return InteractionResult.SUCCESS
    }

    override fun tick() {
        super.tick()
        val train = getTrain()
        if (train == null || !train.isAlive || train.isRemoved) {
            if (!level().isClientSide) {
                ejectPassengers()
                discard()
            }
            return
        }
    }

    override fun readAdditionalSaveData(tag: ValueInput) {
    }

    override fun addAdditionalSaveData(tag: ValueOutput) {
    }

    override fun getAddEntityPacket(entity: ServerEntity): Packet<ClientGamePacketListener> =
        ClientboundAddEntityPacket(this, entity)

    override fun getDimensions(pose: Pose): EntityDimensions =
        EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT)

    override fun isPushable(): Boolean = false

    override fun isPickable(): Boolean = !isRemoved

    open fun canBeCollidedWith(): Boolean = !isRemoved

    override fun canBeCollidedWith(entity: Entity?): Boolean {
        if (isRemoved) return false
        val owner = getTrain()
        if (entity === owner) return false
        if (entity != null && owner?.hasPassenger(entity) == true) return false
        return true
    }

    open fun attachToTrain(train: TrainEntity?, seatIndex: Int) {
        setTrain(train, seatIndex)
    }

    open fun belongsToTrain(trainId: Int): Boolean =
        entityData.get(TRAIN_ID) == trainId

    override fun addPassenger(passenger: Entity) {
        super.addPassenger(passenger)
        if (passenger is Player) {
            passenger.yRot = yRot
        }
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val train = getTrain()
        if (train != null) {
            return train.hurtServer(level, source, amount)
        }
        return false
    }

    companion object {
        private const val HITBOX_WIDTH = 0.9f
        private const val HITBOX_HEIGHT = 0.25f

        @JvmField
        val SEAT_INDEX: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(TrainSeatEntity::class.java, EntityDataSerializers.INT)

        @JvmField
        val TRAIN_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(TrainSeatEntity::class.java, EntityDataSerializers.INT)
    }
}
