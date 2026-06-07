package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.blockentity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModRenewedBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RealTrainModRenewed.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
        BLOCK_ENTITY_TYPES.register("marker", () -> new BlockEntityType<>(MarkerBlockEntity::new,
            RealTrainModRenewedBlocks.MARKER.get(), RealTrainModRenewedBlocks.MARKER_SWITCH.get()));

    /** レールコア: 起点ブロック1個。道床とは無関係。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeRailCoreBlockEntity>> LARGE_RAIL_CORE =
        BLOCK_ENTITY_TYPES.register("large_rail_core", () -> new BlockEntityType<>(LargeRailCoreBlockEntity::new,
            RealTrainModRenewedBlocks.LARGE_RAIL_CORE.get()));

    /** レール当たり判定ブロック: レールコア削除に追従する。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailCollisionBlockEntity>> RAIL_COLLISION =
        BLOCK_ENTITY_TYPES.register("rail_collision", () -> new BlockEntityType<>(RailCollisionBlockEntity::new,
            RealTrainModRenewedBlocks.RAIL_COLLISION.get()));

    /** 道床ブロック: 対応レールコア位置を保持し、壊すとレールも撤去・列車設置検出にも使う。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BallastBlockEntity>> BALLAST =
        BLOCK_ENTITY_TYPES.register("ballast", () -> new BlockEntityType<>(
            BallastBlockEntity::new,
            RealTrainModRenewedBlocks.BALLAST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InstalledObjectBlockEntity>> INSTALLED_OBJECT =
        BLOCK_ENTITY_TYPES.register("installed_object", () -> new BlockEntityType<>(InstalledObjectBlockEntity::new,
            RealTrainModRenewedBlocks.INSTALLED_OBJECT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalRemoteBlockEntity>> SIGNAL_REMOTE =
        BLOCK_ENTITY_TYPES.register("signal_remote", () -> new BlockEntityType<>(SignalRemoteBlockEntity::new,
            RealTrainModRenewedBlocks.SIGNAL_RECEIVER.get(),
            RealTrainModRenewedBlocks.SIGNAL_CHANGER.get(),
            RealTrainModRenewedBlocks.SIGNAL_VALUE_RECEIVER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainDetectorBlockEntity>> TRAIN_DETECTOR =
        BLOCK_ENTITY_TYPES.register("train_detector", () -> new BlockEntityType<>(TrainDetectorBlockEntity::new,
            RealTrainModRenewedBlocks.TRAIN_DETECTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalStateBlockEntity>> SIGNAL_STATE =
        BLOCK_ENTITY_TYPES.register("signal_state", () -> new BlockEntityType<>(SignalStateBlockEntity::new,
            RealTrainModRenewedBlocks.SIGNAL_STATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScriptBlockEntity>> SCRIPT_BLOCK =
        BLOCK_ENTITY_TYPES.register("script_block", () -> new BlockEntityType<>(ScriptBlockEntity::new,
            RealTrainModRenewedBlocks.SCRIPT_BLOCK.get()));
}
