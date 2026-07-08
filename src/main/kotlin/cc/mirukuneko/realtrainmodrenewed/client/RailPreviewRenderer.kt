package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.item.RailItem
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMapBasic
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object RailPreviewRenderer {
    private const val PREVIEW_SAMPLES = 96
    private const val GUIDE_LINE_WIDTH = 3.0f
    private var cachedGuideMarkers: List<BlockPos> = emptyList()
    private var cachedGuideMarkersAtMs = 0L
    private var cachedGuideCenterChunkX = Int.MIN_VALUE
    private var cachedGuideCenterChunkZ = Int.MIN_VALUE
    private var cachedGuideRadiusChunks = -1

    @JvmStatic
    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent.AfterTranslucentBlocks) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

        val buffer = mc.renderBuffers().bufferSource()
        val consumer = buffer.getBuffer(RenderTypes.lines())
        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera.position()

        poseStack.pushPose()
        poseStack.translate(-camera.x, -camera.y, -camera.z)
        try {
            renderStackPreview(mc, poseStack, consumer)
            renderWrenchEditPreview(mc, poseStack, consumer)
            renderPlacedMarkerStraightGuide(mc, poseStack, consumer)
        } finally {
            poseStack.popPose()
            buffer.endBatch()
        }
    }

    private fun renderPlacedMarkerStraightGuide(mc: Minecraft, poseStack: PoseStack, consumer: VertexConsumer) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (mc.screen != null || mc.options.hideGui) return
        if (!MarkerHudOverlay.isLine1Enabled() && !MarkerHudOverlay.isLine2Enabled()) return

        val playerPos = player.position()
        val guideDistance = getGuideDistance(mc)
        val maxDistanceSq = guideDistance * guideDistance
        for (markerPos in getVisibleGuideMarkers(mc)) {
            if (markerPos.distToCenterSqr(playerPos) > maxDistanceSq) continue
            val marker = level.getBlockEntity(markerPos) as? MarkerBlockEntity ?: continue
            val rp = marker.markerRP ?: continue

            val yaw = Math.toRadians(rp.anchorYaw.toDouble())
            val direction = Vec3(kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw)).normalize()
            val origin = Vec3(rp.posX, rp.posY + 0.16, rp.posZ)
            renderMarkerBox(poseStack, consumer, markerPos, 1.0f, 0.1f, 0.1f, 0.8f)

            renderStraightGuideDirection(poseStack, consumer, origin, direction, 1.0f)
            if (MarkerHudOverlay.isLine2Enabled()) {
                renderStraightGuideDirection(poseStack, consumer, origin, direction.scale(-1.0), 0.65f)
            }
        }
    }

    private fun getVisibleGuideMarkers(mc: Minecraft): List<BlockPos> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        val radiusChunks = getGuideRadiusChunks(mc)
        val centerChunkX = player.blockPosition().x shr 4
        val centerChunkZ = player.blockPosition().z shr 4
        val now = System.currentTimeMillis()
        if (
            now - cachedGuideMarkersAtMs < 750L &&
            cachedGuideCenterChunkX == centerChunkX &&
            cachedGuideCenterChunkZ == centerChunkZ &&
            cachedGuideRadiusChunks == radiusChunks
        ) {
            return cachedGuideMarkers
        }

        val markers = ArrayList<BlockPos>()
        for (cx in centerChunkX - radiusChunks..centerChunkX + radiusChunks) {
            for (cz in centerChunkZ - radiusChunks..centerChunkZ + radiusChunks) {
                val chunk = level.getChunk(cx, cz)
                for (pos in chunk.blockEntities.keys) {
                    if (level.getBlockEntity(pos) is MarkerBlockEntity) {
                        markers.add(pos.immutable())
                    }
                }
            }
        }
        cachedGuideMarkers = markers
        cachedGuideMarkersAtMs = now
        cachedGuideCenterChunkX = centerChunkX
        cachedGuideCenterChunkZ = centerChunkZ
        cachedGuideRadiusChunks = radiusChunks
        return cachedGuideMarkers
    }

    private fun getGuideRadiusChunks(mc: Minecraft): Int =
        max(2, min(12, mc.options.renderDistance().get()))

    private fun getGuideDistance(mc: Minecraft): Double = getGuideRadiusChunks(mc) * 16.0

    private fun renderStraightGuideDirection(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        origin: Vec3,
        direction: Vec3,
        alpha: Float
    ) {
        val end = origin.add(direction.scale(64.0))
        line(poseStack, consumer, origin, end, 1.0f, 0.0f, 0.0f, alpha)
        for (meters in 10..60 step 10) {
            val center = origin.add(direction.scale(meters.toDouble()))
            val side = Vec3(-direction.z, 0.0, direction.x).scale(0.8)
            line(poseStack, consumer, center.subtract(side), center.add(side), 1.0f, 0.0f, 0.0f, alpha)
            renderSmallBox(poseStack, consumer, center, 0.12, 1.0f, 0.0f, 0.0f, alpha)
        }
    }

    private fun renderStackPreview(mc: Minecraft, poseStack: PoseStack, consumer: VertexConsumer) {
        val stack = findPreviewStack(mc)
        if (stack.isEmpty) return

        val tag = stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()) ?: return
        var start = resolveStartPosition(mc.level, tag) ?: return
        start = applyPreviewOffset(start, tag)
        val segments = WrenchItem.getSegmentList(tag)
        if (segments.isEmpty()) return

        var i = 0
        while (i + 1 < segments.size) {
            val segmentStart = segments[i]
            var end = segments[i + 1]
            start = applyPreviewOffset(segmentStart, tag)
            end = applyPreviewOffset(end, tag)
            renderRailCurve(poseStack, consumer, start, end, 0.15f, 0.9f, 1.0f, 0.75f)
            renderHandle(poseStack, consumer, start, anchorHandle(start))
            renderHandle(poseStack, consumer, end, anchorHandle(end))
            i += 2
        }
        if ((segments.size and 1) != 0) {
            val end = applyPreviewOffset(segments[segments.size - 1], tag)
            renderRailCurve(poseStack, consumer, start, end, 0.15f, 0.9f, 1.0f, 0.75f)
            renderHandle(poseStack, consumer, start, anchorHandle(start))
            renderHandle(poseStack, consumer, end, anchorHandle(end))
        }
    }

    private fun renderWrenchEditPreview(mc: Minecraft, poseStack: PoseStack, consumer: VertexConsumer) {
        val editPos = WrenchItem.editingMarker ?: return
        val level = mc.level ?: return
        val marker = level.getBlockEntity(editPos) as? MarkerBlockEntity
        if (marker == null) {
            WrenchItem.editingMarker = null
            WrenchItem.editingPair = null
            WrenchItem.followMode = false
            return
        }
        val start = marker.markerRP ?: return
        if (WrenchItem.followMode) {
            updateLiveHandleFromCrosshair(mc, start)
        }
        val liveStart = RailPosition.readFromNBT(start.writeToNBT()) ?: return
        if (WrenchItem.liveLenH > 0.0f) {
            liveStart.anchorYaw = WrenchItem.liveYaw
            liveStart.anchorPitch = WrenchItem.livePitch
            liveStart.anchorLengthHorizontal = WrenchItem.liveLenH
            liveStart.anchorLengthVertical = WrenchItem.liveLenV
        }
        liveStart.cantCenter = WrenchItem.liveCantCenter
        liveStart.cantEdge = WrenchItem.liveCantEdge
        liveStart.init()

        renderMarkerBox(poseStack, consumer, editPos, 0.2f, 1.0f, 0.2f, 0.9f)
        renderHandle(poseStack, consumer, liveStart, anchorHandle(liveStart))
        val pairPos = WrenchItem.editingPair
        if (pairPos != null) {
            val pairMarker = level.getBlockEntity(pairPos) as? MarkerBlockEntity
            val end = pairMarker?.markerRP
            if (end != null) {
                renderMarkerBox(poseStack, consumer, pairPos, 0.2f, 1.0f, 0.2f, 0.65f)
                renderRailCurve(poseStack, consumer, liveStart, end, 0.2f, 1.0f, 0.2f, 0.75f)
                renderHandle(poseStack, consumer, end, anchorHandle(end))
            }
        }
    }

    private fun updateLiveHandleFromCrosshair(mc: Minecraft, start: RailPosition) {
        val hit = if (mc.hitResult != null && mc.hitResult!!.type != HitResult.Type.MISS) {
            mc.hitResult!!.location
        } else {
            val player = mc.player ?: return
            val eye = player.getEyePosition(1.0f)
            eye.add(player.getViewVector(1.0f).scale(8.0))
        }
        val dx = hit.x - start.posX
        val dz = hit.z - start.posZ
        val horizontal = max(0.5, kotlin.math.sqrt(dx * dx + dz * dz))
        WrenchItem.liveYaw = Math.toDegrees(kotlin.math.atan2(dx, dz)).toFloat()
        WrenchItem.livePitch = 0.0f
        WrenchItem.liveLenH = horizontal.toFloat()
        WrenchItem.liveLenV = horizontal.toFloat()
    }

    private fun findPreviewStack(mc: Minecraft): ItemStack {
        val player = mc.player ?: return ItemStack.EMPTY
        for (hand in InteractionHand.entries) {
            val stack = player.getItemInHand(hand)
            if (
                (stack.item is RailItem || stack.item is WrenchItem) &&
                stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()) != null
            ) {
                return stack
            }
        }
        return ItemStack.EMPTY
    }

    private fun resolveStartPosition(level: Level?, tag: CompoundTag): RailPosition? {
        if (level == null || !tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) {
            return RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"))
        }
        val startPos = BlockPos(NbtCompat.getInt(tag, "X"), NbtCompat.getInt(tag, "Y"), NbtCompat.getInt(tag, "Z"))
        val startBe: BlockEntity? = level.getBlockEntity(startPos)
        if (startBe is MarkerBlockEntity) {
            return startBe.markerRP
        }
        if (startBe is LargeRailCoreBlockEntity) {
            val first = startBe.firstRailPosition
            if (first != null) return first
        }
        return RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"))
    }

    private fun applyPreviewOffset(raw: RailPosition, tag: CompoundTag?): RailPosition {
        val copy = RailPosition.readFromNBT(raw.writeToNBT()) ?: return raw
        if (tag == null) return raw
        copy.posX += NbtCompat.getInt(tag, "OffsetX") / 16.0
        copy.posY += NbtCompat.getInt(tag, "OffsetY") / 16.0
        copy.posZ += NbtCompat.getInt(tag, "OffsetZ") / 16.0
        return copy
    }

    private fun anchorHandle(rp: RailPosition): Vec3 {
        val length = if (rp.anchorLengthHorizontal > 0.0f) rp.anchorLengthHorizontal else 2.0f
        val yaw = Math.toRadians(rp.anchorYaw.toDouble())
        val pitch = Math.toRadians(rp.anchorPitch.toDouble())
        return Vec3(
            rp.posX + kotlin.math.sin(yaw) * length,
            rp.posY + kotlin.math.sin(pitch) * length,
            rp.posZ + kotlin.math.cos(yaw) * length
        )
    }

    private fun renderRailCurve(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        start: RailPosition,
        end: RailPosition,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val map = RailMapBasic(
            RailPosition.readFromNBT(start.writeToNBT()) ?: return,
            RailPosition.readFromNBT(end.writeToNBT()) ?: return
        )
        val split = max(8, RailMap.curveSplitForLength(map.getHorizontalPathLength()))
        val samples = min(PREVIEW_SAMPLES, max(16, ceil(map.getLength() * 2.0).toInt()))
        var previous: Vec3? = null
        for (i in 0..samples) {
            val index = (split * (i / samples.toDouble())).let { kotlin.math.round(it).toInt() }
            val railPos = map.getRailPos(split, index)
            val current = Vec3(railPos[1], map.getRailHeight(split, index) + 0.08, railPos[0])
            val last = previous
            if (last != null) {
                line(poseStack, consumer, last, current, r, g, b, a)
            }
            previous = current
        }
    }

    private fun renderHandle(poseStack: PoseStack, consumer: VertexConsumer, source: RailPosition, handle: Vec3) {
        val start = Vec3(source.posX, source.posY + 0.12, source.posZ)
        line(poseStack, consumer, start, handle, 0.2f, 1.0f, 0.2f, 0.85f)
        renderSmallBox(poseStack, consumer, handle, 0.18, 1.0f, 0.1f, 0.1f, 0.9f)
    }

    private fun renderMarkerBox(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        pos: BlockPos,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val center = Vec3.atBottomCenterOf(pos).add(0.0, 0.12, 0.0)
        renderSmallBox(poseStack, consumer, center, 0.45, r, g, b, a)
    }

    private fun renderSmallBox(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        center: Vec3,
        radius: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val minX = center.x - radius
        val maxX = center.x + radius
        val minY = center.y - radius * 0.25
        val maxY = center.y + radius * 0.25
        val minZ = center.z - radius
        val maxZ = center.z + radius
        val a0 = Vec3(minX, minY, minZ)
        val a1 = Vec3(maxX, minY, minZ)
        val a2 = Vec3(maxX, minY, maxZ)
        val a3 = Vec3(minX, minY, maxZ)
        val b0 = Vec3(minX, maxY, minZ)
        val b1 = Vec3(maxX, maxY, minZ)
        val b2 = Vec3(maxX, maxY, maxZ)
        val b3 = Vec3(minX, maxY, maxZ)
        line(poseStack, consumer, a0, a1, r, g, b, a)
        line(poseStack, consumer, a1, a2, r, g, b, a)
        line(poseStack, consumer, a2, a3, r, g, b, a)
        line(poseStack, consumer, a3, a0, r, g, b, a)
        line(poseStack, consumer, b0, b1, r, g, b, a)
        line(poseStack, consumer, b1, b2, r, g, b, a)
        line(poseStack, consumer, b2, b3, r, g, b, a)
        line(poseStack, consumer, b3, b0, r, g, b, a)
        line(poseStack, consumer, a0, b0, r, g, b, a)
        line(poseStack, consumer, a1, b1, r, g, b, a)
        line(poseStack, consumer, a2, b2, r, g, b, a)
        line(poseStack, consumer, a3, b3, r, g, b, a)
    }

    private fun line(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        start: Vec3,
        end: Vec3,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val pose = poseStack.last()
        consumer.addVertex(pose.pose(), start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
            .setColor(r, g, b, a)
            .setLineWidth(GUIDE_LINE_WIDTH)
            .setNormal(0.0f, 1.0f, 0.0f)
        consumer.addVertex(pose.pose(), end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
            .setColor(r, g, b, a)
            .setLineWidth(GUIDE_LINE_WIDTH)
            .setNormal(0.0f, 1.0f, 0.0f)
    }
}
