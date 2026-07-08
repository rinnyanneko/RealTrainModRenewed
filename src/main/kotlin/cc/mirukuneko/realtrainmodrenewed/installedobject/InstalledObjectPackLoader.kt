package cc.mirukuneko.realtrainmodrenewed.installedobject

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cc.mirukuneko.realtrainmodrenewed.BundledPackStore
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import net.minecraft.world.phys.Vec3
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.regex.Pattern

object InstalledObjectPackLoader {
    private val LIGHT_STATE_PATTERN = Pattern.compile("S\\((\\d+)\\)")
    private val LIGHT_PARTS_PATTERN = Pattern.compile("P\\(([^)]+)\\)")
    private val LOADED: MutableList<InstalledObjectDefinition> = ArrayList()
    @Volatile private var loaded = false

    @JvmStatic @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        LOADED.clear()
        try {
            loadFromModJar()
            loadDirectoryPacks(FMLPaths.GAMEDIR.get())
            loadArchiveDirectory(FMLPaths.GAMEDIR.get())
            val modsDir = FMLPaths.GAMEDIR.get().resolve("mods")
            if (Files.isDirectory(modsDir)) { loadDirectoryPacks(modsDir); loadArchiveDirectory(modsDir) }
            val contentDir = FMLPaths.GAMEDIR.get().resolve("content")
            if (Files.isDirectory(contentDir)) { loadDirectoryPacks(contentDir); loadArchiveDirectory(contentDir) }
            for (root in configRoots()) {
                loadDirectoryPacks(root)
                loadArchiveDirectory(root)
                for (child in arrayOf("packs", "installed_object_packs", "rail_packs", "vehicle_packs")) {
                    val dir = root.resolve(child)
                    loadDirectoryPacks(dir)
                    loadArchiveDirectory(dir)
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not scan installed object packs", e)
        }
        InstalledObjectRegistry.setDefinitions(LOADED)
        RealTrainModRenewed.LOGGER.info("Loaded {} installed object definition(s)", LOADED.size)
    }

    private fun loadFromModJar() {
        try {
            val modFileEntry = ModList.get().getModFileById(RealTrainModRenewed.MODID) ?: return
            val modFile = modFileEntry.file
            val jsonDir = modFile.filePath.resolve("assets/minecraft/models/json")
            if (!Files.isDirectory(jsonDir)) return
            val packName = RealTrainModRenewed.MODID
            Files.list(jsonDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && isSupportedJson(normalize(it.fileName.toString())) }
                    .forEach { path ->
                        try { parse(normalize(path.fileName.toString()), Files.readAllBytes(path), packName) }
                        catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to load built-in installed object definition {}", path.fileName, e) }
                    }
            }
            for (path in BundledPackStore.listBundledPacks("installed_object")) {
                Files.newInputStream(path).use { loadPack(it, path.fileName.toString()) }
            }
            for (path in BundledPackStore.listBundledPacks("rail")) {
                Files.newInputStream(path).use { loadPack(it, path.fileName.toString()) }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not scan built-in installed object packs", e)
        }
    }

    @JvmStatic @Synchronized
    fun reload() { loaded = false; load() }

    @Throws(IOException::class)
    private fun loadDirectoryPacks(dir: Path) {
        if (!Files.isDirectory(dir)) return
        Files.list(dir).use { stream ->
            stream.filter { Files.isDirectory(it) && looksLikeInstalledObjectPackDirectory(it) }
                .forEach { path ->
                    try { loadPackDirectory(path, path.fileName.toString()) }
                    catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to load installed object directory pack {}", path.fileName, e) }
                }
        }
    }

    @Throws(IOException::class)
    private fun loadArchiveDirectory(dir: Path) {
        if (!Files.isDirectory(dir)) return
        Files.list(dir).use { stream ->
            stream.filter { isSupportedArchive(it) }.forEach { path ->
                try { Files.newInputStream(path).use { loadPack(it, path.fileName.toString()) } }
                catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to load installed object archive {}", path.fileName, e) }
            }
        }
    }

    private fun isSupportedArchive(path: Path): Boolean {
        val fileName = path.fileName.toString().lowercase(Locale.ROOT)
        return fileName.endsWith(".zip") || fileName.endsWith(".jar")
    }

    private fun looksLikeInstalledObjectPackDirectory(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) return false
        if (Files.exists(dir.resolve("assets")) || Files.exists(dir.resolve("models"))
            || Files.exists(dir.resolve("scripts"))) return true
        return try {
            Files.walk(dir, 4).use { stream ->
                stream.filter { Files.isRegularFile(it) }.map { it.fileName.toString().lowercase(Locale.ROOT) }
                    .anyMatch { name -> name.endsWith(".json") && (
                        name.startsWith("modelmachine_") || name.startsWith("modelsignal_")
                        || name.startsWith("modelconnector_") || name.startsWith("modelwire_")
                        || name.startsWith("modelcrossing_") || name.startsWith("signboard_")) }
            }
        } catch (_: IOException) { false }
    }

