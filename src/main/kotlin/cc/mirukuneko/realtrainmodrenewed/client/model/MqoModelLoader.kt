// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.model

import cc.mirukuneko.realtrainmodrenewed.client.render.VertexWriter
import cc.mirukuneko.realtrainmodrenewed.BundledPackStore.getModJarPath
import cc.mirukuneko.realtrainmodrenewed.Config
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ShaderCompat.isShaderPackInUse
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.modelpack.VehicleModelPackManager
import cc.mirukuneko.realtrainmodrenewed.rail.RailDefinition
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader.resolvePackPath
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder.readText
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader.openZipFile
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader.read
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry.getById
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Lightmap
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.util.Util
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.w3c.dom.Node
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.*
import java.util.function.Function
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import javax.script.Invocable
import javax.script.ScriptEngine
import kotlin.Any
import kotlin.Array
import kotlin.BooleanArray
import kotlin.Byte
import kotlin.ByteArray
import kotlin.Comparator
import kotlin.Exception
import kotlin.FloatArray
import kotlin.Int
import kotlin.IntArray
import kotlin.Long
import kotlin.NumberFormatException
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.compareTo
import kotlin.concurrent.Volatile
import kotlin.floatArrayOf
import kotlin.hashCode
import kotlin.io.use
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.plus
import kotlin.run
import kotlin.synchronized
import kotlin.text.StringBuilder
import kotlin.text.contains
import kotlin.text.endsWith
import kotlin.text.equals
import kotlin.text.format
import kotlin.text.indexOf
import kotlin.text.isBlank
import kotlin.text.isEmpty
import kotlin.text.lastIndexOf
import kotlin.text.lowercase
import kotlin.text.matches
import kotlin.text.replace
import kotlin.text.replaceFirst
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toDouble
import kotlin.text.toFloat
import kotlin.text.toInt
import kotlin.text.toRegex
import kotlin.text.trim
import kotlin.times
import kotlin.use

// import org.jcodec.api.FrameGrab;
// import org.jcodec.common.io.ByteBufferSeekableByteChannel;
// import org.jcodec.common.model.Picture;
// import org.jcodec.scale.AWTUtil;
/**
 * Metasequoia (.mqo) loader aligned with legacy model library `MqoModel`: 0.01 vertex scale, triangulation and quad handling.
 */
object MqoModelLoader {
    private const val RTM_DEFAULT_SMOOTHING_ANGLE = 60.0f
    private const val TEXTURE_META_SEPARATOR = "|ptmeta="
    private val V_PATTERN: Pattern = Pattern.compile("V\\((.+?)\\)")
    private val UV_PATTERN: Pattern = Pattern.compile("UV\\((.+?)\\)")
    private val M_PATTERN: Pattern = Pattern.compile("M\\((.+?)\\)")
    private val TEX_PATTERN: Pattern = Pattern.compile("tex\\(\"([^\"]+)\"\\)")

    /** MQO マテリアルの col(r g b a)。4番目がアルファ(不透明度)。RTM はガラス等をこの a<1 で半透明にする。  */
    private val COL_PATTERN: Pattern =
        Pattern.compile("col\\(\\s*([-0-9.]+)\\s+([-0-9.]+)\\s+([-0-9.]+)\\s+([-0-9.]+)\\s*\\)")
    private val TRAIN_ENTITY_TRANSLUCENT_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation("pipeline/rtmr_train_entity_translucent_no_depth")
        .withShaderDefine("ALPHA_CUTOUT", 0.1f)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withSampler("Sampler1")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
        .build()
    private val TRAIN_ENTITY_TRANSLUCENT_NO_DEPTH: Function<Identifier, RenderType> =
        Util.memoize(
            Function<Identifier, RenderType> { texture ->
                val state = RenderSetup.builder(TRAIN_ENTITY_TRANSLUCENT_NO_DEPTH_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()
                RenderType.create("rtmr_train_entity_translucent_no_depth", state)
            })
    private val MODEL_CACHE_LOCK = Any()
    private val MODEL_CACHE = LinkedHashMap<String?, CachedModel?>(64, 0.75f, true)
    private val FAILED_MODEL_KEYS: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val SOUND_SCRIPT_SOURCE_CACHE: MutableMap<String, String> = ConcurrentHashMap<String, String>()
    private val TEXTURE_INFO_CACHE: MutableMap<String, TextureInfo> = ConcurrentHashMap<String, TextureInfo>()
    private val SCRIPT_TEXTURE_CACHE: MutableMap<String?, ScriptTextureData> =
        ConcurrentHashMap<String?, ScriptTextureData>()
    private val RESOURCE_SEARCH_CACHE: MutableMap<String?, ResourceSearchResult?> =
        ConcurrentHashMap<String?, ResourceSearchResult?>()
    private val MISSING_SCRIPT_WARNINGS: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val SHADER_MOD_IDS = setOf("iris", "oculus")

    @Volatile
    private var sharedPackCandidates: List<Path>? = null
        get() {
            val cached = field
            if (cached != null) {
                return cached
            }
            synchronized(MqoModelLoader::class.java) {
                if (field != null) {
                    return field
                }
                val candidates =
                    LinkedHashSet<Path>()
                try {
                    val gameDir =
                        Minecraft.getInstance().gameDirectory.toPath()
                    addPackCandidates(candidates, gameDir)
                    addPackCandidates(
                        candidates,
                        gameDir.resolve("mods")
                    )
                    addPackCandidates(
                        candidates,
                        gameDir.resolve("content")
                    )
                    addPackCandidates(
                        candidates,
                        gameDir.resolve("vehicle_packs")
                    )
                    val configDir = gameDir.resolve("config").resolve("realtrainmodunofficial")
                    addPackCandidates(
                        candidates,
                        configDir
                    )
                    addPackCandidates(
                        candidates,
                        configDir.resolve("packs")
                    )
                    addPackCandidates(
                        candidates,
                        configDir.resolve("vehicle_packs")
                    )
                    addPackCandidates(
                        candidates,
                        configDir.resolve("rail_packs")
                    )
                    addPackCandidates(
                        candidates,
                        configDir.resolve("bundled_pack_cache")
                    )
                    try {
                        val modJar =
                            getModJarPath()
                        if (modJar != null) {
                            candidates.add(modJar)
                        }
                    } catch (ignored: Exception) {
                    }
                } catch (e: Exception) {
                    RealTrainModRenewed.LOGGER.warn(
                        "Failed to build shared pack search list",
                        e
                    )
                }
                field = candidates.toList()
                return field
            }
        }
    private var fallbackWhite: Identifier? = null
    private var modelCacheBytes: Long = 0
    private var bakedFilterLogCount = 0

    @Volatile
    private var shaderPipelineCacheUntilMillis: Long = 0

    @Volatile
    private var shaderPipelineCacheValue = false
    private val MISSING_RESOURCE = ResourceSearchResult(null, null, "__missing__")

    /** Invalidates path-based model and script data before add-on packs are rescanned. */
    @JvmStatic
    fun clearPackCaches() {
        synchronized(MODEL_CACHE_LOCK) {
            MODEL_CACHE.clear()
            modelCacheBytes = 0L
        }
        FAILED_MODEL_KEYS.clear()
        SOUND_SCRIPT_SOURCE_CACHE.clear()
        TEXTURE_INFO_CACHE.clear()
        SCRIPT_TEXTURE_CACHE.clear()
        RESOURCE_SEARCH_CACHE.clear()
        MISSING_SCRIPT_WARNINGS.clear()
        sharedPackCandidates = null
        fallbackRailModel = null
        fallbackRailAttempted = false
    }

    private fun logModelLoadDetail(phase: String?, pattern: String?, vararg args: Any?) {
        RealTrainModRenewed.LOGGER.debug("[ModelLoad:{}] " + pattern, *MqoModelLoader.prependArg(phase, args))
    }

    private fun prependArg(first: String?, rest: Array<out Any?>): Array<Any?> {
        val merged = arrayOfNulls<Any>(rest.size + 1)
        merged[0] = first
        System.arraycopy(rest, 0, merged, 1, rest.size)
        return merged
    }

    @JvmStatic
    fun loadModelForRail(def: RailDefinition?): MqoModel? {
        if (def == null) return null
        val key = "r|" + def.packName + "|" + def.modelFile + "|" + def.textureOverrides.hashCode()
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null
        }
        val cached = getCachedModel(key)
        if (cached != null) {
            return cached
        }
        val packPath = resolvePackPath(def.packName)
        var model = if (packPath == null)
            null
        else
            MqoModelLoader.loadInternal(packPath, def.modelFile, def.textureOverrides, false)
        if (model == null) {
            // レールモデルが解決できないと描画されず道床(砂利)だけ生成され「レールが無い」
            // 状態になる (ユーザー報告)。標準レール ModelRail_1067mm.mqo を mod jar から
            // フォールバック読み込みし、必ず鉄レールが出るようにする。
            model = loadFallbackRailModel()
        }
        if (model != null) {
            if (packPath != null) loadScriptForModel(model, packPath, def.scriptPath)
            cacheModel(key, model)
        } else {
            FAILED_MODEL_KEYS.add(key)
        }
        return model
    }

    private var fallbackRailModel: MqoModel? = null
    private var fallbackRailAttempted = false

    /** 標準 1067mm レールを mod jar から読み込むフォールバック。失敗しても null を返すだけ。  */
    private fun loadFallbackRailModel(): MqoModel? {
        if (fallbackRailAttempted) return fallbackRailModel
        fallbackRailAttempted = true
        try {
            val modJar = getModJarPath()
            if (modJar != null) {
                fallbackRailModel = loadInternal(
                    modJar, "ModelRail_1067mm.mqo",
                    mapOf("default" to "textures/rail/largeRail.png"), false
                )
            }
        } catch (t: Throwable) {
            RealTrainModRenewed.LOGGER.warn("Failed to load fallback rail model", t)
        }
        return fallbackRailModel
    }

