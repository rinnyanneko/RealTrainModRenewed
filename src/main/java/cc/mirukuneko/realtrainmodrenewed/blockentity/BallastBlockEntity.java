package cc.mirukuneko.realtrainmodrenewed.blockentity;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 道床(バラスト)ブロックのブロックエンティティ。
 * 対応するレールコア(LargeRailCore)の位置を保持し、道床を壊すとレールも撤去できるようにする。
 * また列車設置時のレール検出 (getRailMapAt) で道床からレールコアを引けるようにする。
 */
public class BallastBlockEntity extends BlockEntity {
    private BlockPos corePos;

    public BallastBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModRenewedBlockEntities.BALLAST.get(), pos, state);
    }

    public void setCorePos(BlockPos corePos) {
        this.corePos = corePos;
        setChanged();
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        if (corePos != null) {
            tag.putIntArray("CorePos", new int[]{corePos.getX(), corePos.getY(), corePos.getZ()});
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        tag.getIntArray("CorePos").ifPresent(a -> {
            if (a.length >= 3) {
                corePos = new BlockPos(a[0], a[1], a[2]);
            }
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
