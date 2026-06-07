package cc.mirukuneko.realtrainmodrenewed.compat.webctc;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import com.mojang.serialization.Codec;
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class WebCtcSavedData extends SavedData {
    private static final Codec<WebCtcSavedData> CODEC = CompoundTag.CODEC.xmap(
        WebCtcSavedData::load,
        WebCtcSavedData::saveTag
    );
    private static final SavedDataType<WebCtcSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "webctc"),
        WebCtcSavedData::new,
        CODEC,
        DataFixTypes.LEVEL
    );

    private String waypointsJson = "[]";
    private String railgroupsJson = "[]";
    private String teconsJson = "[]";

    public static WebCtcSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static WebCtcSavedData load(CompoundTag tag) {
        WebCtcSavedData data = new WebCtcSavedData();
        data.waypointsJson = safeArray(NbtCompat.getString(tag, "Waypoints"));
        data.railgroupsJson = safeArray(NbtCompat.getString(tag, "Railgroups"));
        data.teconsJson = safeArray(NbtCompat.getString(tag, "Tecons"));
        return data;
    }

    private CompoundTag saveTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Waypoints", waypointsJson);
        tag.putString("Railgroups", railgroupsJson);
        tag.putString("Tecons", teconsJson);
        return tag;
    }

    public String get(String name) {
        return switch (name) {
            case "waypoints" -> waypointsJson;
            case "railgroups" -> railgroupsJson;
            case "tecons" -> teconsJson;
            default -> "[]";
        };
    }

    public void set(String name, String json) {
        String safe = safeArray(json);
        switch (name) {
            case "waypoints" -> waypointsJson = safe;
            case "railgroups" -> railgroupsJson = safe;
            case "tecons" -> teconsJson = safe;
            default -> {
                return;
            }
        }
        setDirty();
    }

    private static String safeArray(String json) {
        if (json == null || json.isBlank()) {
            return "[]";
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return "[]";
        }
        return trimmed;
    }
}
