// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat

import jp.ngt.mccompat.init.Blocks
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

class BlockStateCompat(@JvmField val state: BlockState) {
    fun func_177230_c(): Block? = Blocks.canonical(state.block)
    fun vanilla(): BlockState = state
    fun func_177228_b(): Map<String, MetaValue> =
        linkedMapOf("meta" to MetaValue(Blocks.colorMeta(state.block)))
    fun getBlock(): Block? = func_177230_c()
    fun getProperties(): Map<String, MetaValue> = func_177228_b()
    fun values(): Collection<MetaValue> = func_177228_b().values

    class MetaValue(private val meta: Int) : Comparable<MetaValue> {
        fun func_176765_a(): Int = meta
        fun getMetadata(): Int = meta
        override fun compareTo(other: MetaValue): Int = meta.compareTo(other.meta)
        override fun equals(other: Any?): Boolean = other is MetaValue && meta == other.meta
        override fun hashCode(): Int = meta
        override fun toString(): String = meta.toString()
    }
}
