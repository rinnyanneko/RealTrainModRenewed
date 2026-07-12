// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.voicetext

import net.minecraft.util.Mth
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.concurrent.thread

/**
 * VoiceText Web API text-to-speech client. Playback uses a javax [Clip].
 */
open class VoiceText(private val key: String?) {
    private var text: String? = null
    private var speaker: Speaker? = null
    private var format: Format? = null
    private var emotion: Emotion? = null
    private var emotionLevel = 0
    private var pitch = 0
    private var speed = 0
    private var volume = 0

    fun setText(text: String?): VoiceText {
        this.text = text
        return this
    }

    fun setSpeaker(speaker: Speaker?): VoiceText {
        this.speaker = speaker
        return this
    }

    fun setFormat(format: Format?): VoiceText {
        this.format = format
        return this
    }

    fun setEmotion(emotion: Emotion?, level: Int): VoiceText {
        this.emotion = emotion
        emotionLevel = Mth.clamp(level, 1, 4)
        return this
    }

    fun setPitch(pitch: Int): VoiceText {
        this.pitch = Mth.clamp(pitch, 50, 200)
        return this
    }

    fun setSpeed(speed: Int): VoiceText {
        this.speed = Mth.clamp(speed, 50, 400)
        return this
    }

    fun setVolume(volume: Int): VoiceText {
        this.volume = Mth.clamp(volume, 50, 200)
        return this
    }

    fun playSound() {
        thread(name = "ATSAssist-VoiceText") {
            getAudioInputStream()?.let(::play)
        }
    }

    fun getBytes(): ByteArray? {
        val text = text
        val speaker = speaker
        if (text == null || speaker == null) {
            return null
        }
        return try {
            val output = ByteArrayOutputStream()
            output.write(("text=$text").toByteArray(StandardCharsets.UTF_8))
            output.write('&'.code)
            output.write(("speaker=$speaker").toByteArray())
            format?.let {
                output.write('&'.code)
                output.write(("format=$it").toByteArray())
            }
            emotion?.let {
                output.write('&'.code)
                output.write(("emotion=$it").toByteArray())
                output.write(("emotion_level=$emotionLevel").toByteArray())
            }
            if (pitch != 0) {
                output.write('&'.code)
                output.write(("pitch=$pitch").toByteArray())
            }
            if (speed != 0) {
                output.write('&'.code)
                output.write(("speed=$speed").toByteArray())
            }
            if (volume != 0) {
                output.write('&'.code)
                output.write(("volume=$volume").toByteArray())
            }
            output.toByteArray()
        } catch (_: IOException) {
            null
        }
    }

    fun getAudioInputStream(): AudioInputStream? {
        val bytes = getBytes()
        if (key == null || text == null || speaker == null || bytes == null) {
            return null
        }
        return getAudioInputStream(key, bytes)
    }

    companion object {
        private const val BASE_URL = "https://api.voicetext.jp/v1/tts"
        private const val UA_VERSION = "1.8.0"

        @JvmStatic
        fun getAudioInputStream(key: String, bytes: ByteArray): AudioInputStream? =
            try {
                val connection = URL(BASE_URL).openConnection() as HttpURLConnection
                connection.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString("$key:".toByteArray()),
                )
                connection.setRequestProperty("User-Agent", String.format("ATSAssist_%s:FromMinecraft", UA_VERSION))
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.write(bytes)
                if (connection.responseCode != 200) {
                    null
                } else {
                    AudioSystem.getAudioInputStream(BufferedInputStream(connection.inputStream))
                }
            } catch (_: IOException) {
                null
            } catch (_: UnsupportedAudioFileException) {
                null
            }

        private fun play(audioInputStream: AudioInputStream) {
            try {
                val clip: Clip = AudioSystem.getClip()
                clip.open(audioInputStream)
                clip.start()
            } catch (_: LineUnavailableException) {
                // Ignore playback failure.
            } catch (_: IOException) {
                // Ignore playback failure.
            }
        }
    }
}
