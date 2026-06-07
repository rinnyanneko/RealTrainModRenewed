package jp.kaiz.atsassistmod;

import com.mojang.logging.LogUtils;
import jp.kaiz.atsassistmod.controller.TrainControllerManager;
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities;
import jp.kaiz.atsassistmod.registry.ATSAModBlocks;
import jp.kaiz.atsassistmod.registry.ATSAModItems;
import jp.kaiz.atsassistmod.registry.ATSAModTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * ATSAssistMod main entry point (NeoForge 1.21.1 port).
 *
 * <p>Original mod id was {@code ATSAssistMod}; 1.21 requires lowercase ids, so the
 * registry namespace is {@code atsassistmod}.</p>
 */
@Mod(ATSAssistMod.MODID)
public final class ATSAssistMod {
    public static final String MODID = "atsassistmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ATSAssistMod(IEventBus modBus) {
        ATSAModBlocks.register(modBus);
        ATSAModItems.register(modBus);
        ATSAModBlockEntities.register(modBus);
        ATSAModTabs.register(modBus);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("[ATSAssist] initialised for {}", MODID);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TrainControllerManager.onTick(event.getServer());
    }
}
