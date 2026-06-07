package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.block.BallastBlock;
import cc.mirukuneko.realtrainmodrenewed.block.CrossingGateBlock;
import cc.mirukuneko.realtrainmodrenewed.block.InstalledObjectBlock;
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock;
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock;
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock;
import cc.mirukuneko.realtrainmodrenewed.block.ScriptBlock;
import cc.mirukuneko.realtrainmodrenewed.block.SignalRemoteBlock;
import cc.mirukuneko.realtrainmodrenewed.block.SignalStateBlock;
import cc.mirukuneko.realtrainmodrenewed.block.TrainDetectorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModRenewedBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RealTrainModRenewed.MODID);

    public static final DeferredBlock<CrossingGateBlock> CROSSING_GATE
        = BLOCKS.registerBlock("crossing_gate", CrossingGateBlock::new,
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0F, 6.0F).noOcclusion());
    public static final DeferredBlock<MarkerBlock> MARKER
        = BLOCKS.registerBlock("marker", props -> new MarkerBlock(false, props),
            () -> BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.0F, 1.0F).noOcclusion().noCollision());
    public static final DeferredBlock<MarkerBlock> MARKER_SWITCH
        = BLOCKS.registerBlock("marker_switch", props -> new MarkerBlock(true, props),
            () -> BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.0F, 1.0F).noOcclusion().noCollision());

    /** 道床ブロック（レールと独立した物理ブロック） */
    public static final DeferredBlock<BallastBlock> BALLAST
        = BLOCKS.registerBlock("ballast", BallastBlock::new,
            () -> BlockBehaviour.Properties.of()
                .sound(SoundType.GRAVEL)
                .strength(0.6F, 3.0F)
                .noOcclusion()
                .isSuffocating((s, g, p) -> false)
                .isViewBlocking((s, g, p) -> false));

    /** レールコアブロック（起点1個のみ、MQOモデル描画を担当） */
    public static final DeferredBlock<LargeRailCoreBlock> LARGE_RAIL_CORE
        = BLOCKS.registerBlock("large_rail_core", LargeRailCoreBlock::new,
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.5F, 6.0F).noOcclusion());

    /** レール当たり判定ブロック（非表示・薄い） */
    public static final DeferredBlock<RailCollisionBlock> RAIL_COLLISION
        = BLOCKS.registerBlock("rail_collision", RailCollisionBlock::new,
            () -> BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                .strength(0.1F, 0.1F)
                .noOcclusion()
                .isSuffocating((s, g, p) -> false)
                .isViewBlocking((s, g, p) -> false));

    public static final DeferredBlock<InstalledObjectBlock> INSTALLED_OBJECT
        = BLOCKS.registerBlock("installed_object", InstalledObjectBlock::new,
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.4F, 2.0F).noOcclusion());

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_RECEIVER
        = BLOCKS.registerBlock("signal_receiver", props -> new SignalRemoteBlock(props, SignalRemoteBlock.Mode.RECEIVER),
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0F, 6.0F));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_CHANGER
        = BLOCKS.registerBlock("signal_changer", props -> new SignalRemoteBlock(props, SignalRemoteBlock.Mode.CHANGER),
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0F, 6.0F));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_VALUE_RECEIVER
        = BLOCKS.registerBlock("signal_value_receiver", props -> new SignalRemoteBlock(props, SignalRemoteBlock.Mode.VALUE_INPUT),
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0F, 6.0F));

    public static final DeferredBlock<TrainDetectorBlock> TRAIN_DETECTOR
        = BLOCKS.registerBlock("train_detector", TrainDetectorBlock::new,
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2F, 6.0F));

    public static final DeferredBlock<SignalStateBlock> SIGNAL_STATE
        = BLOCKS.registerBlock("signal_state", SignalStateBlock::new,
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2F, 6.0F));

    public static final DeferredBlock<ScriptBlock> SCRIPT_BLOCK
        = BLOCKS.registerBlock("script_block", ScriptBlock::new,
            () -> BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.2F, 6.0F));
}
