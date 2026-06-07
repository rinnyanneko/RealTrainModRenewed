package cc.mirukuneko.realtrainmodrenewed.signal;

import com.mojang.serialization.Codec;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat;
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * 信号番号と現示をワールド保存するテーブルです。
 * 対象チャンクが読み込まれていない時も、次回ロード時に最新状態へ戻せます。
 */
public class SignalNetworkSavedData extends SavedData {
    private static final Codec<SignalNetworkSavedData> CODEC = CompoundTag.CODEC.xmap(
        SignalNetworkSavedData::load,
        SignalNetworkSavedData::saveTag
    );
    private static final SavedDataType<SignalNetworkSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "signal_network"),
        SignalNetworkSavedData::new,
        CODEC,
        DataFixTypes.LEVEL
    );

    private final Map<Integer, SignalEntry> entries = new HashMap<>();
    private int nextChannel = 1000;

    public static SignalNetworkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static SignalNetworkSavedData load(CompoundTag tag) {
        SignalNetworkSavedData data = new SignalNetworkSavedData();
        data.nextChannel = Math.max(1000, NbtCompat.getInt(tag, "NextChannel"));
        ListTag list = NbtCompat.getList(tag, "Entries");
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag entryTag)) {
                continue;
            }
            int channel = NbtCompat.getInt(entryTag, "Channel");
            String dimensionId = NbtCompat.getString(entryTag, "Dimension");
            SignalAspect aspect = SignalAspect.byId(NbtCompat.getInt(entryTag, "Aspect"));
            BlockPos pos = new BlockPos(NbtCompat.getInt(entryTag, "X"), NbtCompat.getInt(entryTag, "Y"), NbtCompat.getInt(entryTag, "Z"));
            if (channel > 0 && !dimensionId.isBlank()) {
                data.entries.put(channel, new SignalEntry(dimensionId, pos, aspect));
            }
        }
        return data;
    }

    private CompoundTag saveTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("NextChannel", nextChannel);
        ListTag list = new ListTag();
        for (Map.Entry<Integer, SignalEntry> entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("Channel", entry.getKey());
            entryTag.putString("Dimension", entry.getValue().dimensionId());
            entryTag.putInt("X", entry.getValue().pos().getX());
            entryTag.putInt("Y", entry.getValue().pos().getY());
            entryTag.putInt("Z", entry.getValue().pos().getZ());
            entryTag.putInt("Aspect", entry.getValue().aspect().getId());
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }

    public int assignNewChannel(ServerLevel level, BlockPos pos, int previousChannel, SignalAspect currentAspect) {
        if (previousChannel > 0) {
            entries.remove(previousChannel);
        }
        int channel = nextChannel++;
        entries.put(channel, new SignalEntry(dimensionId(level), pos.immutable(), currentAspect));
        setDirty();
        applyAspectIfLoaded(level.getServer(), channel);
        return channel;
    }

    public boolean hasChannel(int channel) {
        return entries.containsKey(channel);
    }

    public SignalAspect getAspect(int channel) {
        SignalEntry entry = entries.get(channel);
        return entry == null ? SignalAspect.STOP : entry.aspect();
    }

    public void setAspect(MinecraftServer server, int channel, SignalAspect aspect) {
        SignalEntry entry = entries.get(channel);
        if (entry == null) {
            return;
        }
        entries.put(channel, new SignalEntry(entry.dimensionId(), entry.pos(), aspect));
        setDirty();
        applyAspectIfLoaded(server, channel);
    }

    public void removeSignal(ServerLevel level, BlockPos pos, int channel) {
        SignalEntry entry = entries.get(channel);
        if (entry == null) {
            return;
        }
        if (entry.pos().equals(pos) && entry.dimensionId().equals(dimensionId(level))) {
            entries.remove(channel);
            setDirty();
        }
    }

    public void syncLoadedSignal(ServerLevel level, InstalledObjectBlockEntity blockEntity) {
        int channel = blockEntity.getSignalChannel();
        if (channel <= 0) {
            return;
        }
        SignalEntry entry = entries.get(channel);
        if (entry == null) {
            return;
        }
        if (!entry.dimensionId().equals(dimensionId(level)) || !entry.pos().equals(blockEntity.getBlockPos())) {
            return;
        }
        blockEntity.setSignalAspect(entry.aspect(), false);
    }

    private void applyAspectIfLoaded(MinecraftServer server, int channel) {
        SignalEntry entry = entries.get(channel);
        if (entry == null) {
            return;
        }
        Identifier dimensionLocation = Identifier.tryParse(entry.dimensionId());
        if (dimensionLocation == null) {
            return;
        }
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        ServerLevel targetLevel = server.getLevel(levelKey);
        if (targetLevel == null || !targetLevel.hasChunkAt(entry.pos())) {
            return;
        }
        if (targetLevel.getBlockEntity(entry.pos()) instanceof InstalledObjectBlockEntity blockEntity) {
            blockEntity.setSignalAspect(entry.aspect(), true);
        }
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private record SignalEntry(String dimensionId, BlockPos pos, SignalAspect aspect) {
    }
}

