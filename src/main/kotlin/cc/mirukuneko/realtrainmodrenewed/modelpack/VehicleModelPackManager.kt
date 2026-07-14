// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.modelpack

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.phys.Vec3
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * legacy-inspired ModelPackManager with advanced JSON processing and caching.
 * Handles vehicle packs, textures, models, and configuration files.
 */
class VehicleModelPackManager private constructor() : ResourceManagerReloadListener {
    private val resourceConfigs: MutableMap<String, ResourceConfig> = ConcurrentHashMap()
    private val scriptCache: MutableMap<String, String> = ConcurrentHashMap()
    private val identifierCache: MutableMap<String, Identifier> = ConcurrentHashMap()
    private val jsonCache: MutableMap<String, JsonElement> = ConcurrentHashMap()
    private var resourceManager: ResourceManager? = null

    private val loadedPacks: MutableMap<String, ModelPack> = ConcurrentHashMap()
    private val activePacks: MutableSet<String> = HashSet()

    private var initialized = false

    fun initialize(resourceManager: ResourceManager) {
        if (initialized) return

        RealTrainModRenewed.LOGGER.info("Initializing legacy Model Pack Manager...")

        try {
            this.resourceManager = resourceManager
            scanVehicleConfigs(resourceManager)
            loadModelPacks()
            validateAndRegisterResources()

            initialized = true
            RealTrainModRenewed.LOGGER.info("legacy Model Pack Manager initialized successfully")
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.error("Failed to initialize legacy Model Pack Manager", e)
        }
    }

