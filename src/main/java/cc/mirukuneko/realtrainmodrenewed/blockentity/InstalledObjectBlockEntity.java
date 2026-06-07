package cc.mirukuneko.realtrainmodrenewed.blockentity;

import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry;
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect;
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class InstalledObjectBlockEntity extends BlockEntity {
    private static final int TICKET_GATE_OPEN_TICKS = 60;
    private static final int TICKET_GATE_MOVE_TICKS = 12;
    private String definitionId = "";
    private String category = InstalledObjectCategory.LIGHT.name();
    private float yaw;
    // 壁(横面)挿し時に碍子を横倒しにするためのピッチ(度)。0=通常(縦置き)。
    private float mountPitch;
    private BlockPos wireStart;
    private BlockPos wireEnd;
    private boolean powered;
    private int barMoveCount;
    private int lightCount = -1;
    private int tickCountOnActive;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private int signalChannel = -1;
    private int signalAspect = SignalAspect.STOP.getId();
    // スピーカー: 音が聞こえる範囲(ブロック)。GUIで可変。
    private int speakerRange = 32;
    private final Map<String, String> scriptData = new HashMap<>();

    public InstalledObjectBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModRenewedBlockEntities.INSTALLED_OBJECT.get(), pos, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putString("DefinitionId", definitionId);
        tag.putString("Category", category);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("MountPitch", mountPitch);
        if (wireStart != null) {
            tag.putInt("WireStartX", wireStart.getX());
            tag.putInt("WireStartY", wireStart.getY());
            tag.putInt("WireStartZ", wireStart.getZ());
        }
        if (wireEnd != null) {
            tag.putInt("WireEndX", wireEnd.getX());
            tag.putInt("WireEndY", wireEnd.getY());
            tag.putInt("WireEndZ", wireEnd.getZ());
        }
        tag.putBoolean("Powered", powered);
        tag.putInt("BarMoveCount", barMoveCount);
        tag.putInt("LightCount", lightCount);
        tag.putInt("TickCountOnActive", tickCountOnActive);
        tag.putDouble("OffsetX", offsetX);
        tag.putDouble("OffsetY", offsetY);
        tag.putDouble("OffsetZ", offsetZ);
        tag.putInt("SignalChannel", signalChannel);
        tag.putInt("SignalAspect", signalAspect);
        tag.putInt("SpeakerRange", speakerRange);
        if (!scriptData.isEmpty()) {
            CompoundTag scriptDataTag = new CompoundTag();
            scriptData.forEach(scriptDataTag::putString);
            tag.store("ScriptData", CompoundTag.CODEC, scriptDataTag);
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        definitionId = tag.getStringOr("DefinitionId", "");
        category = tag.getStringOr("Category", InstalledObjectCategory.LIGHT.name());
        yaw = tag.getFloatOr("Yaw", 0.0F);
        mountPitch = tag.getFloatOr("MountPitch", 0.0F);
        wireStart = tag.getInt("WireStartX").isPresent()
            ? new BlockPos(tag.getIntOr("WireStartX", 0), tag.getIntOr("WireStartY", 0), tag.getIntOr("WireStartZ", 0))
            : null;
        wireEnd = tag.getInt("WireEndX").isPresent()
            ? new BlockPos(tag.getIntOr("WireEndX", 0), tag.getIntOr("WireEndY", 0), tag.getIntOr("WireEndZ", 0))
            : null;
        powered = tag.getBooleanOr("Powered", false);
        barMoveCount = tag.getIntOr("BarMoveCount", 0);
        lightCount = tag.getIntOr("LightCount", -1);
        tickCountOnActive = tag.getIntOr("TickCountOnActive", 0);
        offsetX = tag.getDoubleOr("OffsetX", 0.0D);
        offsetY = tag.getDoubleOr("OffsetY", 0.0D);
        offsetZ = tag.getDoubleOr("OffsetZ", 0.0D);
        signalChannel = tag.getIntOr("SignalChannel", -1);
        signalAspect = tag.getIntOr("SignalAspect", SignalAspect.STOP.getId());
        speakerRange = tag.getIntOr("SpeakerRange", 32);
        scriptData.clear();
        tag.read("ScriptData", CompoundTag.CODEC).ifPresent(scriptDataTag -> {
            for (String key : scriptDataTag.keySet()) {
                scriptData.put(key, scriptDataTag.getStringOr(key, ""));
            }
        });
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void setDefinition(String definitionId, InstalledObjectCategory category, float yaw) {
        this.definitionId = definitionId == null ? "" : definitionId;
        this.category = category == null ? InstalledObjectCategory.LIGHT.name() : category.name();
        this.yaw = yaw;
        if (category == InstalledObjectCategory.SIGNAL) {
            this.signalAspect = SignalAspect.STOP.getId();
        }
        setChanged();
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public InstalledObjectCategory getCategory() {
        try {
            return InstalledObjectCategory.valueOf(category);
        } catch (Exception e) {
            return InstalledObjectCategory.LIGHT;
        }
    }

    public float getYaw() {
        return yaw;
    }

    public float getMountPitch() {
        return mountPitch;
    }

    public void setMountPitch(float mountPitch) {
        this.mountPitch = mountPitch;
        setChanged();
    }

    public void setRenderOffset(double offsetX, double offsetY, double offsetZ) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        setChanged();
    }

    public Vec3 getRenderOffset() {
        return new Vec3(offsetX, offsetY, offsetZ);
    }

    public void setWireEndpoints(BlockPos start, BlockPos end) {
        this.wireStart = start;
        this.wireEnd = end;
        setChanged();
    }

    public BlockPos getWireStart() {
        return wireStart;
    }

    public BlockPos getWireEnd() {
        return wireEnd;
    }

    public Vec3 getRenderCenter() {
        return Vec3.atCenterOf(getBlockPos());
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            setChanged();
        }
    }

    public boolean isPowered() {
        return powered;
    }

    public int getBarMoveCount() {
        return barMoveCount;
    }

    public boolean isTicketGateOpen() {
        return getCategory() == InstalledObjectCategory.TICKET_GATE && powered;
    }

    public void activateTicketGate() {
        if (getCategory() != InstalledObjectCategory.TICKET_GATE) {
            return;
        }
        powered = true;
        tickCountOnActive = 0;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getLightCount() {
        if (isSignal()) {
            return getLegacySignalState();
        }
        return lightCount;
    }

    public boolean isSignal() {
        return getCategory() == InstalledObjectCategory.SIGNAL;
    }

    public boolean isSpeaker() {
        return getCategory() == InstalledObjectCategory.SPEAKER;
    }

    /** スピーカーが音を鳴らす範囲(ブロック)。 */
    public int getSpeakerRange() {
        return speakerRange;
    }

    public void setSpeakerRange(int range) {
        this.speakerRange = Math.max(1, Math.min(256, range));
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Legacy RTM signal scripts read the current aspect through entity.getSignal().
     */
    public int getSignal() {
        return Math.max(0, getLegacySignalState());
    }

    /**
     * Legacy RTM signal scripts query a resource-state wrapper to read config values.
     */
    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    /**
     * Legacy 1.7-style scripts sometimes access modelSet directly.
     */
    public ModelSetCompat getModelSet() {
        return new ModelSetCompat(this);
    }

    /**
     * The installed-object renderer already applies block yaw before the script runs,
     * so returning zero here avoids rotating scripted signals twice.
     */
    public float getRotation() {
        return 0.0F;
    }

    /**
     * Slanted placement is not implemented for installed objects yet, so the block
     * direction is exposed as zero for script compatibility.
     */
    public float getBlockDirection() {
        return 0.0F;
    }

    public int getSignalChannel() {
        return signalChannel;
    }

    public void setSignalChannel(int signalChannel, boolean updateClient) {
        this.signalChannel = signalChannel;
        setChanged();
        if (updateClient && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public SignalAspect getSignalAspect() {
        return SignalAspect.byId(signalAspect);
    }

    /**
     * RTM signal scripts use sparse numeric states rather than the compact UI ids.
     */
    public int getLegacySignalState() {
        return getSignalAspect().getLegacyValue();
    }

    public void setSignalAspect(SignalAspect aspect, boolean updateClient) {
        this.signalAspect = aspect == null ? SignalAspect.STOP.getId() : aspect.getId();
        setChanged();
        if (updateClient && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String getModelName() {
        int index = definitionId.lastIndexOf(':');
        return index >= 0 ? definitionId.substring(index + 1) : definitionId;
    }

    // ---- Legacy RTM script position/direction accessors ----

    public int getX() { return worldPosition.getX(); }
    public int getY() { return worldPosition.getY(); }
    public int getZ() { return worldPosition.getZ(); }

    /** 0–3 facing direction derived from yaw (0=south,1=west,2=north,3=east). */
    public int getDir() {
        int d = Math.floorMod(Math.round(yaw / 90.0F), 4);
        return d;
    }

    /** Whether this block is connected to a neighbor on the given RTM side (0–5). Always false stub. */
    public boolean isConnected(int side) { return false; }

    /** Side index this block is attached to (0=down,1=up,2-5=NSWE). Returns 1 (attached to ground). */
    public int getAttachedSide() { return 1; }

    /** Random decorative scale factor (used by RenderPalm etc.). */
    public float getRandomScale() { return 1.0F; }

    private InstalledObjectDefinition getDefinition() {
        return InstalledObjectRegistry.getById(definitionId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && isSignal()) {
            SignalNetworkSavedData.get(serverLevel).syncLoadedSignal(serverLevel, this);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InstalledObjectBlockEntity be) {
        if (level.isClientSide()) {
            // sound_Running を持つ設置オブジェクト(スピーカー/サイレン/踏切など)は種別を問わず、
            // powered の間ループ再生する。実際の再生可否(powered・音名)は CrossingGateSoundManager 側で判定。
            InstalledObjectDefinition definition = be.getDefinition();
            String running = definition == null ? null : definition.getRunningSound();
            if (running != null && !running.isBlank()) {
                ClientHooks.tickCrossingGateSound(be);
            } else {
                ClientHooks.stopCrossingGateSound(level, pos);
            }
            return;
        }
        if (be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
            boolean changed = false;
            if (be.powered) {
                if (be.barMoveCount < 90) {
                    be.barMoveCount = Math.min(90, be.barMoveCount + Math.max(1, 90 / TICKET_GATE_MOVE_TICKS));
                    changed = true;
                }
                be.tickCountOnActive++;
                if (be.tickCountOnActive >= TICKET_GATE_OPEN_TICKS) {
                    be.powered = false;
                    be.tickCountOnActive = 0;
                    changed = true;
                }
            } else {
                if (be.barMoveCount > 0) {
                    be.barMoveCount = Math.max(0, be.barMoveCount - Math.max(1, 90 / TICKET_GATE_MOVE_TICKS));
                    changed = true;
                }
            }
            if (changed) {
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }
        // サーバー側: 遮断桿アニメ等は踏切ロジック対象のみ
        if (!be.shouldHandleCrossingLogic()) {
            return;
        }
        // 踏切のレッドストーン受信を毎tick再評価する。neighborChanged の取りこぼしや
        // ワールド再読込・ワイヤ隣接(hasNeighborSignal が拾いにくいケース)に強くするため、
        // getBestNeighborSignal(>0)で判定して powered を常に信号と同期させる。
        boolean redstone = level.getBestNeighborSignal(pos) > 0;
        boolean changed = false;
        if (be.powered != redstone) {
            be.powered = redstone;
            changed = true;
        }
        if (be.powered) {
            if (be.barMoveCount < 90) {
                be.barMoveCount++;
                changed = true;
            }
            be.tickCountOnActive = (be.tickCountOnActive + 1) % 360;
            int previousLight = be.lightCount;
            if (be.lightCount < 0) {
                be.lightCount = 0;
            } else if (be.tickCountOnActive % 10 == 0) {
                be.lightCount = (be.lightCount + 1) % 2;
            }
            changed |= previousLight != be.lightCount;
        } else {
            if (be.barMoveCount > 0) {
                be.barMoveCount--;
                changed = true;
            }
            if (be.tickCountOnActive != 0 || be.lightCount != -1) {
                be.tickCountOnActive = 0;
                be.lightCount = -1;
                changed = true;
            }
        }
        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public static final class ResourceStateCompat {
        private final InstalledObjectBlockEntity blockEntity;

        public ResourceStateCompat(InstalledObjectBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        public ModelSetCompat getResourceSet() {
            return new ModelSetCompat(blockEntity);
        }

        public DataMapCompat getDataMap() {
            return new DataMapCompat(blockEntity);
        }

        public String getResourceName() {
            return blockEntity == null ? "" : blockEntity.getModelName();
        }
    }

    public static final class DataMapCompat {
        private final InstalledObjectBlockEntity blockEntity;
        private final Map<String, Object> values = new HashMap<>();

        public DataMapCompat(InstalledObjectBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
            refresh();
        }

        public boolean contains(String key) {
            refresh();
            return values.containsKey(key) || blockEntity != null && blockEntity.scriptData.containsKey(key);
        }

        public Object get(String key) {
            refresh();
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
            return blockEntity == null ? null : blockEntity.scriptData.get(key);
        }

        public int getInt(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1 : 0;
            }
            if (value instanceof String string) {
                try {
                    return Integer.decode(string);
                } catch (NumberFormatException ignored) {
                    try {
                        return (int) Math.round(Double.parseDouble(string));
                    } catch (NumberFormatException ignoredAgain) {
                    }
                }
            }
            return 0;
        }

        public int getHex(String key) {
            return getInt(key);
        }

        public double getDouble(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1.0D : 0.0D;
            }
            if (value instanceof String string) {
                try {
                    return Double.parseDouble(string);
                } catch (NumberFormatException ignored) {
                }
            }
            return 0.0D;
        }

        public boolean getBoolean(String key) {
            Object value = get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            if (value instanceof String string) {
                return Boolean.parseBoolean(string) || "1".equals(string);
            }
            return false;
        }

        public String getString(String key) {
            Object value = get(key);
            return value == null ? "" : String.valueOf(value);
        }

        public void setBoolean(String key, boolean value, int syncType) {
            values.put(key, value);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, Boolean.toString(value));
                blockEntity.setChanged();
            }
        }

        public void setInt(String key, int value, int syncType) {
            values.put(key, value);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, Integer.toString(value));
                blockEntity.setChanged();
            }
        }

        public void setDouble(String key, double value, int syncType) {
            values.put(key, value);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, Double.toString(value));
                blockEntity.setChanged();
            }
        }

        public void setString(String key, String value, int syncType) {
            String safeValue = value == null ? "" : value;
            values.put(key, safeValue);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, safeValue);
                blockEntity.setChanged();
            }
        }

        private void refresh() {
            if (blockEntity == null) {
                return;
            }
            values.put("powered", blockEntity.isPowered() ? 1 : 0);
            values.put("isPowered", blockEntity.isPowered());
            values.put("barMoveCount", blockEntity.getBarMoveCount());
            values.put("lightCount", blockEntity.getLightCount());
            values.put("signal", blockEntity.getSignal());
            values.put("signalAspect", blockEntity.getLegacySignalState());
            values.put("yaw", blockEntity.getYaw());
            blockEntity.scriptData.forEach(values::putIfAbsent);
        }
    }

    public static final class ModelSetCompat {
        private final InstalledObjectBlockEntity blockEntity;

        public ModelSetCompat(InstalledObjectBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        public String getName() {
            return blockEntity == null ? "" : blockEntity.getModelName();
        }

        public String getModelName() {
            return getName();
        }

        public String getTextureName() {
            return getName();
        }

        public ConfigCompat getConfig() {
            return new ConfigCompat(blockEntity);
        }
    }

    public static final class ConfigCompat {
        public final float[] offset;
        public final ModelPartsBodyCompat modelPartsBody;

        public ConfigCompat(InstalledObjectBlockEntity blockEntity) {
            InstalledObjectDefinition definition = blockEntity == null ? null : blockEntity.getDefinition();
            Vec3 modelOffset = definition == null ? Vec3.ZERO : definition.getModelOffset();
            Vec3 scriptBodyPos = definition == null ? Vec3.ZERO : definition.getScriptBodyPos();
            this.offset = new float[] {(float) modelOffset.x, (float) modelOffset.y, (float) modelOffset.z};
            this.modelPartsBody = new ModelPartsBodyCompat(scriptBodyPos);
        }
    }

    public static final class ModelPartsBodyCompat {
        public final float[] pos;

        public ModelPartsBodyCompat(Vec3 pos) {
            this.pos = new float[] {(float) pos.x, (float) pos.y, (float) pos.z};
        }
    }

    private boolean shouldHandleCrossingLogic() {
        if (getCategory() == InstalledObjectCategory.CROSSING) {
            return true;
        }
        InstalledObjectDefinition definition = getDefinition();
        if (definition == null) {
            return false;
        }
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase(java.util.Locale.ROOT);
        String name = definition.getDisplayName() == null ? "" : definition.getDisplayName().toLowerCase(java.util.Locale.ROOT);
        String model = definition.getModelFile() == null ? "" : definition.getModelFile().toLowerCase(java.util.Locale.ROOT);
        String sound = definition.getRunningSound() == null ? "" : definition.getRunningSound().toLowerCase(java.util.Locale.ROOT);
        return id.contains("crossing")
            || id.contains("fumikiri")
            || name.contains("crossing")
            || name.contains("fumikiri")
            || model.contains("crossing")
            || model.contains("fumikiri")
            || sound.contains("crossing")
            || sound.contains("fumikiri")
            || sound.contains("toryanse");
    }
}
