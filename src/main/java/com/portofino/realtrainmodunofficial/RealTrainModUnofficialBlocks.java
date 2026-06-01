package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.block.BallastBlock;
import com.portofino.realtrainmodunofficial.block.CrossingGateBlock;
import com.portofino.realtrainmodunofficial.block.InstalledObjectBlock;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.block.RailCollisionBlock;
import com.portofino.realtrainmodunofficial.block.ScriptBlock;
import com.portofino.realtrainmodunofficial.block.SignalRemoteBlock;
import com.portofino.realtrainmodunofficial.block.SignalStateBlock;
import com.portofino.realtrainmodunofficial.block.TrainDetectorBlock;
import com.portofino.realtrainmodunofficial.compat.atsassist.block.AtsaGroundUnitBlock;
import com.portofino.realtrainmodunofficial.compat.atsassist.block.AtsaSimpleBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModUnofficialBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RealTrainModUnofficial.MODID);

    public static final DeferredBlock<CrossingGateBlock> CROSSING_GATE
        = BLOCKS.register("crossing_gate", () -> new CrossingGateBlock());
    public static final DeferredBlock<MarkerBlock> MARKER
        = BLOCKS.register("marker", () -> new MarkerBlock(false));
    public static final DeferredBlock<MarkerBlock> MARKER_SWITCH
        = BLOCKS.register("marker_switch", () -> new MarkerBlock(true));

    /** 道床ブロック（レールと独立した物理ブロック） */
    public static final DeferredBlock<BallastBlock> BALLAST
        = BLOCKS.register("ballast", BallastBlock::new);

    /** レールコアブロック（起点1個のみ、MQOモデル描画を担当） */
    public static final DeferredBlock<LargeRailCoreBlock> LARGE_RAIL_CORE
        = BLOCKS.register("large_rail_core", () -> new LargeRailCoreBlock());

    /** レール当たり判定ブロック（非表示・薄い） */
    public static final DeferredBlock<RailCollisionBlock> RAIL_COLLISION
        = BLOCKS.register("rail_collision", () -> new RailCollisionBlock());

    public static final DeferredBlock<InstalledObjectBlock> INSTALLED_OBJECT
        = BLOCKS.register("installed_object", () -> new InstalledObjectBlock());

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_RECEIVER
        = BLOCKS.register("signal_receiver", () -> new SignalRemoteBlock(SignalRemoteBlock.Mode.RECEIVER));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_CHANGER
        = BLOCKS.register("signal_changer", () -> new SignalRemoteBlock(SignalRemoteBlock.Mode.CHANGER));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_VALUE_RECEIVER
        = BLOCKS.register("signal_value_receiver", () -> new SignalRemoteBlock(SignalRemoteBlock.Mode.VALUE_INPUT));

    public static final DeferredBlock<TrainDetectorBlock> TRAIN_DETECTOR
        = BLOCKS.register("train_detector", () -> new TrainDetectorBlock());

    public static final DeferredBlock<SignalStateBlock> SIGNAL_STATE
        = BLOCKS.register("signal_state", () -> new SignalStateBlock());

    public static final DeferredBlock<ScriptBlock> SCRIPT_BLOCK
        = BLOCKS.register("script_block", () -> new ScriptBlock());

    public static final DeferredBlock<AtsaGroundUnitBlock> ATSA_GROUND_UNIT
        = BLOCKS.register("atsa_ground_unit", () -> new AtsaGroundUnitBlock());

    public static final DeferredBlock<AtsaSimpleBlock> ATSA_IFTTT
        = BLOCKS.register("atsa_ifttt", () -> new AtsaSimpleBlock("IFTTT"));

    public static final DeferredBlock<AtsaSimpleBlock> ATSA_STATION_ANNOUNCE
        = BLOCKS.register("atsa_station_announce", () -> new AtsaSimpleBlock("Station Announce"));
}
