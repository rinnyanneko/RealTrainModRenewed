package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.blockentity.ScriptBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class ScriptBlock : BaseEntityBlock {
    companion object {
        val POWERED: BooleanProperty = BlockStateProperties.POWERED
    }

    private val codec: MapCodec<ScriptBlock> = simpleCodec { ScriptBlock() }

    constructor() : this(Properties.of().sound(SoundType.METAL).strength(1.2f, 6.0f))
    constructor(properties: BlockBehaviour.Properties) : super(properties) {
        registerDefaultState(stateDefinition.any().setValue(POWERED, false))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = codec
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(POWERED) }
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape =
        box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ScriptBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        return createTickerHelper(type, RealTrainModRenewedBlockEntities.SCRIPT_BLOCK.get()) { _, tickerPos, tickerState, be ->
            if (level is ServerLevel) ScriptBlockEntity.serverTick(level, tickerPos, tickerState, be)
        }
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) ClientHooks.openScriptBlockScreen(pos)
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block, orientation: net.minecraft.world.level.redstone.Orientation?, movedByPiston: Boolean) {
        val be = level.getBlockEntity(pos)
        if (be is ScriptBlockEntity) be.onNeighborSignalChanged(level.hasNeighborSignal(pos))
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston)
    }
}
