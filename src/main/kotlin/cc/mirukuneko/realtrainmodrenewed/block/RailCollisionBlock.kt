package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
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
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.max
import kotlin.math.min

class RailCollisionBlock : BaseEntityBlock {
    companion object {
        val CODEC: MapCodec<RailCollisionBlock> = simpleCodec { RailCollisionBlock() }

        private fun railShape(level: BlockGetter, pos: BlockPos): VoxelShape {
            var s = 0.0f
            val be = level.getBlockEntity(pos)
            if (be is RailCollisionBlockEntity) s = be.surfaceY
            val top = max(1.0, min(16.0, (s * 16.0).toDouble()))
            return box(0.0, 0.0, 0.0, 16.0, top, 16.0)
        }
    }

    constructor() : this(Properties.of().sound(SoundType.METAL).strength(0.1f, 0.1f).noOcclusion()
        .isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false })
    constructor(props: BlockBehaviour.Properties) : super(props)

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = railShape(level, pos)
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = railShape(level, pos)
    override fun getInteractionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape = railShape(level, pos)

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean): ItemStack {
        val be = level.getBlockEntity(pos)
        if (be is RailCollisionBlockEntity) {
            val corePos = be.corePos
            if (corePos != null && level.getBlockEntity(corePos) is LargeRailCoreBlockEntity)
                return LargeRailCoreBlock.createRailCloneStack(corePos, level.getBlockEntity(corePos) as LargeRailCoreBlockEntity)
        }
        return ItemStack.EMPTY
    }

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, isMoving: Boolean) {
        if (!RailMap.suppressRailRemoval.get()) {
            val be = level.getBlockEntity(pos)
            if (be is RailCollisionBlockEntity) {
                val corePos = be.corePos
                if (corePos != null && level.getBlockState(corePos).block is LargeRailCoreBlock) {
                    val core = level.getBlockEntity(corePos) as? LargeRailCoreBlockEntity
                    LargeRailCoreBlock.removeRailNetwork(level, corePos, core)
                }
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && !RailMap.suppressRailRemoval.get()) {
            val be = level.getBlockEntity(pos)
            if (be is RailCollisionBlockEntity) {
                val corePos = be.corePos
                if (corePos != null && level.getBlockState(corePos).block is LargeRailCoreBlock) {
                    val core = level.getBlockEntity(corePos) as? LargeRailCoreBlockEntity
                    LargeRailCoreBlock.removeRailNetwork(level, corePos, core)
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = RailCollisionBlockEntity(pos, state)

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: net.minecraft.world.level.redstone.Orientation?, isMoving: Boolean) {
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos)
            if (be is RailCollisionBlockEntity) {
                val corePos = be.corePos
                if (corePos != null && level.getBlockEntity(corePos) is LargeRailCoreBlockEntity)
                    (level.getBlockEntity(corePos) as LargeRailCoreBlockEntity).requestSwitchStateRefresh()
            }
        }
        super.neighborChanged(state, level, pos, block, orientation, isMoving)
    }
}
