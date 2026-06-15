package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import cc.mirukuneko.realtrainmodrenewed.blockentity.BallastBlockEntity
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class BallastBlock : BaseEntityBlock {
    companion object {
        val CODEC: MapCodec<BallastBlock> = simpleCodec { BallastBlock() }
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
    }

    constructor() : this(Properties.of().sound(SoundType.GRAVEL).strength(0.6f, 3.0f).noOcclusion()
        .isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false })
    constructor(properties: BlockBehaviour.Properties) : super(properties)

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = BallastBlockEntity(pos, state)
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, isMoving: Boolean) {
        if (!RailMap.suppressRailRemoval.get()) {
            val be = level.getBlockEntity(pos)
            if (be is BallastBlockEntity) {
                val corePos = be.corePos
                if (corePos != null && level.getBlockState(corePos).block is LargeRailCoreBlock)
                    level.removeBlock(corePos, false)
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving)
    }
}
