package cc.mirukuneko.realtrainmodrenewed.installedobject

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object SpeakerSoundConfig {
    const val MAX_SOUND_ID: Int = 64

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private const val FILE_NAME = "speaker_sounds.json"
    private val sounds = arrayOfNulls<String>(MAX_SOUND_ID)
    private var loaded = false

    private fun configFile(): Path {
        return FMLPaths.GAMEDIR.get()
            .resolve("config")
            .resolve("realtrainmodunofficial")
            .resolve(FILE_NAME)
    }

    /** Loads speaker sound mappings from config. */
    @JvmStatic
    @Synchronized
    fun load() {
        loaded = true
        val file = configFile()
        if (!Files.exists(file)) {
            return
        }
        try {
            val json = Files.readString(file, StandardCharsets.UTF_8)
            val incoming = gson.fromJson(json, Array<String?>::class.java)
            if (incoming != null) {
                for (i in 0 until minOf(MAX_SOUND_ID, incoming.size)) {
                    sounds[i] = incoming[i]
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to load speaker_sounds.json", e)
        }
    }

    @Synchronized
    private fun save() {
        val file = configFile()
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(sounds), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            RealTrainModRenewed.LOGGER.warn("Failed to save speaker_sounds.json", e)
        }
    }

    /** Returns the sound name for an id in 1..64, or null if unset. */
    @JvmStatic
    @Synchronized
    fun getSound(id: Int): String? {
        if (!loaded) {
            load()
        }
        if (id !in 1..MAX_SOUND_ID) {
            return null
        }
        val sound = sounds[id - 1]
        if (sound.isNullOrBlank() || sound == "null") {
            return null
        }
        return sound
    }

    /** Sets a sound name for an id in 1..64, optionally saving server-side config. */
    @JvmStatic
    @Synchronized
    fun setSound(id: Int, sound: String?, saveToDisk: Boolean) {
        if (id !in 1..MAX_SOUND_ID) {
            return
        }
        sounds[id - 1] = if (sound.isNullOrBlank()) null else sound
        if (saveToDisk) {
            save()
        }
    }

    /** Snapshot for sync packets. Empty strings are used because STRING_UTF8 cannot encode null. */
    @JvmStatic
    @Synchronized
    fun snapshot(): Array<String> {
        if (!loaded) {
            load()
        }
        return Array(MAX_SOUND_ID) { i -> sounds[i] ?: "" }
    }

    /** Replaces all mappings from a client sync packet without saving. */
    @JvmStatic
    @Synchronized
    fun replaceAll(incoming: Array<String>?) {
        loaded = true
        for (i in 0 until MAX_SOUND_ID) {
            sounds[i] = if (incoming != null && i < incoming.size) incoming[i] else null
        }
    }
}
