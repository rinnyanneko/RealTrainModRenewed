// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.ATSAssistMod
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.EmergencyBrake
import jp.kaiz.atsassistmod.rtm.RtmTrains
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Game-bus client events: emergency-brake key polling. */
@EventBusSubscriber(modid = ATSAssistMod.MODID, value = [Dist.CLIENT])
object ATSAModClientGameEvents {
    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        while (ATSAModKeys.EMERGENCY_BRAKE.consumeClick()) {
            val player = Minecraft.getInstance().player ?: continue
            val vehicle = player.vehicle
            if (vehicle is TrainEntity && RtmTrains.isControlCar(vehicle)) {
                ClientNetworkHelper.sendToServer(EmergencyBrake.INSTANCE)
            }
        }
    }
}
