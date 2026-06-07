package cc.mirukuneko.realtrainmodrenewed.block;

import com.mojang.serialization.MapCodec;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig;
import cc.mirukuneko.realtrainmodrenewed.network.SpeakerPlayPayload;
import cc.mirukuneko.realtrainmodrenewed.network.SpeakerStopPayload;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems;
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry;
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InstalledObjectBlock extends BaseEntityBlock {
    public static final MapCodec<InstalledObjectBlock> CODEC = simpleCodec(InstalledObjectBlock::new);
    private static final VoxelShape RTM_SELECTION_SHAPE = box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape EMPTY_SHAPE = Shapes.empty();

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

    // 照明カテゴリかつレッドストーンで点灯中のときブロック光源レベル15を返す。
    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.LIGHT
            && be.isPowered()) {
            return 15;
        }
        return super.getLightEmission(state, level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return EMPTY_SHAPE;
            }
            return RTM_SELECTION_SHAPE;
        }
        return RTM_SELECTION_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity
            && blockEntity.getCategory() == InstalledObjectCategory.TICKET_GATE
            && !blockEntity.isTicketGateOpen()) {
            return RTM_SELECTION_SHAPE;
        }
        return EMPTY_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InstalledObjectBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealTrainModRenewedBlockEntities.INSTALLED_OBJECT.get(), InstalledObjectBlockEntity::tick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RealTrainModRenewedItems.IC_CARD_ITEM.get())
            && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
            if (!level.isClientSide()) {
                be.activateTicketGate();
            }
            return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be && be.isSpeaker()) {
            if (level.isClientSide()) {
                ClientHooks.openSpeakerScreen(pos);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide()) {
            updatePoweredState(level, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean isMoving) {
        if (!level.isClientSide()) {
            updatePoweredState(level, pos);
        }
        super.neighborChanged(state, level, pos, block, orientation, isMoving);
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean isMoving) {
        removeSignalLink(level, pos);
        removeAttachedWires(level, pos);
        stopSpeakerSoundOnRemove(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
    }

    /** スピーカーブロック破壊時、再生中の音を範囲内プレイヤーで停止させる(壊しても鳴り続ける対策)。 */
    private static void stopSpeakerSoundOnRemove(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double cx = pos.getX() + 0.5D, cy = pos.getY() + 0.5D, cz = pos.getZ() + 0.5D;
        var stop = new SpeakerStopPayload(cx, cy, cz);
        for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, stop);
        }
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
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity selfBe)
            || selfBe.getCategory() != InstalledObjectCategory.WIRE) {
            return;
        }
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
        if (cat == InstalledObjectCategory.SPEAKER && !hasDefinitionRunningSound(blockEntity)) {
            updateSpeaker(level, pos, blockEntity);
            return;
        }
        // 照明: レッドストーン信号で点灯/消灯し、ブロック光源レベルを更新する。
        if (cat == InstalledObjectCategory.LIGHT) {
            boolean powered = level.hasNeighborSignal(pos);
            if (blockEntity.isPowered() != powered) {
                blockEntity.setPowered(powered);
                level.getLightEngine().checkBlock(pos);
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
            return;
        }
        if (cat != InstalledObjectCategory.CROSSING && !hasDefinitionRunningSound(blockEntity)) {
            return;
        }
        // hasNeighborSignal はワイヤ隣接などで拾えないことがあるため getBestNeighborSignal(>0) で判定。
        boolean powered = level.getBestNeighborSignal(pos) > 0;
        blockEntity.setPowered(powered);
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    private static boolean hasDefinitionRunningSound(InstalledObjectBlockEntity blockEntity) {
        var definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        String sound = definition == null ? "" : definition.getRunningSound();
        return sound != null && !sound.isBlank();
    }

    private static void updateSpeaker(Level level, BlockPos pos, InstalledObjectBlockEntity blockEntity) {
        // レッドストーン信号強度(1-15)を音源ID(本家踏襲)として使い、立ち上がり(OFF→ON)で鳴らす。
        int signal = level.getBestNeighborSignal(pos);
        boolean wasPowered = blockEntity.isPowered();
        boolean nowPowered = signal > 0;
        blockEntity.setPowered(nowPowered);
        if (level instanceof ServerLevel serverLevel) {
            double cx = pos.getX() + 0.5D;
            double cy = pos.getY() + 0.5D;
            double cz = pos.getZ() + 0.5D;
            if (nowPowered && !wasPowered) {
                // 立ち上がり: 再生
                String sound = SpeakerSoundConfig.getSound(signal);
                if (sound != null) {
                    int range = blockEntity.getSpeakerRange();
                    float volume = Math.max(1.0F, range / 16.0F);
                    var payload = new SpeakerPlayPayload(
                        cx, cy, cz, sound, volume, 1.0F);
                    double rangeSq = (double) range * (double) range;
                    for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
                        if (p.distanceToSqr(cx, cy, cz) <= rangeSq) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
                        }
                    }
                }
            } else if (!nowPowered && wasPowered) {
                // 立ち下がり(レバーOFF): 再生中の音を止める。範囲外プレイヤーにも送って取りこぼしを防ぐ。
                var stop = new SpeakerStopPayload(cx, cy, cz);
                for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, stop);
                }
            }
        }
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }
}
