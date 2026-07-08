package cc.mirukuneko.realtrainmodrenewed.rail.util

import cc.mirukuneko.realtrainmodrenewed.rail.math.CurveMath
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level

/**
 * Port of jp.legacy.legacy.rail.util.RailPosition (subset used by RealTrainModRenewed rails).
 */
class RailPosition(
    @JvmField var blockX: Int,
    @JvmField var blockY: Int,
    @JvmField var blockZ: Int,
    dir: Int,
    @JvmField val switchType: Byte
) {
    companion object {
        @JvmField
        val REVISION: Array<FloatArray> = arrayOf(
            floatArrayOf(0.0F, -0.5F), floatArrayOf(-0.5F, -0.5F), floatArrayOf(-0.5F, 0.0F), floatArrayOf(-0.5F, 0.499999F),
            floatArrayOf(0.0F, 0.499999F), floatArrayOf(0.499999F, 0.499999F), floatArrayOf(0.499999F, 0.0F), floatArrayOf(0.499999F, -0.5F)
        )

        @JvmStatic
        fun readFromNBT(nbt: CompoundTag): RailPosition? {
            val pos = NbtCompat.getIntArray(nbt, "BlockPos")
            if (pos.size < 3) return null
            val b0 = NbtCompat.getByte(nbt, "Direction")
            val b2 = NbtCompat.getByte(nbt, "SwitchType")
            val rp = RailPosition(pos[0], pos[1], pos[2], b0.toInt(), b2)
            rp.setHeight(NbtCompat.getByte(nbt, "Height"))
            rp.anchorYaw = NbtCompat.getFloat(nbt, "A_Direction")
            rp.anchorPitch = NbtCompat.getFloat(nbt, "A_Pitch")
            rp.anchorLengthHorizontal = NbtCompat.getFloat(nbt, "A_Length")
            rp.anchorLengthVertical = NbtCompat.getFloat(nbt, "A_LenV")
            rp.cantCenter = NbtCompat.getFloat(nbt, "C_Center")
            rp.cantEdge = NbtCompat.getFloat(nbt, "C_Edge")
            rp.cantRandom = NbtCompat.getFloat(nbt, "C_Random")
            rp.constLimitHP = NbtCompat.getFloat(nbt, "Const_Limit_HP")
            rp.constLimitHN = NbtCompat.getFloat(nbt, "Const_Limit_HN")
            rp.constLimitWP = NbtCompat.getFloat(nbt, "Const_Limit_WP")
            rp.constLimitWN = NbtCompat.getFloat(nbt, "Const_Limit_WN")
            // Restore precise position overrides before init() so copy-preview offsets survive load.
            // NbtCompat.getDouble returns 0.0 for missing keys; the contains guard ensures we only override when present.
            if (nbt.contains("PosX")) rp.precisePosX = NbtCompat.getDouble(nbt, "PosX")
            if (nbt.contains("PosY")) rp.precisePosY = NbtCompat.getDouble(nbt, "PosY")
            if (nbt.contains("PosZ")) rp.precisePosZ = NbtCompat.getDouble(nbt, "PosZ")
            rp.init()
            return rp
        }
    }

    @JvmField var direction: Byte = dir.toByte()
    var height: Byte = 0
        private set
    @JvmField var anchorYaw: Float = CurveMath.wrapAngle(dir.toFloat() * 45.0F)
    @JvmField var anchorPitch: Float = 0f
    @JvmField var anchorLengthHorizontal: Float = -1.0F
    @JvmField var anchorLengthVertical: Float = 0f
    @JvmField var cantCenter: Float = 0f
    @JvmField var cantEdge: Float = 0f
    @JvmField var cantRandom: Float = 0f
    @JvmField var constLimitHP: Float = 3.99F
    @JvmField var constLimitHN: Float = 0.0F
    @JvmField var constLimitWP: Float = 1.49F
    @JvmField var constLimitWN: Float = -1.49F
    @JvmField var posX: Double = 0.0
    @JvmField var posY: Double = 0.0
    @JvmField var posZ: Double = 0.0
    @JvmField var precisePosX: Double? = null
    @JvmField var precisePosY: Double? = null
    @JvmField var precisePosZ: Double? = null

    init {
        init()
    }

    constructor(blockX: Int, blockY: Int, blockZ: Int, dir: Int, switchType: Int) :
        this(blockX, blockY, blockZ, dir, switchType.toByte())

    fun init() {
        posX = precisePosX ?: (blockX.toDouble() + 0.5 + REVISION[direction.toInt() and 7][0].toDouble())
        posY = precisePosY ?: (blockY.toDouble() + (height + 1).toDouble() * 0.0625)
        posZ = precisePosZ ?: (blockZ.toDouble() + 0.5 + REVISION[direction.toInt() and 7][1].toDouble())
    }

    fun addHeight(par1: Double) {
        val h2 = (par1 / 0.0625).toInt()
        height = (height + h2).toByte()
        if (precisePosY != null) {
            // Preserve the existing sub-block Y offset by adding the delta directly.
            precisePosY = precisePosY!! + par1
            posY = precisePosY!!
        }
        init()
    }

    fun writeToNBT(): CompoundTag {
        val nbt = CompoundTag()
        nbt.putIntArray("BlockPos", intArrayOf(blockX, blockY, blockZ))
        nbt.putByte("SwitchType", switchType)
        nbt.putByte("Direction", direction)
        nbt.putByte("Height", height)
        nbt.putFloat("A_Direction", anchorYaw)
        nbt.putFloat("A_Pitch", anchorPitch)
        nbt.putFloat("A_Length", anchorLengthHorizontal)
        nbt.putFloat("A_LenV", anchorLengthVertical)
        nbt.putFloat("C_Center", cantCenter)
        nbt.putFloat("C_Edge", cantEdge)
        nbt.putFloat("C_Random", cantRandom)
        nbt.putFloat("Const_Limit_HP", constLimitHP)
        nbt.putFloat("Const_Limit_HN", constLimitHN)
        nbt.putFloat("Const_Limit_WP", constLimitWP)
        nbt.putFloat("Const_Limit_WN", constLimitWN)
        // Persist precise position overrides so copy-preview offsets survive save/load.
        precisePosX?.let { nbt.putDouble("PosX", it) }
        precisePosY?.let { nbt.putDouble("PosY", it) }
        precisePosZ?.let { nbt.putDouble("PosZ", it) }
        return nbt
    }

    fun setHeight(par1: Byte) {
        // Preserve the existing sub-block fractional Y offset when a precise override is active.
        // Without this, setHeight would snap precisePosY to the canonical Y at the new height,
        // destroying any exact offset from copy-paste preview or other precise placement.
        val oldHeight = height
        height = par1
        if (precisePosY != null) {
            val oldCanonicalY = blockY.toDouble() + (oldHeight + 1).toDouble() * 0.0625
            val offset = precisePosY!! - oldCanonicalY
            precisePosY = blockY.toDouble() + (par1 + 1).toDouble() * 0.0625 + offset
            posY = precisePosY!!
        } else {
            posY = blockY.toDouble() + (par1 + 1).toDouble() * 0.0625
        }
    }

    fun getNeighborBlockPos(): BlockPos {
        val x2 = CurveMath.floor(posX + REVISION[direction.toInt() and 7][0].toDouble())
        val y2 = blockY
        val z2 = CurveMath.floor(posZ + REVISION[direction.toInt() and 7][1].toDouble())
        return BlockPos(x2, y2, z2)
    }

    fun getDir(p1: RailPosition, p2: RailPosition): RailDir {
        val dif1x = p1.posX - posX
        val dif1z = p1.posZ - posZ
        val dif2x = p2.posX - posX
        val dif2z = p2.posZ - posZ
        val cross = dif1z * dif2x - dif1x * dif2z
        return when {
            cross > 0.0 -> RailDir.LEFT
            cross < 0.0 -> RailDir.RIGHT
            else -> RailDir.NONE
        }
    }

    fun checkRSInput(level: Level?): Boolean =
        level != null && level.getBestNeighborSignal(BlockPos(blockX, blockY, blockZ)) > 0

    override fun equals(other: Any?): Boolean {
        if (other !is RailPosition) return false
        return blockX == other.blockX && blockY == other.blockY && blockZ == other.blockZ
            && switchType == other.switchType
    }

    override fun hashCode(): Int = blockX xor (blockZ shl 8) xor (blockY shl 16)
}