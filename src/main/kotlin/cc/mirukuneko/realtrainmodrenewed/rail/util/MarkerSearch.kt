// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail.util

import cc.mirukuneko.realtrainmodrenewed.Config
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/**
 * Enumerates marker block entities from already-loaded chunks.
 *
 * Rail search ranges can be configured up to hundreds of blocks. Iterating every
 * block in that volume would stall the server, while a chunk's block-entity map
 * only contains the sparse positions that can actually be markers.
 */
object MarkerSearch {
    @JvmStatic
    fun forEachInRange(
        level: Level,
        origin: BlockPos,
        action: (BlockPos, MarkerBlockEntity) -> Unit,
    ) {
        val range = Config.RAIL_MARKER_SEARCH_RANGE.get()
        val height = Config.RAIL_MARKER_SEARCH_HEIGHT.get()
        val minChunkX = (origin.x - range) shr 4
        val maxChunkX = (origin.x + range) shr 4
        val minChunkZ = (origin.z - range) shr 4
        val maxChunkZ = (origin.z + range) shr 4

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                if (!level.hasChunk(chunkX, chunkZ)) continue
                for (blockEntity in level.getChunk(chunkX, chunkZ).blockEntities.values) {
                    val marker = blockEntity as? MarkerBlockEntity ?: continue
                    val markerPos = marker.blockPos
                    if (
                        kotlin.math.abs(markerPos.x - origin.x) <= range &&
                        kotlin.math.abs(markerPos.y - origin.y) <= height &&
                        kotlin.math.abs(markerPos.z - origin.z) <= range
                    ) {
                        action(markerPos, marker)
                    }
                }
            }
        }
    }
}
