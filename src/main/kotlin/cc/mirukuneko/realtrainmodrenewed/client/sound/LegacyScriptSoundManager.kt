// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.sound

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object LegacyScriptSoundManager {
    private val ACTIVE = ConcurrentHashMap<String, LoopingTrainSound>()
    private val AUTO_RUNNING = ConcurrentHashMap<UUID, AutoRunningSoundState>()
    private val ONE_SHOT_LAST_PLAY_TICK = ConcurrentHashMap<String, Long>()
    private val SPEAKER_SOUNDS = ConcurrentHashMap<String, SimpleSoundInstance>()
    private val INVALID_SOUND_IDS = ConcurrentHashMap.newKeySet<String>()
    private val SOUND_LOG_LAST_MS = ConcurrentHashMap<String, Long>()
    private const val ONE_SHOT_DEBOUNCE_MS = 180L
    private const val LEVER_CLICK_DEBOUNCE_MS = 70L
    private const val SOUND_LOG_THROTTLE_MS = 1_000L
    private var lastLeverClickMs = 0L

    @JvmStatic
    fun play(train: TrainEntity?, namespace: String?, soundName: String?, volume: Float, pitch: Float) {
        play(train, namespace, soundName, volume, pitch, true)
    }

    @JvmStatic
    fun playLegacyId(train: TrainEntity?, legacySoundId: String?, volume: Float, pitch: Float, looping: Boolean) {
        if (legacySoundId == null || legacySoundId.isBlank()) {
            return
        }
        var namespace = "rtm"
        var soundName = legacySoundId
        val separator = legacySoundId.indexOf(':')
        if (separator >= 0) {
            namespace = legacySoundId.substring(0, separator)
            soundName = legacySoundId.substring(separator + 1)
        }
        play(train, namespace, soundName, volume, pitch, looping)
    }

    @JvmStatic
    fun play(train: TrainEntity?, namespace: String?, soundName: String?, volume: Float, pitch: Float, looping: Boolean) {
        if (train == null || !train.level().isClientSide) {
            return
        }
        val soundId = toSoundId(namespace, soundName) ?: return
        val effectiveVolume = volume * legacyScriptMixGain(train, soundId, looping)
        if (effectiveVolume <= 0.001f) {
            if (looping) {
                stop(train, namespace, soundName)
            }
            return
        }
        val minecraft = Minecraft.getInstance()
        if (minecraft.soundManager == null) {
            return
        }
        if (!looping) {
            val oneShotKey = key(train.uuid, soundId)
            val now = System.currentTimeMillis()
            val lastPlay = ONE_SHOT_LAST_PLAY_TICK[oneShotKey]
            if (lastPlay != null && now - lastPlay < ONE_SHOT_DEBOUNCE_MS) {
                return
            }
            ONE_SHOT_LAST_PLAY_TICK[oneShotKey] = now
            minecraft.soundManager.play(
                SimpleSoundInstance(
                    soundId,
                    SoundSource.NEUTRAL,
                    Mth.clamp(effectiveVolume, 0.0f, 8.0f),
                    Mth.clamp(pitch, 0.05f, 4.0f),
                    SoundInstance.createUnseededRandom(),
                    false,
                    0,
                    SoundInstance.Attenuation.LINEAR,
                    train.x,
                    train.y,
                    train.z,
                    false,
                ),
            )
            return
        }
        val key = key(train.uuid, soundChannel(namespace, soundName, soundId))
        var sound = ACTIVE[key]
        if (sound == null || sound.isStopped) {
            if (sound != null) {
                ACTIVE.remove(key, sound)
            }
            sound = LoopingTrainSound(train, soundId, key)
            sound.update(effectiveVolume, pitch)
            ACTIVE[key] = sound
            minecraft.soundManager.play(sound)
            logLoopTransition("start", train, key, soundId, effectiveVolume, pitch)
        } else {
            sound.update(effectiveVolume, pitch)
        }
    }

    @JvmStatic
    fun playWithRange(
        train: TrainEntity?,
        namespace: String?,
        soundName: String?,
        volume: Float,
        pitch: Float,
        soundRange: Float,
        looping: Boolean,
    ) {
        if (train == null || !train.level().isClientSide) {
            return
        }
        if (!looping) {
            val rangedVolume = calcVolumeForRange(volume, soundRange, train.x.toFloat(), train.y.toFloat(), train.z.toFloat())
            play(train, namespace, soundName, rangedVolume, pitch, false)
            return
        }
        val soundId = toSoundId(namespace, soundName) ?: return
        val effectiveVolume = volume * legacyScriptMixGain(train, soundId, looping)
        if (effectiveVolume <= 0.001f) {
            stop(train, namespace, soundName)
            return
        }
        val minecraft = Minecraft.getInstance()
        if (minecraft.soundManager == null) {
            return
        }
        val key = key(train.uuid, soundChannel(namespace, soundName, soundId))
        var sound = ACTIVE[key]
        if (sound == null || sound.isStopped) {
            if (sound != null) {
                ACTIVE.remove(key, sound)
            }
            sound = LoopingTrainSound(train, soundId, key)
            sound.update(effectiveVolume, pitch, soundRange)
            ACTIVE[key] = sound
            minecraft.soundManager.play(sound)
            logLoopTransition("start", train, key, soundId, effectiveVolume, pitch)
        } else {
            sound.update(effectiveVolume, pitch, soundRange)
        }
    }

    @JvmStatic
    fun calcVolumeForRange(baseVolume: Float, soundRange: Float, x: Float, y: Float, z: Float): Float {
        if (!baseVolume.isFinite() || baseVolume <= 0.0f) {
            return 0.0f
        }
        if (!soundRange.isFinite() || soundRange <= 0.0f) {
            return baseVolume
        }
        val minecraft = Minecraft.getInstance()
        val listener = minecraft.cameraEntity ?: minecraft.player ?: return baseVolume
        val dx = listener.x.toFloat() - x
        val dy = listener.y.toFloat() - y
        val dz = listener.z.toFloat() - z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance >= soundRange) {
            return 0.0f
        }
        val defaultRange = 16.0f
        return if (soundRange >= defaultRange) {
            if (distance <= defaultRange) {
                baseVolume
            } else {
                baseVolume * (soundRange - distance) / (soundRange - defaultRange)
            }
        } else {
            baseVolume * (soundRange - distance) / soundRange
        }
    }

    @JvmStatic
    fun playAt(x: Double, y: Double, z: Double, soundIdStr: String?, volume: Float, pitch: Float) {
        if (soundIdStr == null || soundIdStr.isBlank()) {
            return
        }
        val soundId = Identifier.tryParse(soundIdStr.trim().lowercase(Locale.ROOT)) ?: return
        val minecraft = Minecraft.getInstance()
        if (minecraft.soundManager == null) {
            return
        }
        val instance = SimpleSoundInstance(
            soundId,
            SoundSource.RECORDS,
            Mth.clamp(volume, 0.0f, 16.0f),
            Mth.clamp(pitch, 0.05f, 4.0f),
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.LINEAR,
            x,
            y,
            z,
            false,
        )
        val key = posKey(x, y, z)
        val previous = SPEAKER_SOUNDS.put(key, instance)
        if (previous != null) {
            minecraft.soundManager.stop(previous)
        }
        minecraft.soundManager.play(instance)
    }

    private fun posKey(x: Double, y: Double, z: Double): String =
        "${floor(x).toInt()},${floor(y).toInt()},${floor(z).toInt()}"

    @JvmStatic
    fun stopAt(x: Double, y: Double, z: Double) {
        val sound = SPEAKER_SOUNDS.remove(posKey(x, y, z))
        if (sound != null) {
            val minecraft = Minecraft.getInstance()
            if (minecraft.soundManager != null) {
                minecraft.soundManager.stop(sound)
            }
        }
    }

    @JvmStatic
    fun tickJsonRunningSound(train: TrainEntity?) {
        if (train == null || !train.level().isClientSide) {
            return
        }
        val definition = VehicleRegistry.getById(train.vehicleId)
        if (definition == null) {
            stopAutoRunningSound(train)
            return
        }
        if (definition.hasSoundScript() && train.getSoundScriptEngine() != null) {
            stopAutoRunningSound(train)
            return
        }
        if (definition.hasSoundScript() && !definition.hasJsonRunningSounds()) {
            tickScriptFallbackRunningSound(train, definition)
            return
        }
        if (!definition.hasJsonRunningSounds()) {
            stopAutoRunningSound(train)
            return
        }

        val state = AUTO_RUNNING.computeIfAbsent(train.uuid) { AutoRunningSoundState() }
        val speed = abs(train.speed)
        val moving = speed > 0.0025f
        val powering = train.notch > 0
        val accelerating = powering || speed > state.previousSpeed + 0.0005f
        val sound = selectJsonRunningSound(definition, train, speed, moving, accelerating)
        state.previousSpeed = speed

        if (sound == null || sound.isBlank()) {
            stopAutoRunningSound(train)
            return
        }
        val soundId = toSoundIdFromLegacyString(sound)
        if (soundId == null) {
            stopAutoRunningSound(train)
            return
        }
        if (state.currentSoundId != null && state.currentSoundId != soundId) {
            stop(train, state.currentSoundId)
        }
        state.currentSoundId = soundId

        val volume = if (moving) Mth.clamp(0.45f + speed * 7.5f, 0.35f, 1.35f) else 0.55f
        val pitch = if (shouldPitchJsonRunningSound(definition, speed)) {
            Mth.clamp(0.65f + speed * 5.0f, 0.65f, 1.75f)
        } else {
            1.0f
        }
        play(train, soundId.namespace, soundId.path, volume, pitch, true)
    }

    private fun selectJsonRunningSound(
        definition: VehicleDefinition,
        train: TrainEntity,
        speed: Float,
        moving: Boolean,
        accelerating: Boolean,
    ): String? {
        if (!moving) {
            return definition.soundStop
        }
        val startSpeed = getFirstConfiguredMaxSpeed(definition)
        if (speed < startSpeed) {
            return if (accelerating) {
                firstNonBlank(definition.soundStartAcceleration, definition.soundAcceleration)
            } else {
                firstNonBlank(definition.soundDecelerationStop, definition.soundDeceleration, definition.soundStop)
            }
        }
        return if (accelerating) {
            firstNonBlank(definition.soundAcceleration, definition.soundStartAcceleration)
        } else {
            firstNonBlank(definition.soundDeceleration, definition.soundDecelerationStop, definition.soundStop)
        }
    }

    private fun shouldPitchJsonRunningSound(definition: VehicleDefinition, speed: Float): Boolean {
        val startSpeed = getFirstConfiguredMaxSpeed(definition)
        return speed >= startSpeed
    }

    private fun getFirstConfiguredMaxSpeed(definition: VehicleDefinition?): Float {
        if (definition == null || definition.getNotchMaxSpeeds().isEmpty()) {
            return 0.06f
        }
        for (speed in definition.getNotchMaxSpeeds()) {
            if (speed != null && speed > 0.0f) {
                return max(0.005f, speed / 72.0f)
            }
        }
        return 0.06f
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (value in values) {
            if (value != null && value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private fun tickScriptFallbackRunningSound(train: TrainEntity, definition: VehicleDefinition) {
        val scriptPath = definition.soundScriptPath.lowercase(Locale.ROOT).replace('\\', '/')
        if (scriptPath.contains("sound_223")) {
            tickFallback223Sound(train)
        } else if (scriptPath.contains("sound_o220")) {
            tickFallbackTsurikakeSound(train)
        } else if (scriptPath.contains("sound_trailer")) {
            tickFallbackTrailerSound(train)
        } else {
            tickFallbackTrailerSound(train)
        }
    }

    private fun tickFallback223Sound(train: TrainEntity) {
        val speedKmh = abs(train.speed) * 72.0f
        val powering = train.notch != 0
        if (!powering) {
            stop(train, "rtm", "train.223_air")
            stop(train, "rtm", "train.223_s0")
            stop(train, "rtm", "train.223_s1")
            stop(train, "rtm", "train.223_s2")
            stop(train, "rtm", "train.223_run")
            stop(train, "rtm", "train.223_run_tunnel")
            return
        }
        if (speedKmh <= 0.1f) {
            stop(train, "rtm", "train.223_s0")
            stop(train, "rtm", "train.223_s1")
            stop(train, "rtm", "train.223_s2")
            stop(train, "rtm", "train.223_run")
            play(train, "rtm", "train.223_air", 1.0f, 1.0f, true)
            return
        }
        stop(train, "rtm", "train.223_air")
        if (speedKmh < 20.0f) {
            val volume = if (speedKmh < 5.0f) speedKmh / 5.0f else if (speedKmh > 10.0f) (20.0f - speedKmh) / 10.0f else 1.0f
        } else {
            stop(train, "rtm", "train.223_s0")
        }
        if (speedKmh >= 8.0f) {
            val volume = if (speedKmh < 12.0f) (speedKmh - 8.0f) / 4.0f else 1.0f
            val pitch = (speedKmh - 8.0f) / (120.0f - 8.0f) + 0.8f
        } else {
            stop(train, "rtm", "train.223_s1")
        }
        if (speedKmh >= 12.0f) {
            val pitch = (speedKmh - 12.0f) / (120.0f - 12.0f) + 0.9f
            val runVolume = Mth.clamp((speedKmh - 12.0f) / (120.0f - 12.0f), 0.0f, 1.0f)
            play(train, "rtm", "train.223_run", runVolume, 1.0f, true)
        } else {
            stop(train, "rtm", "train.223_s2")
            stop(train, "rtm", "train.223_run")
        }
        stop(train, "rtm", "train.223_run_tunnel")
    }

    private fun tickFallbackTsurikakeSound(train: TrainEntity) {
        val speedKmh = abs(train.speed) * 72.0f
        val powering = train.notch != 0
        if (speedKmh <= 0.1f) {
            play(train, "rtm", "train.223_air", 1.0f, 1.0f, true)
            stop(train, "rtm", "train.tsurikake")
            stop(train, "rtm", "train.tsurikake_x2")
            stop(train, "rtm", "train.tsurikake_n")
            return
        }
        stop(train, "rtm", "train.223_air")
        val neutralVolume = if (speedKmh > 10.0f) (speedKmh / 62.0f) * 0.5f + 0.5f else (speedKmh / 10.0f) * 0.5f
        play(train, "rtm", "train.tsurikake_n", Mth.clamp(neutralVolume, 0.0f, 1.25f), (speedKmh / 72.0f) * 0.25f + 1.0f, true)
        if (!powering) {
            stop(train, "rtm", "train.tsurikake")
            stop(train, "rtm", "train.tsurikake_x2")
            return
        }
        val volume = if (speedKmh < 10.0f) speedKmh / 10.0f else 1.0f
        if (speedKmh >= 36.0f) {
            stop(train, "rtm", "train.tsurikake")
            play(train, "rtm", "train.tsurikake_x2", Mth.clamp(volume, 0.0f, 1.0f), speedKmh / 36.0f, true)
        } else {
            stop(train, "rtm", "train.tsurikake_x2")
            play(train, "rtm", "train.tsurikake", Mth.clamp(volume, 0.0f, 1.0f), speedKmh / 36.0f + 1.0f, true)
        }
    }

    private fun tickFallbackTrailerSound(train: TrainEntity) {
        val speedKmh = abs(train.speed) * 72.0f
        if (speedKmh <= 1.0f) {
            stop(train, "rtm", "train.run_trailer")
            return
        }
        play(
            train,
            "rtm",
            "train.run_trailer",
            Mth.clamp(speedKmh / 80.0f, 0.15f, 1.0f),
            Mth.clamp(0.8f + speedKmh / 120.0f, 0.8f, 1.6f),
            true,
        )
    }

    @JvmStatic
    fun stop(train: TrainEntity?, namespace: String?, soundName: String?) {
        if (train == null) {
            return
        }
        val soundId = toSoundId(namespace, soundName) ?: return
        val activeKey = key(train.uuid, soundChannel(namespace, soundName, soundId))
        val sound = ACTIVE.remove(activeKey)
        if (sound != null) {
            logLoopTransition("stop", train, activeKey, soundId, 0.0f, 0.0f)
        }
        sound?.requestStop()
    }

    private fun stop(train: TrainEntity?, soundId: Identifier?) {
        if (train == null || soundId == null) {
            return
        }
        val sound = ACTIVE.remove(key(train.uuid, soundId))
        sound?.requestStop()
    }

    @JvmStatic
    fun stopAll(train: TrainEntity?) {
        if (train == null) return
        val prefix = "${train.uuid}|"
        ACTIVE.entries.removeIf { (activeKey, sound) ->
            if (!activeKey.startsWith(prefix)) return@removeIf false
            sound.requestStop()
            true
        }
        AUTO_RUNNING.remove(train.uuid)
        SOUND_LOG_LAST_MS.keys.removeIf { it.startsWith(prefix) }
    }

    @JvmStatic
    fun stopAutoRunningSound(train: TrainEntity?) {
        if (train == null) {
            return
        }
        val state = AUTO_RUNNING.remove(train.uuid)
        if (state != null && state.currentSoundId != null) {
            stop(train, state.currentSoundId)
        }
    }

    @JvmStatic
    fun playLeverClick() {
        val minecraft = Minecraft.getInstance()
        if (minecraft.soundManager == null) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastLeverClickMs < LEVER_CLICK_DEBOUNCE_MS) {
            return
        }
        lastLeverClickMs = now
        val soundId = Identifier.fromNamespaceAndPath("rtm", "train.lever")
        minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(soundId), 1.0f, 0.55f))
    }

    private fun key(trainId: UUID, soundId: Identifier): String = "$trainId|$soundId"

    private fun key(trainId: UUID, channel: String): String = "$trainId|$channel"

    private fun soundChannel(namespace: String?, soundName: String?, soundId: Identifier): String {
        val requestedNamespace = namespace?.trim()?.lowercase(Locale.ROOT).orEmpty().ifBlank { "minecraft" }
        var requestedPath = soundName?.trim()?.replace('\\', '/')?.lowercase(Locale.ROOT).orEmpty()
        if (requestedPath.startsWith("sounds/")) requestedPath = requestedPath.substring("sounds/".length)
        if (requestedPath.endsWith(".ogg")) requestedPath = requestedPath.removeSuffix(".ogg")
        return if (requestedNamespace == "minecraft" && requestedPath == "minecart.base") {
            "minecraft:minecart.base"
        } else {
            soundId.toString()
        }
    }

    private fun legacyScriptMixGain(train: TrainEntity, soundId: Identifier, looping: Boolean): Float {
        if (!looping) return 1.0f
        val scriptPath = VehicleRegistry.getById(train.vehicleId)
            ?.soundScriptPath
            ?.replace('\\', '/')
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return when {
            scriptPath.endsWith("sound_hitachi_igbt1.js") && soundId.path == "tora_e231_loop" -> 0.0f
            scriptPath.endsWith("sound_hitachi_igbt1.js") && soundId.path == "tora_e231_loop-2x" -> 0.3f
            scriptPath.endsWith("sound_middlecar1.js") && soundId.path == "223_air" -> 0.0f
            scriptPath.endsWith("sound_middlecar1.js") && soundId.path == "minecart_base" -> 0.3f
            else -> 1.0f
        }
    }

    private fun logLoopTransition(
        action: String,
        train: TrainEntity,
        activeKey: String,
        soundId: Identifier,
        volume: Float,
        pitch: Float,
    ) {
        val logKey = "$activeKey|$action"
        val now = System.currentTimeMillis()
        val previous = SOUND_LOG_LAST_MS.put(logKey, now)
        if (previous != null && now - previous < SOUND_LOG_THROTTLE_MS) return
        RealTrainModRenewed.LOGGER.info(
            "[SoundLoop] {} vehicle={} channel={} event={} notch={} speedKmh={} volume={} pitch={}",
            action,
            train.vehicleId,
            activeKey.substringAfter('|'),
            soundId,
            train.notch,
            abs(train.speed) * 72.0f,
            volume,
            pitch,
        )
    }

    private fun toSoundId(namespace: String?, soundName: String?): Identifier? {
        if (soundName == null || soundName.isBlank()) {
            return null
        }
        var resolvedNamespace = if (namespace == null || namespace.isBlank()) "minecraft" else namespace.lowercase(Locale.ROOT)
        var resolvedPath = soundName.trim().replace('\\', '/').lowercase(Locale.ROOT)
        if (resolvedPath.startsWith("sounds/")) {
            resolvedPath = resolvedPath.substring("sounds/".length)
        }
        if (resolvedPath.endsWith(".ogg")) {
            resolvedPath = resolvedPath.substring(0, resolvedPath.length - ".ogg".length)
        }
        if (resolvedNamespace == "minecraft" && resolvedPath == "minecart.base") {
            resolvedNamespace = "sound_rtm"
            resolvedPath = "minecart_base"
        } else if (resolvedNamespace == "rtm" &&
            (resolvedPath == "train.223_air" || resolvedPath == "train.223_run" || resolvedPath == "train.223_run_tunnel")
        ) {
            resolvedNamespace = "sound_rtm"
            resolvedPath = resolvedPath.substring("train.".length)
        } else if (resolvedNamespace == "rtm" && resolvedPath.indexOf('/') >= 0) {
            resolvedPath = resolvedPath.replace('/', '.')
        }
        val cacheKey = "$resolvedNamespace:$resolvedPath"
        if (INVALID_SOUND_IDS.contains(cacheKey)) {
            return null
        }
        return try {
            Identifier.fromNamespaceAndPath(resolvedNamespace, resolvedPath)
        } catch (exception: Exception) {
            if (INVALID_SOUND_IDS.add(cacheKey)) {
                RealTrainModRenewed.LOGGER.warn("Invalid legacy sound id {}:{}", resolvedNamespace, soundName)
            }
            null
        }
    }

    private fun toSoundIdFromLegacyString(legacySoundId: String?): Identifier? {
        if (legacySoundId == null || legacySoundId.isBlank()) {
            return null
        }
        var namespace = "rtm"
        var soundName = legacySoundId
        val separator = legacySoundId.indexOf(':')
        if (separator >= 0) {
            namespace = legacySoundId.substring(0, separator)
            soundName = legacySoundId.substring(separator + 1)
        }
        return toSoundId(namespace, soundName)
    }

    private class AutoRunningSoundState {
        var currentSoundId: Identifier? = null
        var previousSpeed: Float = 0.0f
    }

    private class LoopingTrainSound(
        private val train: TrainEntity,
        private val soundId: Identifier,
        private val activeKey: String,
    ) :
        AbstractTickableSoundInstance(
            SoundEvent.createVariableRangeEvent(soundId),
            SoundSource.NEUTRAL,
            SoundInstance.createUnseededRandom(),
        ) {
        private var baseVolume: Float = 0.0f
        private var soundRange: Float? = null

        init {
            looping = true
            delay = 0
            volume = 0.0f
            pitch = 1.0f
            relative = false
            x = train.x
            y = train.y
            z = train.z
        }

        fun update(volume: Float, pitch: Float) {
            this.pitch = Mth.clamp(pitch, 0.05f, 4.0f)
            baseVolume = Mth.clamp(volume, 0.0f, 8.0f)
            soundRange = null
            updateVolumeForPosition()
            x = train.x
            y = train.y
            z = train.z
        }

        fun update(volume: Float, pitch: Float, soundRange: Float) {
            this.pitch = Mth.clamp(pitch, 0.05f, 4.0f)
            baseVolume = Mth.clamp(volume, 0.0f, 8.0f)
            this.soundRange = soundRange.takeIf { it.isFinite() && it > 0.0f }
            updateVolumeForPosition()
            x = train.x
            y = train.y
            z = train.z
        }

        fun requestStop() {
            stop()
        }

        override fun tick() {
            if (!train.isAlive) {
                ACTIVE.remove(activeKey, this)
                AUTO_RUNNING.remove(train.uuid)
                stop()
                return
            }
            x = train.x
            y = train.y
            z = train.z
            updateVolumeForPosition()
        }

        private fun updateVolumeForPosition() {
            val range = soundRange
            volume = if (range == null) {
                baseVolume
            } else {
                Mth.clamp(
                    calcVolumeForRange(baseVolume, range, train.x.toFloat(), train.y.toFloat(), train.z.toFloat()),
                    0.0f,
                    8.0f,
                )
            }
        }
    }
}

