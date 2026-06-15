package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.blockentity.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object RealTrainModRenewedBlockEntities {
    @JvmField
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RealTrainModRenewed.MODID)

    @JvmField val MARKER: DeferredHolder<BlockEntityType<*>, BlockEntityType<MarkerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("marker", Supplier { BlockEntityType(::MarkerBlockEntity, RealTrainModRenewedBlocks.MARKER.get(), RealTrainModRenewedBlocks.MARKER_SWITCH.get()) })
    @JvmField val LARGE_RAIL_CORE: DeferredHolder<BlockEntityType<*>, BlockEntityType<LargeRailCoreBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("large_rail_core", Supplier { BlockEntityType(::LargeRailCoreBlockEntity, RealTrainModRenewedBlocks.LARGE_RAIL_CORE.get()) })
    @JvmField val RAIL_COLLISION: DeferredHolder<BlockEntityType<*>, BlockEntityType<RailCollisionBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("rail_collision", Supplier { BlockEntityType(::RailCollisionBlockEntity, RealTrainModRenewedBlocks.RAIL_COLLISION.get()) })
    @JvmField val BALLAST: DeferredHolder<BlockEntityType<*>, BlockEntityType<BallastBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("ballast", Supplier { BlockEntityType(::BallastBlockEntity, RealTrainModRenewedBlocks.BALLAST.get()) })
    @JvmField val INSTALLED_OBJECT: DeferredHolder<BlockEntityType<*>, BlockEntityType<InstalledObjectBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("installed_object", Supplier { BlockEntityType(::InstalledObjectBlockEntity, RealTrainModRenewedBlocks.INSTALLED_OBJECT.get()) })
    @JvmField val SIGNAL_REMOTE: DeferredHolder<BlockEntityType<*>, BlockEntityType<SignalRemoteBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("signal_remote", Supplier { BlockEntityType(::SignalRemoteBlockEntity, RealTrainModRenewedBlocks.SIGNAL_RECEIVER.get(), RealTrainModRenewedBlocks.SIGNAL_CHANGER.get(), RealTrainModRenewedBlocks.SIGNAL_VALUE_RECEIVER.get()) })
    @JvmField val TRAIN_DETECTOR: DeferredHolder<BlockEntityType<*>, BlockEntityType<TrainDetectorBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("train_detector", Supplier { BlockEntityType(::TrainDetectorBlockEntity, RealTrainModRenewedBlocks.TRAIN_DETECTOR.get()) })
    @JvmField val SIGNAL_STATE: DeferredHolder<BlockEntityType<*>, BlockEntityType<SignalStateBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("signal_state", Supplier { BlockEntityType(::SignalStateBlockEntity, RealTrainModRenewedBlocks.SIGNAL_STATE.get()) })
    @JvmField val SCRIPT_BLOCK: DeferredHolder<BlockEntityType<*>, BlockEntityType<ScriptBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("script_block", Supplier { BlockEntityType(::ScriptBlockEntity, RealTrainModRenewedBlocks.SCRIPT_BLOCK.get()) })
}
