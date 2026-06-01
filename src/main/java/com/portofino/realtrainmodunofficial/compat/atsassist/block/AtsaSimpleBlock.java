package com.portofino.realtrainmodunofficial.compat.atsassist.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaSimpleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AtsaSimpleBlock extends BaseEntityBlock {
    private final String screenTitle;
    private final MapCodec<AtsaSimpleBlock> codec;

    public AtsaSimpleBlock(String screenTitle) {
        this(screenTitle, BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.5F, 6.0F));
    }

    public AtsaSimpleBlock(String screenTitle, BlockBehaviour.Properties properties) {
        super(properties);
        this.screenTitle = screenTitle;
        this.codec = simpleCodec(props -> new AtsaSimpleBlock(screenTitle, props));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AtsaSimpleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(
            type,
            RealTrainModUnofficialBlockEntities.ATSA_SIMPLE.get(),
            AtsaSimpleBlockEntity::serverTick
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openAtsaSimpleBlockScreen(pos, screenTitle);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AtsaSimpleBlockEntity blockEntity) {
            return blockEntity.getRedstoneOutput();
        }
        return 0;
    }
}
