package cc.mirukuneko.realtrainmodrenewed.rail.util;

import cc.mirukuneko.realtrainmodrenewed.rail.math.CurveMath;
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Port of jp.legacy.legacy.rail.util.RailPosition (subset used by RealTrainModRenewed rails).
 */
public final class RailPosition {
    public static final float[][] REVISION = new float[][]{
        {0.0F, -0.5F}, {-0.5F, -0.5F}, {-0.5F, 0.0F}, {-0.5F, 0.499999F},
        {0.0F, 0.499999F}, {0.499999F, 0.499999F}, {0.499999F, 0.0F}, {0.499999F, -0.5F}
    };
    public int blockX;
    public int blockY;
    public int blockZ;
    public final byte switchType;
    public byte direction;
    public byte height;
    public float anchorYaw;
    public float anchorPitch;
    public float anchorLengthHorizontal;
    public float anchorLengthVertical;
    public float cantCenter;
    public float cantEdge;
    public float cantRandom;
    public float constLimitHP;
    public float constLimitHN;
    public float constLimitWP;
    public float constLimitWN;
    public double posX;
    public double posY;
    public double posZ;

    public RailPosition(int x, int y, int z, int dir, int type) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.direction = (byte) dir;
        this.switchType = (byte) type;
        this.height = 0;
        this.anchorYaw = CurveMath.wrapAngle((float) dir * 45.0F);
        this.anchorLengthHorizontal = -1.0F;
        this.constLimitHP = 3.99F;
        this.constLimitHN = 0.0F;
        this.constLimitWP = 1.49F;
        this.constLimitWN = -1.49F;
        this.init();
    }

    public void init() {
        this.posX = (double) this.blockX + 0.5D + (double) REVISION[this.direction & 7][0];
        this.posY = (double) this.blockY + (double) (this.height + 1) * 0.0625D;
        this.posZ = (double) this.blockZ + 0.5D + (double) REVISION[this.direction & 7][1];
    }

    public void addHeight(double par1) {
        int h2 = (int) (par1 / 0.0625D);
        this.height = (byte) (this.height + h2);
        this.init();
    }

    public static RailPosition readFromNBT(CompoundTag nbt) {
        int[] pos = NbtCompat.getIntArray(nbt, "BlockPos");
        if (pos.length < 3) return null;
        byte b0 = NbtCompat.getByte(nbt, "Direction");
        byte b2 = NbtCompat.getByte(nbt, "SwitchType");
        RailPosition rp = new RailPosition(pos[0], pos[1], pos[2], b0, b2);
        rp.setHeight(NbtCompat.getByte(nbt, "Height"));
        rp.anchorYaw = NbtCompat.getFloat(nbt, "A_Direction");
        rp.anchorPitch = NbtCompat.getFloat(nbt, "A_Pitch");
        rp.anchorLengthHorizontal = NbtCompat.getFloat(nbt, "A_Length");
        rp.anchorLengthVertical = NbtCompat.getFloat(nbt, "A_LenV");
        rp.cantCenter = NbtCompat.getFloat(nbt, "C_Center");
        rp.cantEdge = NbtCompat.getFloat(nbt, "C_Edge");
        rp.cantRandom = NbtCompat.getFloat(nbt, "C_Random");
        rp.constLimitHP = NbtCompat.getFloat(nbt, "Const_Limit_HP");
        rp.constLimitHN = NbtCompat.getFloat(nbt, "Const_Limit_HN");
        rp.constLimitWP = NbtCompat.getFloat(nbt, "Const_Limit_WP");
        rp.constLimitWN = NbtCompat.getFloat(nbt, "Const_Limit_WN");
        rp.init();
        return rp;
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putIntArray("BlockPos", new int[]{this.blockX, this.blockY, this.blockZ});
        nbt.putByte("SwitchType", this.switchType);
        nbt.putByte("Direction", this.direction);
        nbt.putByte("Height", this.height);
        nbt.putFloat("A_Direction", this.anchorYaw);
        nbt.putFloat("A_Pitch", this.anchorPitch);
        nbt.putFloat("A_Length", this.anchorLengthHorizontal);
        nbt.putFloat("A_LenV", this.anchorLengthVertical);
        nbt.putFloat("C_Center", this.cantCenter);
        nbt.putFloat("C_Edge", this.cantEdge);
        nbt.putFloat("C_Random", this.cantRandom);
        nbt.putFloat("Const_Limit_HP", this.constLimitHP);
        nbt.putFloat("Const_Limit_HN", this.constLimitHN);
        nbt.putFloat("Const_Limit_WP", this.constLimitWP);
        nbt.putFloat("Const_Limit_WN", this.constLimitWN);
        return nbt;
    }

    public void setHeight(byte par1) {
        this.height = par1;
        this.posY = (double) this.blockY + (double) (par1 + 1) * 0.0625D;
    }

    public BlockPos getNeighborBlockPos() {
        int x2 = CurveMath.floor(this.posX + (double) REVISION[this.direction & 7][0]);
        int y2 = this.blockY;
        int z2 = CurveMath.floor(this.posZ + (double) REVISION[this.direction & 7][1]);
        return new BlockPos(x2, y2, z2);
    }

    public RailDir getDir(RailPosition p1, RailPosition p2) {
        double dif1x = p1.posX - this.posX;
        double dif1z = p1.posZ - this.posZ;
        double dif2x = p2.posX - this.posX;
        double dif2z = p2.posZ - this.posZ;
        double cross = dif1z * dif2x - dif1x * dif2z;
        return cross > 0.0D ? RailDir.LEFT : cross < 0.0D ? RailDir.RIGHT : RailDir.NONE;
    }

    public boolean checkRSInput(Level level) {
        return level != null && level.getBestNeighborSignal(new BlockPos(this.blockX, this.blockY, this.blockZ)) > 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RailPosition other)) {
            return false;
        }
        return this.blockX == other.blockX && this.blockY == other.blockY && this.blockZ == other.blockZ
            && this.switchType == other.switchType;
    }

    @Override
    public int hashCode() {
        return this.blockX ^ this.blockZ << 8 ^ this.blockY << 16;
    }
}