    private fun scanVehicleConfigs(resourceManager: ResourceManager) {
        try {
            val resources = resourceManager.listResources("vehicle_packs") { path -> path.path.endsWith("_config.json") }
            for ((location, resource) in resources) {
                try {
                    loadVehicleConfig(location, resource)
                } catch (e: Exception) {
                    RealTrainModRenewed.LOGGER.warn("Failed to load vehicle config: {}", location, e)
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to scan vehicle configs", e)
        }
    }

    @Throws(IOException::class)
    private fun loadVehicleConfig(location: Identifier, resource: Resource) {
        resource.open().use { stream ->
            val jsonContent = PackTextDecoder.decodeJson(stream.readAllBytes())
            val root = JsonParser.parseString(jsonContent)

            if (!root.isJsonObject) {
                throw IOException("Invalid JSON format in vehicle config")
            }

            val packId = extractPackId(location)
            resourceConfigs[packId] = parseResourceConfig(root.asJsonObject, packId)

            RealTrainModRenewed.LOGGER.debug("Loaded vehicle config: {}", packId)
        }
    }

    private fun extractPackId(location: Identifier): String {
        val path = location.path
        val matcher = VEHICLE_JSON_PATTERN.matcher(path)
        if (matcher.find()) {
            return matcher.group(1)
        }

        val lastSlash = path.lastIndexOf('/')
        val lastDot = path.lastIndexOf('.')
        return path.substring(lastSlash + 1, lastDot)
    }

    private fun parseResourceConfig(configObj: JsonObject, packId: String): ResourceConfig {
        val config = ResourceConfig()
        config.packId = packId
        config.name = getString(configObj, "name", packId)
        config.version = getString(configObj, "version", "1.0.0")
        config.author = getString(configObj, "author", "Unknown")
        config.description = getString(configObj, "description", "")

        if (configObj.has("vehicles") && configObj.get("vehicles").isJsonArray) {
            config.vehicles = parseVehicleConfigs(configObj.getAsJsonArray("vehicles"))
        }
        if (configObj.has("models") && configObj.get("models").isJsonArray) {
            config.models = parseModelConfigs(configObj.getAsJsonArray("models"))
        }
        if (configObj.has("textures") && configObj.get("textures").isJsonArray) {
            config.textures = parseTextureConfigs(configObj.getAsJsonArray("textures"))
        }
        if (configObj.has("dependencies") && configObj.get("dependencies").isJsonArray) {
            config.dependencies = parseDependencies(configObj.getAsJsonArray("dependencies"))
        }
        if (configObj.has("scripts") && configObj.get("scripts").isJsonArray) {
            config.scripts = parseScriptConfigs(configObj.getAsJsonArray("scripts"))
        }

        return config
    }

    private fun parseVehicleConfigs(vehiclesArray: JsonArray): MutableList<VehicleConfig> {
        val vehicles = ArrayList<VehicleConfig>()

        for (element in vehiclesArray) {
            if (!element.isJsonObject) continue

            val vehicleObj = element.asJsonObject
            val vehicle = VehicleConfig()
            vehicle.id = getString(vehicleObj, "id", "")
            vehicle.name = getString(vehicleObj, "name", vehicle.id)
            vehicle.modelFile = getString(vehicleObj, "modelFile", "")
            vehicle.modelScale = parseFloat(vehicleObj, "scale", 1.0f)
            vehicle.modelOffset = parseVec3(vehicleObj, "offset", 1.0 / 16.0)
            vehicle.seatPositions = parselegacySeatPositions(vehicleObj)
            vehicle.playerPositions = parseLegacyPlayerPositions(vehicleObj)
            vehicle.bogiePositions = parselegacyBogiePositions(vehicleObj)
            vehicle.bogieModels = parselegacyBogieModels(vehicleObj)
            vehicle.textures = parselegacyTextures(vehicleObj)
            vehicle.trainDistance = parseFloat(vehicleObj, "trainDistance", 4.5f)
            vehicle.maxSpeed = parseFloat(vehicleObj, "maxSpeed", 20.0f)
            vehicle.weight = parseFloat(vehicleObj, "weight", 1000.0f)
            vehicle.power = parseFloat(vehicleObj, "power", 500.0f)
            vehicle.scriptPath = getString(
                vehicleObj,
                "rendererPath",
                getString(
                    vehicleObj,
                    "serverScriptPath",
                    getString(vehicleObj, "soundScriptPath", getString(vehicleObj, "scriptPath", "")),
                ),
            )
            vehicle.doorType = getString(vehicleObj, "doorType", "manual")
            vehicle.lightType = getString(vehicleObj, "lightType", "standard")
            vehicle.soundType = getString(vehicleObj, "soundType", "default")

            vehicles.add(vehicle)
        }

        return vehicles
    }

    private fun parselegacySeatPositions(obj: JsonObject): MutableList<Vec3> {
        val seats = ArrayList<Vec3>()
        appendVec3Array(obj, "seatPos", seats, 1.0 / 16.0)
        appendVec3Array(obj, "seatPosF", seats, 1.0)
        appendVec3Array(obj, "seatPositions", seats, 1.0)
        return seats
    }

    private fun parseLegacyPlayerPositions(obj: JsonObject): MutableList<Vec3> {
        val players = ArrayList<Vec3>()
        appendVec3Array(obj, "playerPos", players, 1.0)
        appendVec3Array(obj, "playerPosF", players, 1.0)
        return players
    }

    private fun parselegacyBogiePositions(obj: JsonObject): MutableList<Vec3> {
        val bogies = ArrayList<Vec3>()
        appendVec3Array(obj, "bogiePos", bogies, 1.0)
        appendVec3Array(obj, "bogiePositions", bogies, 1.0)
        appendVec3Array(obj, "truckPos", bogies, 1.0)
        appendVec3Array(obj, "truckPositions", bogies, 1.0)
        return bogies
    }

    private fun parselegacyBogieModels(obj: JsonObject): MutableList<BogieModelConfig> {
        val bogieModels = ArrayList<BogieModelConfig>()

        if (obj.has("bogieModel3") && obj.get("bogieModel3").isJsonArray) {
            val array = obj.getAsJsonArray("bogieModel3")
            for (element in array) {
                if (!element.isJsonObject) continue

                val bogieObj = element.asJsonObject
                val bogieConfig = BogieModelConfig()
                bogieConfig.modelFile = getString(bogieObj, "modelFile", "")
                bogieConfig.textures = parselegacyTextures(bogieObj)
                bogieConfig.scale = parseFloat(bogieObj, "scale", 1.0f)
                bogieConfig.offset = parseVec3(bogieObj, "offset", 1.0 / 16.0)
                bogieModels.add(bogieConfig)
            }
        } else {
            var bogieModel = getObject(obj, "bogieModel2")
            if (bogieModel == null) bogieModel = getObject(obj, "bogieModel")

            if (bogieModel != null) {
                val bogieConfig = BogieModelConfig()
                bogieConfig.modelFile = getString(bogieModel, "modelFile", "")
                bogieConfig.textures = parselegacyTextures(bogieModel)
                bogieConfig.scale = parseFloat(bogieModel, "scale", 1.0f)
                bogieConfig.offset = parseVec3(bogieModel, "offset", 1.0 / 16.0)
                bogieModels.add(bogieConfig)
            }
        }

        return bogieModels
    }

    private fun parselegacyTextures(modelObj: JsonObject?): MutableMap<String, String> {
        val textures = HashMap<String, String>()
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray) {
            return textures
        }

        val array = modelObj.getAsJsonArray("textures")
        for (element in array) {
            if (!element.isJsonArray || element.asJsonArray.size() < 2) continue

            val pair = element.asJsonArray
            val material = pair[0].asString
            val texture = pair[1].asString
            if (material.isNotBlank() && texture.isNotBlank()) {
                textures[material] = encodeTextureDescriptor(pair)
            }
        }

        return textures
    }

    private fun encodeTextureDescriptor(pair: JsonArray): String {
        val texture = pair[1].asString
        if (pair.size() < 3) {
            return texture
        }
        val flags = ArrayList<String>()
        for (i in 2 until pair.size()) {
            val option = pair[i]
            if (option == null || !option.isJsonPrimitive) {
                continue
            }
            val value = option.asString
            if (value.isNotBlank()) {
                flags.add(value.trim())
            }
        }
        if (flags.isEmpty()) {
            return texture
        }
        return "$texture|ptmeta=${flags.joinToString(",")}"
    }

    private fun appendVec3Array(obj: JsonObject, key: String, out: MutableList<Vec3>, scale: Double) {
        if (!obj.has(key) || !obj.get(key).isJsonArray) return

        val array = obj.getAsJsonArray(key)
        for (element in array) {
            if (!element.isJsonArray || element.asJsonArray.size() < 3) continue

            val vecArray = element.asJsonArray
            try {
                val x = vecArray[0].asDouble
                val y = vecArray[1].asDouble
                val z = vecArray[2].asDouble
                out.add(Vec3(x * scale, y * scale, z * scale))
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, e.message)
            }
        }
    }

