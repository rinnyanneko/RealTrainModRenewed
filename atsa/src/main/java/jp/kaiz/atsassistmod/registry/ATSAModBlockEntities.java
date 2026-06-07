package jp.kaiz.atsassistmod.registry;

import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ATSAModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, ATSAssistMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundUnitBlockEntity>> GROUND_UNIT =
            BLOCK_ENTITIES.register("groundunit", () ->
                    new BlockEntityType<>(GroundUnitBlockEntity::new, ATSAModBlocks.GROUND_UNIT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IftttBlockEntity>> IFTTT =
            BLOCK_ENTITIES.register("ifttt", () ->
                    new BlockEntityType<>(IftttBlockEntity::new, ATSAModBlocks.IFTTT.get()));

    private ATSAModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
