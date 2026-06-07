package jp.kaiz.atsassistmod.client;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.EmergencyBrake;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Game-bus client events: emergency-brake key polling. */
@EventBusSubscriber(modid = ATSAssistMod.MODID, value = Dist.CLIENT)
public final class ATSAModClientGameEvents {
    private ATSAModClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ATSAModKeys.EMERGENCY_BRAKE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                continue;
            }
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof TrainEntity train && RtmTrains.isControlCar(train)) {
                jp.kaiz.atsassistmod.client.ClientNetworkHelper.sendToServer(EmergencyBrake.INSTANCE);
            }
        }
    }
}
