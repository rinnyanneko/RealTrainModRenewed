// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.mccompat.init

import cc.mirukuneko.realtrainmodrenewed.compat.LegacyColorMetadata
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import java.util.concurrent.ConcurrentHashMap

/**
 * Script ABI for the 1.7.10 `net.minecraft.init.Blocks` SRG fields.
 *
 * Coloured block families are represented by their white variant. WorldCompat
 * returns that canonical block and exposes the original colour through legacy
 * metadata, preserving block-detection signal scripts.
 */
object Blocks {
    @JvmField val field_150350_a: Block = net.minecraft.world.level.block.Blocks.AIR
    @JvmField val field_150348_b: Block = net.minecraft.world.level.block.Blocks.STONE
    @JvmField val field_150346_d: Block = net.minecraft.world.level.block.Blocks.DIRT
    @JvmField val field_150351_n: Block = net.minecraft.world.level.block.Blocks.GRAVEL
    @JvmField val field_150325_L: Block = net.minecraft.world.level.block.Blocks.WHITE_WOOL
    @JvmField val field_150359_w: Block = net.minecraft.world.level.block.Blocks.GLASS
    @JvmField val field_150426_aN: Block = net.minecraft.world.level.block.Blocks.GLOWSTONE
    @JvmField val field_150339_S: Block = net.minecraft.world.level.block.Blocks.IRON_BLOCK
    @JvmField val field_150406_ce: Block = net.minecraft.world.level.block.Blocks.WHITE_TERRACOTTA
    @JvmField val field_150405_ch: Block = net.minecraft.world.level.block.Blocks.TERRACOTTA
    @JvmField val field_150410_aZ: Block = net.minecraft.world.level.block.Blocks.GLASS_PANE
    @JvmField val field_150451_bX: Block = net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK
    @JvmField val field_150399_cn: Block = net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS
    @JvmField val field_150397_co: Block = net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS_PANE
    @JvmField val field_150404_cg: Block = net.minecraft.world.level.block.Blocks.WHITE_CARPET
    @JvmField val field_150379_bu: Block = net.minecraft.world.level.block.Blocks.REDSTONE_LAMP
    @JvmField val field_150429_aA: Block = net.minecraft.world.level.block.Blocks.REDSTONE_TORCH
    @JvmField val field_150442_at: Block = net.minecraft.world.level.block.Blocks.LEVER

    private val colors = LegacyColorMetadata.names
    private val canonicalCache = ConcurrentHashMap<Block, Block>()
    private val metadataCache = ConcurrentHashMap<Block, Int>()

    @JvmStatic
    fun func_149716_u(block: Any?): Boolean =
        block is net.minecraft.world.level.block.EntityBlock ||
            (block is Block && block.defaultBlockState().hasBlockEntity())

    @JvmStatic
    fun canonical(block: Block?): Block? {
        if (block == null) return null
        decode(block)
        return canonicalCache[block] ?: block
    }

    @JvmStatic
    fun colorMeta(block: Block?): Int {
        if (block == null) return 0
        decode(block)
        return metadataCache[block] ?: 0
    }

    @JvmStatic
    fun withColorMeta(block: Block, meta: Int): Block {
        if (meta !in colors.indices) return block
        val path = BuiltInRegistries.BLOCK.getKey(block).path
        if (!path.startsWith("white_")) return block
        val id = Identifier.withDefaultNamespace(colors[meta] + path.removePrefix("white"))
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(block)
    }

    private fun decode(block: Block) {
        if (canonicalCache.containsKey(block)) return
        var base = block
        var meta = 0
        val path = BuiltInRegistries.BLOCK.getKey(block).path
        val (canonicalPath, colorMeta) = LegacyColorMetadata.decodeBlockPath(path)
        if (canonicalPath != path) {
            val white = BuiltInRegistries.BLOCK.getOptional(
                Identifier.withDefaultNamespace(canonicalPath),
            ).orElse(null)
            if (white != null) {
                base = white
                meta = colorMeta
            }
        }
        canonicalCache[block] = base
        metadataCache[block] = meta
    }
}