    @JvmStatic
    fun loadModelForVehicle(def: VehicleDefinition?): MqoModel? {
        if (def == null) {
            RealTrainModRenewed.LOGGER.warn("loadModelForVehicle: def is null")
            return null
        }
        val packPath = resolvePackPath(def.getPackName())
        if (packPath == null) {
            RealTrainModRenewed.LOGGER.warn("loadModelForVehicle: packPath is null for pack {}", def.getPackName())
            return null
        }
        val scriptPath = resolveVehicleRenderScriptPath(packPath, def)
        val soundScriptPath = if (def.getSoundScriptPath() != null) def.getSoundScriptPath() else ""
        // legacy script は init() で trainName/modelName ごとの差分を固定するため、車両ID単位で分離する
        val key =
            "v|" + def.getId() + "|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides()
                .hashCode() + "|" + scriptPath.hashCode() + "|" + soundScriptPath.hashCode() + "|smooth"
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null
        }
        val cached = getCachedModel(key)
        if (cached != null) {
            return cached
        }
        RealTrainModRenewed.LOGGER.debug("loadModelForVehicle: vehicleId={}, scriptPath='{}'", def.getId(), scriptPath)
        val model = MqoModelLoader.loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), true)
        if (model != null) {
            RealTrainModRenewed.LOGGER.info("loadModelForVehicle: model loaded, loading script")
            loadScriptForModel(model, packPath, scriptPath, def.getId())
            cacheModel(key, model)
        } else {
            RealTrainModRenewed.LOGGER.warn("loadModelForVehicle: model is null")
            FAILED_MODEL_KEYS.add(key)
        }
        return model
    }

    private fun resolveVehicleRenderScriptPath(packPath: Path?, def: VehicleDefinition?): String {
        if (def == null) {
            return ""
        }
        val explicit = normalizeScriptPath(def.getScriptPath())
        if (!explicit.isBlank()) {
            return explicit
        }
        val inferred = inferVehicleRenderScriptPath(packPath, def.getId(), def.getModelFile())
        if (!inferred.isBlank()) {
            RealTrainModRenewed.LOGGER.info("Inferred legacy render script '{}' for vehicle {}", inferred, def.getId())
            return inferred
        }
        return ""
    }

    private fun inferVehicleRenderScriptPath(packPath: Path?, vehicleId: String, modelFile: String): String {
        if (packPath == null || !Files.exists(packPath)) {
            return ""
        }
        val candidates = LinkedHashSet<String?>()
        for (token in listOf(vehicleId, modelFile)) {
            val family = inferVehicleScriptFamily(token)
            if (family.isBlank()) {
                continue
            }
            candidates.add("assets/minecraft/scripts/render_" + family + ".js")
            candidates.add("scripts/render_" + family + ".js")
            candidates.add("assets/minecraft/scripts/" + family + ".js")
            candidates.add("scripts/" + family + ".js")
        }
        for (candidate in candidates) {
            try {
                if (Files.isDirectory(packPath)) {
                    val file = resolveFilePathInPack(packPath, candidate)
                    if (file != null && Files.exists(file)) {
                        return candidate!!
                    }
                } else {
                    openZipFile(packPath).use { zip ->
                        if (findEntry(zip, candidate) != null) {
                            return candidate!!
                        }
                    }
                }
            } catch (ignored: IOException) {
            }
        }
        return ""
    }

    private fun inferVehicleScriptFamily(raw: String?): String {
        if (raw == null || raw.isBlank()) {
            return ""
        }
        var base = raw.replace('\\', '/')
        val slash = base.lastIndexOf('/')
        if (slash >= 0) {
            base = base.substring(slash + 1)
        }
        val dot = base.lastIndexOf('.')
        if (dot > 0) {
            base = base.substring(0, dot)
        }
        base = base.lowercase()
        if (base.startsWith("modeltrain_")) {
            base = base.substring("modeltrain_".length)
        }
        base = base.replaceFirst("_(?:mc|mcp\\d+|p\\d+)$".toRegex(), "")
        return base
    }

    @JvmStatic
    fun loadServerScriptForVehicle(def: VehicleDefinition?): ScriptEngine? {
        if (def == null || !def.hasServerScript()) {
            return null
        }
        val packPath = resolvePackPath(def.getPackName())
        if (packPath == null) {
            return null
        }
        val scriptPath = def.getServerScriptPath()
        val key = "server|" + def.getId() + "|" + def.getPackName() + "|" + scriptPath
        val scriptSource = SOUND_SCRIPT_SOURCE_CACHE.computeIfAbsent(key) { ignored: String? ->
            val loaded = loadStandaloneScriptSource(packPath, scriptPath)
            if (loaded == null) "" else loaded
        }
        if (scriptSource == null || scriptSource.isBlank()) {
            return null
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, scriptSource, def.getId())
    }

    @JvmStatic
    fun loadSoundScriptForVehicle(def: VehicleDefinition?): ScriptEngine? {
        if (def == null || !def.hasSoundScript()) {
            return null
        }
        val packPath = resolvePackPath(def.getPackName())
        if (packPath == null) {
            return null
        }
        val scriptPath = def.getSoundScriptPath()
        val key = "sound|" + def.getId() + "|" + def.getPackName() + "|" + scriptPath
        val scriptSource = SOUND_SCRIPT_SOURCE_CACHE.computeIfAbsent(key) { ignored: String? ->
            val loaded = loadStandaloneScriptSource(packPath, scriptPath)
            if (loaded == null) "" else loaded
        }
        if (scriptSource == null || scriptSource.isBlank()) {
            return null
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, scriptSource, def.getId())
    }

    @JvmOverloads
    fun loadModelForVehiclePart(
        def: VehicleDefinition?,
        modelFile: String?,
        textureOverrides: Map<String, String>?,
        scriptPath: String? = ""
    ): MqoModel? {
        if (def == null || modelFile == null || modelFile.isBlank()) return null
        val tex = textureOverrides ?: emptyMap()
        val script = if (scriptPath == null) "" else scriptPath
        val key = "vp|" + def.getPackName() + "|" + modelFile + "|" + tex.hashCode() + "|smooth|" + script.hashCode()
        val cached = getCachedModel(key)
        if (cached != null) {
            return cached
        }
        val packPath = resolvePackPath(def.getPackName())
        val model = loadInternal(packPath, modelFile, tex, true)
        if (model != null) {
            if (!script.isBlank()) {
                loadScriptForModel(model, packPath, script, def.getId())
            }
            cacheModel(key, model)
        }
        return model
    }

    @JvmStatic
    fun resolvePackTexture(packName: String?, texturePath: String?): Identifier? {
        if (packName == null || packName.isBlank() || texturePath == null || texturePath.isBlank()) {
            return fallbackTexture()
        }
        val packPath = resolvePackPath(packName)
        if (packPath == null) {
            return fallbackTexture()
        }
        val binding: TextureBinding = TextureBinding.Companion.parse(texturePath)
        val cacheKey = packPath.toString() + "|" + binding.cacheKey()
        val info = TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey) { key: String? ->
            registerTextureFromZip(binding, object : TextureOpener {
                @Throws(Exception::class)
                override fun open(rel: String?): InputStream? {
                    return openTexture(packPath, rel)
                }

                override fun getPackKey(): String = packPath.toString()
            })
        }
        return info.location
    }

    @JvmStatic
    fun loadModelFromPack(
        packName: String?, modelFile: String?, textureOverrides: Map<String, String>?,
        scriptPath: String?, smoothing: Boolean
    ): MqoModel? {
        if (packName == null || modelFile == null || modelFile.isBlank()) {
            return null
        }
        val tex = textureOverrides ?: emptyMap()
        val key =
            "p|" + packName + "|" + modelFile + "|" + tex.hashCode() + "|" + smoothing + "|" + (if (scriptPath == null) 0 else scriptPath.hashCode())
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null
        }
        val cached = getCachedModel(key)
        if (cached != null) {
            return cached
        }
        val packPath = resolvePackPath(packName)
        if (packPath == null) {
            FAILED_MODEL_KEYS.add(key)
            return null
        }
        val model = loadInternal(packPath, modelFile, tex, smoothing)
        if (model != null) {
            loadScriptForModel(model, packPath, scriptPath)
            cacheModel(key, model)
        } else {
            FAILED_MODEL_KEYS.add(key)
        }
        return model
    }

    private fun loadInternal(
        packPath: Path?,
        modelFile: String?,
        textureOverrides: Map<String, String>,
        smoothing: Boolean
    ): MqoModel? {
        if (packPath == null || !Files.exists(packPath)) return null
        logModelLoadDetail(
            "begin",
            "packPath={}, modelFile={}, smoothing={}, textureOverrides={}",
            packPath,
            modelFile,
            smoothing,
            textureOverrides
        )
        try {
            if (Files.isDirectory(packPath)) {
                val modelResource = findResource(modelFile, packPath)
                if (modelResource == null) {
                    RealTrainModRenewed.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile)
                    return null
                }
                val modelPackPath = modelResource.packPath
                if (modelPackPath == null) {
                    RealTrainModRenewed.LOGGER.warn(
                        "Resolved MQO had no source pack for {} from {}",
                        modelFile,
                        packPath
                    )
                    return null
                }
                logModelLoadDetail(
                    "resolved",
                    "modelFile={} resolvedPack={} filePath={} zipEntry={}",
                    modelFile,
                    modelPackPath,
                    modelResource.filePath,
                    modelResource.zipEntryName
                )
                val lowerModelFile = modelFile!!.lowercase()
                val opener: TextureOpener = object : TextureOpener {
                    @Throws(Exception::class)
                    override fun open(rel: String?): InputStream? {
                        return openTexture(modelPackPath, rel)
                    }

                    override fun getPackKey(): String {
                        return modelPackPath.toString()
                    }
                }
                if (lowerModelFile.endsWith(".obj")) {
                    return bakeObj(readText(modelResource), opener, textureOverrides, smoothing)
                }
                val text = if (lowerModelFile.endsWith(".mqoz"))
                    readCompressedMqo(modelResource)
                else
                    readText(modelResource)
                return bake(text, opener, textureOverrides, smoothing)
            }
            openZipFile(packPath).use { zf ->
                val modelResource = findResource(modelFile, packPath)
                if (modelResource == null) {
                    RealTrainModRenewed.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile)
                    return null
                }
                val modelPackPath = modelResource.packPath
                if (modelPackPath == null) {
                    RealTrainModRenewed.LOGGER.warn(
                        "Resolved MQO had no source pack for {} from {}",
                        modelFile,
                        packPath
                    )
                    return null
                }
                logModelLoadDetail(
                    "resolved",
                    "modelFile={} resolvedPack={} filePath={} zipEntry={}",
                    modelFile,
                    modelPackPath,
                    modelResource.filePath,
                    modelResource.zipEntryName
                )
                val lowerModelFile = modelFile!!.lowercase()
                val opener: TextureOpener = object : TextureOpener {
                    @Throws(Exception::class)
                    override fun open(rel: String?): InputStream? {
                        return openTexture(modelPackPath, rel)
                    }

                    override fun getPackKey(): String {
                        return modelPackPath.toString()
                    }
                }
                if (lowerModelFile.endsWith(".obj")) {
                    return bakeObj(readText(modelResource), opener, textureOverrides, smoothing)
                }
                val text = if (lowerModelFile.endsWith(".mqoz"))
                    readCompressedMqo(modelResource)
                else
                    readText(modelResource)
                return bake(text, opener, textureOverrides, smoothing)
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to load MQO {} from {}", modelFile, packPath, e)
            return null
        }
    }

    @Throws(IOException::class)
    private fun openTexture(packPath: Path?, relative: String?): InputStream? {
        if (packPath == null) {
            return null
        }
        if (Files.isDirectory(packPath)) {
            val file = resolveFilePathInPack(packPath, relative)
            if (file != null) {
                return Files.newInputStream(file)
            }
        } else {
            val zip = openZipFile(packPath)
            val entry = findEntry(zip, relative)
            if (entry != null) {
                val raw = zip.getInputStream(entry)
                return object : FilterInputStream(raw) {
                    @Throws(IOException::class)
                    override fun close() {
                        super.close()
                        zip.close()
                    }
                }
            }
            zip.close()
        }
        val fallback = findResource(relative, packPath)
        if (fallback == null || packPath == fallback.packPath) {
            return null
        }
        return openResource(fallback)
    }

    @Throws(IOException::class)
    private fun readCompressedMqo(path: Path): String {
        openZipFile(path).use { zf ->
            for (entry in Collections.list(zf.entries())) {
                if (!entry.isDirectory() && entry.getName().lowercase().endsWith(".mqo")) {
                    zf.getInputStream(entry).use { `in` ->
                        return readText(`in`)
                    }
                }
            }
        }
        throw IOException("No .mqo entry found inside compressed MQO: " + path)
    }

    @Throws(IOException::class)
    private fun readCompressedMqo(input: InputStream): String {
        val result = arrayOfNulls<String>(1)
        read(input, PackZipReader.EntryConsumer { entry: ZipEntry?, entryInput: InputStream? ->
            if (result[0] == null && !entry!!.isDirectory() && entry.getName().lowercase().endsWith(".mqo")) {
                result[0] = PackTextDecoder.readText(entryInput!!)
            }
        })
        result[0]?.let { return it }
        throw IOException("No .mqo entry found inside compressed MQO stream")
    }

    private fun getCachedModel(key: String?): MqoModel? {
        synchronized(MODEL_CACHE_LOCK) {
            val cached = MODEL_CACHE.get(key)
            if (cached == null) {
                return null
            }
            cached.touch(System.nanoTime())
            return cached.model()
        }
    }

    private fun cacheModel(key: String?, model: MqoModel?) {
        if (key == null || model == null) {
            return
        }
        synchronized(MODEL_CACHE_LOCK) {
            val previous = MODEL_CACHE.remove(key)
            if (previous != null) {
                modelCacheBytes -= previous.estimatedBytes()
            }
            val cached = CachedModel(model, model.estimateMemoryBytes(), System.nanoTime())
            MODEL_CACHE.put(key, cached)
            modelCacheBytes += cached.estimatedBytes()
            evictModelCacheLocked()
        }
    }

    private fun evictModelCacheLocked() {
        val limitBytes = max(1024L, Config.MODEL_CACHE_LIMIT_MIB.get().toLong()) * 1024L * 1024L
        val protectNanos = max(300L, Config.MODEL_CACHE_PROTECT_SECONDS.get().toLong()) * 1000000000L
        if (modelCacheBytes <= limitBytes) {
            return
        }
        val now = System.nanoTime()
        val iterator = MODEL_CACHE.entries.iterator()
        while (modelCacheBytes > limitBytes && iterator.hasNext()) {
            val entry = iterator.next()
            val cached = entry.value ?: continue
            if (protectNanos > 0L && now - cached.lastAccessNanos() < protectNanos) {
                continue
            }
            modelCacheBytes -= cached.estimatedBytes()
            iterator.remove()
        }
    }

    @Throws(IOException::class)
    private fun resolveFilePathInPack(root: Path, relative: String?): Path? {
        if (relative == null) return null
        val norm = relative.replace('\\', '/')
        for (candidatePath in candidateResourcePaths(norm)) {
            val candidate = root.resolve(candidatePath)
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate
            }
        }
        val leaf = if (norm.contains("/")) norm.substring(norm.lastIndexOf('/') + 1) else norm
        Files.walk(root).use { stream ->
            for (file in Iterable { stream.iterator() }) {
                if (!Files.isRegularFile(file)) continue
                val name = file.getFileName().toString()
                if (name.equals(norm, ignoreCase = true) || name.equals(leaf, ignoreCase = true)) return file
            }
        }
        return null
    }

    private fun normalizeScriptPath(scriptPath: String?): String {
        if (scriptPath == null || scriptPath.isBlank()) {
            return ""
        }
        return scriptPath.replace('\\', '/').replaceFirst("^/+".toRegex(), "")
    }

    private fun findEntry(zf: ZipFile, relative: String?): ZipEntry? {
        if (relative == null) return null
        val norm = relative.replace('\\', '/')
        for (candidatePath in candidateResourcePaths(norm)) {
            val direct = zf.getEntry(candidatePath)
            if (direct != null && !direct.isDirectory()) {
                return direct
            }
        }
        val leaf = if (norm.contains("/")) norm.substring(norm.lastIndexOf('/') + 1) else norm
        val leafLower = leaf.lowercase()
        val en = zf.entries()
        while (en.hasMoreElements()) {
            val ze: ZipEntry = en.nextElement()
            if (ze.isDirectory()) continue
            val name = ze.getName().replace('\\', '/')
            if (name.equals(norm, ignoreCase = true)) return ze
            val slash = name.lastIndexOf('/')
            val shortName = if (slash >= 0) name.substring(slash + 1) else name
            if (shortName.equals(leaf, ignoreCase = true) || shortName.equals(leafLower, ignoreCase = true)) return ze
        }
        return null
    }

    private fun candidateResourcePaths(norm: String): MutableList<String> {
        val candidates: MutableList<String> = ArrayList<String>()
        candidates.add(norm)
        candidates.add("assets/minecraft/" + norm)
        if (!norm.startsWith("textures/")) {
            candidates.add("assets/minecraft/textures/" + norm)
        }
        if (!norm.startsWith("models/") && looksLikeModelPath(norm)) {
            candidates.add("assets/minecraft/models/" + norm)
        }
        if (!norm.startsWith("scripts/") && looksLikeScriptPath(norm)) {
            candidates.add("assets/minecraft/scripts/" + norm)
        }
        return candidates
    }

    private fun looksLikeModelPath(norm: String): Boolean {
        val lower = norm.lowercase()
        return lower.endsWith(".mqo") || lower.endsWith(".mqoz") || lower.endsWith(".obj") || lower.endsWith(".ngto")
    }

    private fun looksLikeScriptPath(norm: String): Boolean {
        val lower = norm.lowercase()
        return lower.endsWith(".js")
    }

    @Throws(IOException::class)
    private fun findResource(relative: String?, preferredPackPath: Path?): ResourceSearchResult? {
        if (relative == null || relative.isBlank()) {
            return null
        }
        val normalized = normalize(relative).replaceFirst("^/+".toRegex(), "")
        val leaf = if (normalized.contains("/")) normalized.substring(normalized.lastIndexOf('/') + 1) else normalized
        val preferredKey =
            if (preferredPackPath == null) "" else preferredPackPath.toAbsolutePath().normalize().toString()
        val cacheKey = preferredKey + "|" + normalized
        val cached = RESOURCE_SEARCH_CACHE.get(cacheKey)
        if (cached != null) {
            logModelLoadDetail(
                "resource-cache",
                "relative={} preferredPack={} hit={} resolvedPack={} filePath={} zipEntry={}",
                normalized,
                preferredPackPath,
                cached !== MISSING_RESOURCE,
                cached.packPath,
                cached.filePath,
                cached.zipEntryName
            )
            return if (cached === MISSING_RESOURCE) null else cached
        }
        val candidates = LinkedHashSet<Path?>()
        if (preferredPackPath != null) {
            candidates.add(preferredPackPath.toAbsolutePath().normalize())
        }
        candidates.addAll(sharedPackCandidates!!)
        for (candidate in candidates) {
            logModelLoadDetail("resource-scan", "relative={} candidatePack={}", normalized, candidate)
            var found = findResourceInPack(candidate, normalized)
            if (found != null) {
                logModelLoadDetail(
                    "resource-hit", "relative={} candidatePack={} filePath={} zipEntry={}",
                    normalized, candidate, found.filePath, found.zipEntryName
                )
                RESOURCE_SEARCH_CACHE.put(cacheKey, found)
                return found
            }
            if (leaf != normalized) {
                found = findResourceInPack(candidate, leaf)
                if (found != null) {
                    logModelLoadDetail(
                        "resource-hit-leaf", "relative={} leaf={} candidatePack={} filePath={} zipEntry={}",
                        normalized, leaf, candidate, found.filePath, found.zipEntryName
                    )
                    RESOURCE_SEARCH_CACHE.put(cacheKey, found)
                    return found
                }
            }
        }
        logModelLoadDetail(
            "resource-miss",
            "relative={} preferredPack={} searchedPacks={}",
            normalized,
            preferredPackPath,
            candidates
        )
        RESOURCE_SEARCH_CACHE.put(cacheKey, MISSING_RESOURCE)
        return null
    }

    @Throws(IOException::class)
    private fun findResourceInPack(packPath: Path?, relative: String?): ResourceSearchResult? {
        if (packPath == null || relative == null || relative.isBlank() || !Files.exists(packPath)) {
            return null
        }
        if (Files.isDirectory(packPath)) {
            val file = resolveFilePathInPack(packPath, relative)
            return if (file != null) ResourceSearchResult(packPath, file, null) else null
        }
        try {
            openZipFile(packPath).use { zip ->
                val entry = findEntry(zip, relative)
                return if (entry != null) ResourceSearchResult(packPath, null, entry.getName()) else null
            }
        } catch (e: IOException) {
            return null
        }
    }

    private fun addPackCandidates(candidates: LinkedHashSet<Path>, dir: Path?) {
        if (dir == null || !Files.isDirectory(dir)) {
            return
        }
        try {
            Files.list(dir).use { stream ->
                stream.forEach { path: Path ->
                    try {
                        if (Files.isDirectory(path) || isSupportedArchive(path)) {
                            candidates.add(path!!.toAbsolutePath().normalize())
                        }
                    } catch (ignored: Exception) {
                    }
                }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun isSupportedArchive(path: Path): Boolean {
        val fileName = path.getFileName().toString().lowercase()
        return fileName.endsWith(".zip") || fileName.endsWith(".jar")
    }

    @Throws(IOException::class)
    private fun readText(resource: ResourceSearchResult): String {
        if (resource.filePath != null) {
            return readText(resource.filePath)
        }
        PackZipReader.openZipFile(resource.packPath!!).use { zip ->
            val entry = zip.getEntry(resource.zipEntryName)
            if (entry == null) {
                throw IOException("Missing zip entry: " + resource.zipEntryName)
            }
            zip.getInputStream(entry).use { `in` ->
                return readText(`in`)
            }
        }
    }

    @Throws(IOException::class)
    private fun readCompressedMqo(resource: ResourceSearchResult): String {
        if (resource.filePath != null) {
            return readCompressedMqo(resource.filePath)
        }
        PackZipReader.openZipFile(resource.packPath!!).use { zip ->
            val entry = zip.getEntry(resource.zipEntryName)
            if (entry == null) {
                throw IOException("Missing zip entry: " + resource.zipEntryName)
            }
            zip.getInputStream(entry).use { `in` ->
                return readCompressedMqo(`in`)
            }
        }
    }

    @Throws(IOException::class)
    private fun openResource(resource: ResourceSearchResult?): InputStream? {
        if (resource == null) {
            return null
        }
        if (resource.filePath != null) {
            return Files.newInputStream(resource.filePath)
        }
        val zip = PackZipReader.openZipFile(resource.packPath!!)
        val entry = zip.getEntry(resource.zipEntryName)
        if (entry == null) {
            zip.close()
            return null
        }
        val raw = zip.getInputStream(entry)
        return object : FilterInputStream(raw) {
            @Throws(IOException::class)
            override fun close() {
                super.close()
                zip.close()
            }
        }
    }

    @Throws(Exception::class)
    private fun bake(
        mqoText: String,
        opener: TextureOpener,
        textureOverrides: Map<String, String>,
        smoothing: Boolean
    ): MqoModel {
        val materialOrder: MutableList<String?> = ArrayList<String?>()
        val materialTexPaths: MutableList<String?> = ArrayList<String?>()
        val materialAlphas: MutableList<Float?> = ArrayList<Float?>()
        val currentVerts: MutableList<Vec3> = ArrayList<Vec3>()
        // key = groupName + "|" + matKey so each object×material pair is a separate batch
        val byGroup: MutableMap<String?, BatchBuilder> = LinkedHashMap<String?, BatchBuilder>()
        var mirrorType = -1
        var braceType = -1
        var currentGroup: String? = "default"
        var currentFacetAngle = RTM_DEFAULT_SMOOTHING_ANGLE
        val OBJ_NAME = Pattern.compile("Object\\s+\"([^\"]*)\"")

        val lines = mqoText.split("\\R".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (raw in lines) {
            val line = raw.trim { it <= ' ' }
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line == "{") continue
            if (line.startsWith("}")) {
                braceType = -1
                continue
            }
            if (braceType >= 0) {
                if (braceType == 1) {
                    val v = parseVertexLine(line)
                    if (v != null) currentVerts.add(v)
                } else if (braceType == 2) {
                    addFaceLine(
                        line,
                        currentVerts,
                        materialOrder,
                        materialTexPaths,
                        materialAlphas,
                        textureOverrides,
                        opener,
                        mirrorType,
                        currentGroup,
                        currentFacetAngle,
                        byGroup
                    )
                } else if (braceType == 3) {
                    val tok = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (tok.size > 0) {
                        val name = tok[0].replace("\"", "")
                        if (!name.isBlank()) {
                            materialOrder.add(name)
                            val texMatcher = TEX_PATTERN.matcher(line)
                            materialTexPaths.add(if (texMatcher.find()) texMatcher.group(1) else null)
                            val colMatcher = COL_PATTERN.matcher(line)
                            var matAlpha = 1.0f
                            if (colMatcher.find()) {
                                try {
                                    matAlpha = colMatcher.group(4).toFloat()
                                } catch (ignored: NumberFormatException) {
                                }
                            }
                            materialAlphas.add(matAlpha)
                        }
                    }
                }
                continue
            }
            if (line.startsWith("Material ")) {
                braceType = 3
                continue
            }
            if (line.startsWith("vertex ")) {
                currentVerts.clear()
                braceType = 1
                continue
            }
            if (line.startsWith("face ")) {
                braceType = 2
                continue
            }
            if (line.startsWith("Object ")) {
                mirrorType = -1
                currentFacetAngle = RTM_DEFAULT_SMOOTHING_ANGLE
                val m = OBJ_NAME.matcher(line)
                currentGroup = if (m.find()) m.group(1) else "default"
                continue
            }
            if (line.startsWith("facet ")) {
                val p = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (p.size > 1) {
                    try {
                        currentFacetAngle = p[1].toFloat()
                    } catch (ignored: NumberFormatException) {
                    }
                }
                continue
            }
            if (line.startsWith("mirror_axis ")) {
                val p = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (p.size > 1) {
                    val axis = p[1].toInt()
                    mirrorType = if (axis == 1) 0 else if (axis == 2) 1 else if (axis == 3) 2 else -1
                }
            }
        }

        val out: MutableList<Batch?> = ArrayList<Batch?>()
        for (bb in byGroup.values) {
            if (!bb.positions.isEmpty()) out.add(bb.bake(smoothing))
        }
        val materialTextures: MutableList<Identifier?> = ArrayList<Identifier?>(materialOrder.size)
        for (i in materialOrder.indices) {
            materialTextures.add(
                resolveTexture(
                    i.toByte(),
                    materialOrder,
                    materialTexPaths,
                    textureOverrides,
                    opener
                ).location
            )
        }
        return MqoModel(out, materialTextures)
    }

    @Throws(Exception::class)
    private fun bakeObj(
        objText: String,
        opener: TextureOpener,
        textureOverrides: Map<String, String>,
        smoothing: Boolean
    ): MqoModel {
        val vertices: MutableList<Vec3?> = ArrayList<Vec3?>()
        val texCoords: MutableList<FloatArray> = ArrayList<FloatArray>()
        val normals: MutableList<Vector3f?> = ArrayList<Vector3f?>()
        val materialTextures: MutableMap<String, String> = HashMap<String, String>()
        val materialAlphas: MutableMap<String, Float> = HashMap<String, Float>()
        val byGroup: MutableMap<String?, BatchBuilder> = LinkedHashMap<String?, BatchBuilder>()
        var currentGroup = "default"
        var currentMaterial = "default"

        for (raw in objText.split("\\R".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val line = raw.trim { it <= ' ' }
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }
            if (line.startsWith("mtllib ")) {
                val library = loadObjMaterialLibrary(line.substring(7).trim { it <= ' ' }, opener)
                materialTextures.putAll(library.textures)
                materialAlphas.putAll(library.alphas)
                continue
            }
            if (line.startsWith("o ") || line.startsWith("g ")) {
                val name = line.substring(2).trim { it <= ' ' }
                currentGroup = if (name.isBlank()) "default" else name
                continue
            }
            if (line.startsWith("usemtl ")) {
                val name = line.substring(7).trim { it <= ' ' }
                currentMaterial = if (name.isBlank()) "default" else name
                continue
            }
            if (line.startsWith("v ")) {
                val parts = line.substring(2).trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (parts.size >= 3) {
                    vertices.add(
                        Vec3(
                            parts[0].toDouble(),
                            parts[1].toDouble(),
                            parts[2].toDouble()
                        )
                    )
                }
                continue
            }
            if (line.startsWith("vt ")) {
                val parts = line.substring(3).trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (parts.size >= 2) {
                    texCoords.add(
                        floatArrayOf(
                            parts[0].toFloat(),
                            1.0f - parts[1].toFloat()
                        )
                    )
                }
                continue
            }
            if (line.startsWith("vn ")) {
                val parts = line.substring(3).trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (parts.size >= 3) {
                    val normal = Vector3f(
                        parts[0].toFloat(),
                        parts[1].toFloat(),
                        parts[2].toFloat()
                    )
                    if (normal.lengthSquared() > 1.0E-8f) {
                        normal.normalize()
                    }
                    normals.add(normal)
                }
                continue
            }
            if (!line.startsWith("f ")) {
                continue
            }

            val faceVertices = parseObjFace(line.substring(2).trim { it <= ' ' }, vertices, texCoords, normals)
            if (faceVertices.size < 3) {
                continue
            }

            val textureInfo = resolveObjTexture(currentMaterial, materialTextures, textureOverrides, opener)
            val materialAlpha = materialAlphas[currentMaterial] ?: 1.0f
            val uvBounds = flattenUvs(faceVertices)
            var avgY = 0f
            for (fv in faceVertices) avgY += fv.position!!.y.toFloat()
            avgY /= faceVertices.size.toFloat()
            val materialLower = currentMaterial.lowercase()
            val groupLower = currentGroup.lowercase()
            val namedGlassMaterial = materialLower.contains("glass") || materialLower.contains("window")
            val unnamedMaterial = materialLower.isBlank() || materialLower == "default"
            val glassMaterial = namedGlassMaterial || unnamedMaterial &&
                (groupLower.contains("glass") || groupLower.contains("window") || groupLower.contains("wind") ||
                    groupLower == "alpha" || groupLower.startsWith("alpha_"))
            val translucent = materialAlpha < 0.999f || glassMaterial ||
                shouldTreatFaceAsTranslucent(textureInfo, currentGroup, uvBounds, faceVertices.size, avgY)
            val materialId = if (currentMaterial.equals("light", ignoreCase = true)) {
                2
            } else {
                currentMaterial.hashCode() and 0x7FFFFFFF
            }
            val batchKey = currentGroup + "|" + currentMaterial + "|" + translucent
            val batchGroupName = currentGroup
            val bb = byGroup.computeIfAbsent(
                batchKey
            ) { k: String? ->
                val builder = BatchBuilder(
                    byGroup.size,
                    batchGroupName,
                    textureInfo.location,
                    textureInfo.emissiveTextures,
                    materialId,
                    translucent,
                    60.0f
                )
                builder.baseAlpha = materialAlpha
                builder.glassTranslucent = materialAlpha < 0.999f || glassMaterial || textureInfo.hasGlassBand
                builder.explicitGlassOnly = glassMaterial
                builder.opaqueTexture = textureInfo.opaqueLocation
                builder.windowTexture =
                    if (materialAlpha < 0.999f || glassMaterial) textureInfo.location else textureInfo.windowLocation
                builder
            }

            if (faceVertices.size == 4) {
                emitObjQuad(faceVertices[0], faceVertices[1], faceVertices[2], faceVertices[3], bb)
            } else {
                for (i in 1..<faceVertices.size - 1) {
                    emitObjTri(faceVertices[0], faceVertices[i], faceVertices[i + 1], bb)
                }
            }
        }

        val out: MutableList<Batch?> = ArrayList<Batch?>()
        val uniqueTextures = LinkedHashSet<Identifier?>()
        for (bb in byGroup.values) {
            if (!bb.positions.isEmpty()) {
                val batch = bb.bake(smoothing)
                out.add(batch)
                uniqueTextures.add(batch.texture)
            }
        }
        return MqoModel(out, ArrayList<Identifier?>(uniqueTextures))
    }

    private fun parseObjFace(
        faceSpec: String,
        vertices: MutableList<Vec3?>,
        texCoords: MutableList<FloatArray>,
        normals: MutableList<Vector3f?>
    ): Array<ObjFaceVertex> {
        val parts = faceSpec.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val out: MutableList<ObjFaceVertex?> = ArrayList<ObjFaceVertex?>(parts.size)
        for (part in parts) {
            if (part.isBlank()) {
                continue
            }
            val indices: Array<String?> = part.split("/".toRegex()).toTypedArray()
            val vertexIndex = resolveObjIndex(if (indices.size > 0) indices[0] else "", vertices.size)
            if (vertexIndex < 0 || vertexIndex >= vertices.size) {
                continue
            }
            var u = 0.0f
            var v = 0.0f
            if (indices.size > 1 && !indices[1]!!.isBlank()) {
                val texIndex = resolveObjIndex(indices[1], texCoords.size)
                if (texIndex >= 0 && texIndex < texCoords.size) {
                    val uv = texCoords.get(texIndex)
                    u = uv[0]
                    v = uv[1]
                }
            }
            var normal: Vector3f? = null
            if (indices.size > 2 && !indices[2]!!.isBlank()) {
                val normalIndex = resolveObjIndex(indices[2], normals.size)
                if (normalIndex >= 0 && normalIndex < normals.size) {
                    normal = Vector3f(normals.get(normalIndex))
                }
            }
            out.add(ObjFaceVertex(vertices.get(vertexIndex), u, v, normal))
        }
        return out.filterNotNull().toTypedArray()
    }

    private fun resolveObjIndex(token: String?, size: Int): Int {
        if (token == null || token.isBlank()) {
            return -1
        }
        val index = token.trim { it <= ' ' }.toInt()
        return if (index > 0) index - 1 else size + index
    }

    private fun flattenUvs(vertices: Array<ObjFaceVertex>): FloatArray {
        val out = FloatArray(vertices.size * 2)
        for (i in vertices.indices) {
            out[i * 2] = vertices[i].u
            out[i * 2 + 1] = vertices[i].v
        }
        return out
    }

    private fun emitObjQuad(
        v0: ObjFaceVertex,
        v1: ObjFaceVertex,
        v2: ObjFaceVertex,
        v3: ObjFaceVertex,
        bb: BatchBuilder
    ) {
        val normal = chooseFaceNormal(v0, v1, v2, v3)
        if (!bb.markFace(
                arrayOf<Vec3>(v0.position!!, v1.position!!, v2.position!!, v3.position!!),
                floatArrayOf(v0.u, v0.v, v1.u, v1.v, v2.u, v2.v, v3.u, v3.v)
            )
        ) {
            return
        }
        putObjVertex(bb, v0, normal)
        putObjVertex(bb, v1, normal)
        putObjVertex(bb, v2, normal)
        putObjVertex(bb, v3, normal)
    }

    private fun emitObjTri(v0: ObjFaceVertex, v1: ObjFaceVertex, v2: ObjFaceVertex, bb: BatchBuilder) {
        val normal = chooseFaceNormal(v0, v1, v2, null)
        if (!bb.markFace(
                arrayOf<Vec3>(v0.position!!, v1.position!!, v2.position!!, v2.position),
                floatArrayOf(v0.u, v0.v, v1.u, v1.v, v2.u, v2.v, v2.u, v2.v)
            )
        ) {
            return
        }
        putObjVertex(bb, v0, normal)
        putObjVertex(bb, v1, normal)
        putObjVertex(bb, v2, normal)
        putObjVertex(bb, v2, normal)
    }

    private fun putObjVertex(bb: BatchBuilder, vertex: ObjFaceVertex, fallbackNormal: Vector3f) {
        val normal = if (vertex.normal != null) Vector3f(vertex.normal) else Vector3f(fallbackNormal)
        if (normal.lengthSquared() <= 1.0E-8f) {
            normal.set(0.0f, 1.0f, 0.0f)
        } else {
            normal.normalize()
        }
        bb.put(vertex.position!!, normal, vertex.u, vertex.v)
    }

    private fun chooseFaceNormal(
        v0: ObjFaceVertex,
        v1: ObjFaceVertex,
        v2: ObjFaceVertex,
        v3: ObjFaceVertex?
    ): Vector3f {
        val supplied = averageSuppliedNormals(v0, v1, v2, v3)
        if (supplied != null) {
            return supplied
        }
        val e1 = Vector3f(
            (v1.position!!.x - v0.position!!.x).toFloat(),
            (v1.position.y - v0.position.y).toFloat(),
            (v1.position.z - v0.position.z).toFloat()
        )
        val e2 = Vector3f(
            (v2.position!!.x - v0.position.x).toFloat(),
            (v2.position.y - v0.position.y).toFloat(),
            (v2.position.z - v0.position.z).toFloat()
        )
        var normal = e1.cross(e2)
        if (normal.lengthSquared() <= 1.0E-8f && v3 != null) {
            e2.set(
                (v3.position!!.x - v0.position.x).toFloat(),
                (v3.position.y - v0.position.y).toFloat(),
                (v3.position.z - v0.position.z).toFloat()
            )
            normal = e1.cross(e2)
        }
        if (normal.lengthSquared() <= 1.0E-8f) {
            normal.set(0.0f, 1.0f, 0.0f)
        } else {
            normal.normalize()
        }
        return normal
    }

    private fun averageSuppliedNormals(vararg vertices: ObjFaceVertex?): Vector3f? {
        val sum = Vector3f()
        var count = 0
        for (vertex in vertices) {
            if (vertex != null && vertex.normal != null) {
                sum.add(vertex.normal)
                count++
            }
        }
        if (count == 0 || sum.lengthSquared() <= 1.0E-8f) {
            return null
        }
        sum.normalize()
        return sum
    }

    @Throws(Exception::class)
    private fun resolveObjTexture(
        materialName: String?, materialTextures: Map<String, String>,
        textureOverrides: Map<String, String>, opener: TextureOpener
    ): TextureInfo {
        var path: String? = null
        if (materialName != null && textureOverrides.containsKey(materialName)) {
            path = textureOverrides.get(materialName)
        }
        if ((path == null || path.isBlank()) && textureOverrides.containsKey("default")) {
            path = textureOverrides.get("default")
        }
        // OBJ も MQO 同様、JSON overrides を mtl 由来のパスより優先する。
        if ((path == null || path.isBlank()) && !textureOverrides.isEmpty()) {
            path = textureOverrides.values.iterator().next()
        }
        if ((path == null || path.isBlank()) && materialName != null) {
            path = materialTextures.get(materialName)
        }
        if ((path == null || path.isBlank()) && !materialTextures.isEmpty()) {
            path = materialTextures.values.iterator().next()
        }
        if (path == null || path.isBlank()) {
            path = "textures/misc/white.png"
        }
        val binding: TextureBinding = TextureBinding.Companion.parse(path)
        val cacheKey = opener.getPackKey() + "|" + binding.cacheKey()
        logModelLoadDetail(
            "texture-resolve-obj",
            "materialName={} resolvedPath={} cacheKey={}",
            materialName,
            path,
            cacheKey
        )
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey) { k: String? -> registerTextureFromZip(binding, opener) }
    }

    private data class ObjMaterialLibrary(
        val textures: MutableMap<String, String> = HashMap(),
        val alphas: MutableMap<String, Float> = HashMap(),
    )

    private fun loadObjMaterialLibrary(materialFile: String?, opener: TextureOpener): ObjMaterialLibrary {
        val library = ObjMaterialLibrary()
        if (materialFile == null || materialFile.isBlank()) {
            return library
        }
        try {
            opener.open(materialFile).use { input ->
                if (input == null) {
                    return library
                }
                var current: String? = null
                val diffuseTextures: MutableMap<String, String> = HashMap()
                val alphaTextures: MutableMap<String, String> = HashMap()
                for (raw in readText(input).split("\\R".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val line = raw.trim { it <= ' ' }
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue
                    }
                    if (line.startsWith("newmtl ")) {
                        current = line.substring(7).trim { it <= ' ' }
                        continue
                    }
                    if (current == null) {
                        continue
                    }
                    if (line.startsWith("map_Kd ")) {
                        diffuseTextures[current] = line.substring(7).trim { it <= ' ' }
                    } else if (line.startsWith("map_d ")) {
                        alphaTextures[current] = line.substring(6).trim { it <= ' ' }
                    } else if (line.startsWith("d ")) {
                        line.substring(2).trim().toFloatOrNull()?.let { library.alphas[current] = Mth.clamp(it, 0.0f, 1.0f) }
                    } else if (line.startsWith("Tr ")) {
                        line.substring(3).trim().toFloatOrNull()?.let {
                            library.alphas[current] = 1.0f - Mth.clamp(it, 0.0f, 1.0f)
                        }
                    }
                }
                for (name in diffuseTextures.keys + alphaTextures.keys) {
                    val path = diffuseTextures[name] ?: alphaTextures[name] ?: continue
                    val alpha = library.alphas[name] ?: 1.0f
                    library.textures[name] = if (alpha < 0.999f || alphaTextures.containsKey(name)) {
                        path + TEXTURE_META_SEPARATOR + "alphablend"
                    } else {
                        path
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return library
    }

    private fun parseVertexLine(line: String): Vec3? {
        val t = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        try {
            if (t.size == 2) {
                val x = t[0].toFloat() * 0.01f
                val y = t[1].toFloat() * 0.01f
                return Vec3(x.toDouble(), y.toDouble(), 0.0)
            }
            if (t.size >= 3) {
                val x = t[0].toFloat() * 0.01f
                val y = t[1].toFloat() * 0.01f
                val z = t[2].toFloat() * 0.01f
                return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
            }
        } catch (ignored: NumberFormatException) {
        }
        return null
    }

    @Throws(Exception::class)
    private fun addFaceLine(
        line: String,
        verts: MutableList<Vec3>,
        materialOrder: MutableList<String?>,
        materialTexPaths: MutableList<String?>?,
        materialAlphas: MutableList<Float?>,
        textureOverrides: Map<String, String>,
        opener: TextureOpener,
        mirrorType: Int,
        groupName: String?,
        facetAngle: Float,
        byGroup: MutableMap<String?, BatchBuilder>
    ) {
        val tokens = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (tokens.size == 0) return
        val vertexCount = tokens[0].toInt()
        if (vertexCount < 3) return
        val matId = parseMaterialId(line).toByte()
        val textureInfo = resolveTexture(matId, materialOrder, materialTexPaths, textureOverrides, opener)
        val matAlpha: Float =
            (if ((matId.toInt() and 0xFF) < materialAlphas.size) materialAlphas.get(matId.toInt() and 0xFF) else 1.0f)!!
        val matKey = matId.toInt() and 0xFF
        val materialName = if (matKey < materialOrder.size) materialOrder[matKey] else null
        val lowerMaterialName = materialName?.lowercase() ?: ""
        val lowerGroupName = groupName?.lowercase() ?: ""
        val namedGlassMaterial = lowerMaterialName.contains("glass") || lowerMaterialName.contains("window")
        val unnamedMaterial = lowerMaterialName.isBlank() || lowerMaterialName == "default"
        val explicitGlass = namedGlassMaterial || unnamedMaterial &&
                (lowerGroupName.contains("glass") || lowerGroupName.contains("window") ||
                    lowerGroupName.contains("wind") || lowerGroupName == "alpha" || lowerGroupName.startsWith("alpha_"))
        val vi = matchGroup(V_PATTERN, line)
        val uv = matchGroup(UV_PATTERN, line)
        if (vi == null) return
        val vidx = vi.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val uvs = parseUv(uv, vertexCount)
        var avgY = 0f
        var faceMinY = Float.MAX_VALUE
        var faceMaxY = -Float.MAX_VALUE
        run {
            val cnt = min(vertexCount, vidx.size)
            for (i in 0..<cnt) {
                try {
                    val vy = verts.get(vidx[i].toInt()).y.toFloat()
                    avgY += vy
                    if (vy < faceMinY) faceMinY = vy
                    if (vy > faceMaxY) faceMaxY = vy
                } catch (ignored: Exception) {
                }
            }
            if (cnt > 0) avgY /= cnt.toFloat()
        }
        if (shouldSkipLegacyShadowPlaneFace(groupName, verts, vidx, vertexCount, faceMinY, faceMaxY)) {
            return
        }
        // マテリアル col の a<1 = ガラス等の半透明。グループ名に依らず半透明描画し、その不透明度を適用する。
        val translucent = matAlpha < 0.99f || explicitGlass
                || shouldTreatFaceAsTranslucent(textureInfo, groupName, uvs, vertexCount, avgY)
        val batchKey = groupName + "|" + matKey + "|" + translucent
        val batchOrder = byGroup.size
        val baseAlpha = matAlpha
        val bb = byGroup.computeIfAbsent(batchKey) { k: String? ->
            val b = BatchBuilder(
                batchOrder,
                groupName,
                textureInfo.location,
                textureInfo.emissiveTextures,
                matKey,
                translucent,
                facetAngle
            )
            b.baseAlpha = baseAlpha
            b.glassTranslucent = explicitGlass || textureInfo.hasGlassBand
            b.explicitGlassOnly = explicitGlass
            b.opaqueTexture = textureInfo.opaqueLocation
            b.windowTexture = if (matAlpha < 0.999f || explicitGlass) textureInfo.location else textureInfo.windowLocation
            b
        }

        if (vertexCount == 4) {
            addQuad(verts, vidx, uvs, matId, bb, mirrorType)
        } else {
            addPolygonFan(verts, vidx, uvs, vertexCount, bb, mirrorType)
        }
    }

    private fun shouldSkipLegacyShadowPlaneFace(
        groupName: String?, verts: MutableList<Vec3>?, vidx: Array<String>?,
        vertexCount: Int, faceMinY: Float, faceMaxY: Float
    ): Boolean {
        if (groupName == null || verts == null || vidx == null) {
            return false
        }
        val lower = groupName.trim { it <= ' ' }.lowercase()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        val cnt = min(vertexCount, vidx.size)
        for (i in 0..<cnt) {
            try {
                val v = verts.get(vidx[i].toInt())
                val x = v.x.toFloat()
                val z = v.z.toFloat()
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
            } catch (ignored: Exception) {
            }
        }
        if (cnt < 3 || minX == Float.MAX_VALUE || minZ == Float.MAX_VALUE) {
            return false
        }
        val dx = maxX - minX
        val dy = faceMaxY - faceMinY
        val dz = maxZ - minZ
        // MQO vertices are stored after the legacy 0.01 scale conversion. 旧RTM用パックの
        // 車体下「影板」は元MQO上で y=-98 / z=±900 付近なので、ここでは -0.98 / ±9.0
        // として判定する。
        val underBody = faceMinY < -0.10f
        val broadHorizontalPlate = faceMinY < -0.05f && dy < 0.035f && dx > 0.45f && dz > 1.20f
        val veryLowFlatPlate = faceMinY < -0.75f && dy < 0.05f && (dz > 0.45f || dx > 0.80f)
        val lowUnderbodyPlate = faceMinY < -0.62f && dy < 0.012f && dx > 0.42f && dz > 0.62f
        val unnamedLegacyShadowPlate = faceMinY < -0.90f && dy < 0.008f && dx > 0.90f && dz > 2.0f
        if (unnamedLegacyShadowPlate) {
            return true
        }
        // Some legacy packs (e.g. 2419) put the fake underbody shadow inside broad body
        // groups such as obj1/obj2 instead of a "shadow" group.  RTM's old renderer did
        // not show these as hard black planes, so strip only large, almost perfectly flat
        // plates below the vehicle body. 立体の床下機器/台車は dy があるため残る。
        if ((veryLowFlatPlate || lowUnderbodyPlate || broadHorizontalPlate) && (lower == "obj1" || lower == "obj2" || lower == "obj3"
                    || lower == "body" || lower.startsWith("body_"))
        ) {
            return true
        }
        if (lower == "alpha" || lower.startsWith("alpha_")) {
            val e131UnderbodyShadowBox = faceMinY < -0.75f && faceMaxY < 0.05f && (dz > 18.0f || dx > 0.75f)
            return veryLowFlatPlate || e131UnderbodyShadowBox
        }
        if (!lower.contains("shadow") && !lower.endsWith("_ms")) {
            return false
        }
        val veryLong = dz > 12.0f || dx > 2.2f
        val slabLike = dy < 1.4f
        return veryLowFlatPlate || (underBody && veryLong && slabLike)
    }

    private fun addQuad(
        verts: MutableList<Vec3>,
        vidx: Array<String>,
        uvs: FloatArray?,
        matId: Byte,
        bb: BatchBuilder,
        mirrorType: Int
    ) {
        val ix = IntArray(4)
        for (i in 0..3) ix[i] = vidx[i].toInt()
        val p = arrayOfNulls<Vec3>(4)
        val u = FloatArray(4)
        val v = FloatArray(4)
        for (i in 0..3) {
            val si = 3 - i
            p[si] = verts.get(ix[i])
            if (uvs != null) {
                u[si] = uvs[i * 2]
                v[si] = uvs[i * 2 + 1]
            }
        }
        MqoModelLoader.emitQuad(
            p[0]!!,
            p[1]!!,
            p[2]!!,
            p[3]!!,
            u[0],
            v[0],
            u[1],
            v[1],
            u[2],
            v[2],
            u[3],
            v[3],
            bb,
            mirrorType
        )
    }

    private fun emitQuad(
        p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3,
        u0: Float, v0: Float, u1: Float, v1: Float,
        u2: Float, v2: Float, u3: Float, v3: Float,
        bb: BatchBuilder, mirrorType: Int
    ) {
        if (!bb.markFace(arrayOf<Vec3>(p0, p1, p2, p3), floatArrayOf(u0, v0, u1, v1, u2, v2, u3, v3))) {
            return
        }
        val e1 = Vector3f((p1.x - p0.x).toFloat(), (p1.y - p0.y).toFloat(), (p1.z - p0.z).toFloat())
        val e2 = Vector3f((p2.x - p0.x).toFloat(), (p2.y - p0.y).toFloat(), (p2.z - p0.z).toFloat())
        val n = e1.cross(e2)
        if (n.lengthSquared() > 1.0e-8f) n.normalize()
        else n.set(0f, 1f, 0f)
        bb.put(p0, n, u0, v0)
        bb.put(p1, n, u1, v1)
        bb.put(p2, n, u2, v2)
        bb.put(p3, n, u3, v3)
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(arrayOf<Vec3>(p0, p1, p2, p3), mirrorType)) {
            val mp0 = mirror(p0, mirrorType)
            val mp3 = mirror(p3, mirrorType)
            val mp2 = mirror(p2, mirrorType)
            val mp1 = mirror(p1, mirrorType)
            if (!bb.markFace(arrayOf<Vec3>(mp0, mp3, mp2, mp1), floatArrayOf(u0, v0, u3, v3, u2, v2, u1, v1))) {
                return
            }
            val mn = mirrorN(n, mirrorType)
            bb.put(mp0, mn, u0, v0)
            bb.put(mp3, mn, u3, v3)
            bb.put(mp2, mn, u2, v2)
            bb.put(mp1, mn, u1, v1)
        }
    }

    private fun addPolygonFan(
        verts: MutableList<Vec3>,
        vidx: Array<String>,
        uvs: FloatArray?,
        vertexCount: Int,
        bb: BatchBuilder,
        mirrorType: Int
    ) {
        val p = arrayOfNulls<Vec3>(vertexCount)
        val localU = FloatArray(vertexCount)
        val localV = FloatArray(vertexCount)
        for (i in 0..<vertexCount) {
            val si = vertexCount - 1 - i
            p[si] = verts.get(vidx[i].toInt())
            if (uvs != null) {
                localU[si] = uvs[i * 2]
                localV[si] = uvs[i * 2 + 1]
            }
        }

        for (i in 1..<vertexCount - 1) {
            MqoModelLoader.emitTri(
                p[0]!!, p[i]!!, p[i + 1]!!,
                if (uvs == null) 0.0f else localU[0], if (uvs == null) 0.0f else localV[0],
                if (uvs == null) 0.0f else localU[i], if (uvs == null) 0.0f else localV[i],
                if (uvs == null) 0.0f else localU[i + 1], if (uvs == null) 0.0f else localV[i + 1],
                bb, mirrorType
            )
        }
    }

    private fun emitTri(
        p0: Vec3,
        p1: Vec3,
        p2: Vec3,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        u2: Float,
        v2: Float,
        bb: BatchBuilder,
        mirrorType: Int
    ) {
        if (!bb.markFace(arrayOf<Vec3>(p0, p1, p2, p2), floatArrayOf(u0, v0, u1, v1, u2, v2, u2, v2))) {
            return
        }
        val e1 = Vector3f((p1.x - p0.x).toFloat(), (p1.y - p0.y).toFloat(), (p1.z - p0.z).toFloat())
        val e2 = Vector3f((p2.x - p0.x).toFloat(), (p2.y - p0.y).toFloat(), (p2.z - p0.z).toFloat())
        val n = e1.cross(e2)
        if (n.lengthSquared() > 1.0e-8f) n.normalize()
        else n.set(0f, 1f, 0f)
        // QUADSモードは4頂点/面が必要 → 3頂点の三角形は縮退クワッドとして扱う (v0,v1,v2,v2)
        bb.put(p0, n, u0, v0)
        bb.put(p1, n, u1, v1)
        bb.put(p2, n, u2, v2)
        bb.put(p2, n, u2, v2)
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(arrayOf<Vec3>(p0, p1, p2), mirrorType)) {
            val mp0 = mirror(p0, mirrorType)
            val mp2 = mirror(p2, mirrorType)
            val mp1 = mirror(p1, mirrorType)
            if (!bb.markFace(arrayOf<Vec3>(mp0, mp2, mp1, mp1), floatArrayOf(u0, v0, u2, v2, u1, v1, u1, v1))) {
                return
            }
            val mn = mirrorN(n, mirrorType)
            bb.put(mp0, mn, u0, v0)
            bb.put(mp2, mn, u2, v2)
            bb.put(mp1, mn, u1, v1)
            bb.put(mp1, mn, u1, v1)
        }
    }

    private fun mirror(p: Vec3, type: Int): Vec3 {
        val x = p.x.toFloat()
        val y = p.y.toFloat()
        val z = p.z.toFloat()
        val m = when (type) {
            0 -> floatArrayOf(-1f, 1f, 1f)
            1 -> floatArrayOf(1f, -1f, 1f)
            else -> floatArrayOf(1f, 1f, -1f)
        }
        return Vec3((x * m[0]).toDouble(), (y * m[1]).toDouble(), (z * m[2]).toDouble())
    }

    private fun mirrorN(n: Vector3f, type: Int): Vector3f {
        val m = when (type) {
            0 -> floatArrayOf(-1f, 1f, 1f)
            1 -> floatArrayOf(1f, -1f, 1f)
            else -> floatArrayOf(1f, 1f, -1f)
        }
        val o = Vector3f(n.x * m[0], n.y * m[1], n.z * m[2])
        if (o.lengthSquared() > 1.0e-8f) o.normalize()
        return o
    }

    private fun isFaceOnMirrorPlane(points: Array<Vec3>, mirrorType: Int): Boolean {
        if (mirrorType < 0 || mirrorType > 2) return false
        val epsilon = 1.0e-5
        for (p in points) {
            val value = if (mirrorType == 0) p.x else if (mirrorType == 1) p.y else p.z
            if (abs(value) > epsilon) {
                return false
            }
        }
        return true
    }

    private fun parseUv(uv: String?, vertexCount: Int): FloatArray? {
        if (uv == null || uv.isBlank()) return null
        val parts = uv.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size < vertexCount * 2) return null
        val out = FloatArray(vertexCount * 2)
        for (i in 0..<vertexCount * 2) {
            out[i] = parts[i].toFloat()
        }
        return out
    }

    private fun parseMaterialId(line: String): Int {
        val m = matchGroup(M_PATTERN, line)
        if (m == null || m.isBlank()) return 0
        try {
            return m.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            return 0
        }
    }

    private fun matchGroup(pat: Pattern, line: String): String? {
        val mm = pat.matcher(line)
        return if (mm.find()) mm.group(1) else null
    }

    @Throws(Exception::class)
    private fun resolveTexture(
        matId: Byte,
        materialOrder: MutableList<String?>,
        materialTexPaths: MutableList<String?>?,
        overrides: Map<String, String>,
        opener: TextureOpener
    ): TextureInfo {
        val idx = matId.toInt() and 0xFF
        var matName = if (idx < materialOrder.size) materialOrder.get(idx) else null
        if (matName == null && !materialOrder.isEmpty()) matName = materialOrder.get(0)
        var path: String? = null
        // 1. material name lookup (e.g. "KQBody" -> "KQBody.png")
        if (matName != null) path = overrides.get(matName)
        // 2. numeric index lookup (e.g. "0" -> "texture.png")
        if (path == null) path = overrides.get(idx.toString())
        // 3. "default" override — checked before embedded MQO tex so JSON-specified
        //    textures (e.g. signalTexture in installed-object JSON) take priority over
        //    the tex("...") line baked into the MQO (which may reference a file not
        //    bundled with this mod, causing a white-texture fallback).
        if (path == null) path = overrides.get("default")
        // 4. first override as a JSON fallback — MQO は信用ならない（C:\... 絶対パスや
        //    存在しないファイル名が tex("...") に焼き込まれている事が多い）。
        //    JSON/JS にどれか overrides が書いてあればそれを優先する。
        if (path == null && !overrides.isEmpty()) path = overrides.values.iterator().next()
        // 5. tex("...") embedded in MQO material line — skip Windows absolute paths (C:\...) that can't be resolved
        if (path == null && materialTexPaths != null && idx < materialTexPaths.size) {
            val embedded = materialTexPaths.get(idx)
            if (embedded != null && !isWindowsAbsolutePath(embedded)) {
                path = embedded
            }
        }
        if (path == null) path = "textures/misc/white.png"
        val resolvedPath: String? = path
        val resolvedMatName = matName
        val binding: TextureBinding = TextureBinding.Companion.parse(resolvedPath)
        val cacheKey = opener.getPackKey() + "|" + binding.cacheKey()
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey) { k: String? ->
            logModelLoadDetail(
                "texture-resolve-mqo",
                "matId={} matName={} resolvedPath={}",
                matId,
                resolvedMatName,
                resolvedPath
            )
            registerTextureFromZip(binding, opener)
        }
    }

    private fun loadScriptForModel(model: MqoModel?, packPath: Path?, scriptPath: String?, modelName: String? = null) {
        if (model == null || packPath == null) {
            RealTrainModRenewed.LOGGER.warn("loadScriptForModel: model or packPath is null")
            return
        }
        val normalized = normalizeScriptPath(scriptPath)
        val leaf = if (normalized.contains("/")) normalized.substring(normalized.lastIndexOf('/') + 1) else normalized
        val hasExplicitPath = !normalized.isBlank()

        RealTrainModRenewed.LOGGER.info(
            "loadScriptForModel: scriptPath='{}', normalized='{}', leaf='{}', hasExplicitPath={}",
            scriptPath,
            normalized,
            leaf,
            hasExplicitPath
        )

        try {
            if (hasExplicitPath) {
                var legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized)
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf)
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    RealTrainModRenewed.LOGGER.info(
                        "Loaded legacy script from resource manager: {}, length={}",
                        normalized,
                        legacyScript.length
                    )
                    TrainScriptSystem.loadScript(normalized, legacyScript, model, modelName)
                    return
                }
            }
        } catch (ignored: Exception) {
            if (hasExplicitPath) {
                val warnKey = packPath.toString() + "|" + normalized
                if (MISSING_SCRIPT_WARNINGS.add(warnKey)) {
                    RealTrainModRenewed.LOGGER.debug(
                        "Legacy script lookup failed for {}; falling back to pack search",
                        normalized
                    )
                }
            }
            // legacy resource manager may not be initialized or the script may not be available
        }

        if (!hasExplicitPath) {
            return
        }

        RealTrainModRenewed.LOGGER.info(
            "Attempting to load legacy model script '{}' from pack {}",
            normalized,
            packPath
        )
        try {
            if (Files.isDirectory(packPath)) {
                var scriptFile: Path? = null
                if (hasExplicitPath) {
                    scriptFile = resolveFilePathInPack(packPath, normalized)
                    if (scriptFile == null) {
                        scriptFile = resolveFilePathInPack(packPath, leaf)
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    RealTrainModRenewed.LOGGER.info("Found model script at {}", scriptFile)
                    var script = readText(scriptFile)
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath))
                    RealTrainModRenewed.LOGGER.info("Script file loaded, length={}", script.length)
                    TrainScriptSystem.loadScript(normalized, script, model, modelName)
                } else {
                    val external = findResource(normalized, packPath)
                    if (external != null && packPath != external.packPath) {
                        loadScriptFromResource(model, external, normalized, modelName)
                    } else {
                        RealTrainModRenewed.LOGGER.warn(
                            "Model script not found in pack directory: {} (normalized={})",
                            packPath,
                            normalized
                        )
                    }
                }
            } else {
                openZipFile(packPath).use { zf ->
                    var entry: ZipEntry? = null
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized)
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf)
                        }
                    }
                    if (entry != null) {
                        RealTrainModRenewed.LOGGER.info("Found model script in pack zip: {}", entry.getName())
                        zf.getInputStream(entry).use { `in` ->
                            var script = readText(`in`)
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script)
                            TrainScriptSystem.loadScript(normalized, script, model, modelName)
                        }
                    } else {
                        val external = findResource(normalized, packPath)
                        if (external != null && packPath != external.packPath) {
                            loadScriptFromResource(model, external, normalized, modelName)
                        } else {
                            RealTrainModRenewed.LOGGER.warn(
                                "Model script not found in pack zip: {} (normalized={})",
                                packPath,
                                normalized
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to load script {} from pack {}", scriptPath, packPath, e)
        }
    }

    private fun loadStandaloneScript(packPath: Path?, scriptPath: String?, modelName: String?): ScriptEngine? {
        if (packPath == null) {
            return null
        }
        val source = loadStandaloneScriptSource(packPath, scriptPath)
        if (source == null || source.isBlank()) {
            return null
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, source, modelName)
    }

    private fun loadStandaloneScriptSource(packPath: Path?, scriptPath: String?): String? {
        if (packPath == null) {
            return null
        }
        val normalized = normalizeScriptPath(scriptPath)
        val leaf = if (normalized.contains("/")) normalized.substring(normalized.lastIndexOf('/') + 1) else normalized
        val hasExplicitPath = !normalized.isBlank()

        try {
            if (hasExplicitPath) {
                var legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized)
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf)
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    return legacyScript
                }
            }
        } catch (ignored: Exception) {
        }

        try {
            if (Files.isDirectory(packPath)) {
                var scriptFile: Path? = null
                if (hasExplicitPath) {
                    scriptFile = resolveFilePathInPack(packPath, normalized)
                    if (scriptFile == null) {
                        scriptFile = resolveFilePathInPack(packPath, leaf)
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    var script = readText(scriptFile)
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath))
                    return script
                }
                if (hasExplicitPath) {
                    val external = findResource(normalized, packPath)
                    if (external != null) {
                        return readText(external)
                    }
                }
            } else {
                openZipFile(packPath).use { zf ->
                    var entry: ZipEntry? = null
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized)
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf)
                        }
                    }
                    if (entry != null) {
                        zf.getInputStream(entry).use { `in` ->
                            var script = readText(`in`)
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script)
                            return script
                        }
                    }
                    if (hasExplicitPath) {
                        val external = findResource(normalized, packPath)
                        if (external != null) {
                            return readText(external)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to load standalone script {} from {}", scriptPath, packPath, e)
        }
        return null
    }

    @Throws(IOException::class)
    private fun loadScriptFromResource(
        model: MqoModel?,
        resource: ResourceSearchResult,
        scriptPath: String?,
        modelName: String?
    ) {
        if (resource.filePath != null) {
            val scriptFile = resource.filePath
            var script = readText(scriptFile)
            script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(resource.packPath))
            TrainScriptSystem.loadScript(scriptPath, script, model, modelName)
            return
        }
        PackZipReader.openZipFile(resource.packPath!!).use { zip ->
            val entry = zip.getEntry(resource.zipEntryName)
            if (entry == null) {
                return
            }
            zip.getInputStream(entry).use { `in` ->
                var script = readText(`in`)
                script = preprocessScriptIncludesForZip(zip, entry.getName(), script)
                TrainScriptSystem.loadScript(scriptPath, script, model, modelName)
            }
        }
    }

    private fun rootDirectory(packPath: Path?): Path? {
        if (packPath == null) {
            return null
        }
        return if (Files.isDirectory(packPath)) packPath else packPath.getParent()
    }

    private fun preprocessScriptIncludesForDirectory(scriptFile: Path, root: Path?): String {
        try {
            return preprocessScriptIncludes(
                readText(scriptFile),
                normalize(scriptFile.toString()),
                IncludeResolver { includePath: String? -> resolveIncludeFromDirectory(scriptFile, root, includePath) }
            )
        } catch (e: Exception) {
            return safeRead(scriptFile)
        }
    }

    private fun safeRead(path: Path): String {
        try {
            return readText(path)
        } catch (e: Exception) {
            return ""
        }
    }

    private fun preprocessScriptIncludesForZip(zipFile: ZipFile, entryName: String?, content: String?): String {
        return preprocessScriptIncludes(
            content,
            normalize(entryName),
            IncludeResolver { includePath: String? -> resolveIncludeFromZip(zipFile, entryName, includePath) })
    }

    private fun preprocessScriptIncludes(
        content: String?,
        scriptIdentifier: String?,
        resolver: IncludeResolver,
        visiting: MutableSet<String?> = HashSet<String?>()
    ): String {
        if (content == null || content.isBlank()) {
            return content!!
        }
        if (!visiting.add(scriptIdentifier)) {
            RealTrainModRenewed.LOGGER.warn("Detected cyclic script include for {}", scriptIdentifier)
            return content
        }

        var processed: String? = content
        var matcher = Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$").matcher(processed)
        while (matcher.find()) {
            val includeTarget = matcher.group(1).trim { it <= ' ' }
            var replacement = ""
            try {
                val includeSource = resolver.resolve(includeTarget)
                if (includeSource != null && includeSource.content != null) {
                    replacement =
                        preprocessScriptIncludes(includeSource.content, includeSource.identifier, resolver, visiting)
                }
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.warn(
                    "Failed to resolve include '{}' in {}",
                    includeTarget,
                    scriptIdentifier,
                    e
                )
            }
            processed = matcher.replaceFirst(Matcher.quoteReplacement(replacement))
            matcher = Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$").matcher(processed)
        }

        visiting.remove(scriptIdentifier)
        return processed
    }

    @Throws(IOException::class)
    private fun resolveIncludeFromDirectory(scriptFile: Path, root: Path?, includePath: String?): IncludeSource? {
        val normalizedInclude = normalize(includePath)
        val parent = scriptFile.getParent()

        if (parent != null) {
            val relative = parent.resolve(normalizedInclude).normalize()
            if (Files.exists(relative) && Files.isRegularFile(relative)) {
                return IncludeSource(normalize(relative.toString()), readText(relative))
            }
        }

        if (root != null) {
            val rootResolved = root.resolve(normalizedInclude).normalize()
            if (Files.exists(rootResolved) && Files.isRegularFile(rootResolved)) {
                return IncludeSource(normalize(rootResolved.toString()), readText(rootResolved))
            }
            val found = resolveFilePathInPack(root, normalizedInclude)
            if (found != null) {
                return IncludeSource(normalize(found.toString()), readText(found))
            }
            // assets/<namespace>/ ルートからの解決(RTM の //include は assets 名前空間相対)。
            try {
                val rel = normalize(root.relativize(scriptFile).toString())
                val assetsRoot = assetsNamespaceRoot(rel)
                if (!assetsRoot.isEmpty()) {
                    val p = root.resolve(assetsRoot + normalizedInclude).normalize()
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return IncludeSource(normalize(p.toString()), readText(p))
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        return null
    }

    @Throws(IOException::class)
    private fun resolveIncludeFromZip(
        zipFile: ZipFile,
        currentEntryName: String?,
        includePath: String?
    ): IncludeSource? {
        val normalizedInclude = normalize(includePath)
        val current = normalize(currentEntryName)
        var parent = ""
        val slash = current.lastIndexOf('/')
        if (slash >= 0) {
            parent = current.substring(0, slash + 1)
        }

        var relative = findEntry(zipFile, parent + normalizedInclude)
        if (relative == null) {
            relative = findEntry(zipFile, normalizedInclude)
        }
        if (relative == null) {
            // RTM の //include <scripts/...> は "assets/<namespace>/" からの相対パス。
            // スクリプト親ディレクトリ相対でも生パスでも見つからない場合、assets ルートから解決する
            // (例: assets/minecraft/scripts/hi03_e259/render.js から <scripts/hi03_lib/x.js> →
            //  assets/minecraft/scripts/hi03_lib/x.js)。これが無いと CustomMonitor 等の include が
            //  解決できず init が例外→台車/座席/LCD など丸ごと描画されなくなる。
            val assetsRoot = assetsNamespaceRoot(current)
            if (!assetsRoot.isEmpty()) {
                relative = findEntry(zipFile, assetsRoot + normalizedInclude)
            }
        }
        if (relative == null) {
            return null
        }

        zipFile.getInputStream(relative).use { `in` ->
            return IncludeSource(normalize(relative.getName()), readText(`in`))
        }
    }

    private fun normalize(path: String?): String {
        return if (path == null) "" else path.replace('\\', '/')
    }

    /** "assets/minecraft/scripts/..." → "assets/minecraft/"。assets 配下でなければ ""。  */
    private fun assetsNamespaceRoot(entryName: String?): String {
        val n = normalize(entryName)
        if (!n.startsWith("assets/")) return ""
        val second = n.indexOf('/', "assets/".length)
        return if (second >= 0) n.substring(0, second + 1) else ""
    }

    @Throws(IOException::class)
    private fun findFallbackScriptFile(root: Path?): Path? {
        if (root == null || !Files.exists(root)) return null
        var found: Path? = null
        Files.walk(root).use { stream ->
            for (file in Iterable { stream.iterator() }) {
                if (!Files.isRegularFile(file)) continue
                val relative = root.relativize(file).toString().replace('\\', '/')
                if (!relative.lowercase().contains("/scripts/")) continue
                if (!relative.lowercase().endsWith(".js")) continue
                if (found != null) {
                    return null
                }
                found = file
            }
        }
        return found
    }

    private fun findFallbackScriptEntry(zf: ZipFile?): ZipEntry? {
        if (zf == null) return null
        var fallback: ZipEntry? = null
        val entries = zf.entries()
        while (entries.hasMoreElements()) {
            val entry: ZipEntry = entries.nextElement()
            if (entry.isDirectory()) continue
            val name = entry.getName().replace('\\', '/')
            if (!(name.lowercase().contains("/scripts/") && name.lowercase().endsWith(".js"))) continue
            if (fallback != null) {
                return null
            }
            fallback = entry
        }
        return fallback
    }

    /** 画像に中間アルファ(0/255 以外)のピクセルがあれば true (本当の半透明)。cutout の二値アルファは false。  */
    private fun hasPartialAlpha(img: NativeImage): Boolean {
        try {
            if (img.format() != NativeImage.Format.RGBA) {
                return false
            }
            val w = img.getWidth()
            val h = img.getHeight()
            val stepX = max(1, w / 128)
            val stepY = max(1, h / 128)
            var sampled = 0
            var partial = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val a = (img.getPixel(x, y) ushr 24) and 0xFF
                    sampled++
                    if (a >= 8 && a <= 247) partial++
                    x += stepX
                }
                y += stepY
            }
            // 部分アルファの割合が高い=ガラス等の半透明テクスチャ。AA縁だけの車体テクスチャ(数%未満)は
            // 不透明のまま扱い、車体が透けないようにする。3%以上で半透明と判定。
            return sampled > 0 && (partial * 100) >= (sampled * 3)
        } catch (ignored: Throwable) {
        }
        return false
    }

    /**
     * 明確な「ガラス帯」を持つか。alpha 32..224 の中間アルファ(本当に透けるガラス/煙等)が
     * 一定割合(1.5%)以上あれば true。AA 縁の薄い勾配だけ(SL車体の二値カットアウト)は
     * この範囲のピクセルがごく僅かなので false。これでガラス窓(透ける)とカットアウト車体
     * (透けない)を区別し、ガラス窓だけ強制カットアウトを免除してブレンド描画する。
     */
    private fun hasGlassBand(img: NativeImage): Boolean {
        try {
            if (img.format() != NativeImage.Format.RGBA) {
                return false
            }
            val w = img.getWidth()
            val h = img.getHeight()
            val stepX = max(1, w / 128)
            val stepY = max(1, h / 128)
            var sampled = 0
            var band = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val a = (img.getPixel(x, y) ushr 24) and 0xFF
                    sampled++
                    if (a >= 32 && a <= 224) band++
                    x += stepX
                }
                y += stepY
            }
            return sampled > 0 && (band * 1000L) >= (sampled * 15L)
        } catch (ignored: Throwable) {
        }
        return false
    }

    /** 床下蓋用の2x2白テクスチャ(setShaderColor でグレーに着色して使う)。遅延生成・キャッシュ。  */
    @Volatile
    private var whiteTextureLoc: Identifier? = null

    private fun newDynamicTexture(label: String?, image: NativeImage): DynamicTexture {
        return DynamicTexture(Supplier { label!! }, image)
    }

    private val capWhiteTexture: Identifier
        get() {
            var loc =
                whiteTextureLoc
            if (loc != null) return loc
            val img = NativeImage(2, 2, false)
            for (y in 0..1) {
                for (x in 0..1) {
                    img.setPixel(x, y, -0x1)
                }
            }
            val tex =
                newDynamicTexture("mqo", img)
            loc = Identifier.fromNamespaceAndPath(
                RealTrainModRenewed.MODID,
                "dynamic/white"
            )
            Minecraft.getInstance().getTextureManager().register(loc, tex)
            whiteTextureLoc = loc
            return loc
        }

    /**
     * RTM系の pass0 用。窓ガラスのような中間アルファは pass1 に回しつつ、
     * アンチエイリアス縁のような「ほぼ不透明」は pass0 に残して文字やロゴの
     * 痩せを防ぐ。
     */
    private fun copyOpaqueOnlyAlpha(img: NativeImage): NativeImage {
        val w = img.getWidth()
        val h = img.getHeight()
        val dst = NativeImage(w, h, false)
        for (y in 0..<h) {
            for (x in 0..<w) {
                val p = img.getPixel(x, y) // 0xAABBGGRR (リトルエンディアン)
                val a = (p ushr 24) and 0xFF
                val na = if (a >= 0xF0) 0xFF else 0x00
                dst.setPixel(x, y, (p and 0x00FFFFFF) or (na shl 24))
            }
        }
        return dst
    }

    /**
     * RTM系の pass1 用。ガラス帯など本当に半透明なピクセルだけを残し、
     * ほぼ不透明な縁は pass0 側へ寄せる。
     */
    private fun copyNonOpaqueAlpha(img: NativeImage): NativeImage {
        val w = img.getWidth()
        val h = img.getHeight()
        val dst = NativeImage(w, h, false)
        for (y in 0..<h) {
            for (x in 0..<w) {
                val p = img.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                val na = if (a > 0x00 && a < 0xE0) a else 0x00
                dst.setPixel(x, y, (p and 0x00FFFFFF) or (na shl 24))
            }
        }
        return dst
    }

    /** ガラス専用上限アルファ(0x73≈0.45)。これ以上の中間アルファ窓もここまで下げて確実に透かす。  */
    private const val GLASS_MAX_ALPHA = 0x73

    private fun copyStainedGlassAlpha(img: NativeImage): NativeImage {
        val w = img.getWidth()
        val h = img.getHeight()
        val dst = NativeImage(w, h, false)
        for (y in 0..<h) {
            for (x in 0..<w) {
                val p = img.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                val na = if (a > 0x00 && a < 0xF0) min(a, GLASS_MAX_ALPHA) else 0x00
                dst.setPixel(x, y, (p and 0x00FFFFFF) or (na shl 24))
            }
        }
        return dst
    }

    /**
     * バニラ・ガラス安定方式用テクスチャ。
     * - alpha >= 0xF0(≈240) … 車体本体 → 255(完全不透明・深度を持つ=スケスケしない)
     * - 0x1A <= a < 0xF0    … 窓ガラス → min(a, GLASS_MAX_ALPHA)(色付きでも確実に透ける)
     * - alpha <  0x1A       … 抜き穴   → 0(透過。シェーダの discard 境界に合わせる)
     * RGB はそのままなので色味は保持。これを blend で1パス描画すると色付きガラスも半透明になる。
     */
    private fun copyGlassAlpha(img: NativeImage): NativeImage {
        val w = img.getWidth()
        val h = img.getHeight()
        val dst = NativeImage(w, h, false)
        for (y in 0..<h) {
            for (x in 0..<w) {
                val p = img.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                val na = if (a >= 0xF0) 0xFF else (if (a >= 0x1A) min(a, GLASS_MAX_ALPHA) else 0x00)
                dst.setPixel(x, y, (p and 0x00FFFFFF) or (na shl 24))
            }
        }
        return dst
    }

    private fun registerTextureFromZip(binding: TextureBinding, opener: TextureOpener): TextureInfo {
        val alphaBlendOption = binding.options.contains("alphablend")
                || binding.options.contains("translucent")
                || binding.options.contains("glassalpha")
        try {
            opener.open(binding.path).use { `in` ->
                if (`in` != null) {
                    val data = `in`.readAllBytes()
                    val img = NativeImage.read(ByteArrayInputStream(data))
                    // テクスチャに「中間アルファ(0/255以外)」があれば本当の半透明 (ガラス等)。
                    // cutout 用の二値アルファ(車体の穴)と区別し、本当の半透明だけ translucent 扱いにする。
                    val partialAlpha = hasPartialAlpha(img)
                    val glassBand = hasGlassBand(img)
                    val key = abs(binding.cacheKey().hashCode())
                    val tex = newDynamicTexture("mqo", img)
                    val loc = Identifier.fromNamespaceAndPath(
                        RealTrainModRenewed.MODID,
                        "dynamic/mqo/" + Integer.toHexString(key)
                    )
                    Minecraft.getInstance().getTextureManager().register(loc, tex)
                    val baseLoc = loc
                    var opaqueLoc = loc
                    var windowLoc = loc
                    if (alphaBlendOption || partialAlpha || glassBand) {
                        val opaqueImg = copyOpaqueOnlyAlpha(img)
                        val opaqueTex = newDynamicTexture("mqo opaque", opaqueImg)
                        opaqueLoc = Identifier.fromNamespaceAndPath(
                            RealTrainModRenewed.MODID,
                            "dynamic/mqo/" + Integer.toHexString(key) + "_opq"
                        )
                        Minecraft.getInstance().getTextureManager().register(opaqueLoc, opaqueTex)
                        val windowImg = if (glassBand) copyStainedGlassAlpha(img) else copyNonOpaqueAlpha(img)
                        val windowTex = newDynamicTexture("mqo translucent", windowImg)
                        windowLoc = Identifier.fromNamespaceAndPath(
                            RealTrainModRenewed.MODID,
                            "dynamic/mqo/" + Integer.toHexString(key) + "_trans"
                        )
                        Minecraft.getInstance().getTextureManager().register(windowLoc, windowTex)
                    }
                    // 発光(Light)テクスチャの emissive 解決はサブライトテクスチャ(_light0 等)があるときのみ。
                    // ※以前「サブが無ければ元テクスチャを emissive にする」フォールバックを入れたが、Spacia/E259 等の
                    //   AlphaBlend,Light 車体や Light グループが発光パスで二重描画され、チカチカ/急行灯増殖/車体白化を
                    //   起こしたため撤去。踏切ライトの発光は別の安全な手段で対応する。
                    var lightTextures = resolveLegacyLightTextures(binding, opener)
                    if (lightTextures.isEmpty() && binding.options.contains("light") &&
                        !alphaBlendOption && isDedicatedLightTexture(binding.path)
                    ) {
                        lightTextures = arrayOf(baseLoc)
                    }
                    return TextureInfo(
                        baseLoc,
                        lightTextures,
                        alphaBlendOption || partialAlpha || glassBand,
                        partialAlpha,
                        glassBand,
                        opaqueLoc,
                        windowLoc
                    )
                }
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.debug("Could not load texture {}: {}", binding.path, e.message)
        }
        val fallback = fallbackTexture()
        return TextureInfo(fallback, arrayOfNulls<Identifier>(0), false)
    }

    private fun resolveLegacyLightTextures(binding: TextureBinding?, opener: TextureOpener): Array<Identifier?> {
        if (binding == null || !binding.hasLightTextures()) {
            return arrayOfNulls<Identifier>(0)
        }
        val explicitPaths = binding.lightTexturePaths
        val count = max(3, explicitPaths.size)
        val found: MutableList<Identifier?> = MutableList(count) { null }
        for (i in 0..<count) {
            val candidate =
                if (i < explicitPaths.size) explicitPaths.get(i) else deriveLegacyLightTexturePath(binding.path, i)
            if (candidate == null || candidate.isBlank()) continue
            val loaded = tryLoadOptionalTexture(candidate, opener, binding.cacheKey() + "#light" + i)
            if (loaded != null) {
                found[i] = loaded
            }
        }
        return found.toTypedArray<Identifier?>()
    }

    private fun isDedicatedLightTexture(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val leaf = path.replace('\\', '/').substringAfterLast('/').lowercase(Locale.ROOT)
        return leaf.contains("light") || leaf.contains("lamp") ||
            leaf.contains("glow") || leaf.contains("emissive")
    }

    private fun deriveLegacyLightTexturePath(basePath: String?, index: Int): String {
        if (basePath == null || basePath.isBlank()) {
            return ""
        }
        val dot = basePath.lastIndexOf('.')
        if (dot < 0) {
            return basePath + "_light" + index
        }
        return basePath.substring(0, dot) + "_light" + index + basePath.substring(dot)
    }

    private fun loadOptionalTexture(path: String?, opener: TextureOpener, cacheKeySuffix: String): Identifier {
        try {
            opener.open(path).use { `in` ->
                if (`in` == null) {
                    return fallbackTexture()
                }
                val data = `in`.readAllBytes()
                val img = NativeImage.read(ByteArrayInputStream(data))
                val tex = newDynamicTexture("mqo", img)
                val loc = Identifier.fromNamespaceAndPath(
                    RealTrainModRenewed.MODID,
                    "dynamic/mqo/" + Integer.toHexString(cacheKeySuffix.hashCode())
                )
                Minecraft.getInstance().getTextureManager().register(loc, tex)
                return loc
            }
        } catch (ignored: Exception) {
            return fallbackTexture()
        }
    }

    private fun tryLoadOptionalTexture(path: String?, opener: TextureOpener, cacheKeySuffix: String): Identifier? {
        try {
            opener.open(path).use { `in` ->
                if (`in` == null) {
                    return null
                }
                val data = `in`.readAllBytes()
                val img = NativeImage.read(ByteArrayInputStream(data))
                val tex = newDynamicTexture("mqo", img)
                val loc = Identifier.fromNamespaceAndPath(
                    RealTrainModRenewed.MODID,
                    "dynamic/mqo/" + Integer.toHexString(cacheKeySuffix.hashCode())
                )
                Minecraft.getInstance().getTextureManager().register(loc, tex)
                return loc
            }
        } catch (ignored: Exception) {
            return null
        }
    }

    private fun shouldTreatFaceAsTranslucent(
        textureInfo: TextureInfo?,
        groupName: String?,
        uvs: FloatArray?,
        vertexCount: Int,
        avgY: Float
    ): Boolean {
        if (textureInfo == null) {
            return false
        }
        if (textureInfo.isTranslucent || textureInfo.hasPartialAlpha || textureInfo.hasGlassBand) {
            return true
        }
        // RTM packs often mark full-body SL/rod textures as AlphaBlend for cutout holes.
        // Those must stay in the opaque pass or the scripted body disappears.
        return false
    }

    private fun isLegacyTransparentGroupName(lowerGroupName: String?): Boolean {
        if (lowerGroupName == null || lowerGroupName.isBlank()) {
            return false
        }
        return lowerGroupName == "alpha"
                || lowerGroupName == "a"
                || lowerGroupName.startsWith("alpha_")
                || lowerGroupName.contains("glass")
                || lowerGroupName.contains("window")
                || lowerGroupName.contains("wind")
                || lowerGroupName.contains("trans")
                || lowerGroupName.contains("light")
                || lowerGroupName.contains("lamp")
                || lowerGroupName.contains("marker")
    }

    private fun isWindowsAbsolutePath(path: String?): Boolean {
        if (path == null || path.length < 2) return false
        val c = path.get(0)
        return ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) && path.get(1) == ':'
    }

    private fun shouldCullModelFaces(entity: Any?): Boolean {
        if (entity is TrainEntity) {
            val def = getById(entity.vehicleId)
            return def != null && def.isDoCulling()
        }
        return true
    }


    private fun fallbackTexture(): Identifier {
        if (fallbackWhite != null) return fallbackWhite!!
        try {
            val img = NativeImage(4, 4, false)
            for (y in 0..3) {
                for (x in 0..3) {
                    img.setPixel(x, y, -0x1)
                }
            }
            val tex = newDynamicTexture("mqo", img)
            fallbackWhite = Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "dynamic/mqo/_white")
            Minecraft.getInstance().getTextureManager().register(fallbackWhite!!, tex)
        } catch (e: Exception) {
            fallbackWhite = TextureManager.INTENTIONAL_MISSING_TEXTURE
        }
        return fallbackWhite!!
    }

    @JvmStatic
    fun getScriptTexture(domain: String?, path: String?, frameIndex: Int): Identifier? {
        if (path == null || path.isBlank()) {
            return fallbackTexture()
        }
        val data = getScriptTextureData(domain, path)
        if (data.frames.isEmpty()) {
            return fallbackTexture()
        }
        val index = Math.floorMod(frameIndex, data.frames.size)
        return data.frames.get(index)
    }

    @JvmStatic
    fun getScriptTextureData(domain: String?, path: String?): ScriptTextureData {
        if (path == null || path.isBlank()) {
            return ScriptTextureData.Companion.fallback(fallbackTexture())
        }
        val namespace = if (domain == null || domain.isBlank()) "minecraft" else domain
        val normalizedPath = path.replace('\\', '/')
        val cacheKey = namespace + ":" + normalizedPath
        return SCRIPT_TEXTURE_CACHE.computeIfAbsent(cacheKey) { key: String? ->
            loadScriptTextureData(
                namespace,
                normalizedPath
            )
        }
    }

    @JvmStatic
    fun getScriptTextureByTick(domain: String?, path: String?, tick: Double, fps: Double): Identifier? {
        val data = getScriptTextureData(domain, path)
        if (data.frames.isEmpty()) {
            return fallbackTexture()
        }
        val index = data.resolveFrameIndex(tick, fps)
        return data.frames.get(index)
    }

    val whiteTexture: Identifier
        get() = fallbackTexture()

    private fun loadScriptTextureData(domain: String?, path: String): ScriptTextureData {
        try {
            val lower = path.lowercase()
            if (lower.endsWith(".gif")) {
                openScriptTextureStream(domain, path).use { `in` ->
                    if (`in` == null) {
                        return ScriptTextureData.Companion.fallback(fallbackTexture())
                    }
                    return registerGifFrames(domain, path, `in`)
                }
            }
            if (lower.endsWith(".mp4")) {
                val bytes = openScriptTextureBytes(domain, path)
                if (bytes == null || bytes.size == 0) {
                    return ScriptTextureData.Companion.fallback(fallbackTexture())
                }
                return registerMp4Frames(domain, path, bytes)
            }
            val sequence = tryRegisterSequenceFrames(domain, path)
            if (sequence != null) {
                return sequence
            }
            openScriptTextureStream(domain, path).use { `in` ->
                if (`in` == null) {
                    return ScriptTextureData.Companion.fallback(fallbackTexture())
                }
                val image = ImageIO.read(`in`)
                val frame = registerBufferedImage(domain, path, 0, image)
                return ScriptTextureData(
                    listOf(frame),
                    listOf(50),
                    if (image != null) image.getWidth() else 1,
                    if (image != null) image.getHeight() else 1,
                    20.0
                )
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not load script texture {}:{}: {}", domain, path, e.message)
            return ScriptTextureData.Companion.fallback(fallbackTexture())
        }
    }

    @Throws(IOException::class)
    private fun openScriptTextureStream(domain: String?, path: String): InputStream? {
        val normalizedPath = path.replace('\\', '/').replaceFirst("^/+".toRegex(), "")
        val resolvedDomain = if (domain == null || domain.isBlank()) "minecraft" else domain
        val identifier = if (isVanillaResourcePathSafe(resolvedDomain, normalizedPath))
            Identifier.tryBuild(resolvedDomain, normalizedPath)
        else
            null
        if (identifier != null) {
            val resource: Optional<Resource> = Minecraft.getInstance().getResourceManager().getResource(identifier)
            if (resource.isPresent()) {
                return resource.get().open()
            }
        }
        val entryName = "assets/" + resolvedDomain + "/" + normalizedPath
        val modsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods")
        if (!Files.isDirectory(modsDir)) {
            return null
        }
        Files.list(modsDir).use { files ->
            for (file in files.toList()) {
                val name = file.getFileName().toString().lowercase()
                if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
                    continue
                }
                val zip = openZipFile(file)
                val entry = findEntry(zip, entryName)
                if (entry == null) {
                    zip.close()
                    continue
                }
                val raw = zip.getInputStream(entry)
                return object : FilterInputStream(raw) {
                    @Throws(IOException::class)
                    override fun close() {
                        super.close()
                        zip.close()
                    }
                }
            }
        }
        return null
    }

    private fun isVanillaResourcePathSafe(namespace: String?, path: String?): Boolean {
        if (namespace == null || path == null || namespace.isBlank() || path.isBlank()) {
            return false
        }
        for (i in 0..<namespace.length) {
            val c = namespace.get(i)
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false
            }
        }
        for (i in 0..<path.length) {
            val c = path.get(i)
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun openScriptTextureBytes(domain: String?, path: String): ByteArray? {
        openScriptTextureStream(domain, path).use { `in` ->
            return if (`in` == null) null else `in`.readAllBytes()
        }
    }

    @Throws(IOException::class)
    private fun tryRegisterSequenceFrames(domain: String?, path: String): ScriptTextureData? {
        if (!path.contains("%")) {
            return null
        }
        val frames: MutableList<Identifier?> = ArrayList<Identifier?>()
        var width = 1
        var height = 1
        for (i in 0..511) {
            val resolved = String.format(Locale.ROOT, path, i)
            val bytes = openScriptTextureBytes(domain, resolved)
            if (bytes == null || bytes.size == 0) {
                break
            }
            val image = ImageIO.read(ByteArrayInputStream(bytes))
            if (image == null) {
                break
            }
            width = image.getWidth()
            height = image.getHeight()
            frames.add(registerBufferedImage(domain, resolved, i, image))
        }
        if (frames.isEmpty()) {
            return null
        }
        val delays: MutableList<Int?> = ArrayList<Int?>(frames.size)
        for (i in frames.indices) {
            delays.add(50)
        }
        return ScriptTextureData(frames.filterNotNull(), delays.filterNotNull(), width, height, 20.0)
    }

    @Throws(IOException::class)
    private fun registerGifFrames(domain: String?, path: String?, `in`: InputStream): ScriptTextureData {
        val readers = ImageIO.getImageReadersByFormatName("gif")
        if (!readers.hasNext()) {
            return ScriptTextureData.Companion.fallback(fallbackTexture())
        }
        val reader = readers.next()
        val frames: MutableList<Identifier?> = ArrayList<Identifier?>()
        val delays: MutableList<Int?> = ArrayList<Int?>()
        var width = 1
        var height = 1
        try {
            ImageIO.createImageInputStream(`in`).use { imageInput ->
                reader.setInput(imageInput)
                val count = reader.getNumImages(true)
                var composed: BufferedImage? = null
                var graphics: Graphics2D? = null
                for (i in 0..<count) {
                    val frame = reader.read(i)
                    width = frame.getWidth()
                    height = frame.getHeight()
                    if (composed == null) {
                        composed = BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB)
                        graphics = composed.createGraphics()
                    }
                    var left = 0
                    var top = 0
                    var delayMs = 50
                    try {
                        val root = reader.getImageMetadata(i)
                            .getAsTree(reader.getImageMetadata(i).getNativeMetadataFormatName())
                        val desc = findGifMetadataNode(root, "ImageDescriptor")
                        if (desc != null && desc.getAttributes() != null) {
                            val leftNode = desc.getAttributes().getNamedItem("imageLeftPosition")
                            val topNode = desc.getAttributes().getNamedItem("imageTopPosition")
                            if (leftNode != null) left = leftNode.getNodeValue().toInt()
                            if (topNode != null) top = topNode.getNodeValue().toInt()
                        }
                        val gce = findGifMetadataNode(root, "GraphicControlExtension")
                        if (gce != null && gce.getAttributes() != null) {
                            val delayNode = gce.getAttributes().getNamedItem("delayTime")
                            if (delayNode != null) {
                                delayMs = max(20, delayNode.getNodeValue().toInt() * 10)
                            }
                        }
                    } catch (ignored: Exception) {
                    }
                    graphics!!.drawImage(frame, left, top, null)
                    val snapshot = BufferedImage(composed.getWidth(), composed.getHeight(), BufferedImage.TYPE_INT_ARGB)
                    snapshot.setData(composed.getData())
                    frames.add(registerBufferedImage(domain, path, i, snapshot))
                    delays.add(delayMs)
                }
                if (graphics != null) {
                    graphics.dispose()
                }
            }
        } finally {
            reader.dispose()
        }
        return ScriptTextureData(frames.filterNotNull(), delays.filterNotNull(), width, height, 20.0)
    }

    @Throws(IOException::class)
    private fun registerMp4Frames(domain: String?, path: String?, bytes: ByteArray?): ScriptTextureData {
        // MP4 support requires optional jcodec library - disabled by default
        return ScriptTextureData.Companion.fallback(fallbackTexture())
    }

    private fun findGifMetadataNode(root: Node?, nodeName: String): Node? {
        if (root == null) {
            return null
        }
        if (nodeName.equals(root.getNodeName(), ignoreCase = true)) {
            return root
        }
        var child = root.getFirstChild()
        while (child != null) {
            val found = findGifMetadataNode(child, nodeName)
            if (found != null) {
                return found
            }
            child = child.getNextSibling()
        }
        return null
    }

    private fun registerBufferedImage(domain: String?, path: String?, frame: Int, image: BufferedImage?): Identifier {
        if (image == null) {
            return fallbackTexture()
        }
        val nativeImage = NativeImage(image.getWidth(), image.getHeight(), true)
        for (y in 0..<image.getHeight()) {
            for (x in 0..<image.getWidth()) {
                val argb = image.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                nativeImage.setPixel(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        val safe = Integer.toHexString((domain + ":" + path + "#" + frame).hashCode())
        val loc = Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "dynamic/script/" + safe)
        Minecraft.getInstance().getTextureManager().register(loc, newDynamicTexture("script texture", nativeImage))
        return loc
    }

    @JvmStatic
    fun renderModel(model: MqoModel?, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, entity: Any?) {
        if (model == null) return
        model.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, null, null, entity)
    }

    @JvmStatic
    fun renderModelPreferScript(
        model: MqoModel?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        entity: Any?
    ) {
        if (model == null) return
        model.renderPreferScript(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, null, null, entity)
    }

    @JvmStatic
    fun renderModel(
        model: MqoModel?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        groupTransform: GroupTransform?,
        entity: Any?
    ) {
        if (model == null) return
        model.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, null, groupTransform, entity)
    }

    private fun applySmoothNormalsAcrossBatches(builders: MutableCollection<BatchBuilder?>?) {
        if (builders == null || builders.isEmpty()) {
            return
        }
        val byPosition: MutableMap<String?, MutableList<SmoothVertexRef?>?> =
            HashMap<String?, MutableList<SmoothVertexRef?>?>()
        for (builder in builders) {
            if (builder == null || builder.positions.isEmpty()) {
                continue
            }
            val vertexCount = builder.positions.size / 8
            for (i in 0..<vertexCount) {
                val o = i * 8
                val normal = Vector3f(
                    builder.positions.get(o + 3)!!,
                    builder.positions.get(o + 4)!!,
                    builder.positions.get(o + 5)!!
                )
                if (normal.lengthSquared() > 1.0E-8f) {
                    normal.normalize()
                } else {
                    normal.set(0.0f, 1.0f, 0.0f)
                }
                byPosition.computeIfAbsent(
                    cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.positionKey(
                        builder.positions,
                        o
                    )
                ) { k: kotlin.String? -> java.util.ArrayList<cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.SmoothVertexRef?>() }!!
                    .add(SmoothVertexRef(builder, i, normal))
            }
        }
        val smoothGroups: Stream<MutableList<SmoothVertexRef?>?> = if (byPosition.size > 4096)
            byPosition.values.parallelStream()
        else
            byPosition.values.stream()
        smoothGroups.forEach { shared: MutableList<SmoothVertexRef?>? ->
            if (shared == null || shared.size <= 1) {
                return@forEach
            }
            for (ref in shared) {
                if (ref == null) {
                    continue
                }
                val angle =
                    if (ref.builder!!.smoothingAngle > 0.0f) ref.builder.smoothingAngle else RTM_DEFAULT_SMOOTHING_ANGLE
                val cosThreshold = cos(Math.toRadians(angle.toDouble())).toFloat()
                val sum = Vector3f()
                for (other in shared) {
                    if (other == null) {
                        continue
                    }
                    if (ref.normal!!.dot(other.normal) >= cosThreshold) {
                        sum.add(other.normal)
                    }
                }
                if (sum.lengthSquared() > 1.0E-8f) {
                    sum.normalize()
                    val o = ref.index * 8
                    synchronized(ref.builder.positions) {
                        ref.builder.positions.set(o + 3, sum.x)
                        ref.builder.positions.set(o + 4, sum.y)
                        ref.builder.positions.set(o + 5, sum.z)
                    }
                }
            }
        }
    }

    private fun positionKey(positions: MutableList<Float?>, offset: Int): String {
        return (Math.round(positions.get(offset)!! * 1000.0f).toString() + ","
                + Math.round(positions.get(offset + 1)!! * 1000.0f) + ","
                + Math.round(positions.get(offset + 2)!! * 1000.0f))
    }

    @JvmOverloads
    @JvmStatic
    fun renderModel(
        model: MqoModel?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        groupFilter: GroupPredicate? = null
    ) {
        if (model == null) return
        model.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, groupFilter)
    }

    @JvmStatic
    fun renderModelWithoutScript(
        model: MqoModel?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        overlay: Int,
        translucent: Boolean,
        groupFilter: GroupPredicate?,
        renderer: TrainScriptSystem.ScriptModelRenderer?
    ) {
        if (model == null) return
        model.renderInternal(poseStack, buffer, packedLight, overlay, translucent, groupFilter, null, renderer, null)
    }

    @JvmStatic
    fun renderModelWithoutScript(
        model: MqoModel?, poseStack: PoseStack, buffer: MultiBufferSource,
        packedLight: Int, overlay: Int, translucent: Boolean,
        groupFilter: GroupPredicate?, groupTransform: GroupTransform?, entity: Any?
    ) {
        if (model == null) return
        model.renderInternal(
            poseStack,
            buffer,
            packedLight,
            overlay,
            translucent,
            groupFilter,
            groupTransform,
            model.scriptRenderer,
            entity
        )
    }

    @JvmStatic
    fun renderModelColorOverlay(
        model: MqoModel?, poseStack: PoseStack, buffer: MultiBufferSource,
        overlay: Int, groupFilter: GroupPredicate?,
        red: Int, green: Int, blue: Int, alpha: Int
    ) {
        if (model == null) return
        model.renderColorOverlay(poseStack, buffer, overlay, groupFilter, red, green, blue, alpha)
    }

    @JvmStatic
    fun renderModel(
        model: MqoModel?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        groupFilter: GroupPredicate?,
        groupTransform: GroupTransform?
    ) {
        if (model == null) return
        model.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, groupFilter, groupTransform)
    }

    @JvmStatic
    fun renderModel(
        model: MqoModel?, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int,
        groupFilter: GroupPredicate?, groupTransform: GroupTransform?, entity: Any?
    ) {
        if (model == null) return
        model.renderPreferScript(
            poseStack,
            buffer,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            groupFilter,
            groupTransform,
            entity
        )
    }

    @JvmStatic
    fun renderLegacyLightLayer(
        model: MqoModel?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        lightTextureIndex: Int,
        fullbright: Boolean,
        groupFilter: GroupPredicate?,
        groupTransform: GroupTransform?,
        entity: Any?,
    ) {
        model?.renderLegacyLightLayer(
            poseStack,
            buffer,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            lightTextureIndex,
            fullbright,
            groupFilter,
            groupTransform,
            entity,
        )
    }

    @JvmRecord
    private data class ResourceSearchResult(val packPath: Path?, val filePath: Path?, val zipEntryName: String?)

    @JvmRecord
    private data class ObjFaceVertex(val position: Vec3?, val u: Float, val v: Float, val normal: Vector3f?)

    private fun interface IncludeResolver {
        @Throws(Exception::class)
        fun resolve(includePath: String?): IncludeSource?
    }

    @JvmRecord
    private data class IncludeSource(val identifier: String?, val content: String?)

    @JvmRecord
    data class ScriptTextureData(
        val frames: List<Identifier>,
        val delaysMs: List<Int>,
        val width: Int,
        val height: Int,
        val defaultFps: Double
    ) {
        fun resolveFrameIndex(tick: Double, fpsOverride: Double): Int {
            if (frames.isEmpty()) {
                return 0
            }
            if (delaysMs.size == frames.size) {
                val millis = max(0L, Math.round(tick * 50.0))
                var total = 0L
                for (delay in delaysMs) {
                    total += max(1, delay).toLong()
                }
                if (total > 0L) {
                    val wrapped = millis % total
                    var cursor = 0L
                    for (i in delaysMs.indices) {
                        cursor += max(1, delaysMs.get(i)).toLong()
                        if (wrapped < cursor) {
                            return i
                        }
                    }
                }
            }
            val fps = if (fpsOverride > 0.0) fpsOverride else defaultFps
            return Math.floorMod(floor((tick / 20.0) * fps).toInt(), frames.size)
        }

        companion object {
            fun fallback(texture: Identifier): ScriptTextureData {
                return ScriptTextureData(listOf(texture), listOf(50), 1, 1, 20.0)
            }
        }
    }

    private fun interface TextureOpener {
        @Throws(Exception::class)
        fun open(path: String?): InputStream?

        fun getPackKey(): String = ""
    }

    private class TextureInfo(
        val location: Identifier?, emissiveTextures: Array<Identifier?>?, val isTranslucent: Boolean,
        /** テクスチャに中間アルファ(0/255以外)があるか。true=本当の半透明(ガラス等)。  */
        val hasPartialAlpha: Boolean,
        /**
         * 明確な「ガラス帯」(alpha 32..224 の半透明ピクセルがまとまった割合)を持つか。
         * AA 縁だけの二値カットアウト(SL車体等)と区別し、true なら本当の半透明テクスチャとして
         * グループ名キーワードに依らず必ずブレンド描画する(強制カットアウトしない)。
         */
        val hasGlassBand: Boolean, opaqueLocation: Identifier?, windowLocation: Identifier?
    ) {
        val emissiveTextures: Array<Identifier?>

        /** RTM pass0(不透明描画)用テクスチャ。車体だけ残し窓は穴。非AlphaBlendは元と同じ。  */
        val opaqueLocation: Identifier?

        /** RTM pass1(半透明)用テクスチャ。窓ガラスだけ残し車体は透過。非AlphaBlendは元と同じ。  */
        val windowLocation: Identifier?

        internal constructor(
            location: Identifier?,
            emissiveTextures: Array<Identifier?>?,
            isTranslucent: Boolean
        ) : this(location, emissiveTextures, isTranslucent, false, false, location, location)

        internal constructor(
            location: Identifier?,
            emissiveTextures: Array<Identifier?>?,
            isTranslucent: Boolean,
            hasPartialAlpha: Boolean
        ) : this(location, emissiveTextures, isTranslucent, hasPartialAlpha, false, location, location)

        internal constructor(
            location: Identifier?,
            emissiveTextures: Array<Identifier?>?,
            isTranslucent: Boolean,
            hasPartialAlpha: Boolean,
            hasGlassBand: Boolean
        ) : this(location, emissiveTextures, isTranslucent, hasPartialAlpha, hasGlassBand, location, location)

        init {
            this.emissiveTextures = if (emissiveTextures == null) arrayOfNulls<Identifier>(0) else emissiveTextures
            this.opaqueLocation = if (opaqueLocation == null) location else opaqueLocation
            this.windowLocation = if (windowLocation == null) location else windowLocation
        }

        fun emissiveTextureForPass(pass: Int): Identifier? {
            val index = pass - 2
            if (index < 0 || index >= emissiveTextures.size) {
                return null
            }
            return emissiveTextures[index]
        }
    }

    @JvmRecord
    private data class TextureBinding(
        val path: String?,
        val options: Set<String>,
        val lightTexturePaths: List<String>
    ) {
        fun hasLightTextures(): Boolean {
            return options.contains("light")
        }

        fun cacheKey(): String? {
            if (options.isEmpty() && lightTexturePaths.isEmpty()) {
                return path
            }
            val metadata: MutableList<String?> = ArrayList<String?>(options)
            metadata.addAll(lightTexturePaths)
            return path + TEXTURE_META_SEPARATOR + java.lang.String.join(",", metadata)
        }

        companion object {
            fun parse(raw: String?): TextureBinding {
                if (raw == null || raw.isBlank()) {
                    return TextureBinding("textures/misc/white.png", emptySet(), emptyList())
                }
                val metaIndex = raw.indexOf(TEXTURE_META_SEPARATOR)
                if (metaIndex < 0) {
                    return TextureBinding(raw, emptySet(), emptyList())
                }
                val path = raw.substring(0, metaIndex)
                val metadata = raw.substring(metaIndex + TEXTURE_META_SEPARATOR.length)
                if (metadata.isBlank()) {
                    return TextureBinding(path, emptySet(), emptyList())
                }
                val options: MutableSet<String?> = LinkedHashSet<String?>()
                val lightTexturePaths: MutableList<String?> = ArrayList<String?>()
                for (token in metadata.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val trimmed = token.trim { it <= ' ' }
                    if (!trimmed.isBlank()) {
                        val lowered = trimmed.lowercase()
                        if (lowered == "light"
                            || lowered == "alphablend"
                            || lowered == "translucent"
                            || lowered == "glassalpha"
                        ) {
                            options.add(lowered)
                        } else if (lowered == "onetex" || lowered == "one_tex") {
                            // RTM の "OneTex" フラグ: モデル全体が1テクスチャを共有する → 無視
                        } else {
                            lightTexturePaths.add(trimmed)
                        }
                    }
                }
                return TextureBinding(path, options.filterNotNull().toSet(), lightTexturePaths.filterNotNull().toList())
            }
        }
    }

    /** グループ名を受け取り、そのグループをレンダリングするかどうかを返す述語。  */
    fun interface GroupPredicate {
        fun shouldRender(groupName: String?): Boolean
    }

    /** グループ名を受け取り、そのグループに対して追加の変換を行う関数。  */
    fun interface GroupTransform {
        fun apply(poseStack: PoseStack, groupName: String?)

        /**
         * 任意の早期判定: groupName に対して何も変換しない場合は false を返す。
         * renderSelectedBatches は false 時に pushPose/popPose を完全に省略する。
         * デフォルトは保守的に true (常に push/pop)。
         * SL のような扉なし車両で 100 batch × 200 push/pop/フレームを丸ごと
         * 削減できる ⇒ Pose (Matrix4f+Matrix3f) の確保が消える。
         */
        fun mayModify(groupName: String?): Boolean {
            return true
        }
    }

    @JvmRecord
    private data class SmoothVertexRef(val builder: BatchBuilder?, val index: Int, val normal: Vector3f?)

    private class BatchBuilder(
        val order: Int,
        val groupName: String?,
        val texture: Identifier?,
        emissiveTextures: Array<Identifier?>?,
        val materialId: Int,
        val translucent: Boolean,
        val smoothingAngle: Float
    ) {
        val emissiveTextures: Array<Identifier?>

        /** マテリアル col の不透明度 (1.0=不透明)。半透明ガラス等は <1。描画時に色のαへ乗算。  */
        var baseAlpha: Float = 1.0f

        /** テクスチャが明確なガラス帯を持つ=本当の半透明。強制カットアウトを免除する。  */
        var glassTranslucent: Boolean = false

        /** 材質またはグループ自体がガラス。RTM の不透明 pass では描画しない。 */
        var explicitGlassOnly: Boolean = false

        /** RTM pass0(不透明描画)用のアルファテスト相当テクスチャ。  */
        var opaqueTexture: Identifier? = null

        /** RTM pass1(半透明)用の窓ガラスのみテクスチャ。  */
        var windowTexture: Identifier? = null
        val positions: MutableList<Float?> = ArrayList<Float?>()
        val faceSignatures: MutableSet<String?> = HashSet<String?>()
        var minU: Float = Float.POSITIVE_INFINITY
        var maxU: Float = Float.NEGATIVE_INFINITY
        var minV: Float = Float.POSITIVE_INFINITY
        var maxV: Float = Float.NEGATIVE_INFINITY

        init {
            this.emissiveTextures = if (emissiveTextures == null) arrayOfNulls<Identifier>(0) else emissiveTextures
        }

        fun put(p: Vec3, n: Vector3f, u: Float, v: Float) {
            positions.add(p.x.toFloat())
            positions.add(p.y.toFloat())
            positions.add(p.z.toFloat())
            positions.add(n.x)
            positions.add(n.y)
            positions.add(n.z)
            positions.add(u)
            positions.add(v)
            minU = min(minU, u)
            maxU = max(maxU, u)
            minV = min(minV, v)
            maxV = max(maxV, v)
        }

        fun markFace(points: Array<Vec3>, uv: FloatArray?): Boolean {
            val builder = StringBuilder(points.size * 40)
            for (i in points.indices) {
                val point = points[i]
                builder.append(java.lang.Float.floatToIntBits(point.x.toFloat())).append(':')
                    .append(java.lang.Float.floatToIntBits(point.y.toFloat())).append(':')
                    .append(java.lang.Float.floatToIntBits(point.z.toFloat())).append(':')
                if (uv != null && uv.size >= (i + 1) * 2) {
                    builder.append(java.lang.Float.floatToIntBits(uv[i * 2])).append(':')
                        .append(java.lang.Float.floatToIntBits(uv[i * 2 + 1]))
                }
                builder.append('|')
            }
            return faceSignatures.add(builder.toString())
        }

        fun bake(smoothing: Boolean): Batch {
            if (smoothing) {
                applySmoothNormals()
            }
            val data = FloatArray(positions.size)
            for (i in positions.indices) data[i] = positions.get(i)!!
            val safeMinU = if (minU.isFinite()) minU else 0.0f
            val safeMaxU = if (maxU.isFinite()) maxU else 1.0f
            val safeMinV = if (minV.isFinite()) minV else 0.0f
            val safeMaxV = if (maxV.isFinite()) maxV else 1.0f
            val built = Batch(
                order,
                groupName,
                texture,
                emissiveTextures,
                data,
                data.size / 8,
                materialId,
                translucent,
                safeMinU,
                safeMaxU,
                safeMinV,
                safeMaxV
            )
            built.baseAlpha = baseAlpha
            built.glassTranslucent = glassTranslucent
            built.explicitGlassOnly = explicitGlassOnly
            built.opaqueTexture = if (opaqueTexture != null) opaqueTexture else texture
            built.windowTexture = if (windowTexture != null) windowTexture else texture
            return built
        }

        fun applySmoothNormals() {
            val vertexCount = positions.size / 8
            if (vertexCount <= 0) {
                return
            }

            val byPosition: MutableMap<String?, MutableList<Int?>?> = HashMap<String?, MutableList<Int?>?>()
            val originalNormals: Array<Vector3f> = Array(vertexCount) { Vector3f() }
            for (i in 0..<vertexCount) {
                val o = i * 8
                byPosition.computeIfAbsent(positionKey(o)) { k: kotlin.String? -> java.util.ArrayList<kotlin.Int?>() }!!
                    .add(i)
                originalNormals[i] = Vector3f(positions.get(o + 3)!!, positions.get(o + 4)!!, positions.get(o + 5)!!)
                if (originalNormals[i].lengthSquared() > 1.0E-8f) {
                    originalNormals[i].normalize()
                } else {
                    originalNormals[i].set(0.0f, 1.0f, 0.0f)
                }
            }

            val angle = if (this.smoothingAngle > 0.0f) this.smoothingAngle else RTM_DEFAULT_SMOOTHING_ANGLE
            val cosThreshold = cos(Math.toRadians(angle.toDouble())).toFloat()
            for (i in 0..<vertexCount) {
                val o = i * 8
                val shared = byPosition.get(positionKey(o))
                if (shared == null || shared.isEmpty()) {
                    continue
                }

                val current = originalNormals[i]
                val sum = Vector3f()
                for (other in shared) {
                    val normal: Vector3f? = originalNormals[other!!]
                    if (current.dot(normal) >= cosThreshold) {
                        sum.add(normal)
                    }
                }
                if (sum.lengthSquared() > 1.0E-8f) {
                    sum.normalize()
                    positions.set(o + 3, sum.x)
                    positions.set(o + 4, sum.y)
                    positions.set(o + 5, sum.z)
                }
            }
        }

        fun positionKey(offset: Int): String {
            return (Math.round(positions.get(offset)!! * 1000.0f).toString() + ","
                    + Math.round(positions.get(offset + 1)!! * 1000.0f) + ","
                    + Math.round(positions.get(offset + 2)!! * 1000.0f))
        }
    }

    class MqoModel internal constructor(private val batches: MutableList<Batch?>, materialTextures: MutableList<Identifier?>) {
        private val batchesByNormalizedGroup: MutableMap<String?, MutableList<Batch?>?>

        val scriptModel: ScriptModel?
        private val groupQuadCornerCache: MutableMap<String?, MutableList<FloatArray?>?> =
            ConcurrentHashMap<String?, MutableList<FloatArray?>?>()
        private val groupCenterCache: MutableMap<String?, Vec3?> = ConcurrentHashMap<String?, Vec3?>()

        // 床下の蓋(下向き面)用。車体シェルの底Y・XZ範囲を遅延計算してキャッシュ。
        // 片面表示のままだと開いた底から中の暗い空間が透けて黒く見えるため、底に下向きの
        // グレー板を1枚足して塞ぐ(両面表示は使わない=禁止ルール遵守)。
        @Volatile
        private var bodyCapRect: FloatArray? = null // {minX, minZ, maxX, maxZ, bottomY}
            /** 車体シェルの {minX,minZ,maxX,maxZ,bottomY} を遅延計算。蓋を持たない(該当面なし)なら null。  */
            get() {
                if (bodyCapComputed) return field
                synchronized(this) {
                    if (!bodyCapComputed) {
                        field = computeBodyCapRect()
                        bodyCapComputed = true
                    }
                }
                return field
            }

        @Volatile
        private var bodyCapComputed = false

        private fun computeBodyCapRect(): FloatArray? {
            var minX = kotlin.Float.MAX_VALUE
            var minZ = kotlin.Float.MAX_VALUE
            var minY = kotlin.Float.MAX_VALUE
            var maxX = -kotlin.Float.MAX_VALUE
            var maxZ = -kotlin.Float.MAX_VALUE
            var any = false
            for (b in batches) {
                if (b == null || b.data == null || b.vertexCount <= 0) continue
                if (isUnderTruckGroup(b.groupNameLower)) continue
                for (i in 0..<b.vertexCount) {
                    val o = i * 8
                    val x = b.data[o]
                    val y = b.data[o + 1]
                    val z = b.data[o + 2]
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (z < minZ) minZ = z
                    if (z > maxZ) maxZ = z
                    if (y < minY) minY = y
                    any = true
                }
            }
            if (!any || maxX <= minX || maxZ <= minZ) return null
            // 横幅をわずかに内側へ詰める(外板と完全一致だと縁がはみ出して見えるのを防ぐ)。
            val insetX = (maxX - minX) * 0.02f
            val insetZ = (maxZ - minZ) * 0.02f
            return floatArrayOf(minX + insetX, minZ + insetZ, maxX - insetX, maxZ - insetZ, minY)
        }

        /**
         * 床下の蓋を MultiBufferSource 経由で描く(Iris 等シェーダ有効時=fullbright でない経路)。
         * RenderType.entitySolid + グレー色で、シェーダのライティングに乗せて描画する。
         */
        private fun renderBodyBottomCapBuffered(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
            overlay: Int
        ) {
            val r = this.bodyCapRect
            if (r == null) return
            val minX = r[0]
            val minZ = r[1]
            val maxX = r[2]
            val maxZ = r[3]
            val y = r[4]
            val pose = poseStack.last()
            val mat = pose.pose()
            val vc = buffer.getBuffer(
                RenderTypes.entitySolid(
                    capWhiteTexture
                )
            )
            val gray = 0x29 // 暗めグレー(41) アルベド。シェーダのライティングで陰影が付く。
            // 下向き(-Y)の面を両巻きで2枚(自前の蓋なので両面OK)。
            capVertexBuf(vc, mat, minX, y, minZ, gray, packedLight, overlay, 0f, -1f, 0f)
            capVertexBuf(vc, mat, maxX, y, minZ, gray, packedLight, overlay, 0f, -1f, 0f)
            capVertexBuf(vc, mat, maxX, y, maxZ, gray, packedLight, overlay, 0f, -1f, 0f)
            capVertexBuf(vc, mat, minX, y, maxZ, gray, packedLight, overlay, 0f, -1f, 0f)
            capVertexBuf(vc, mat, minX, y, maxZ, gray, packedLight, overlay, 0f, 1f, 0f)
            capVertexBuf(vc, mat, maxX, y, maxZ, gray, packedLight, overlay, 0f, 1f, 0f)
            capVertexBuf(vc, mat, maxX, y, minZ, gray, packedLight, overlay, 0f, 1f, 0f)
            capVertexBuf(vc, mat, minX, y, minZ, gray, packedLight, overlay, 0f, 1f, 0f)
        }

        fun estimateMemoryBytes(): Long {
            var bytes = 512L
            bytes += batches.size.toLong() * 160L
            for (batch in batches) {
                bytes += 64L
                bytes += batch!!.data!!.size.toLong() * java.lang.Float.BYTES
                if (batch.groupName != null) {
                    bytes += batch.groupName.length.toLong() * 2L
                }
            }
            bytes += scriptModel!!.textures.size.toLong() * 64L
            return bytes
        }

        private var scriptEngine: ScriptEngine? = null
        internal var scriptRenderer: TrainScriptSystem.ScriptModelRenderer? = null
        private var hasLegacyRenderFunction: Boolean? = null
        private var legacyScriptDisabled = false
        private var legacyScriptFailureCount = 0
        private val observedLegacyPassActivity = BooleanArray(LEGACY_SCRIPT_PASS_COUNT)
        private var legacyPassObservationMask = 0

        // pass を最後に観測してから経過した呼び出し数。
        // 一定値毎に強制再観測することで、ライト ON 等の状態変化時に
        // skip を解除する。
        private val passSinceRecheck = IntArray(LEGACY_SCRIPT_PASS_COUNT)
        fun setScriptEngine(engine: ScriptEngine?, renderer: TrainScriptSystem.ScriptModelRenderer?) {
            this.scriptEngine = engine
            this.scriptRenderer = renderer
            this.hasLegacyRenderFunction = null
            this.legacyScriptDisabled = false
            this.legacyScriptFailureCount = 0
            this.legacyPassObservationMask = 0
            Arrays.fill(this.observedLegacyPassActivity, false)
        }

        fun setScriptEngine(engine: Any?) {
            if (engine is ScriptEngine) {
                setScriptEngine(engine, null)
            }
        }

        fun getScriptEngine(): ScriptEngine? {
            return scriptEngine
        }

        fun hasRenderScript(): Boolean {
            return scriptEngine != null && !legacyScriptDisabled
        }

        /** AABB {minX,minY,minZ,maxX,maxY,maxZ} を全頂点から計算。モデルが空なら単位ボックス。  */
        fun computeBounds(): FloatArray {
            var minX = kotlin.Float.MAX_VALUE
            var minY = kotlin.Float.MAX_VALUE
            var minZ = kotlin.Float.MAX_VALUE
            var maxX = -kotlin.Float.MAX_VALUE
            var maxY = -kotlin.Float.MAX_VALUE
            var maxZ = -kotlin.Float.MAX_VALUE
            for (b in batches) {
                if (b == null || b.data == null) continue
                for (i in 0..<b.vertexCount) {
                    val o = i * 8
                    val x = b.data[o]
                    val y = b.data[o + 1]
                    val z = b.data[o + 2]
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    if (z < minZ) minZ = z
                    if (z > maxZ) maxZ = z
                }
            }
            if (minX > maxX) return floatArrayOf(-0.5f, 0f, -0.5f, 0.5f, 2f, 0.5f)
            return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        }

        fun hasGroupNamed(groupName: String?): Boolean {
            if (groupName == null || groupName.isBlank()) {
                return false
            }
            for (batch in batches) {
                if (batch!!.groupName != null && batch.groupName.equals(groupName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun getGroupCenter(groupName: String?): Vec3? {
            val normalized: String = normalizeBatchGroupName(groupName)
            if (normalized.isEmpty()) {
                return null
            }
            return groupCenterCache.computeIfAbsent(normalized) { key: String? ->
                val groupBatches = batchesByNormalizedGroup.get(key)
                if (groupBatches == null || groupBatches.isEmpty()) {
                    return@computeIfAbsent null
                }
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var minZ = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                var maxZ = Double.NEGATIVE_INFINITY
                for (b in groupBatches) {
                    if (b == null || b.data == null) continue
                    for (i in 0..<b.vertexCount) {
                        val o = i * 8
                        val x = b.data[o].toDouble()
                        val y = b.data[o + 1].toDouble()
                        val z = b.data[o + 2].toDouble()
                        minX = min(minX, x)
                        minY = min(minY, y)
                        minZ = min(minZ, z)
                        maxX = max(maxX, x)
                        maxY = max(maxY, y)
                        maxZ = max(maxZ, z)
                    }
                }
                if (!minX.isFinite() || !maxX.isFinite()) {
                    return@computeIfAbsent null
                }
                Vec3(
                    (minX + maxX) * 0.5,
                    (minY + maxY) * 0.5,
                    (minZ + maxZ) * 0.5
                )
            }
        }

        /**
         * 指定グループの各クワッド面の4隅座標(モデル空間)を返す。各要素は長さ12のfloat[]
         * (4隅 × x,y,z)。LCD/モニタのスクリプトが面の上にgif等を貼るために使う。
         * data は QUADS(4頂点/面 × 8float: x,y,z,nx,ny,nz,u,v)。
         */
        fun getGroupQuadCorners(groupNames: MutableSet<String?>?): MutableList<FloatArray?> {
            val out: MutableList<FloatArray?> = ArrayList<FloatArray?>()
            if (groupNames == null || groupNames.isEmpty()) return out
            val norm: MutableSet<String?> = HashSet<String?>()
            for (g in groupNames) {
                if (g != null && !g.isBlank()) norm.add(normalizeBatchGroupName(g))
            }
            if (norm.isEmpty()) {
                return out
            }
            val cacheKey = java.lang.String.join(",", TreeSet<String?>(norm))
            val cached = groupQuadCornerCache.get(cacheKey)
            if (cached != null) {
                return cached
            }
            for (b in batches) {
                if (b == null || b.data == null) continue
                if (!norm.contains(normalizeBatchGroupName(b.groupName))) continue
                var i = 0
                while (i + 4 <= b.vertexCount) {
                    val q = FloatArray(12)
                    for (c in 0..3) {
                        val o = (i + c) * 8
                        q[c * 3] = b.data[o]
                        q[c * 3 + 1] = b.data[o + 1]
                        q[c * 3 + 2] = b.data[o + 2]
                    }
                    out.add(q)
                    i += 4
                }
            }
            val immutable = out.toMutableList()
            groupQuadCornerCache.put(cacheKey, immutable)
            return immutable
        }

        /**
         * 車体MQO自身が走り装置(車輪)を持つか。蒸気機関車のように車輪・動輪・台車を
         * 車体モデル内に持ちスクリプトで自前描画する車両は、別途の汎用台車モデル
         * (ModelBogie.class 置換) を描く必要がない (= 二重描画/散乱の原因)。
         */
        fun hasOwnWheelGroups(): Boolean {
            for (batch in batches) {
                val g = batch!!.groupNameLower
                if (g == null) continue
                if (g.startsWith("wheel") || g.contains("動輪") || g.contains("車輪")) {
                    return true
                }
            }
            return false
        }

        val allNormalizedGroupNames: MutableSet<String?>
            /** モデル内の全グループ名 (正規化済み: trim + toLowerCase) を返す。  */
            get() {
                val result: MutableSet<String?> = LinkedHashSet<String?>()
                for (batch in batches) {
                    if (batch!!.groupName != null && !batch.groupName.isBlank()) {
                        result.add(batch.groupName.trim { it <= ' ' }.lowercase())
                    }
                }
                return result
            }

        fun hasTranslucentBatches(): Boolean {
            for (batch in batches) {
                if (batch!!.translucent) {
                    return true
                }
            }
            return false
        }

        fun hasOpaqueBatches(): Boolean {
            for (batch in batches) {
                if (!batch!!.explicitGlassOnly) {
                    return true
                }
            }
            return false
        }

        val batchCount: Int
            get() = batches.size

        val translucentBatchCount: Int
            get() {
                var count = 0
                for (batch in batches) {
                    if (batch!!.translucent) {
                        count++
                    }
                }
                return count
            }

        val totalVertexCount: Int
            get() {
                var count = 0
                for (batch in batches) {
                    count += batch!!.vertexCount
                }
                return count
            }

        fun hasLegacyLightTextures(): Boolean {
            for (batch in batches) {
                if (batch!!.emissiveTextures.any { it != null }) {
                    return true
                }
            }
            return false
        }

        fun renderNamedGroups(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, overlay: Int,
            translucent: Boolean, normalizedGroupNames: MutableSet<String?>?,
            scriptRenderer: TrainScriptSystem.ScriptModelRenderer?
        ) {
            if (normalizedGroupNames == null || normalizedGroupNames.isEmpty()) {
                return
            }
            var ordered = renderListCache.get(normalizedGroupNames)
            if (ordered == null) {
                val selected: MutableSet<Batch?> = LinkedHashSet<Batch?>()
                for (name in normalizedGroupNames) {
                    val batches = batchesByNormalizedGroup.get(name)
                    if (batches != null && !batches.isEmpty()) {
                        selected.addAll(batches)
                    }
                }
                if (selected.isEmpty()) {
                    ordered = mutableListOf<Batch?>()
                } else {
                    ordered = ArrayList<Batch?>(selected)
                    ordered.sortWith(Comparator.comparingInt<Batch?>(ToIntFunction { batch: Batch? -> batch!!.order }))
                }
                renderListCache.put(normalizedGroupNames, ordered)
            }
            if (ordered.isEmpty()) {
                return
            }
            val entity = if (scriptRenderer != null) scriptRenderer.currentEntity else null
            val fullbright = false
            renderSelectedBatches(
                ordered,
                poseStack,
                buffer,
                packedLight,
                overlay,
                translucent,
                scriptRenderer,
                entity,
                fullbright
            )
        }

        // (Set インスタンス → ソート済み Batch リスト) を IdentityHashMap でキャッシュ。
        // SL の動軸 renderParts ループで毎フレーム発生していた LinkedHashSet/ArrayList
        // 確保 + sort コストを排除する。ParsedGroupSet.presentGroupNames は
        // 同一 Set インスタンスのまま渡されるため、ヒット率はほぼ 100%。
        private val renderListCache = IdentityHashMap<MutableSet<String?>?, MutableList<Batch?>?>()


        init {
            this.batchesByNormalizedGroup = buildBatchIndex(
                batches
            )
            this.scriptModel = ScriptModel(materialTextures)
        }

        private fun executeScript(
            poseStack: PoseStack?,
            buffer: MultiBufferSource?,
            packedLight: Int,
            overlay: Int,
            pass: Int,
            entity: Any?
        ): Boolean {
            if (scriptEngine == null || legacyScriptDisabled) {
                return false
            }
            try {
                if (scriptRenderer != null) {
                    scriptRenderer!!.setRenderContext(poseStack, buffer, packedLight, overlay, pass, entity)
                    val lightBatch = batches.firstOrNull { it?.materialId == 2 }
                    if (lightBatch != null) {
                        scriptRenderer!!.setLegacyMaterialContext(lightBatch.materialId, lightBatch.texture)
                    }
                }
                // RTM スクリプトは model.renderPart("...") で部品を描画する。
                // ScriptModel が現在の renderer を知らないと描画できないため、毎フレーム差し替える。
                if (scriptModel != null && scriptRenderer != null) {
                    scriptModel.setActiveRenderer(scriptRenderer)
                }
                if (scriptRenderer != null) {
                    if (scriptRenderer!!.tryReplayCachedScript(pass, entity)) {
                        // replay 成功 - JS engine を 1 度も呼ばずに描画完了
                        noteLegacyPassActivity(pass, true)
                        return true
                    }
                    // miss: 録画開始してから JS を走らせる
                    scriptRenderer!!.beginRecording(pass, entity)
                }
                if (scriptEngine is ScriptEngine) {
                    val renderedBatchesBefore =
                        if (scriptRenderer != null) scriptRenderer!!.renderedBatchCount else 0
                    // RTMレガシースクリプトは entity.getBogie(n) / entity.field_70177_z 等
                    // LegacyScriptExecutor のAPIを前提としている。生の TrainEntity ではなく
                    // LegacyScriptExecutor でラップして渡す。
                    val scriptEntity = if (scriptRenderer != null) scriptRenderer!!.scriptEntityFor(entity) else entity
                    scriptEngine!!.put("poseStack", null)
                    scriptEngine!!.put("pass", pass)
                    scriptEngine!!.put("entity", scriptEntity)
                    scriptEngine!!.put("executer", scriptEntity)
                    scriptEngine!!.put("executor", scriptEntity)
                    if (hasLegacyRenderFunction == null) {
                        val renderType = scriptEngine!!.eval("typeof render")
                        hasLegacyRenderFunction = "function" == renderType
                                || (renderType != null && "function" == renderType.toString())
                        RealTrainModRenewed.LOGGER.info(
                            "[ScriptDiag] hasRenderFn={} typeofRender={}",
                            hasLegacyRenderFunction, renderType
                        )
                    }
                    if (hasLegacyRenderFunction == true) {
                        var rendered: kotlin.Boolean
                        if (scriptEngine is Invocable) {
                            try {
                                (scriptEngine as Invocable).invokeFunction("render", scriptEntity, pass, null)
                                rendered =
                                    scriptRenderer == null || scriptRenderer!!.renderedBatchCount > renderedBatchesBefore
                                if (scriptRenderer != null) scriptRenderer!!.endRecording(rendered)
                                noteLegacyPassActivity(pass, rendered)
                                return rendered
                            } catch (ignored: NoSuchMethodException) {
                            }
                        }
                        scriptEngine!!.eval("render(entity, pass, null);")
                        rendered =
                            scriptRenderer == null || scriptRenderer!!.renderedBatchCount > renderedBatchesBefore
                        if (scriptRenderer != null) scriptRenderer!!.endRecording(rendered)
                        noteLegacyPassActivity(pass, rendered)
                        return rendered
                    }
                }
            } catch (e: Exception) {
                if (scriptRenderer != null) scriptRenderer!!.endRecording(false)
                // 例外が出ても、それまでに renderRegisteredGroups などで登録済みの
                // scriptedOpaqueGroups / scriptedTranslucentGroups は残す。
                // これらをクリアしてしまうと baked render の filter が無効化されて、
                // 既にスクリプト側で描いた body 等を baked が再度上書き描画し
                // z-fighting が発生する (C12 SL: render_rod が <eval>:317 で例外)。
                legacyScriptFailureCount++
                if (legacyScriptFailureCount >= 3) {
                    legacyScriptDisabled = true
                    if (scriptRenderer != null) {
                        scriptRenderer!!.clearScriptRegisteredGroups()
                    }
                    RealTrainModRenewed.LOGGER.warn(
                        "Legacy model script failed on pass {} three times; disabling script and using baked render for this model.",
                        pass, e
                    )
                } else {
                    RealTrainModRenewed.LOGGER.warn(
                        "Legacy model script failed on pass {} ({}/3 before disabling).",
                        pass, legacyScriptFailureCount, e
                    )
                }
            } finally {
                if (scriptRenderer != null) {
                    // スクリプトが pushMatrix/popMatrix のバランスを崩したまま終了した場合に
                    // matrixDepth を 0 に戻す。残った push は外側の executeScript 呼び出し元の
                    // pushPose/popPose で吸収されるため、ここでは内部カウンタを 0 にするだけ。
                    scriptRenderer!!.restoreMatrixDepth(0)
                    scriptRenderer!!.clearRenderContext()
                }
            }
            return false
        }

        private fun noteLegacyPassActivity(pass: Int, rendered: kotlin.Boolean) {
            if (pass < 0 || pass >= LEGACY_SCRIPT_PASS_COUNT) {
                return
            }
            legacyPassObservationMask = legacyPassObservationMask or (1 shl pass)
            if (rendered) {
                observedLegacyPassActivity[pass] = true
            }
        }

        private fun shouldSkipObservedLegacyPass(pass: Int): kotlin.Boolean {
            if (pass < 0 || pass >= LEGACY_SCRIPT_PASS_COUNT) return false
            if (pass == 0) return false // pass 0 は本体描画。絶対走らせる。

            val bit = 1 shl pass
            val observed = (legacyPassObservationMask and bit) != 0
            if (!observed) return false
            // 一度走らせて batch を 0 個しか出さなかった pass はスキップ。
            // ただし PASS_RECHECK_INTERVAL 回ごとに再観測してライト ON 等の
            // 状態変化を検出する。
            if (observedLegacyPassActivity[pass]) return false
            if (++passSinceRecheck[pass] >= PASS_RECHECK_INTERVAL) {
                passSinceRecheck[pass] = 0
                legacyPassObservationMask = legacyPassObservationMask and bit.inv() // 再観測
                return false
            }
            return true
        }

        internal fun renderInternal(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, overlay: Int,
            translucent: kotlin.Boolean, groupFilter: GroupPredicate?, groupTransform: GroupTransform?,
            scriptRenderer: TrainScriptSystem.ScriptModelRenderer?, entity: Any?
        ) {
            val fullbright = false
            renderSelectedBatches(
                this.batches,
                poseStack,
                buffer,
                packedLight,
                overlay,
                translucent,
                groupFilter,
                groupTransform,
                scriptRenderer,
                entity,
                fullbright
            )
        }

        private fun renderSelectedBatches(
            selectedBatches: MutableList<Batch?>,
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
            overlay: Int,
            translucent: kotlin.Boolean,
            scriptRenderer: TrainScriptSystem.ScriptModelRenderer?,
            entity: Any?,
            fullbright: kotlin.Boolean,
        ) {
            renderSelectedBatches(
                selectedBatches,
                poseStack,
                buffer,
                packedLight,
                overlay,
                translucent,
                null,
                null,
                scriptRenderer,
                entity,
                fullbright,
            )
        }

        private fun renderSelectedBatches(
            selectedBatches: MutableList<Batch?>,
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
            overlay: Int,
            translucent: kotlin.Boolean,
            groupFilter: GroupPredicate?,
            groupTransform: GroupTransform?,
            scriptRenderer: TrainScriptSystem.ScriptModelRenderer?,
            entity: Any?,
            fullbright: kotlin.Boolean,
            legacyLightTextureIndex: Int? = null,
            legacyLightFullbright: Boolean = true,
        ) {
            // シェーダー(Iris/Oculus)有効時は、フラットな直接GL経路ではなく法線付きの
            // バッファ経路で描画する。直接GL経路は頂点法線スムージングが効かず、影modで
            // 車体がカクついて見えるため(数値は一切変更しない・経路のみ切替)。
            var fullbright = fullbright
            if (fullbright && isShaderPackInUse()) {
                fullbright = false
            }
            // ループ全体で保持する直近値。再設定を skip するため。
            val gr = if (scriptRenderer != null) scriptRenderer.colorRed255 else 255
            val gg = if (scriptRenderer != null) scriptRenderer.colorGreen255 else 255
            val gb = if (scriptRenderer != null) scriptRenderer.colorBlue255 else 255
            val ga = if (scriptRenderer != null) scriptRenderer.applyAlpha255(255) else 255
            val lastBlendMode = -1 // 0=disabled, 1=blend(depthMask=false), 2=cutout(depthMask=true)
            val lastCullMode = -1 // 0=両面(cull無効), 1=片面(cull有効)。batch ごとに切替。
            val useCull = shouldCullModelFaces(entity)
            // fullbright(直接GL/VBO)経路はライトマップを使わないので、周囲の明るさを
            // setShaderColor に係数として掛けて疑似的に再現する。これで「高速VBO」かつ
            // 「夜は暗く/昼は明るく(=勝手に発光しない)」を両立する。法線ディフューズは
            // RTM 原作同様フラット(向きで陰影を付けない)。emissive pass(>=2)は packedLight が
            // FULL なので係数 ≈ 1.0 となり、前照灯等はそのまま明るい。
            val lightFactor = if (fullbright) computeFlatBrightness(packedLight) else 1.0f

            for (batch in selectedBatches) {
                if (scriptRenderer != null && shouldSkipLegacyPlaceholderGroup(batch!!.groupName)) {
                    continue
                }
                if (groupFilter != null && !groupFilter.shouldRender(batch!!.groupName)) {
                    continue
                }
                if (shouldSuppressPackSpecificShadowArtifact(entity, batch!!.groupNameLower)) {
                    continue
                }
                val scriptPassNow = legacyLightTextureIndex?.plus(2)
                    ?: if (scriptRenderer != null) scriptRenderer.currentPass else 0
                if (!translucent && batch.baseAlpha < 0.999f && scriptPassNow < 2) continue
                if (!translucent && batch.explicitGlassOnly && scriptPassNow < 2) continue
                if (translucent && !batch.translucent && scriptPassNow < 2) continue
                if (scriptRenderer != null) {
                    scriptRenderer.currentMatId = batch.materialId
                    scriptRenderer.currentBatchTexture = batch.texture
                    scriptRenderer.onBatchRendered()
                }
                val willTransform = groupTransform != null && groupTransform.mayModify(batch.groupName)
                if (willTransform) {
                    poseStack.pushPose()
                }
                try {
                    if (willTransform) {
                        groupTransform.apply(poseStack, batch.groupName)
                    }

                    val scriptPass = legacyLightTextureIndex?.plus(2)
                        ?: if (scriptRenderer != null) scriptRenderer.currentPass else 0
                    val scriptTexture = scriptRenderer != null && scriptRenderer.boundTexture != null
                    val emissiveTexture =
                        if (!scriptTexture && scriptPass >= 2) batch.emissiveTextureForPass(scriptPass) else null
                    val emissiveBatchFullbright = emissiveTexture != null &&
                        (legacyLightTextureIndex == null || legacyLightFullbright)
                    val legacyPlainFullbright = scriptPass >= 2 && isLegacyPlainFullbrightGroup(batch.groupNameLower)
                    if (scriptPass >= 2 && !scriptTexture && emissiveTexture == null && !legacyPlainFullbright) {
                        continue
                    }
                    val lowerGroupName = batch.groupNameLower
                    var texture = (if (scriptTexture)
                        scriptRenderer.boundTexture
                    else
                        (if (emissiveTexture != null) emissiveTexture else batch.texture))!!
                    if (!scriptTexture && emissiveTexture == null && !legacyPlainFullbright) {
                        texture = (if (translucent) batch.windowTexture else batch.opaqueTexture)!!
                    }

                    val forceCutout: kotlin.Boolean
                    val depthBias: kotlin.Float
                    if (scriptTexture) {
                        forceCutout = shouldForceLegacyAlphaCutout(batch, lowerGroupName, true)
                                || shouldForceCabCutout(batch, lowerGroupName, true)
                                || shouldForceDisplayCutout(batch, lowerGroupName, true)
                                || shouldForceShaderSafeCutout(entity, batch, lowerGroupName, true)
                        depthBias = getDepthBias(batch, lowerGroupName, true)
                    } else {
                        // scriptTexture=false の結果はバッチ構築時に 1 度計算してキャッシュ。
                        // SL のように毎フレーム数百回呼ばれる shouldForce*/getDepthBias の
                        // 文字列 contains/startsWith を完全に省ける。
                        if (!batch.cachedComputed) {
                            batch.cachedForceCutoutNoScriptTex =
                                shouldForceLegacyAlphaCutout(batch, lowerGroupName, false)
                                        || shouldForceCabCutout(batch, lowerGroupName, false)
                                        || shouldForceDisplayCutout(batch, lowerGroupName, false)
                            batch.cachedDepthBiasNoScriptTex = getDepthBias(batch, lowerGroupName, false)
                            batch.cachedComputed = true
                        }
                        forceCutout = batch.cachedForceCutoutNoScriptTex
                                || shouldForceShaderSafeCutout(entity, batch, lowerGroupName, false)
                        depthBias = batch.cachedDepthBiasNoScriptTex
                    }
                    val needsBlend = (translucent && batch.translucent)
                            || (!forceCutout && (scriptTexture || scriptPassNow >= 2))

                    val scriptRed = if (scriptRenderer != null) scriptRenderer.colorRed255 else 255
                    val scriptGreen = if (scriptRenderer != null) scriptRenderer.colorGreen255 else 255
                    val scriptBlue = if (scriptRenderer != null) scriptRenderer.colorBlue255 else 255
                    var scriptAlpha = if (scriptRenderer != null) scriptRenderer.applyAlpha255(255) else 255
                    // マテリアル col の不透明度を乗算 (ガラス等 a<1 で半透明に透ける)。
                    if (batch.baseAlpha < 0.999f) {
                        scriptAlpha = Math.round(scriptAlpha * batch.baseAlpha)
                    }

                    // Lightmap-aware path: block entities (rails, installed objects)
                    // メタセコイア同様の片面 (cull) 表示。
                    val renderType = if (needsBlend)
                        TRAIN_ENTITY_TRANSLUCENT_NO_DEPTH.apply(texture)
                    else
                        (if (useCull) RenderTypes.entityCutout(texture) else RenderTypes.entityCutout(texture))
                    val consumer = buffer.getBuffer(renderType)
                    val pose = poseStack.last()
                    val mat = pose.pose()
                    val norm = pose.normal()
                    val normalOut = FloatArray(3)
                    val vertexLight = if (legacyPlainFullbright || emissiveBatchFullbright) {
                        0x00F000F0
                    } else {
                        resolveVertexLight(entity, lowerGroupName, packedLight)
                    }
                    for (i in 0..<batch.vertexCount) {
                        val o = i * 8
                        var x = batch.data!![o]
                        var y = batch.data[o + 1]
                        var z = batch.data[o + 2]
                        val nx = batch.data[o + 3]
                        val ny = batch.data[o + 4]
                        val nz = batch.data[o + 5]
                        var u = batch.data[o + 6]
                        var v = batch.data[o + 7]
                        if (depthBias != 0.0f) {
                            val il = (1.0 / sqrt(max(1.0E-8f, nx * nx + ny * ny + nz * nz).toDouble())).toFloat()
                            x += nx * il * depthBias
                            y += ny * il * depthBias
                            z += nz * il * depthBias
                        }
                        if (scriptRenderer != null) {
                            u = scriptRenderer.mapU(u, batch.minU, batch.maxU)
                            v = scriptRenderer.mapV(v, batch.minV, batch.maxV)
                        }
                        val tnx = norm.m00() * nx + norm.m10() * ny + norm.m20() * nz
                        val tny = norm.m01() * nx + norm.m11() * ny + norm.m21() * nz
                        val tnz = norm.m02() * nx + norm.m12() * ny + norm.m22() * nz
                        normalizeNormal(tnx, tny, tnz, normalOut)
                        VertexWriter.addVertex(consumer, mat, x, y, z)
                            .setColor(scriptRed, scriptGreen, scriptBlue, scriptAlpha)
                            .setUv(u, v)
                            .setOverlay(overlay)
                            .setLight(vertexLight)
                            .setNormal(normalOut[0], normalOut[1], normalOut[2])
                    }
                } finally {
                    if (willTransform) {
                        poseStack.popPose()
                    }
                }
            }

            // (床下の蓋は撤去: ユーザー報告「床に敷いた影のように見えて邪魔」のため。)
        }

        fun renderLegacyLightLayer(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
            overlay: Int,
            lightTextureIndex: Int,
            fullbright: Boolean,
            groupFilter: GroupPredicate?,
            groupTransform: GroupTransform?,
            entity: Any?,
        ) {
            if (lightTextureIndex < 0 || !hasLegacyLightTextures()) return
            renderSelectedBatches(
                batches,
                poseStack,
                buffer,
                packedLight,
                overlay,
                true,
                groupFilter,
                groupTransform,
                null,
                entity,
                false,
                lightTextureIndex,
                fullbright,
            )
        }

        fun renderPreferScript(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, overlay: Int,
            groupFilter: GroupPredicate?, groupTransform: GroupTransform?, entity: Any?
        ) {
            // 元の実装は renderPreferScript 内で script を実行し、その後 render(7-arg) を呼んで
            // 再度 script を実行 + baked render する2段構えだった。これにより 2回目の
            // resetRenderStatistics() が scriptedOpaqueGroups をクリアし、baked filter
            // チェック時に hasScriptRenderedGroups=false となって登録グループまで baked が描画する
            // → 全 body 変形が重なる現象が発生していた。
            // 修正: 1段で完結させる。script を実行し、その直後に baked filter を組み立てて baked render を呼ぶ。
            val hasScript = scriptEngine != null
            // installed object 側も RTM 本家どおり「全不透明の後に半透明」を守る。
            // script/baked の順序だけ整え、同じ buffer 上で最後にまとめて流す。
            // ★ deferTranslucent はスクリプト描画専用の仕組み。scriptRenderer が null の
            //   (スクリプト無し車両 = E131 等)では使わない。以前は true 固定で、null の
            //   scriptRenderer に setDeferTranslucent を呼んで NPE → 描画全体が失敗し
            //   車体も台車も一切見えなくなっていた。
            val deferTrans = scriptRenderer != null
            try {
                if (scriptRenderer != null) {
                    scriptRenderer!!.resetRenderStatistics()
                }
                if (deferTrans) {
                    scriptRenderer!!.setDeferTranslucent(true)
                }
                if (hasScript) {
                    // script の render() を全 pass (0/1/2/3) で呼ぶ。 pack の script は
                    // pass=0 で body opaque、 pass=1 で translucent 装飾 (KQ前面の黒)、
                    // pass=2 で emissive (前照灯) を描画するロジックを持つことがあるため。
                    // 同じ group の重複描画は renderRegisteredGroups 側で 「同 pass で再描画させない」
                    // チェックを入れて防ぐ (todo set + scriptedOpaque/TranslucentGroups)。
                    for (pass in 0..<LEGACY_SCRIPT_PASS_COUNT) {
                        if (entity !is TrainEntity && shouldSkipObservedLegacyPass(pass)) continue
                        if (pass >= 2 && scriptRenderer != null && !scriptRenderer!!.hasEmissivePassContent()) continue
                        poseStack.pushPose()
                        try {
                            executeScript(poseStack, buffer, packedLight, overlay, pass, entity)
                        } finally {
                            try {
                                poseStack.popPose()
                            } catch (ignored: Throwable) {
                            }
                        }
                    }
                }
                // baked render filter 組み立て (scriptedOpaqueGroups を 使う前にクリアしない)
                var opaqueFilter = groupFilter
                var translucentFilter = groupFilter
                if (hasScript && scriptRenderer != null && bakedFilterLogCount < 3) {
                    bakedFilterLogCount++
                    RealTrainModRenewed.LOGGER.info(
                        "[BakedFilter:preferScript] hasScriptRenderedGroups={}",
                        scriptRenderer!!.hasScriptRenderedGroups()
                    )
                }
                if (hasScript && scriptRenderer != null && scriptRenderer!!.hasScriptRenderedGroups()) {
                    opaqueFilter = GroupPredicate { groupName: String? ->
                        (groupFilter == null || groupFilter.shouldRender(groupName))
                                && scriptRenderer!!.shouldRenderBakedGroup(groupName, false)
                    }
                    translucentFilter = GroupPredicate { groupName: String? ->
                        (groupFilter == null || groupFilter.shouldRender(groupName))
                                && scriptRenderer!!.shouldRenderBakedGroup(groupName, true)
                    }
                }
                // baked render 前に currentPass を 0 にリセットする。
                // スクリプトの最終 pass (emissive = 2) が残ったまま renderInternal を
                // 呼ぶと renderSelectedBatches 内で scriptPass>=2 と判定され、
                // emissiveTexture が本来不要なバッチにも適用されてしまう。
                if (scriptRenderer != null) {
                    scriptRenderer!!.clearRenderContext()
                }
                if (hasOpaqueBatches()) {
                    renderInternal(
                        poseStack,
                        buffer,
                        packedLight,
                        overlay,
                        false,
                        opaqueFilter,
                        groupTransform,
                        scriptRenderer,
                        entity
                    )
                }
                if (hasTranslucentBatches() || (scriptRenderer != null && scriptRenderer!!.hasAlphaPassContent())) {
                    renderInternal(
                        poseStack,
                        buffer,
                        packedLight,
                        overlay,
                        true,
                        translucentFilter,
                        groupTransform,
                        scriptRenderer,
                        entity
                    )
                }
                // 全不透明(script + baked)描画後に、溜めておいた半透明を最後に一括描画する。
                if (deferTrans) {
                    scriptRenderer!!.flushDeferredTranslucent(poseStack, buffer)
                    scriptRenderer!!.setDeferTranslucent(false)
                }
            } finally {
                if (deferTrans) {
                    scriptRenderer!!.setDeferTranslucent(false)
                }
                if (scriptRenderer != null) {
                    scriptRenderer!!.clearRenderContext()
                }
            }
        }

        @JvmOverloads
        fun render(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, overlay: Int,
            groupFilter: GroupPredicate? = null, groupTransform: GroupTransform? = null, entity: Any? = null
        ) {
            val hasScript = scriptEngine != null
            var scriptRendered = false
            val deferTrans = true
            try {
                if (scriptRenderer != null) {
                    scriptRenderer!!.resetRenderStatistics()
                }
                if (deferTrans && scriptRenderer != null) {
                    scriptRenderer!!.setDeferTranslucent(true)
                }
                if (hasScript) {
                    for (pass in 0..<LEGACY_SCRIPT_PASS_COUNT) {
                        if (pass >= 2 && scriptRenderer != null && !scriptRenderer!!.hasEmissivePassContent()) continue
                        // スクリプトが poseStack を破壊する事例 (rotate/translate を push/pop なしで多用、
                        // NaN を渡す等) に対する安全網。push/pop で囲んで corruption を局所化する。
                        poseStack.pushPose()
                        try {
                            scriptRendered =
                                scriptRendered or executeScript(poseStack, buffer, packedLight, overlay, pass, entity)
                        } finally {
                            try {
                                poseStack.popPose()
                            } catch (ignored: Throwable) {
                            }
                        }
                    }
                }
            } finally {
                if (scriptRenderer != null) {
                    scriptRenderer!!.clearRenderContext()
                }
            }
            var opaqueFilter = groupFilter
            var translucentFilter = groupFilter
            // baked render の filter 適用状態を一度だけ可視化する。
            if (hasScript && scriptRenderer != null && bakedFilterLogCount < 3) {
                bakedFilterLogCount++
                RealTrainModRenewed.LOGGER.info(
                    "[BakedFilter] hasScriptRenderedGroups={}",
                    scriptRenderer!!.hasScriptRenderedGroups()
                )
            }
            if (hasScript && scriptRenderer != null && scriptRenderer!!.hasScriptRenderedGroups()) {
                opaqueFilter = GroupPredicate { groupName: String? ->
                    (groupFilter == null || groupFilter.shouldRender(groupName))
                            && scriptRenderer!!.shouldRenderBakedGroup(groupName, false)
                }
                translucentFilter = GroupPredicate { groupName: String? ->
                    (groupFilter == null || groupFilter.shouldRender(groupName))
                            && scriptRenderer!!.shouldRenderBakedGroup(groupName, true)
                }
            }
            if (hasOpaqueBatches()) {
                renderInternal(
                    poseStack,
                    buffer,
                    packedLight,
                    overlay,
                    false,
                    opaqueFilter,
                    groupTransform,
                    scriptRenderer,
                    entity
                )
            }
            if (hasTranslucentBatches() || (scriptRenderer != null && scriptRenderer!!.hasAlphaPassContent())) {
                renderInternal(
                    poseStack,
                    buffer,
                    packedLight,
                    overlay,
                    true,
                    translucentFilter,
                    groupTransform,
                    scriptRenderer,
                    entity
                )
            }
            if (deferTrans && scriptRenderer != null) {
                scriptRenderer!!.flushDeferredTranslucent(poseStack, buffer)
                scriptRenderer!!.setDeferTranslucent(false)
            }
        }

        internal fun renderColorOverlay(
            poseStack: PoseStack, buffer: MultiBufferSource, overlay: Int,
            groupFilter: GroupPredicate?, red: Int, green: Int, blue: Int, alpha: Int
        ) {
            val surfaceBias = 0.0025f
            for (batch in batches) {
                if (groupFilter != null && !groupFilter.shouldRender(batch!!.groupName)) continue
                val texture = if (batch!!.texture != null) batch.texture else fallbackTexture()
                val consumer = buffer.getBuffer(
                    if (batch.translucent) TRAIN_ENTITY_TRANSLUCENT_NO_DEPTH.apply(texture) else RenderTypes.entityCutout(
                        texture
                    )
                )
                val pose = poseStack.last()
                val mat = pose.pose()
                val norm = pose.normal()
                val normalized = FloatArray(3)
                for (i in 0..<batch.vertexCount) {
                    val o = i * 8
                    var vx = batch.data!![o]
                    var vy = batch.data[o + 1]
                    var vz = batch.data[o + 2]
                    val nx = batch.data[o + 3]
                    val ny = batch.data[o + 4]
                    val nz = batch.data[o + 5]
                    val localLength = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                    if (localLength > 1.0E-6f) {
                        val scale = surfaceBias / localLength
                        vx += nx * scale
                        vy += ny * scale
                        vz += nz * scale
                    }
                    val tnx = norm.m00() * nx + norm.m10() * ny + norm.m20() * nz
                    val tny = norm.m01() * nx + norm.m11() * ny + norm.m21() * nz
                    val tnz = norm.m02() * nx + norm.m12() * ny + norm.m22() * nz
                    normalizeNormal(tnx, tny, tnz, normalized)
                    VertexWriter.addVertex(consumer, mat, vx, vy, vz)
                        .setColor(red, green, blue, alpha)
                        .setUv(batch.data[o + 6], batch.data[o + 7])
                        .setOverlay(overlay)
                        .setLight(0x00F000F0)
                        .setNormal(normalized[0], normalized[1], normalized[2])
                }
            }
        }

        private fun shouldSkipLegacyPlaceholderGroup(groupName: String?): kotlin.Boolean {
            if (groupName == null || groupName.isBlank()) {
                return false
            }
            val lower = groupName.trim { it <= ' ' }.lowercase()
            if (lower == "dest" && hasGroupNamed("dest0")) {
                return true
            }
            if (lower == "type" && hasGroupNamed("type0")) {
                return true
            }
            return lower == "lever" && (hasGroupNamed("L_F")
                    || hasGroupNamed("L_M")
                    || hasGroupNamed("L_B")
                    )
        }

        companion object {
            // RTM scripts use pass 0 (opaque), 1 (transparent), and "pass > 1" (emissive/fullbright).
            // Running more than 3 passes would repeat emissive content needlessly.
            private const val LEGACY_SCRIPT_PASS_COUNT = 3

            /** 台車・車輪・パンタ等(車体シェルでない)グループ名か。床下蓋のAABB計算から除外する。  */
            private fun isUnderTruckGroup(lowerGroupName: String?): kotlin.Boolean {
                if (lowerGroupName == null || lowerGroupName.isBlank()) return true
                return lowerGroupName.contains("bogie") || lowerGroupName.contains("truck")
                        || lowerGroupName.contains("daisya") || lowerGroupName.contains("台車")
                        || lowerGroupName.contains("wheel") || lowerGroupName.contains("sharin")
                        || lowerGroupName.contains("車輪") || lowerGroupName.contains("pant")
                        || lowerGroupName.contains("パンタ") || lowerGroupName.contains("rod")
                        || lowerGroupName.contains("axle") || lowerGroupName.contains("spring")
                        || lowerGroupName.contains("coupler") || lowerGroupName.contains("連結")
                        || lowerGroupName.contains("brake")
            }

            private fun capVertexBuf(
                vc: VertexConsumer, mat: Matrix4f, x: kotlin.Float, y: kotlin.Float, z: kotlin.Float,
                gray: Int, packedLight: Int, overlay: Int, nx: kotlin.Float, ny: kotlin.Float, nz: kotlin.Float
            ) {
                VertexWriter.addVertex(vc, mat, x, y, z)
                    .setColor(gray, gray, gray, 255)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(overlay)
                    .setLight(packedLight)
                    .setNormal(nx, ny, nz)
            }

            private fun buildBatchIndex(batches: MutableList<Batch?>): MutableMap<String?, MutableList<Batch?>?> {
                val index: MutableMap<String?, MutableList<Batch?>?> = HashMap<String?, MutableList<Batch?>?>()
                for (batch in batches) {
                    val key: String = normalizeBatchGroupName(batch!!.groupName)
                    index.computeIfAbsent(key) { ignored: kotlin.String? -> java.util.ArrayList<cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.Batch?>() }!!
                        .add(batch)
                }
                return index
            }

            private fun normalizeBatchGroupName(groupName: String?): String {
                return if (groupName == null) "" else groupName.trim { it <= ' ' }.lowercase()
            }

            private const val PASS_RECHECK_INTERVAL = 40 // 約1秒で再観測

            private fun softenNormalForVanilla(nx: kotlin.Float, ny: kotlin.Float, nz: kotlin.Float, out: FloatArray) {
                // Original RTM called glDisable(GL_LIGHTING) before rendering,
                // so all faces were at full brightness regardless of normal direction.
                // Using top-facing normal (0,1,0) ensures the maximum brightness
                // multiplier (1.0) is applied to every face, reproducing that behavior.
                out[0] = 0.0f
                out[1] = 1.0f
                out[2] = 0.0f
            }

            private fun resolveVertexLight(entity: Any?, lowerGroupName: String?, packedLight: Int): Int {
                if (entity is TrainEntity && lowerGroupName != null) {
                    if (entity.isInteriorLightOn && isLegacyInteriorSurfaceGroup(lowerGroupName)) {
                        return 0x00F000F0
                    }
                    if (isBakedDisplayEmissionGroup(lowerGroupName)) {
                        return 0x00F000F0
                    }
                }
                return packedLight
            }

            private fun isLegacyInteriorSurfaceGroup(groupName: String): Boolean {
                return groupName.contains("interior") ||
                    groupName.contains("inside") ||
                    groupName.contains("roomlight") ||
                    groupName.contains("room_light") ||
                    groupName.contains("cabinlight") ||
                    groupName.contains("cabin_light") ||
                    groupName.contains("cab_") ||
                    groupName.contains("fluorescent") ||
                    groupName.contains("ceiling_light") ||
                    groupName.contains("蛍光灯") ||
                    groupName.contains("室内灯") ||
                    groupName.contains("車内灯") ||
                    groupName.startsWith("内装") ||
                    groupName.contains("運転台") ||
                    groupName.contains("乗務員") ||
                    groupName.contains("天井") ||
                    groupName.contains("網棚") ||
                    groupName.contains("つり革") ||
                    groupName.contains("トイレ") ||
                    groupName == "isu"
            }

            private fun isBakedDisplayEmissionGroup(groupName: String): Boolean {
                return groupName.contains("rollsign") ||
                    groupName.contains("destination") ||
                    groupName.contains("display") ||
                    groupName.contains("案内表示器") ||
                    groupName.contains("行先") ||
                    groupName.contains("方向幕") ||
                    groupName.contains("行路表")
            }

            /**
             * packedLight から「フラット明るさ係数」(0..1) を計算する。ライトマップを使えない
             * 直接GL/VBO 経路で、周囲の明るさを setShaderColor 乗算で疑似再現するため。
             * バニラのライトマップ計算(Lightmap.updateLightTexture)の主要項のみを近似:
             * sky 寄与 = getBrightness(sky) * (getSkyDarken*0.95+0.05)、block 寄与 = getBrightness(block)。
             * 細かなフリッカ/ガンマ/暗黒効果は無視(フラット表示なので十分)。
             */
            private fun computeFlatBrightness(packedLight: Int): kotlin.Float {
                try {
                    val mc = Minecraft.getInstance()
                    val level = mc.level
                    if (level == null) return 1.0f
                    val dim = level.dimensionType()
                    val blockLevel = LightCoordsUtil.block(packedLight)
                    val skyLevel = LightCoordsUtil.sky(packedLight)
                    val skyMul = level.getSkyDarken() * 0.95f + 0.05f
                    val skyB = Lightmap.getBrightness(dim, skyLevel) * skyMul
                    val blockB = Lightmap.getBrightness(dim, blockLevel)
                    val b = max(skyB, blockB)
                    // 完全な暗黒で真っ黒にならないよう下限を少し残す(バニラも 0.04 程度のグレー混合がある)。
                    return Mth.clamp(b, 0.05f, 1.0f)
                } catch (ignored: Throwable) {
                    return 1.0f
                }
            }

            private fun normalizeNormal(nx: kotlin.Float, ny: kotlin.Float, nz: kotlin.Float, out: FloatArray) {
                val lenSq = nx * nx + ny * ny + nz * nz
                if (lenSq <= 1.0E-8f) {
                    out[0] = 0.0f
                    out[1] = 1.0f
                    out[2] = 0.0f
                    return
                }
                val invLen = (1.0 / sqrt(lenSq.toDouble())).toFloat()
                out[0] = nx * invLen
                out[1] = ny * invLen
                out[2] = nz * invLen
            }

            private fun hasActiveShaderPipeline(): kotlin.Boolean {
                val now = System.currentTimeMillis()
                if (now < shaderPipelineCacheUntilMillis) {
                    return shaderPipelineCacheValue
                }
                var active = false
                try {
                    val irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
                    val irisApi = irisApiClass.getMethod("getInstance").invoke(null)
                    val inUse = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi)
                    if (inUse is kotlin.Boolean) {
                        active = inUse
                    }
                } catch (ignored: Throwable) {
                }
                shaderPipelineCacheValue = active
                shaderPipelineCacheUntilMillis = now + 1000L
                return active
            }

            private fun shouldRenderReflectionOverlay(
                entity: Any?,
                batch: Batch?,
                lowerGroupName: String?,
                scriptTexture: kotlin.Boolean
            ): kotlin.Boolean {
                return false
            }

            private fun isGlassGroup(lowerGroupName: String): kotlin.Boolean {
                return lowerGroupName.contains("glass")
                        || lowerGroupName.contains("window")
                        || lowerGroupName.contains("wind")
            }

            private fun isLightGroup(lowerGroupName: String): kotlin.Boolean {
                return lowerGroupName.contains("light")
                        || lowerGroupName.contains("lamp")
                        || lowerGroupName.contains("marker")
            }

            private fun isLegacyPlainFullbrightGroup(lowerGroupName: String?): kotlin.Boolean {
                if (lowerGroupName == null || lowerGroupName.isBlank()) {
                    return false
                }
                return lowerGroupName.contains("light")
                        || lowerGroupName.contains("lamp")
                        || lowerGroupName.contains("marker")
                        || lowerGroupName.contains("interior")
                        || lowerGroupName.contains("roomlight")
                        || lowerGroupName.contains("room_light")
                        || lowerGroupName.contains("cabinlight")
                        || lowerGroupName.contains("cabin_light")
                        || lowerGroupName.contains("_ceil")
                        || lowerGroupName.contains("led_box")
                        || lowerGroupName.contains("led")
                        || lowerGroupName == "i_body"
                        || lowerGroupName == "inner"
            }

            private fun shouldUseGlassOnlyPass(batch: Batch?, lowerGroupName: String?): kotlin.Boolean {
                if (batch == null || lowerGroupName == null || !batch.translucent) {
                    return false
                }
                // ボディ全体を AlphaBlend 指定している蒸機やロッド物はここに入れない。
                // 明示的にガラス/窓/alpha グループとして切られているものだけを対象にする。
                return lowerGroupName == "alpha"
                        || lowerGroupName.startsWith("alpha_")
                        || lowerGroupName.contains("glass")
                        || lowerGroupName.contains("window")
                        || lowerGroupName.contains("wind")
            }

            private fun shouldSuppressPackSpecificShadowArtifact(
                entity: Any?,
                lowerGroupName: String?
            ): kotlin.Boolean {
                if (entity !is TrainEntity || lowerGroupName == null) {
                    return false
                }
                val vehicleId = entity.vehicleId
                if (vehicleId == null) {
                    return false
                }
                val lowerId = vehicleId.lowercase()
                val def = getById(vehicleId)
                val lowerModelFile = if (def == null || def.getModelFile() == null)
                    ""
                else
                    def.getModelFile().replace('\\', '/').lowercase()
                // T-ONREC E131 パックは alpha / alpha_ グループの中に、窓ガラスではなく
                // 車体下へ大きく伸びる補助板が入っている。移植版ではこれが黒い「影板」として
                // 出てしまう。面単位の除外は bake 時に行い、ここでは丸ごと消さない。
                if (lowerId.startsWith("t-on_e131")
                    || lowerModelFile.startsWith("t-onrec/e131/")
                    || lowerModelFile.contains("/t-onrec/e131/")
                ) {
                    return false
                }
                if (lowerId.startsWith("baru_keikyu")
                    || lowerId.contains("keikyu")
                    || lowerModelFile.startsWith("baru_keikyu_")
                    || lowerModelFile.contains("/baru_keikyu_")
                ) {
                    return lowerGroupName == "shadow"
                            || lowerGroupName.startsWith("shadow_")
                            || lowerGroupName.endsWith("_shadow")
                }
                if ((lowerId.startsWith("d51-498") || lowerModelFile.startsWith("d51-498"))
                    && lowerGroupName == "fl"
                ) {
                    return true
                }
                return false
            }

            private fun isInteriorGroup(lowerGroupName: String): kotlin.Boolean {
                return lowerGroupName.contains("seat")
                        || lowerGroupName.contains("chair")
                        || lowerGroupName.contains("bogie")
                        || lowerGroupName.contains("wheel")
                        || lowerGroupName.contains("pantograph")
                        || lowerGroupName.contains("under")
                        || lowerGroupName.contains("floor")
                        || lowerGroupName.contains("panel")
            }

            private fun isBodyGroup(lowerGroupName: String): kotlin.Boolean {
                return !lowerGroupName.isBlank()
            }

            private fun getDepthBias(
                batch: Batch?,
                lowerGroupName: String,
                scriptTexture: kotlin.Boolean
            ): kotlin.Float {
                if (batch == null || scriptTexture) {
                    return 0.0f
                }
                if (isCabControlGroup(lowerGroupName)) {
                    return 0.0f
                }
                if (isScriptDisplayGroup(lowerGroupName)) {
                    return 0.0002f
                }
                if (isLegacyDisplayGroup(lowerGroupName)) {
                    return 0.0002f
                }
                if (isLightGroup(lowerGroupName)) {
                    return 0.0010f
                }
                if (!batch.translucent) {
                    return 0.0f
                }
                if (lowerGroupName == "alpha") {
                    return 0.0012f
                }
                return 0.0f
            }

            private fun shouldForceLegacyAlphaCutout(
                batch: Batch?,
                lowerGroupName: String,
                scriptTexture: kotlin.Boolean
            ): kotlin.Boolean {
                if (batch == null || scriptTexture || !batch.translucent) {
                    return false
                }
                if (lowerGroupName.contains("mask") && !isLegacyDisplayGroup(lowerGroupName)) {
                    return true
                }
                // RTM 系 SL/D51 等: ボディテクスチャが AlphaBlend で登録されていても、
                // 実体はアルファテスト用（ロッドの隙間など）。ブレンドで描画すると深度書き込みが
                // 切れ、車体越しに反対側のパーツが透けて見える。
                // 明確な透過キーワード (glass/window/alpha/trans) を持たないグループは
                // カットアウト扱いに切り替える。
                if (isLegacyDisplayGroup(lowerGroupName) || isScriptDisplayGroup(lowerGroupName)) {
                    return false
                }
                val hasTransparencyKeyword = isLegacyTransparentGroupName(lowerGroupName)
                if (!hasTransparencyKeyword) {
                    return true
                }
                return false
            }

            private fun shouldForceDisplayCutout(
                batch: Batch?,
                lowerGroupName: String?,
                scriptTexture: kotlin.Boolean
            ): kotlin.Boolean {
                if (batch == null || scriptTexture || !batch.translucent) {
                    return false
                }
                return isLegacyDisplayGroup(lowerGroupName) || isScriptDisplayGroup(lowerGroupName)
            }

            private fun shouldForceCabCutout(
                batch: Batch?,
                lowerGroupName: String,
                scriptTexture: kotlin.Boolean
            ): kotlin.Boolean {
                if (batch == null || scriptTexture || !batch.translucent) {
                    return false
                }
                return isCabControlGroup(lowerGroupName) || lowerGroupName == "cabpanel"
            }

            private fun shouldForceShaderSafeCutout(
                entity: Any?,
                batch: Batch?,
                lowerGroupName: String,
                scriptTexture: kotlin.Boolean
            ): kotlin.Boolean {
                if (batch == null || scriptTexture || !batch.translucent || !hasActiveShaderPipeline()) {
                    return false
                }
                if ((entity !is TrainEntity) && (entity !is LargeRailCoreBlockEntity) && (entity !is InstalledObjectBlockEntity)) {
                    return false
                }
                if (isGlassGroup(lowerGroupName) || isLegacyDisplayGroup(lowerGroupName)) {
                    return false
                }
                return true
            }

            private fun isLegacyDisplayGroup(lowerGroupName: String?): kotlin.Boolean {
                if (lowerGroupName == null || lowerGroupName.isBlank()) {
                    return false
                }
                return lowerGroupName == "dest"
                        || lowerGroupName == "type"
                        || lowerGroupName.startsWith("dest") && lowerGroupName.length > 4 && lowerGroupName.substring(4)
                    .chars().allMatch(
                        IntPredicate { codePoint: Int -> Character.isDigit(codePoint) }) || lowerGroupName.startsWith("type") && lowerGroupName.length > 4 && lowerGroupName.substring(
                    4
                ).chars().allMatch(
                    IntPredicate { codePoint: Int -> Character.isDigit(codePoint) })
            }

            private fun isScriptDisplayGroup(lowerGroupName: String?): kotlin.Boolean {
                if (lowerGroupName == null || lowerGroupName.isBlank()) {
                    return false
                }
                return lowerGroupName == "sign"
                        || lowerGroupName.startsWith("type_")
                        || lowerGroupName.matches("s_[td][ab]\\d+".toRegex())
            }

            private fun isCabControlGroup(lowerGroupName: String?): kotlin.Boolean {
                if (lowerGroupName == null || lowerGroupName.isBlank()) {
                    return false
                }
                return lowerGroupName == "f"
                        || lowerGroupName == "m"
                        || lowerGroupName == "b"
                        || lowerGroupName == "n"
                        || lowerGroupName == "eb"
                        || lowerGroupName.matches("b[1-7]".toRegex())
                        || lowerGroupName.matches("p[1-5]".toRegex())
            }

            private fun computeReflectionAlpha(
                pose: Matrix4f,
                nx: kotlin.Float,
                ny: kotlin.Float,
                nz: kotlin.Float,
                x: kotlin.Float,
                y: kotlin.Float,
                z: kotlin.Float,
                glass: kotlin.Boolean
            ): Int {
                var nx = nx
                var ny = ny
                var nz = nz
                val normalLenSq = nx * nx + ny * ny + nz * nz
                if (normalLenSq <= 1.0E-8f) {
                    return 0
                }
                val normalInvLen = (1.0 / sqrt(normalLenSq.toDouble())).toFloat()
                nx *= normalInvLen
                ny *= normalInvLen
                nz *= normalInvLen

                var vx = pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30()
                var vy = pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31()
                var vz = pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32()
                val viewLenSq = vx * vx + vy * vy + vz * vz
                if (viewLenSq <= 1.0E-8f) {
                    return 0
                }
                val viewInvLen = (1.0 / sqrt(viewLenSq.toDouble())).toFloat()
                vx = -vx * viewInvLen
                vy = -vy * viewInvLen
                vz = -vz * viewInvLen

                var fresnel = 1.0f - max(0.0f, nx * vx + ny * vy + nz * vz)
                fresnel *= fresnel
                fresnel *= fresnel
                val skyBias = max(0.0f, ny) * 0.35f
                val strength = if (glass) 0.20f else 0.06f
                val alpha = (fresnel * 0.8f + skyBias) * strength
                val maxAlpha = if (glass) 52 else 24
                return min(maxAlpha, max(0, Math.round(alpha * 255.0f)))
            }
        }
    }

    internal class Batch(
        val order: Int,
        val groupName: String?,
        val texture: Identifier?,
        emissiveTextures: Array<Identifier?>?,
        val data: FloatArray?,
        val vertexCount: Int,
        val materialId: Int,
        val translucent: kotlin.Boolean,
        val minU: kotlin.Float,
        val maxU: kotlin.Float,
        val minV: kotlin.Float,
        val maxV: kotlin.Float
    ) {
        val groupNameLower: String
        val emissiveTextures: Array<Identifier?>

        /** マテリアル col の不透明度 (1.0=不透明)。半透明ガラス等は <1。描画時に色αへ乗算。  */
        var baseAlpha: kotlin.Float = 1.0f

        /** テクスチャが明確なガラス帯を持つ=本当の半透明。強制カットアウトを免除する。  */
        var glassTranslucent: kotlin.Boolean = false

        /** 材質またはグループ自体がガラス。RTM の不透明 pass では描画しない。 */
        var explicitGlassOnly: kotlin.Boolean = false

        /** RTM pass0(不透明描画)用のアルファテスト相当テクスチャ。  */
        var opaqueTexture: Identifier? = null

        /** RTM pass1(半透明)用の窓ガラスのみテクスチャ。  */
        var windowTexture: Identifier? = null

        // scriptTexture=false 時の事前計算結果。SL/通常列車は大半の batch で
        // scriptTexture=false なので毎フレームの string 操作 (contains/startsWith)
        // を 1 度の構築時計算に置換できる。
        var cachedForceCutoutNoScriptTex: kotlin.Boolean = false
        var cachedDepthBiasNoScriptTex: kotlin.Float = 0f
        var cachedComputed: kotlin.Boolean = false

        init {
            this.groupNameLower = if (groupName == null) "" else groupName.lowercase()
            this.emissiveTextures = if (emissiveTextures == null) arrayOfNulls<Identifier>(0) else emissiveTextures
        }

        fun emissiveTextureForPass(pass: Int): Identifier? {
            val index = pass - 2
            if (index < 0 || index >= emissiveTextures.size) {
                return null
            }
            return emissiveTextures[index]
        }
    }

    private class CachedModel(private val model: MqoModel?, estimatedBytes: Long, private var lastAccessNanos: Long) {
        private val estimatedBytes: Long

        init {
            this.estimatedBytes = max(1L, estimatedBytes)
        }

        fun model(): MqoModel? {
            return model
        }

        fun estimatedBytes(): Long {
            return estimatedBytes
        }

        fun lastAccessNanos(): Long {
            return lastAccessNanos
        }

        fun touch(now: Long) {
            this.lastAccessNanos = now
        }
    }

    class ScriptModel internal constructor(materialTextures: MutableList<Identifier?>) {
        @JvmField
        val textures: Array<ScriptMaterialTexture?>

        // 直前にレンダー中の renderer を保持。MqoModel.renderPreferScript / render が
        // executeScript 直前に setActiveRenderer() で差し替える。
        // RTM のレンダースクリプトは model.renderPart("groupName") で部品描画を要求するため、
        // ScriptModel 側からも renderer に処理を委譲できる必要がある。
        @Transient
        private var activeRenderer: TrainScriptSystem.ScriptModelRenderer? = null

        init {
            this.textures = arrayOfNulls<ScriptMaterialTexture>(materialTextures.size)
            for (i in materialTextures.indices) {
                this.textures[i] = ScriptMaterialTexture(MqoModelLoader.ScriptMaterial(materialTextures.get(i)!!))
            }
        }

        fun setActiveRenderer(renderer: TrainScriptSystem.ScriptModelRenderer?) {
            this.activeRenderer = renderer
        }

        // ---- 旧 RTM レンダースクリプト用 API ----
        fun renderPart(group: String?) {
            if (activeRenderer != null && group != null) activeRenderer!!.renderParts(group)
        }

        fun renderParts(groups: Any?) {
            if (activeRenderer != null && groups != null) activeRenderer!!.renderParts(groups)
        }

        fun renderAll() {
            // renderAll 相当: 空文字列で renderParts を呼ぶと全部品扱いの仕様。
            if (activeRenderer != null) activeRenderer!!.renderParts("*")
        }

        fun renderOnly(groups: Any?) {
            if (activeRenderer != null && groups != null) activeRenderer!!.renderParts(groups)
        }

        fun render(groups: Any?) {
            renderParts(groups)
        }
    }

    class ScriptMaterialTexture internal constructor(@JvmField var material: ScriptMaterial?)

    class ScriptMaterial internal constructor(texture: Identifier) {
        @JvmField
        var texture: Any?

        init {
            this.texture = ScriptTexture(texture)
        }
    }

    class ScriptTexture internal constructor(resource: Identifier) {
        @JvmField
        var namespace: String?
        @JvmField
        var domain: String?
        @JvmField
        var path: String?
        @JvmField
        var resourcePath: String?

        init {
            this.namespace = resource.getNamespace()
            this.domain = this.namespace
            this.path = resource.getPath()
            this.resourcePath = this.path
        }

        fun func_110624_b(): String? {
            return namespace
        }

        fun func_110623_a(): String? {
            return path
        }
    }
}