    private fun loadModelPacks() {
        for (packId in resourceConfigs.keys) {
            val config = resourceConfigs[packId]
            try {
                val pack = ModelPack(packId, config)
                loadedPacks[packId] = pack
                activePacks.add(packId)
                RealTrainModRenewed.LOGGER.debug("Loaded model pack: {}", packId)
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.warn("Failed to load model pack: {}", packId, e)
            }
        }
    }

    private fun validateAndRegisterResources() {
        for (pack in loadedPacks.values) {
            try {
                pack.validate()
                pack.registerVehicles()
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.warn("Failed to validate/register pack: {}", pack.packId, e)
                activePacks.remove(pack.packId)
            }
        }
    }

    fun getResource(path: String): Identifier? {
        val cached = identifierCache[path]
        if (cached != null) {
            return cached
        }
        val identifier = if (path.contains(":")) {
            val parts = path.split(":").toTypedArray()
            Identifier.tryBuild(parts[0], parts[1])
        } else {
            Identifier.tryBuild("minecraft", path)
        }
        if (identifier != null) {
            identifierCache[path] = identifier
        }
        return identifier
    }

    @Throws(IOException::class)
    fun getScript(fileName: String): String = scriptCache.computeIfAbsent(fileName, ::loadScriptWithIncludes)

    private fun loadScriptWithIncludes(fileName: String): String {
        try {
            val rawScript = loadScriptFile(fileName)
            return processScriptIncludes(rawScript)
        } catch (e: IOException) {
            throw RuntimeException("Failed to load script: $fileName", e)
        }
    }

    private fun processScriptIncludes(rawScript: String): String {
        var script = rawScript
        var matcher = SCRIPT_INCLUDE_PATTERN.matcher(script)

        while (matcher.find()) {
            val includePath = matcher.group(1)
            script = try {
                val includedScript = getScript(includePath)
                matcher.replaceFirst(Matcher.quoteReplacement(includedScript))
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.warn("Failed to include script: {}", includePath, e)
                matcher.replaceFirst("")
            }
            matcher = SCRIPT_INCLUDE_PATTERN.matcher(script)
        }

        return script
    }

    @Throws(IOException::class)
    private fun loadScriptFile(fileName: String): String {
        val manager = resourceManager ?: throw IOException("legacy model pack resource manager is not initialized")
        val normalized = fileName.replace('\\', '/')
        val fileNameOnly = if (normalized.contains("/")) normalized.substring(normalized.lastIndexOf('/') + 1) else normalized

        try {
            val scriptResources = manager.listResources("vehicle_packs") { path ->
                val candidate = path.path
                candidate == normalized || candidate.endsWith("/$fileNameOnly") || candidate.endsWith("/$normalized")
            }
            for (resource in scriptResources.values) {
                resource.open().use { stream ->
                    return PackTextDecoder.readText(stream)
                }
            }
        } catch (ignored: IOException) {
        }

        throw IOException("Script not found: $fileName")
    }

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        resourceConfigs.clear()
        scriptCache.clear()
        identifierCache.clear()
        jsonCache.clear()
        loadedPacks.clear()
        activePacks.clear()

