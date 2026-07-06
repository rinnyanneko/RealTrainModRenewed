package cc.mirukuneko.realtrainmodrenewed.block

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LargeRailCoreBlock : BaseEntityBlock {
    companion object {
        val CODEC: MapCodec<LargeRailCoreBlock> = simpleCodec { LargeRailCoreBlock() }
        private val SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)

        @JvmStatic
        fun removeRailNetwork(level: Level, corePos: BlockPos?, core: LargeRailCoreBlockEntity?) {
            if (level == null || corePos == null) return
            val prev = RailMap.suppressRailRemoval.get()
            RailMap.suppressRailRemoval.set(true)
            try {
                val maps = core?.allRailMaps ?: emptyArray()
                for (map in maps) map?.removeRailBlocks(level)
                removeRemainingCollisionBlocks(level, corePos, maps)
                if (level.getBlockState(corePos).block is LargeRailCoreBlock) level.removeBlock(corePos, false)
            } finally { RailMap.suppressRailRemoval.set(prev) }
        }

        private fun removeRemainingCollisionBlocks(level: Level, corePos: BlockPos, maps: Array<RailMap>) {
            var minX = corePos.x - 2; var maxX = corePos.x + 2
            var minY = corePos.y - 2; var maxY = corePos.y + 2
            var minZ = corePos.z - 2; var maxZ = corePos.z + 2
            for (map in maps) {
                if (map == null) continue
                val split = RailMap.curveSplitForLength(map.getHorizontalPathLength())
                val samples = max(16, split + 1)
                for (i in 0 until samples) {
                    val j = if (samples <= 1) 0 else (split.toDouble() * i / (samples - 1)).roundToInt()
                    val idx = min(split, j)
                    val point = map.getRailPos(split, idx)
                    val x = floor(point[1]).toInt(); val y = floor(map.getRailHeight(split, idx)).toInt(); val z = floor(point[0]).toInt()
                    minX = min(minX, x - 2); maxX = max(maxX, x + 2)
                    minY = min(minY, y - 2); maxY = max(maxY, y + 2)
                    minZ = min(minZ, z - 2); maxZ = max(maxZ, z + 2)
                }
            }
            if (maps.isEmpty()) { minX = corePos.x - 128; maxX = corePos.x + 128; minY = corePos.y - 32; maxY = corePos.y + 32; minZ = corePos.z - 128; maxZ = corePos.z + 128 }
            for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                val scanPos = BlockPos(x, y, z)
                val be = level.getBlockEntity(scanPos)
                if (be is RailCollisionBlockEntity && corePos == be.corePos) level.removeBlock(scanPos, false)
            }
        }

        @JvmStatic
        fun createRailCloneStack(corePos: BlockPos, core: LargeRailCoreBlockEntity): ItemStack {
            val stack = ItemStack(RealTrainModRenewedItems.RAIL_ITEM.get())
            val railDefId = core.railDefinitionId
            if (!railDefId.isNullOrBlank()) stack.set(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get(), railDefId)
            val positions = core.railPositions ?: return stack
            if (positions.size < 2 || positions[0] == null) return stack
            val preview = CompoundTag()
            preview.putInt("X", corePos.x); preview.putInt("Y", corePos.y); preview.putInt("Z", corePos.z)
            preview.putBoolean("WrenchMode", true)
            preview.put("StartRP", positions[0]!!.writeToNBT())
            val segments = ListTag()
            var i = 0
            while (i + 1 < positions.size) {
                val start = positions[i] ?: run { i += 2; continue }
                val end = positions[i + 1] ?: run { i += 2; continue }
                val segment = CompoundTag()
                segment.put("StartRP", start.writeToNBT())
                segment.put("EndRP", end.writeToNBT())
                segments.add(segment)
                i += 2
            }
            if (!segments.isEmpty) {
                preview.putBoolean("BranchMode", segments.size > 1)
                preview.put("RailSegments", segments)
                preview.put("EndRP", NbtCompat.getCompound(NbtCompat.getCompound(segments, 0), "EndRP"))
                stack.set(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get(), preview)
            }
            return stack
        }
    }

    constructor() : this(Properties.of().sound(SoundType.METAL).strength(0.5f, 6.0f).noOcclusion())
    constructor(props: BlockBehaviour.Properties) : super(props)

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = SHAPE
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = SHAPE
    override fun getInteractionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape = SHAPE

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, isMoving: Boolean) {
        val be = level.getBlockEntity(pos)
        if (be is LargeRailCoreBlockEntity) removeRailNetwork(level, pos, be)
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide) { val be = level.getBlockEntity(pos); if (be is LargeRailCoreBlockEntity) removeRailNetwork(level, pos, be) }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = LargeRailCoreBlockEntity(pos, state)

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        if (stack.item is WrenchItem && !level.isClientSide) {
            val core = level.getBlockEntity(pos) as? LargeRailCoreBlockEntity
            if (core != null && core.allRailMaps.size >= 2 && core.cycleSwitch())
                player.sendOverlayMessage(Component.literal("分岐切替: ${core.activeSegmentIndex + 1}/${core.allRailMaps.size} 番線"))
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        return InteractionResult.PASS
    }

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean): ItemStack {
        val be = level.getBlockEntity(pos)
        return if (be is LargeRailCoreBlockEntity) createRailCloneStack(pos, be) else ItemStack.EMPTY
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: net.minecraft.world.level.redstone.Orientation?, isMoving: Boolean) {
        if (!level.isClientSide) { val be = level.getBlockEntity(pos); if (be is LargeRailCoreBlockEntity) be.requestSwitchStateRefresh() }
        super.neighborChanged(state, level, pos, block, orientation, isMoving)
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        null
}
