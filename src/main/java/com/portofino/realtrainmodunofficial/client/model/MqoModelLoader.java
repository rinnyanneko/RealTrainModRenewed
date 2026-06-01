package com.portofino.realtrainmodunofficial.client.model;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.portofino.realtrainmodunofficial.Config;
import net.minecraft.client.renderer.GameRenderer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.portofino.realtrainmodunofficial.modelpack.VehicleModelPackManager;
import com.portofino.realtrainmodunofficial.script.TrainScriptSystem;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
// import org.jcodec.api.FrameGrab;
// import org.jcodec.common.io.ByteBufferSeekableByteChannel;
// import org.jcodec.common.model.Picture;
// import org.jcodec.scale.AWTUtil;
import org.w3c.dom.Node;

/**
 * Metasequoia (.mqo) loader aligned with legacy model library {@code MqoModel}: 0.01 vertex scale, triangulation and quad handling.
 */
public final class MqoModelLoader {
    private static final String TEXTURE_META_SEPARATOR = "|ptmeta=";
    private static final Pattern V_PATTERN = Pattern.compile("V\\((.+?)\\)");
    private static final Pattern UV_PATTERN = Pattern.compile("UV\\((.+?)\\)");
    private static final Pattern M_PATTERN = Pattern.compile("M\\((.+?)\\)");
    private static final Pattern TEX_PATTERN = Pattern.compile("tex\\(\"([^\"]+)\"\\)");
    /** MQO マテリアルの col(r g b a)。4番目がアルファ(不透明度)。RTM はガラス等をこの a<1 で半透明にする。 */
    private static final Pattern COL_PATTERN = Pattern.compile("col\\(\\s*([-0-9.]+)\\s+([-0-9.]+)\\s+([-0-9.]+)\\s+([-0-9.]+)\\s*\\)");
    private static final Object MODEL_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, CachedModel> MODEL_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<String> FAILED_MODEL_KEYS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> SOUND_SCRIPT_SOURCE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TextureInfo> TEXTURE_INFO_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ScriptTextureData> SCRIPT_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ResourceSearchResult> RESOURCE_SEARCH_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_SCRIPT_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SHADER_MOD_IDS = Set.of("iris", "oculus");
    private static volatile List<Path> sharedPackCandidates;
    private static ResourceLocation fallbackWhite;
    private static long modelCacheBytes;
    private static int bakedFilterLogCount = 0;
    private static volatile long shaderPipelineCacheUntilMillis;
    private static volatile boolean shaderPipelineCacheValue;
    private static final ResourceSearchResult MISSING_RESOURCE = new ResourceSearchResult(null, null, "__missing__");

    private static void logModelLoadDetail(String phase, String pattern, Object... args) {
        RealTrainModUnofficial.LOGGER.info("[ModelLoad:{}] " + pattern, prependArg(phase, args));
    }

    private static Object[] prependArg(String first, Object[] rest) {
        Object[] merged = new Object[rest.length + 1];
        merged[0] = first;
        System.arraycopy(rest, 0, merged, 1, rest.length);
        return merged;
    }

    private MqoModelLoader() {
    }

