// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.sound

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import cc.mirukuneko.realtrainmodrenewed.util.sanitizeSoundPath as sanitizeLegacySoundPath
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.AddPackFindersEvent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.Optional

object ExternalSoundPackBridge {
    private const val PACK_ID = "realtrainmodunofficial:external_sound_bridge"
    private val PACK_TITLE: Component = Component.literal("RTM External Sounds")
    private val GENERATED_PACK_ROOT: Path = FMLPaths.GAMEDIR.get()
        .resolve("config")
        .resolve("realtrainmodunofficial")
        .resolve("generated_sound_pack")

    @JvmStatic
    fun register(event: AddPackFindersEvent) {
        if (event.packType != PackType.CLIENT_RESOURCES) {
            return
        }
        val packRoot = rebuildGeneratedPack() ?: return
        val pack = Pack.readMetaAndCreate(
            PackLocationInfo(PACK_ID, PACK_TITLE, PackSource.BUILT_IN, Optional.empty()),
            PathPackResources.PathResourcesSupplier(packRoot),
            PackType.CLIENT_RESOURCES,
            PackSelectionConfig(true, Pack.Position.TOP, false),
        )
        if (pack == null) {
            RealTrainModRenewed.LOGGER.warn("Generated external sound bridge pack could not be registered")
            return
        }
        event.addRepositorySource { consumer -> consumer.accept(pack) }
    }

    private fun rebuildGeneratedPack(): Path? {
        try {
            deleteDirectoryIfExists(GENERATED_PACK_ROOT)
            Files.createDirectories(GENERATED_PACK_ROOT)
            val mergedSoundDefs = HashMap<String, JsonObject>()
            var copiedAnySoundAsset = false
            for (candidate in collectCandidatePacks()) {
                try {
                    if (Files.isDirectory(candidate)) {
                        copiedAnySoundAsset = copiedAnySoundAsset or collectFromDirectory(candidate, mergedSoundDefs)
                    } else if (isSupportedArchive(candidate)) {
                        copiedAnySoundAsset = copiedAnySoundAsset or collectFromArchive(candidate, mergedSoundDefs)
                    }
                } catch (exception: Exception) {
                    RealTrainModRenewed.LOGGER.debug("Could not scan external sound assets from {}", candidate, exception)
                }
            }
            val wroteAnyJson = writeMergedSoundsJson(mergedSoundDefs)
            repairEventKeySoundReferences()
            if (!copiedAnySoundAsset && !wroteAnyJson) {
                deleteDirectoryIfExists(GENERATED_PACK_ROOT)
                return null
            }
            writePackMeta()
            return GENERATED_PACK_ROOT
        } catch (exception: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not build external sound bridge pack", exception)
            return null
        }
    }

