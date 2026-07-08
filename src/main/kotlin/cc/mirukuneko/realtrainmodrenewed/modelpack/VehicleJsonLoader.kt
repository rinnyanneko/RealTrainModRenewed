package cc.mirukuneko.realtrainmodrenewed.modelpack

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

open class VehicleJsonLoader {
    companion object {
        private val ENCODINGS = arrayOf("UTF-8", "SJIS")

        @JvmStatic
        @Throws(IOException::class)
        fun parseVehicleConfig(jsonPath: Path): VehicleConfig {
            val jsonContent = readFileWithEncoding(jsonPath)
            val root = JsonParser.parseString(jsonContent)

            if (!root.isJsonObject) {
                throw IOException("Invalid JSON format: root must be object")
            }

            val obj = root.asJsonObject
            return parseVehicleConfig(obj, jsonPath.fileName.toString())
        }

        private fun parseVehicleConfig(obj: JsonObject, fileName: String): VehicleConfig {
            val config = VehicleConfig()

            config.id = getString(obj, "trainName", "train")
            config.displayName = getString(obj, "displayName", config.id ?: "train")

            var modelObj = getObject(obj, "trainModel2")
            if (modelObj == null) {
                modelObj = getObject(obj, "trainModel")
            }

            if (modelObj != null) {
                config.modelFile = getString(modelObj, "modelFile")
                config.modelScale = parseFloat(modelObj, "scale", 1.0f)
                config.modelOffset = parseVec3(modelObj, "offset", 1.0 / 16.0)
                config.scriptPath = getString(modelObj, "rendererPath")

                config.textures = parseTextures(modelObj)

                config.seatPositions = parseSeatPositions(modelObj)
                config.seatPositions.addAll(parseSeatPositions(obj))

                config.bogiePositions = parseBogiePositions(modelObj)
                config.bogiePositions.addAll(parseBogiePositions(obj))

                config.bogieModels = parseBogieModels(obj)
            }

            config.trainDistance = parseFloat(modelObj, "trainDistance", parseFloat(obj, "trainDistance", 4.5f))
            config.maxSpeed = parseFloat(obj, "maxSpeed", 20.0f)
            config.weight = parseFloat(obj, "weight", 1000.0f)

            return config
        }

        @Throws(IOException::class)
        private fun readFileWithEncoding(path: Path): String {
            for (encoding in ENCODINGS) {
                try {
                    return Files.readString(path, Charset.forName(encoding))
                } catch (ignored: IOException) {
                }
            }
            throw IOException("Failed to read file with any supported encoding: $path")
        }

        private fun parseSeatPositions(obj: JsonObject): MutableList<Vec3> {
            val seats = mutableListOf<Vec3>()
            val seatFields = arrayOf("seatPos", "seatPosF", "playerPos", "playerPosF")
            for (field in seatFields) {
                appendVec3Array(obj, field, seats)
            }
            return seats
        }

        private fun parseBogiePositions(obj: JsonObject): MutableList<Vec3> {
            val bogies = mutableListOf<Vec3>()
            appendVec3Array(obj, "bogiePos", bogies)
            return bogies
        }

        private fun parseBogieModels(obj: JsonObject): MutableList<BogieModelConfig> {
            val bogieModels = mutableListOf<BogieModelConfig>()

            if (obj.has("bogieModel3") && obj["bogieModel3"].isJsonArray) {
                val array = obj.getAsJsonArray("bogieModel3")
                for (i in 0 until array.size()) {
                    if (!array[i].isJsonObject) {
                        continue
                    }

                    val bogieObj = array[i].asJsonObject
                    val bogieConfig = BogieModelConfig()
                    bogieConfig.modelFile = getString(bogieObj, "modelFile")
                    bogieConfig.textures = parseTextures(bogieObj)
                    bogieConfig.scale = parseFloat(bogieObj, "scale", 1.0f)
                    bogieConfig.offset = parseVec3(bogieObj, "offset", 1.0 / 16.0)

                    bogieModels.add(bogieConfig)
                }
            } else {
                var bogieModel = getObject(obj, "bogieModel2")
                if (bogieModel == null) {
                    bogieModel = getObject(obj, "bogieModel")
                }

                if (bogieModel != null) {
                    val bogieConfig = BogieModelConfig()
                    bogieConfig.modelFile = getString(bogieModel, "modelFile")
                    bogieConfig.textures = parseTextures(bogieModel)
                    bogieConfig.scale = parseFloat(bogieModel, "scale", 1.0f)
                    bogieConfig.offset = parseVec3(bogieModel, "offset", 1.0 / 16.0)

                    bogieModels.add(bogieConfig)
                }
            }

            return bogieModels
        }

        private fun parseTextures(modelObj: JsonObject?): MutableMap<String, String> {
            val textures = mutableMapOf<String, String>()

            if (modelObj == null || !modelObj.has("textures") || !modelObj["textures"].isJsonArray) {
                return textures
            }

            val array = modelObj.getAsJsonArray("textures")
            for (element in array) {
                if (!element.isJsonArray || element.asJsonArray.size() < 2) {
                    continue
                }

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
            val flags = mutableListOf<String>()
            for (i in 2 until pair.size()) {
                val option: JsonElement? = pair[i]
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
            return texture + "|ptmeta=" + flags.joinToString(",")
        }

        private fun appendVec3Array(obj: JsonObject, key: String, out: MutableList<Vec3>) {
            if (!obj.has(key) || !obj[key].isJsonArray) {
                return
            }

            val array = obj.getAsJsonArray(key)
            for (element in array) {
                if (!element.isJsonArray || element.asJsonArray.size() < 3) {
                    continue
                }

                val vecArray = element.asJsonArray
                try {
                    val x = vecArray[0].asDouble
                    val y = vecArray[1].asDouble
                    val z = vecArray[2].asDouble
                    out.add(Vec3(x / 16.0, y / 16.0, z / 16.0))
                } catch (exception: Exception) {
                    RealTrainModRenewed.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, exception.message)
                }
            }
        }

        private fun parseVec3(obj: JsonObject?, key: String, scale: Double): Vec3 {
            if (obj == null || !obj.has(key) || !obj[key].isJsonArray) {
                return Vec3.ZERO
            }

            val array = obj.getAsJsonArray(key)
            if (array.size() < 3) {
                return Vec3.ZERO
            }

            try {
                return Vec3(
                    array[0].asDouble * scale,
                    array[1].asDouble * scale,
                    array[2].asDouble * scale,
                )
            } catch (exception: Exception) {
                RealTrainModRenewed.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, exception.message)
                return Vec3.ZERO
            }
        }

        private fun getObject(obj: JsonObject, key: String): JsonObject? =
            if (obj.has(key) && obj[key].isJsonObject) obj.getAsJsonObject(key) else null

        private fun getString(obj: JsonObject, key: String): String = getString(obj, key, "")

        private fun getString(obj: JsonObject, key: String, defaultValue: String): String =
            if (obj.has(key) && obj[key].isJsonPrimitive) obj[key].asString else defaultValue

        private fun parseFloat(obj: JsonObject?, key: String, defaultValue: Float): Float {
            if (obj == null || !obj.has(key)) {
                return defaultValue
            }
            return try {
                obj[key].asFloat
            } catch (exception: Exception) {
                defaultValue
            }
        }
    }

