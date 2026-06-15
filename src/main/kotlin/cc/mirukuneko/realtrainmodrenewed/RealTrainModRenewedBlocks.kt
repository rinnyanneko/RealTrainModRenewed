package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.block.*
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Function
import java.util.function.Supplier

object RealTrainModRenewedBlocks {
    @JvmField
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(RealTrainModRenewed.MODID)

    @JvmField val CROSSING_GATE: DeferredBlock<CrossingGateBlock> = BLOCKS.registerBlock("crossing_gate", Function { _: BlockBehaviour.Properties -> CrossingGateBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0f, 6.0f).noOcclusion() })
    @JvmField val MARKER: DeferredBlock<MarkerBlock> = BLOCKS.registerBlock("marker", Function { props: BlockBehaviour.Properties -> MarkerBlock(false, props) }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.0f, 1.0f).noOcclusion().noCollision() })
    @JvmField val MARKER_SWITCH: DeferredBlock<MarkerBlock> = BLOCKS.registerBlock("marker_switch", Function { props: BlockBehaviour.Properties -> MarkerBlock(true, props) }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.0f, 1.0f).noOcclusion().noCollision() })
    @JvmField val BALLAST: DeferredBlock<BallastBlock> = BLOCKS.registerBlock("ballast", Function { _: BlockBehaviour.Properties -> BallastBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.GRAVEL).strength(0.6f, 3.0f).noOcclusion().isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false } })
    @JvmField val LARGE_RAIL_CORE: DeferredBlock<LargeRailCoreBlock> = BLOCKS.registerBlock("large_rail_core", Function { _: BlockBehaviour.Properties -> LargeRailCoreBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.5f, 6.0f).noOcclusion() })
    @JvmField val RAIL_COLLISION: DeferredBlock<RailCollisionBlock> = BLOCKS.registerBlock("rail_collision", Function { _: BlockBehaviour.Properties -> RailCollisionBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.1f, 0.1f).noOcclusion().isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false } })
    @JvmField val INSTALLED_OBJECT: DeferredBlock<InstalledObjectBlock> = BLOCKS.registerBlock("installed_object", Function { _: BlockBehaviour.Properties -> InstalledObjectBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.4f, 2.0f).noOcclusion() })
    @JvmField val SIGNAL_RECEIVER: DeferredBlock<SignalRemoteBlock> = BLOCKS.registerBlock("signal_receiver", Function { props: BlockBehaviour.Properties -> SignalRemoteBlock(props, SignalRemoteBlock.Mode.RECEIVER) }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0f, 6.0f) })
    @JvmField val SIGNAL_CHANGER: DeferredBlock<SignalRemoteBlock> = BLOCKS.registerBlock("signal_changer", Function { props: BlockBehaviour.Properties -> SignalRemoteBlock(props, SignalRemoteBlock.Mode.CHANGER) }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0f, 6.0f) })
    @JvmField val SIGNAL_VALUE_RECEIVER: DeferredBlock<SignalRemoteBlock> = BLOCKS.registerBlock("signal_value_receiver", Function { props: BlockBehaviour.Properties -> SignalRemoteBlock(props, SignalRemoteBlock.Mode.VALUE_INPUT) }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0f, 6.0f) })
    @JvmField val TRAIN_DETECTOR: DeferredBlock<TrainDetectorBlock> = BLOCKS.registerBlock("train_detector", Function { _: BlockBehaviour.Properties -> TrainDetectorBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2f, 6.0f) })
    @JvmField val SIGNAL_STATE: DeferredBlock<SignalStateBlock> = BLOCKS.registerBlock("signal_state", Function { _: BlockBehaviour.Properties -> SignalStateBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2f, 6.0f) })
    @JvmField val SCRIPT_BLOCK: DeferredBlock<ScriptBlock> = BLOCKS.registerBlock("script_block", Function { _: BlockBehaviour.Properties -> ScriptBlock() }, Supplier { BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2f, 6.0f) })
}
