package jp.kaiz.atsassistmod.registry;

import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.IftttBlock;
import jp.kaiz.atsassistmod.block.StationAnnounceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ATSAModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ATSAssistMod.MODID);

    public static final DeferredBlock<GroundUnitBlock> GROUND_UNIT =
            BLOCKS.registerBlock("groundunit", GroundUnitBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredBlock<IftttBlock> IFTTT =
            BLOCKS.registerBlock("ifttt", IftttBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredBlock<StationAnnounceBlock> STATION_ANNOUNCE =
            BLOCKS.registerBlock("station_announce", StationAnnounceBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops());

    private ATSAModBlocks() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        BLOCKS.register(bus);
    }
}
