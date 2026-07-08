// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.model

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import cc.mirukuneko.realtrainmodrenewed.modelpack.VehicleModelPackManager
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import net.minecraft.resources.Identifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ModelLoader {
    private val MODEL_CACHE: MutableMap<String, MQOModel> = ConcurrentHashMap()

    @JvmStatic
    fun loadModel(definition: VehicleDefinition): MQOModel? {
        val cacheKey = "${definition.packName}:${definition.modelFile}"
        MODEL_CACHE[cacheKey]?.let {
            RealTrainModRenewed.LOGGER.info("Returning cached model: {}", cacheKey)
            return it
        }

        RealTrainModRenewed.LOGGER.info("Loading model: {} from pack: {}", definition.modelFile, definition.packName)

        return try {
            val packPath = resolvePackPath(definition.packName)
                ?: run { RealTrainModRenewed.LOGGER.error("Pack path not found for: {}", definition.packName); return null }

            RealTrainModRenewed.LOGGER.info("Pack path resolved: {}", packPath)

            val model = if (Files.isDirectory(packPath))
                loadFromDirectory(packPath, definition.modelFile)
            else
                loadFromZip(packPath, definition.modelFile)

            if (model != null) {
                RealTrainModRenewed.LOGGER.info("Model loaded successfully: {}", definition.modelFile)
                if (definition.hasScript()) loadScriptForModel(model, definition)
                MODEL_CACHE[cacheKey] = model
            } else {
                RealTrainModRenewed.LOGGER.warn("Model returned null: {}", definition.modelFile)
            }
            model
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.error("Failed to load model {} from pack {}", definition.modelFile, definition.packName, e)
            null
        }
    }

    private fun loadScriptForModel(model: MQOModel, definition: VehicleDefinition) {
        try {
            val packPath = resolvePackPath(definition.packName) ?: return
            val normalized = normalizeScriptPath(definition.scriptPath)
            val scriptLeaf = if (normalized.contains("/")) normalized.substringAfterLast('/') else normalized

            if (normalized.isNotBlank()) {
                try {
                    val legacyScript: String? = null // VehicleModelPackManager.getScript stub
                    if (!legacyScript.isNullOrBlank()) {
                        RealTrainModRenewed.LOGGER.info("Loaded legacy script from resource manager: {}", normalized)
                        TrainScriptSystem.loadScript(normalized, legacyScript, model)
                        return
                    }
                } catch (_: Exception) { }
            }

            if (Files.isDirectory(packPath)) {
                var scriptPath = packPath.resolve(normalized)
                if (!Files.exists(scriptPath)) scriptPath = resolveFilePath(packPath, normalized)
                if (scriptPath == null || !Files.exists(scriptPath))
                    scriptPath = resolveFilePath(packPath, scriptLeaf)
                if (scriptPath != null && Files.exists(scriptPath))
                    TrainScriptSystem.loadScript(scriptPath.toString(), model)
                else
                    RealTrainModRenewed.LOGGER.warn("Script {} not found in pack {}", normalized, packPath)
            } else {
                PackZipReader.openZipFile(packPath).use { zf ->
                    val entry = findEntry(zf, normalized)
                    if (entry != null) {
                        val script = zf.getInputStream(entry).use { it.readAllBytes().toString(StandardCharsets.UTF_8) }
                        TrainScriptSystem.loadScript(normalized, script, model)
                    } else {
                        RealTrainModRenewed.LOGGER.warn("Script {} not found in pack {}", normalized, packPath)
                    }
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.error("Failed to load script for model {}", definition.modelFile, e)
        }
    }

    private fun resolveFilePath(root: Path, relative: String): Path? {
        val norm = relative.replace('\\', '/')
        var candidate = root.resolve(norm)
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate
        candidate = root.resolve("assets/minecraft").resolve(norm)
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate
        val leaf = if (norm.contains("/")) norm.substringAfterLast('/') else norm
        Files.walk(root).use { stream ->
            for (file in stream) {
                if (!Files.isRegularFile(file)) continue
                val name = file.fileName.toString()
                if (name.equals(norm, ignoreCase = true) || name.equals(leaf, ignoreCase = true)) return file
            }
        }
        return null
    }

    private fun findEntry(zf: ZipFile, relative: String): ZipEntry? {
        val norm = relative.replace('\\', '/')
        zf.getEntry(norm)?.takeIf { !it.isDirectory }?.let { return it }
        zf.getEntry("assets/minecraft/$norm")?.takeIf { !it.isDirectory }?.let { return it }
        val leaf = if (norm.contains("/")) norm.substringAfterLast('/') else norm
        val leafLower = leaf.lowercase(Locale.ROOT)
        val en = zf.entries()
        while (en.hasMoreElements()) {
            val ze = en.nextElement()
            if (ze.isDirectory) continue
            val name = ze.name.replace('\\', '/')
            if (name.equals(norm, ignoreCase = true)) return ze
            val slash = name.lastIndexOf('/')
            val shortName = if (slash >= 0) name.substring(slash + 1) else name
            if (shortName.equals(leaf, ignoreCase = true) || shortName.equals(leafLower, ignoreCase = true)) return ze
        }
        return null
    }

    @JvmStatic
    fun loadBogieModel(bogieModelFile: String, parentDef: VehicleDefinition): MQOModel? {
        val cacheKey = "${parentDef.packName}:bogie:$bogieModelFile"
        MODEL_CACHE[cacheKey]?.let { return it }

        return try {
            val packPath = resolvePackPath(parentDef.packName) ?: return null
            val model = if (Files.isDirectory(packPath))
                loadFromDirectory(packPath, bogieModelFile)
            else
                loadFromZip(packPath, bogieModelFile)
            if (model != null) MODEL_CACHE[cacheKey] = model
            model
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.error("Failed to load bogie model {} from pack {}", bogieModelFile, parentDef.packName, e)
            null
        }
    }

    private fun loadFromDirectory(packDir: Path, modelFile: String): MQOModel? {
        val modelPath = packDir.resolve(modelFile.replace('\\', '/'))
        if (!Files.exists(modelPath)) return null
        val compressed = modelFile.lowercase().endsWith(".mqoz")
        Files.newInputStream(modelPath).use { return MQOParser.parse(it, compressed) }
    }

    private fun loadFromZip(zipPath: Path, modelFile: String): MQOModel? {
        var result: MQOModel? = null
        Files.newInputStream(zipPath).use { input ->
            PackZipReader.read(input) { entry, entryInput ->
                if (result != null || entry.isDirectory) return@read
                val name = entry.name.replace('\\', '/')
                if (name.equals(modelFile, ignoreCase = true) || name.endsWith("/$modelFile")) {
                    val compressed = modelFile.lowercase().endsWith(".mqoz")
                    result = MQOParser.parse(entryInput, compressed)
                }
            }
        }
        return result
    }

    private fun resolvePackPath(packName: String): Path? {
        return try {
            val gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
            val modsDir = gameDir.resolve("mods")
            var packPath = modsDir.resolve(packName)
            if (Files.exists(packPath)) return packPath
            val vehiclePacksDir = gameDir.resolve("vehicle_packs")
            packPath = vehiclePacksDir.resolve(packName)
            if (Files.exists(packPath)) packPath else null
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.error("Failed to resolve pack path for {}", packName, e)
            null
        }
    }

    private fun normalizeScriptPath(scriptPath: String?): String =
        scriptPath?.replace('\\', '/')?.trimStart('/') ?: ""

    @JvmStatic
    fun resolveTexture(texturePath: String?): Identifier? {
        if (texturePath.isNullOrBlank()) return null
        var path = texturePath
        if (path.endsWith(".png")) path = path.substring(0, path.length - 4)
        if (path.startsWith("textures/")) return Identifier.tryParse(path)
        Identifier.tryParse(path)?.let { return it }
        return Identifier.tryParse("realtrainmodunofficial:$path")
    }
}
