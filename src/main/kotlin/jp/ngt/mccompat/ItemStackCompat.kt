// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat

import jp.ngt.mccompat.nbt.NBTTagCompound
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

@Suppress("unused")
class ItemStackCompat(@JvmField val stack: ItemStack) {
    @JvmField val field_77994_a: Int = stack.count
    fun func_77973_b() = stack.item
    fun getItem() = stack.item
    fun getCount(): Int = stack.count
    fun func_190926_b(): Boolean = stack.isEmpty
    fun rawItem() = stack.item
    fun func_77960_j(): Int = 0
    fun func_77942_o(): Boolean = getTagCompat() != null
    fun func_77978_p(): NBTTagCompound? = getTagCompat()
    fun func_77982_d(value: Any?) {
        NBTTagCompound.unwrap(value)?.let { stack.set(DataComponents.CUSTOM_DATA, CustomData.of(it)) }
    }
    fun func_190916_E(): Int = stack.count

    private fun getTagCompat(): NBTTagCompound? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        if (data.isEmpty) return null
        return NBTTagCompound(data.copyTag())
    }

    companion object {
        @JvmStatic
        fun unwrap(value: Any?): ItemStack? = when (value) {
            is ItemStackCompat -> value.stack
            is ItemStack -> value
            else -> null
        }
    }
}
