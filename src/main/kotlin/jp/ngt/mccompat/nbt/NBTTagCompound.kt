// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat.nbt

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

@Suppress("unused")
class NBTTagCompound(@JvmField val tag: CompoundTag = CompoundTag()) {
    fun func_74737_b() = NBTTagCompound(tag.copy())
    fun func_74760_g(key: String): Float = tag.getFloatOr(key, 0f)
    fun func_74776_a(key: String, value: Float) = tag.putFloat(key, value)
    fun func_74764_b(key: String): Boolean = tag.contains(key)
    fun func_74775_l(key: String) = NBTTagCompound(tag.getCompoundOrEmpty(key))
    fun func_74782_a(key: String, value: Any?) {
        when (value) {
            is NBTTagCompound -> tag.put(key, value.tag)
            is NBTTagList -> tag.put(key, value.list)
            is Tag -> tag.put(key, value)
        }
    }
    fun func_74778_a(key: String, value: String) = tag.putString(key, value)
    fun func_74779_i(key: String): String = tag.getStringOr(key, "")
    fun func_74768_a(key: String, value: Int) = tag.putInt(key, value)
    fun func_74762_e(key: String): Int = tag.getIntOr(key, 0)
    fun func_74757_a(key: String, value: Boolean) = tag.putBoolean(key, value)
    fun func_74767_n(key: String): Boolean = tag.getBooleanOr(key, false)
    fun func_74780_a(key: String, value: Double) = tag.putDouble(key, value)
    fun func_74769_h(key: String): Double = tag.getDoubleOr(key, 0.0)
    fun func_150295_c(key: String, type: Int) = NBTTagList(tag.getListOrEmpty(key))
    override fun toString(): String = tag.toString()

    companion object {
        @JvmStatic
        fun unwrap(value: Any?): CompoundTag? = when (value) {
            is NBTTagCompound -> value.tag
            is CompoundTag -> value
            else -> null
        }
    }
}
