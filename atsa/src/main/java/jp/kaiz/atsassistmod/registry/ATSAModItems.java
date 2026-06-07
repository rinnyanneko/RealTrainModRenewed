package jp.kaiz.atsassistmod.registry;

import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.block.GroundUnitType;
import jp.kaiz.atsassistmod.item.DataMapEditorItem;
import jp.kaiz.atsassistmod.item.GroundUnitItem;
import jp.kaiz.atsassistmod.item.TrainProtectionSelectorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ATSAModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ATSAssistMod.MODID);

    /**
     * Single ground-unit item (blank / None). As in the original, you place one block
     * and choose its function via the right-click GUI — there is no separate item per
     * variant.
     */
    public static final DeferredItem<GroundUnitItem> GROUND_UNIT =
            ITEMS.registerItem("groundunit_0",
                    props -> new GroundUnitItem(ATSAModBlocks.GROUND_UNIT.get(), GroundUnitType.None, props));

    public static final DeferredItem<BlockItem> IFTTT =
            ITEMS.registerSimpleBlockItem("ifttt", ATSAModBlocks.IFTTT);
    public static final DeferredItem<BlockItem> STATION_ANNOUNCE =
            ITEMS.registerSimpleBlockItem("station_announce", ATSAModBlocks.STATION_ANNOUNCE);

    public static final DeferredItem<TrainProtectionSelectorItem> TRAIN_PROTECTION_SELECTOR =
            ITEMS.registerItem("train_protection_selector", TrainProtectionSelectorItem::new);
    public static final DeferredItem<DataMapEditorItem> DATA_MAP_EDITOR =
            ITEMS.registerItem("data_map_editor", DataMapEditorItem::new);

    private ATSAModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
