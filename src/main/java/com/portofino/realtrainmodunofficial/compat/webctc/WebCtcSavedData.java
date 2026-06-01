package com.portofino.realtrainmodunofficial.compat.webctc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class WebCtcSavedData extends SavedData {
    private static final String DATA_NAME = "rtmu_webctc";

    private String waypointsJson = "[]";
    private String railgroupsJson = "[]";
    private String teconsJson = "[]";

    public static WebCtcSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WebCtcSavedData::new, WebCtcSavedData::load, DataFixTypes.LEVEL),
            DATA_NAME
        );
    }

    private static WebCtcSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WebCtcSavedData data = new WebCtcSavedData();
        data.waypointsJson = safeArray(tag.getString("Waypoints"));
        data.railgroupsJson = safeArray(tag.getString("Railgroups"));
        data.teconsJson = safeArray(tag.getString("Tecons"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
