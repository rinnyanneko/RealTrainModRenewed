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
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3

/** Invisible floor tile that restores RTM-style walkable vehicle interiors. */
class TrainFloorEntity(type: EntityType<out TrainFloorEntity>, level: Level) : Entity(type, level) {
    private var train: TrainEntity? = null

    init {
        noPhysics = true
        setNoGravity(true)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(TRAIN_ID, -1)
        builder.define(LOCAL_X, 0.0f)
        builder.define(LOCAL_Z, 0.0f)
        builder.define(TILE_WIDTH, DEFAULT_FLOOR_WIDTH)
        builder.define(TILE_LENGTH, DEFAULT_FLOOR_LENGTH)
        builder.define(TILE_HEIGHT, DEFAULT_FLOOR_HEIGHT)
    }

    fun attachToTrain(
        train: TrainEntity,
        localX: Float,
        localZ: Float,
        width: Float,
        length: Float,
        height: Float,
    ) {
        this.train = train
        entityData.set(TRAIN_ID, train.id)
        entityData.set(LOCAL_X, localX)
        entityData.set(LOCAL_Z, localZ)
        entityData.set(TILE_WIDTH, width)
        entityData.set(TILE_LENGTH, length)
        entityData.set(TILE_HEIGHT, height)
        refreshDimensions()
        updateFloorPosition(train)
    }

    fun getTrain(): TrainEntity? {
        train?.takeUnless { it.isRemoved }?.let { return it }
        val trainId = entityData.get(TRAIN_ID)
        val resolved = if (trainId >= 0) level().getEntity(trainId) else null
        return (resolved as? TrainEntity)?.also { train = it }
    }

    fun belongsToTrain(trainId: Int): Boolean = entityData.get(TRAIN_ID) == trainId

    override fun tick() {
        super.tick()
        val owner = getTrain()
        if (owner == null || !owner.isAlive || owner.isRemoved) {
            if (!level().isClientSide && tickCount > 20) discard()
            return
        }
        updateFloorPosition(owner)
    }

    private fun updateFloorPosition(owner: TrainEntity) {
        val localX = entityData.get(LOCAL_X).toDouble()
        val localZ = entityData.get(LOCAL_Z).toDouble()
        val world = owner.localPointToWorld(Vec3(localX, FLOOR_LOCAL_Y, localZ))
        setPos(world.x, world.y, world.z)
        boundingBox = owner.getInteriorFloorBounds(
            localX = localX,
            localY = FLOOR_LOCAL_Y,
            localZ = localZ,
            width = entityData.get(TILE_WIDTH).toDouble(),
            length = entityData.get(TILE_LENGTH).toDouble(),
            height = entityData.get(TILE_HEIGHT).toDouble(),
        )
        setYRot(owner.yRot)
    }

    override fun getDimensions(pose: Pose): EntityDimensions =
        EntityDimensions.scalable(maxOf(entityData.get(TILE_WIDTH), entityData.get(TILE_LENGTH)), entityData.get(TILE_HEIGHT))

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        if (key == TILE_WIDTH || key == TILE_LENGTH || key == TILE_HEIGHT) refreshDimensions()
    }

    override fun isPickable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun canBeCollidedWith(other: Entity?): Boolean {
        if (isRemoved) return false
        val owner = getTrain()
        return other !== owner && (other == null || owner?.hasPassenger(other) != true)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        if (getTrain() == null) discard()
        return false
    }

    override fun readAdditionalSaveData(input: ValueInput) = Unit

    override fun addAdditionalSaveData(output: ValueOutput) = Unit

    override fun getAddEntityPacket(entity: ServerEntity): Packet<ClientGamePacketListener> =
        ClientboundAddEntityPacket(this, entity)

    companion object {
        const val DEFAULT_FLOOR_WIDTH: Float = 2.6f
        const val DEFAULT_FLOOR_LENGTH: Float = 2.6f
        const val DEFAULT_FLOOR_HEIGHT: Float = 0.18f
        private const val FLOOR_LOCAL_Y: Double = -0.12

        private val TRAIN_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(TrainFloorEntity::class.java, EntityDataSerializers.INT)
        private val LOCAL_X: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(TrainFloorEntity::class.java, EntityDataSerializers.FLOAT)
        private val LOCAL_Z: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(TrainFloorEntity::class.java, EntityDataSerializers.FLOAT)
        private val TILE_WIDTH: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(TrainFloorEntity::class.java, EntityDataSerializers.FLOAT)
        private val TILE_LENGTH: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(TrainFloorEntity::class.java, EntityDataSerializers.FLOAT)
        private val TILE_HEIGHT: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(TrainFloorEntity::class.java, EntityDataSerializers.FLOAT)
    }
}
