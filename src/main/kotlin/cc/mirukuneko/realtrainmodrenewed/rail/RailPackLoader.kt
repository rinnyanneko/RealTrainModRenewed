package cc.mirukuneko.realtrainmodrenewed.rail

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cc.mirukuneko.realtrainmodrenewed.BundledPackStore
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object RailPackLoader {
    private val LOADED: MutableList<RailDefinition> = ArrayList()
    private val VIRTUAL_PACKS: MutableMap<String, Path> = ConcurrentHashMap()
    @Volatile private var loaded = false

    @JvmStatic
    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        LOADED.clear()
        loadFromExternalDirectories()
        loadFromGameDirectories()
        loadFromModJar()
        RailRegistry.setDefinitions(LOADED)
        RealTrainModRenewed.LOGGER.info("Loaded {} rail definition(s)", LOADED.size)
    }

    private fun loadFromModJar() {
        try {
            for (path in BundledPackStore.listBundledPacks("rail")) {
                try {
                    Files.newInputStream(path).use { loadRailPack(it, path.fileName.toString()) }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not load bundled rail packs from mod jar", e)
        }
    }

    private fun loadFromExternalDirectories() {
        for (configRoot in configRoots()) {
            for (dirName in arrayOf("rail_packs", "packs", "")) {
                try {
                    var externalDir = configRoot
                    if (dirName.isNotEmpty()) externalDir = externalDir.resolve(dirName)
                    if (Files.isDirectory(externalDir)) loadArchiveDirectory(externalDir)
                } catch (e: Exception) {
                    RealTrainModRenewed.LOGGER.warn("Could not scan external rail packs {}", dirName, e)
                }
            }
        }
    }

    private fun loadFromGameDirectories() {
        try {
            val gameDir = FMLPaths.GAMEDIR.get()
            if (Files.isDirectory(gameDir)) {
                loadArchiveDirectory(gameDir)
                val modsDir = gameDir.resolve("mods")
                if (Files.isDirectory(modsDir)) loadArchiveDirectory(modsDir)
            }
            val contentDir = gameDir.resolve("content")
            if (Files.isDirectory(contentDir)) loadArchiveDirectory(contentDir)
            val vp = gameDir.resolve("vehicle_packs")
            if (Files.isDirectory(vp)) loadArchiveDirectory(vp)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not scan game directory for rail packs", e)
        }
    }

    @Throws(IOException::class)
    private fun loadArchiveDirectory(dir: Path) {
        Files.list(dir).use { stream ->
            stream.filter { isSupportedArchive(it) }.forEach { zipPath ->
                try {
                    Files.newInputStream(zipPath).use { input ->
                        val before = LOADED.size
                        loadRailPack(input, zipPath.fileName.toString())
                        val added = LOADED.size - before
                        if (added > 0)
                            RealTrainModRenewed.LOGGER.info("Loaded {} rail definition(s) from {}", added, zipPath.fileName)
                    }
                } catch (e: Exception) {
                    RealTrainModRenewed.LOGGER.warn("Failed to load rail pack {}", zipPath.fileName, e)
                }
            }
        }
    }

    private fun isSupportedArchive(path: Path): Boolean {
        val fileName = path.fileName.toString().lowercase(Locale.ROOT)
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".jar")) return false
        return !fileName.contains("realtrainmodunofficial")
            && !fileName.contains("rtm-official-assets")
            && !fileName.contains("kaizpatchx")
    }

    @JvmStatic
    @Synchronized
    fun reload() { loaded = false; load() }

    @Throws(IOException::class)
    private fun loadRailPack(zipInput: InputStream, packName: String) = loadRailPack(zipInput, packName, 0)

    @Throws(IOException::class)
    private fun loadRailPack(zipInput: InputStream, packName: String, depth: Int) {
        val jsonBytes = ArrayList<ByteArray>()
        val nestedArchives = ArrayList<NestedArchive>()
        PackZipReader.read(zipInput) { entry, zip ->
            if (!entry.isDirectory) {
                val name = normalize(entry.name)
                when {
                    isRailJson(name) -> jsonBytes.add(zip.readAllBytes())
                    depth < 2 && isArchiveName(name) -> nestedArchives.add(NestedArchive(name, zip.readAllBytes()))
                }
            }
        }
        for (bytes in jsonBytes) parseRailJson(bytes, packName)
        for (nested in nestedArchives) {
            val materialized = materializeNestedPack(nested.name, nested.bytes)
            Files.newInputStream(materialized).use { input ->
                val before = LOADED.size
                loadRailPack(input, nested.name, depth + 1)
                val added = LOADED.size - before
                if (added > 0)
                    RealTrainModRenewed.LOGGER.info("Loaded {} rail definition(s) from nested pack {} in {}", added, nested.name, packName)
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun materializeNestedPack(nestedName: String?, bytes: ByteArray): Path {
        val leaf = if (nestedName.isNullOrBlank()) "nested_pack.zip"
        else normalize(nestedName).substringAfterLast('/')
        val safeName = leaf.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val hash = Integer.toHexString(bytes.contentHashCode())
        val cacheDir = configRoot().resolve("nested_pack_cache")
        Files.createDirectories(cacheDir)
        val cached = cacheDir.resolve("${hash}_$safeName")
        if (!Files.exists(cached) || Files.size(cached) != bytes.size.toLong())
            Files.write(cached, bytes)
        VIRTUAL_PACKS[leaf] = cached
        VIRTUAL_PACKS[nestedName ?: ""] = cached
        VIRTUAL_PACKS[cached.fileName.toString()] = cached
        return cached
    }

    private fun parseRailJson(bytes: ByteArray, packName: String) {
        try {
            val el = JsonParser.parseString(PackTextDecoder.decodeJson(bytes))
            if (!el.isJsonObject) return
            val obj = el.asJsonObject
            val railName = getString(obj, "railName")
            val id = railName ?: "rail"
            val displayName = railName ?: id
            var model = getObject(obj, "model")
                ?: getObject(obj, "railModel")
                ?: getObject(obj, "railModel2") ?: return
            val modelFile = getString(model, "modelFile")
            if (modelFile.isNullOrBlank()) return
            var scriptPath = getString(model, "rendererPath")
            if (scriptPath.isNullOrBlank()) scriptPath = getString(obj, "scriptPath")
            val buttonTexture = firstNonBlank(getString(obj, "buttonTexture"), getString(model, "buttonTexture"))
            val tex = parseTextures(model)
            val offset = parseVec3(model, "offset", 1.0 / 16.0)
            val scale = parseFloat(model, "scale", 1.0F)

            var ballast = 0
            var ballastBlockId = ""
            if (obj.has("ballastWidth")) ballast = readIntSafe(obj.get("ballastWidth"), 0)
            else if (model.has("ballastWidth")) ballast = readIntSafe(model.get("ballastWidth"), 0)

            val ballastEl = if (obj.has("defaultBallast")) obj.get("defaultBallast")
            else model.get("defaultBallast")
            if (ballastEl != null) {
                val firstBlock = firstBallastBlockName(ballastEl)
                if (!firstBlock.isNullOrBlank()) {
                    ballastBlockId = normalizeBlockId(firstBlock)
                    if (ballast <= 0) ballast = 3
                } else if (ballastEl.isJsonPrimitive && ballastEl.asJsonPrimitive.isNumber) {
                    ballast = readIntSafe(ballastEl, ballast)
                }
            }
            if (ballast <= 0 && ballastBlockId.isEmpty()) {
                val idLower = (id ?: "").lowercase(Locale.ROOT)
                val fileLower = modelFile.lowercase(Locale.ROOT)
                if (idLower.contains("1067mm") || idLower.contains("1435mm") || idLower.contains("1524mm")
                    || fileLower.contains("1067mm") || fileLower.contains("1435mm") || fileLower.contains("1524mm")
                    || idLower.contains("762mm") || fileLower.contains("762mm"))
                    ballast = 3
            }
            LOADED.add(RailDefinition(id, displayName, packName, packName, modelFile, scriptPath ?: "",
                buttonTexture, tex, offset, scale, ballast, ballastBlockId))
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to parse rail json in {}: {}", packName, e.message)
        }
    }

    // JSON helpers (same as VehiclePackLoader)
    private fun getString(obj: JsonObject, key: String): String? = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun getObject(obj: JsonObject, key: String): JsonObject? = obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun parseTextures(model: JsonObject): Map<String, String> {
        val tex = HashMap<String, String>()
        val textures = model.get("textures") ?: return tex
        if (!textures.isJsonObject) return tex
        for ((key, value) in textures.asJsonObject.entrySet()) {
            if (value.isJsonPrimitive) tex[key] = value.asString
        }
        return tex
    }

    private fun parseVec3(obj: JsonObject, key: String, scale: Double): Vec3 {
        val arr = obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return Vec3.ZERO
        val list = ArrayList<Double>()
        for (e in arr) if (e.isJsonPrimitive) list.add(e.asDouble * scale)
        while (list.size < 3) list.add(0.0)
        return Vec3(list[0], list[1], list[2])
    }

    private fun parseFloat(obj: JsonObject, key: String, def: Float): Float =
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asFloat ?: def

    private fun firstNonBlank(a: String?, b: String?): String? =
        if (!a.isNullOrBlank()) a else b

    private fun readIntSafe(el: JsonElement?, def: Int): Int =
        try { el?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: def } catch (_: Exception) { def }

    private fun firstBallastBlockName(el: JsonElement): String? {
        return try {
            when {
                el.isJsonArray -> el.asJsonArray.firstNotNullOfOrNull { e ->
                    when {
                        e.isJsonObject -> getString(e.asJsonObject, "blockName")
                        e.isJsonPrimitive && e.asJsonPrimitive.isString -> e.asString
                        else -> null
                    }
                }
                el.isJsonObject -> getString(el.asJsonObject, "blockName")
                el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun normalizeBlockId(name: String): String {
        val n = name.trim().lowercase(Locale.ROOT)
        if (n.isEmpty() || n == "air" || n == "minecraft:air") return ""
        return if (n.contains(":")) n else "minecraft:$n"
    }

    // Shared helpers
    private fun isRailJson(path: String): Boolean {
        val normalized = normalize(path).lowercase(Locale.ROOT)
        if (!normalized.endsWith(".json")) return false
        val leaf = normalized.substringAfterLast('/')
        return leaf.startsWith("rail_") || leaf.startsWith("modelrail_") || normalized.contains("/json/")
    }

    private fun isArchiveName(name: String): Boolean {
        val lower = normalize(name).lowercase(Locale.ROOT)
        return lower.endsWith(".zip") || lower.endsWith(".jar")
    }

    private fun normalize(path: String): String = path.replace('\\', '/')

    private fun configRoot(): Path = FMLPaths.GAMEDIR.get().resolve("config").resolve(RealTrainModRenewed.MODID)

    private fun configRoots(): List<Path> {
        val renewed = configRoot()
        val legacy = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial")
        return if (renewed == legacy) listOf(renewed) else listOf(renewed, legacy)
    }

    @JvmStatic
    fun resolvePackPath(packName: String): Path? {
        val gameDir = FMLPaths.GAMEDIR.get()
        val candidates = listOf(
            gameDir, gameDir.resolve("mods"), gameDir.resolve("content"),
            gameDir.resolve("vehicle_packs"), gameDir.resolve("rail_packs")
        )
        for (dir in candidates) {
            if (!Files.isDirectory(dir)) continue
            try {
                val found = Files.list(dir).use { stream ->
                    stream.filter { it.fileName.toString().equals(packName, ignoreCase = true) }.findFirst().orElse(null)
                }
                if (found != null) return found
            } catch (_: Exception) { }
        }
        val cached = VIRTUAL_PACKS[packName] ?: VIRTUAL_PACKS[packName.substringAfterLast('/')]
        if (cached != null && Files.exists(cached)) return cached
        val modJar = resolveModJar(packName)
        if (modJar != null) return modJar
        if (RealTrainModRenewed.MODID.equals(packName, ignoreCase = true)) {
            val officialModsDir = gameDir.resolve("mods")
            try {
                val found = Files.list(officialModsDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .filter {
                            val name = it.fileName.toString().lowercase(Locale.ROOT)
                            name.endsWith(".zip") && name.contains("rtm-official-assets")
                        }.findFirst().orElse(null)
                }
                if (found != null) return found
            } catch (_: Exception) { }
        }
        return null
    }

    private fun resolveModJar(packName: String): Path? {
        try {
            val modFileEntry = net.neoforged.fml.ModList.get().getModFileById(packName)
            return modFileEntry?.file?.filePath
        } catch (_: Exception) { }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun openPackStream(definition: RailDefinition?): InputStream? {
        val p = definition?.let { resolvePackPath(it.packName) } ?: return null
        return Files.newInputStream(p)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun openPackStreamByName(packName: String): InputStream? {
        val p = resolvePackPath(packName) ?: return null
        return Files.newInputStream(p)
    }

    @JvmStatic
    fun readScriptContent(definition: RailDefinition?): String? {
        if (definition == null || definition.scriptPath.isNullOrBlank()) return null
        val packPath = resolvePackPath(definition.packName) ?: return null
        val scriptPath = normalize(definition.scriptPath)
        val scriptFileName = scriptPath.substringAfterLast('/').lowercase()
        try {
            Files.newInputStream(packPath).use { input ->
                var result: String? = null
                PackZipReader.read(input) { entry, zip ->
                    val name = normalize(entry.name)
                    if (result == null && (name.equals(scriptPath, ignoreCase = true)
                            || name.lowercase().endsWith("/$scriptFileName")
                            || name.lowercase() == scriptFileName))
                        result = PackTextDecoder.readText(zip)
                }
                return result
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to read script {} from pack {}", definition.scriptPath, definition.packName, e)
        }
        return null
    }

    private data class NestedArchive(val name: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NestedArchive) return false
            return name == other.name && bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
    }
}
