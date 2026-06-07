package jp.kaiz.atsassistmod.block;

import com.mojang.serialization.MapCodec;
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity;
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * IFTTT control block. The original also implemented RTM's {@code IBlockConnective}
 * (electric grid). RTM's new API has no public equivalent, so electric connectivity
 * is dropped (documented in PORTING_NOTES.md); comparator output is preserved.
 */
public class IftttBlock extends BaseEntityBlock {
    public static final MapCodec<IftttBlock> CODEC = simpleCodec(p -> new IftttBlock());

    public IftttBlock() {
        this(Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops());
    }

    public IftttBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IftttBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ATSAModBlockEntities.IFTTT.get(), IftttBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            jp.kaiz.atsassistmod.client.ATSAModClientHooks.openIftttEditor(pos);
        }
        return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof IftttBlockEntity be ? be.getRedStoneOutput() : 0;
    }
}
