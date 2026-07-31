// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.vehicle

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object VehiclePackLoader {
    private val LOADED: MutableList<VehicleDefinition> = ArrayList()
    @Volatile private var loaded = false

    @JvmStatic @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        LOADED.clear()
        loadFromModJar()
        loadFromExternalDirectories()
        loadFromGameDirectories()
        VehicleRegistry.setDefinitions(LOADED)
        RealTrainModRenewed.LOGGER.info("Loaded {} vehicle definition(s)", LOADED.size)
    }

    private fun loadFromModJar() {
        try {
            val modFileEntry = ModList.get().getModFileById(RealTrainModRenewed.MODID) ?: return
            val modFile = modFileEntry.file
            val jsonDir = modFile.filePath.resolve("assets/minecraft/models/json")
            if (!Files.isDirectory(jsonDir)) return
            var count = 0
            Files.list(jsonDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.filter {
                    val n = it.fileName.toString().lowercase(Locale.ROOT)
                    n.startsWith("modelvehicle_") && n.endsWith(".json")
                }.forEach { p ->
                    try { parseTrainJson(Files.readAllBytes(p), RealTrainModRenewed.MODID, p.fileName.toString()); count++ }
                    catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to load bundled vehicle {}", p, e) }
                }
            }
            RealTrainModRenewed.LOGGER.info("Loaded {} bundled car definition(s)", count)
        } catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Could not load bundled car definitions", e) }
    }

    private fun loadFromExternalDirectories() {
        for (configRoot in configRoots()) {
            for (dirName in arrayOf("vehicle_packs", "packs", "")) {
                try {
                    var externalDir = configRoot
                    if (dirName.isNotEmpty()) externalDir = externalDir.resolve(dirName)
                    if (Files.exists(externalDir)) scanPackRoot(externalDir)
                } catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Could not scan external vehicle packs {}", dirName, e) }
            }
        }
    }

    private fun loadFromGameDirectories() {
        try {
            val gameDir = FMLPaths.GAMEDIR.get()
            if (Files.exists(gameDir)) {
                scanPackRoot(gameDir)
                val modsDir = gameDir.resolve("mods")
                if (Files.exists(modsDir)) scanPackRoot(modsDir)
            }
            val contentDir = gameDir.resolve("content")
            if (Files.exists(contentDir)) scanPackRoot(contentDir)
            val vehiclePacksDir = gameDir.resolve("vehicle_packs")
            if (Files.exists(vehiclePacksDir)) scanPackRoot(vehiclePacksDir)
        } catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Could not scan game directory for vehicle packs", e) }
    }

    @Throws(IOException::class)
    private fun scanPackRoot(dir: Path) {
        if (!Files.isDirectory(dir)) return
        Files.list(dir).use { stream ->
            stream.forEach { path ->
                try {
                    val lowerName = path.fileName.toString().lowercase(Locale.ROOT)
                    if (lowerName.contains("rtm-official-assets") || lowerName.contains("kaizpatchx")
                        || lowerName.contains("realtrainmodunofficial")) return@forEach
                    if (Files.isDirectory(path) && looksLikeVehiclePackDirectory(path))
                        loadVehiclePackDirectory(path)
                    else if (isSupportedArchive(path))
                        loadVehicleZip(path)
                } catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to scan vehicle pack {}", path.fileName, e) }
            }
        }
    }

    private fun looksLikeVehiclePackDirectory(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) return false
        if (Files.exists(dir.resolve("assets")) || Files.exists(dir.resolve("scripts"))
            || Files.exists(dir.resolve("textures"))) return true
        return try {
            Files.walk(dir, 4).use { stream ->
                stream.filter { Files.isRegularFile(it) }.map { it.fileName.toString().lowercase(Locale.ROOT) }
                    .anyMatch { name -> name.endsWith(".json") && (
                        name.startsWith("modeltrain_") || name.startsWith("train_")
                        || name.startsWith("modelvehicle_") || name.startsWith("vehicle_")) }
            }
        } catch (_: IOException) { false }
    }

    @Throws(IOException::class)
    private fun loadVehicleZip(zipPath: Path) {
        Files.newInputStream(zipPath).use { loadVehiclePack(it, zipPath.fileName.toString()) }
    }

    private fun isSupportedArchive(path: Path): Boolean {
        val fileName = path.fileName.toString().lowercase(Locale.ROOT)
        return (fileName.endsWith(".zip") || fileName.endsWith(".jar")) && !isOwnModJar(path)
    }

    private fun isOwnModJar(path: Path): Boolean {
        return try {
            val modFile = ModList.get().getModFileById(RealTrainModRenewed.MODID) ?: return false
            val ownArchive = modFile.file.filePath
            ownArchive != null && Files.isSameFile(path, ownArchive)
        } catch (_: Exception) { false }
    }

    @Throws(IOException::class)
    private fun loadVehiclePackDirectory(packDir: Path) {
        val packName = packDir.fileName.toString()
        Files.walk(packDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && isTrainJson(normalize(packDir.relativize(it).toString())) }
                .forEach { path ->
                    try { parseTrainJson(Files.readAllBytes(path), packName, normalize(packDir.relativize(path).toString())) }
                    catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to load vehicle pack json {} in {}", path, packName, e) }
                }
        }
    }

    @JvmStatic @Synchronized
    fun reload() { loaded = false; load() }

    @Throws(IOException::class)
    private fun loadVehiclePack(zipInput: InputStream, packName: String) = loadVehiclePack(zipInput, packName, 0)

    @Throws(IOException::class)
    private fun loadVehiclePack(zipInput: InputStream, packName: String, depth: Int) {
        val jsonBytes = ArrayList<ByteArray>()
        val jsonPaths = ArrayList<String>()
        val nestedArchives = ArrayList<Pair<String, ByteArray>>()
        PackZipReader.read(zipInput) { entry, zip ->
            if (!entry.isDirectory) {
                val name = normalize(entry.name)
                if (isTrainJson(name)) { jsonBytes.add(zip.readAllBytes()); jsonPaths.add(name) }
                else if (depth < 2 && isArchiveName(name)) nestedArchives.add(Pair(name, zip.readAllBytes()))
            }
        }
        for (i in jsonBytes.indices) parseTrainJson(jsonBytes[i], packName, jsonPaths[i])
        for ((nestedName, nestedBytes) in nestedArchives) {
            val materialized = RailPackLoader.materializeNestedPack(nestedName, nestedBytes)
            Files.newInputStream(materialized).use { loadVehiclePack(it, nestedName, depth + 1) }
        }
    }

    private fun isTrainJson(path: String): Boolean {
        val normalized = normalize(path).lowercase(Locale.ROOT)
        if (!normalized.endsWith(".json")) return false
        val leaf = normalized.substringAfterLast('/')
        return leaf.startsWith("modeltrain_") || leaf.startsWith("train_")
            || leaf.startsWith("modelvehicle_") || leaf.startsWith("vehicle_")
    }

    private fun isArchiveName(name: String): Boolean {
        val lower = normalize(name).lowercase(Locale.ROOT)
        return lower.endsWith(".zip") || lower.endsWith(".jar")
    }

    private fun parseTrainJson(bytes: ByteArray, packName: String, path: String) {
        try {
            val el = JsonParser.parseString(PackTextDecoder.decodeJson(bytes))
            if (!el.isJsonObject) return
            val obj = el.asJsonObject
            val lowerPath = path.lowercase(Locale.ROOT)
            val isCar = lowerPath.contains("modelvehicle_") || lowerPath.contains("vehicle_")
            val modelObject = getObject(obj, "ModelTrain")
                ?: getObject(obj, "modelTrain")
                ?: getObject(obj, "trainModel3")
                ?: getObject(obj, "trainModel2")
                ?: getObject(obj, "trainModel")
                ?: getObject(obj, "modelTrain3")
                ?: getObject(obj, "modelTrain2")
                ?: getObject(obj, "ModelVehicle")
                ?: getObject(obj, "modelVehicle")
                ?: getObject(obj, "vehicleModel3")
                ?: getObject(obj, "vehicleModel2")
                ?: getObject(obj, "vehicleModel")
                ?: if (isTrainJson(path)) getObject(obj, "model") else null
            val trainModel = modelObject ?: obj.takeIf { getString(it, "modelFile")?.isNotBlank() == true } ?: return

            val modelFile = firstNonBlank(getString(trainModel, "modelFile"), getString(obj, "modelFile")) ?: return
            val displayName = firstNonBlank(
                getString(obj, "trainName"), getString(obj, "vehicleName"),
                getString(trainModel, "name"), getString(obj, "name"),
                path.substringAfterLast('/').removeSuffix(".json")
            )
            val id = firstNonBlank(getString(obj, "id"), displayName)
            val scriptPath = firstNonBlank(getString(trainModel, "rendererPath"), getString(obj, "rendererPath"),
                getString(trainModel, "scriptPath"), getString(obj, "scriptPath")) ?: ""
            val soundScriptPath = firstNonBlank(getString(trainModel, "soundScriptPath"), getString(obj, "soundScriptPath"),
                getString(trainModel, "soundRendererPath"), getString(obj, "soundRendererPath")) ?: ""
            val vehicleType = if (isCar) "car" else "train"
            val doorType = firstNonBlank(getString(trainModel, "doorType"), getString(obj, "doorType")) ?: ""
            val buttonTexture = firstNonBlank(getString(obj, "buttonTexture"), getString(trainModel, "buttonTexture"))
            val tex = parseTextures(trainModel)
            val offset = if (trainModel.has("offset")) {
                parseVec3(trainModel, "offset", 1.0 / 16.0)
            } else {
                parseVec3(obj, "offset", 1.0 / 16.0)
            }
            val scale = parseFloat(trainModel, "scale", parseFloat(obj, "scale", 1.0F))

            val bogies = parseBogies(obj, trainModel)
            val playerPositions = parseVec3List(obj, trainModel, "playerPos", "playerPositions", "PosF")
            val seatPositions = parseVec3List(obj, trainModel, "seatPos", "Pos")
            val rideableSeatMarkers = buildSeatMarkers(playerPositions, seatPositions)
            val seatOffset = playerPositions.firstOrNull() ?: seatPositions.firstOrNull()
            val trainDistance = parseFloat(trainModel, "trainDistance", parseFloat(obj, "trainDistance", 4.5F))
            val driverSeatIndex = parseInt(trainModel, "driverSeatIndex", parseInt(obj, "driverSeatIndex", 0))
            val frontIdx = resolveFrontDriverSeatIndex(obj, trainModel, rideableSeatMarkers, driverSeatIndex)
            val rearIdx = resolveRearDriverSeatIndex(obj, trainModel, rideableSeatMarkers, frontIdx)
            val leftDoors = parseDoorAnimations(obj, trainModel, "door_left")
            val rightDoors = parseDoorAnimations(obj, trainModel, "door_right")
            val notchMaxSpeeds = parseFloatList(obj, trainModel, "maxSpeed")
            val brakeDecelerations = parseFloatList(obj, trainModel, "deccelerations")
            val rollsignNames = parseStringList(obj, trainModel, "rollsignNames")
            val customButtonNames = parseCustomButtonNames(obj, trainModel)
            val customButtonOptions = parseCustomButtonOptions(obj, trainModel)
            val rollsignTexture = firstNonBlank(getString(trainModel, "rollsignTexture"), getString(obj, "rollsignTexture"))
            val rollsigns = parseRollsigns(obj, trainModel)
            val typeSignNames = parseStringList(obj, trainModel, "typeSignNames")
            val typeSignTexture = firstNonBlank(
                getString(trainModel, "typeSignTexture"),
                getString(obj, "typeSignTexture"),
            )
            val typeSigns = parseSignPanels(obj, trainModel, "typeSigns")
            val headLights = parseLights(obj, trainModel, "headLights")
            val tailLights = parseLights(obj, trainModel, "tailLights")
            val interiorLights = parseLights(obj, trainModel, "interiorLights")
            val hornSound = firstNonBlank(getString(trainModel, "sound_Horn"), getString(obj, "sound_Horn")) ?: ""
            val announcementSounds = parseAnnouncementSounds(obj, trainModel)
            val acceleration = parseFloat(trainModel, "acceleration", parseFloat(trainModel, "accelerateion",
                parseFloat(obj, "acceleration", parseFloat(obj, "accelerateion", 0.00243F))))
            val smoothing = parseBoolean(trainModel, "smoothing", parseBoolean(obj, "smoothing", false))
            val doCulling = parseBoolean(trainModel, "doCulling", parseBoolean(obj, "doCulling", false))
            val renderLight = headLights.isNotEmpty() || tailLights.isNotEmpty() || interiorLights.isNotEmpty()
            val notDisplayCab = parseBoolean(trainModel, "notDisplayCab", parseBoolean(obj, "notDisplayCab", false))
            val singleTrain = parseBoolean(trainModel, "singleTrain", parseBoolean(obj, "singleTrain", false))

            val def = VehicleDefinition(id ?: "", displayName ?: "", packName, modelFile, buttonTexture ?: "", tex, offset, scale,
                bogies, rideableSeatMarkers, seatPositions, playerPositions, seatOffset ?: Vec3.ZERO,
                scriptPath, soundScriptPath, vehicleType, doorType, trainDistance,
                driverSeatIndex, frontIdx, rearIdx, leftDoors, rightDoors,
                notchMaxSpeeds, brakeDecelerations, acceleration, smoothing,
                rollsignNames, customButtonNames, customButtonOptions,
                rollsignTexture ?: "", rollsigns, headLights, tailLights, interiorLights,
                hornSound, announcementSounds, doCulling, renderLight, notDisplayCab, singleTrain)

            val soundStop = firstNonBlank(getString(trainModel, "sound_Stop"), getString(obj, "sound_Stop"))
            val soundSA = firstNonBlank(getString(trainModel, "sound_S_A"), getString(obj, "sound_S_A"))
            val soundAccel = firstNonBlank(getString(trainModel, "sound_Acceleration"), getString(obj, "sound_Acceleration"))
            val soundDecel = firstNonBlank(getString(trainModel, "sound_Deceleration"), getString(obj, "sound_Deceleration"))
            val soundDS = firstNonBlank(getString(trainModel, "sound_D_S"), getString(obj, "sound_D_S"))
            def.setJsonRunningSounds(soundStop, soundSA, soundAccel, soundDecel, soundDS)
            def.setDoorSounds(
                firstNonBlank(getString(trainModel, "sound_DoorOpen"), getString(obj, "sound_DoorOpen")),
                firstNonBlank(getString(trainModel, "sound_DoorClose"), getString(obj, "sound_DoorClose")))
            def.setServerScriptPath(firstNonBlank(getString(trainModel, "serverScriptPath"), getString(obj, "serverScriptPath")))
            def.setAnnouncementNames(parseAnnouncementNames(obj, trainModel))
            def.setTypeSign(typeSignNames, typeSignTexture, typeSigns)

            LOADED.add(def)
        } catch (e: Exception) { RealTrainModRenewed.LOGGER.warn("Failed to parse vehicle json {}: {}", path, e.message) }
    }

    private fun parseBogies(root: JsonObject, trainModel: JsonObject): List<VehicleDefinition.BogieDefinition> {
        val list = mutableListOf<VehicleDefinition.BogieDefinition>()
        val bogieArray = trainModel.get("bogies")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.get("bogies")?.takeIf { it.isJsonArray }?.asJsonArray
        if (bogieArray == null) {
            return parseLegacyBogies(root, trainModel)
        }
        for (e in bogieArray) {
            if (!e.isJsonObject) continue
            val b = e.asJsonObject
            val modelFile = getString(b, "modelFile") ?: continue
            val tex = parseTextures(b)
            val pos = parseVec3(b, "position", 1.0)
            val script = getString(b, "scriptPath") ?: ""
            list.add(VehicleDefinition.BogieDefinition(modelFile, tex, pos, script))
        }
        return list
    }

    private fun parseLegacyBogies(root: JsonObject, trainModel: JsonObject): List<VehicleDefinition.BogieDefinition> {
        val models = firstJsonArrayOrObject(root, trainModel, "bogieModel3", "bogieModel2", "bogieModel", "bogieModels")
            ?: return emptyList()
        val positions = parseVec3List(root, trainModel, "bogiePos", "bogiePositions")
        val list = mutableListOf<VehicleDefinition.BogieDefinition>()
        val count = maxOf(models.size(), positions.size)
        for (i in 0 until count) {
            if (models.size() <= 0) continue
            val element = models[if (i < models.size()) i else models.size() - 1]
            if (!element.isJsonObject) continue
            val bogie = element.asJsonObject
            val modelFile = getString(bogie, "modelFile") ?: continue
            val textureOverrides = parseTextures(bogie)
            val position = positions.getOrNull(i) ?: positions.lastOrNull() ?: Vec3.ZERO
            val script = firstNonBlank(getString(bogie, "rendererPath"), getString(bogie, "scriptPath")) ?: ""
            list.add(VehicleDefinition.BogieDefinition(modelFile, textureOverrides, position, script))
        }
        return list
    }

    private fun firstJsonArrayOrObject(root: JsonObject, trainModel: JsonObject, vararg keys: String): JsonArray? {
        for (key in keys) {
            val element = trainModel.get(key) ?: root.get(key) ?: continue
            if (element.isJsonArray) return element.asJsonArray
            if (element.isJsonObject) return JsonArray().apply { add(element) }
        }
        return null
    }

    private fun buildSeatMarkers(playerPositions: List<Vec3>, seatPositions: List<Vec3>): List<VehicleDefinition.SeatMarker> {
        val markers = mutableListOf<VehicleDefinition.SeatMarker>()
        playerPositions.forEach { markers.add(VehicleDefinition.SeatMarker(it, -1, true)) }
        seatPositions.forEach { markers.add(VehicleDefinition.SeatMarker(it, 1, false)) }
        return markers
    }

    private fun parseVec3List(root: JsonObject, trainModel: JsonObject, vararg keys: String): List<Vec3> {
        for (key in keys) {
            val arr = (trainModel.get(key) ?: root.get(key))?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            val list = mutableListOf<Vec3>()
            for (e in arr) {
                if (e.isJsonArray) {
                    val a = e.asJsonArray
                    if (a.size() >= 3) list.add(Vec3(a[0].asDouble, a[1].asDouble, a[2].asDouble))
                }
            }
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    private fun firstJsonArray(root: JsonObject, trainModel: JsonObject, vararg keys: String): JsonArray? {
        for (key in keys) {
            val element = trainModel.get(key) ?: root.get(key)
            if (element != null && element.isJsonArray) {
                return element.asJsonArray
            }
        }
        return null
    }

    private fun parseDoorAnimations(root: JsonObject, trainModel: JsonObject, key: String): List<VehicleDefinition.DoorAnimationDefinition> {
        val arr = (trainModel.get(key) ?: root.get(key))?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val d = e.asJsonObject
            val objects = d.get("objects")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList()
            val closedPosition = parseOptionalVec3(d, "closedPosition")
                ?: parseOptionalVec3(d, "pos")
                ?: Vec3.ZERO
            val openTranslation = parseOptionalVec3(d, "openTranslation")
                ?: parseLegacyDoorTranslation(d)
                ?: Vec3.ZERO
            VehicleDefinition.DoorAnimationDefinition(objects, closedPosition, openTranslation)
        }
    }

    private fun parseLegacyDoorTranslation(door: JsonObject): Vec3? {
        val transforms = door.get("transform")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        for (transform in transforms) {
            if (!transform.isJsonArray) continue
            val values = transform.asJsonArray
            if (values.size() != 3) continue
            return runCatching { Vec3(values[0].asDouble, values[1].asDouble, values[2].asDouble) }.getOrNull()
        }
        return null
    }

    private fun parseOptionalVec3(obj: JsonObject, key: String): Vec3? {
        val values = obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        if (values.size() < 3) return null
        return runCatching { Vec3(values[0].asDouble, values[1].asDouble, values[2].asDouble) }.getOrNull()
    }

    private fun parseFloatList(root: JsonObject, trainModel: JsonObject, key: String): List<Float> {
        val arr = (trainModel.get(key) ?: root.get(key))?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { try { it.asFloat } catch (_: Exception) { null } }
    }

    private fun parseStringList(root: JsonObject, trainModel: JsonObject, key: String): List<String> {
        val arr = (trainModel.get(key) ?: root.get(key))?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { try { it.asString } catch (_: Exception) { null } }.filter { it.isNotBlank() }
    }

    private fun parseRollsigns(root: JsonObject, trainModel: JsonObject): List<VehicleDefinition.RollsignDefinition> {
        return parseSignPanels(root, trainModel, "rollsigns")
    }

    private fun parseSignPanels(
        root: JsonObject,
        trainModel: JsonObject,
        key: String,
    ): List<VehicleDefinition.RollsignDefinition> {
        val arr = (trainModel.get(key) ?: root.get(key))?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val r = e.asJsonObject
            val uv = r.get("uv")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { try { it.asFloat } catch (_: Exception) { null } }?.toFloatArray() ?: floatArrayOf(0f, 0f, 1f, 1f)
            val pos = parseRollsignPositions(r)
            VehicleDefinition.RollsignDefinition(uv, pos, parseBoolean(r, "doAnimation", false), parseBoolean(r, "disableLighting", false))
        }
    }

    private fun parseRollsignPositions(obj: JsonObject): Array<Array<FloatArray>> {
        val arr = obj.get("pos")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyArray()
        val quads = mutableListOf<Array<FloatArray>>()
        for (quadElement in arr) {
            if (!quadElement.isJsonArray) continue
            val quadArray = quadElement.asJsonArray
            if (quadArray.size() < 4) continue
            val points = mutableListOf<FloatArray>()
            for (pointElement in quadArray) {
                if (!pointElement.isJsonArray) continue
                val values = pointElement.asJsonArray
                    .mapNotNull { try { it.asFloat } catch (_: Exception) { null } }
                if (values.size >= 3) {
                    points += floatArrayOf(values[0], values[1], values[2])
                }
            }
            if (points.size >= 4) {
                quads += points.take(4).toTypedArray()
            }
        }
        return quads.toTypedArray()
    }

    private fun parseLights(root: JsonObject, trainModel: JsonObject, key: String): List<VehicleDefinition.LightDefinition> {
        val arr = (trainModel.get(key) ?: root.get(key))?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val l = e.asJsonObject
            VehicleDefinition.LightDefinition(parseInt(l, "type", 0).toByte(), parseInt(l, "color", 0xFFFFFF),
                parseVec3(l, "position", 1.0), parseFloat(l, "radius", 1.0F), parseBoolean(l, "reverse", false))
        }
    }

    private fun parseCustomButtonNames(root: JsonObject, trainModel: JsonObject): List<String> {
        var values = parseNamedButtonList(trainModel, "customButtons")
        if (values.isNotEmpty()) return values
        values = parseNamedButtonList(root, "customButtons")
        if (values.isNotEmpty()) return values
        values = parseNamedButtonList(trainModel, "buttons")
        if (values.isNotEmpty()) return values
        return parseNamedButtonList(root, "buttons")
    }

    private fun parseCustomButtonOptions(root: JsonObject, trainModel: JsonObject): List<List<String>> {
        var values = parseButtonOptionGrid(trainModel, "customButtons")
        if (values.isNotEmpty()) return values
        return parseButtonOptionGrid(root, "customButtons")
    }

    private fun parseButtonOptionGrid(source: JsonObject, key: String): List<List<String>> {
        val arr = source.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            if (!e.isJsonArray) return@mapNotNull null
            val values = e.asJsonArray.mapNotNull { try { it.asString } catch (_: Exception) { null } }.filter { it.isNotBlank() }
            if (values.isEmpty()) null else values
        }
    }

    private fun parseNamedButtonList(source: JsonObject, key: String): List<String> {
        val arr = source.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        val values = mutableListOf<String>()
        for (e in arr) collectNamedButtonValue(e, values)
        return values
    }

    private fun collectNamedButtonValue(element: JsonElement, values: MutableList<String>) {
        when {
            element.isJsonPrimitive -> { val v = element.asString; if (v.isNotBlank()) values.add(v) }
            element.isJsonArray -> element.asJsonArray.forEach { collectNamedButtonValue(it, values) }
            element.isJsonObject -> {
                val b = element.asJsonObject
                val v = firstNonBlank(getString(b, "name"), getString(b, "label"),
                    getString(b, "text"), getString(b, "value")) ?: return
                if (v.isNotBlank()) values.add(v)
            }
        }
    }

    private fun resolveFrontDriverSeatIndex(root: JsonObject, trainModel: JsonObject, markers: List<VehicleDefinition.SeatMarker>, default: Int): Int {
        val idx = parseInt(trainModel, "frontDriverSeatIndex", parseInt(root, "frontDriverSeatIndex", -1))
        if (idx >= 0 && idx < markers.size) return idx
        return markers.indexOfFirst { it.driverCab }.takeIf { it >= 0 } ?: 0
    }

    private fun resolveRearDriverSeatIndex(root: JsonObject, trainModel: JsonObject, markers: List<VehicleDefinition.SeatMarker>, frontIdx: Int): Int {
        val idx = parseInt(trainModel, "rearDriverSeatIndex", parseInt(root, "rearDriverSeatIndex", -1))
        if (idx >= 0 && idx < markers.size) return idx
        return markers.indices.lastOrNull { markers[it].driverCab && it != frontIdx } ?: frontIdx
    }

    private fun parseAnnouncementSounds(obj: JsonObject, trainModel: JsonObject): List<String> {
        val sounds = mutableListOf<String>()
        appendAnnouncementSounds(trainModel, sounds)
        if (trainModel !== obj) appendAnnouncementSounds(obj, sounds)
        return sounds
    }

    private fun appendAnnouncementSounds(json: JsonObject, target: MutableList<String>) {
        val arr = json.get("sound_Announcement")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        for (entry in arr) {
            val sound = extractAnnouncementSound(entry)
            if (!sound.isNullOrBlank()) target.add(sound)
        }
    }

    private fun parseAnnouncementNames(obj: JsonObject, trainModel: JsonObject): List<String> = buildList {
        appendAnnouncementNames(trainModel, this)
        if (trainModel !== obj) appendAnnouncementNames(obj, this)
    }

    private fun appendAnnouncementNames(json: JsonObject, target: MutableList<String>) {
        val entries = json.get("sound_Announcement")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        for (entry in entries) {
            if (extractAnnouncementSound(entry).isNullOrBlank()) continue
            target += extractAnnouncementName(entry)
        }
    }

    private fun extractAnnouncementName(entry: JsonElement): String = when {
        entry.isJsonArray -> entry.asJsonArray.let { array ->
            if (array.size() >= 2 && array[0].isJsonPrimitive && array[0].asJsonPrimitive.isString) {
                array[0].asString
            } else {
                ""
            }
        }
        entry.isJsonObject -> entry.asJsonObject.let { obj ->
            firstNonBlank(getString(obj, "name"), getString(obj, "displayName")) ?: ""
        }
        else -> ""
    }

    private fun extractAnnouncementSound(entry: JsonElement): String? = when {
        entry.isJsonPrimitive && entry.asJsonPrimitive.isString -> entry.asString
        entry.isJsonArray -> {
            val array = entry.asJsonArray
            if (array.size() >= 2 && array[1].isJsonPrimitive && array[1].asJsonPrimitive.isString)
                array[1].asString
            else if (!array.isEmpty) extractAnnouncementSound(array[0])
            else null
        }
        entry.isJsonObject -> {
            val obj = entry.asJsonObject
            firstNonBlank(getString(obj, "sound"), getString(obj, "soundName"),
                getString(obj, "id"), getString(obj, "path"))
        }
        else -> null
    }

    private fun parseTextures(modelObj: JsonObject): Map<String, String> {
        val arr = modelObj.get("textures")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyMap()
        val overrides = HashMap<String, String>()
        for (entry in arr) {
            if (!entry.isJsonArray) continue
            val pair = entry.asJsonArray
            if (pair.size() < 2) continue
            val mat = pair[0].asString
            val tex = pair[1].asString
            if (mat.isNotBlank() && tex.isNotBlank()) overrides[mat] = encodeTextureDescriptor(pair)
        }
        return overrides
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

    private fun parseVec3(obj: JsonObject, key: String, scale: Double): Vec3 {
        val arr = obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return Vec3.ZERO
        if (arr.size() < 3) return Vec3.ZERO
        return try { Vec3(arr[0].asDouble * scale, arr[1].asDouble * scale, arr[2].asDouble * scale) }
        catch (_: Exception) { Vec3.ZERO }
    }

    private fun parseFloat(obj: JsonObject, key: String, def: Float): Float =
        try { obj.get(key)?.asFloat ?: def } catch (_: Exception) { def }
    private fun parseInt(obj: JsonObject, key: String, def: Int): Int =
        try { obj.get(key)?.asInt ?: def } catch (_: Exception) { def }
    private fun parseBoolean(obj: JsonObject, key: String, def: Boolean): Boolean =
        try { obj.get(key)?.asBoolean ?: def } catch (_: Exception) { def }

    private fun getObject(o: JsonObject, k: String): JsonObject? = o.get(k)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun getString(o: JsonObject, k: String): String? = o.get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    @JvmStatic
    fun openPackStream(definition: VehicleDefinition?): InputStream? {
        val p = definition?.let { RailPackLoader.resolvePackPath(it.packName) } ?: return null
        return Files.newInputStream(p)
    }

    private fun normalize(value: String): String = value.replace('\\', '/')

    private fun configRoot(): Path = FMLPaths.GAMEDIR.get().resolve("config").resolve(RealTrainModRenewed.MODID)

    private fun configRoots(): List<Path> {
        val renewed = configRoot()
        val legacy = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial")
        return if (renewed == legacy) listOf(renewed) else listOf(renewed, legacy)
    }
}
