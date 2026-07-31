// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.WeakHashMap

@Suppress("unused")
class PlayerCompat private constructor(@JvmField val player: Player?) {
    @JvmField var field_70165_t: Double = 0.0
    @JvmField var field_70163_u: Double = 0.0
    @JvmField var field_70161_v: Double = 0.0
    @JvmField var field_70177_z: Float = 0f
    @JvmField var field_70125_A: Float = 0f
    @JvmField val field_71071_by: InventoryCompat = InventoryCompat()
    @JvmField var field_70170_p: WorldCompat? = null

    init {
        refresh()
    }

    fun refresh() {
        val value = player ?: return
        field_70165_t = value.x
        field_70163_u = value.y
        field_70161_v = value.z
        field_70177_z = value.yRot
        field_70125_A = value.xRot
        if (field_70170_p?.level !== value.level()) field_70170_p = WorldCompat(value.level())
        field_71071_by.refresh(value)
    }

    fun func_70078_a(target: Any?) {
        val value = player ?: return
        if (target == null) value.stopRiding()
        else unwrapEntity(target)?.let { value.startRiding(it, true, false) }
    }

    fun func_184210_p() = player?.stopRiding()
    fun func_145782_y(): Int = player?.id ?: -1
    fun func_70005_c_(): String = player?.name?.string ?: ""

    class InventoryCompat {
        @JvmField var field_70461_c: Int = 0
        @JvmField val field_70462_a: Array<ItemStackCompat?> = arrayOfNulls(36)

        internal fun refresh(player: Player) {
            field_70461_c = player.inventory.selectedSlot
            for (index in field_70462_a.indices) {
                val stack = player.inventory.getItem(index)
                field_70462_a[index] = if (stack.isEmpty) null else ItemStackCompat(stack)
            }
        }

        fun func_70448_g(): ItemStackCompat? = field_70462_a.getOrNull(field_70461_c)
        fun getCurrentItem(): ItemStackCompat? = func_70448_g()
        fun func_70301_a(index: Int): ItemStackCompat? = field_70462_a.getOrNull(index)
    }

    companion object {
        private val cache = WeakHashMap<Player, PlayerCompat>()
        @JvmField val EMPTY: PlayerCompat = PlayerCompat(null)

        @JvmStatic
        @Synchronized
        fun of(player: Player?): PlayerCompat? =
            player?.let { cache.getOrPut(it) { PlayerCompat(it) } }

        @JvmStatic
        fun unwrap(value: Any?): Player? = when (value) {
            is PlayerCompat -> value.player
            is Player -> value
            else -> null
        }

        private fun unwrapEntity(value: Any?): Entity? = when (value) {
            is PlayerCompat -> value.player
            is Entity -> value
            else -> null
        }
    }
}
