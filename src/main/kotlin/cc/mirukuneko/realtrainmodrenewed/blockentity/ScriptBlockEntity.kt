package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.block.ScriptBlock
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class ScriptBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.SCRIPT_BLOCK.get(), pos, state) {

    var script: String = ""
        private set
    var lastError: String = ""
        private set
    var runOnRedstone: Boolean = true
    var powered: Boolean = false
        private set

    companion object {
        @JvmStatic
        fun serverTick(level: ServerLevel, pos: BlockPos, state: BlockState, be: ScriptBlockEntity) {
            val hasSignal = level.hasNeighborSignal(pos)
            if (state.hasProperty(ScriptBlock.POWERED) && state.getValue(ScriptBlock.POWERED) != hasSignal)
                level.setBlock(pos, state.setValue(ScriptBlock.POWERED, hasSignal), 3)
            be.powered = hasSignal
        }
    }

    fun onNeighborSignalChanged(hasSignal: Boolean) {
        val risingEdge = !powered && hasSignal
        powered = hasSignal
        if (level is ServerLevel && runOnRedstone && risingEdge) runScript(level as ServerLevel)
    }

    fun setScript(script: String) { this.script = script; setChanged() }
    fun setRunOnRedstone(run: Boolean) { runOnRedstone = run; setChanged() }

    fun runScript(level: ServerLevel): Boolean {
        val success = TrainScriptSystem.getInstance().executeBlockScript(level, worldPosition, script, powered, null)
        lastError = if (success) "" else "Script error"
        setChanged()
        return success
    }

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putString("Script", script)
        tag.putString("LastError", lastError)
        tag.putBoolean("RunOnRedstone", runOnRedstone)
        tag.putBoolean("Powered", powered)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        script = tag.getStringOr("Script", "")
        lastError = tag.getStringOr("LastError", "")
        runOnRedstone = tag.getBooleanOr("RunOnRedstone", true)
        powered = tag.getBooleanOr("Powered", false)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}
