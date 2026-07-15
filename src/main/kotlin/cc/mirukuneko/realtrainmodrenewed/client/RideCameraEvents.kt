// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent
import net.neoforged.neoforge.client.event.InputEvent

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object RideCameraEvents {
    private const val MIN_DISTANCE = 4.0f
    private const val MAX_DISTANCE = 48.0f
    private const val SCROLL_STEP = 2.0f
    private var distance = MIN_DISTANCE

    @JvmStatic
    @SubscribeEvent
    fun onScroll(event: InputEvent.MouseScrollingEvent) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen != null || !isRidingTrain(minecraft) || minecraft.options.cameraType.isFirstPerson) {
            return
        }
        val delta = event.scrollDeltaY
        if (delta == 0.0) {
            return
        }
        distance = Mth.clamp(distance + delta.toFloat() * SCROLL_STEP, MIN_DISTANCE, MAX_DISTANCE)
        event.isCanceled = true
    }

    @JvmStatic
    @SubscribeEvent
    fun onCameraDistance(event: CalculateDetachedCameraDistanceEvent) {
        if (isRidingTrain(Minecraft.getInstance())) {
            event.distance = distance
        }
    }

    private fun isRidingTrain(minecraft: Minecraft): Boolean =
        when (val vehicle = minecraft.player?.vehicle) {
            is TrainEntity -> true
            is TrainSeatEntity -> vehicle.train != null
            else -> false
        }
}
