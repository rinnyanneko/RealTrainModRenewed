package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.MarkerBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.ScriptBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.SignalStateBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.TrainDetectorBlockEntity;
import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaGroundUnitBlockEntity;
import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaSimpleBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModUnofficialBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RealTrainModUnofficial.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
        BLOCK_ENTITY_TYPES.register("marker", () -> BlockEntityType.Builder.of(MarkerBlockEntity::new,
            RealTrainModUnofficialBlocks.MARKER.get(), RealTrainModUnofficialBlocks.MARKER_SWITCH.get()).build(null));

    /** レールコア: 起点ブロック1個。道床とは無関係。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeRailCoreBlockEntity>> LARGE_RAIL_CORE =
        BLOCK_ENTITY_TYPES.register("large_rail_core", () -> BlockEntityType.Builder.of(LargeRailCoreBlockEntity::new,
            RealTrainModUnofficialBlocks.LARGE_RAIL_CORE.get()).build(null));

    /** レール当たり判定ブロック: レールコア削除に追従する。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailCollisionBlockEntity>> RAIL_COLLISION =
        BLOCK_ENTITY_TYPES.register("rail_collision", () -> BlockEntityType.Builder.of(RailCollisionBlockEntity::new,
            RealTrainModUnofficialBlocks.RAIL_COLLISION.get()).build(null));

    /** 道床ブロック: 対応レールコア位置を保持し、壊すとレールも撤去・列車設置検出にも使う。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity>> BALLAST =
        BLOCK_ENTITY_TYPES.register("ballast", () -> BlockEntityType.Builder.of(
            com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity::new,
            RealTrainModUnofficialBlocks.BALLAST.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InstalledObjectBlockEntity>> INSTALLED_OBJECT =
        BLOCK_ENTITY_TYPES.register("installed_object", () -> BlockEntityType.Builder.of(InstalledObjectBlockEntity::new,
            RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalRemoteBlockEntity>> SIGNAL_REMOTE =
        BLOCK_ENTITY_TYPES.register("signal_remote", () -> BlockEntityType.Builder.of(SignalRemoteBlockEntity::new,
            RealTrainModUnofficialBlocks.SIGNAL_RECEIVER.get(),
            RealTrainModUnofficialBlocks.SIGNAL_CHANGER.get(),
            RealTrainModUnofficialBlocks.SIGNAL_VALUE_RECEIVER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainDetectorBlockEntity>> TRAIN_DETECTOR =
        BLOCK_ENTITY_TYPES.register("train_detector", () -> BlockEntityType.Builder.of(TrainDetectorBlockEntity::new,
            RealTrainModUnofficialBlocks.TRAIN_DETECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalStateBlockEntity>> SIGNAL_STATE =
        BLOCK_ENTITY_TYPES.register("signal_state", () -> BlockEntityType.Builder.of(SignalStateBlockEntity::new,
            RealTrainModUnofficialBlocks.SIGNAL_STATE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScriptBlockEntity>> SCRIPT_BLOCK =
        BLOCK_ENTITY_TYPES.register("script_block", () -> BlockEntityType.Builder.of(ScriptBlockEntity::new,
            RealTrainModUnofficialBlocks.SCRIPT_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtsaGroundUnitBlockEntity>> ATSA_GROUND_UNIT =
        BLOCK_ENTITY_TYPES.register("atsa_ground_unit", () -> BlockEntityType.Builder.of(AtsaGroundUnitBlockEntity::new,
            RealTrainModUnofficialBlocks.ATSA_GROUND_UNIT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtsaSimpleBlockEntity>> ATSA_SIMPLE =
        BLOCK_ENTITY_TYPES.register("atsa_simple", () -> BlockEntityType.Builder.of(AtsaSimpleBlockEntity::new,
            RealTrainModUnofficialBlocks.ATSA_IFTTT.get(),
            RealTrainModUnofficialBlocks.ATSA_STATION_ANNOUNCE.get()).build(null));
}
