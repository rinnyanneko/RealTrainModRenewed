package cc.mirukuneko.realtrainmodrenewed.block;

import com.mojang.serialization.MapCodec;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.blockentity.ScriptBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ScriptBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final MapCodec<ScriptBlock> codec;

    public ScriptBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2F, 6.0F));
    }

    public ScriptBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.codec = simpleCodec(ScriptBlock::new);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
            type,
            RealTrainModRenewedBlockEntities.SCRIPT_BLOCK.get(),
            (tickerLevel, tickerPos, tickerState, blockEntity) -> {
                if (tickerLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    ScriptBlockEntity.serverTick(serverLevel, tickerPos, tickerState, blockEntity);
                }
            }
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            ClientHooks.openScriptBlockScreen(pos);
        }
        return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ScriptBlockEntity blockEntity) {
            blockEntity.onNeighborSignalChanged(level.hasNeighborSignal(pos));
        }
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
    }
}
