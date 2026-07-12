// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.sound

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity

/** Moving sound that follows an entity. */
open class EntitySoundInstance(
    private val entity: Entity,
    sound: SoundEvent,
    repeat: Boolean,
    volume: Float,
) : AbstractTickableSoundInstance(sound, SoundSource.RECORDS, RandomSource.create()) {
    init {
        looping = repeat
        this.volume = volume
        x = entity.x
        y = entity.y
        z = entity.z
    }

    override fun tick() {
        if (entity.isAlive) {
            x = entity.x
            y = entity.y
            z = entity.z
        } else {
            stop()
        }
    }
}