    private fun collectCandidatePacks(): List<Path> {
        val gameDir = FMLPaths.GAMEDIR.get()
        val roots = listOf(
            gameDir.resolve("mods"),
            gameDir.resolve("content"),
            gameDir.resolve("vehicle_packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("vehicle_packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("rail_packs"),
        )
        val unique = LinkedHashSet<Path>()
        for (root in roots) {
            if (!Files.isDirectory(root)) {
                continue
            }
            try {
                Files.list(root).use { stream ->
                    stream.forEach { path ->
                        if (path != GENERATED_PACK_ROOT) {
                            unique.add(path)
                        }
                    }
                }
            } catch (exception: IOException) {
                RealTrainModRenewed.LOGGER.debug("Could not list sound candidate root {}", root, exception)
            }
        }
        return ArrayList(unique)
    }

    @Throws(IOException::class)
    private fun collectFromDirectory(packDir: Path, mergedSoundDefs: MutableMap<String, JsonObject>): Boolean {
        val rootNamespace = namespaceFromPackName(packDir.fileName.toString())
        var copiedAny = false
        val rootSoundsJson = packDir.resolve("sounds.json")
        if (Files.isRegularFile(rootSoundsJson)) {
            mergeSoundDefinitions(rootNamespace, Files.readString(rootSoundsJson), mergedSoundDefs)
            copiedAny = true
        }
        val rootSoundsDir = packDir.resolve("sounds")
        if (Files.isDirectory(rootSoundsDir)) {
            Files.walk(rootSoundsDir).use { walk ->
                for (source in walk.filter(Files::isRegularFile).toList()) {
                    val relative = rootSoundsDir.relativize(source)
                    val target = GENERATED_PACK_ROOT.resolve("assets").resolve(rootNamespace).resolve("sounds")
                        .resolve(sanitizedSoundAssetPath(relative))
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    registerCopiedSound(mergedSoundDefs, rootNamespace, relative)
                    copiedAny = true
                }
            }
        }
        val assetsDir = packDir.resolve("assets")
        if (!Files.isDirectory(assetsDir)) {
            return copiedAny
        }
        Files.list(assetsDir).use { namespaces ->
            for (namespaceDir in namespaces.toList()) {
                if (!Files.isDirectory(namespaceDir)) {
                    continue
                }
                val namespace = namespaceDir.fileName.toString().lowercase(Locale.ROOT)
                val soundsJson = namespaceDir.resolve("sounds.json")
                if (Files.isRegularFile(soundsJson)) {
                    mergeSoundDefinitions(namespace, Files.readString(soundsJson), mergedSoundDefs)
                    copiedAny = true
                }
                val soundsDir = namespaceDir.resolve("sounds")
                if (Files.isDirectory(soundsDir)) {
                    Files.walk(soundsDir).use { walk ->
                        for (source in walk.filter(Files::isRegularFile).toList()) {
                            val relative = soundsDir.relativize(source)
                            val target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds")
                                .resolve(sanitizedSoundAssetPath(relative))
                            Files.createDirectories(target.parent)
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                            registerCopiedSound(mergedSoundDefs, namespace, relative)
                            copiedAny = true
                        }
                    }
                }
            }
        }
        return copiedAny
    }

    @Throws(IOException::class)
    private fun collectFromArchive(archive: Path, mergedSoundDefs: MutableMap<String, JsonObject>): Boolean {
        var copiedAny = false
        val rootNamespace = namespaceFromPackName(archive.fileName.toString())
        PackZipReader.openZipFile(archive).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }
                val normalized = normalize(entry.name)
                val lower = normalized.lowercase(Locale.ROOT)
                if (lower == "sounds.json") {
                    zipFile.getInputStream(entry).use { input ->
                        mergeSoundDefinitions(rootNamespace, readUtf8(input), mergedSoundDefs)
                        copiedAny = true
                    }
                    continue
                }
                if (lower.startsWith("sounds/")) {
                    val parts = normalized.split("/").toTypedArray()
                    var target = GENERATED_PACK_ROOT.resolve("assets").resolve(rootNamespace).resolve("sounds")
                    for (i in 1 until parts.size) {
                        target = target.resolve(sanitizePathSegment(parts[i]))
                    }
                    Files.createDirectories(target.parent)
                    zipFile.getInputStream(entry).use { input ->
                        Files.write(
                            target,
                            input.readAllBytes(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE,
                        )
                    }
                    registerCopiedSound(mergedSoundDefs, rootNamespace, Path.of(parts.copyOfRange(1, parts.size).joinToString("/")))
                    copiedAny = true
                    continue
                }
                if (!lower.startsWith("assets/")) {
                    continue
                }
                val parts = normalized.split("/").toTypedArray()
                if (parts.size < 3) {
                    continue
                }
                val namespace = parts[1].lowercase(Locale.ROOT)
                if (lower == "assets/$namespace/sounds.json") {
                    zipFile.getInputStream(entry).use { input ->
                        mergeSoundDefinitions(namespace, readUtf8(input), mergedSoundDefs)
                        copiedAny = true
                    }
                    continue
                }
                if (parts.size >= 4 && "sounds".equals(parts[2], ignoreCase = true)) {
                    var target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds")
                    for (i in 3 until parts.size) {
                        target = target.resolve(sanitizePathSegment(parts[i]))
                    }
                    Files.createDirectories(target.parent)
                    zipFile.getInputStream(entry).use { input ->
                        Files.write(
                            target,
                            input.readAllBytes(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE,
                        )
                    }
                    registerCopiedSound(mergedSoundDefs, namespace, Path.of(parts.copyOfRange(3, parts.size).joinToString("/")))
                    copiedAny = true
                }
            }
        }
        return copiedAny
    }

    private fun sanitizedSoundAssetPath(path: Path): Path {
        var lowered = Path.of("")
        for (part in path) {
            lowered = lowered.resolve(sanitizePathSegment(part.toString()))
        }
        return lowered
    }

