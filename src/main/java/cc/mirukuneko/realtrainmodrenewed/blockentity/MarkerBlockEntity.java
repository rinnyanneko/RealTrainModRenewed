package cc.mirukuneko.realtrainmodrenewed.blockentity;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * マーカーのブロックエンティティ。RTM の TileEntityMarker 相当。
 * 通常は facing からデフォルトの RailPosition を生成するが、レンチ設定GUIで
 * アンカー(曲線制御)・カント(傾き)を上書き設定できる。設定値は NBT に保存・同期する。
 */
public class MarkerBlockEntity extends BlockEntity {
    private boolean configured;
    private float anchorYaw;
    private float anchorPitch;
    private float anchorLengthHorizontal = -1.0F;
    private float anchorLengthVertical;
    private float cantCenter;
    private float cantEdge;
    private float cantRandom;

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModRenewedBlockEntities.MARKER.get(), pos, state);
    }

    public RailPosition getMarkerRP() {
        if (level == null) return null;
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof MarkerBlock)) return null;
        int facing = st.getValue(MarkerBlock.FACING);
        int dir = MarkerBlock.getMarkerDir(facing);
        boolean sw = ((MarkerBlock) st.getBlock()).isSwitch;
        RailPosition rp = new RailPosition(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), dir, sw ? 1 : 0);
        if (configured) {
            rp.anchorYaw = anchorYaw;
            rp.anchorPitch = anchorPitch;
            rp.anchorLengthHorizontal = anchorLengthHorizontal;
            rp.anchorLengthVertical = anchorLengthVertical;
            rp.cantCenter = cantCenter;
            rp.cantEdge = cantEdge;
            rp.cantRandom = cantRandom;
            rp.init();
        }
        return rp;
    }

    /** レンチ設定GUIからの値を適用する。 */
    public void configure(float anchorYaw, float anchorPitch, float anchorLengthHorizontal, float anchorLengthVertical,
                          float cantCenter, float cantEdge, float cantRandom) {
        this.configured = true;
        this.anchorYaw = anchorYaw;
        this.anchorPitch = anchorPitch;
        this.anchorLengthHorizontal = anchorLengthHorizontal;
        this.anchorLengthVertical = anchorLengthVertical;
        this.cantCenter = cantCenter;
        this.cantEdge = cantEdge;
        this.cantRandom = cantRandom;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isConfigured() { return configured; }
    public float getAnchorYaw() { return configured ? anchorYaw : defaultAnchorYaw(); }
    public float getAnchorPitch() { return anchorPitch; }
    public float getAnchorLengthHorizontal() { return anchorLengthHorizontal; }
    public float getAnchorLengthVertical() { return anchorLengthVertical; }
    public float getCantCenter() { return cantCenter; }
    public float getCantEdge() { return cantEdge; }
    public float getCantRandom() { return cantRandom; }

    private float defaultAnchorYaw() {
        BlockState st = getBlockState();
        if (st.getBlock() instanceof MarkerBlock) {
            return MarkerBlock.getMarkerDir(st.getValue(MarkerBlock.FACING)) * 45.0F;
        }
        return 0.0F;
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Configured", configured);
        tag.putFloat("A_Direction", anchorYaw);
        tag.putFloat("A_Pitch", anchorPitch);
        tag.putFloat("A_Length", anchorLengthHorizontal);
        tag.putFloat("A_LenV", anchorLengthVertical);
        tag.putFloat("C_Center", cantCenter);
        tag.putFloat("C_Edge", cantEdge);
        tag.putFloat("C_Random", cantRandom);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        configured = tag.getBooleanOr("Configured", false);
        anchorYaw = tag.getFloatOr("A_Direction", 0.0F);
        anchorPitch = tag.getFloatOr("A_Pitch", 0.0F);
        anchorLengthHorizontal = tag.getFloatOr("A_Length", -1.0F);
        anchorLengthVertical = tag.getFloatOr("A_LenV", 0.0F);
        cantCenter = tag.getFloatOr("C_Center", 0.0F);
        cantEdge = tag.getFloatOr("C_Edge", 0.0F);
        cantRandom = tag.getFloatOr("C_Random", 0.0F);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MarkerBlockEntity be) {
    }
}