    public static MqoModel loadModelForRail(RailDefinition def) {
        if (def == null) return null;
        String key = "r|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode();
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        MqoModel model = packPath == null ? null
            : loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), false);
        if (model == null) {
            // レールモデルが解決できないと描画されず道床(砂利)だけ生成され「レールが無い」
            // 状態になる (ユーザー報告)。標準レール ModelRail_1067mm.mqo を mod jar から
            // フォールバック読み込みし、必ず鉄レールが出るようにする。
            model = loadFallbackRailModel();
        }
        if (model != null) {
            if (packPath != null) loadScriptForModel(model, packPath, def.getScriptPath());
            cacheModel(key, model);
        } else {
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    private static MqoModel fallbackRailModel;
    private static boolean fallbackRailAttempted;

    /** 標準 1067mm レールを mod jar から読み込むフォールバック。失敗しても null を返すだけ。 */
    private static MqoModel loadFallbackRailModel() {
        if (fallbackRailAttempted) return fallbackRailModel;
        fallbackRailAttempted = true;
        try {
            Path modJar = com.portofino.realtrainmodunofficial.BundledPackStore.getModJarPath();
            if (modJar != null) {
                fallbackRailModel = loadInternal(modJar, "ModelRail_1067mm.mqo",
                    java.util.Map.of("default", "textures/rail/largeRail.png"), false);
            }
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load fallback rail model", t);
        }
        return fallbackRailModel;
    }

    public static MqoModel loadModelForVehicle(VehicleDefinition def) {
        if (def == null) {
            RealTrainModUnofficial.LOGGER.warn("loadModelForVehicle: def is null");
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            RealTrainModUnofficial.LOGGER.warn("loadModelForVehicle: packPath is null for pack {}", def.getPackName());
            return null;
        }
        String scriptPath = resolveVehicleRenderScriptPath(packPath, def);
        String soundScriptPath = def.getSoundScriptPath() != null ? def.getSoundScriptPath() : "";
        // legacy script は init() で trainName/modelName ごとの差分を固定するため、車両ID単位で分離する
        String key = "v|" + def.getId() + "|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode() + "|" + scriptPath.hashCode() + "|" + soundScriptPath.hashCode() + "|" + def.isSmoothing();
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        RealTrainModUnofficial.LOGGER.debug("loadModelForVehicle: vehicleId={}, scriptPath='{}'", def.getId(), scriptPath);
        MqoModel model = loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), def.isSmoothing());
        if (model != null) {
            RealTrainModUnofficial.LOGGER.info("loadModelForVehicle: model loaded, loading script");
            loadScriptForModel(model, packPath, scriptPath, def.getId());
            cacheModel(key, model);
        } else {
            RealTrainModUnofficial.LOGGER.warn("loadModelForVehicle: model is null");
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    private static String resolveVehicleRenderScriptPath(Path packPath, VehicleDefinition def) {
        if (def == null) {
            return "";
        }
        String explicit = normalizeScriptPath(def.getScriptPath());
        if (!explicit.isBlank()) {
            return explicit;
        }
        String inferred = inferVehicleRenderScriptPath(packPath, def.getId(), def.getModelFile());
        if (!inferred.isBlank()) {
            RealTrainModUnofficial.LOGGER.info("Inferred legacy render script '{}' for vehicle {}", inferred, def.getId());
            return inferred;
        }
        return "";
    }

    private static String inferVehicleRenderScriptPath(Path packPath, String vehicleId, String modelFile) {
        if (packPath == null || !Files.exists(packPath)) {
            return "";
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String token : List.of(vehicleId, modelFile)) {
            String family = inferVehicleScriptFamily(token);
            if (family.isBlank()) {
                continue;
            }
            candidates.add("assets/minecraft/scripts/render_" + family + ".js");
            candidates.add("scripts/render_" + family + ".js");
            candidates.add("assets/minecraft/scripts/" + family + ".js");
            candidates.add("scripts/" + family + ".js");
        }
        for (String candidate : candidates) {
            try {
                if (Files.isDirectory(packPath)) {
                    Path file = resolveFilePathInPack(packPath, candidate);
                    if (file != null && Files.exists(file)) {
                        return candidate;
                    }
                } else {
                    try (ZipFile zip = new ZipFile(packPath.toFile())) {
                        if (findEntry(zip, candidate) != null) {
                            return candidate;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return "";
    }

    private static String inferVehicleScriptFamily(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.toLowerCase(Locale.ROOT);
        if (base.startsWith("modeltrain_")) {
            base = base.substring("modeltrain_".length());
        }
        base = base.replaceFirst("_(?:mc|mcp\\d+|p\\d+)$", "");
        return base;
    }

    public static ScriptEngine loadServerScriptForVehicle(VehicleDefinition def) {
        if (def == null || !def.hasServerScript()) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            return null;
        }
        String scriptPath = def.getServerScriptPath();
        String key = "server|" + def.getId() + "|" + def.getPackName() + "|" + scriptPath;
        String scriptSource = SOUND_SCRIPT_SOURCE_CACHE.computeIfAbsent(key, ignored -> {
            String loaded = loadStandaloneScriptSource(packPath, scriptPath);
            return loaded == null ? "" : loaded;
        });
        if (scriptSource == null || scriptSource.isBlank()) {
            return null;
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, scriptSource, def.getId());
    }

    public static ScriptEngine loadSoundScriptForVehicle(VehicleDefinition def) {
        if (def == null || !def.hasSoundScript()) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            return null;
        }
        String scriptPath = def.getSoundScriptPath();
        String key = "sound|" + def.getId() + "|" + def.getPackName() + "|" + scriptPath;
        String scriptSource = SOUND_SCRIPT_SOURCE_CACHE.computeIfAbsent(key, ignored -> {
            String loaded = loadStandaloneScriptSource(packPath, scriptPath);
            return loaded == null ? "" : loaded;
        });
        if (scriptSource == null || scriptSource.isBlank()) {
            return null;
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, scriptSource, def.getId());
    }

    public static MqoModel loadModelForVehiclePart(VehicleDefinition def, String modelFile, Map<String, String> textureOverrides) {
        if (def == null || modelFile == null || modelFile.isBlank()) return null;
        Map<String, String> tex = textureOverrides == null ? Map.of() : textureOverrides;
        String key = "vp|" + def.getPackName() + "|" + modelFile + "|" + tex.hashCode() + "|false";
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        MqoModel model = loadInternal(RailPackLoader.resolvePackPath(def.getPackName()), modelFile, tex, false);
        if (model != null) {
            cacheModel(key, model);
        }
        return model;
    }

    public static ResourceLocation resolvePackTexture(String packName, String texturePath) {
        if (packName == null || packName.isBlank() || texturePath == null || texturePath.isBlank()) {
            return fallbackTexture();
        }
        Path packPath = RailPackLoader.resolvePackPath(packName);
        if (packPath == null) {
            return fallbackTexture();
        }
        TextureBinding binding = TextureBinding.parse(texturePath);
        String cacheKey = packPath + "|" + binding.cacheKey();
        TextureInfo info = TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, key -> registerTextureFromZip(binding, new TextureOpener() {
            @Override
            public InputStream open(String rel) throws Exception {
                return openTexture(packPath, rel);
            }

            @Override
            public String getPackKey() {
                return packPath.toString();
            }
        }));
        return info.location;
    }

    public static MqoModel loadModelFromPack(String packName, String modelFile, Map<String, String> textureOverrides,
                                             String scriptPath, boolean smoothing) {
        if (packName == null || modelFile == null || modelFile.isBlank()) {
            return null;
        }
        Map<String, String> tex = textureOverrides == null ? Map.of() : textureOverrides;
        String key = "p|" + packName + "|" + modelFile + "|" + tex.hashCode() + "|" + smoothing + "|" + (scriptPath == null ? 0 : scriptPath.hashCode());
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        Path packPath = RailPackLoader.resolvePackPath(packName);
        if (packPath == null) {
            FAILED_MODEL_KEYS.add(key);
            return null;
        }
        MqoModel model = loadInternal(packPath, modelFile, tex, smoothing);
        if (model != null) {
            loadScriptForModel(model, packPath, scriptPath);
            cacheModel(key, model);
        } else {
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    private static MqoModel loadInternal(Path packPath, String modelFile, Map<String, String> textureOverrides, boolean smoothing) {
        if (packPath == null || !Files.exists(packPath)) return null;
        logModelLoadDetail("begin", "packPath={}, modelFile={}, smoothing={}, textureOverrides={}", packPath, modelFile, smoothing, textureOverrides);
        try {
            if (Files.isDirectory(packPath)) {
                ResourceSearchResult modelResource = findResource(modelFile, packPath);
                if (modelResource == null) {
                    RealTrainModUnofficial.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile);
                    return null;
                }
                Path modelPackPath = modelResource.packPath();
                if (modelPackPath == null) {
                    RealTrainModUnofficial.LOGGER.warn("Resolved MQO had no source pack for {} from {}", modelFile, packPath);
                    return null;
                }
                logModelLoadDetail("resolved", "modelFile={} resolvedPack={} filePath={} zipEntry={}", modelFile, modelPackPath, modelResource.filePath(), modelResource.zipEntryName());
                String lowerModelFile = modelFile.toLowerCase(Locale.ROOT);
                TextureOpener opener = new TextureOpener() {
                    @Override
                    public InputStream open(String rel) throws Exception {
                        return openTexture(modelPackPath, rel);
                    }
                    @Override
                    public String getPackKey() {
                        return modelPackPath.toString();
                    }
                };
                if (lowerModelFile.endsWith(".obj")) {
                    return bakeObj(readText(modelResource), opener, textureOverrides, smoothing);
                }
                String text = lowerModelFile.endsWith(".mqoz")
                    ? readCompressedMqo(modelResource)
                    : readText(modelResource);
                return bake(text, opener, textureOverrides, smoothing);
            }
            try (ZipFile zf = new ZipFile(packPath.toFile())) {
                ResourceSearchResult modelResource = findResource(modelFile, packPath);
                if (modelResource == null) {
                    RealTrainModUnofficial.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile);
                    return null;
                }
                Path modelPackPath = modelResource.packPath();
                if (modelPackPath == null) {
                    RealTrainModUnofficial.LOGGER.warn("Resolved MQO had no source pack for {} from {}", modelFile, packPath);
                    return null;
                }
                logModelLoadDetail("resolved", "modelFile={} resolvedPack={} filePath={} zipEntry={}", modelFile, modelPackPath, modelResource.filePath(), modelResource.zipEntryName());
                String lowerModelFile = modelFile.toLowerCase(Locale.ROOT);
                TextureOpener opener = new TextureOpener() {
                    @Override
                    public InputStream open(String rel) throws Exception {
                        return openTexture(modelPackPath, rel);
                    }
                    @Override
                    public String getPackKey() {
                        return modelPackPath.toString();
                    }
                };
                if (lowerModelFile.endsWith(".obj")) {
                    return bakeObj(readText(modelResource), opener, textureOverrides, smoothing);
                }
                String text = lowerModelFile.endsWith(".mqoz")
                    ? readCompressedMqo(modelResource)
                    : readText(modelResource);
                return bake(text, opener, textureOverrides, smoothing);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load MQO {} from {}", modelFile, packPath, e);
            return null;
        }
    }

    private static InputStream openTexture(Path packPath, String relative) throws IOException {
        if (packPath == null) {
            return null;
        }
        if (Files.isDirectory(packPath)) {
            Path file = resolveFilePathInPack(packPath, relative);
            if (file != null) {
                return Files.newInputStream(file);
            }
        } else {
            ZipFile zip = new ZipFile(packPath.toFile());
            ZipEntry entry = findEntry(zip, relative);
            if (entry != null) {
                InputStream raw = zip.getInputStream(entry);
                return new java.io.FilterInputStream(raw) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        zip.close();
                    }
                };
            }
            zip.close();
        }
        ResourceSearchResult fallback = findResource(relative, packPath);
        if (fallback == null || packPath.equals(fallback.packPath())) {
            return null;
        }
        return openResource(fallback);
    }

    private static String readCompressedMqo(Path path) throws java.io.IOException {
        try (ZipFile zf = new ZipFile(path.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zf.entries())) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mqo")) {
                    try (InputStream in = zf.getInputStream(entry)) {
                        return PackTextDecoder.readText(in);
                    }
                }
            }
        }
        throw new java.io.IOException("No .mqo entry found inside compressed MQO: " + path);
    }

    private static String readCompressedMqo(InputStream input) throws java.io.IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new BufferedInputStream(input))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mqo")) {
                    return PackTextDecoder.readText(zis);
                }
            }
        }
        throw new java.io.IOException("No .mqo entry found inside compressed MQO stream");
    }

    private static MqoModel getCachedModel(String key) {
        synchronized (MODEL_CACHE_LOCK) {
            CachedModel cached = MODEL_CACHE.get(key);
            if (cached == null) {
                return null;
            }
            cached.touch(System.nanoTime());
            return cached.model();
        }
    }

    private static void cacheModel(String key, MqoModel model) {
        if (key == null || model == null) {
            return;
        }
        synchronized (MODEL_CACHE_LOCK) {
            CachedModel previous = MODEL_CACHE.remove(key);
            if (previous != null) {
                modelCacheBytes -= previous.estimatedBytes();
            }
            CachedModel cached = new CachedModel(model, model.estimateMemoryBytes(), System.nanoTime());
            MODEL_CACHE.put(key, cached);
            modelCacheBytes += cached.estimatedBytes();
            evictModelCacheLocked();
        }
    }

    private static void evictModelCacheLocked() {
        long limitBytes = Math.max(16L, Config.MODEL_CACHE_LIMIT_MIB.get()) * 1024L * 1024L;
        long protectNanos = Math.max(0L, Config.MODEL_CACHE_PROTECT_SECONDS.get()) * 1_000_000_000L;
        if (modelCacheBytes <= limitBytes) {
            return;
        }
        long now = System.nanoTime();
        Iterator<Map.Entry<String, CachedModel>> iterator = MODEL_CACHE.entrySet().iterator();
        while (modelCacheBytes > limitBytes && iterator.hasNext()) {
            Map.Entry<String, CachedModel> entry = iterator.next();
            CachedModel cached = entry.getValue();
            if (protectNanos > 0L && now - cached.lastAccessNanos() < protectNanos) {
                continue;
            }
            modelCacheBytes -= cached.estimatedBytes();
            iterator.remove();
        }
    }

    private static Path resolveFilePathInPack(Path root, String relative) throws java.io.IOException {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        for (String candidatePath : candidateResourcePaths(norm)) {
            Path candidate = root.resolve(candidatePath);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        String leaf = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                if (name.equalsIgnoreCase(norm) || name.equalsIgnoreCase(leaf)) return file;
            }
        }
        return null;
    }

    private static String normalizeScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return "";
        }
        return scriptPath.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static ZipEntry findEntry(ZipFile zf, String relative) {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        for (String candidatePath : candidateResourcePaths(norm)) {
            ZipEntry direct = zf.getEntry(candidatePath);
            if (direct != null && !direct.isDirectory()) {
                return direct;
            }
        }
        String leaf = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
        String leafLower = leaf.toLowerCase(Locale.ROOT);
        java.util.Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (ze.isDirectory()) continue;
            String name = ze.getName().replace('\\', '/');
            if (name.equalsIgnoreCase(norm)) return ze;
            int slash = name.lastIndexOf('/');
            String shortName = slash >= 0 ? name.substring(slash + 1) : name;
            if (shortName.equalsIgnoreCase(leaf) || shortName.equalsIgnoreCase(leafLower)) return ze;
        }
        return null;
    }

    private static List<String> candidateResourcePaths(String norm) {
        List<String> candidates = new ArrayList<>();
        candidates.add(norm);
        candidates.add("assets/minecraft/" + norm);
        if (!norm.startsWith("textures/")) {
            candidates.add("assets/minecraft/textures/" + norm);
        }
        if (!norm.startsWith("models/") && looksLikeModelPath(norm)) {
            candidates.add("assets/minecraft/models/" + norm);
        }
        if (!norm.startsWith("scripts/") && looksLikeScriptPath(norm)) {
            candidates.add("assets/minecraft/scripts/" + norm);
        }
        return candidates;
    }

    private static boolean looksLikeModelPath(String norm) {
        String lower = norm.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mqo") || lower.endsWith(".mqoz") || lower.endsWith(".obj") || lower.endsWith(".ngto");
    }

    private static boolean looksLikeScriptPath(String norm) {
        String lower = norm.toLowerCase(Locale.ROOT);
        return lower.endsWith(".js");
    }

    private record ResourceSearchResult(Path packPath, Path filePath, String zipEntryName) {
    }

    private static ResourceSearchResult findResource(String relative, Path preferredPackPath) throws IOException {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String normalized = normalize(relative).replaceFirst("^/+", "");
        String leaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        String preferredKey = preferredPackPath == null ? "" : preferredPackPath.toAbsolutePath().normalize().toString();
        String cacheKey = preferredKey + "|" + normalized;
        ResourceSearchResult cached = RESOURCE_SEARCH_CACHE.get(cacheKey);
        if (cached != null) {
            logModelLoadDetail("resource-cache", "relative={} preferredPack={} hit={} resolvedPack={} filePath={} zipEntry={}",
                normalized, preferredPackPath, cached != MISSING_RESOURCE, cached.packPath(), cached.filePath(), cached.zipEntryName());
            return cached == MISSING_RESOURCE ? null : cached;
        }
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        if (preferredPackPath != null) {
            candidates.add(preferredPackPath.toAbsolutePath().normalize());
        }
        candidates.addAll(getSharedPackCandidates());
        for (Path candidate : candidates) {
            logModelLoadDetail("resource-scan", "relative={} candidatePack={}", normalized, candidate);
            ResourceSearchResult found = findResourceInPack(candidate, normalized);
            if (found != null) {
                logModelLoadDetail("resource-hit", "relative={} candidatePack={} filePath={} zipEntry={}",
                    normalized, candidate, found.filePath(), found.zipEntryName());
                RESOURCE_SEARCH_CACHE.put(cacheKey, found);
                return found;
            }
            if (!leaf.equals(normalized)) {
                found = findResourceInPack(candidate, leaf);
                if (found != null) {
                    logModelLoadDetail("resource-hit-leaf", "relative={} leaf={} candidatePack={} filePath={} zipEntry={}",
                        normalized, leaf, candidate, found.filePath(), found.zipEntryName());
                    RESOURCE_SEARCH_CACHE.put(cacheKey, found);
                    return found;
                }
            }
        }
        logModelLoadDetail("resource-miss", "relative={} preferredPack={} searchedPacks={}", normalized, preferredPackPath, candidates);
        RESOURCE_SEARCH_CACHE.put(cacheKey, MISSING_RESOURCE);
        return null;
    }

    private static ResourceSearchResult findResourceInPack(Path packPath, String relative) throws IOException {
        if (packPath == null || relative == null || relative.isBlank() || !Files.exists(packPath)) {
            return null;
        }
        if (Files.isDirectory(packPath)) {
            Path file = resolveFilePathInPack(packPath, relative);
            return file != null ? new ResourceSearchResult(packPath, file, null) : null;
        }
        try (ZipFile zip = new ZipFile(packPath.toFile())) {
            ZipEntry entry = findEntry(zip, relative);
            return entry != null ? new ResourceSearchResult(packPath, null, entry.getName()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static List<Path> getSharedPackCandidates() {
        List<Path> cached = sharedPackCandidates;
        if (cached != null) {
            return cached;
        }
        synchronized (MqoModelLoader.class) {
            if (sharedPackCandidates != null) {
                return sharedPackCandidates;
            }
            LinkedHashSet<Path> candidates = new LinkedHashSet<>();
            try {
                Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
                addPackCandidates(candidates, gameDir);
                addPackCandidates(candidates, gameDir.resolve("mods"));
                addPackCandidates(candidates, gameDir.resolve("content"));
                addPackCandidates(candidates, gameDir.resolve("vehicle_packs"));
                Path configDir = gameDir.resolve("config").resolve("realtrainmodunofficial");
                addPackCandidates(candidates, configDir);
                addPackCandidates(candidates, configDir.resolve("packs"));
                addPackCandidates(candidates, configDir.resolve("vehicle_packs"));
                addPackCandidates(candidates, configDir.resolve("rail_packs"));
                addPackCandidates(candidates, configDir.resolve("bundled_pack_cache"));
                try {
                    Path modJar = com.portofino.realtrainmodunofficial.BundledPackStore.getModJarPath();
                    if (modJar != null) {
                        candidates.add(modJar);
                    }
                } catch (Exception ignored) {}
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to build shared pack search list", e);
            }
            sharedPackCandidates = List.copyOf(candidates);
            return sharedPackCandidates;
        }
    }

    private static void addPackCandidates(LinkedHashSet<Path> candidates, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    if (Files.isDirectory(path) || isSupportedArchive(path)) {
                        candidates.add(path.toAbsolutePath().normalize());
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".zip") || fileName.endsWith(".jar");
    }

    private static String readText(ResourceSearchResult resource) throws IOException {
        if (resource.filePath() != null) {
            return PackTextDecoder.readText(resource.filePath());
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                throw new IOException("Missing zip entry: " + resource.zipEntryName());
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return PackTextDecoder.readText(in);
            }
        }
    }

    private static String readCompressedMqo(ResourceSearchResult resource) throws IOException {
        if (resource.filePath() != null) {
            return readCompressedMqo(resource.filePath());
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                throw new IOException("Missing zip entry: " + resource.zipEntryName());
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return readCompressedMqo(in);
            }
        }
    }

    private static InputStream openResource(ResourceSearchResult resource) throws IOException {
        if (resource == null) {
            return null;
        }
        if (resource.filePath() != null) {
            return Files.newInputStream(resource.filePath());
        }
        ZipFile zip = new ZipFile(resource.packPath().toFile());
        ZipEntry entry = zip.getEntry(resource.zipEntryName());
        if (entry == null) {
            zip.close();
            return null;
        }
        InputStream raw = zip.getInputStream(entry);
        return new java.io.FilterInputStream(raw) {
            @Override
            public void close() throws IOException {
                super.close();
                zip.close();
            }
        };
    }

    private static MqoModel bake(String mqoText, TextureOpener opener, Map<String, String> textureOverrides, boolean smoothing) throws Exception {
        List<String> materialOrder = new ArrayList<>();
        List<String> materialTexPaths = new ArrayList<>();
        List<Float> materialAlphas = new ArrayList<>();
        List<Vec3> currentVerts = new ArrayList<>();
        // key = groupName + "|" + matKey so each object×material pair is a separate batch
        Map<String, BatchBuilder> byGroup = new LinkedHashMap<>();
        int mirrorType = -1;
        int braceType = -1;
        String currentGroup = "default";
        float currentFacetAngle = 59.5F;
        Pattern OBJ_NAME = Pattern.compile("Object\\s+\"([^\"]*)\"");

        String[] lines = mqoText.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.equals("{")) continue;
            if (line.startsWith("}")) {
                braceType = -1;
                continue;
            }
            if (braceType >= 0) {
                if (braceType == 1) {
                    Vec3 v = parseVertexLine(line);
                    if (v != null) currentVerts.add(v);
                } else if (braceType == 2) {
                    addFaceLine(line, currentVerts, materialOrder, materialTexPaths, materialAlphas, textureOverrides, opener, mirrorType, currentGroup, currentFacetAngle, byGroup);
                } else if (braceType == 3) {
                    String[] tok = line.split("\\s+");
                    if (tok.length > 0) {
                        String name = tok[0].replace("\"", "");
                        if (!name.isBlank()) {
                            materialOrder.add(name);
                            Matcher texMatcher = TEX_PATTERN.matcher(line);
                            materialTexPaths.add(texMatcher.find() ? texMatcher.group(1) : null);
                            Matcher colMatcher = COL_PATTERN.matcher(line);
                            float matAlpha = 1.0F;
                            if (colMatcher.find()) {
                                try { matAlpha = Float.parseFloat(colMatcher.group(4)); } catch (NumberFormatException ignored) {}
                            }
                            materialAlphas.add(matAlpha);
                        }
                    }
                }
                continue;
            }
            if (line.startsWith("Material ")) { braceType = 3; continue; }
            if (line.startsWith("vertex ")) { currentVerts.clear(); braceType = 1; continue; }
            if (line.startsWith("face ")) { braceType = 2; continue; }
            if (line.startsWith("Object ")) {
                mirrorType = -1;
                currentFacetAngle = 59.5F;
                Matcher m = OBJ_NAME.matcher(line);
                currentGroup = m.find() ? m.group(1) : "default";
                continue;
            }
            if (line.startsWith("facet ")) {
                String[] p = line.split("\\s+");
                if (p.length > 1) {
                    try {
                        currentFacetAngle = Float.parseFloat(p[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                continue;
            }
            if (line.startsWith("mirror_axis ")) {
                String[] p = line.split("\\s+");
                if (p.length > 1) {
                    int axis = Integer.parseInt(p[1]);
                    mirrorType = axis == 1 ? 0 : axis == 2 ? 1 : axis == 3 ? 2 : -1;
                }
            }
        }

        List<Batch> out = new ArrayList<>();
        for (BatchBuilder bb : byGroup.values()) {
            if (!bb.positions.isEmpty()) out.add(bb.bake(smoothing));
        }
        List<ResourceLocation> materialTextures = new ArrayList<>(materialOrder.size());
        for (int i = 0; i < materialOrder.size(); i++) {
            materialTextures.add(resolveTexture((byte) i, materialOrder, materialTexPaths, textureOverrides, opener).location);
        }
        return new MqoModel(out, materialTextures);
    }

    private static MqoModel bakeObj(String objText, TextureOpener opener, Map<String, String> textureOverrides, boolean smoothing) throws Exception {
        List<Vec3> vertices = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        Map<String, String> materialTextures = new HashMap<>();
        Map<String, BatchBuilder> byGroup = new LinkedHashMap<>();
        String currentGroup = "default";
        String currentMaterial = "default";

        for (String raw : objText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("mtllib ")) {
                materialTextures.putAll(loadObjMaterialLibrary(line.substring(7).trim(), opener));
                continue;
            }
            if (line.startsWith("o ") || line.startsWith("g ")) {
                String name = line.substring(2).trim();
                currentGroup = name.isBlank() ? "default" : name;
                continue;
            }
            if (line.startsWith("usemtl ")) {
                String name = line.substring(7).trim();
                currentMaterial = name.isBlank() ? "default" : name;
                continue;
            }
            if (line.startsWith("v ")) {
                String[] parts = line.substring(2).trim().split("\\s+");
                if (parts.length >= 3) {
                    vertices.add(new Vec3(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2])
                    ));
                }
                continue;
            }
            if (line.startsWith("vt ")) {
                String[] parts = line.substring(3).trim().split("\\s+");
                if (parts.length >= 2) {
                    texCoords.add(new float[]{
                        Float.parseFloat(parts[0]),
                        1.0F - Float.parseFloat(parts[1])
                    });
                }
                continue;
            }
            if (line.startsWith("vn ")) {
                String[] parts = line.substring(3).trim().split("\\s+");
                if (parts.length >= 3) {
                    Vector3f normal = new Vector3f(
                        Float.parseFloat(parts[0]),
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2])
                    );
                    if (normal.lengthSquared() > 1.0E-8F) {
                        normal.normalize();
                    }
                    normals.add(normal);
                }
                continue;
            }
            if (!line.startsWith("f ")) {
                continue;
            }

            ObjFaceVertex[] faceVertices = parseObjFace(line.substring(2).trim(), vertices, texCoords, normals);
            if (faceVertices.length < 3) {
                continue;
            }

            TextureInfo textureInfo = resolveObjTexture(currentMaterial, materialTextures, textureOverrides, opener);
            float[] uvBounds = flattenUvs(faceVertices);
            float avgY = 0f;
            for (ObjFaceVertex fv : faceVertices) avgY += (float) fv.position().y;
            avgY /= faceVertices.length;
            boolean translucent = shouldTreatFaceAsTranslucent(textureInfo, currentGroup, uvBounds, faceVertices.length, avgY);
            int materialId = currentMaterial.hashCode() & 0x7FFFFFFF;
            String batchKey = currentGroup + "|" + currentMaterial + "|" + translucent;
            final String batchGroupName = currentGroup;
            BatchBuilder bb = byGroup.computeIfAbsent(batchKey,
                k -> new BatchBuilder(byGroup.size(), batchGroupName, textureInfo.location, textureInfo.emissiveTextures, materialId, translucent, 60.0F));

            if (faceVertices.length == 4) {
                emitObjQuad(faceVertices[0], faceVertices[1], faceVertices[2], faceVertices[3], bb);
            } else {
                for (int i = 1; i < faceVertices.length - 1; i++) {
                    emitObjTri(faceVertices[0], faceVertices[i], faceVertices[i + 1], bb);
                }
            }
        }

        List<Batch> out = new ArrayList<>();
        LinkedHashSet<ResourceLocation> uniqueTextures = new LinkedHashSet<>();
        for (BatchBuilder bb : byGroup.values()) {
            if (!bb.positions.isEmpty()) {
                Batch batch = bb.bake(smoothing);
                out.add(batch);
                uniqueTextures.add(batch.texture);
            }
        }
        return new MqoModel(out, new ArrayList<>(uniqueTextures));
    }

    private static ObjFaceVertex[] parseObjFace(String faceSpec, List<Vec3> vertices, List<float[]> texCoords, List<Vector3f> normals) {
        String[] parts = faceSpec.split("\\s+");
        List<ObjFaceVertex> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] indices = part.split("/", -1);
            int vertexIndex = resolveObjIndex(indices.length > 0 ? indices[0] : "", vertices.size());
            if (vertexIndex < 0 || vertexIndex >= vertices.size()) {
                continue;
            }
            float u = 0.0F;
            float v = 0.0F;
            if (indices.length > 1 && !indices[1].isBlank()) {
                int texIndex = resolveObjIndex(indices[1], texCoords.size());
                if (texIndex >= 0 && texIndex < texCoords.size()) {
                    float[] uv = texCoords.get(texIndex);
                    u = uv[0];
                    v = uv[1];
                }
            }
            Vector3f normal = null;
            if (indices.length > 2 && !indices[2].isBlank()) {
                int normalIndex = resolveObjIndex(indices[2], normals.size());
                if (normalIndex >= 0 && normalIndex < normals.size()) {
                    normal = new Vector3f(normals.get(normalIndex));
                }
            }
            out.add(new ObjFaceVertex(vertices.get(vertexIndex), u, v, normal));
        }
        return out.toArray(ObjFaceVertex[]::new);
    }

    private static int resolveObjIndex(String token, int size) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        int index = Integer.parseInt(token.trim());
        return index > 0 ? index - 1 : size + index;
    }

    private static float[] flattenUvs(ObjFaceVertex[] vertices) {
        float[] out = new float[vertices.length * 2];
        for (int i = 0; i < vertices.length; i++) {
            out[i * 2] = vertices[i].u();
            out[i * 2 + 1] = vertices[i].v();
        }
        return out;
    }

    private static void emitObjQuad(ObjFaceVertex v0, ObjFaceVertex v1, ObjFaceVertex v2, ObjFaceVertex v3, BatchBuilder bb) {
        Vector3f normal = chooseFaceNormal(v0, v1, v2, v3);
        if (!bb.markFace(
            new Vec3[]{v0.position(), v1.position(), v2.position(), v3.position()},
            new float[]{v0.u(), v0.v(), v1.u(), v1.v(), v2.u(), v2.v(), v3.u(), v3.v()})) {
            return;
        }
        putObjVertex(bb, v0, normal);
        putObjVertex(bb, v1, normal);
        putObjVertex(bb, v2, normal);
        putObjVertex(bb, v3, normal);
    }

    private static void emitObjTri(ObjFaceVertex v0, ObjFaceVertex v1, ObjFaceVertex v2, BatchBuilder bb) {
        Vector3f normal = chooseFaceNormal(v0, v1, v2, null);
        if (!bb.markFace(
            new Vec3[]{v0.position(), v1.position(), v2.position(), v2.position()},
            new float[]{v0.u(), v0.v(), v1.u(), v1.v(), v2.u(), v2.v(), v2.u(), v2.v()})) {
            return;
        }
        putObjVertex(bb, v0, normal);
        putObjVertex(bb, v1, normal);
        putObjVertex(bb, v2, normal);
        putObjVertex(bb, v2, normal);
    }

    private static void putObjVertex(BatchBuilder bb, ObjFaceVertex vertex, Vector3f fallbackNormal) {
        Vector3f normal = vertex.normal() != null ? new Vector3f(vertex.normal()) : new Vector3f(fallbackNormal);
        if (normal.lengthSquared() <= 1.0E-8F) {
            normal.set(0.0F, 1.0F, 0.0F);
        } else {
            normal.normalize();
        }
        bb.put(vertex.position(), normal, vertex.u(), vertex.v());
    }

    private static Vector3f chooseFaceNormal(ObjFaceVertex v0, ObjFaceVertex v1, ObjFaceVertex v2, ObjFaceVertex v3) {
        Vector3f supplied = averageSuppliedNormals(v0, v1, v2, v3);
        if (supplied != null) {
            return supplied;
        }
        Vector3f e1 = new Vector3f((float) (v1.position().x - v0.position().x), (float) (v1.position().y - v0.position().y), (float) (v1.position().z - v0.position().z));
        Vector3f e2 = new Vector3f((float) (v2.position().x - v0.position().x), (float) (v2.position().y - v0.position().y), (float) (v2.position().z - v0.position().z));
        Vector3f normal = e1.cross(e2);
        if (normal.lengthSquared() <= 1.0E-8F && v3 != null) {
            e2.set((float) (v3.position().x - v0.position().x), (float) (v3.position().y - v0.position().y), (float) (v3.position().z - v0.position().z));
            normal = e1.cross(e2);
        }
        if (normal.lengthSquared() <= 1.0E-8F) {
            normal.set(0.0F, 1.0F, 0.0F);
        } else {
            normal.normalize();
        }
        return normal;
    }

    private static Vector3f averageSuppliedNormals(ObjFaceVertex... vertices) {
        Vector3f sum = new Vector3f();
        int count = 0;
        for (ObjFaceVertex vertex : vertices) {
            if (vertex != null && vertex.normal() != null) {
                sum.add(vertex.normal());
                count++;
            }
        }
        if (count == 0 || sum.lengthSquared() <= 1.0E-8F) {
            return null;
        }
        sum.normalize();
        return sum;
    }

    private static TextureInfo resolveObjTexture(String materialName, Map<String, String> materialTextures,
                                                 Map<String, String> textureOverrides, TextureOpener opener) throws Exception {
        String path = null;
        if (materialName != null && textureOverrides.containsKey(materialName)) {
            path = textureOverrides.get(materialName);
        }
        if ((path == null || path.isBlank()) && textureOverrides.containsKey("default")) {
            path = textureOverrides.get("default");
        }
        // OBJ も MQO 同様、JSON overrides を mtl 由来のパスより優先する。
        if ((path == null || path.isBlank()) && !textureOverrides.isEmpty()) {
            path = textureOverrides.values().iterator().next();
        }
        if ((path == null || path.isBlank()) && materialName != null) {
            path = materialTextures.get(materialName);
        }
        if ((path == null || path.isBlank()) && !materialTextures.isEmpty()) {
            path = materialTextures.values().iterator().next();
        }
        if (path == null || path.isBlank()) {
            path = "textures/misc/white.png";
        }
        TextureBinding binding = TextureBinding.parse(path);
        String cacheKey = opener.getPackKey() + "|" + binding.cacheKey();
        logModelLoadDetail("texture-resolve-obj", "materialName={} resolvedPath={} cacheKey={}", materialName, path, cacheKey);
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, k -> registerTextureFromZip(binding, opener));
    }

    private static Map<String, String> loadObjMaterialLibrary(String materialFile, TextureOpener opener) {
        Map<String, String> materials = new HashMap<>();
        if (materialFile == null || materialFile.isBlank()) {
            return materials;
        }
        try (InputStream input = opener.open(materialFile)) {
            if (input == null) {
                return materials;
            }
            String current = null;
            for (String raw : PackTextDecoder.readText(input).split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("newmtl ")) {
                    current = line.substring(7).trim();
                    continue;
                }
                if (current == null) {
                    continue;
                }
                if (line.startsWith("map_Kd ")) {
                    materials.put(current, line.substring(7).trim());
                } else if (line.startsWith("map_d ")) {
                    materials.putIfAbsent(current, line.substring(6).trim() + TEXTURE_META_SEPARATOR + "alphablend");
                }
            }
        } catch (Exception ignored) {
        }
        return materials;
    }

    private record ObjFaceVertex(Vec3 position, float u, float v, Vector3f normal) {
    }

    private static Vec3 parseVertexLine(String line) {
        String[] t = line.split("\\s+");
        try {
            if (t.length == 2) {
                float x = Float.parseFloat(t[0]) * 0.01f;
                float y = Float.parseFloat(t[1]) * 0.01f;
                return new Vec3(x, y, 0);
            }
            if (t.length >= 3) {
                float x = Float.parseFloat(t[0]) * 0.01f;
                float y = Float.parseFloat(t[1]) * 0.01f;
                float z = Float.parseFloat(t[2]) * 0.01f;
                return new Vec3(x, y, z);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static void addFaceLine(
        String line,
        List<Vec3> verts,
        List<String> materialOrder,
        List<String> materialTexPaths,
        List<Float> materialAlphas,
        Map<String, String> textureOverrides,
        TextureOpener opener,
        int mirrorType,
        String groupName,
        float facetAngle,
        Map<String, BatchBuilder> byGroup
    ) throws Exception {
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0) return;
        int vertexCount = Integer.parseInt(tokens[0]);
        if (vertexCount < 3) return;
        byte matId = (byte) parseMaterialId(line);
        TextureInfo textureInfo = resolveTexture(matId, materialOrder, materialTexPaths, textureOverrides, opener);
        float matAlpha = (matId & 0xFF) < materialAlphas.size() ? materialAlphas.get(matId & 0xFF) : 1.0F;
        int matKey = matId & 0xFF;
        String vi = matchGroup(V_PATTERN, line);
        String uv = matchGroup(UV_PATTERN, line);
        if (vi == null) return;
        String[] vidx = vi.trim().split("\\s+");
        float[] uvs = parseUv(uv, vertexCount);
        float avgY = 0f;
        {
            int cnt = Math.min(vertexCount, vidx.length);
            for (int i = 0; i < cnt; i++) {
                try { avgY += (float) verts.get(Integer.parseInt(vidx[i])).y; } catch (Exception ignored) {}
            }
            if (cnt > 0) avgY /= cnt;
        }
        // マテリアル col の a<1 = ガラス等の半透明。グループ名に依らず半透明描画し、その不透明度を適用する。
        boolean translucent = matAlpha < 0.99F
            || shouldTreatFaceAsTranslucent(textureInfo, groupName, uvs, vertexCount, avgY);
        String batchKey = groupName + "|" + matKey + "|" + translucent;
        int batchOrder = byGroup.size();
        float baseAlpha = matAlpha;
        BatchBuilder bb = byGroup.computeIfAbsent(batchKey, k -> {
            BatchBuilder b = new BatchBuilder(batchOrder, groupName, textureInfo.location, textureInfo.emissiveTextures, matKey, translucent, facetAngle);
            b.baseAlpha = baseAlpha;
            b.glassTranslucent = textureInfo.hasGlassBand;
            b.opaqueTexture = textureInfo.opaqueLocation;
            return b;
        });

        if (vertexCount == 4) {
            addQuad(verts, vidx, uvs, matId, bb, mirrorType);
        } else {
            addPolygonFan(verts, vidx, uvs, vertexCount, bb, mirrorType);
        }
    }

    private static void addQuad(List<Vec3> verts, String[] vidx, float[] uvs, byte matId, BatchBuilder bb, int mirrorType) {
        int[] ix = new int[4];
        for (int i = 0; i < 4; i++) ix[i] = Integer.parseInt(vidx[i]);
        Vec3[] p = new Vec3[4];
        float[] u = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            int si = 3 - i;
            p[si] = verts.get(ix[i]);
            if (uvs != null) {
                u[si] = uvs[i * 2];
                v[si] = uvs[i * 2 + 1];
            }
        }
        emitQuad(p[0], p[1], p[2], p[3], u[0], v[0], u[1], v[1], u[2], v[2], u[3], v[3], bb, mirrorType);
    }

    private static void emitQuad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                  float u0, float v0, float u1, float v1,
                                  float u2, float v2, float u3, float v3,
                                  BatchBuilder bb, int mirrorType) {
        if (!bb.markFace(new Vec3[]{p0, p1, p2, p3}, new float[]{u0, v0, u1, v1, u2, v2, u3, v3})) {
            return;
        }
        Vector3f e1 = new Vector3f((float) (p1.x - p0.x), (float) (p1.y - p0.y), (float) (p1.z - p0.z));
        Vector3f e2 = new Vector3f((float) (p2.x - p0.x), (float) (p2.y - p0.y), (float) (p2.z - p0.z));
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() > 1.0e-8f) n.normalize();
        else n.set(0, 1, 0);
        bb.put(p0, n, u0, v0);
        bb.put(p1, n, u1, v1);
        bb.put(p2, n, u2, v2);
        bb.put(p3, n, u3, v3);
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(new Vec3[]{p0, p1, p2, p3}, mirrorType)) {
            Vec3 mp0 = mirror(p0, mirrorType);
            Vec3 mp3 = mirror(p3, mirrorType);
            Vec3 mp2 = mirror(p2, mirrorType);
            Vec3 mp1 = mirror(p1, mirrorType);
            if (!bb.markFace(new Vec3[]{mp0, mp3, mp2, mp1}, new float[]{u0, v0, u3, v3, u2, v2, u1, v1})) {
                return;
            }
            Vector3f mn = mirrorN(n, mirrorType);
            bb.put(mp0, mn, u0, v0);
            bb.put(mp3, mn, u3, v3);
            bb.put(mp2, mn, u2, v2);
            bb.put(mp1, mn, u1, v1);
        }
    }

    private static void addPolygonFan(List<Vec3> verts, String[] vidx, float[] uvs, int vertexCount, BatchBuilder bb, int mirrorType) {
        Vec3[] p = new Vec3[vertexCount];
        float[] localU = new float[vertexCount];
        float[] localV = new float[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            int si = vertexCount - 1 - i;
            p[si] = verts.get(Integer.parseInt(vidx[i]));
            if (uvs != null) {
                localU[si] = uvs[i * 2];
                localV[si] = uvs[i * 2 + 1];
            }
        }

        for (int i = 1; i < vertexCount - 1; i++) {
            emitTri(
                p[0], p[i], p[i + 1],
                uvs == null ? 0.0F : localU[0], uvs == null ? 0.0F : localV[0],
                uvs == null ? 0.0F : localU[i], uvs == null ? 0.0F : localV[i],
                uvs == null ? 0.0F : localU[i + 1], uvs == null ? 0.0F : localV[i + 1],
                bb, mirrorType
            );
        }
    }

    private static void emitTri(Vec3 p0, Vec3 p1, Vec3 p2, float u0, float v0, float u1, float v1, float u2, float v2, BatchBuilder bb, int mirrorType) {
        if (!bb.markFace(new Vec3[]{p0, p1, p2, p2}, new float[]{u0, v0, u1, v1, u2, v2, u2, v2})) {
            return;
        }
        Vector3f e1 = new Vector3f((float) (p1.x - p0.x), (float) (p1.y - p0.y), (float) (p1.z - p0.z));
        Vector3f e2 = new Vector3f((float) (p2.x - p0.x), (float) (p2.y - p0.y), (float) (p2.z - p0.z));
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() > 1.0e-8f) n.normalize();
        else n.set(0, 1, 0);
        // QUADSモードは4頂点/面が必要 → 3頂点の三角形は縮退クワッドとして扱う (v0,v1,v2,v2)
        bb.put(p0, n, u0, v0);
        bb.put(p1, n, u1, v1);
        bb.put(p2, n, u2, v2);
        bb.put(p2, n, u2, v2);
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(new Vec3[]{p0, p1, p2}, mirrorType)) {
            Vec3 mp0 = mirror(p0, mirrorType);
            Vec3 mp2 = mirror(p2, mirrorType);
            Vec3 mp1 = mirror(p1, mirrorType);
            if (!bb.markFace(new Vec3[]{mp0, mp2, mp1, mp1}, new float[]{u0, v0, u2, v2, u1, v1, u1, v1})) {
                return;
            }
            Vector3f mn = mirrorN(n, mirrorType);
            bb.put(mp0, mn, u0, v0);
            bb.put(mp2, mn, u2, v2);
            bb.put(mp1, mn, u1, v1);
            bb.put(mp1, mn, u1, v1);
        }
    }

    private static Vec3 mirror(Vec3 p, int type) {
        float x = (float) p.x;
        float y = (float) p.y;
        float z = (float) p.z;
        float[] m = switch (type) {
            case 0 -> new float[]{-1, 1, 1};
            case 1 -> new float[]{1, -1, 1};
            default -> new float[]{1, 1, -1};
        };
        return new Vec3(x * m[0], y * m[1], z * m[2]);
    }

    private static Vector3f mirrorN(Vector3f n, int type) {
        float[] m = switch (type) {
            case 0 -> new float[]{-1, 1, 1};
            case 1 -> new float[]{1, -1, 1};
            default -> new float[]{1, 1, -1};
        };
        Vector3f o = new Vector3f(n.x * m[0], n.y * m[1], n.z * m[2]);
        if (o.lengthSquared() > 1.0e-8f) o.normalize();
        return o;
    }

    private static boolean isFaceOnMirrorPlane(Vec3[] points, int mirrorType) {
        if (mirrorType < 0 || mirrorType > 2) return false;
        double epsilon = 1.0e-5;
        for (Vec3 p : points) {
            double value = mirrorType == 0 ? p.x : mirrorType == 1 ? p.y : p.z;
            if (Math.abs(value) > epsilon) {
                return false;
            }
        }
        return true;
    }

    private static float[] parseUv(String uv, int vertexCount) {
        if (uv == null || uv.isBlank()) return null;
        String[] parts = uv.trim().split("\\s+");
        if (parts.length < vertexCount * 2) return null;
        float[] out = new float[vertexCount * 2];
        for (int i = 0; i < vertexCount * 2; i++) {
            out[i] = Float.parseFloat(parts[i]);
        }
        return out;
    }

    private static int parseMaterialId(String line) {
        String m = matchGroup(M_PATTERN, line);
        if (m == null || m.isBlank()) return 0;
        try {
            return Integer.parseInt(m.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String matchGroup(Pattern pat, String line) {
        Matcher mm = pat.matcher(line);
        return mm.find() ? mm.group(1) : null;
    }

    private static TextureInfo resolveTexture(byte matId, List<String> materialOrder, List<String> materialTexPaths, Map<String, String> overrides, TextureOpener opener) throws Exception {
        int idx = matId & 0xFF;
        String matName = (idx < materialOrder.size()) ? materialOrder.get(idx) : null;
        if (matName == null && !materialOrder.isEmpty()) matName = materialOrder.get(0);
        String path = null;
        // 1. material name lookup (e.g. "KQBody" -> "KQBody.png")
        if (matName != null) path = overrides.get(matName);
        // 2. numeric index lookup (e.g. "0" -> "texture.png")
        if (path == null) path = overrides.get(String.valueOf(idx));
        // 3. "default" override — checked before embedded MQO tex so JSON-specified
        //    textures (e.g. signalTexture in installed-object JSON) take priority over
        //    the tex("...") line baked into the MQO (which may reference a file not
        //    bundled with this mod, causing a white-texture fallback).
        if (path == null) path = overrides.get("default");
        // 4. first override as a JSON fallback — MQO は信用ならない（C:\... 絶対パスや
        //    存在しないファイル名が tex("...") に焼き込まれている事が多い）。
        //    JSON/JS にどれか overrides が書いてあればそれを優先する。
        if (path == null && !overrides.isEmpty()) path = overrides.values().iterator().next();
        // 5. tex("...") embedded in MQO material line — skip Windows absolute paths (C:\...) that can't be resolved
        if (path == null && materialTexPaths != null && idx < materialTexPaths.size()) {
            String embedded = materialTexPaths.get(idx);
            if (embedded != null && !isWindowsAbsolutePath(embedded)) {
                path = embedded;
            }
        }
        if (path == null) path = "textures/misc/white.png";
        final String resolvedPath = path;
        final String resolvedMatName = matName;
        TextureBinding binding = TextureBinding.parse(resolvedPath);
        String cacheKey = opener.getPackKey() + "|" + binding.cacheKey();
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, k -> {
            logModelLoadDetail("texture-resolve-mqo", "matId={} matName={} resolvedPath={}", matId, resolvedMatName, resolvedPath);
            return registerTextureFromZip(binding, opener);
        });
    }

    private static void loadScriptForModel(MqoModel model, Path packPath, String scriptPath) {
        loadScriptForModel(model, packPath, scriptPath, null);
    }

    private static void loadScriptForModel(MqoModel model, Path packPath, String scriptPath, String modelName) {
        if (model == null || packPath == null) {
            RealTrainModUnofficial.LOGGER.warn("loadScriptForModel: model or packPath is null");
            return;
        }
        String normalized = normalizeScriptPath(scriptPath);
        String leaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        boolean hasExplicitPath = !normalized.isBlank();

        RealTrainModUnofficial.LOGGER.info("loadScriptForModel: scriptPath='{}', normalized='{}', leaf='{}', hasExplicitPath={}", scriptPath, normalized, leaf, hasExplicitPath);

        try {
            if (hasExplicitPath) {
                String legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized);
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf);
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    RealTrainModUnofficial.LOGGER.info("Loaded legacy script from resource manager: {}, length={}", normalized, legacyScript.length());
                    TrainScriptSystem.loadScript(normalized, legacyScript, model, modelName);
                    return;
                }
            }
        } catch (Exception ignored) {
            if (hasExplicitPath) {
                String warnKey = packPath + "|" + normalized;
                if (MISSING_SCRIPT_WARNINGS.add(warnKey)) {
                    RealTrainModUnofficial.LOGGER.debug("Legacy script lookup failed for {}; falling back to pack search", normalized);
                }
            }
            // legacy resource manager may not be initialized or the script may not be available
        }

        RealTrainModUnofficial.LOGGER.info("Attempting to load legacy model script '{}' from pack {}", hasExplicitPath ? normalized : "(fallback search)", packPath);
        try {
            if (Files.isDirectory(packPath)) {
                Path scriptFile = null;
                if (hasExplicitPath) {
                    scriptFile = resolveFilePathInPack(packPath, normalized);
                    if (scriptFile == null) {
                        scriptFile = resolveFilePathInPack(packPath, leaf);
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    RealTrainModUnofficial.LOGGER.info("Found model script at {}", scriptFile);
                    String script = PackTextDecoder.readText(scriptFile);
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath));
                    RealTrainModUnofficial.LOGGER.info("Script file loaded, length={}", script.length());
                    TrainScriptSystem.loadScript(normalized, script, model, modelName);
                } else {
                    Path fallback = findFallbackScriptFile(packPath);
                    if (fallback != null) {
                        RealTrainModUnofficial.LOGGER.warn("Model script {} not found in pack directory {}; using fallback {}", normalized, packPath, fallback);
                        String script = PackTextDecoder.readText(fallback);
                        script = preprocessScriptIncludesForDirectory(fallback, rootDirectory(packPath));
                        TrainScriptSystem.loadScript(fallback.toString(), script, model, modelName);
                    } else {
                        if (hasExplicitPath) {
                            ResourceSearchResult external = findResource(normalized, packPath);
                            if (external != null && !packPath.equals(external.packPath())) {
                                loadScriptFromResource(model, external, normalized, modelName);
                            } else {
                                RealTrainModUnofficial.LOGGER.warn("Model script not found in pack directory: {} (normalized={})", packPath, normalized);
                            }
                        } else {
                            RealTrainModUnofficial.LOGGER.warn("No fallback model script found in pack directory: {}", packPath);
                        }
                    }
                }
            } else {
                try (ZipFile zf = new ZipFile(packPath.toFile())) {
                    ZipEntry entry = null;
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized);
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf);
                        }
                    }
                    if (entry != null) {
                        RealTrainModUnofficial.LOGGER.info("Found model script in pack zip: {}", entry.getName());
                        try (InputStream in = zf.getInputStream(entry)) {
                            String script = PackTextDecoder.readText(in);
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script);
                            TrainScriptSystem.loadScript(normalized, script, model, modelName);
                        }
                    } else {
                        ZipEntry fallback = findFallbackScriptEntry(zf);
                        if (fallback != null) {
                            RealTrainModUnofficial.LOGGER.warn("Model script {} not found in pack zip {}; using fallback {}", normalized, packPath, fallback.getName());
                            try (InputStream in = zf.getInputStream(fallback)) {
                                String script = PackTextDecoder.readText(in);
                                script = preprocessScriptIncludesForZip(zf, fallback.getName(), script);
                                TrainScriptSystem.loadScript(fallback.getName(), script, model, modelName);
                            }
                        } else {
                            if (hasExplicitPath) {
                                ResourceSearchResult external = findResource(normalized, packPath);
                                if (external != null && !packPath.equals(external.packPath())) {
                                    loadScriptFromResource(model, external, normalized, modelName);
                                } else {
                                    RealTrainModUnofficial.LOGGER.warn("Model script not found in pack zip: {} (normalized={})", packPath, normalized);
                                }
                            } else {
                                RealTrainModUnofficial.LOGGER.warn("No fallback model script found in pack zip: {}", packPath);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load script {} from pack {}", scriptPath, packPath, e);
        }
    }

    private static ScriptEngine loadStandaloneScript(Path packPath, String scriptPath, String modelName) {
        if (packPath == null) {
            return null;
        }
        String source = loadStandaloneScriptSource(packPath, scriptPath);
        if (source == null || source.isBlank()) {
            return null;
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, source, modelName);
    }

    private static String loadStandaloneScriptSource(Path packPath, String scriptPath) {
        if (packPath == null) {
            return null;
        }
        String normalized = normalizeScriptPath(scriptPath);
        String leaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        boolean hasExplicitPath = !normalized.isBlank();

        try {
            if (hasExplicitPath) {
                String legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized);
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf);
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    return legacyScript;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (Files.isDirectory(packPath)) {
                Path scriptFile = null;
                if (hasExplicitPath) {
                    scriptFile = resolveFilePathInPack(packPath, normalized);
                    if (scriptFile == null) {
                        scriptFile = resolveFilePathInPack(packPath, leaf);
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    String script = PackTextDecoder.readText(scriptFile);
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath));
                    return script;
                }
                if (hasExplicitPath) {
                    ResourceSearchResult external = findResource(normalized, packPath);
                    if (external != null) {
                        return readText(external);
                    }
                }
            } else {
                try (ZipFile zf = new ZipFile(packPath.toFile())) {
                    ZipEntry entry = null;
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized);
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf);
                        }
                    }
                    if (entry != null) {
                        try (InputStream in = zf.getInputStream(entry)) {
                            String script = PackTextDecoder.readText(in);
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script);
                            return script;
                        }
                    }
                    if (hasExplicitPath) {
                        ResourceSearchResult external = findResource(normalized, packPath);
                        if (external != null) {
                            return readText(external);
                        }
                    }
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load standalone script {} from {}", scriptPath, packPath, e);
        }
        return null;
    }

    private static void loadScriptFromResource(MqoModel model, ResourceSearchResult resource, String scriptPath, String modelName) throws IOException {
        if (resource.filePath() != null) {
            Path scriptFile = resource.filePath();
            String script = PackTextDecoder.readText(scriptFile);
            script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(resource.packPath()));
            TrainScriptSystem.loadScript(scriptPath, script, model, modelName);
            return;
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                return;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String script = PackTextDecoder.readText(in);
                script = preprocessScriptIncludesForZip(zip, entry.getName(), script);
                TrainScriptSystem.loadScript(scriptPath, script, model, modelName);
            }
        }
    }

    private static Path rootDirectory(Path packPath) {
        if (packPath == null) {
            return null;
        }
        return Files.isDirectory(packPath) ? packPath : packPath.getParent();
    }

    private static String preprocessScriptIncludesForDirectory(Path scriptFile, Path root) {
        try {
            return preprocessScriptIncludes(
                PackTextDecoder.readText(scriptFile),
                normalize(scriptFile.toString()),
                includePath -> resolveIncludeFromDirectory(scriptFile, root, includePath)
            );
        } catch (Exception e) {
            return safeRead(scriptFile);
        }
    }

    private static String safeRead(Path path) {
        try {
            return PackTextDecoder.readText(path);
        } catch (IOException e) {
            return "";
        }
    }

    private static String preprocessScriptIncludesForZip(ZipFile zipFile, String entryName, String content) {
        return preprocessScriptIncludes(content, normalize(entryName), includePath -> resolveIncludeFromZip(zipFile, entryName, includePath));
    }

    private static String preprocessScriptIncludes(String content, String scriptIdentifier, IncludeResolver resolver) {
        return preprocessScriptIncludes(content, scriptIdentifier, resolver, new HashSet<>());
    }

    private static String preprocessScriptIncludes(String content, String scriptIdentifier, IncludeResolver resolver, Set<String> visiting) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (!visiting.add(scriptIdentifier)) {
            RealTrainModUnofficial.LOGGER.warn("Detected cyclic script include for {}", scriptIdentifier);
            return content;
        }

        String processed = content;
        Matcher matcher = Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$").matcher(processed);
        while (matcher.find()) {
            String includeTarget = matcher.group(1).trim();
            String replacement = "";
            try {
                IncludeSource includeSource = resolver.resolve(includeTarget);
                if (includeSource != null && includeSource.content() != null) {
                    replacement = preprocessScriptIncludes(includeSource.content(), includeSource.identifier(), resolver, visiting);
                }
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to resolve include '{}' in {}", includeTarget, scriptIdentifier, e);
            }
            processed = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
            matcher = Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$").matcher(processed);
        }

        visiting.remove(scriptIdentifier);
        return processed;
    }

    private static IncludeSource resolveIncludeFromDirectory(Path scriptFile, Path root, String includePath) throws IOException {
        String normalizedInclude = normalize(includePath);
        Path parent = scriptFile.getParent();

        if (parent != null) {
            Path relative = parent.resolve(normalizedInclude).normalize();
            if (Files.exists(relative) && Files.isRegularFile(relative)) {
                return new IncludeSource(normalize(relative.toString()), PackTextDecoder.readText(relative));
            }
        }

        if (root != null) {
            Path rootResolved = root.resolve(normalizedInclude).normalize();
            if (Files.exists(rootResolved) && Files.isRegularFile(rootResolved)) {
                return new IncludeSource(normalize(rootResolved.toString()), PackTextDecoder.readText(rootResolved));
            }
            Path found = resolveFilePathInPack(root, normalizedInclude);
            if (found != null) {
                return new IncludeSource(normalize(found.toString()), PackTextDecoder.readText(found));
            }
        }

        return null;
    }

    private static IncludeSource resolveIncludeFromZip(ZipFile zipFile, String currentEntryName, String includePath) throws IOException {
        String normalizedInclude = normalize(includePath);
        String current = normalize(currentEntryName);
        String parent = "";
        int slash = current.lastIndexOf('/');
        if (slash >= 0) {
            parent = current.substring(0, slash + 1);
        }

        ZipEntry relative = findEntry(zipFile, parent + normalizedInclude);
        if (relative == null) {
            relative = findEntry(zipFile, normalizedInclude);
        }
        if (relative == null) {
            return null;
        }

        try (InputStream in = zipFile.getInputStream(relative)) {
            return new IncludeSource(normalize(relative.getName()), PackTextDecoder.readText(in));
        }
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    @FunctionalInterface
    private interface IncludeResolver {
        IncludeSource resolve(String includePath) throws Exception;
    }

    private record IncludeSource(String identifier, String content) {}

    private static Path findFallbackScriptFile(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return null;
        Path found = null;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!relative.toLowerCase(Locale.ROOT).contains("/scripts/")) continue;
                if (!relative.toLowerCase(Locale.ROOT).endsWith(".js")) continue;
                if (found != null) {
                    return null;
                }
                found = file;
            }
        }
        return found;
    }

    private static ZipEntry findFallbackScriptEntry(ZipFile zf) {
        if (zf == null) return null;
        ZipEntry fallback = null;
        java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName().replace('\\', '/');
            if (!(name.toLowerCase(Locale.ROOT).contains("/scripts/") && name.toLowerCase(Locale.ROOT).endsWith(".js"))) continue;
            if (fallback != null) {
                return null;
            }
            fallback = entry;
        }
        return fallback;
    }

    /** 画像に中間アルファ(0/255 以外)のピクセルがあれば true (本当の半透明)。cutout の二値アルファは false。 */
    private static boolean hasPartialAlpha(com.mojang.blaze3d.platform.NativeImage img) {
        try {
            if (img.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
                return false;
            }
            int w = img.getWidth(), h = img.getHeight();
            int stepX = Math.max(1, w / 128);
            int stepY = Math.max(1, h / 128);
            int sampled = 0, partial = 0;
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    sampled++;
                    if (a >= 8 && a <= 247) partial++;
                }
            }
            // 部分アルファの割合が高い=ガラス等の半透明テクスチャ。AA縁だけの車体テクスチャ(数%未満)は
            // 不透明のまま扱い、車体が透けないようにする。3%以上で半透明と判定。
            return sampled > 0 && (partial * 100) >= (sampled * 3);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 明確な「ガラス帯」を持つか。alpha 32..224 の中間アルファ(本当に透けるガラス/煙等)が
     * 一定割合(1.5%)以上あれば true。AA 縁の薄い勾配だけ(SL車体の二値カットアウト)は
     * この範囲のピクセルがごく僅かなので false。これでガラス窓(透ける)とカットアウト車体
     * (透けない)を区別し、ガラス窓だけ強制カットアウトを免除してブレンド描画する。
     */
    private static boolean hasGlassBand(com.mojang.blaze3d.platform.NativeImage img) {
        try {
            if (img.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
                return false;
            }
            int w = img.getWidth(), h = img.getHeight();
            int stepX = Math.max(1, w / 128);
            int stepY = Math.max(1, h / 128);
            int sampled = 0, band = 0;
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    sampled++;
                    if (a >= 32 && a <= 224) band++;
                }
            }
            return sampled > 0 && (band * 1000L) >= (sampled * 15L);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 元画像をコピーし、アルファを二値化した新しい NativeImage を返す (元は破壊しない)。
     * alpha >= threshold は不透明(255)、未満は透明(0)。RTM の pass0(通常描画)は
     * アルファテスト(alpha<0.1 を discard, 残りは不透明)なので、threshold≈26(=0.1*255)で
     * その挙動を事前適用したテクスチャを作り、POSITION_TEX_COLOR+blend で同じ結果を得る。
     */
    private static com.mojang.blaze3d.platform.NativeImage copyBinarizeAlpha(com.mojang.blaze3d.platform.NativeImage img, int threshold) {
        int w = img.getWidth(), h = img.getHeight();
        com.mojang.blaze3d.platform.NativeImage dst = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);            // 0xAABBGGRR (リトルエンディアン)
                int a = (p >>> 24) & 0xFF;
                int na = a >= threshold ? 0xFF : 0x00;
                dst.setPixelRGBA(x, y, (p & 0x00FFFFFF) | (na << 24));
            }
        }
        return dst;
    }

    private static TextureInfo registerTextureFromZip(TextureBinding binding, TextureOpener opener) {
        boolean isTranslucent = binding.options().contains("alphablend")
            || binding.options().contains("translucent")
            || binding.options().contains("glassalpha");
        try (InputStream in = opener.open(binding.path())) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
                // テクスチャに「中間アルファ(0/255以外)」があれば本当の半透明 (ガラス等)。
                // cutout 用の二値アルファ(車体の穴)と区別し、本当の半透明だけ translucent 扱いにする。
                boolean partialAlpha = hasPartialAlpha(img);
                boolean glassBand = hasGlassBand(img);
                int key = Math.abs(binding.cacheKey().hashCode());
                // 元テクスチャ(アルファ維持) = RTM pass1(AlphaBlend ブレンド重ね)用。
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,
                    "dynamic/mqo/" + Integer.toHexString(key));
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                // pass0(通常描画)用 = アルファテスト相当に二値化(alpha>=26→不透明, <26→透明)。
                // RTM の pass0 は alpha<0.1 を discard して残りを不透明描画するので、これを事前適用。
                // AlphaBlend テクスチャだけ別途用意し、不透明テクスチャは元と共用(穴は二値なので同じ)。
                ResourceLocation opaqueLoc = loc;
                if (isTranslucent) {
                    // 閾値を高め(230)にして、中間アルファ(窓ガラス)を pass0(不透明)から除外する。
                    // 窓は pass1 のブレンドのみで描かれて透ける。ほぼ完全不透明(alpha>=230=外板)だけ
                    // pass0 で不透明描画してスケスケを防ぐ。抜き(alpha0)も透過。
                    com.mojang.blaze3d.platform.NativeImage opaqueImg = copyBinarizeAlpha(img, 230);
                    DynamicTexture opaqueTex = new DynamicTexture(opaqueImg);
                    opaqueLoc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,
                        "dynamic/mqo/" + Integer.toHexString(key) + "_op");
                    Minecraft.getInstance().getTextureManager().register(opaqueLoc, opaqueTex);
                }
                return new TextureInfo(loc, resolveLegacyLightTextures(binding, opener), isTranslucent, partialAlpha, glassBand, opaqueLoc);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.debug("Could not load texture {}: {}", binding.path(), e.getMessage());
        }
        ResourceLocation fallback = fallbackTexture();
        return new TextureInfo(fallback, new ResourceLocation[0], false);
    }

    private static ResourceLocation[] resolveLegacyLightTextures(TextureBinding binding, TextureOpener opener) {
        if (binding == null || !binding.hasLightTextures()) {
            return new ResourceLocation[0];
        }
        List<String> explicitPaths = binding.lightTexturePaths();
        int count = Math.max(3, explicitPaths.size());
        List<ResourceLocation> found = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String candidate = i < explicitPaths.size() ? explicitPaths.get(i) : deriveLegacyLightTexturePath(binding.path(), i);
            if (candidate == null || candidate.isBlank()) continue;
            ResourceLocation loaded = tryLoadOptionalTexture(candidate, opener, binding.cacheKey() + "#light" + i);
            if (loaded != null) {
                found.add(loaded);
            }
        }
        return found.toArray(new ResourceLocation[0]);
    }

    private static String deriveLegacyLightTexturePath(String basePath, int index) {
        if (basePath == null || basePath.isBlank()) {
            return "";
        }
        int dot = basePath.lastIndexOf('.');
        if (dot < 0) {
            return basePath + "_light" + index;
        }
        return basePath.substring(0, dot) + "_light" + index + basePath.substring(dot);
    }

    private static ResourceLocation loadOptionalTexture(String path, TextureOpener opener, String cacheKeySuffix) {
        try (InputStream in = opener.open(path)) {
            if (in == null) {
                return fallbackTexture();
            }
            byte[] data = in.readAllBytes();
            com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,
                "dynamic/mqo/" + Integer.toHexString(cacheKeySuffix.hashCode())
            );
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception ignored) {
            return fallbackTexture();
        }
    }

    private static ResourceLocation tryLoadOptionalTexture(String path, TextureOpener opener, String cacheKeySuffix) {
        try (InputStream in = opener.open(path)) {
            if (in == null) {
                return null;
            }
            byte[] data = in.readAllBytes();
            com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,
                "dynamic/mqo/" + Integer.toHexString(cacheKeySuffix.hashCode())
            );
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean shouldTreatFaceAsTranslucent(TextureInfo textureInfo, String groupName, float[] uvs, int vertexCount, float avgY) {
        if (textureInfo == null) {
            return false;
        }
        // テクスチャに中間アルファ(本当の半透明=ガラス等)があれば、MQO の AlphaBlend 指定や
        // グループ名に依らず半透明描画する (ユーザー報告「半透明テクスチャが透けない」)。
        // この判定は isTranslucent ゲートより前に置く (以前はゲートで弾かれて無視されていた)。
        if (textureInfo.hasPartialAlpha) {
            return true;
        }
        if (!textureInfo.isTranslucent) {
            return false;
        }
        String lower = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
        if (lower.equals("alpha") || lower.equals("a")) {
            return true;
        }
        if (lower.contains("alpha") || lower.contains("glass") || lower.contains("trans") || lower.contains("window")) {
            return true;
        }
        if (lower.contains("light") || lower.contains("lamp") || lower.contains("marker")) {
            return true;
        }
        // テクスチャに中間アルファ(0/255以外)がある=本当の半透明(ガラス等)。グループ名に依らず
        // 半透明描画する (ユーザー報告「半透明テクスチャが透けてない」)。
        // cutout 用の二値アルファ(車体の穴)は hasPartialAlpha=false なので不透明のまま=SL車体は消えない。
        if (textureInfo.hasPartialAlpha) {
            return true;
        }
        // RTM packs often mark full-body SL/rod textures as AlphaBlend for cutout holes.
        // Those must stay in the opaque pass or the scripted body disappears.
        return false;
    }

    private static boolean isWindowsAbsolutePath(String path) {
        if (path == null || path.length() < 2) return false;
        char c = path.charAt(0);
        return ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) && path.charAt(1) == ':';
    }


    private static ResourceLocation fallbackTexture() {
        if (fallbackWhite != null) return fallbackWhite;
        try {
            com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(4, 4, false);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    img.setPixelRGBA(x, y, 0xFFFFFFFF);
                }
            }
            DynamicTexture tex = new DynamicTexture(img);
            fallbackWhite = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "dynamic/mqo/_white");
            Minecraft.getInstance().getTextureManager().register(fallbackWhite, tex);
        } catch (Exception e) {
            fallbackWhite = TextureManager.INTENTIONAL_MISSING_TEXTURE;
        }
        return fallbackWhite;
    }

    public static ResourceLocation getScriptTexture(String domain, String path, int frameIndex) {
        if (path == null || path.isBlank()) {
            return fallbackTexture();
        }
        ScriptTextureData data = getScriptTextureData(domain, path);
        if (data.frames().isEmpty()) {
            return fallbackTexture();
        }
        int index = Math.floorMod(frameIndex, data.frames().size());
        return data.frames().get(index);
    }

    public static ScriptTextureData getScriptTextureData(String domain, String path) {
        if (path == null || path.isBlank()) {
            return ScriptTextureData.fallback(fallbackTexture());
        }
        String namespace = domain == null || domain.isBlank() ? "minecraft" : domain;
        String normalizedPath = path.replace('\\', '/');
        String cacheKey = namespace + ":" + normalizedPath;
        return SCRIPT_TEXTURE_CACHE.computeIfAbsent(cacheKey, key -> loadScriptTextureData(namespace, normalizedPath));
    }

    public static ResourceLocation getScriptTextureByTick(String domain, String path, double tick, double fps) {
        ScriptTextureData data = getScriptTextureData(domain, path);
        if (data.frames().isEmpty()) {
            return fallbackTexture();
        }
        int index = data.resolveFrameIndex(tick, fps);
        return data.frames().get(index);
    }

    public static ResourceLocation getWhiteTexture() {
        return fallbackTexture();
    }

    private static ScriptTextureData loadScriptTextureData(String domain, String path) {
        try {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gif")) {
                try (InputStream in = openScriptTextureStream(domain, path)) {
                    if (in == null) {
                        return ScriptTextureData.fallback(fallbackTexture());
                    }
                    return registerGifFrames(domain, path, in);
                }
            }
            if (lower.endsWith(".mp4")) {
                byte[] bytes = openScriptTextureBytes(domain, path);
                if (bytes == null || bytes.length == 0) {
                    return ScriptTextureData.fallback(fallbackTexture());
                }
                return registerMp4Frames(domain, path, bytes);
            }
            ScriptTextureData sequence = tryRegisterSequenceFrames(domain, path);
            if (sequence != null) {
                return sequence;
            }
            try (InputStream in = openScriptTextureStream(domain, path)) {
                if (in == null) {
                    return ScriptTextureData.fallback(fallbackTexture());
                }
                BufferedImage image = ImageIO.read(in);
                ResourceLocation frame = registerBufferedImage(domain, path, 0, image);
                return new ScriptTextureData(List.of(frame), List.of(50), image != null ? image.getWidth() : 1, image != null ? image.getHeight() : 1, 20.0D);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not load script texture {}:{}: {}", domain, path, e.getMessage());
            return ScriptTextureData.fallback(fallbackTexture());
        }
    }

    private static InputStream openScriptTextureStream(String domain, String path) throws IOException {
        String normalizedPath = path.replace('\\', '/').replaceFirst("^/+", "");
        ResourceLocation resourceLocation = ResourceLocation.tryBuild(domain == null || domain.isBlank() ? "minecraft" : domain, normalizedPath);
        if (resourceLocation != null) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
            if (resource.isPresent()) {
                return resource.get().open();
            }
        }
        String entryName = "assets/" + domain + "/" + normalizedPath;
        Path modsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return null;
        }
        try (var files = Files.list(modsDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
                    continue;
                }
                ZipFile zip = new ZipFile(file.toFile());
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    zip.close();
                    continue;
                }
                InputStream raw = zip.getInputStream(entry);
                return new java.io.FilterInputStream(raw) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        zip.close();
                    }
                };
            }
        }
        return null;
    }

    private static byte[] openScriptTextureBytes(String domain, String path) throws IOException {
        try (InputStream in = openScriptTextureStream(domain, path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static ScriptTextureData tryRegisterSequenceFrames(String domain, String path) throws IOException {
        if (!path.contains("%")) {
            return null;
        }
        List<ResourceLocation> frames = new ArrayList<>();
        int width = 1;
        int height = 1;
        for (int i = 0; i < 512; i++) {
            String resolved = String.format(Locale.ROOT, path, i);
            byte[] bytes = openScriptTextureBytes(domain, resolved);
            if (bytes == null || bytes.length == 0) {
                break;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                break;
            }
            width = image.getWidth();
            height = image.getHeight();
            frames.add(registerBufferedImage(domain, resolved, i, image));
        }
        if (frames.isEmpty()) {
            return null;
        }
        List<Integer> delays = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            delays.add(50);
        }
        return new ScriptTextureData(frames, delays, width, height, 20.0D);
    }

    private static ScriptTextureData registerGifFrames(String domain, String path, InputStream in) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return ScriptTextureData.fallback(fallbackTexture());
        }
        ImageReader reader = readers.next();
        List<ResourceLocation> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        int width = 1;
        int height = 1;
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(in)) {
            reader.setInput(imageInput);
            int count = reader.getNumImages(true);
            BufferedImage composed = null;
            java.awt.Graphics2D graphics = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                width = frame.getWidth();
                height = frame.getHeight();
                if (composed == null) {
                    composed = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    graphics = composed.createGraphics();
                }
                int left = 0;
                int top = 0;
                int delayMs = 50;
                try {
                    Node root = reader.getImageMetadata(i).getAsTree(reader.getImageMetadata(i).getNativeMetadataFormatName());
                    Node desc = findGifMetadataNode(root, "ImageDescriptor");
                    if (desc != null && desc.getAttributes() != null) {
                        Node leftNode = desc.getAttributes().getNamedItem("imageLeftPosition");
                        Node topNode = desc.getAttributes().getNamedItem("imageTopPosition");
                        if (leftNode != null) left = Integer.parseInt(leftNode.getNodeValue());
                        if (topNode != null) top = Integer.parseInt(topNode.getNodeValue());
                    }
                    Node gce = findGifMetadataNode(root, "GraphicControlExtension");
                    if (gce != null && gce.getAttributes() != null) {
                        Node delayNode = gce.getAttributes().getNamedItem("delayTime");
                        if (delayNode != null) {
                            delayMs = Math.max(20, Integer.parseInt(delayNode.getNodeValue()) * 10);
                        }
                    }
                } catch (Exception ignored) {
                }
                graphics.drawImage(frame, left, top, null);
                BufferedImage snapshot = new BufferedImage(composed.getWidth(), composed.getHeight(), BufferedImage.TYPE_INT_ARGB);
                snapshot.setData(composed.getData());
                frames.add(registerBufferedImage(domain, path, i, snapshot));
                delays.add(delayMs);
            }
            if (graphics != null) {
                graphics.dispose();
            }
        } finally {
            reader.dispose();
        }
        return new ScriptTextureData(frames, delays, width, height, 20.0D);
    }

    private static ScriptTextureData registerMp4Frames(String domain, String path, byte[] bytes) throws IOException {
        // MP4 support requires optional jcodec library - disabled by default
        return ScriptTextureData.fallback(fallbackTexture());
    }

    private static Node findGifMetadataNode(Node root, String nodeName) {
        if (root == null) {
            return null;
        }
        if (nodeName.equalsIgnoreCase(root.getNodeName())) {
            return root;
        }
        Node child = root.getFirstChild();
        while (child != null) {
            Node found = findGifMetadataNode(child, nodeName);
            if (found != null) {
                return found;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static ResourceLocation registerBufferedImage(String domain, String path, int frame, BufferedImage image) {
        if (image == null) {
            return fallbackTexture();
        }
        com.mojang.blaze3d.platform.NativeImage nativeImage = new com.mojang.blaze3d.platform.NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        String safe = Integer.toHexString((domain + ":" + path + "#" + frame).hashCode());
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "dynamic/script/" + safe);
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(nativeImage));
        return loc;
    }

    public record ScriptTextureData(List<ResourceLocation> frames, List<Integer> delaysMs, int width, int height, double defaultFps) {
        public static ScriptTextureData fallback(ResourceLocation texture) {
            return new ScriptTextureData(List.of(texture), List.of(50), 1, 1, 20.0D);
        }

        public int resolveFrameIndex(double tick, double fpsOverride) {
            if (frames.isEmpty()) {
                return 0;
            }
            if (delaysMs.size() == frames.size()) {
                long millis = Math.max(0L, Math.round(tick * 50.0D));
                long total = 0L;
                for (int delay : delaysMs) {
                    total += Math.max(1, delay);
                }
                if (total > 0L) {
                    long wrapped = millis % total;
                    long cursor = 0L;
                    for (int i = 0; i < delaysMs.size(); i++) {
                        cursor += Math.max(1, delaysMs.get(i));
                        if (wrapped < cursor) {
                            return i;
                        }
                    }
                }
            }
            double fps = fpsOverride > 0.0D ? fpsOverride : defaultFps;
            return Math.floorMod((int) Math.floor((tick / 20.0D) * fps), frames.size());
        }
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        renderModel(model, poseStack, buffer, packedLight, null);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Object entity) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, null, entity);
    }

    public static void renderModelPreferScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Object entity) {
        if (model == null) return;
        model.renderPreferScript(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, null, entity);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, groupTransform, entity);
    }

    @FunctionalInterface
    private interface TextureOpener {
        InputStream open(String path) throws Exception;
        default String getPackKey() {
            return "";
        }
    }

    private static final class TextureInfo {
        final ResourceLocation location;
        final ResourceLocation[] emissiveTextures;
        final boolean isTranslucent;
        /** テクスチャに中間アルファ(0/255以外)があるか。true=本当の半透明(ガラス等)。 */
        final boolean hasPartialAlpha;
        /**
         * 明確な「ガラス帯」(alpha 32..224 の半透明ピクセルがまとまった割合)を持つか。
         * AA 縁だけの二値カットアウト(SL車体等)と区別し、true なら本当の半透明テクスチャとして
         * グループ名キーワードに依らず必ずブレンド描画する(強制カットアウトしない)。
         */
        final boolean hasGlassBand;
        /** RTM pass0(通常/不透明描画)用テクスチャ。アルファテスト相当に二値化済み。非AlphaBlendは元と同じ。 */
        final ResourceLocation opaqueLocation;

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent) {
            this(location, emissiveTextures, isTranslucent, false, false, location);
        }

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent, boolean hasPartialAlpha) {
            this(location, emissiveTextures, isTranslucent, hasPartialAlpha, false, location);
        }

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent, boolean hasPartialAlpha, boolean hasGlassBand) {
            this(location, emissiveTextures, isTranslucent, hasPartialAlpha, hasGlassBand, location);
        }

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent, boolean hasPartialAlpha, boolean hasGlassBand, ResourceLocation opaqueLocation) {
            this.location = location;
            this.emissiveTextures = emissiveTextures == null ? new ResourceLocation[0] : emissiveTextures;
            this.isTranslucent = isTranslucent;
            this.hasPartialAlpha = hasPartialAlpha;
            this.hasGlassBand = hasGlassBand;
            this.opaqueLocation = opaqueLocation == null ? location : opaqueLocation;
        }

        ResourceLocation emissiveTextureForPass(int pass) {
            int index = pass - 2;
            if (index < 0 || index >= emissiveTextures.length) {
                return null;
            }
            return emissiveTextures[index];
        }

    }

    private record TextureBinding(String path, Set<String> options, List<String> lightTexturePaths) {
        static TextureBinding parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new TextureBinding("textures/misc/white.png", Set.of(), List.of());
            }
            int metaIndex = raw.indexOf(TEXTURE_META_SEPARATOR);
            if (metaIndex < 0) {
                return new TextureBinding(raw, Set.of(), List.of());
            }
            String path = raw.substring(0, metaIndex);
            String metadata = raw.substring(metaIndex + TEXTURE_META_SEPARATOR.length());
            if (metadata.isBlank()) {
                return new TextureBinding(path, Set.of(), List.of());
            }
            Set<String> options = new LinkedHashSet<>();
            List<String> lightTexturePaths = new ArrayList<>();
            for (String token : metadata.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isBlank()) {
                    String lowered = trimmed.toLowerCase(Locale.ROOT);
                    if (lowered.equals("light")
                        || lowered.equals("alphablend")
                        || lowered.equals("translucent")
                        || lowered.equals("glassalpha")) {
                        options.add(lowered);
                    } else if (lowered.equals("onetex") || lowered.equals("one_tex")) {
                        // RTM の "OneTex" フラグ: モデル全体が1テクスチャを共有する → 無視
                    } else {
                        lightTexturePaths.add(trimmed);
                    }
                }
            }
            return new TextureBinding(path, Set.copyOf(options), List.copyOf(lightTexturePaths));
        }

        boolean hasLightTextures() {
            return options.contains("light");
        }

        String cacheKey() {
            if (options.isEmpty() && lightTexturePaths.isEmpty()) {
                return path;
            }
            List<String> metadata = new ArrayList<>(options);
            metadata.addAll(lightTexturePaths);
            return path + TEXTURE_META_SEPARATOR + String.join(",", metadata);
        }
    }

    /** グループ名を受け取り、そのグループをレンダリングするかどうかを返す述語。 */
    @FunctionalInterface
    public interface GroupPredicate {
        boolean shouldRender(String groupName);
    }

    /** グループ名を受け取り、そのグループに対して追加の変換を行う関数。 */
    @FunctionalInterface
    public interface GroupTransform {
        void apply(PoseStack poseStack, String groupName);
        /**
         * 任意の早期判定: groupName に対して何も変換しない場合は false を返す。
         * renderSelectedBatches は false 時に pushPose/popPose を完全に省略する。
         * デフォルトは保守的に true (常に push/pop)。
         * SL のような扉なし車両で 100 batch × 200 push/pop/フレームを丸ごと
         * 削減できる ⇒ Pose (Matrix4f+Matrix3f) の確保が消える。
         */
        default boolean mayModify(String groupName) {
            return true;
        }
    }

    private static final class BatchBuilder {
        final int order;
        final String groupName;
        final ResourceLocation texture;
        final ResourceLocation[] emissiveTextures;
        final boolean translucent;
        final int materialId;
        final float smoothingAngle;
        /** マテリアル col の不透明度 (1.0=不透明)。半透明ガラス等は <1。描画時に色のαへ乗算。 */
        float baseAlpha = 1.0F;
        /** テクスチャが明確なガラス帯を持つ=本当の半透明。強制カットアウトを免除する。 */
        boolean glassTranslucent = false;
        /** RTM pass0(不透明描画)用のアルファテスト相当テクスチャ。 */
        ResourceLocation opaqueTexture = null;
        final List<Float> positions = new ArrayList<>();
        final Set<String> faceSignatures = new HashSet<>();
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        BatchBuilder(int order, String groupName, ResourceLocation texture, ResourceLocation[] emissiveTextures, int materialId, boolean translucent, float smoothingAngle) {
            this.order = order;
            this.groupName = groupName;
            this.texture = texture;
            this.emissiveTextures = emissiveTextures == null ? new ResourceLocation[0] : emissiveTextures;
            this.materialId = materialId;
            this.translucent = translucent;
            this.smoothingAngle = smoothingAngle;
        }

        void put(Vec3 p, Vector3f n, float u, float v) {
            positions.add((float) p.x);
            positions.add((float) p.y);
            positions.add((float) p.z);
            positions.add(n.x);
            positions.add(n.y);
            positions.add(n.z);
            positions.add(u);
            positions.add(v);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        boolean markFace(Vec3[] points, float[] uv) {
            StringBuilder builder = new StringBuilder(points.length * 40);
            for (int i = 0; i < points.length; i++) {
                Vec3 point = points[i];
                builder.append(Float.floatToIntBits((float) point.x)).append(':')
                    .append(Float.floatToIntBits((float) point.y)).append(':')
                    .append(Float.floatToIntBits((float) point.z)).append(':');
                if (uv != null && uv.length >= (i + 1) * 2) {
                    builder.append(Float.floatToIntBits(uv[i * 2])).append(':')
                        .append(Float.floatToIntBits(uv[i * 2 + 1]));
                }
                builder.append('|');
            }
            return faceSignatures.add(builder.toString());
        }

        Batch bake(boolean smoothing) {
            if (smoothing) {
                applySmoothNormals();
            }
            float[] data = new float[positions.size()];
            for (int i = 0; i < positions.size(); i++) data[i] = positions.get(i);
            float safeMinU = Float.isFinite(minU) ? minU : 0.0F;
            float safeMaxU = Float.isFinite(maxU) ? maxU : 1.0F;
            float safeMinV = Float.isFinite(minV) ? minV : 0.0F;
            float safeMaxV = Float.isFinite(maxV) ? maxV : 1.0F;
            Batch built = new Batch(order, groupName, texture, emissiveTextures, data, data.length / 8, materialId, translucent, safeMinU, safeMaxU, safeMinV, safeMaxV);
            built.baseAlpha = baseAlpha;
            built.glassTranslucent = glassTranslucent;
            built.opaqueTexture = opaqueTexture != null ? opaqueTexture : texture;
            return built;
        }

        private void applySmoothNormals() {
            int vertexCount = positions.size() / 8;
            if (vertexCount <= 0) {
                return;
            }

            Map<String, List<Integer>> byPosition = new HashMap<>();
            Vector3f[] originalNormals = new Vector3f[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                int o = i * 8;
                byPosition.computeIfAbsent(positionKey(o), k -> new ArrayList<>()).add(i);
                originalNormals[i] = new Vector3f(positions.get(o + 3), positions.get(o + 4), positions.get(o + 5));
                if (originalNormals[i].lengthSquared() > 1.0E-8F) {
                    originalNormals[i].normalize();
                } else {
                    originalNormals[i].set(0.0F, 1.0F, 0.0F);
                }
            }

            float angle = this.smoothingAngle > 0.0F ? this.smoothingAngle : 59.5F;
            float cosThreshold = (float) Math.cos(Math.toRadians(angle));
            for (int i = 0; i < vertexCount; i++) {
                int o = i * 8;
                List<Integer> shared = byPosition.get(positionKey(o));
                if (shared == null || shared.isEmpty()) {
                    continue;
                }

                Vector3f current = originalNormals[i];
                Vector3f sum = new Vector3f();
                for (int other : shared) {
                    Vector3f normal = originalNormals[other];
                    if (current.dot(normal) >= cosThreshold) {
                        sum.add(normal);
                    }
                }
                if (sum.lengthSquared() > 1.0E-8F) {
                    sum.normalize();
                    positions.set(o + 3, sum.x);
                    positions.set(o + 4, sum.y);
                    positions.set(o + 5, sum.z);
                }
            }
        }

        private String positionKey(int offset) {
            return Math.round(positions.get(offset) * 1000.0F) + ","
                + Math.round(positions.get(offset + 1) * 1000.0F) + ","
                + Math.round(positions.get(offset + 2) * 1000.0F);
        }
    }

    public static final class MqoModel {
        // RTM scripts use pass 0 (opaque), 1 (transparent), and "pass > 1" (emissive/fullbright).
        // Running more than 3 passes would repeat emissive content needlessly.
        private static final int LEGACY_SCRIPT_PASS_COUNT = 3;
        private final List<Batch> batches;
        private final Map<String, List<Batch>> batchesByNormalizedGroup;

        private final ScriptModel scriptModel;

        public MqoModel(List<Batch> batches, List<ResourceLocation> materialTextures) {
            this.batches = batches;
            this.batchesByNormalizedGroup = buildBatchIndex(batches);
            this.scriptModel = new ScriptModel(materialTextures);
        }

        long estimateMemoryBytes() {
            long bytes = 512L;
            bytes += (long) batches.size() * 160L;
            for (Batch batch : batches) {
                bytes += 64L;
                bytes += (long) batch.data.length * Float.BYTES;
                if (batch.groupName != null) {
                    bytes += (long) batch.groupName.length() * 2L;
                }
            }
            bytes += (long) scriptModel.textures.length * 64L;
            return bytes;
        }

        private static Map<String, List<Batch>> buildBatchIndex(List<Batch> batches) {
            Map<String, List<Batch>> index = new HashMap<>();
            for (Batch batch : batches) {
                String key = normalizeBatchGroupName(batch.groupName);
                index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(batch);
            }
            return index;
        }

        private static String normalizeBatchGroupName(String groupName) {
            return groupName == null ? "" : groupName.trim().toLowerCase(Locale.ROOT);
        }

        private ScriptEngine scriptEngine;
        private TrainScriptSystem.ScriptModelRenderer scriptRenderer;
        private Boolean hasLegacyRenderFunction;
        private boolean legacyScriptDisabled;
        private final boolean[] observedLegacyPassActivity = new boolean[LEGACY_SCRIPT_PASS_COUNT];
        private int legacyPassObservationMask;
        // pass を最後に観測してから経過した呼び出し数。
        // 一定値毎に強制再観測することで、ライト ON 等の状態変化時に
        // skip を解除する。
        private final int[] passSinceRecheck = new int[LEGACY_SCRIPT_PASS_COUNT];
        private static final int PASS_RECHECK_INTERVAL = 40;  // 約1秒で再観測

        public void setScriptEngine(ScriptEngine engine, TrainScriptSystem.ScriptModelRenderer renderer) {
            this.scriptEngine = engine;
            this.scriptRenderer = renderer;
            this.hasLegacyRenderFunction = null;
            this.legacyScriptDisabled = false;
            this.legacyPassObservationMask = 0;
            java.util.Arrays.fill(this.observedLegacyPassActivity, false);
        }

        public void setScriptEngine(Object engine) {
            if (engine instanceof ScriptEngine scriptEngine) {
                setScriptEngine(scriptEngine, null);
            }
        }

        public ScriptEngine getScriptEngine() {
            return scriptEngine;
        }

        public boolean hasRenderScript() {
            return scriptEngine != null && !legacyScriptDisabled;
        }

        public ScriptModel getScriptModel() {
            return scriptModel;
        }

        public boolean hasGroupNamed(String groupName) {
            if (groupName == null || groupName.isBlank()) {
                return false;
            }
            for (Batch batch : batches) {
                if (batch.groupName != null && batch.groupName.equalsIgnoreCase(groupName)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 車体MQO自身が走り装置(車輪)を持つか。蒸気機関車のように車輪・動輪・台車を
         * 車体モデル内に持ちスクリプトで自前描画する車両は、別途の汎用台車モデル
         * (ModelBogie.class 置換) を描く必要がない (= 二重描画/散乱の原因)。
         */
        public boolean hasOwnWheelGroups() {
            for (Batch batch : batches) {
                String g = batch.groupNameLower;
                if (g == null) continue;
                if (g.startsWith("wheel") || g.contains("動輪") || g.contains("車輪")) {
                    return true;
                }
            }
            return false;
        }

        /** モデル内の全グループ名 (正規化済み: trim + toLowerCase) を返す。 */
        public java.util.Set<String> getAllNormalizedGroupNames() {
            java.util.Set<String> result = new java.util.LinkedHashSet<>();
            for (Batch batch : batches) {
                if (batch.groupName != null && !batch.groupName.isBlank()) {
                    result.add(batch.groupName.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            return result;
        }

        public boolean hasTranslucentBatches() {
            for (Batch batch : batches) {
                if (batch.translucent) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasOpaqueBatches() {
            for (Batch batch : batches) {
                if (!batch.translucent) {
                    return true;
                }
            }
            return false;
        }

        public int getBatchCount() {
            return batches.size();
        }

        public int getTranslucentBatchCount() {
            int count = 0;
            for (Batch batch : batches) {
                if (batch.translucent) {
                    count++;
                }
            }
            return count;
        }

        public int getTotalVertexCount() {
            int count = 0;
            for (Batch batch : batches) {
                count += batch.vertexCount;
            }
            return count;
        }

        public boolean hasLegacyLightTextures() {
            for (Batch batch : batches) {
                if (batch.emissiveTextures.length > 0) {
                    return true;
                }
            }
            return false;
        }

        public void renderNamedGroups(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                      boolean translucent, Set<String> normalizedGroupNames,
                                      TrainScriptSystem.ScriptModelRenderer scriptRenderer) {
            if (normalizedGroupNames == null || normalizedGroupNames.isEmpty()) {
                return;
            }
            List<Batch> ordered = renderListCache.get(normalizedGroupNames);
            if (ordered == null) {
                Set<Batch> selected = new LinkedHashSet<>();
                for (String name : normalizedGroupNames) {
                    List<Batch> batches = batchesByNormalizedGroup.get(name);
                    if (batches != null && !batches.isEmpty()) {
                        selected.addAll(batches);
                    }
                }
                if (selected.isEmpty()) {
                    ordered = java.util.Collections.emptyList();
                } else {
                    ordered = new ArrayList<>(selected);
                    ordered.sort(java.util.Comparator.comparingInt(batch -> batch.order));
                }
                renderListCache.put(normalizedGroupNames, ordered);
            }
            if (ordered.isEmpty()) {
                return;
            }
            // シェーダーパイプライン有効時は直接GL (POSITION_TEX_COLOR) を使わない。
            // Irisはこのシェーダーをgbuffers_texturedに振り分けエミッシブ扱いするため、
            // 車体・台車が光って見える。シェーダー有効時は entityCutoutNoCull 経由で
            // gbuffers_entities に通し、FULL_BRIGHT lightmap で明るさを維持する。
            // RTM's legacy AlphaBlend pass uses GL_BLEND without disabling depth writes.
            // Minecraft's entityTranslucent does disable depth writes, which makes dense
            // train interiors shimmer/flicker. Use the direct RTM-style path for scripted
            // train alpha passes when shaders are not active.
            // トレインの半透明(AlphaBlend=車体/窓/内装)は全パスで直接GL(深度書き込み保持)に通す。
            // entityTranslucent は深度書き込みを切るため、pass 0 の車体(body)が視点角度により透けて
            // 消える (ユーザー報告「ボディが出ない」)。RTM 同様 GL_BLEND + 深度書き込みで solid に描く。
            // 列車の全ジオメトリ(不透明 body 含む)を直接GL/VBO 経路(fullbright)で高速描画する。
            // 不透明 body も CPU 頂点変換(ライトマップ経路)だと毎フレーム数万頂点を処理して重い。
            // VBO 経路なら 1 度アップロードした静的バッファを行列1枚で描けるので劇的に軽い。
            // 「夜でも発光する」問題は renderSelectedBatches 側で setShaderColor に明るさ係数
            // (computeFlatBrightness)を掛けて解消済み(pass>=2 は packedLight=FULL で係数≈1)。
            // 法線ディフューズ陰影は付かない(RTM 原作と同じフラット表示)。
            boolean fullbright = scriptRenderer != null && scriptRenderer.isRenderingTrain()
                    && !hasActiveShaderPipeline();
            renderSelectedBatches(ordered, poseStack, buffer, packedLight, overlay, translucent, scriptRenderer, null, fullbright);
        }

        // (Set インスタンス → ソート済み Batch リスト) を IdentityHashMap でキャッシュ。
        // SL の動軸 renderParts ループで毎フレーム発生していた LinkedHashSet/ArrayList
        // 確保 + sort コストを排除する。ParsedGroupSet.presentGroupNames は
        // 同一 Set インスタンスのまま渡されるため、ヒット率はほぼ 100%。
        private final java.util.IdentityHashMap<Set<String>, List<Batch>> renderListCache = new java.util.IdentityHashMap<>();



        private boolean executeScript(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, int pass, Object entity) {
            if (scriptEngine == null || legacyScriptDisabled) {
                return false;
            }
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.setRenderContext(poseStack, buffer, packedLight, overlay, pass, entity);
                }
                // RTM スクリプトは model.renderPart("...") で部品を描画する。
                // ScriptModel が現在の renderer を知らないと描画できないため、毎フレーム差し替える。
                if (scriptModel != null && scriptRenderer != null) {
                    scriptModel.setActiveRenderer(scriptRenderer);
                }
                // 列車はリプレイキャッシュを使わない。リプレイは状態を量子化(車輪5°/ドア丸め)して
                // 再生するためアニメがカクつき、さらにドア開閉中に署名が乱れて車両が消える事があった
                // (ユーザー報告)。RTM 同様 JS を毎フレーム実行して滑らかさを保つ。描画自体は Phase 1
                // の VBO 化で十分軽いので、JS 毎フレームでも実用的。軽量化の続きはアニメに影響しない
                // フラスタムカリング/距離LOD で行う。
                boolean allowReplay = !(entity instanceof TrainEntity);
                if (scriptRenderer != null && allowReplay) {
                    long sig = scriptRenderer.computeReplaySignature(pass, entity);
                    if (sig != 0L) {
                        if (scriptRenderer.tryReplayCachedScript(sig)) {
                            // replay 成功 - JS engine を 1 度も呼ばずに描画完了
                            noteLegacyPassActivity(pass, true);
                            return true;
                        }
                        // miss: 録画開始してから JS を走らせる
                        scriptRenderer.beginRecording(sig);
                    }
                }
                if (scriptEngine instanceof ScriptEngine engine) {
                    int renderedBatchesBefore = scriptRenderer != null ? scriptRenderer.getRenderedBatchCount() : 0;
                    // RTMレガシースクリプトは entity.getBogie(n) / entity.field_70177_z 等
                    // LegacyScriptExecutor のAPIを前提としている。生の TrainEntity ではなく
                    // LegacyScriptExecutor でラップして渡す。
                    Object scriptEntity = entity instanceof TrainEntity te
                        ? new TrainScriptSystem.LegacyScriptExecutor(te) : entity;
                    engine.put("poseStack", null);
                    engine.put("pass", pass);
                    engine.put("entity", scriptEntity);
                    engine.put("executer", scriptEntity);
                    engine.put("executor", scriptEntity);
                    if (hasLegacyRenderFunction == null) {
                        Object renderType = engine.eval("typeof render");
                        hasLegacyRenderFunction = "function".equals(renderType)
                            || (renderType != null && "function".equals(renderType.toString()));
                        RealTrainModUnofficial.LOGGER.info(
                            "[ScriptDiag] hasRenderFn={} typeofRender={}",
                            hasLegacyRenderFunction, renderType);
                    }
                    if (Boolean.TRUE.equals(hasLegacyRenderFunction)) {
                        boolean rendered;
                        if (engine instanceof Invocable invocable) {
                            try {
                                invocable.invokeFunction("render", scriptEntity, pass, null);
                                rendered = scriptRenderer == null || scriptRenderer.getRenderedBatchCount() > renderedBatchesBefore;
                                if (scriptRenderer != null) scriptRenderer.endRecording(rendered);
                                noteLegacyPassActivity(pass, rendered);
                                return rendered;
                            } catch (NoSuchMethodException ignored) {
                            }
                        }
                        engine.eval("render(entity, pass, null);");
                        rendered = scriptRenderer == null || scriptRenderer.getRenderedBatchCount() > renderedBatchesBefore;
                        if (scriptRenderer != null) scriptRenderer.endRecording(rendered);
                        noteLegacyPassActivity(pass, rendered);
                        return rendered;
                    }
                }
            } catch (Exception e) {
                if (scriptRenderer != null) scriptRenderer.endRecording(false);
                // 例外が出ても、それまでに renderRegisteredGroups などで登録済みの
                // scriptedOpaqueGroups / scriptedTranslucentGroups は残す。
                // これらをクリアしてしまうと baked render の filter が無効化されて、
                // 既にスクリプト側で描いた body 等を baked が再度上書き描画し
                // z-fighting が発生する (C12 SL: render_rod が <eval>:317 で例外)。
                RealTrainModUnofficial.LOGGER.warn("Legacy model script failed on pass {} (will retry next frame)", pass, e);
            } finally {
                if (scriptRenderer != null) {
                    // スクリプトが pushMatrix/popMatrix のバランスを崩したまま終了した場合に
                    // matrixDepth を 0 に戻す。残った push は外側の executeScript 呼び出し元の
                    // pushPose/popPose で吸収されるため、ここでは内部カウンタを 0 にするだけ。
                    scriptRenderer.restoreMatrixDepth(0);
                    scriptRenderer.clearRenderContext();
                }
            }
            return false;
        }

        private void noteLegacyPassActivity(int pass, boolean rendered) {
            if (pass < 0 || pass >= LEGACY_SCRIPT_PASS_COUNT) {
                return;
            }
            legacyPassObservationMask |= 1 << pass;
            if (rendered) {
                observedLegacyPassActivity[pass] = true;
            }
        }

        private boolean shouldSkipObservedLegacyPass(int pass) {
            if (pass < 0 || pass >= LEGACY_SCRIPT_PASS_COUNT) return false;
            if (pass == 0) return false; // pass 0 は本体描画。絶対走らせる。
            int bit = 1 << pass;
            boolean observed = (legacyPassObservationMask & bit) != 0;
            if (!observed) return false;
            // 一度走らせて batch を 0 個しか出さなかった pass はスキップ。
            // ただし PASS_RECHECK_INTERVAL 回ごとに再観測してライト ON 等の
            // 状態変化を検出する。
            if (observedLegacyPassActivity[pass]) return false;
            if (++passSinceRecheck[pass] >= PASS_RECHECK_INTERVAL) {
                passSinceRecheck[pass] = 0;
                legacyPassObservationMask &= ~bit;  // 再観測
                return false;
            }
            return true;
        }

        private void renderInternal(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                    boolean translucent, GroupPredicate groupFilter, GroupTransform groupTransform,
                                    TrainScriptSystem.ScriptModelRenderer scriptRenderer, Object entity) {
            // 全体 fullbright にすると車体全部が夜に光る (RTM 原作では Light マテリアル =
            // 個別バッチの emissive のみが光る挙動)。fullbright は無効化し、各バッチが持つ
            // emissiveTexture の有無で個別に判定する経路に任せる。
            boolean fullbright = false;
            renderSelectedBatches(this.batches, poseStack, buffer, packedLight, overlay, translucent, groupFilter, groupTransform, scriptRenderer, entity, fullbright);
        }

        private void renderSelectedBatches(List<Batch> selectedBatches, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                           boolean translucent, TrainScriptSystem.ScriptModelRenderer scriptRenderer, Object entity, boolean fullbright) {
            renderSelectedBatches(selectedBatches, poseStack, buffer, packedLight, overlay, translucent, null, null, scriptRenderer, entity, fullbright);
        }

        private void renderSelectedBatches(List<Batch> selectedBatches, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                           boolean translucent, GroupPredicate groupFilter, GroupTransform groupTransform,
                                           TrainScriptSystem.ScriptModelRenderer scriptRenderer, Object entity, boolean fullbright) {
            // ループ全体で保持する直近値。再設定を skip するため。
            int gr = scriptRenderer != null ? scriptRenderer.getColorRed255()   : 255;
            int gg = scriptRenderer != null ? scriptRenderer.getColorGreen255() : 255;
            int gb = scriptRenderer != null ? scriptRenderer.getColorBlue255()  : 255;
            int ga = scriptRenderer != null ? scriptRenderer.applyAlpha255(255) : 255;
            int lastBlendMode = -1; // 0=disabled, 1=blend(depthMask=false), 2=cutout(depthMask=true)
            int lastCullMode = -1;  // 0=両面(cull無効), 1=片面(cull有効)。batch ごとに切替。
            // fullbright(直接GL/VBO)経路はライトマップを使わないので、周囲の明るさを
            // setShaderColor に係数として掛けて疑似的に再現する。これで「高速VBO」かつ
            // 「夜は暗く/昼は明るく(=勝手に発光しない)」を両立する。法線ディフューズは
            // RTM 原作同様フラット(向きで陰影を付けない)。emissive pass(>=2)は packedLight が
            // FULL なので係数 ≈ 1.0 となり、前照灯等はそのまま明るい。
            float lightFactor = fullbright ? computeFlatBrightness(packedLight) : 1.0F;
            if (fullbright) {
                RenderSystem.enableDepthTest();
                // カリングは batch ごとに切り替える(下のループ内)。不透明ボディは両面表示にする。
                // 全面カリングONだと、巻き順が完全に揃っていない面(SLボディ等)が裏面扱いになり
                // 「後ろから見るとボディが消える」ため(ユーザー報告)。半透明ガラスだけ片面にして
                // 表裏二重ブレンドで暗くなるのを防ぐ。
                // ループ全体で 1 度だけシェーダ・色を設定。
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderColor(gr / 255f * lightFactor, gg / 255f * lightFactor, gb / 255f * lightFactor, ga / 255f);
            }

            for (Batch batch : selectedBatches) {
                if (scriptRenderer != null && shouldSkipLegacyPlaceholderGroup(batch.groupName)) {
                    continue;
                }
                if (groupFilter != null && !groupFilter.shouldRender(batch.groupName)) {
                    continue;
                }
                // RTM 2パス: opaque pass(translucent=false)は全 batch を不透明描画、
                // translucent pass(translucent=true)は AlphaBlend batch のみブレンド重ね描画。
                // emissive(scriptPass>=2 = 前照灯等)は別系統なので translucent pass でも通す。
                int scriptPassNow = scriptRenderer != null ? scriptRenderer.getCurrentPass() : 0;
                if (translucent && !batch.translucent && scriptPassNow < 2) continue;
                if (scriptRenderer != null) {
                    scriptRenderer.currentMatId = batch.materialId;
                    scriptRenderer.onBatchRendered();
                }
                boolean willTransform = groupTransform != null && groupTransform.mayModify(batch.groupName);
                if (willTransform) {
                    poseStack.pushPose();
                }
                try {
                    if (willTransform) {
                        groupTransform.apply(poseStack, batch.groupName);
                    }

                    int scriptPass = scriptRenderer != null ? scriptRenderer.getCurrentPass() : 0;
                    boolean scriptTexture = scriptRenderer != null && scriptRenderer.getBoundTexture() != null;
                    ResourceLocation emissiveTexture = !scriptTexture && scriptPass >= 2 ? batch.emissiveTextureForPass(scriptPass) : null;
                    if (scriptPass >= 2 && !scriptTexture && emissiveTexture == null) {
                        continue;
                    }
                    ResourceLocation texture = scriptTexture
                        ? scriptRenderer.getBoundTexture()
                        : (emissiveTexture != null ? emissiveTexture : batch.texture);
                    // RTM 2パス: opaque pass は不透明(アルファテスト相当に二値化済み)テクスチャ、
                    // translucent pass は元(アルファ維持)テクスチャを使う。script/emissive 指定時は除外。
                    if (!scriptTexture && emissiveTexture == null) {
                        texture = translucent ? batch.texture : batch.opaqueTexture;
                    }

                    String lowerGroupName = batch.groupNameLower;
                    boolean forceCutout;
                    float depthBias;
                    if (scriptTexture) {
                        forceCutout = shouldForceLegacyAlphaCutout(batch, lowerGroupName, true)
                            || shouldForceCabCutout(batch, lowerGroupName, true)
                            || shouldForceDisplayCutout(batch, lowerGroupName, true);
                        depthBias = getDepthBias(batch, lowerGroupName, true);
                    } else {
                        // scriptTexture=false の結果はバッチ構築時に 1 度計算してキャッシュ。
                        // SL のように毎フレーム数百回呼ばれる shouldForce*/getDepthBias の
                        // 文字列 contains/startsWith を完全に省ける。
                        if (!batch.cachedComputed) {
                            batch.cachedForceCutoutNoScriptTex =
                                shouldForceLegacyAlphaCutout(batch, lowerGroupName, false)
                                || shouldForceCabCutout(batch, lowerGroupName, false)
                                || shouldForceDisplayCutout(batch, lowerGroupName, false);
                            batch.cachedDepthBiasNoScriptTex = getDepthBias(batch, lowerGroupName, false);
                            batch.cachedComputed = true;
                        }
                        forceCutout = batch.cachedForceCutoutNoScriptTex;
                        depthBias = batch.cachedDepthBiasNoScriptTex;
                    }
                    boolean needsBlend = translucent && !forceCutout && (scriptTexture || batch.translucent);

                    int scriptRed   = scriptRenderer != null ? scriptRenderer.getColorRed255()   : 255;
                    int scriptGreen = scriptRenderer != null ? scriptRenderer.getColorGreen255() : 255;
                    int scriptBlue  = scriptRenderer != null ? scriptRenderer.getColorBlue255()  : 255;
                    int scriptAlpha = scriptRenderer != null ? scriptRenderer.applyAlpha255(255) : 255;
                    // マテリアル col の不透明度を乗算 (ガラス等 a<1 で半透明に透ける)。
                    if (batch.baseAlpha < 0.999F) {
                        scriptAlpha = Math.round(scriptAlpha * batch.baseAlpha);
                    }

                    if (fullbright) {
                        // Direct OpenGL path: fullbright, no lightmap (trains/entities)
                        // テクスチャは batch ごとに変わる可能性があるので毎回設定 (RenderSystem 内部
                        // で同値なら GL バインドはスキップされる)。
                        RenderSystem.setShaderTexture(0, texture);
                        // 色は通常スクリプトが頻繁には変えないので、ループ先頭で設定済みのものを
                        // 使う (scriptRed/Green/Blue/Alpha がループ内で変わったら更新)。
                        if (scriptRed != gr || scriptGreen != gg || scriptBlue != gb || scriptAlpha != ga) {
                            RenderSystem.setShaderColor(scriptRed / 255f * lightFactor, scriptGreen / 255f * lightFactor, scriptBlue / 255f * lightFactor, scriptAlpha / 255f);
                            gr = scriptRed; gg = scriptGreen; gb = scriptBlue; ga = scriptAlpha;
                        }
                        // 不透明 pass(translucent=false)は片面(cull)。車両下面の裏向きの面が見えて
                        // 黒い塊(影に見える)になるのを防ぐ。SL の「後ろから消える」は body-数字 の
                        // 角度バリアント除外が原因で別途修正済みなので、片面でも消えない。
                        // 半透明 pass(AlphaBlend ブレンド)は両面にして窓を外/内どちらからも見せる。
                        int desiredCull = translucent ? 0 : 1;
                        if (desiredCull != lastCullMode) {
                            lastCullMode = desiredCull;
                            if (desiredCull == 1) {
                                RenderSystem.enableCull();
                            } else {
                                RenderSystem.disableCull();
                            }
                        }
                        // RTM 2パス: opaque pass(translucent=false)はブレンド無効(シェーダが
                        // alpha<0.1 を discard するので穴も正しく抜け、残りは不透明・深度書き込み)。
                        // translucent pass(translucent=true)はブレンド有効で AlphaBlend を重ね描画。
                        int desiredBlend = translucent ? 1 : 0;
                        if (desiredBlend != lastBlendMode) {
                            lastBlendMode = desiredBlend;
                            if (desiredBlend == 1) {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.SRC_ALPHA,
                                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE,
                                    GlStateManager.DestFactor.ZERO);
                                // 半透明 batch でも深度書き込みは ON にする。京急のように外板+窓が
                                // 1テクスチャ(glassBand で batch 全体が半透明扱い)の車両では、深度を
                                // 書かないとボディ外板まで透けて「スケスケ」になる(ユーザー報告)。
                                // 深度ONなら外板(alpha≈255)は不透明として深度を持ち透けない。窓
                                // (alpha中間)はブレンドで半透明色が出る(背景が透ける)。「窓越しに内装」は
                                // 諦めるが、ボディ消失感より軽微。
                                RenderSystem.depthMask(true);
                            } else if (desiredBlend == 2) {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.SRC_ALPHA,
                                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE,
                                    GlStateManager.DestFactor.ZERO);
                                RenderSystem.depthMask(true);
                            } else {
                                RenderSystem.disableBlend();
                                RenderSystem.depthMask(true);
                            }
                        }
                        // GPU VBO 高速経路: depthBias=0 かつ scriptRenderer の UV 変換が無ければ
                        // 静的 VBO を再利用。
                        boolean canUseStaticVbo = depthBias == 0.0F
                            && (scriptRenderer == null || !scriptRenderer.hasUvWindow());
                        com.mojang.blaze3d.vertex.VertexBuffer cachedVbo = canUseStaticVbo
                            ? batch.getOrBuildFullbrightVbo()
                            : null;
                        if (cachedVbo != null && !cachedVbo.isInvalid()) {
                            // drawWithShader の第1引数は完全な ModelView 行列が必要。
                            // poseStack.last().pose() はエンティティローカル変換のみ (カメラ view を含まない) なので、
                            // RenderSystem の ModelView を左から掛けないとカメラ空間描画になりカメラに追従する。
                            org.joml.Matrix4f mv = new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
                            cachedVbo.bind();
                            cachedVbo.drawWithShader(
                                mv,
                                RenderSystem.getProjectionMatrix(),
                                net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader());
                            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                        } else {
                            // CPU フォールバック (scriptRenderer の UV/色変換が必要なフレーム等)
                            BufferBuilder builder = Tesselator.getInstance().begin(
                                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                            Matrix4f mat = poseStack.last().pose();
                            for (int i = 0; i < batch.vertexCount; i++) {
                                int o = i * 8;
                                float x = batch.data[o], y = batch.data[o + 1], z = batch.data[o + 2];
                                float nx = batch.data[o + 3], ny = batch.data[o + 4], nz = batch.data[o + 5];
                                float u = batch.data[o + 6], v = batch.data[o + 7];
                                if (depthBias != 0.0F) {
                                    float il = (float)(1.0D / Math.sqrt(Math.max(1.0E-8F, nx*nx + ny*ny + nz*nz)));
                                    x += nx*il*depthBias; y += ny*il*depthBias; z += nz*il*depthBias;
                                }
                                if (scriptRenderer != null) {
                                    u = scriptRenderer.mapU(u, batch.minU, batch.maxU);
                                    v = scriptRenderer.mapV(v, batch.minV, batch.maxV);
                                }
                                float tx = mat.m00()*x + mat.m10()*y + mat.m20()*z + mat.m30();
                                float ty = mat.m01()*x + mat.m11()*y + mat.m21()*z + mat.m31();
                                float tz = mat.m02()*x + mat.m12()*y + mat.m22()*z + mat.m32();
                                builder.addVertex(tx, ty, tz).setUv(u, v).setColor(255, 255, 255, 255);
                            }
                            if (batch.vertexCount > 0) {
                                BufferUploader.drawWithShader(builder.buildOrThrow());
                            }
                        }
                    } else {
                        // Lightmap-aware path: block entities (rails, installed objects)
                        // メタセコイア同様の片面 (cull) 表示。
                        RenderType renderType = needsBlend
                            ? RenderType.entityTranslucentCull(texture)
                            : RenderType.entityCutout(texture);
                        VertexConsumer consumer = buffer.getBuffer(renderType);
                        PoseStack.Pose pose = poseStack.last();
                        Matrix4f mat = pose.pose();
                        Matrix3f norm = pose.normal();
                        float[] normalOut = new float[3];
                        for (int i = 0; i < batch.vertexCount; i++) {
                            int o = i * 8;
                            float x = batch.data[o], y = batch.data[o + 1], z = batch.data[o + 2];
                            float nx = batch.data[o + 3], ny = batch.data[o + 4], nz = batch.data[o + 5];
                            float u = batch.data[o + 6], v = batch.data[o + 7];
                            if (depthBias != 0.0F) {
                                float il = (float)(1.0D / Math.sqrt(Math.max(1.0E-8F, nx*nx + ny*ny + nz*nz)));
                                x += nx*il*depthBias; y += ny*il*depthBias; z += nz*il*depthBias;
                            }
                            if (scriptRenderer != null) {
                                u = scriptRenderer.mapU(u, batch.minU, batch.maxU);
                                v = scriptRenderer.mapV(v, batch.minV, batch.maxV);
                            }
                            float tnx = norm.m00()*nx + norm.m10()*ny + norm.m20()*nz;
                            float tny = norm.m01()*nx + norm.m11()*ny + norm.m21()*nz;
                            float tnz = norm.m02()*nx + norm.m12()*ny + norm.m22()*nz;
                            normalizeNormal(tnx, tny, tnz, normalOut);
                            consumer.addVertex(mat, x, y, z)
                                .setColor(scriptRed, scriptGreen, scriptBlue, scriptAlpha)
                                .setUv(u, v)
                                .setOverlay(overlay)
                                .setLight(packedLight)
                                .setNormal(normalOut[0], normalOut[1], normalOut[2]);
                        }
                    }
                } finally {
                    if (willTransform) {
                        poseStack.popPose();
                    }
                }
            }

            if (fullbright) {
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay) {
            render(poseStack, buffer, packedLight, overlay, null, null, null);
        }

        public void renderPreferScript(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                       GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
            // 元の実装は renderPreferScript 内で script を実行し、その後 render(7-arg) を呼んで
            // 再度 script を実行 + baked render する2段構えだった。これにより 2回目の
            // resetRenderStatistics() が scriptedOpaqueGroups をクリアし、baked filter
            // チェック時に hasScriptRenderedGroups=false となって登録グループまで baked が描画する
            // → 全 body 変形が重なる現象が発生していた。
            // 修正: 1段で完結させる。script を実行し、その直後に baked filter を組み立てて baked render を呼ぶ。
            boolean hasScript = scriptEngine != null;
            // 半透明の遅延描画は一旦無効化。baked 描画後に直接GLでフラッシュすると描画順/ブレンド
            // 状態が乱れ、KQパックで床下の黒い影が再発し半透明が崩れたため(ユーザー報告)。
            // 深度書き込みoff(desiredBlend==1)のみの安定状態に戻す。窓越しの内装は見えにくいが
            // 影/崩れより軽微。今後やるなら baked と同じ buffer 経由でソートする方式が必要。
            boolean deferTrans = false;
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.resetRenderStatistics();
                }
                if (deferTrans) {
                    scriptRenderer.setDeferTranslucent(true);
                }
                if (hasScript) {
                    // script の render() を全 pass (0/1/2/3) で呼ぶ。 pack の script は
                    // pass=0 で body opaque、 pass=1 で translucent 装飾 (KQ前面の黒)、
                    // pass=2 で emissive (前照灯) を描画するロジックを持つことがあるため。
                    // 同じ group の重複描画は renderRegisteredGroups 側で 「同 pass で再描画させない」
                    // チェックを入れて防ぐ (todo set + scriptedOpaque/TranslucentGroups)。
                    for (int pass = 0; pass < LEGACY_SCRIPT_PASS_COUNT; pass++) {
                        if (shouldSkipObservedLegacyPass(pass)) continue;
                        if (pass == 1 && scriptRenderer != null && !scriptRenderer.hasAlphaPassContent()) continue;
                        if (pass >= 2 && scriptRenderer != null && !scriptRenderer.hasEmissivePassContent()) continue;
                        if (pass >= 2 && entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity te) {
                            if (!te.isInteriorLightOn()) continue;
                        }
                        poseStack.pushPose();
                        try {
                            executeScript(poseStack, buffer, packedLight, overlay, pass, entity);
                        } finally {
                            try { poseStack.popPose(); } catch (Throwable ignored) {}
                        }
                    }
                }
                // baked render filter 組み立て (scriptedOpaqueGroups を 使う前にクリアしない)
                GroupPredicate opaqueFilter = groupFilter;
                GroupPredicate translucentFilter = groupFilter;
                if (hasScript && scriptRenderer != null && bakedFilterLogCount < 3) {
                    bakedFilterLogCount++;
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                        "[BakedFilter:preferScript] hasScriptRenderedGroups={}",
                        scriptRenderer.hasScriptRenderedGroups());
                }
                if (hasScript && scriptRenderer != null && scriptRenderer.hasScriptRenderedGroups()) {
                    opaqueFilter = groupName ->
                        (groupFilter == null || groupFilter.shouldRender(groupName))
                            && scriptRenderer.shouldRenderBakedGroup(groupName, false);
                    translucentFilter = groupName ->
                        (groupFilter == null || groupFilter.shouldRender(groupName))
                            && scriptRenderer.shouldRenderBakedGroup(groupName, true);
                }
                // baked render 前に currentPass を 0 にリセットする。
                // スクリプトの最終 pass (emissive = 2) が残ったまま renderInternal を
                // 呼ぶと renderSelectedBatches 内で scriptPass>=2 と判定され、
                // emissiveTexture が本来不要なバッチにも適用されてしまう。
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
                if (hasOpaqueBatches()) {
                    renderInternal(poseStack, buffer, packedLight, overlay, false, opaqueFilter, groupTransform, scriptRenderer, entity);
                }
                if (hasTranslucentBatches() || (scriptRenderer != null && scriptRenderer.hasAlphaPassContent())) {
                    renderInternal(poseStack, buffer, packedLight, overlay, true, translucentFilter, groupTransform, scriptRenderer, entity);
                }
                // 全不透明(script + baked)描画後に、溜めておいた半透明を最後に一括描画する。
                if (deferTrans) {
                    scriptRenderer.flushDeferredTranslucent(poseStack, buffer);
                    scriptRenderer.setDeferTranslucent(false);
                }
            } finally {
                if (deferTrans) {
                    scriptRenderer.setDeferTranslucent(false);
                }
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
            }
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, GroupPredicate groupFilter) {
            render(poseStack, buffer, packedLight, overlay, groupFilter, null, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, GroupPredicate groupFilter, GroupTransform groupTransform) {
            render(poseStack, buffer, packedLight, overlay, groupFilter, groupTransform, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                           GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
            boolean hasScript = scriptEngine != null;
            boolean scriptRendered = false;
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.resetRenderStatistics();
                }
                if (hasScript) {
                    for (int pass = 0; pass < LEGACY_SCRIPT_PASS_COUNT; pass++) {
                        // スクリプトが poseStack を破壊する事例 (rotate/translate を push/pop なしで多用、
                        // NaN を渡す等) に対する安全網。push/pop で囲んで corruption を局所化する。
                        poseStack.pushPose();
                        try {
                            scriptRendered |= executeScript(poseStack, buffer, packedLight, overlay, pass, entity);
                        } finally {
                            try { poseStack.popPose(); } catch (Throwable ignored) {}
                        }
                    }
                }
            } finally {
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
            }
            GroupPredicate opaqueFilter = groupFilter;
            GroupPredicate translucentFilter = groupFilter;
            // baked render の filter 適用状態を一度だけ可視化する。
            if (hasScript && scriptRenderer != null && bakedFilterLogCount < 3) {
                bakedFilterLogCount++;
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                    "[BakedFilter] hasScriptRenderedGroups={}",
                    scriptRenderer.hasScriptRenderedGroups());
            }
            if (hasScript && scriptRenderer != null && scriptRenderer.hasScriptRenderedGroups()) {
                opaqueFilter = groupName ->
                    (groupFilter == null || groupFilter.shouldRender(groupName))
                        && scriptRenderer.shouldRenderBakedGroup(groupName, false);
                translucentFilter = groupName ->
                    (groupFilter == null || groupFilter.shouldRender(groupName))
                        && scriptRenderer.shouldRenderBakedGroup(groupName, true);
            }
            if (hasOpaqueBatches()) {
                renderInternal(poseStack, buffer, packedLight, overlay, false, opaqueFilter, groupTransform, scriptRenderer, entity);
            }
            if (hasTranslucentBatches() || (scriptRenderer != null && scriptRenderer.hasAlphaPassContent())) {
                renderInternal(poseStack, buffer, packedLight, overlay, true, translucentFilter, groupTransform, scriptRenderer, entity);
            }
        }

        private void renderColorOverlay(PoseStack poseStack, MultiBufferSource buffer, int overlay,
                                        GroupPredicate groupFilter, int red, int green, int blue, int alpha) {
            final float surfaceBias = 0.0025F;
            for (Batch batch : batches) {
                if (groupFilter != null && !groupFilter.shouldRender(batch.groupName)) continue;
                ResourceLocation texture = batch.texture != null ? batch.texture : fallbackTexture();
                VertexConsumer consumer = buffer.getBuffer(
                    batch.translucent ? RenderType.entityTranslucentCull(texture) : RenderType.entityCutout(texture)
                );
                PoseStack.Pose pose = poseStack.last();
                Matrix4f mat = pose.pose();
                Matrix3f norm = pose.normal();
                float[] normalized = new float[3];
                for (int i = 0; i < batch.vertexCount; i++) {
                    int o = i * 8;
                    float vx = batch.data[o];
                    float vy = batch.data[o + 1];
                    float vz = batch.data[o + 2];
                    float nx = batch.data[o + 3];
                    float ny = batch.data[o + 4];
                    float nz = batch.data[o + 5];
                    float localLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (localLength > 1.0E-6F) {
                        float scale = surfaceBias / localLength;
                        vx += nx * scale;
                        vy += ny * scale;
                        vz += nz * scale;
                    }
                    float tnx = norm.m00() * nx + norm.m10() * ny + norm.m20() * nz;
                    float tny = norm.m01() * nx + norm.m11() * ny + norm.m21() * nz;
                    float tnz = norm.m02() * nx + norm.m12() * ny + norm.m22() * nz;
                    normalizeNormal(tnx, tny, tnz, normalized);
                    consumer.addVertex(mat, vx, vy, vz)
                        .setColor(red, green, blue, alpha)
                        .setUv(batch.data[o + 6], batch.data[o + 7])
                        .setOverlay(overlay)
                        .setLight(0x00F000F0)
                        .setNormal(normalized[0], normalized[1], normalized[2]);
                }
            }
        }

        private static void softenNormalForVanilla(float nx, float ny, float nz, float[] out) {
            // Original RTM called glDisable(GL_LIGHTING) before rendering,
            // so all faces were at full brightness regardless of normal direction.
            // Using top-facing normal (0,1,0) ensures the maximum brightness
            // multiplier (1.0) is applied to every face, reproducing that behavior.
            out[0] = 0.0F;
            out[1] = 1.0F;
            out[2] = 0.0F;
        }

        private static int resolveVertexLight(Object entity, String lowerGroupName, int packedLight) {
            return packedLight;
        }

        /**
         * packedLight から「フラット明るさ係数」(0..1) を計算する。ライトマップを使えない
         * 直接GL/VBO 経路で、周囲の明るさを setShaderColor 乗算で疑似再現するため。
         * バニラのライトマップ計算(LightTexture.updateLightTexture)の主要項のみを近似:
         * sky 寄与 = getBrightness(sky) * (getSkyDarken*0.95+0.05)、block 寄与 = getBrightness(block)。
         * 細かなフリッカ/ガンマ/暗黒効果は無視(フラット表示なので十分)。
         */
        private static float computeFlatBrightness(int packedLight) {
            try {
                Minecraft mc = Minecraft.getInstance();
                net.minecraft.client.multiplayer.ClientLevel level = mc.level;
                if (level == null) return 1.0F;
                net.minecraft.world.level.dimension.DimensionType dim = level.dimensionType();
                int blockLevel = net.minecraft.client.renderer.LightTexture.block(packedLight);
                int skyLevel = net.minecraft.client.renderer.LightTexture.sky(packedLight);
                float skyMul = level.getSkyDarken(1.0F) * 0.95F + 0.05F;
                float skyB = net.minecraft.client.renderer.LightTexture.getBrightness(dim, skyLevel) * skyMul;
                float blockB = net.minecraft.client.renderer.LightTexture.getBrightness(dim, blockLevel);
                float b = Math.max(skyB, blockB);
                // 完全な暗黒で真っ黒にならないよう下限を少し残す(バニラも 0.04 程度のグレー混合がある)。
                return net.minecraft.util.Mth.clamp(b, 0.05F, 1.0F);
            } catch (Throwable ignored) {
                return 1.0F;
            }
        }

        private static void normalizeNormal(float nx, float ny, float nz, float[] out) {
            float lenSq = nx * nx + ny * ny + nz * nz;
            if (lenSq <= 1.0E-8F) {
                out[0] = 0.0F;
                out[1] = 1.0F;
                out[2] = 0.0F;
                return;
            }
            float invLen = (float) (1.0D / Math.sqrt(lenSq));
            out[0] = nx * invLen;
            out[1] = ny * invLen;
            out[2] = nz * invLen;
        }

        private static boolean hasActiveShaderPipeline() {
            long now = System.currentTimeMillis();
            if (now < shaderPipelineCacheUntilMillis) {
                return shaderPipelineCacheValue;
            }
            boolean active = false;
            try {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
                Object inUse = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
                if (inUse instanceof Boolean enabled) {
                    active = enabled;
                }
            } catch (Throwable ignored) {
            }
            shaderPipelineCacheValue = active;
            shaderPipelineCacheUntilMillis = now + 1000L;
            return active;
        }

        private static boolean shouldRenderReflectionOverlay(Object entity, Batch batch, String lowerGroupName, boolean scriptTexture) {
            return false;
        }

        private static boolean isGlassGroup(String lowerGroupName) {
            return lowerGroupName.contains("glass")
                || lowerGroupName.contains("window")
                || lowerGroupName.contains("wind");
        }

        private static boolean isLightGroup(String lowerGroupName) {
            return lowerGroupName.contains("light")
                || lowerGroupName.contains("lamp")
                || lowerGroupName.contains("marker");
        }

        private static boolean isInteriorGroup(String lowerGroupName) {
            return lowerGroupName.contains("seat")
                || lowerGroupName.contains("chair")
                || lowerGroupName.contains("bogie")
                || lowerGroupName.contains("wheel")
                || lowerGroupName.contains("pantograph")
                || lowerGroupName.contains("under")
                || lowerGroupName.contains("floor")
                || lowerGroupName.contains("panel");
        }

        private static boolean isBodyGroup(String lowerGroupName) {
            return !lowerGroupName.isBlank();
        }

        private static float getDepthBias(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture) {
                return 0.0F;
            }
            if (isCabControlGroup(lowerGroupName)) {
                return 0.0F;
            }
            if (isScriptDisplayGroup(lowerGroupName)) {
                return 0.0002F;
            }
            if (isLegacyDisplayGroup(lowerGroupName)) {
                return 0.0002F;
            }
            if (isLightGroup(lowerGroupName)) {
                return 0.0010F;
            }
            if (!batch.translucent) {
                return 0.0F;
            }
            if (lowerGroupName.equals("alpha")) {
                return 0.0012F;
            }
            return 0.0F;
        }

        private static boolean shouldForceLegacyAlphaCutout(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent) {
                return false;
            }
            if (lowerGroupName.contains("mask") && !isLegacyDisplayGroup(lowerGroupName)) {
                return true;
            }
            // テクスチャが明確なガラス帯(中間アルファのまとまり)を持つなら本当の半透明
            // (ガラス窓・煙等)。グループ名に window/glass が無くても強制カットアウトしない。
            // 二値カットアウトの SL 車体はガラス帯を持たない(false)ので従来通り不透明維持。
            if (batch.glassTranslucent) {
                return false;
            }
            // RTM 系 SL/D51 等: ボディテクスチャが AlphaBlend で登録されていても、
            // 実体はアルファテスト用（ロッドの隙間など）。ブレンドで描画すると深度書き込みが
            // 切れ、車体越しに反対側のパーツが透けて見える。
            // 明確な透過キーワード (glass/window/alpha/trans) を持たないグループは
            // カットアウト扱いに切り替える。
            if (isLegacyDisplayGroup(lowerGroupName) || isScriptDisplayGroup(lowerGroupName)) {
                return false;
            }
            boolean hasTransparencyKeyword = lowerGroupName.contains("glass")
                || lowerGroupName.contains("window")
                || lowerGroupName.contains("alpha")
                || lowerGroupName.contains("trans")
                || lowerGroupName.equals("a");
            if (!hasTransparencyKeyword) {
                return true;
            }
            return false;
        }

        private static boolean shouldForceDisplayCutout(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent) {
                return false;
            }
            return isLegacyDisplayGroup(lowerGroupName) || isScriptDisplayGroup(lowerGroupName);
        }

        private static boolean shouldForceCabCutout(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent) {
                return false;
            }
            return isCabControlGroup(lowerGroupName) || lowerGroupName.equals("cabpanel");
        }

        private static boolean shouldForceShaderSafeCutout(Object entity, Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent || !hasActiveShaderPipeline()) {
                return false;
            }
            if (!(entity instanceof LargeRailCoreBlockEntity) && !(entity instanceof InstalledObjectBlockEntity)) {
                return false;
            }
            if (isGlassGroup(lowerGroupName) || isLegacyDisplayGroup(lowerGroupName)) {
                return false;
            }
            return true;
        }

        private static boolean isLegacyDisplayGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            return lowerGroupName.equals("dest")
                || lowerGroupName.equals("type")
                || lowerGroupName.startsWith("dest") && lowerGroupName.length() > 4 && lowerGroupName.substring(4).chars().allMatch(Character::isDigit)
                || lowerGroupName.startsWith("type") && lowerGroupName.length() > 4 && lowerGroupName.substring(4).chars().allMatch(Character::isDigit);
        }

        private static boolean isScriptDisplayGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            return lowerGroupName.equals("sign")
                || lowerGroupName.startsWith("type_")
                || lowerGroupName.matches("s_[td][ab]\\d+");
        }

        private static boolean isCabControlGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            return lowerGroupName.equals("f")
                || lowerGroupName.equals("m")
                || lowerGroupName.equals("b")
                || lowerGroupName.equals("n")
                || lowerGroupName.equals("eb")
                || lowerGroupName.matches("b[1-7]")
                || lowerGroupName.matches("p[1-5]");
        }

        private static int computeReflectionAlpha(Matrix4f pose, float nx, float ny, float nz, float x, float y, float z, boolean glass) {
            float normalLenSq = nx * nx + ny * ny + nz * nz;
            if (normalLenSq <= 1.0E-8F) {
                return 0;
            }
            float normalInvLen = (float) (1.0D / Math.sqrt(normalLenSq));
            nx *= normalInvLen;
            ny *= normalInvLen;
            nz *= normalInvLen;

            float vx = pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30();
            float vy = pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31();
            float vz = pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32();
            float viewLenSq = vx * vx + vy * vy + vz * vz;
            if (viewLenSq <= 1.0E-8F) {
                return 0;
            }
            float viewInvLen = (float) (1.0D / Math.sqrt(viewLenSq));
            vx = -vx * viewInvLen;
            vy = -vy * viewInvLen;
            vz = -vz * viewInvLen;

            float fresnel = 1.0F - Math.max(0.0F, nx * vx + ny * vy + nz * vz);
            fresnel *= fresnel;
            fresnel *= fresnel;
            float skyBias = Math.max(0.0F, ny) * 0.35F;
            float strength = glass ? 0.20F : 0.06F;
            float alpha = (fresnel * 0.8F + skyBias) * strength;
            int maxAlpha = glass ? 52 : 24;
            return Math.min(maxAlpha, Math.max(0, Math.round(alpha * 255.0F)));
        }

        private boolean shouldSkipLegacyPlaceholderGroup(String groupName) {
            if (groupName == null || groupName.isBlank()) {
                return false;
            }
            String lower = groupName.trim().toLowerCase(Locale.ROOT);
            if (lower.equals("dest") && hasGroupNamed("dest0")) {
                return true;
            }
            if (lower.equals("type") && hasGroupNamed("type0")) {
                return true;
            }
            return lower.equals("lever") && (
                hasGroupNamed("L_F")
                    || hasGroupNamed("L_M")
                    || hasGroupNamed("L_B")
            );
        }
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupPredicate groupFilter) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter);
    }

    public static void renderModelWithoutScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, boolean translucent, GroupPredicate groupFilter, TrainScriptSystem.ScriptModelRenderer renderer) {
        if (model == null) return;
        model.renderInternal(poseStack, buffer, packedLight, overlay, translucent, groupFilter, null, renderer, null);
    }

    public static void renderModelWithoutScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer,
                                                int packedLight, int overlay, boolean translucent,
                                                GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.renderInternal(poseStack, buffer, packedLight, overlay, translucent, groupFilter, groupTransform, model.scriptRenderer, entity);
    }

    public static void renderModelColorOverlay(MqoModel model, PoseStack poseStack, MultiBufferSource buffer,
                                               int overlay, GroupPredicate groupFilter,
                                               int red, int green, int blue, int alpha) {
        if (model == null) return;
        model.renderColorOverlay(poseStack, buffer, overlay, groupFilter, red, green, blue, alpha);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupPredicate groupFilter, GroupTransform groupTransform) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter, groupTransform);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                   GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.renderPreferScript(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter, groupTransform, entity);
    }

    private static final class Batch {
        final int order;
        final String groupName;
        final String groupNameLower;
        final ResourceLocation texture;
        final ResourceLocation[] emissiveTextures;
        final boolean translucent;
        final int materialId;
        /** マテリアル col の不透明度 (1.0=不透明)。半透明ガラス等は <1。描画時に色αへ乗算。 */
        float baseAlpha = 1.0F;
        /** テクスチャが明確なガラス帯を持つ=本当の半透明。強制カットアウトを免除する。 */
        boolean glassTranslucent = false;
        /** RTM pass0(不透明描画)用のアルファテスト相当テクスチャ。 */
        ResourceLocation opaqueTexture = null;
        final float[] data;
        final int vertexCount;
        // scriptTexture=false 時の事前計算結果。SL/通常列車は大半の batch で
        // scriptTexture=false なので毎フレームの string 操作 (contains/startsWith)
        // を 1 度の構築時計算に置換できる。
        boolean cachedForceCutoutNoScriptTex;
        float cachedDepthBiasNoScriptTex;
        boolean cachedComputed;
        final float minU;
        final float maxU;
        final float minV;
        final float maxV;

        // GPU VBO キャッシュ。フルブライト経路の同じ batch を毎フレーム再
        // ビルドしていた CPU コストを 1 回ビルド + GPU 側 modelview 変換に
        // 置き換える。scriptRenderer のテクスチャ/UV/色変更が無い場合のみ使用。
        volatile com.mojang.blaze3d.vertex.VertexBuffer fullbrightVbo;
        volatile boolean vboBuildAttempted;
        volatile boolean vboBuildFailed;

        Batch(int order, String groupName, ResourceLocation texture, ResourceLocation[] emissiveTextures, float[] data, int vertexCount, int materialId, boolean translucent,
              float minU, float maxU, float minV, float maxV) {
            this.order = order;
            this.groupName = groupName;
            this.groupNameLower = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
            this.texture = texture;
            this.emissiveTextures = emissiveTextures == null ? new ResourceLocation[0] : emissiveTextures;
            this.translucent = translucent;
            this.materialId = materialId;
            this.data = data;
            this.vertexCount = vertexCount;
            this.minU = minU;
            this.maxU = maxU;
            this.minV = minV;
            this.maxV = maxV;
        }

        ResourceLocation emissiveTextureForPass(int pass) {
            int index = pass - 2;
            if (index < 0 || index >= emissiveTextures.length) {
                return null;
            }
            return emissiveTextures[index];
        }

        /**
         * フルブライト経路用 VBO を取得。bias != 0 の場合は法線方向に bias 分シフトした
         * 頂点で VBO を構築 (depthBias=0.001 等の light/display グループも GPU 化される)。
         * bias 値ごとに別 VBO をキャッシュ。
         */
        com.mojang.blaze3d.vertex.VertexBuffer getOrBuildFullbrightVbo() {
            return getOrBuildFullbrightVbo(0.0F);
        }

        private volatile java.util.Map<Float, com.mojang.blaze3d.vertex.VertexBuffer> biasedVbos;

        com.mojang.blaze3d.vertex.VertexBuffer getOrBuildFullbrightVbo(float bias) {
            if (bias == 0.0F) {
                if (vboBuildFailed) return null;
                if (fullbrightVbo != null) return fullbrightVbo;
                synchronized (this) {
                    if (fullbrightVbo == null && !vboBuildFailed) {
                        vboBuildAttempted = true;
                        com.mojang.blaze3d.vertex.VertexBuffer vbo = buildVbo(0.0F);
                        if (vbo == null) vboBuildFailed = true;
                        else fullbrightVbo = vbo;
                    }
                }
                return fullbrightVbo;
            }
            // bias != 0: 別キャッシュ
            java.util.Map<Float, com.mojang.blaze3d.vertex.VertexBuffer> map = biasedVbos;
            if (map != null) {
                com.mojang.blaze3d.vertex.VertexBuffer cached = map.get(bias);
                if (cached != null) return cached;
            }
            synchronized (this) {
                if (biasedVbos == null) biasedVbos = new java.util.HashMap<>(2);
                com.mojang.blaze3d.vertex.VertexBuffer cached = biasedVbos.get(bias);
                if (cached != null) return cached;
                com.mojang.blaze3d.vertex.VertexBuffer vbo = buildVbo(bias);
                if (vbo != null) biasedVbos.put(bias, vbo);
                return vbo;
            }
        }

        private com.mojang.blaze3d.vertex.VertexBuffer buildVbo(float bias) {
            if (vertexCount <= 0) return null;
            try {
                com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
                for (int i = 0; i < vertexCount; i++) {
                    int o = i * 8;
                    float x = data[o], y = data[o + 1], z = data[o + 2];
                    float nx = data[o + 3], ny = data[o + 4], nz = data[o + 5];
                    float u = data[o + 6], v = data[o + 7];
                    if (bias != 0.0F) {
                        float il = (float)(1.0D / Math.sqrt(Math.max(1.0E-8F, nx*nx + ny*ny + nz*nz)));
                        x += nx*il*bias; y += ny*il*bias; z += nz*il*bias;
                    }
                    bb.addVertex(x, y, z).setUv(u, v).setColor(255, 255, 255, 255);
                }
                com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
                if (mesh == null) return null;
                com.mojang.blaze3d.vertex.VertexBuffer vbo = new com.mojang.blaze3d.vertex.VertexBuffer(
                    com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(mesh);
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                return vbo;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static final class CachedModel {
        private final MqoModel model;
        private final long estimatedBytes;
        private long lastAccessNanos;

        CachedModel(MqoModel model, long estimatedBytes, long lastAccessNanos) {
            this.model = model;
            this.estimatedBytes = Math.max(1L, estimatedBytes);
            this.lastAccessNanos = lastAccessNanos;
        }

        MqoModel model() {
            return model;
        }

        long estimatedBytes() {
            return estimatedBytes;
        }

        long lastAccessNanos() {
            return lastAccessNanos;
        }

        void touch(long now) {
            this.lastAccessNanos = now;
        }
    }

    public static final class ScriptModel {
        public final ScriptMaterialTexture[] textures;
        // 直前にレンダー中の renderer を保持。MqoModel.renderPreferScript / render が
        // executeScript 直前に setActiveRenderer() で差し替える。
        // RTM のレンダースクリプトは model.renderPart("groupName") で部品描画を要求するため、
        // ScriptModel 側からも renderer に処理を委譲できる必要がある。
        private transient com.portofino.realtrainmodunofficial.script.TrainScriptSystem.ScriptModelRenderer activeRenderer;

        ScriptModel(List<ResourceLocation> materialTextures) {
            this.textures = new ScriptMaterialTexture[materialTextures.size()];
            for (int i = 0; i < materialTextures.size(); i++) {
                this.textures[i] = new ScriptMaterialTexture(new ScriptMaterial(materialTextures.get(i)));
            }
        }

        public void setActiveRenderer(com.portofino.realtrainmodunofficial.script.TrainScriptSystem.ScriptModelRenderer renderer) {
            this.activeRenderer = renderer;
        }

        // ---- 旧 RTM レンダースクリプト用 API ----
        public void renderPart(String group) {
            if (activeRenderer != null && group != null) activeRenderer.renderParts(group);
        }
        public void renderParts(Object groups) {
            if (activeRenderer != null && groups != null) activeRenderer.renderParts(groups);
        }
        public void renderAll() {
            // renderAll 相当: 空文字列で renderParts を呼ぶと全部品扱いの仕様。
            if (activeRenderer != null) activeRenderer.renderParts("*");
        }
        public void renderOnly(Object groups) {
            if (activeRenderer != null && groups != null) activeRenderer.renderParts(groups);
        }
        public void render(Object groups) {
            renderParts(groups);
        }
    }

    public static final class ScriptMaterialTexture {
        public ScriptMaterial material;

        ScriptMaterialTexture(ScriptMaterial material) {
            this.material = material;
        }
    }

    public static final class ScriptMaterial {
        public Object texture;

        ScriptMaterial(ResourceLocation texture) {
            this.texture = new ScriptTexture(texture);
        }
    }

    public static final class ScriptTexture {
        public String namespace;
        public String domain;
        public String path;
        public String resourcePath;

        ScriptTexture(ResourceLocation resource) {
            this.namespace = resource.getNamespace();
            this.domain = this.namespace;
            this.path = resource.getPath();
            this.resourcePath = this.path;
        }

        public String func_110624_b() {
            return namespace;
        }

        public String func_110623_a() {
            return path;
        }
    }
}
