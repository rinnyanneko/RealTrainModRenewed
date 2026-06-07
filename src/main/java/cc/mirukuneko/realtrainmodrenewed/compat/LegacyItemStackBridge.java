package cc.mirukuneko.realtrainmodrenewed.compat;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

public final class LegacyItemStackBridge {
    public static final String LEGACY_MODEL_NAME = "ModelName";
    public static final String LEGACY_STATE = "State";
    public static final String LEGACY_DATA_MAP = "DataMap";
    public static final String LEGACY_DATA_LIST = "DataList";
    public static final String LEGACY_DATA_MAP_ARG = "LegacyDataMapArg";
    public static final String LEGACY_STATE_NAME = "Name";

    private LegacyItemStackBridge() {
    }

    public static String getSelectedModelId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String modern = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get());
        if (!isBlank(modern)) {
            return modern;
        }
        CompoundTag tag = getLegacyCustomData(stack);
        return NbtCompat.contains(tag, LEGACY_MODEL_NAME, Tag.TAG_STRING) ? NbtCompat.getString(tag, LEGACY_MODEL_NAME) : "";
    }

    public static String getSelectedDataMap(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String modern = stack.getOrDefault(RealTrainModRenewedComponents.SELECTED_MODEL_DATA_MAP.get(), "");
        if (!isBlank(modern)) {
            return modern;
        }
        CompoundTag tag = getLegacyCustomData(stack);
        if (NbtCompat.contains(tag, LEGACY_DATA_MAP_ARG, Tag.TAG_STRING)) {
            return NbtCompat.getString(tag, LEGACY_DATA_MAP_ARG);
        }
        return extractLegacyDataMapArg(tag);
    }

    public static void setSelectedModelData(ItemStack stack, String modelId, String dataMapValue) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String safeModelId = modelId == null ? "" : modelId;
        String safeDataMap = dataMapValue == null ? "" : dataMapValue;
        stack.set(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get(), safeModelId);
        stack.set(RealTrainModRenewedComponents.SELECTED_MODEL_DATA_MAP.get(), safeDataMap);

        CompoundTag tag = getLegacyCustomData(stack);
        if (!safeModelId.isBlank()) {
            tag.putString(LEGACY_MODEL_NAME, safeModelId);
        }
        if (!safeDataMap.isBlank()) {
            tag.putString(LEGACY_DATA_MAP_ARG, safeDataMap);
            writeLegacyStateArg(tag, safeDataMap);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    private static CompoundTag getLegacyCustomData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static String extractLegacyDataMapArg(CompoundTag tag) {
        if (!NbtCompat.contains(tag, LEGACY_STATE, Tag.TAG_COMPOUND)) {
            return "";
        }
        CompoundTag state = NbtCompat.getCompound(tag, LEGACY_STATE);
        if (!NbtCompat.contains(state, LEGACY_DATA_MAP, Tag.TAG_COMPOUND)) {
            return "";
        }
        ListTag list = NbtCompat.getList(NbtCompat.getCompound(state, LEGACY_DATA_MAP), LEGACY_DATA_LIST);
        if (list.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = NbtCompat.getCompound(list, i);
            String name = NbtCompat.getString(entry, "Name");
            String type = legacyDataTypeKey(NbtCompat.getString(entry, "Type"));
            String value = readLegacyDataValue(entry);
            if (name.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(name).append("=(").append(type).append(')').append(value);
        }
        return builder.toString();
    }

    private static void writeLegacyStateArg(CompoundTag tag, String dataMapValue) {
        CompoundTag state = NbtCompat.contains(tag, LEGACY_STATE, Tag.TAG_COMPOUND)
            ? NbtCompat.getCompound(tag, LEGACY_STATE)
            : new CompoundTag();
        if (!NbtCompat.contains(state, LEGACY_STATE_NAME, Tag.TAG_STRING)) {
            state.putString(LEGACY_STATE_NAME, "no_name");
        }
        if (!state.contains("Color")) {
            state.putInt("Color", 0xFFFFFF);
        }
        CompoundTag dataMap = NbtCompat.contains(state, LEGACY_DATA_MAP, Tag.TAG_COMPOUND)
            ? NbtCompat.getCompound(state, LEGACY_DATA_MAP)
            : new CompoundTag();
        ListTag list = new ListTag();
        for (String part : dataMapValue.split(",")) {
            CompoundTag entry = toLegacyDataEntry(part.trim());
            if (!entry.isEmpty()) {
                list.add(entry);
            }
        }
        dataMap.put(LEGACY_DATA_LIST, list);
        state.put(LEGACY_DATA_MAP, dataMap);
        tag.put(LEGACY_STATE, state);
    }

    private static CompoundTag toLegacyDataEntry(String arg) {
        CompoundTag entry = new CompoundTag();
        int idxEq = arg.indexOf('=');
        int idxOpen = arg.indexOf('(');
        int idxClose = arg.indexOf(')');
        if (idxEq <= 0 || idxOpen != idxEq + 1 || idxClose <= idxOpen + 1) {
            return entry;
        }

        String name = arg.substring(0, idxEq).trim();
        String type = normalizeDataType(arg.substring(idxOpen + 1, idxClose));
        String value = idxClose + 1 < arg.length() ? arg.substring(idxClose + 1).trim() : "";
        if (name.isEmpty()) {
            return entry;
        }

        entry.putString("Name", name);
        entry.putString("Type", legacyDataTypeKey(type));
        entry.putInt("Flag", 3);
        writeLegacyDataValue(entry, type, value);
        return entry;
    }

    private static String readLegacyDataValue(CompoundTag entry) {
        String type = normalizeDataType(NbtCompat.getString(entry, "Type"));
        return switch (type) {
            case "int", "hex" -> Integer.toString(NbtCompat.getInt(entry, "Data"));
            case "double" -> Double.toString(NbtCompat.getDouble(entry, "Data"));
            case "boolean" -> Boolean.toString(NbtCompat.getBoolean(entry, "Data"));
            default -> NbtCompat.getString(entry, "Data");
        };
    }

    private static void writeLegacyDataValue(CompoundTag entry, String type, String value) {
        try {
            switch (type) {
                case "int", "hex" -> entry.putInt("Data", value.isBlank() ? 0 : Integer.decode(value));
                case "double" -> entry.putDouble("Data", value.isBlank() ? 0.0D : Double.parseDouble(value));
                case "boolean" -> entry.putBoolean("Data", Boolean.parseBoolean(value));
                default -> entry.putString("Data", value);
            }
        } catch (NumberFormatException ex) {
            entry.putString("Data", value);
        }
    }

    private static String normalizeDataType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "i", "integer" -> "int";
            case "d", "float" -> "double";
            case "b", "bool" -> "boolean";
            case "s" -> "string";
            case "v", "vec3" -> "vec";
            case "h" -> "hex";
            default -> normalized.isBlank() ? "string" : normalized;
        };
    }

    private static String legacyDataTypeKey(String type) {
        return switch (normalizeDataType(type)) {
            case "int" -> "Int";
            case "double" -> "Double";
            case "boolean" -> "Boolean";
            case "vec" -> "Vec";
            case "hex" -> "Hex";
            default -> "String";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
