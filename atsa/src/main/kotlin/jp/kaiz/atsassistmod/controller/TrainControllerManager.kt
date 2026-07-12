// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.network.ATSAModNet
import jp.kaiz.atsassistmod.network.payload.HudPayload
import jp.kaiz.atsassistmod.rtm.RtmTrains
import net.minecraft.server.MinecraftServer

/**
 * Tracks one [TrainController] per formation, keyed by the head car's entity id.
 */
object TrainControllerManager {
    private val trackingTrainMap = HashMap<Long, TrainController>()

    @JvmStatic
    fun getTrainController(train: TrainEntity?): TrainController {
        if (train == null) {
            return TrainController.NULL
        }
        val key = RtmTrains.formationKey(train)
        val controller = trackingTrainMap.computeIfAbsent(key) {
            TrainController(RtmTrains.head(train))
        }
        controller.bind(RtmTrains.head(train))
        return controller
    }

    /** Server-side: advance every tracked controller; drop stale ones; sync HUD. */
    @JvmStatic
    fun onTick(server: MinecraftServer) {
        if (trackingTrainMap.isEmpty()) {
            return
        }
        val deleteList = ArrayList<Long>()
        trackingTrainMap.forEach { (key, controller) ->
            val train = controller.getTrain()
            if (
                train == null ||
                train.isRemoved ||
                !RtmTrains.isControlCar(train) ||
                RtmTrains.formationKey(train) != key
            ) {
                deleteList.add(key)
                return@forEach
            }
            try {
                controller.onUpdate()
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            ATSAModNet.broadcastHud(server, toHud(key, controller))
        }
        deleteList.forEach { key ->
            ATSAModNet.broadcastHud(server, HudPayload.remove(key))
            trackingTrainMap.remove(key)
        }
    }

    private fun toHud(key: Long, controller: TrainController): HudPayload =
        HudPayload(
            1,
            key,
            controller.isATO(),
            controller.tascController.isEnable(),
            controller.getTrainProtectionType().id,
            controller.getATOSpeedLimit(),
            controller.tascController.getStopDistance().toInt(),
            controller.getSpeedLimit(),
            controller.getTrainProtectionSpeedLimit(),
            controller.isManualDrive(),
        )

    @JvmStatic
    fun find(key: Long): TrainController? = trackingTrainMap[key]

    @JvmStatic
    fun clear() {
        trackingTrainMap.clear()
    }
}
