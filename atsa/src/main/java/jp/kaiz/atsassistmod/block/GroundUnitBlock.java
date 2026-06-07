package jp.kaiz.atsassistmod.block;

import com.mojang.serialization.MapCodec;
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;

/**
 * Single ground-unit block carrying the variant in the {@link #TYPE} blockstate
 * (0-15). Replaces the original metadata-based block + 14 tile-entity subclasses.
 */
public class GroundUnitBlock extends BaseEntityBlock {
    public static final MapCodec<GroundUnitBlock> CODEC = simpleCodec(p -> new GroundUnitBlock());
    public static final IntegerProperty TYPE = IntegerProperty.create("gu_type", 0, 15);

    public GroundUnitBlock() {
        this(Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops());
    }

    public GroundUnitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundUnitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ATSAModBlockEntities.GROUND_UNIT.get(), GroundUnitBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            jp.kaiz.atsassistmod.client.ATSAModClientHooks.openGroundUnit(pos);
        }
        return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof GroundUnitBlockEntity be ? be.getRedStoneOutput() : 0;
    }
}
