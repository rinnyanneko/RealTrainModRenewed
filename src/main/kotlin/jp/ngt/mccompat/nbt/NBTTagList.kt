// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat.nbt

import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.CompoundTag

@Suppress("unused")
class NBTTagList(@JvmField val list: ListTag = ListTag()) {
    fun func_74745_c(): Int = list.size
    fun func_150305_b(index: Int) = NBTTagCompound(list.getCompound(index).orElseGet(::CompoundTag))
    fun func_74742_a(value: Any?) {
        when (value) {
            is NBTTagCompound -> list.add(value.tag)
            is Tag -> list.add(value)
        }
    }
}