    private fun registerCopiedSound(mergedSoundDefs: MutableMap<String, JsonObject>, namespace: String, relativePath: Path?) {
        if (relativePath == null) {
            return
        }
        var soundPath = normalize(relativePath.toString()).lowercase(Locale.ROOT)
        if (!soundPath.endsWith(".ogg")) {
            return
        }
        soundPath = soundPath.substring(0, soundPath.length - ".ogg".length)
        soundPath = sanitizeSoundPath(soundPath)
        if (soundPath.isBlank()) {
            return
        }
        val eventKey = soundPath.replace('/', '.')
        val target = mergedSoundDefs.computeIfAbsent(namespace) { JsonObject() }
        if (target.has(eventKey)) {
            return
        }
        val event = JsonObject()
        event.addProperty("replace", true)
        val sounds = JsonArray()
        sounds.add("$namespace:$soundPath")
        event.add("sounds", sounds)
        target.add(eventKey, event)
    }

    private fun mergeSoundDefinitions(namespace: String, jsonText: String, mergedSoundDefs: MutableMap<String, JsonObject>) {
        try {
            val reader: Reader = StringReader(jsonText)
            reader.use {
                val parsed = JsonParser.parseReader(it)
                if (!parsed.isJsonObject) {
                    return
                }
                val target = mergedSoundDefs.computeIfAbsent(namespace) { JsonObject() }
                val source = parsed.asJsonObject
                for ((key, value) in source.entrySet()) {
                    val eventKey = sanitizeSoundEventKey(key)
                    if (eventKey.isNotBlank()) {
                        target.add(eventKey, normalizeSoundEvent(namespace, value))
                    }
                }
            }
        } catch (exception: Exception) {
            RealTrainModRenewed.LOGGER.debug("Could not merge sounds.json for namespace {}", namespace, exception)
        }
    }

    private fun normalizeSoundEvent(namespace: String, element: JsonElement?): JsonElement? {
        if (element == null || element.isJsonNull) {
            return element
        }
        if (element.isJsonArray) {
            val result = JsonArray()
            for (child in element.asJsonArray) {
                result.add(normalizeSoundEvent(namespace, child))
            }
            return result
        }
        if (!element.isJsonObject) {
            return element.deepCopy()
        }
        val copy = element.asJsonObject.deepCopy()
        copy.addProperty("replace", true)
        val sounds = copy["sounds"]
        if (sounds != null && sounds.isJsonArray) {
            val normalizedSounds = JsonArray()
            for (soundEntry in sounds.asJsonArray) {
                normalizedSounds.add(normalizeSoundReference(namespace, soundEntry))
            }
            copy.add("sounds", normalizedSounds)
        }
        return copy
    }

    private fun normalizeSoundReference(namespace: String, soundEntry: JsonElement?): JsonElement? {
        if (soundEntry == null || soundEntry.isJsonNull) {
            return soundEntry
        }
        if (soundEntry.isJsonPrimitive && soundEntry.asJsonPrimitive.isString) {
            return JsonPrimitive(namespacedSoundPath(namespace, soundEntry.asString))
        }
        if (!soundEntry.isJsonObject) {
            return soundEntry.deepCopy()
        }
        val copy = soundEntry.asJsonObject.deepCopy()
        val name = copy["name"]
        if (name != null && name.isJsonPrimitive && name.asJsonPrimitive.isString) {
            copy.addProperty("name", namespacedSoundPath(namespace, name.asString))
        }
        return copy
    }

