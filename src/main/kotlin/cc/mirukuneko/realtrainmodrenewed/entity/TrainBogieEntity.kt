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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

open class TrainBogieEntity(type: EntityType<out TrainBogieEntity>, level: Level) : Entity(type, level) {
    @JvmField
    var train: TrainEntity? = null
    private var cachedTrainId: Int = -1

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(TRAIN_ENTITY_ID, -1)
        builder.define(BOGIE_INDEX, 0)
        builder.define(ACTIVATED, false)
    }

    open fun setTrain(train: TrainEntity?, bogieIndex: Int) {
        this.train = train
        if (train != null) {
            entityData.set(TRAIN_ENTITY_ID, train.id)
            entityData.set(BOGIE_INDEX, bogieIndex)
            entityData.set(ACTIVATED, true)
        } else {
            entityData.set(TRAIN_ENTITY_ID, -1)
            entityData.set(BOGIE_INDEX, 0)
            entityData.set(ACTIVATED, false)
            cachedTrainId = -1
        }
    }

    open fun getTrain(): TrainEntity? {
        train?.let { return it }
        val id = entityData.get(TRAIN_ENTITY_ID)
        if (id < 0) return null
        try {
            train = level().getEntity(id) as TrainEntity
            cachedTrainId = id
        } catch (_: Throwable) {
            train = null
        }
        return train
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide) return
        val train = getTrain() ?: return
        val index = entityData.get(BOGIE_INDEX)
    }

    open val boundingBoxForCulling: AABB
        get() = boundingBox.inflate(2.0)

    override fun readAdditionalSaveData(tag: ValueInput) {
    }

    override fun addAdditionalSaveData(tag: ValueOutput) {
    }

    override fun getAddEntityPacket(entity: ServerEntity): Packet<ClientGamePacketListener> =
        ClientboundAddEntityPacket(this, entity)

    override fun getDimensions(pose: Pose): EntityDimensions =
        EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT)

    override fun isPushable(): Boolean = false

    override fun isPickable(): Boolean = true

    open fun canBeCollidedWith(): Boolean = false

    override fun canBeCollidedWith(entity: Entity?): Boolean = false

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        val train = getTrain() ?: return InteractionResult.PASS
        return train.interact(player, hand)
    }

    open fun isActivated(): Boolean = entityData.get(ACTIVATED)

    open fun setActivated(activated: Boolean) {
        entityData.set(ACTIVATED, activated)
    }

    open fun getBogieIndex(): Int = entityData.get(BOGIE_INDEX)

    open fun attachToTrain(train: TrainEntity?, bogieIndex: Int) {
        setTrain(train, bogieIndex)
    }

    open fun belongsToTrain(trainId: Int): Boolean =
        entityData.get(TRAIN_ENTITY_ID) == trainId

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val train = getTrain()
        if (train != null) {
            return train.hurtServer(level, source, amount)
        }
        return false
    }

    companion object {
        private const val HITBOX_WIDTH = 1.8f
        private const val HITBOX_HEIGHT = 1.05f

        @JvmField
        val TRAIN_ENTITY_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(TrainBogieEntity::class.java, EntityDataSerializers.INT)

        @JvmField
        val BOGIE_INDEX: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(TrainBogieEntity::class.java, EntityDataSerializers.INT)

        @JvmField
        val ACTIVATED: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(TrainBogieEntity::class.java, EntityDataSerializers.BOOLEAN)
    }
}
