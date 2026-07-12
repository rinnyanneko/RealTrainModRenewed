// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.Entity
import kotlin.concurrent.thread

/**
 * Plays a sequence of sound orders. Each order is either a namespace:path sound or
 * a numeric pause in seconds.
 */
object SoundSequence {
    @JvmStatic
    fun play(posList: List<IntArray?>, orders: List<String?>, volume: Float) {
        thread(name = "ATSAssist-SoundSequence") {
            val minecraft = Minecraft.getInstance()
            for (order in orders) {
                if (order == null) {
                    continue
                }
                try {
                    if (order.contains(":")) {
                        val event = SoundEvent.createVariableRangeEvent(Identifier.parse(order))
                        val tracks = ArrayList<SoundInstance>()
                        for (pos in posList) {
                            if (pos == null) {
                                continue
                            }
                            val sound = PosSoundInstance(pos[0], pos[1], pos[2], event, false, volume)
                            tracks.add(sound)
                            minecraft.execute { minecraft.soundManager.play(sound) }
                        }
                        if (tracks.isEmpty()) {
                            continue
                        }
                        Thread.sleep(50L)
                        while (minecraft.soundManager.isActive(tracks[0])) {
                            Thread.sleep(50L)
                        }
                    } else if (isNumber(order)) {
                        Thread.sleep((1000L * order.toDouble()).toLong())
                    }
                } catch (_: Throwable) {
                    break
                }
            }
        }
    }

    @JvmStatic
    fun play(entity: Entity, orders: List<String?>, volume: Float) {
        thread(name = "ATSAssist-SoundSequence") {
            val minecraft = Minecraft.getInstance()
            for (order in orders) {
                if (order == null) {
                    continue
                }
                try {
                    if (order.contains(":")) {
                        val event = SoundEvent.createVariableRangeEvent(Identifier.parse(order))
                        val track = EntitySoundInstance(entity, event, false, volume)
                        minecraft.execute { minecraft.soundManager.play(track) }
                        Thread.sleep(50L)
                        while (minecraft.soundManager.isActive(track)) {
                            Thread.sleep(50L)
                        }
                    } else if (isNumber(order)) {
                        Thread.sleep((1000L * order.toDouble()).toLong())
                    }
                } catch (_: Throwable) {
                    break
                }
            }
        }
    }

    @JvmStatic
    private fun isNumber(value: String?): Boolean {
        if (value.isNullOrEmpty()) {
            return false
        }
        return value.toDoubleOrNull() != null
    }
}
