package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.block.TrainDetectorBlock
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import kotlin.math.max

class TrainDetectorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.TRAIN_DETECTOR.get(), pos, state) {

    companion object {
        private const val DEFAULT_RANGE = 3
        private const val MAX_RANGE = 64
        private const val RELEASE_DELAY_TICKS = 40

        @JvmStatic
        fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState, be: TrainDetectorBlockEntity) {
            if ((level.gameTime + pos.asLong()) % 5L != 0L) return
            val range = max(1.0, be.detectionRange.toDouble())
            val box = AABB(pos).inflate(range, 3.0, range)
            val occupiedNow = level.getEntitiesOfClass(TrainEntity::class.java, box) { it.isAlive }.isNotEmpty()
            if (occupiedNow) be.releaseCooldown = RELEASE_DELAY_TICKS
            else if (be.releaseCooldown > 0) be.releaseCooldown--
            be.setOccupied(level, pos, state, occupiedNow || be.releaseCooldown > 0)
        }
    }

    var linkedChannel: Int = -1
    var detectionRange: Int = DEFAULT_RANGE
        set(value) { field = value.coerceIn(1, MAX_RANGE) }
    var occupied: Boolean = false
        private set
    var releaseCooldown: Int = 0

    fun isOccupied(): Boolean = occupied

    private fun setOccupied(level: ServerLevel, pos: BlockPos, state: BlockState, occupiedNow: Boolean) {
        if (occupied == occupiedNow && state.getValue(TrainDetectorBlock.POWERED) == occupiedNow) return
        occupied = occupiedNow
        if (state.hasProperty(TrainDetectorBlock.POWERED) && state.getValue(TrainDetectorBlock.POWERED) != occupiedNow) {
            level.setBlock(pos, state.setValue(TrainDetectorBlock.POWERED, occupiedNow), 3)
            level.updateNeighborsAt(pos, state.block)
        }
        if (linkedChannel > 0)
            SignalNetworkSavedData.get(level).setAspect(level.server, linkedChannel, if (occupiedNow) SignalAspect.STOP else SignalAspect.PROCEED)
        setChanged()
    }

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putInt("LinkedChannel", linkedChannel)
        tag.putInt("DetectionRange", detectionRange)
        tag.putBoolean("Occupied", occupied)
        tag.putInt("ReleaseCooldown", releaseCooldown)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        linkedChannel = tag.getIntOr("LinkedChannel", -1)
        detectionRange = tag.getIntOr("DetectionRange", DEFAULT_RANGE).coerceIn(1, MAX_RANGE)
        occupied = tag.getBooleanOr("Occupied", false)
        releaseCooldown = max(0, tag.getIntOr("ReleaseCooldown", 0))
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}
