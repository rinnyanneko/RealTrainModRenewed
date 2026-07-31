// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat

import jp.ngt.mccompat.init.Blocks
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import java.util.Random

@Suppress("unused")
open class WorldCompat(@JvmField val level: Level) {
    @JvmField val field_72995_K: Boolean = level.isClientSide
    @JvmField val rand: Random = Random()

    fun getLevel(): Level = level
    fun isRemote(): Boolean = level.isClientSide
    fun func_72820_D(): Long = level.overworldClockTime
    fun func_82737_E(): Long = level.gameTime
    fun getWorldTime(): Long = level.overworldClockTime
    fun getTotalWorldTime(): Long = level.gameTime
    fun func_72929_e(partialTick: Float): Float =
        ((level.overworldClockTime % 24_000L).toFloat() + partialTick) / 24_000f
    fun getCelestialAngle(partialTick: Float): Float = func_72929_e(partialTick)

    fun func_72839_b(exclude: Any?, aabb: Any?): List<Entity> =
        if (aabb is AABB) level.getEntities(unwrapEntity(exclude), aabb) else emptyList()

    fun func_73045_a(id: Int): Any? = wrapEntity(level.getEntity(id))
    fun func_73045_a(id: Any?): Any? = id?.toString()?.toIntOrNull()?.let(::func_73045_a)
    fun getEntityByID(id: Int): Entity? = level.getEntity(id)

    fun func_147465_d(x: Double, y: Double, z: Double, block: Any?, meta: Int, flag: Int): Boolean {
        val actual = block as? Block ?: return false
        val pos = BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))
        return level.setBlock(pos, Blocks.withColorMeta(actual, meta).defaultBlockState(), flag)
    }

    fun func_147468_f(x: Double, y: Double, z: Double): Boolean =
        level.setBlock(
            BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)),
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
            3,
        )

    fun func_147438_o(x: Double, y: Double, z: Double): Any? =
        level.getBlockEntity(BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)))

    fun func_175625_s(pos: BlockPos?) = pos?.let(level::getBlockEntity)

    fun func_147439_a(x: Double, y: Double, z: Double): Block? =
        Blocks.canonical(level.getBlockState(BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))).block)

    fun func_72805_g(x: Double, y: Double, z: Double): Int =
        Blocks.colorMeta(level.getBlockState(BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))).block)

    fun func_180495_p(pos: BlockPos?): BlockStateCompat? =
        pos?.let { BlockStateCompat(level.getBlockState(it)) }

    fun func_180495_p(x: Double, y: Double, z: Double): BlockStateCompat =
        BlockStateCompat(level.getBlockState(BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))))

    fun func_147471_g(x: Double, y: Double, z: Double) {
        val pos = BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))
        val state = level.getBlockState(pos)
        level.sendBlockUpdated(pos, state, state, 3)
    }

    private fun wrapEntity(entity: Entity?): Any? =
        if (entity is Player) PlayerCompat.of(entity) else entity

    private fun unwrapEntity(value: Any?): Entity? = when (value) {
        is PlayerCompat -> value.player
        is Entity -> value
        else -> null
    }
}
