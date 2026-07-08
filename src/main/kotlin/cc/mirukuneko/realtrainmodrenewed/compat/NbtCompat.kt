package cc.mirukuneko.realtrainmodrenewed.compat

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

object NbtCompat {
    @JvmStatic
    fun contains(tag: CompoundTag, key: String, type: Int): Boolean {
        val value: Tag? = tag.get(key)
        return value != null && value.id.toInt() == type
    }

    @JvmStatic
    fun getByte(tag: CompoundTag, key: String): Byte = tag.getByteOr(key, 0.toByte())

    @JvmStatic
    fun getInt(tag: CompoundTag, key: String): Int = tag.getIntOr(key, 0)

    @JvmStatic
    fun getFloat(tag: CompoundTag, key: String): Float = tag.getFloatOr(key, 0.0f)

    @JvmStatic
    fun getDouble(tag: CompoundTag, key: String): Double = tag.getDoubleOr(key, 0.0)

    @JvmStatic
    fun getBoolean(tag: CompoundTag, key: String): Boolean = tag.getBooleanOr(key, false)

    @JvmStatic
    fun getString(tag: CompoundTag, key: String): String = tag.getStringOr(key, "")

    @JvmStatic
    fun getIntArray(tag: CompoundTag, key: String): IntArray = tag.getIntArray(key).orElseGet { IntArray(0) }

    @JvmStatic
    fun getCompound(tag: CompoundTag, key: String): CompoundTag = tag.getCompoundOrEmpty(key)

    @JvmStatic
    fun getList(tag: CompoundTag, key: String): ListTag = tag.getListOrEmpty(key)

    @JvmStatic
    fun getCompound(tag: ListTag, index: Int): CompoundTag = tag.getCompoundOrEmpty(index)

    @JvmStatic
    fun getString(tag: ListTag, index: Int): String = tag.getString(index).orElse("")
}
