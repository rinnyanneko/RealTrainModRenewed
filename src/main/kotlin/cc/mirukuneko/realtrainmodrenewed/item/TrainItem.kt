package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.blockentity.BallastBlockEntity
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.util.Mth
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TrainItem : Item {
    enum class Category { ELECTRIC, DIESEL, TEST }

    @JvmField val category: Category

    companion object {
        private const val SPAWN_COOLDOWN_TICKS = 4
        private const val DEFAULT_PLACEMENT_HALF_WIDTH = 1.35
        private const val RAIL_CORE_SCAN_RADIUS_CHUNKS = 6
        private const val MAX_CLICK_TO_RAIL_DISTANCE_SQ = 64.0

        @JvmStatic
        fun accepts(category: Category?, definition: VehicleDefinition?): Boolean {
            if (definition == null || definition.isCarType()) return false
            val type = (definition.vehicleType ?: "").uppercase(Locale.ROOT)
            val test = type == "TEST"
            return when (category ?: Category.ELECTRIC) {
                Category.TEST -> test
                Category.DIESEL -> false
                Category.ELECTRIC -> !test
            }
        }

        fun rectanglesOverlap(ax: Double, az: Double, ayaw: Float, ahx: Double, ahz: Double, bx: Double, bz: Double, byaw: Float, bhx: Double, bhz: Double): Boolean {
            val axes = arrayOf(axisFromYaw(ayaw), perpendicularAxisFromYaw(ayaw), axisFromYaw(byaw), perpendicularAxisFromYaw(byaw))
            val dx = bx - ax; val dz = bz - az
            for (axis in axes) {
                if (abs(dx * axis[0] + dz * axis[1]) > projectedExtent(ahx, ahz, ayaw, axis) + projectedExtent(bhx, bhz, byaw, axis)) return false
            }
            return true
        }
        private fun axisFromYaw(yaw: Float): DoubleArray {
            val r = Math.toRadians(-yaw.toDouble())
            return doubleArrayOf(-sin(r), cos(r))
        }
        private fun perpendicularAxisFromYaw(yaw: Float): DoubleArray {
            val a = axisFromYaw(yaw); return doubleArrayOf(-a[1], a[0])
        }
        private fun projectedExtent(hw: Double, hl: Double, yaw: Float, axis: DoubleArray): Double {
            val fwd = axisFromYaw(yaw); val side = perpendicularAxisFromYaw(yaw)
            return abs(axis[0] * side[0] + axis[1] * side[1]) * hw + abs(axis[0] * fwd[0] + axis[1] * fwd[1]) * hl
        }
    }

    constructor() : this(Category.ELECTRIC)
    constructor(category: Category) : this(category, Properties())
    constructor(category: Category, properties: Properties) : super(properties) {
        this.category = category ?: Category.ELECTRIC
    }

    fun accepts(definition: VehicleDefinition): Boolean = accepts(category, definition)

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        if (level.isClientSide)
            return if (findNearestRailSpawn(level, context.clickedPos, context.clickLocation, player.yRot) != null)
                InteractionResult.SUCCESS else InteractionResult.PASS
        if (player.isShiftKeyDown) return InteractionResult.PASS
        if (player.cooldowns.isOnCooldown(context.itemInHand)) return InteractionResult.PASS

        val stack = context.itemInHand
        val selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
        var spawnVehicleId = selectedId
        var def = VehicleRegistry.getById(spawnVehicleId)
        if (def == null || !accepts(def))
            def = VehicleRegistry.getAll().firstOrNull { accepts(it) }?.also { spawnVehicleId = VehicleRegistry.getSelectionId(it) }
        if (def == null) return InteractionResult.PASS

        val spawnData = findNearestRailSpawn(level, context.clickedPos, context.clickLocation, player.yRot)
            ?: run { player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.train.must_be_on_rail")); return InteractionResult.FAIL }

        if (isOccupiedSpawnArea(level, spawnData.x, spawnData.y + 0.25, spawnData.z, spawnData.yaw, def)) {
            player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.train.already_exists"))
            return InteractionResult.FAIL
        }

        val train = TrainEntity.create(level, spawnVehicleId ?: def.id, spawnData.x, spawnData.y, spawnData.z, spawnData.yaw, def.trainDistance)
            ?: run {
                RealTrainModRenewed.LOGGER.warn("Train placement failed: could not create vehicle id={} display={} at ({}, {}, {})", spawnVehicleId ?: def.id, def.displayName, spawnData.x, spawnData.y, spawnData.z)
                player.sendOverlayMessage(Component.literal("列車を生成できませんでした(モデルIDを確認)"))
                return InteractionResult.FAIL
            }
        train.initializeOnRail(spawnData.map, spawnData.split, spawnData.index)
        level.addFreshEntity(train)
        player.cooldowns.addCooldown(context.itemInHand, SPAWN_COOLDOWN_TICKS)
        return InteractionResult.SUCCESS_SERVER
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide && !isLookingAtBlock(level, player))
            ClientHooks.openTrainSelectScreen(player, player.getItemInHand(hand), category)
        return InteractionResult.SUCCESS
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: net.minecraft.world.item.component.TooltipDisplay, tooltip: Consumer<Component>, flag: TooltipFlag) {
        val selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
        if (!selectedId.isNullOrBlank()) {
            val def = VehicleRegistry.getById(selectedId)
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", def?.displayName ?: selectedId))
        } else tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none"))
    }

    private fun isLookingAtBlock(level: Level, player: Player): Boolean {
        val start = player.getEyePosition(1.0f)
        val end = start.add(player.getViewVector(1.0f).scale(player.blockInteractionRange().toDouble()))
        return level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).type == HitResult.Type.BLOCK
    }

    private fun isOccupiedSpawnArea(level: Level, x: Double, y: Double, z: Double, yaw: Float, def: VehicleDefinition): Boolean {
        val halfLength = placementHalfLength(def)
        val halfWidth = placementHalfWidth(def)
        var maxExistingHalfLength = halfLength
        var maxExistingHalfWidth = halfWidth
        for (knownDef in VehicleRegistry.getAll()) {
            maxExistingHalfLength = maxOf(maxExistingHalfLength, placementHalfLength(knownDef))
            maxExistingHalfWidth = maxOf(maxExistingHalfWidth, placementHalfWidth(knownDef))
        }
        val radius = halfLength + maxExistingHalfLength + maxExistingHalfWidth + 2.0
        val bounds = AABB(x - radius, y - 0.75, z - radius, x + radius, y + 4.0, z + radius)
        if (level is net.minecraft.server.level.ServerLevel) TrainEntity.purgeDanglingTrainResidue(level, bounds)
        val overlaps = level.getEntitiesOfClass(TrainEntity::class.java, bounds) { it.isAlive && !it.isRemoved }
            .filter { abs(it.y - y) <= 3.5 }
            .filter {
                rectanglesOverlap(
                    x, z, yaw, halfWidth, halfLength,
                    it.x, it.z, it.yRot, it.bodyHalfWidthForPlacement, it.bodyHalfLengthForPlacement
                )
            }
        if (overlaps.isNotEmpty()) {
            logSpawnOccupancy(level, "train_item", x, y, z, yaw, halfWidth, halfLength, overlaps)
            return true
        }
        return false
    }

    private fun placementHalfLength(def: VehicleDefinition): Double {
        var halfLength = maxOf(1.75, def.trainDistance.toDouble())
        for (bogie in def.bogies) halfLength = maxOf(halfLength, abs(bogie.position.z) + 0.95)
        return halfLength
    }

    private fun placementHalfWidth(def: VehicleDefinition): Double {
        var halfWidth = DEFAULT_PLACEMENT_HALF_WIDTH
        for (bogie in def.bogies) halfWidth = maxOf(halfWidth, abs(bogie.position.x) + 1.0)
        return halfWidth
    }

    private fun logSpawnOccupancy(level: Level, source: String, x: Double, y: Double, z: Double, yaw: Float, hw: Double, hl: Double, overlaps: List<TrainEntity>) {
        val entities = overlaps.joinToString(" | ") {
            String.format(Locale.ROOT, "%s uuid=%s pos=(%.2f,%.2f,%.2f) yaw=%.1f half=(%.2f,%.2f) removed=%s alive=%s",
                it.vehicleId, it.uuid, it.x, it.y, it.z, it.yRot, it.bodyHalfWidthForPlacement, it.bodyHalfLengthForPlacement, it.isRemoved, it.isAlive)
        }
        RealTrainModRenewed.LOGGER.warn("Spawn blocked [$source] level=${level.dimension().identifier()} spawn=($x,$y,$z) yaw=$yaw half=($hw,$hl) overlaps=$entities")
    }

    private fun findNearestRailSpawn(level: Level, clickedPos: BlockPos, clickedPoint: Vec3, preferredYaw: Float): RailSpawnData? {
        for (dy in -4..4) for (dx in -8..8) for (dz in -8..8) {
            val pos = clickedPos.offset(dx, dy, dz)
            val be = level.getBlockEntity(pos)
            when (be) {
                is LargeRailCoreBlockEntity -> if (be.isLoaded) {
                    val map = getNearestRailMap(be, clickedPoint)
                    if (map != null) return findNearestPointOnMap(map, clickedPoint, preferredYaw)
                }
                is RailCollisionBlockEntity -> {
                    val corePos = be.corePos ?: continue
                    val core = level.getBlockEntity(corePos)
                    if (core is LargeRailCoreBlockEntity && core.isLoaded) {
                        val map = getNearestRailMap(core, clickedPoint)
                        if (map != null) return findNearestPointOnMap(map, clickedPoint, preferredYaw)
                    }
                }
                is BallastBlockEntity -> {
                    val corePos = be.corePos ?: continue
                    val core = level.getBlockEntity(corePos)
                    if (core is LargeRailCoreBlockEntity && core.isLoaded) {
                        val map = getNearestRailMap(core, clickedPoint)
                        if (map != null) return findNearestPointOnMap(map, clickedPoint, preferredYaw)
                    }
                }
            }
        }
        return findNearestRailSpawnFromLoadedCores(level, clickedPos, clickedPoint, preferredYaw)
    }

    private fun findNearestRailSpawnFromLoadedCores(level: Level, clickedPos: BlockPos, clickedPoint: Vec3, preferredYaw: Float): RailSpawnData? {
        val centerChunkX = clickedPos.x shr 4
        val centerChunkZ = clickedPos.z shr 4
        var best: RailSpawnData? = null
        var bestDistSq = Double.POSITIVE_INFINITY
        for (cx in centerChunkX - RAIL_CORE_SCAN_RADIUS_CHUNKS..centerChunkX + RAIL_CORE_SCAN_RADIUS_CHUNKS) {
            for (cz in centerChunkZ - RAIL_CORE_SCAN_RADIUS_CHUNKS..centerChunkZ + RAIL_CORE_SCAN_RADIUS_CHUNKS) {
                val chunkCenter = BlockPos((cx shl 4) + 8, clickedPos.y, (cz shl 4) + 8)
                if (!level.hasChunkAt(chunkCenter)) continue
                val chunk = level.getChunk(cx, cz)
                for (pos in chunk.blockEntities.keys) {
                    val core = level.getBlockEntity(pos) as? LargeRailCoreBlockEntity ?: continue
                    if (!core.isLoaded) continue
                    for (map in core.allRailMaps) {
                        val spawn = findNearestPointOnMap(map, clickedPoint, preferredYaw) ?: continue
                        val distSq = spawn.distanceSq
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq
                            best = spawn
                        }
                    }
                }
            }
        }
        return best?.takeIf { it.distanceSq <= MAX_CLICK_TO_RAIL_DISTANCE_SQ }
    }

    private fun getNearestRailMap(core: LargeRailCoreBlockEntity, targetPoint: Vec3): RailMap? {
        val maps = core.allRailMaps
        if (maps.isEmpty()) return null
        if (maps.size == 1) return maps[0]
        var best: RailMap? = null; var bestDistSq = Double.POSITIVE_INFINITY
        for (map in maps) {
            val max = getSpawnSplit(map)
            for (i in 0..max) {
                val p = map.getRailPos(max, i)
                val d2 = (p[1] - targetPoint.x).let { it * it } + (map.getRailHeight(max, i) - targetPoint.y).let { it * it } + (p[0] - targetPoint.z).let { it * it }
                if (d2 < bestDistSq) { bestDistSq = d2; best = map }
            }
        }
        return best
    }

    private fun findNearestPointOnMap(map: RailMap, clickedPoint: Vec3, preferredYaw: Float): RailSpawnData? {
        var best: RailSpawnData? = null; var bestDistSq = Double.POSITIVE_INFINITY
        val max = getSpawnSplit(map)
        for (i in 0..max) {
            val p = map.getRailPos(max, i)
            val d2 = (p[1] - clickedPoint.x).let { it * it } + (map.getRailHeight(max, i) - clickedPoint.y).let { it * it } + (p[0] - clickedPoint.z).let { it * it }
            if (d2 < bestDistSq) { bestDistSq = d2; best = RailSpawnData(map, max, i, p[1], map.getRailHeight(max, i), p[0], choosePreferredRailYaw(map.getRailYaw(max, i), preferredYaw), d2) }
        }
        return best
    }

    private fun choosePreferredRailYaw(railYaw: Float, preferredYaw: Float): Float {
        val fwdDiff = abs(Mth.wrapDegrees(railYaw - preferredYaw))
        val revYaw = Mth.wrapDegrees(railYaw + 180f)
        return if (abs(Mth.wrapDegrees(revYaw - preferredYaw)) < fwdDiff) revYaw else railYaw
    }

    private fun getSpawnSplit(map: RailMap?): Int {
        if (map == null) return 64
        return maxOf(96, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 6)
    }

    private data class RailSpawnData(val map: RailMap, val split: Int, val index: Int, val x: Double, val y: Double, val z: Double, val yaw: Float, val distanceSq: Double)
}
