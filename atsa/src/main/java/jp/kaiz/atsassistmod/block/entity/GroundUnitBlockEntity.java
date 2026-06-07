package jp.kaiz.atsassistmod.block.entity;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.GroundUnitType;
import jp.kaiz.atsassistmod.controller.SpeedOrder;
import jp.kaiz.atsassistmod.controller.TrainControllerManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import jp.kaiz.atsassistmod.util.TrainStateType;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Consolidated ground-unit block entity. Behaviour switches on the
 * {@link GroundUnitBlock#TYPE} blockstate, replicating the original per-subclass
 * {@code updateEntity()}/{@code onTick()} logic against the RTM new API.
 */
public class GroundUnitBlockEntity extends BlockEntity {
    private long formationKey;
    private boolean linkRedStone;
    private int redStoneOutput;

    // ATC speed-limit notice / ATO departure / ATO change-speed
    private int speedLimit;
    // distance-based variants (ATC notice, TASC notice/correction)
    private double distance;
    private boolean autoBrake;
    private boolean useTrainDistance;
    private byte version = 1;
    // TrainState set
    private byte[] states = defaultStates();
    // change train protection
    private int tpType = TrainProtectionType.NONE.id;

    public GroundUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ATSAModBlockEntities.GROUND_UNIT.get(), pos, state);
    }

    private static byte[] defaultStates() {
        return new byte[]{-1, -9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    }

    public GroundUnitType guType() {
        return GroundUnitType.getType(getBlockState().getValue(GroundUnitBlock.TYPE));
    }

    // ---------------------------------------------------------------- ticking
    public static void serverTick(Level level, BlockPos pos, BlockState state, GroundUnitBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        GroundUnitType type = guType();
        switch (type) {
            case ATC_SpeedLimit_Cancel, ATC_SpeedLimit_Reset -> tickCancel(level, pos, type);
            case TASC_StopPotion -> tickStopPosition(level, pos);
            case TrainState_Set -> tickTrainStateSet(level, pos);
            case CHANGE_TP -> tickChangeTP(level, pos);
            case ATACS_Disable -> convertToChangeTP(level, pos);
            case None -> { /* no-op */ }
            default -> tickDefaultDetect(level, pos, type);
        }
    }

    /** Detection used by the majority of variants: control car over the unit. */
    private void tickDefaultDetect(Level level, BlockPos pos, GroundUnitType type) {
        // version migration for TASC notice/correction (distance baseline change)
        if ((type == GroundUnitType.TASC_StopPotion_Notice || type == GroundUnitType.TASC_StopPotion_Correction)
                && version == 0) {
            distance -= 2;
            version = 1;
            setChanged();
            sync();
        }
        if (linkRedStone && !level.hasNeighborSignal(pos)) {
            formationKey = 0;
            return;
        }
        TrainEntity train = firstTrain(level, pos, 3);
        if (train != null && RtmTrains.isControlCar(train)) {
            long key = RtmTrains.formationKey(train);
            if (formationKey != key) {
                applyOnPass(train, type);
                formationKey = key;
            }
            return;
        }
        formationKey = 0;
    }

    private void applyOnPass(TrainEntity train, GroundUnitType type) {
        double td = RtmTrains.trainDistance(train);
        switch (type) {
            case ATC_SpeedLimit_Notice -> {
                double d = useTrainDistance ? distance - td : distance;
                TrainControllerManager.getTrainController(train).addSpeedOrder(new SpeedOrder(speedLimit, d, autoBrake));
            }
            case TASC_StopPotion_Notice ->
                    TrainControllerManager.getTrainController(train).tascController
                            .enable(useTrainDistance ? distance + 1.5d - td : distance + 1.5d);
            case TASC_StopPotion_Correction ->
                    TrainControllerManager.getTrainController(train).tascController
                            .setStopDistance(useTrainDistance ? distance + 1.5d - td : distance + 1.5d);
            case TASC_Cancel -> TrainControllerManager.getTrainController(train).tascController.disable();
            case ATO_Departure_Signal -> TrainControllerManager.getTrainController(train).enableATO(speedLimit);
            case ATO_Cancel -> TrainControllerManager.getTrainController(train).disableATO();
            case ATO_Change_Speed -> TrainControllerManager.getTrainController(train).setMaxSpeed(speedLimit);
            default -> { }
        }
    }

    /** ATC speed-limit cancel/reset: optionally triggered at the rear of the formation. */
    private void tickCancel(Level level, BlockPos pos, GroundUnitType type) {
        if (linkRedStone && !level.hasNeighborSignal(pos)) {
            formationKey = 0;
            return;
        }
        TrainEntity train = firstTrain(level, pos, 4);
        if (train != null) {
            boolean trigger;
            if (useTrainDistance) {
                trigger = RtmTrains.formationSize(train) == 1
                        || (!RtmTrains.isControlCar(train)
                            && (RtmTrains.connected(train, 0) == null || RtmTrains.connected(train, 1) == null));
            } else {
                trigger = RtmTrains.isControlCar(train);
            }
            if (trigger) {
                long key = RtmTrains.formationKey(train);
                if (formationKey != key) {
                    if (type == GroundUnitType.ATC_SpeedLimit_Cancel) {
                        TrainControllerManager.getTrainController(train).removeSpeedLimit();
                    } else {
                        TrainControllerManager.getTrainController(train).removeAllSpeedLimit();
                    }
                    formationKey = key;
                }
                return;
            }
        }
        formationKey = 0;
    }

    /** TASC stop-position: redstone output = car count when stopped on the unit. */
    private void tickStopPosition(Level level, BlockPos pos) {
        TrainEntity train = firstTrain(level, pos, 3);
        if (train != null && (linkRedStone || RtmTrains.isControlCar(train))) {
            setRedStoneOutput(RtmTrains.speed(train) == 0F ? RtmTrains.formationSize(train) : 0);
            return;
        }
        setRedStoneOutput(0);
    }

    private void tickTrainStateSet(Level level, BlockPos pos) {
        if (!level.hasNeighborSignal(pos)) {
            return;
        }
        AABB box = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
        TrainEntity train = first(level.getEntitiesOfClass(TrainEntity.class, box));
        if (train == null) {
            return;
        }
        if (linkRedStone || RtmTrains.isControlCar(train)) {
            for (int i = 0; i < 12; i++) {
                if (i == 3) {
                    continue;
                }
                if (states[i] < TrainStateType.byId(i).min) {
                    continue;
                }
                TrainStateType.apply(train, i, states[i]);
            }
        }
    }

    private void tickChangeTP(Level level, BlockPos pos) {
        if (version == 0) {
            tpType = TrainProtectionType.ATACS.id;
            version = 1;
            setChanged();
            return;
        }
        tickDefaultDetectTP(level, pos);
    }

    private void tickDefaultDetectTP(Level level, BlockPos pos) {
        if (linkRedStone && !level.hasNeighborSignal(pos)) {
            formationKey = 0;
            return;
        }
        TrainEntity train = firstTrain(level, pos, 3);
        if (train != null && RtmTrains.isControlCar(train)) {
            long key = RtmTrains.formationKey(train);
            if (formationKey != key) {
                TrainControllerManager.getTrainController(train).setTrainProtection(TrainProtectionType.getType(tpType));
                formationKey = key;
            }
            return;
        }
        formationKey = 0;
    }

    /** Legacy ATACS-disable block converts itself into a CHANGE_TP unit set to NONE. */
    private void convertToChangeTP(Level level, BlockPos pos) {
        BlockState changed = getBlockState().setValue(GroundUnitBlock.TYPE, GroundUnitType.CHANGE_TP.id);
        level.setBlock(pos, changed, 3);
        if (level.getBlockEntity(pos) instanceof GroundUnitBlockEntity be) {
            be.tpType = TrainProtectionType.NONE.id;
            be.setChanged();
            be.sync();
        }
    }

    // ---------------------------------------------------------------- helpers
    private TrainEntity firstTrain(Level level, BlockPos pos, int height) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
        return first(level.getEntitiesOfClass(TrainEntity.class, box));
    }

    private static TrainEntity first(List<TrainEntity> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    public int getRedStoneOutput() {
        return redStoneOutput;
    }

    public void setRedStoneOutput(int power) {
        if (this.redStoneOutput != power) {
            this.redStoneOutput = power;
            setChanged();
            if (level != null) {
                level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
            }
        }
    }

    public boolean isLinkRedStone() {
        return linkRedStone;
    }

    public void setLinkRedStone(boolean linkRedStone) {
        this.linkRedStone = linkRedStone;
    }

    // accessors used by the config GUI / packets
    public int getSpeedLimit() { return speedLimit; }
    public void setSpeedLimit(int v) { this.speedLimit = v; }
    public double getDistance() { return distance; }
    public void setDistance(double v) { this.distance = v; }
    public boolean isAutoBrake() { return autoBrake; }
    public void setAutoBrake(boolean v) { this.autoBrake = v; }
    public boolean isUseTrainDistance() { return useTrainDistance; }
    public void setUseTrainDistance(boolean v) { this.useTrainDistance = v; }
    public byte[] getStates() { return states; }
    public void setStates(byte[] v) { this.states = v; }
    public TrainProtectionType getTPType() { return TrainProtectionType.getType(tpType); }
    public void setTPType(TrainProtectionType t) { this.tpType = t.id; }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // ---------------------------------------------------------------- NBT
    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putLong("formationID", formationKey);
        tag.putBoolean("linkRedStone", linkRedStone);
        tag.putInt("redStoneOutput", redStoneOutput);
        tag.putInt("speedLimit", speedLimit);
        tag.putDouble("distance", distance);
        tag.putBoolean("autoBrake", autoBrake);
        tag.putBoolean("trainDistance", useTrainDistance);
        tag.putByte("version", version);
        tag.store("state", Codec.BYTE_BUFFER, ByteBuffer.wrap(states));
        tag.putInt("tpType", tpType);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        formationKey = tag.getLongOr("formationID", 0L);
        linkRedStone = tag.getBooleanOr("linkRedStone", false);
        redStoneOutput = tag.getIntOr("redStoneOutput", 0);
        speedLimit = tag.getIntOr("speedLimit", 0);
        distance = tag.getDoubleOr("distance", 0.0D);
        autoBrake = tag.getBooleanOr("autoBrake", false);
        useTrainDistance = tag.getBooleanOr("trainDistance", false);
        version = tag.getByteOr("version", (byte) 1);
        tag.read("state", Codec.BYTE_BUFFER).ifPresent(buffer -> {
            byte[] s = new byte[buffer.remaining()];
            buffer.get(s);
            if (s.length == 12) {
                states = s;
            }
        });
        tpType = tag.getIntOr("tpType", TrainProtectionType.NONE.id);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
