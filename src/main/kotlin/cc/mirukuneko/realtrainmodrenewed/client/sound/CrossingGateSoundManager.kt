// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.sound

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object CrossingGateSoundManager {
    private val CROSSING_SOUND_ID: Identifier = Identifier.fromNamespaceAndPath("rtm", "block.crossing_gate")
    private val ACTIVE: MutableMap<String, LoopingCrossingSound> = ConcurrentHashMap()
    private const val MAX_AUDIBLE_DISTANCE = 48.0
    private const val MAX_AUDIBLE_DISTANCE_SQ = MAX_AUDIBLE_DISTANCE * MAX_AUDIBLE_DISTANCE
    private const val FULL_VOLUME_DISTANCE = 12.0

    private fun playerInRange(pos: BlockPos): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val dx = pos.x + 0.5 - player.x
        val dy = pos.y + 0.5 - player.y
        val dz = pos.z + 0.5 - player.z
        return dx * dx + dy * dy + dz * dz <= MAX_AUDIBLE_DISTANCE_SQ
    }

    private fun volumeForDistance(pos: BlockPos): Float {
        val player = Minecraft.getInstance().player ?: return 0.0f
        val dx = pos.x + 0.5 - player.x
        val dy = pos.y + 0.5 - player.y
        val dz = pos.z + 0.5 - player.z
        val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (dist <= FULL_VOLUME_DISTANCE) return 1.0f
        if (dist >= MAX_AUDIBLE_DISTANCE) return 0.0f
        val t = (dist - FULL_VOLUME_DISTANCE) / (MAX_AUDIBLE_DISTANCE - FULL_VOLUME_DISTANCE)
        return (1.0 - t).toFloat()
    }

    @JvmStatic
    fun tick(blockEntity: InstalledObjectBlockEntity?) {
        if (blockEntity == null) return
        val level = blockEntity.level
        if (level == null || !level.isClientSide) return
        if (!blockEntity.isPowered) {
            stop(level, blockEntity.blockPos)
            return
        }
        if (!shouldPlayCrossingSound(blockEntity)) {
            stop(level, blockEntity.blockPos)
            return
        }
        if (!playerInRange(blockEntity.blockPos)) {
            stop(level, blockEntity.blockPos)
            return
        }

        val minecraft = Minecraft.getInstance()
        val key = key(level, blockEntity.blockPos)
        val soundId = resolveSoundId(blockEntity)
        var sound = ACTIVE[key]
        if (sound == null || sound.isStopped || !sound.matches(soundId)) {
            sound?.requestStop()
            sound = LoopingCrossingSound(blockEntity, soundId)
            ACTIVE[key] = sound
            minecraft.soundManager.play(sound)
        } else {
            sound.refresh()
        }
    }

    @JvmStatic
    fun stop(level: Level?, pos: BlockPos?) {
        if (level == null || pos == null) return
        ACTIVE.remove(key(level, pos))?.requestStop()
    }

    private fun key(level: Level, pos: BlockPos): String =
        "${level.dimension().identifier()}|${pos.asLong()}"

    private fun shouldPlayCrossingSound(blockEntity: InstalledObjectBlockEntity?): Boolean {
        val definition = blockEntity?.let { InstalledObjectRegistry.getById(it.definitionId) }
        val runningSound = definition?.runningSound
        return !runningSound.isNullOrBlank()
    }

    private fun resolveSoundId(blockEntity: InstalledObjectBlockEntity?): Identifier {
        val definition: InstalledObjectDefinition? = blockEntity?.let { InstalledObjectRegistry.getById(it.definitionId) }
        val raw = definition?.runningSound
        if (raw.isNullOrBlank()) return CROSSING_SOUND_ID
        var normalized = raw.trim().replace('\\', '/')
        if (normalized.endsWith(".ogg")) {
            normalized = normalized.substring(0, normalized.length - 4)
        }
        if (normalized.startsWith("sounds/")) {
            normalized = normalized.substring("sounds/".length)
        }
        val lowered = normalized.lowercase(Locale.ROOT)
        if (lowered.contains("rtm_crossinggate") || lowered.contains("crossinggate0") || lowered.contains("crossinggate1")) {
            return CROSSING_SOUND_ID
        }
        return try {
            if (normalized.contains(":")) {
                val split = normalized.split(":", limit = 2)
                val namespace = split[0].ifBlank { "minecraft" }.lowercase(Locale.ROOT)
                var path = split[1].lowercase(Locale.ROOT)
                if (namespace == "rtm" && path.indexOf('/') >= 0) {
                    path = path.replace('/', '.')
                }
                Identifier.fromNamespaceAndPath(namespace, path)
            } else {
                var path = normalized.lowercase(Locale.ROOT)
                if (path.indexOf('/') >= 0) {
                    path = path.replace('/', '.')
                }
                Identifier.fromNamespaceAndPath("rtm", path)
            }
        } catch (_: Exception) {
            CROSSING_SOUND_ID
        }
    }

    private class LoopingCrossingSound(
        private val blockEntity: InstalledObjectBlockEntity,
        soundId: Identifier?
    ) : AbstractTickableSoundInstance(
        SoundEvent.createVariableRangeEvent(soundId ?: CROSSING_SOUND_ID),
        SoundSource.BLOCKS,
        SoundInstance.createUnseededRandom()
    ) {
        private val soundId: Identifier = soundId ?: CROSSING_SOUND_ID

        init {
            looping = true
            delay = 0
            relative = false
            attenuation = SoundInstance.Attenuation.NONE
            volume = 1.0f
            pitch = 1.0f
            refresh()
        }

        fun matches(other: Identifier?): Boolean =
            soundId == (other ?: CROSSING_SOUND_ID)

        fun refresh() {
            val pos = blockEntity.blockPos
            x = pos.x + 0.5
            y = pos.y + 0.5
            z = pos.z + 0.5
            volume = volumeForDistance(pos)
        }

        fun requestStop() {
            stop()
        }

        override fun tick() {
            val level = blockEntity.level
            if (level == null ||
                !level.isClientSide ||
                blockEntity.isRemoved ||
                !blockEntity.isPowered ||
                !shouldPlayCrossingSound(blockEntity) ||
                !playerInRange(blockEntity.blockPos) ||
                level.getBlockEntity(blockEntity.blockPos) !== blockEntity
            ) {
                if (level != null) {
                    ACTIVE.remove(key(level, blockEntity.blockPos), this)
                }
                stop()
                return
            }
            refresh()
        }
    }
}
