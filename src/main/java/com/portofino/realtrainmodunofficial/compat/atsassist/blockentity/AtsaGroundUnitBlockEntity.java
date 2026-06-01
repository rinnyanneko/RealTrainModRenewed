package com.portofino.realtrainmodunofficial.compat.atsassist.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.compat.atsassist.AtsaTrainController;
import com.portofino.realtrainmodunofficial.compat.atsassist.block.AtsaGroundUnitBlock;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class AtsaGroundUnitBlockEntity extends BlockEntity {
    private int speedLimitKmh = 45;
    private int stopDistance = 80;
    private int redstoneOutput;
    private boolean tascEnabled = true;
    private boolean linkRedstone;
    private boolean autoBrake = true;
    private boolean useTrainDistance;
    private byte[] trainStates = new byte[]{-1, -9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    private String trainProtection = "ATS-P";

    public AtsaGroundUnitBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.ATSA_GROUND_UNIT.get(), pos, blockState);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, AtsaGroundUnitBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || (serverLevel.getGameTime() + pos.asLong()) % 5L != 0L) {
            return;
        }
        blockEntity.applyToNearbyTrains(serverLevel, pos, state.getValue(AtsaGroundUnitBlock.TYPE));
    }

    private void applyToNearbyTrains(ServerLevel level, BlockPos pos, int type) {
        if (linkRedstone && !level.hasNeighborSignal(pos)) {
            redstoneOutput = 0;
            return;
        }
        List<TrainEntity> trains = level.getEntitiesOfClass(TrainEntity.class, new AABB(pos).inflate(1.0D, 3.0D, 1.0D), TrainEntity::isAlive);
        redstoneOutput = trains.isEmpty() ? 0 : 15;
        for (TrainEntity train : trains) {
            switch (type) {
                case 1 -> {
                    AtsaTrainController.setSpeedLimit(train, speedLimitKmh);
                    if (autoBrake) {
                        limitSpeed(train, speedLimitKmh);
                    }
                }
                case 2 -> AtsaTrainController.clearSpeedLimit(train);
                case 3 -> AtsaTrainController.clearSpeedLimit(train);
                case 4 -> AtsaTrainController.setTascDistance(train, effectiveDistance(train));
                case 5 -> AtsaTrainController.disableTasc(train);
                case 6 -> AtsaTrainController.setTascDistance(train, effectiveDistance(train));
                case 7 -> {
                    if (Math.abs(train.getSpeed()) < 0.03F) {
                        redstoneOutput = 15;
                    } else {
                        redstoneOutput = 0;
                    }
                }
                case 9 -> AtsaTrainController.enableAto(train, speedLimitKmh);
                case 10 -> AtsaTrainController.disableAto(train);
                case 11 -> AtsaTrainController.enableAto(train, speedLimitKmh);
                case 13 -> applyTrainStates(train);
                case 14 -> AtsaTrainController.setTrainProtection(train, trainProtection);
                case 15 -> AtsaTrainController.setTrainProtection(train, "NONE");
                default -> {
                }
            }
        }
        setChanged();
    }

    private void limitSpeed(TrainEntity train, int kmh) {
        float limit = Math.max(0.0F, kmh / 72.0F);
        if (Math.abs(train.getSpeed()) > limit) {
            train.setNotch(-1);
            train.setSpeed(Math.copySign(limit, train.getSpeed()));
        }
    }

    private void brakeForStop(TrainEntity train) {
        if (!tascEnabled) {
            return;
        }
        if (stopDistance <= 0 || Math.abs(train.getSpeed()) > 0.02F) {
            train.setNotch(-Math.max(1, train.getMaxBrakeNotch()));
        } else {
            train.setNotch(0);
        }
    }

    private double effectiveDistance(TrainEntity train) {
        return useTrainDistance ? Math.max(0.0D, stopDistance - train.getTrainDistance()) : stopDistance;
    }

    private void applyTrainStates(TrainEntity train) {
        for (int i = 0; i < Math.min(trainStates.length, 12); i++) {
            if (i == 3 || trainStates[i] < -1) {
                continue;
            }
            train.syncVehicleState(i, trainStates[i]);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SpeedLimitKmh", speedLimitKmh);
        tag.putInt("StopDistance", stopDistance);
        tag.putInt("RedstoneOutput", redstoneOutput);
        tag.putBoolean("TascEnabled", tascEnabled);
        tag.putBoolean("LinkRedstone", linkRedstone);
        tag.putBoolean("AutoBrake", autoBrake);
        tag.putBoolean("UseTrainDistance", useTrainDistance);
        tag.putByteArray("TrainStates", trainStates);
        tag.putString("TrainProtection", trainProtection);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speedLimitKmh = Mth.clamp(tag.getInt("SpeedLimitKmh"), 0, 320);
        stopDistance = Mth.clamp(tag.getInt("StopDistance"), 0, 2000);
        redstoneOutput = Mth.clamp(tag.getInt("RedstoneOutput"), 0, 15);
        tascEnabled = !tag.contains("TascEnabled") || tag.getBoolean("TascEnabled");
        linkRedstone = tag.getBoolean("LinkRedstone");
        autoBrake = !tag.contains("AutoBrake") || tag.getBoolean("AutoBrake");
        useTrainDistance = tag.getBoolean("UseTrainDistance");
        if (tag.contains("TrainStates")) {
            byte[] loadedStates = tag.getByteArray("TrainStates");
            if (loadedStates.length >= 12) {
                trainStates = loadedStates;
            }
        }
        trainProtection = tag.getString("TrainProtection");
        if (trainProtection.isBlank()) {
            trainProtection = "ATS-P";
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void configure(int type, int speedLimitKmh, int stopDistance, boolean tascEnabled, String trainProtection) {
        configure(type, speedLimitKmh, stopDistance, tascEnabled, trainProtection, linkRedstone, autoBrake, useTrainDistance, trainStates);
    }

    public void configure(int type, int speedLimitKmh, int stopDistance, boolean tascEnabled, String trainProtection,
                          boolean linkRedstone, boolean autoBrake, boolean useTrainDistance, byte[] trainStates) {
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(AtsaGroundUnitBlock.TYPE, Mth.clamp(type, 0, 15)), 3);
        }
        this.speedLimitKmh = Mth.clamp(speedLimitKmh, 0, 320);
        this.stopDistance = Mth.clamp(stopDistance, 0, 2000);
        this.tascEnabled = tascEnabled;
        this.linkRedstone = linkRedstone;
        this.autoBrake = autoBrake;
        this.useTrainDistance = useTrainDistance;
        if (trainStates != null && trainStates.length >= 12) {
            this.trainStates = trainStates;
        }
        this.trainProtection = trainProtection == null || trainProtection.isBlank() ? "ATS-P" : trainProtection;
        setChanged();
    }

    public int getUnitType() {
        return getBlockState().getValue(AtsaGroundUnitBlock.TYPE);
    }

    public int getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public int getStopDistance() {
        return stopDistance;
    }

    public int getRedstoneOutput() {
        return redstoneOutput;
    }

    public boolean isTascEnabled() {
        return tascEnabled;
    }

    public boolean isLinkRedstone() {
        return linkRedstone;
    }

    public boolean isAutoBrake() {
        return autoBrake;
    }

    public boolean isUseTrainDistance() {
        return useTrainDistance;
    }

    public byte[] getTrainStates() {
        return trainStates.clone();
    }

    public String getTrainProtection() {
        return trainProtection;
    }
}
