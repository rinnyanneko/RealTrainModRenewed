// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.ifttt

import jp.kaiz.atsassistmod.ifttt.IFTTTContainer.That
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer.This

/** Creates default IFTTT containers by type id (for the editor screen). */
object IftttFactory {
    /** THIS condition type ids, in editor order. */
    @JvmField
    val THIS_TYPES: List<Int> = listOf(110, 120, 121, 122, 124, 125, 130)

    /** THAT action type ids, in editor order. */
    @JvmField
    val THAT_TYPES: List<Int> = listOf(210, 211, 212, 213, 221, 223, 230)

    @JvmStatic
    fun create(typeId: Int): IFTTTContainer? =
        when (typeId) {
            110 -> This.Minecraft.RedStoneInput()
            120 -> This.RTM.SimpleDetectTrain()
            121 -> This.RTM.Cars()
            122 -> This.RTM.Speed()
            124 -> This.RTM.TrainDataMap()
            125 -> This.RTM.TrainDirection()
            130 -> This.ATSAssist.CrossingObstacleDetection()
            210 -> That.Minecraft.RedStoneOutput()
            211 -> That.Minecraft.PlaySound()
            212 -> That.Minecraft.ExecuteCommand()
            213 -> That.Minecraft.SetBlock()
            221 -> That.RTM.DataMap()
            223 -> That.RTM.TrainSignal()
            230 -> That.ATSAssist.JavaScript()
            else -> null
        }
}
