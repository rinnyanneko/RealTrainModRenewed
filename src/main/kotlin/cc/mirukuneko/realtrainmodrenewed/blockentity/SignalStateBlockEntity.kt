package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.block.SignalStateBlock
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class SignalStateBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.SIGNAL_STATE.get(), pos, state) {

    var linkedChannel: Int = -1
    var aspectId: Int = SignalAspect.STOP.id

    companion object {
        @JvmStatic
        fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState, be: SignalStateBlockEntity) {
            if ((level.gameTime + pos.asLong()) % 5L != 0L) return
            val channel = if (be.linkedChannel > 0) be.linkedChannel else be.findAdjacentSourceSignalChannel()
            val aspect = if (channel > 0) SignalNetworkSavedData.get(level).getAspect(channel) else SignalAspect.STOP
            be.updateState(level, pos, state, channel, aspect)
        }
    }

    private fun updateState(level: ServerLevel, pos: BlockPos, state: BlockState, channel: Int, aspect: SignalAspect) {
        val nextId = aspect.id
        val changed = aspectId != nextId
        aspectId = nextId
        applyAspectToAttachedSignals(aspect)
        if (!changed) return
        if (state.hasProperty(SignalStateBlock.ASPECT) && state.getValue(SignalStateBlock.ASPECT) != nextId)
            level.setBlock(pos, state.setValue(SignalStateBlock.ASPECT, nextId), 3)
        setChanged()
        level.sendBlockUpdated(pos, blockState, blockState, 3)
    }

    private fun findAdjacentSourceSignalChannel(): Int {
        if (level == null) return -1
        for (dir in Direction.entries) {
            val be = level!!.getBlockEntity(worldPosition.relative(dir))
            if (be is InstalledObjectBlockEntity && be.isSignal && be.signalChannel > 0)
                return be.signalChannel
        }
        return -1
    }

    private fun applyAspectToAttachedSignals(aspect: SignalAspect) {
        if (level == null) return
        for (dir in Direction.entries) {
            val be = level!!.getBlockEntity(worldPosition.relative(dir))
            if (be is InstalledObjectBlockEntity && be.isSignal) be.setSignalAspect(aspect, true)
        }
    }

    fun setLinkedChannel(channel: Int) { linkedChannel = channel; setChanged() }

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putInt("LinkedChannel", linkedChannel)
        tag.putInt("AspectId", aspectId)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        linkedChannel = tag.getIntOr("LinkedChannel", -1)
        aspectId = tag.getIntOr("AspectId", SignalAspect.STOP.id)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}
