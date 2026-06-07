package cc.mirukuneko.realtrainmodrenewed.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class NbtCompat {
    private NbtCompat() {
    }

    public static boolean contains(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        return value != null && value.getId() == type;
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByteOr(key, (byte) 0);
    }

    public static int getInt(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }

    public static float getFloat(CompoundTag tag, String key) {
        return tag.getFloatOr(key, 0.0F);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDoubleOr(key, 0.0D);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBooleanOr(key, false);
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        return tag.getIntArray(key).orElseGet(() -> new int[0]);
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    public static ListTag getList(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }

    public static CompoundTag getCompound(ListTag tag, int index) {
        return tag.getCompoundOrEmpty(index);
    }

    public static String getString(ListTag tag, int index) {
        return tag.getString(index).orElse("");
    }
}