    private fun namespacedSoundPath(namespace: String, raw: String?): String? {
        if (raw == null || raw.isBlank()) {
            return raw
        }
        val colon = raw.indexOf(':')
        val ns = if (colon >= 0) raw.substring(0, colon) else namespace
        var path = if (colon >= 0) raw.substring(colon + 1) else raw
        if (ns.isBlank() || "minecraft".equals(ns, ignoreCase = true)) {
            return raw
        }
        path = normalize(path)
        if (path.startsWith("sounds/")) {
            path = path.substring("sounds/".length)
        }
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length - ".ogg".length)
        }
        path = sanitizeSoundPath(path)
        if (path.isBlank()) {
            return raw
        }
        return ns.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]"), "_") + ":" + path
    }

    @Throws(IOException::class)
    private fun writeMergedSoundsJson(mergedSoundDefs: Map<String, JsonObject>): Boolean {
        var wroteAny = false
        for ((namespace, json) in mergedSoundDefs) {
            val target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds.json")
            Files.createDirectories(target.parent)
            Files.writeString(
                target,
                json.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            wroteAny = true
        }
        return wroteAny
    }

    @Throws(IOException::class)
    private fun writePackMeta() {
        val packFormat = kotlin.math.min(64, SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major())
        val packMeta = """
            {
              "pack": {
                "pack_format": %d,
                "description": "RTM external sound bridge"
              }
            }
            """.trimIndent().format(packFormat)
        Files.writeString(
            GENERATED_PACK_ROOT.resolve("pack.mcmeta"),
            packMeta,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun repairEventKeySoundReferences() {
        val assetsRoot = GENERATED_PACK_ROOT.resolve("assets")
        if (!Files.isDirectory(assetsRoot)) {
            return
        }
        try {
            Files.list(assetsRoot).use { namespaces ->
                for (namespaceDir in namespaces.toList()) {
                    val soundsJson = namespaceDir.resolve("sounds.json")
                    val soundsDir = namespaceDir.resolve("sounds")
                    if (!Files.isRegularFile(soundsJson) || !Files.isDirectory(soundsDir)) {
                        continue
                    }
                    val namespace = namespaceDir.fileName.toString()
                    val parsed = JsonParser.parseString(Files.readString(soundsJson))
                    if (!parsed.isJsonObject) {
                        continue
                    }
                    for ((eventKey, value) in parsed.asJsonObject.entrySet()) {
                        repairEventKeySoundElement(namespace, soundsDir, eventKey, value)
                    }
                    Files.writeString(
                        soundsJson,
                        parsed.asJsonObject.toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    )
                }
            }
        } catch (exception: Exception) {
            RealTrainModRenewed.LOGGER.debug("Could not repair event-key sound references", exception)
        }
    }

    private fun repairEventKeySoundElement(namespace: String, soundsDir: Path, eventKey: String, element: JsonElement?) {
        if (element == null || element.isJsonNull) {
            return
        }
        if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                repairEventKeySoundElement(namespace, soundsDir, eventKey, child)
            }
            return
        }
        if (!element.isJsonObject) {
            return
        }
        val obj = element.asJsonObject
        val sounds = obj["sounds"]
        if (sounds != null) {
            repairEventKeySoundElement(namespace, soundsDir, eventKey, sounds)
        }
        val name = obj["name"]
        if (name != null && name.isJsonPrimitive && name.asJsonPrimitive.isString) {
            val repaired = repairEventKeySoundReference(namespace, soundsDir, eventKey, name.asString)
            if (repaired != null) {
                obj.addProperty("name", repaired)
            }
        }
    }

    private fun repairEventKeySoundReference(namespace: String, soundsDir: Path, eventKey: String, rawReference: String?): String? {
        val ref = rawReference ?: ""
        val colon = ref.indexOf(':')
        val refNamespace = if (colon >= 0) ref.substring(0, colon) else namespace
        if (namespace != refNamespace) {
            return null
        }
        var soundPath = normalize(if (colon >= 0) ref.substring(colon + 1) else ref)
        if (soundPath.startsWith("sounds/")) {
            soundPath = soundPath.substring("sounds/".length)
        }
        if (soundPath.endsWith(".ogg")) {
            soundPath = soundPath.substring(0, soundPath.length - ".ogg".length)
        }
        val expected = soundsDir.resolve(sanitizedSoundAssetPath(Path.of("$soundPath.ogg")))
        if (Files.isRegularFile(expected)) {
            return null
        }
        val eventPath = sanitizeSoundPath(eventKey.replace('.', '/'))
        val eventSound = soundsDir.resolve(sanitizedSoundAssetPath(Path.of("$eventPath.ogg")))
        if (Files.isRegularFile(eventSound)) {
            return "$namespace:$eventPath"
        }
        return null
    }

    private fun normalize(raw: String): String = raw.replace('\\', '/').replaceFirst(Regex("^/+"), "")

    private fun sanitizeSoundPath(path: String): String = sanitizeLegacySoundPath(path)

    private fun sanitizePathSegment(segment: String): String {
        val sanitized = sanitizeSoundPath(segment)
        return if (sanitized.isBlank()) "_" else sanitized
    }

    private fun sanitizeSoundEventKey(key: String?): String {
        val sanitized = sanitizeSoundPath(key?.replace('\\', '/') ?: "")
        return sanitized.replace('/', '.')
    }

    private fun namespaceFromPackName(packName: String?): String {
        var base = packName ?: "rtm_pack"
        val dot = base.lastIndexOf('.')
        if (dot > 0) {
            base = base.substring(0, dot)
        }
        val normalized = base.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]"), "_")
        return if (normalized.isBlank()) "rtm_pack" else normalized
    }

    private fun isSupportedArchive(path: Path): Boolean {
        val name = path.fileName.toString().lowercase(Locale.ROOT)
        return name.endsWith(".zip") || name.endsWith(".jar")
    }

    @Throws(IOException::class)
    private fun readUtf8(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        input.transferTo(buffer)
        return buffer.toString(StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    private fun deleteDirectoryIfExists(root: Path) {
        if (!Files.exists(root)) {
            return
        }
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
