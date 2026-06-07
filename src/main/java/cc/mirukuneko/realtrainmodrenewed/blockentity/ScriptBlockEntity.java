package cc.mirukuneko.realtrainmodrenewed.blockentity;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.block.ScriptBlock;
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ScriptBlockEntity extends BlockEntity {
    private String script = "";
    private String lastError = "";
    private boolean runOnRedstone = true;
    private boolean powered;

    public ScriptBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModRenewedBlockEntities.SCRIPT_BLOCK.get(), pos, blockState);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ScriptBlockEntity blockEntity) {
        boolean hasSignal = level.hasNeighborSignal(pos);
        if (state.hasProperty(ScriptBlock.POWERED) && state.getValue(ScriptBlock.POWERED) != hasSignal) {
            level.setBlock(pos, state.setValue(ScriptBlock.POWERED, hasSignal), 3);
        }
        blockEntity.powered = hasSignal;
    }

    public void onNeighborSignalChanged(boolean hasSignal) {
        boolean risingEdge = !powered && hasSignal;
        powered = hasSignal;
        if (level instanceof ServerLevel serverLevel && runOnRedstone && risingEdge) {
            runScript(serverLevel);
        }
    }

    public boolean runScript(ServerLevel level) {
        boolean success = TrainScriptSystem.getInstance().executeBlockScript(level, worldPosition, script, powered, null);
        lastError = success ? "" : "Script error";
        setChanged();
        return success;
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putString("Script", script);
        tag.putString("LastError", lastError);
        tag.putBoolean("RunOnRedstone", runOnRedstone);
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        script = tag.getStringOr("Script", "");
        lastError = tag.getStringOr("LastError", "");
        runOnRedstone = tag.getBooleanOr("RunOnRedstone", true);
        powered = tag.getBooleanOr("Powered", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public String getScript() {
        return script;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isRunOnRedstone() {
        return runOnRedstone;
    }

    public void configure(String script, boolean runOnRedstone) {
        this.script = script == null ? "" : script;
        this.runOnRedstone = runOnRedstone;
        setChanged();
    }
}
