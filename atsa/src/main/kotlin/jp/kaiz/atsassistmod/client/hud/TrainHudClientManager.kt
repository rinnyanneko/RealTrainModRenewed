// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.hud

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.rtm.RtmTrains

/** Client store of per-formation HUD state, keyed like the server (head entity id). */
object TrainHudClientManager {
    private val map = HashMap<Long, TrainHudClient>()

    @JvmStatic
    fun set(
        formationId: Long,
        ato: Boolean,
        tasc: Boolean,
        tpType: Int,
        atoSpeed: Int,
        tascDistance: Int,
        atcSpeed: Int,
        tpLimit: Int,
        manual: Boolean,
    ) {
        map.computeIfAbsent(formationId) { TrainHudClient() }
            .set(ato, tasc, tpType, atoSpeed, tascDistance, atcSpeed, tpLimit, manual)
    }

    @JvmStatic
    fun get(train: TrainEntity?): TrainHudClient? =
        train?.let { map[RtmTrains.formationKey(it)] }

    @JvmStatic
    fun getOrCreate(train: TrainEntity?): TrainHudClient? =
        train?.let { map.computeIfAbsent(RtmTrains.formationKey(it)) { TrainHudClient() } }

    @JvmStatic
    fun remove(formationId: Long) {
        map.remove(formationId)
    }
}
