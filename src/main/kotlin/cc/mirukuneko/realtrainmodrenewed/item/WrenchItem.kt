// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.network.ConfigureMarkerPayload
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class WrenchItem : Item {
    companion object {
        private const val WRENCH_SEARCH_DISTANCE = 64
        private const val WRENCH_SEARCH_HEIGHT = 32
        private const val OFFSET_STEP = 1.0 / 16.0
        private const val OFFSET_LIMIT = 2.0

        @JvmField var editingMarker: BlockPos? = null
        @JvmField var editingPair: BlockPos? = null
        @JvmField var followMode: Boolean = false
        private var editStartTime: Long = 0
        @JvmField var liveYaw: Float = 0f
        @JvmField var livePitch: Float = 0f
        @JvmField var liveLenH: Float = -1.0f
        @JvmField var liveLenV: Float = 0f
        @JvmField var liveCantCenter: Float = 0f
        @JvmField var liveCantEdge: Float = 0f
        @JvmField var liveCantRandom: Float = 0f

        @JvmStatic
        fun findPlayerPreviewStack(player: Player): ItemStack {
            for (i in 0 until player.inventory.getContainerSize()) {
                val stack = player.inventory.getItem(i)
                if (!stack.isEmpty && stack.item is WrenchItem) return stack
            }
            return ItemStack.EMPTY
        }

        @JvmStatic
        fun getSegmentList(tag: CompoundTag): List<RailPosition> {
            val list = NbtCompat.getList(tag, "RailSegments")
            val segments = mutableListOf<RailPosition>()
            var prevEnd: RailPosition? = null
            val globalStart = RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"))
            for (i in 0 until list.size) {
                val seg = NbtCompat.getCompound(list, i)
                val startTag = NbtCompat.getCompound(seg, "StartRP")
                val endTag = NbtCompat.getCompound(seg, "EndRP")

                var s = RailPosition.readFromNBT(startTag)
                if (s == null) {
                    // Backward-compat: older segment entries only carry EndRP.
                    // Use the previous segment end, or the global preview StartRP.
                    s = prevEnd ?: globalStart ?: continue
                }
                val e = RailPosition.readFromNBT(endTag) ?: continue
                segments.add(s)
                segments.add(e)
                prevEnd = e
            }
            return segments
        }

        private fun startEdit(level: Level, pos: BlockPos, player: Player) {
            val be = level.getBlockEntity(pos) as? MarkerBlockEntity ?: return
            val rp = be.markerRP ?: return
            editingMarker = pos.immutable()
            editingPair = null
            followMode = false
            editStartTime = System.currentTimeMillis()
            liveYaw = rp.anchorYaw
            livePitch = rp.anchorPitch
            liveLenH = rp.anchorLengthHorizontal
            liveLenV = rp.anchorLengthVertical
            liveCantCenter = rp.cantCenter
            liveCantEdge = rp.cantEdge
            liveCantRandom = rp.cantRandom
            player.sendSystemMessage(Component.literal("Wrench edit mode: look to bend, right-click to finish"))
        }

        private fun confirmEdit(level: Level, pos: BlockPos, player: Player) {
            val target = editingMarker
            if (target != null) {
                val be = level.getBlockEntity(target) as? MarkerBlockEntity
                be?.configure(liveYaw, livePitch, liveLenH, liveLenV, liveCantCenter, liveCantEdge, liveCantRandom)
            }
            editingMarker = null
            editingPair = null
            followMode = false
            editStartTime = 0
        }

        private fun findNearestMarkerPos(level: Level, origin: BlockPos): BlockPos? {
            var best: BlockPos? = null
            var bestSq = Double.MAX_VALUE
            for (dx in -WRENCH_SEARCH_DISTANCE..WRENCH_SEARCH_DISTANCE)
                for (dy in -WRENCH_SEARCH_HEIGHT..WRENCH_SEARCH_HEIGHT)
                    for (dz in -WRENCH_SEARCH_DISTANCE..WRENCH_SEARCH_DISTANCE) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val p = origin.offset(dx, dy, dz)
                        if (level.getBlockEntity(p) is MarkerBlockEntity) {
                            val d = origin.distSqr(p)
                            if (d < bestSq) { bestSq = d; best = p.immutable() }
                        }
                    }
            return best
        }
    }

    constructor() : this(Properties().stacksTo(1))
    constructor(properties: Properties) : super(properties)

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val level = context.level
        val clickedPos = context.clickedPos

        if (level.getBlockEntity(clickedPos) is InstalledObjectBlockEntity) {
            if (level.isClientSide) return InteractionResult.SUCCESS
            return InteractionResult.PASS
        }

        val blockState = level.getBlockState(clickedPos)
        if (blockState.block is MarkerBlock) {
            if (!level.isClientSide) {
                val be = level.getBlockEntity(clickedPos) as? MarkerBlockEntity ?: return InteractionResult.FAIL
                if (editingMarker == null) {
                    editingMarker = clickedPos.immutable()
                    editingPair = null
                    player.sendSystemMessage(Component.literal("首個マーカー選択。次で2つ目を選択"))
                    return InteractionResult.SUCCESS
                } else if (editingPair == null) {
                    editingPair = clickedPos.immutable()
                    player.sendSystemMessage(Component.literal("2つ目のマーカー選択。レール配置中..."))
                    return InteractionResult.SUCCESS
                }
            }
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }

        return InteractionResult.PASS
    }
}