    open class VehicleConfig {
        @JvmField var id: String? = null
        @JvmField var displayName: String? = null
        @JvmField var modelFile: String? = null
        @JvmField var modelScale: Float = 1.0f
        @JvmField var modelOffset: Vec3 = Vec3.ZERO
        @JvmField var textures: MutableMap<String, String> = HashMap()
        @JvmField var seatPositions: MutableList<Vec3> = ArrayList()
        @JvmField var bogiePositions: MutableList<Vec3> = ArrayList()
        @JvmField var bogieModels: MutableList<BogieModelConfig> = ArrayList()
        @JvmField var scriptPath: String? = null
        @JvmField var trainDistance: Float = 4.5f
        @JvmField var maxSpeed: Float = 20.0f
        @JvmField var weight: Float = 1000.0f

        @Throws(IOException::class)
        open fun validate() {
            if (modelFile == null || modelFile!!.isBlank()) {
                throw IOException("Model file is required")
            }

            if (seatPositions.isEmpty() && bogiePositions.isEmpty()) {
                RealTrainModRenewed.LOGGER.warn("Vehicle '{}' has no seats or bogies defined", id)
            }
        }
    }

    open class BogieModelConfig {
        @JvmField var modelFile: String? = null
        @JvmField var textures: MutableMap<String, String> = HashMap()
        @JvmField var scale: Float = 1.0f
        @JvmField var offset: Vec3 = Vec3.ZERO
    }
}
