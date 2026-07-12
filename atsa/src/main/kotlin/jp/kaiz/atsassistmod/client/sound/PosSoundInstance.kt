// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.sound

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

/** Moving sound fixed at a position. */
open class PosSoundInstance(
    x: Int,
    y: Int,
    z: Int,
    sound: SoundEvent,
    repeat: Boolean,
    volume: Float,
) : AbstractTickableSoundInstance(sound, SoundSource.RECORDS, RandomSource.create()) {
    init {
        looping = repeat
        this.volume = volume
        this.x = x + 0.5
        this.y = y + 0.5
        this.z = z + 0.5
    }

    override fun tick() {
        // Fixed position; nothing to update.
    }
}
