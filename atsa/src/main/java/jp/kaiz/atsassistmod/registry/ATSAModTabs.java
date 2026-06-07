package jp.kaiz.atsassistmod.registry;

import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.block.GroundUnitType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ATSAModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ATSAssistMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("utils", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.atsassistmod.utils"))
                    .icon(() -> new ItemStack(ATSAModItems.IFTTT.get()))
                    .displayItems((params, output) -> {
                        output.accept(ATSAModItems.GROUND_UNIT.get());
                        output.accept(ATSAModItems.IFTTT.get());
                        output.accept(ATSAModItems.STATION_ANNOUNCE.get());
                        output.accept(ATSAModItems.TRAIN_PROTECTION_SELECTOR.get());
                        output.accept(ATSAModItems.DATA_MAP_EDITOR.get());
                    })
                    .build());

    private ATSAModTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