    @Throws(IOException::class)
    private fun loadPack(zipInput: InputStream, packName: String) = loadPack(zipInput, packName, 0)

    @Throws(IOException::class)
    private fun loadPack(zipInput: InputStream, packName: String, depth: Int) {
        val entries = ArrayList<EntryData>()
        val nestedArchives = ArrayList<NestedArchive>()
        PackZipReader.read(zipInput) { entry, zip ->
            if (!entry.isDirectory) {
                val normalized = normalize(entry.name)
                if (isSupportedJson(normalized)) entries.add(EntryData(normalized, zip.readAllBytes()))
                else if (depth < 2 && isArchiveName(normalized)) nestedArchives.add(NestedArchive(normalized, zip.readAllBytes()))
            }
        }
        for (e in entries) parse(e.path, e.bytes, packName)
        for (nested in nestedArchives) {
            val materialized = RailPackLoader.materializeNestedPack(nested.name, nested.bytes)
            Files.newInputStream(materialized).use { loadPack(it, nested.name, depth + 1) }
        }
    }

    @Throws(IOException::class)
    private fun loadPackDirectory(packDir: Path, packName: String) {
        Files.walk(packDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && isSupportedJson(normalize(packDir.relativize(it).toString())) }
                .forEach { path ->
                    try { parse(normalize(packDir.relativize(path).toString()), Files.readAllBytes(path), packName) }
                    catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to parse installed object json {} in {}", path, packName, e) }
                }
        }
    }

    private fun isSupportedJson(path: String): Boolean {
        val file = leaf(path).lowercase(Locale.ROOT)
        return file.endsWith(".json") && (
            file.startsWith("modelmachine_") || file.startsWith("modelsignal_")
            || file.startsWith("modelconnector_") || file.startsWith("modelwire_")
            || file.startsWith("modelcrossing_") || file.startsWith("signboard_"))
    }

    private fun isArchiveName(path: String): Boolean {
        val lower = normalize(path).lowercase(Locale.ROOT)
        return lower.endsWith(".zip") || lower.endsWith(".jar")
    }

    private fun parse(path: String, bytes: ByteArray, packName: String) {
        try {
            val element = JsonParser.parseString(PackTextDecoder.decodeJson(bytes))
            if (!element.isJsonObject) return
            val obj = element.asJsonObject
            val file = leaf(path)
            val lower = file.lowercase(Locale.ROOT)
            if (lower.startsWith("signboard_")) { parseSignboard(obj, packName, file); return }

            val category = categoryFor(obj, lower)
            val model = getObject(obj, "model")
            val modelPartsBody = getObject(obj, "modelPartsBody")
            val modelFile = firstNonBlank(model?.let { getString(it, "modelFile") }, getString(obj, "signalModel"))
            if (modelFile.isNullOrBlank()) return
            val name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName"), file.removeSuffix(".json"))
            val id = "${category.name.lowercase(Locale.ROOT)}:$packName:$name"
            val scriptPath = firstNonBlank(model?.let { getString(it, "rendererPath") }, getString(obj, "rendererPath"))
            val runningSound = firstNonBlank(
                model?.let { getString(it, "sound_Running") }, model?.let { getString(it, "soundRunning") },
                getString(obj, "sound_Running"), getString(obj, "soundRunning"))
            val offset = parseVec3(model, "offset", 1.0 / 16.0)
            val scale = parseFloat(model, "scale", 1.0F)
            val smoothing = getBoolean(obj, "smoothing", true)
            val textures = HashMap(parseTextures(model))
            val buttonTexture = firstNonBlank(getString(obj, "buttonTexture"), model?.let { getString(it, "buttonTexture") })
            val signTexture = firstNonBlank(getString(obj, "signTexture"), model?.let { getString(it, "signTexture") })
            val emissiveTexture = firstNonBlank(getString(obj, "emissiveTexture"), model?.let { getString(it, "emissiveTexture") })
            val signalLights = parseSignalLights(obj)
            val renderObjects = parseRenderObjects(obj, model, modelPartsBody)
            val signFrame = getInt(obj, "signFrame", 1)
            val backTexture = getInt(obj, "backTexture", 1)
            val scriptBodyPos = parseVec3(modelPartsBody, "offset", 1.0 / 16.0)

            val width = parseFloat(obj, "width", parseFloat(model, "width", 1.0F))
            val height = parseFloat(obj, "height", parseFloat(model, "height", 1.0F))
            val depth = parseFloat(obj, "depth", parseFloat(model, "depth", 0.125F))

            val wireSectionLength = parseFloat(obj, "sectionLength", parseFloat(model, "sectionLength", 0.5F))
            val wireDeflection = parseFloat(obj, "deflectionCoefficient", parseFloat(model, "deflectionCoefficient", 0.0F))
            val wireAttachPos = parseVec3(obj, "wireAttachPos", 1.0 / 16.0)

            val def = InstalledObjectDefinition(id ?: "", name ?: "", packName, category, modelFile ?: "",
                scriptPath ?: "", buttonTexture, textures, offset, scale, smoothing,
                width, height, depth, signTexture ?: "", emissiveTexture ?: "",
                runningSound ?: "", signalLights, renderObjects, scriptBodyPos, signFrame, backTexture)
            def.setWireParams(wireSectionLength, wireDeflection)
            def.setWireAttachPos(wireAttachPos)
            LOADED.add(def)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to parse installed object json {}: {}", path, e.message)
        }
    }

    private fun parseSignboard(obj: JsonObject, packName: String, file: String) {
        try {
            val name = firstNonBlank(getString(obj, "name"), getString(obj, "signboardName"), file.removeSuffix(".json"))
            val id = "signboard:$packName:$name"
            val modelFile = firstNonBlank(getString(obj, "modelFile"), getString(obj, "signboardModel"))
            if (modelFile.isNullOrBlank()) return
            val scriptPath = getString(obj, "rendererPath")
            val textures = HashMap(parseTextures(obj))
            val offset = parseVec3(obj, "offset", 1.0 / 16.0)
            val scale = parseFloat(obj, "scale", 1.0F)
            val width = parseFloat(obj, "width", 1.0F)
            val height = parseFloat(obj, "height", 1.0F)
            val depth = parseFloat(obj, "depth", 0.125F)
            val signTexture = getString(obj, "signTexture")
            val emissiveTexture = getString(obj, "emissiveTexture")
            val signFrame = getInt(obj, "signFrame", 1)
            val backTexture = getInt(obj, "backTexture", 1)
            val buttonTexture = getString(obj, "buttonTexture")
            val runningSound = firstNonBlank(getString(obj, "sound_Running"), getString(obj, "soundRunning"))

            LOADED.add(InstalledObjectDefinition(id ?: "", name ?: "", packName, InstalledObjectCategory.SIGNBOARD,
                modelFile ?: "", scriptPath ?: "", buttonTexture, textures, offset, scale, true,
                width, height, depth, signTexture ?: "", emissiveTexture ?: "",
                runningSound ?: "", emptyMap(), emptyList(), Vec3.ZERO, signFrame, backTexture))
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to parse signboard json {}: {}", file, e.message)
        }
    }

    private fun categoryFor(obj: JsonObject, lowerFile: String): InstalledObjectCategory {
        val hay = (getString(obj, "name") ?: "").lowercase(Locale.ROOT) + lowerFile
        if (lowerFile.startsWith("modelsignal_")) return InstalledObjectCategory.SIGNAL
        if (lowerFile.startsWith("modelwire_")) return InstalledObjectCategory.WIRE
        val looksLikeCrossing = lowerFile.startsWith("modelcrossing_") || obj.has("crossingGate") || obj.has("crossing_gate")
        val looksLikeSpeaker = obj.has("speaker") || obj.has("speakerSound") || obj.has("speaker_sound")
        if (lowerFile.startsWith("modelcrossing_") || looksLikeCrossing
            || containsAny(hay, "crossing", "fumikiri", "踏切", "toryanse")) return InstalledObjectCategory.CROSSING
        if (containsAny(hay, "turnstile", "ticketgate", "ticket_gate", "ticketmachine",
                "kaisatsu", "改札", "automaticgate", "iccard")) return InstalledObjectCategory.TICKET_GATE
        if (looksLikeSpeaker || containsAny(hay, "speaker", "スピーカ")) return InstalledObjectCategory.SPEAKER
        if (containsAny(hay, "linepole", "line_pole", "catenarypole", "catenary_pole",
                "poleglay", "架線柱", "架線")) return InstalledObjectCategory.OVERHEAD_LINE_POLE
        if (containsAny(hay, "signboard", "sign_board", "billboard", "看板")) return InstalledObjectCategory.SIGNBOARD
        if (containsAny(hay, "light", "lamp", "lantern", "照明", "ライト", "beacon")) return InstalledObjectCategory.LIGHT
        if (lowerFile.startsWith("modelmachine_")) return InstalledObjectCategory.LIGHT
        return InstalledObjectCategory.INSULATOR
    }

    private fun parseVec3(obj: JsonObject?, key: String, scale: Double): Vec3 {
        val arr = obj?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return Vec3.ZERO
        if (arr.size() < 3) return Vec3.ZERO
        return try { Vec3(arr[0].asDouble * scale, arr[1].asDouble * scale, arr[2].asDouble * scale) }
        catch (_: Exception) { Vec3.ZERO }
    }

    private fun parseFloat(obj: JsonObject?, key: String, fallback: Float): Float =
        try { obj?.get(key)?.asFloat ?: fallback } catch (_: Exception) { fallback }

    private fun getInt(obj: JsonObject, key: String, fallback: Int): Int =
        try { obj.get(key)?.asInt ?: fallback } catch (_: Exception) { fallback }

    private fun getBoolean(obj: JsonObject, key: String, fallback: Boolean): Boolean =
        try { obj.get(key)?.asBoolean ?: fallback } catch (_: Exception) { fallback }

    private fun parseTextures(modelObj: JsonObject?): Map<String, String> {
        val arr = modelObj?.get("textures")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyMap()
        val textures = HashMap<String, String>()
        for (element in arr) {
            if (!element.isJsonArray) continue
            val pair = element.asJsonArray
            if (pair.size() < 2) continue
            val material = pair[0].asString
            val texture = pair[1].asString
            if (material.isNotBlank() && texture.isNotBlank()) textures[material] = encodeTextureDescriptor(pair)
        }
        return textures
    }

    private fun parseRenderObjects(vararg objects: JsonObject?): List<String> {
        val result = mutableListOf<String>()
        for (obj in objects) {
            val arr = obj?.get("objects")?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            for (element in arr) {
                if (!element.isJsonPrimitive) continue
                val value = element.asString
                if (value.isNotBlank() && result.none { it.equals(value, ignoreCase = true) })
                    result.add(value.trim())
            }
        }
        return result
    }

    private fun encodeTextureDescriptor(pair: JsonArray): String {
        val texture = pair[1].asString
        if (pair.size() < 3) return texture
        val flags = (2 until pair.size()).mapNotNull { i ->
            val option = pair[i]
            if (option.isJsonPrimitive) option.asString.takeIf { it.isNotBlank() }?.trim() else null
        }
        return if (flags.isEmpty()) texture else "$texture|ptmeta=${flags.joinToString(",")}"
    }

    private fun parseSignalLights(obj: JsonObject): Map<Int, List<String>> {
        val arr = obj.get("lights")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyMap()
        val lights = HashMap<Int, List<String>>()
        for (element in arr) {
            if (!element.isJsonPrimitive) continue
            val line = element.asString
            val stateMatcher = LIGHT_STATE_PATTERN.matcher(line)
            val partsMatcher = LIGHT_PARTS_PATTERN.matcher(line)
            if (!stateMatcher.find() || !partsMatcher.find()) continue
            val state = stateMatcher.group(1).toInt()
            val parts = partsMatcher.group(1).trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) lights[state] = parts
        }
        return lights
    }

    private fun containsAny(hay: String?, vararg needles: String): Boolean {
        if (hay == null) return false
        return needles.any { hay.contains(it) }
    }

    private fun normalize(value: String): String = value.replace('\\', '/')
    private fun leaf(value: String): String = value.substringAfterLast('/')
    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun configRoot(): Path = FMLPaths.GAMEDIR.get().resolve("config").resolve(RealTrainModRenewed.MODID)

    private fun configRoots(): List<Path> {
        val renewed = configRoot()
        val legacy = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial")
        return if (renewed == legacy) listOf(renewed) else listOf(renewed, legacy)
    }

    private fun getObject(obj: JsonObject?, key: String): JsonObject? =
        obj?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun getString(obj: JsonObject?, key: String): String? =
        obj?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    @JvmStatic
    fun getLoadedDefinitions(): List<InstalledObjectDefinition> = LOADED.toList()

    @JvmStatic
    fun resolvePackPath(packName: String): Path? = RailPackLoader.resolvePackPath(packName)

    private data class EntryData(val path: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is EntryData && path == other.path && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
    }

    private data class NestedArchive(val name: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is NestedArchive && name == other.name && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
    }
}
