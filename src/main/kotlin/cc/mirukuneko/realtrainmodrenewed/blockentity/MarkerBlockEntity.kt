package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class MarkerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.MARKER.get(), pos, state) {

    private var configured: Boolean = false
    var anchorYaw: Float = 0f; private set
    var anchorPitch: Float = 0f; private set
    var anchorLengthHorizontal: Float = -1.0f; private set
    var anchorLengthVertical: Float = 0f; private set
    var cantCenter: Float = 0f; private set
    var cantEdge: Float = 0f; private set
    var cantRandom: Float = 0f; private set

    val markerRP: RailPosition?
        get() {
            if (level == null) return null
            val st = blockState
            if (st.block !is MarkerBlock) return null
            val facing = st.getValue(MarkerBlock.FACING)
            val dir = MarkerBlock.getMarkerDir(facing)
            val sw = (st.block as MarkerBlock).isSwitch
            val rp = RailPosition(blockPos.x, blockPos.y, blockPos.z, dir, if (sw) 1.toByte() else 0.toByte())
            if (configured) {
                rp.anchorYaw = anchorYaw; rp.anchorPitch = anchorPitch
                rp.anchorLengthHorizontal = anchorLengthHorizontal; rp.anchorLengthVertical = anchorLengthVertical
                rp.cantCenter = cantCenter; rp.cantEdge = cantEdge; rp.cantRandom = cantRandom
                rp.init()
            }
            return rp
        }

    fun configure(anchorYaw: Float, anchorPitch: Float, anchorLenH: Float, anchorLenV: Float,
                  cantCenter: Float, cantEdge: Float, cantRandom: Float) {
        configured = true
        this.anchorYaw = anchorYaw; this.anchorPitch = anchorPitch
        this.anchorLengthHorizontal = anchorLenH; this.anchorLengthVertical = anchorLenV
        this.cantCenter = cantCenter; this.cantEdge = cantEdge; this.cantRandom = cantRandom
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, 3)
    }

    fun isConfigured(): Boolean = configured
    private fun defaultAnchorYaw(): Float = markerRP?.anchorYaw ?: 0f

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putBoolean("Configured", configured)
        if (configured) {
            tag.putFloat("AnchorYaw", anchorYaw); tag.putFloat("AnchorPitch", anchorPitch)
            tag.putFloat("AnchorLenH", anchorLengthHorizontal); tag.putFloat("AnchorLenV", anchorLengthVertical)
            tag.putFloat("CantCenter", cantCenter); tag.putFloat("CantEdge", cantEdge); tag.putFloat("CantRandom", cantRandom)
        }
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        configured = tag.getBooleanOr("Configured", false)
        if (configured) {
            anchorYaw = tag.getFloatOr("AnchorYaw", 0f); anchorPitch = tag.getFloatOr("AnchorPitch", 0f)
            anchorLengthHorizontal = tag.getFloatOr("AnchorLenH", -1.0f); anchorLengthVertical = tag.getFloatOr("AnchorLenV", 0f)
            cantCenter = tag.getFloatOr("CantCenter", 0f); cantEdge = tag.getFloatOr("CantEdge", 0f); cantRandom = tag.getFloatOr("CantRandom", 0f)
        }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}
