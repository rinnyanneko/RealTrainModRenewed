package cc.mirukuneko.realtrainmodrenewed.script

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock
import cc.mirukuneko.realtrainmodrenewed.blockentity.BallastBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import cc.mirukuneko.realtrainmodrenewed.item.RailItem
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.Locale

/**
 * SuperRailBuilder3 の 1.12.2 RTM レール API を RTMU のネイティブ敷設へ橋渡しする。
 */
class SrbRailBridge {
    fun createRailPosition(
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        markerDir: Int,
        switchType: Double,
        anchorLength: Double,
        anchorPitch: Double,
        anchorYaw: Double,
        cantCenter: Double,
        cantEdge: Double,
        height: Double
    ): RailPosition {
        val rp = RailPosition(blockX, blockY, blockZ, markerDir, switchType.toInt())
        if (anchorLength >= 0.0) {
            rp.anchorLengthHorizontal = anchorLength.toFloat()
            rp.anchorLengthVertical = anchorLength.toFloat()
        }
        rp.anchorPitch = anchorPitch.toFloat()
        rp.anchorYaw = anchorYaw.toFloat()
        rp.cantCenter = cantCenter.toFloat()
        rp.cantEdge = cantEdge.toFloat()
        rp.setHeight(height.toInt().toByte())
        rp.init()
        return rp
    }

    fun buildNormalRail(world: Any?, start: RailPosition?, end: RailPosition?, modelId: Any?): Boolean {
        val level = toLevel(world)
        RealTrainModRenewed.LOGGER.debug(
            "[RTM-DBG] SRB buildNormalRail level={} start={} end={} model={}",
            level,
            start?.let { "${it.blockX},${it.blockY},${it.blockZ}" } ?: "null",
            end?.let { "${it.blockX},${it.blockY},${it.blockZ}" } ?: "null",
            toModelId(modelId)
        )
        if (level == null || start == null || end == null) return false
        val ok = MarkerBlock.buildRailForScript(level, listOf(start, end), toModelId(modelId))
        RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB buildNormalRail result={}", ok)
        return ok
    }

    fun buildBranchRail(world: Any?, rpsRaw: List<*>?, modelId: Any?): Boolean {
        val level = toLevel(world)
        if (level == null || rpsRaw == null || rpsRaw.size < 2) return false
        val rps = rpsRaw.filterIsInstance<RailPosition>()
        return MarkerBlock.buildRailForScript(level, rps, toModelId(modelId))
    }

    fun deleteRail(world: Any?, x: Int, y: Int, z: Int): Boolean {
        val level = toLevel(world) ?: return false
        val pos = BlockPos(x, y, z)
        val block = level.getBlockState(pos).block
        if (block is LargeRailCoreBlock) {
            level.removeBlock(pos, false)
            return true
        }
        if (block is RailCollisionBlock) {
            val corePos = (level.getBlockEntity(pos) as? RailCollisionBlockEntity)?.corePos
            if (corePos != null && level.getBlockState(corePos).block is LargeRailCoreBlock) {
                level.removeBlock(corePos, false)
                return true
            }
            level.removeBlock(pos, false)
            return true
        }
        return false
    }

    fun heldRailModelId(playerObj: Any?): String {
        val player = playerObj as? Player ?: return ""
        val main = player.mainHandItem
        if (main.item is RailItem) {
            return LegacyItemStackBridge.getSelectedModelId(main) ?: ""
        }
        val off = player.offhandItem
        if (off.item is RailItem) {
            return LegacyItemStackBridge.getSelectedModelId(off) ?: ""
        }
        return ""
    }

    fun chat(playerObj: Any?, msg: String?) {
        val player = playerObj as? Player ?: return
        if (msg == null) return
        try {
            player.sendSystemMessage(Component.literal(msg))
        } catch (_: Throwable) {
        }
    }

    fun railCoreAt(world: Any?, x: Int, y: Int, z: Int): Any? {
        val level = toLevel(world) ?: return null
        val pos = BlockPos(x, y, z)
        val be = level.getBlockEntity(pos)
        if (be is LargeRailCoreBlockEntity) return be
        val corePos = when (be) {
            is RailCollisionBlockEntity -> be.corePos
            is BallastBlockEntity -> be.corePos
            else -> null
        }
        if (corePos != null) {
            val core = level.getBlockEntity(corePos) as? LargeRailCoreBlockEntity
            if (core != null) {
                debugCore("collision->core", core)
                return core
            }
        }
        if (be is LargeRailCoreBlockEntity) {
            debugCore("direct-core", be)
        }
        return be
    }

    fun tilePos(tile: Any?): IntArray {
        val blockEntity = tile as? BlockEntity ?: return intArrayOf(0, 0, 0)
        val pos = blockEntity.blockPos
        return intArrayOf(pos.x, pos.y, pos.z)
    }

    companion object {
        private var lastCoreLog = 0L

        private fun debugCore(tag: String, core: LargeRailCoreBlockEntity) {
            val now = System.currentTimeMillis()
            if (now - lastCoreLog < 1000L) return
            lastCoreLog = now
            try {
                val rps = core.railPositions
                if (rps == null) {
                    RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB railCoreAt {} rps=null", tag)
                    return
                }
                val sb = StringBuilder()
                for (i in rps.indices) {
                    val rp = rps[i]
                    if (rp == null) {
                        sb.append("[").append(i).append("]=null ")
                        continue
                    }
                    sb.append(
                        String.format(
                            Locale.ROOT,
                            "[%d]pos(%.2f,%.2f,%.2f) yaw=%.1f pitch=%.1f dir=%d ",
                            i,
                            rp.posX,
                            rp.posY,
                            rp.posZ,
                            rp.anchorYaw,
                            rp.anchorPitch,
                            rp.direction
                        )
                    )
                }
                RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB railCoreAt {} {}", tag, sb)
            } catch (t: Throwable) {
                RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB railCoreAt {} err {}", tag, t.toString())
            }
        }

        private fun toLevel(world: Any?): Level? = when (world) {
            is Level -> world
            is CarEntity.CarWorldCompat -> world.getLevel()
            else -> null
        }

        private fun toModelId(modelId: Any?): String? =
            modelId?.toString()?.takeUnless { it.isBlank() }
    }
}