        initialized = false
        initialize(resourceManager)
    }

    open class ResourceConfig {
        @JvmField var packId: String? = null
        @JvmField var name: String? = null
        @JvmField var version: String? = null
        @JvmField var author: String? = null
        @JvmField var description: String? = null
        @JvmField var vehicles: MutableList<VehicleConfig> = ArrayList()
        @JvmField var models: MutableList<ModelConfig> = ArrayList()
        @JvmField var textures: MutableList<TextureConfig> = ArrayList()
        @JvmField var dependencies: MutableList<String> = ArrayList()
        @JvmField var scripts: MutableList<String> = ArrayList()
    }

    open class ModelConfig {
        @JvmField var id: String? = null
        @JvmField var modelFile: String? = null
        @JvmField var textureFile: String? = null
        @JvmField var scale = 1.0f
        @JvmField var offset: Vec3 = Vec3.ZERO
    }

    open class TextureConfig {
        @JvmField var id: String? = null
        @JvmField var textureFile: String? = null
        @JvmField var overrides: MutableMap<String, String> = HashMap()
    }

    open class ModelPack(val packId: String, private val config: ResourceConfig?) {
        @Throws(IOException::class)
        fun validate() {
            val resourceConfig = config ?: throw IOException("Model pack has no config: $packId")
            if (resourceConfig.vehicles.isEmpty()) {
                throw IOException("Model pack has no vehicles: $packId")
            }

            for (vehicle in resourceConfig.vehicles) {
                vehicle.validate()
            }
        }

        fun registerVehicles() {
            val resourceConfig = config ?: return
            for (vehicle in resourceConfig.vehicles) {
                try {
                    val definition = convertToVehicleDefinition(vehicle)
                    synchronized(VehicleRegistry::class.java) {
                        val currentDefs = ArrayList(VehicleRegistry.getAll())
                        currentDefs.add(definition)
                        VehicleRegistry.setDefinitions(currentDefs)
                    }
                } catch (e: Exception) {
                    RealTrainModRenewed.LOGGER.warn("Failed to register vehicle: {}", vehicle.id, e)
                }
            }
        }

        private fun convertToVehicleDefinition(vehicle: VehicleConfig): VehicleDefinition {
            val resourceConfig = config ?: ResourceConfig()
            val bogieDefs = ArrayList<VehicleDefinition.BogieDefinition>()

            for (i in vehicle.bogieModels.indices) {
                val bogieModel = vehicle.bogieModels[i]
                val position = if (i < vehicle.bogiePositions.size) vehicle.bogiePositions[i] else Vec3.ZERO
                bogieDefs.add(VehicleDefinition.BogieDefinition(bogieModel.modelFile ?: "", bogieModel.textures, position))
            }

            var scriptPath = vehicle.scriptPath
            if (scriptPath.isBlank() && resourceConfig.scripts.isNotEmpty()) {
                scriptPath = resourceConfig.scripts[0]
            }

            val frontDriverSeatIndex = findExtremeSeatIndexByZ(vehicle.seatPositions, true)
            val rearDriverSeatIndex = findExtremeSeatIndexByZ(vehicle.seatPositions, false)

            return VehicleDefinition(
                vehicle.id ?: "",
                vehicle.name ?: "",
                "Legacy_Pack",
                vehicle.modelFile ?: "",
                "",
                vehicle.textures,
                vehicle.modelOffset,
                vehicle.modelScale,
                bogieDefs,
                vehicle.seatPositions,
                vehicle.playerPositions,
                if (vehicle.playerPositions.isNotEmpty()) vehicle.playerPositions[0] else if (vehicle.seatPositions.isEmpty()) null else vehicle.seatPositions[0],
                scriptPath,
                "",
                "Train",
                vehicle.doorType,
                vehicle.trainDistance,
                0,
                frontDriverSeatIndex,
                rearDriverSeatIndex,
                emptyList(),
                emptyList(),
                if (vehicle.maxSpeed > 0.0f) listOf(vehicle.maxSpeed) else emptyList(),
                emptyList(),
                0.00243f,
                false,
                emptyList(),
                emptyList(),
                emptyList(),
                "",
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                "",
                emptyList(),
                false,
                false,
                false,
                false,
            )
        }

        private fun findExtremeSeatIndexByZ(seats: List<Vec3>?, front: Boolean): Int {
            if (seats == null || seats.isEmpty()) {
                return 0
            }

            var bestIndex = 0
            var bestZ = seats[0].z
            for (i in 1 until seats.size) {
                val z = seats[i].z
                if (if (front) z > bestZ else z < bestZ) {
                    bestZ = z
                    bestIndex = i
                }
            }
            return bestIndex
        }

        fun getConfig(): ResourceConfig? = config
    }

    open class VehicleConfig {
        @JvmField var id: String? = null
        @JvmField var name: String? = null
        @JvmField var modelFile: String? = null
        @JvmField var modelScale = 1.0f
        @JvmField var modelOffset: Vec3 = Vec3.ZERO
        @JvmField var textures: MutableMap<String, String> = HashMap()
        @JvmField var seatPositions: MutableList<Vec3> = ArrayList()
        @JvmField var playerPositions: MutableList<Vec3> = ArrayList()
        @JvmField var bogiePositions: MutableList<Vec3> = ArrayList()
        @JvmField var bogieModels: MutableList<BogieModelConfig> = ArrayList()
        @JvmField var scriptPath = ""
        @JvmField var trainDistance = 4.5f
        @JvmField var maxSpeed = 20.0f
        @JvmField var weight = 1000.0f
        @JvmField var power = 500.0f
        @JvmField var doorType = "manual"
        @JvmField var lightType = "standard"
        @JvmField var soundType = "default"

        @Throws(IOException::class)
        fun validate() {
            if (modelFile == null || modelFile!!.isBlank()) {
                throw IOException("Model file is required for vehicle: $id")
            }

            if (seatPositions.isEmpty() && playerPositions.isEmpty() && bogiePositions.isEmpty()) {
                RealTrainModRenewed.LOGGER.warn("Vehicle '{}' has no seats, driver positions, or bogies defined", id)
            }
        }
    }

    open class BogieModelConfig {
        @JvmField var modelFile: String? = null
        @JvmField var textures: MutableMap<String, String> = HashMap()
        @JvmField var scale = 1.0f
        @JvmField var offset: Vec3 = Vec3.ZERO
    }

    companion object {
        @JvmField
        val INSTANCE = VehicleModelPackManager()

        private val VEHICLE_JSON_PATTERN: Pattern = Pattern.compile("^vehicle_(.+)_config\\.json$")
        private val SCRIPT_INCLUDE_PATTERN: Pattern = Pattern.compile("//include <(.+)>")

        private fun getObject(obj: JsonObject, key: String): JsonObject? =
            if (obj.has(key) && obj.get(key).isJsonObject) obj.getAsJsonObject(key) else null

        private fun getString(obj: JsonObject, key: String, defaultValue: String?): String =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asString else defaultValue ?: ""

        private fun parseFloat(obj: JsonObject?, key: String, defaultValue: Float): Float {
            if (obj == null || !obj.has(key)) return defaultValue
            return try {
                obj.get(key).asFloat
            } catch (e: Exception) {
                defaultValue
            }
        }

        private fun parseVec3(obj: JsonObject?, key: String, scale: Double): Vec3 {
            if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray) return Vec3.ZERO

            val array = obj.getAsJsonArray(key)
            if (array.size() < 3) return Vec3.ZERO

            return try {
                Vec3(
                    array[0].asDouble * scale,
                    array[1].asDouble * scale,
                    array[2].asDouble * scale,
                )
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, e.message)
                Vec3.ZERO
            }
        }

        private fun parseDependencies(array: JsonArray): MutableList<String> {
            val dependencies = ArrayList<String>()
            for (element in array) {
                if (element.isJsonPrimitive) {
                    dependencies.add(element.asString)
                }
            }
            return dependencies
        }

        private fun parseScriptConfigs(array: JsonArray): MutableList<String> {
            val scripts = ArrayList<String>()
            for (element in array) {
                if (element.isJsonPrimitive) {
                    scripts.add(element.asString)
                }
            }
            return scripts
        }

        private fun parseModelConfigs(array: JsonArray): MutableList<ModelConfig> = ArrayList()

        private fun parseTextureConfigs(array: JsonArray): MutableList<TextureConfig> = ArrayList()
    }
}
