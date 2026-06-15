package cc.mirukuneko.realtrainmodrenewed.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class TrainBogieEntity extends Entity {
    private static final float HITBOX_WIDTH = 1.8f;
    private static final float HITBOX_HEIGHT = 1.05f;
    private static final EntityDataAccessor<Integer> TRAIN_ENTITY_ID = SynchedEntityData.defineId(TrainBogieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOGIE_INDEX = SynchedEntityData.defineId(TrainBogieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVATED = SynchedEntityData.defineId(TrainBogieEntity.class, EntityDataSerializers.BOOLEAN);

    public TrainEntity train;
    private int cachedTrainId = -1;

    public TrainBogieEntity(EntityType<? extends TrainBogieEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TRAIN_ENTITY_ID, -1);
        builder.define(BOGIE_INDEX, 0);
        builder.define(ACTIVATED, false);
    }

    public void setTrain(TrainEntity train, int bogieIndex) {
        this.train = train;
        if (train != null) {
            entityData.set(TRAIN_ENTITY_ID, train.getId());
            entityData.set(BOGIE_INDEX, bogieIndex);
            entityData.set(ACTIVATED, true);
        } else {
            entityData.set(TRAIN_ENTITY_ID, -1);
            entityData.set(BOGIE_INDEX, 0);
            entityData.set(ACTIVATED, false);
            cachedTrainId = -1;
        }
    }

    public TrainEntity getTrain() {
        if (train != null) return train;
        if (level() == null) return null;
        int id = entityData.get(TRAIN_ENTITY_ID);
        if (id < 0) return null;
        try {
            train = (TrainEntity) level().getEntity(id);
            cachedTrainId = id;
        } catch (Throwable e) {
            train = null;
        }
        return train;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        TrainEntity t = getTrain();
        if (t == null) return;
        int idx = entityData.get(BOGIE_INDEX);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0);
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {}
    @Override
    public void addAdditionalSaveData(ValueOutput tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity.getId());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT);
    }

    @Override
    public boolean isPushable() { return false; }
    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean canBeCollidedWith() { return false; }

    public boolean isActivated() { return entityData.get(ACTIVATED); }
    public int getBogieIndex() { return entityData.get(BOGIE_INDEX); }
}
