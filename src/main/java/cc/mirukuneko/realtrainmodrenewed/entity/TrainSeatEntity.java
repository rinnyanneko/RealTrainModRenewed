package cc.mirukuneko.realtrainmodrenewed.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class TrainSeatEntity extends Entity {
    private static final float HITBOX_WIDTH = 0.9f;
    private static final float HITBOX_HEIGHT = 0.25f;
    private static final EntityDataAccessor<Integer> SEAT_INDEX = SynchedEntityData.defineId(TrainSeatEntity.class, EntityDataSerializers.INT);

    public TrainEntity train;
    private int cachedTrainId = -1;

    public TrainSeatEntity(EntityType<? extends TrainSeatEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SEAT_INDEX, -1);
    }

    public void setTrain(TrainEntity train, int seatIndex) {
        this.train = train;
        entityData.set(SEAT_INDEX, seatIndex);
    }

    public TrainEntity getTrain() {
        if (train != null) return train;
        if (level() == null) return null;
        Entity vehicle = getVehicle();
        if (vehicle instanceof TrainEntity t) {
            train = t;
            return t;
        }
        return null;
    }

    public int getSeatIndex() { return entityData.get(SEAT_INDEX); }

    public double getPassengersRidingOffset() {
        return 0.7;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isCrouching()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            player.startRiding(this, true, false);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            TrainEntity t = getTrain();
            if (t != null) {
                // Position follows train
            }
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {}
    @Override
    public void addAdditionalSaveData(ValueOutput tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT);
    }

    @Override
    public boolean isPushable() { return false; }
    @Override
    public boolean isPickable() { return true; }
    public boolean canBeCollidedWith() { return false; }

    public void attachToTrain(TrainEntity train, int seatIndex) { setTrain(train, seatIndex); }
    public boolean belongsToTrain(int trainId) { return train != null && train.getId() == trainId; }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof Player) {
            passenger.setYRot(getYRot());
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
}
