package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InstalledObjectBlock extends BaseEntityBlock {
    public static final MapCodec<InstalledObjectBlock> CODEC = simpleCodec(InstalledObjectBlock::new);

    public InstalledObjectBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public InstalledObjectBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.4F, 2.0F).noOcclusion());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return net.minecraft.world.phys.shapes.Shapes.empty();
            }
            return getShapeForCategory(blockEntity.getCategory());
        }
        return box(5, 0, 5, 11, 16, 11);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return net.minecraft.world.phys.shapes.Shapes.empty();
            }
            return getShapeForCategory(blockEntity.getCategory());
        }
        return box(5, 0, 5, 11, 16, 11);
    }

    private static VoxelShape getShapeForCategory(InstalledObjectCategory cat) {
        if (cat == null) return box(5, 0, 5, 11, 16, 11);
        return switch (cat) {
            case SIGNAL      -> box(6, 0, 6, 10, 16, 10);
            case INSULATOR   -> box(6, 0, 6, 10, 8, 10);
            case LIGHT       -> box(5, 0, 5, 11, 12, 11);
            case SIGNBOARD   -> box(4, 0, 4, 12, 16, 12);
            case CROSSING    -> box(6, 0, 6, 10, 16, 10);
            case TICKET_GATE -> box(4, 0, 4, 12, 16, 12);
            case SPEAKER     -> box(4, 0, 4, 12, 16, 12);
            case WIRE        -> net.minecraft.world.phys.shapes.Shapes.empty();
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get(), InstalledObjectBlockEntity::tick);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be && be.isSpeaker()) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openSpeakerScreen(pos);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            removeSignalLink(level, pos);
            removeAttachedWires(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void removeSignalLink(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) || !blockEntity.isSignal()) {
            return;
        }
        SignalNetworkSavedData.get(serverLevel).removeSignal(serverLevel, pos, blockEntity.getSignalChannel());
    }

    private static void removeAttachedWires(Level level, BlockPos pos) {
        int radius = 64;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(checkPos) instanceof InstalledObjectBlockEntity blockEntity)) {
                        continue;
                    }
                    BlockPos start = blockEntity.getWireStart();
                    BlockPos end = blockEntity.getWireEnd();
                    if (pos.equals(start) || pos.equals(end)) {
                        level.removeBlock(checkPos, false);
                    }
                }
            }
        }
    }

    private static void updatePoweredState(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity)) {
            return;
        }
        InstalledObjectCategory cat = blockEntity.getCategory();
        if (cat == InstalledObjectCategory.SPEAKER) {
            updateSpeaker(level, pos, blockEntity);
            return;
        }
        if (cat != InstalledObjectCategory.CROSSING) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        blockEntity.setPowered(powered);
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    private static void updateSpeaker(Level level, BlockPos pos, InstalledObjectBlockEntity blockEntity) {
        // レッドストーン信号強度(1-15)を音源ID(本家踏襲)として使い、立ち上がり(OFF→ON)で鳴らす。
        int signal = level.getBestNeighborSignal(pos);
        boolean wasPowered = blockEntity.isPowered();
        boolean nowPowered = signal > 0;
        blockEntity.setPowered(nowPowered);
        if (nowPowered && !wasPowered && level instanceof ServerLevel serverLevel) {
            String sound = com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig.getSound(signal);
            if (sound != null) {
                int range = blockEntity.getSpeakerRange();
                float volume = Math.max(1.0F, range / 16.0F);
                double cx = pos.getX() + 0.5D;
                double cy = pos.getY() + 0.5D;
                double cz = pos.getZ() + 0.5D;
                var payload = new com.portofino.realtrainmodunofficial.network.SpeakerPlayPayload(
                    cx, cy, cz, sound, volume, 1.0F);
                double rangeSq = (double) range * (double) range;
                for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
                    if (p.distanceToSqr(cx, cy, cz) <= rangeSq) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
                    }
                }
            }
        }
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }
}
