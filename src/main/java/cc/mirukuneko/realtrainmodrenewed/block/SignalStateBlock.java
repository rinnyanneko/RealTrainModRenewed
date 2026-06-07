package cc.mirukuneko.realtrainmodrenewed.block;

import com.mojang.serialization.MapCodec;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems;
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalStateBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SignalStateBlock extends BaseEntityBlock {
    public static final IntegerProperty ASPECT = IntegerProperty.create("aspect", 0, 6);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final MapCodec<SignalStateBlock> codec;

    public SignalStateBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2F, 6.0F));
    }

    public SignalStateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.codec = simpleCodec(SignalStateBlock::new);
        registerDefaultState(stateDefinition.any().setValue(ASPECT, 0).setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASPECT, FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext context) {
        return box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        return new SignalStateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
            type,
            RealTrainModRenewedBlockEntities.SIGNAL_STATE.get(),
            (tickerLevel, tickerPos, tickerState, blockEntity) -> {
                if (tickerLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    SignalStateBlockEntity.serverTick(serverLevel, tickerPos, tickerState, blockEntity);
                }
            }
        );
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RealTrainModRenewedItems.CROWBAR_ITEM.get())) {
            if (!level.isClientSide()) {
                level.destroyBlock(pos, true, player);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        openScreen(level, pos);
        return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, BlockHitResult hit) {
        openScreen(level, pos);
        return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
    }

    private void openScreen(Level level, net.minecraft.core.BlockPos pos) {
        if (level.isClientSide()) {
            ClientHooks.openSignalReceiverScreen(pos);
        }
    }
}
