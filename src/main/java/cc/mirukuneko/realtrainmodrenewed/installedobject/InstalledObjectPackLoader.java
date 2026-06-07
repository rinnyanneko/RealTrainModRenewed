package cc.mirukuneko.realtrainmodrenewed.installedobject;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cc.mirukuneko.realtrainmodrenewed.BundledPackStore;
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader;
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class InstalledObjectPackLoader {
    private static final Pattern LIGHT_STATE_PATTERN = Pattern.compile("S\\((\\d+)\\)");
    private static final Pattern LIGHT_PARTS_PATTERN = Pattern.compile("P\\(([^)]+)\\)");
    private static final List<InstalledObjectDefinition> LOADED = new ArrayList<>();
    private static boolean loaded;

    private InstalledObjectPackLoader() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        LOADED.clear();
        try {
            loadFromModJar();
            loadDirectoryPacks(FMLPaths.GAMEDIR.get());
            loadArchiveDirectory(FMLPaths.GAMEDIR.get());
            Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
            if (Files.isDirectory(modsDir)) {
                loadDirectoryPacks(modsDir);
                loadArchiveDirectory(modsDir);
            }
            Path contentDir = FMLPaths.GAMEDIR.get().resolve("content");
            if (Files.isDirectory(contentDir)) {
                loadDirectoryPacks(contentDir);
                loadArchiveDirectory(contentDir);
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.warn("Could not scan installed object packs", e);
        }
        InstalledObjectRegistry.setDefinitions(LOADED);
        RealTrainModRenewed.LOGGER.info("Loaded {} installed object definition(s)", LOADED.size());
    }

    private static void loadFromModJar() {
        loadFromModJarDirect();
        try {
            for (Path path : BundledPackStore.listBundledPacks("rail")) {
                try (InputStream input = Files.newInputStream(path)) {
                    loadPack(input, path.getFileName().toString());
                }
            }
            for (Path path : BundledPackStore.listBundledPacks("installed_object")) {
                try (InputStream input = Files.newInputStream(path)) {
                    loadPack(input, path.getFileName().toString());
                }
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.warn("Could not scan bundled installed object packs", e);
        }
    }

    private static void loadFromModJarDirect() {
        try {
            var modFileEntry = ModList.get().getModFileById(RealTrainModRenewed.MODID);
            if (modFileEntry == null) return;
            var modFile = modFileEntry.getFile();
            Path jsonDir = modFile.getFilePath().resolve("assets/minecraft/models/json");
            if (jsonDir == null || !Files.isDirectory(jsonDir)) return;
            Path modRoot = modFile.getFilePath();
            if (modRoot == null) return;
            String packName = RealTrainModRenewed.MODID;
            RealTrainModRenewed.LOGGER.info("Loading built-in installed object definitions from {}", jsonDir);
            try (var stream = Files.list(jsonDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> isSupportedJson(normalize(p.getFileName().toString())))
                    .forEach(path -> {
                        try {
                            parse(normalize(path.getFileName().toString()), Files.readAllBytes(path), packName);
                        } catch (Exception e) {
                            RealTrainModRenewed.LOGGER.warn("Failed to load built-in installed object definition {}", path.getFileName(), e);
                        }
                    });
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.warn("Could not load built-in installed object definitions from mod JAR", e);
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadDirectoryPacks(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory)
                .filter(InstalledObjectPackLoader::looksLikeInstalledObjectPackDirectory)
                .forEach(path -> {
                    try {
                        loadPackDirectory(path, path.getFileName().toString());
                    } catch (Exception e) {
                        RealTrainModRenewed.LOGGER.warn("Failed to load installed object directory pack {}", path.getFileName(), e);
                    }
                });
        }
    }

    private static void loadArchiveDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(InstalledObjectPackLoader::isSupportedArchive)
                .forEach(path -> {
                    try (InputStream input = Files.newInputStream(path)) {
                        loadPack(input, path.getFileName().toString());
                    } catch (Exception e) {
                        RealTrainModRenewed.LOGGER.warn("Failed to load installed object pack {}", path.getFileName(), e);
                    }
                });
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".zip") || fileName.endsWith(".jar");
    }

    private static boolean looksLikeInstalledObjectPackDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        if (Files.exists(dir.resolve("assets")) || Files.exists(dir.resolve("models")) || Files.exists(dir.resolve("scripts"))) {
            return true;
        }
        try (var stream = Files.walk(dir, 4)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.endsWith(".json") && (
                    name.startsWith("modelmachine_")
                        || name.startsWith("modelsignal_")
                        || name.startsWith("modelconnector_")
                        || name.startsWith("modelwire_")
                        || name.startsWith("modelcrossing_")
                        || name.startsWith("signboard_")
                ));
        } catch (IOException e) {
            return false;
        }
    }

    private static void loadPack(InputStream zipInput, String packName) throws IOException {
        loadPack(zipInput, packName, 0);
    }

    private static void loadPack(InputStream zipInput, String packName, int depth) throws IOException {
        List<EntryData> entries = new ArrayList<>();
        List<NestedArchive> nestedArchives = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String normalized = normalize(entry.getName());
                    if (isSupportedJson(normalized)) {
                        entries.add(new EntryData(normalized, zip.readAllBytes()));
                    } else if (depth < 2 && isArchiveName(normalized)) {
                        nestedArchives.add(new NestedArchive(normalized, zip.readAllBytes()));
                    }
                }
                zip.closeEntry();
            }
        }
        for (EntryData entry : entries) {
            parse(entry.path(), entry.bytes(), packName);
        }
        for (NestedArchive nested : nestedArchives) {
            Path materialized = RailPackLoader.materializeNestedPack(nested.name(), nested.bytes());
            try (InputStream input = Files.newInputStream(materialized)) {
                loadPack(input, nested.name(), depth + 1);
            }
        }
    }

    private static void loadPackDirectory(Path packDir, String packName) throws IOException {
        try (var stream = Files.walk(packDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> isSupportedJson(normalize(packDir.relativize(path).toString())))
                .forEach(path -> {
                    try {
                        parse(normalize(packDir.relativize(path).toString()), Files.readAllBytes(path), packName);
                    } catch (Exception e) {
                        RealTrainModRenewed.LOGGER.warn("Failed to parse installed object json {} in {}", path, packName, e);
                    }
                });
        }
    }

    private static boolean isSupportedJson(String path) {
        String file = leaf(path).toLowerCase(Locale.ROOT);
        return file.endsWith(".json") && (
            file.startsWith("modelmachine_")
            || file.startsWith("modelsignal_")
            || file.startsWith("modelconnector_")
            || file.startsWith("modelwire_")
            || file.startsWith("modelcrossing_")
            || file.startsWith("signboard_")
        );
    }

    private static boolean isArchiveName(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static void parse(String path, byte[] bytes, String packName) {
        try {
            JsonElement element = JsonParser.parseString(PackTextDecoder.decodeJson(bytes));
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject obj = element.getAsJsonObject();
            String file = leaf(path);
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.startsWith("signboard_")) {
                parseSignboard(obj, packName, file);
                return;
            }

            InstalledObjectCategory category = categoryFor(obj, lower);
            JsonObject model = getObject(obj, "model");
            JsonObject modelPartsBody = getObject(obj, "modelPartsBody");
            String modelFile = firstNonBlank(model == null ? null : getString(model, "modelFile"), getString(obj, "signalModel"));
            if (modelFile == null || modelFile.isBlank()) {
                return;
            }
            String name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName"), file.replace(".json", ""));
            String id = category.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
            String scriptPath = firstNonBlank(model == null ? null : getString(model, "rendererPath"), getString(obj, "rendererPath"));
            String runningSound = firstNonBlank(
                model == null ? null : getString(model, "sound_Running"),
                model == null ? null : getString(model, "soundRunning"),
                getString(obj, "sound_Running"),
                getString(obj, "soundRunning")
            );
            Vec3 offset = parseVec3(model, "offset", 1.0 / 16.0);
            float scale = parseFloat(model, "scale", 1.0F);
            boolean smoothing = getBoolean(obj, "smoothing", true);
            Map<String, String> textures = new HashMap<>(parseTextures(model));
            if (category == InstalledObjectCategory.SIGNAL) {
                String signalTexture = getString(obj, "signalTexture");
                if (signalTexture != null && !signalTexture.isBlank()) {
                    textures.putIfAbsent("default", signalTexture);
                }
            }
            InstalledObjectDefinition def = new InstalledObjectDefinition(
                id,
                name,
                packName,
                category,
                modelFile,
                scriptPath,
                firstNonBlank(getString(obj, "buttonTexture"), model == null ? null : getString(model, "buttonTexture")),
                textures,
                offset,
                scale,
                smoothing,
                1.0F,
                1.0F,
                0.125F,
                "",
                (category == InstalledObjectCategory.SIGNAL || category == InstalledObjectCategory.LIGHT)
                    ? firstNonBlank(getString(obj, "lightTexture"), getString(obj, "emissiveTexture"), getString(obj, "buttonTexture"))
                    : "",
                runningSound,
                // 照明(LIGHT)も信号と同じ "lights": ["S(1) P(部品名)"] 形式で発光パーツを定義できる。
                (category == InstalledObjectCategory.SIGNAL || category == InstalledObjectCategory.LIGHT)
                    ? parseSignalLights(obj) : Map.of(),
                parseRenderObjects(model, modelPartsBody),
                parseVec3(modelPartsBody, "pos", 1.0),
                1,
                1
            );
            if (category == InstalledObjectCategory.WIRE) {
                // ワイヤーは sectionLength(モデル1個分の長さ)と deflectionCoefficient(たるみ)を持つ。
                float sectionLength = parseFloat(obj, "sectionLength", 0.5F);
                float deflection = parseFloat(obj, "deflectionCoefficient", 0.0F);
                def.setWireParams(sectionLength, deflection);
            }
            def.setWireAttachPos(parseVec3(obj, "wirePos", 1.0));
            LOADED.add(def);
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.warn("Failed to parse installed object json {} in {}: {}", path, packName, e.getMessage());
        }
    }

    private static void parseSignboard(JsonObject obj, String packName, String file) {
        String texture = normalizeSignboardTexture(getString(obj, "texture"));
        if (texture == null || texture.isBlank()) {
            return;
        }
        String name = file.replace(".json", "");
        String id = InstalledObjectCategory.SIGNBOARD.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
        int frame = (int) getDouble(obj, "frame", 1.0);
        int backTexture = (int) getDouble(obj, "backTexture", 1.0);
        LOADED.add(new InstalledObjectDefinition(
            id,
            name,
            packName,
            InstalledObjectCategory.SIGNBOARD,
            "",
            "",
            texture,
            Map.of(),
            Vec3.ZERO,
            1.0F,
            false,
            (float) getDouble(obj, "width", 1.0),
            (float) getDouble(obj, "height", 1.0),
            (float) getDouble(obj, "depth", 0.125),
            texture,
            "",
            "",
            Map.of(),
            Vec3.ZERO,
            frame,
            backTexture
        ));
    }

    private static String normalizeSignboardTexture(String texture) {
        if (texture == null || texture.isBlank()) {
            return "";
        }
        String normalized = normalize(texture);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return normalized.contains("/") ? normalized : "textures/signboard/" + normalized;
        }
        if (normalized.contains("/")) {
            return normalized + ".png";
        }
        return "textures/signboard/" + normalized + ".png";
    }

    private static InstalledObjectCategory categoryFor(JsonObject obj, String lowerFile) {
        String machineType = firstNonBlank(getString(obj, "machineType"), getString(obj, "MachineType")).toLowerCase(Locale.ROOT);
        String name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName")).toLowerCase(Locale.ROOT);
        String runningSound = firstNonBlank(
            getString(obj, "sound_Running"),
            getString(obj, "soundRunning"),
            getObject(obj, "model") == null ? null : getString(getObject(obj, "model"), "sound_Running"),
            getObject(obj, "model") == null ? null : getString(getObject(obj, "model"), "soundRunning")
        ).toLowerCase(Locale.ROOT);
        // モデル/レンダラのパスも分類材料に含める。masa 踏切パックの遮断機は
        // name/file が "MasaGate*"(=crossing を含まない)だが rendererPath が "MasaCrossingGate*.js"
        // なので、これを見ないと踏切でなく照明カテゴリに落ちて選択に出なくなる。
        JsonObject modelObjForCat = getObject(obj, "model");
        // getString はキーが無いと null を返すので null 安全に(以前は null.toLowerCase で NPE→分類失敗→
        // rendererPath/modelFile を持たない踏切がレッドストーンに反応しなくなっていた)。
        String rendererPathRaw = modelObjForCat == null ? null : getString(modelObjForCat, "rendererPath");
        String modelFileRaw = modelObjForCat == null ? null : getString(modelObjForCat, "modelFile");
        String rendererPath = rendererPathRaw == null ? "" : rendererPathRaw.toLowerCase(Locale.ROOT);
        String modelFile = modelFileRaw == null ? "" : modelFileRaw.toLowerCase(Locale.ROOT);
        boolean looksLikeCrossing = lowerFile.contains("crossing")
            || lowerFile.contains("fumikiri")
            || name.contains("crossing")
            || name.contains("fumikiri")
            || runningSound.contains("crossing")
            || runningSound.contains("fumikiri")
            || runningSound.contains("toryanse")
            || rendererPath.contains("crossing")
            || rendererPath.contains("fumikiri")
            || modelFile.contains("crossing")
            || modelFile.contains("fumikiri");
        boolean looksLikeSpeaker = lowerFile.contains("speaker")
            || name.contains("speaker")
            || machineType.contains("speaker");
        // 分類対象文字列(ファイル名+name+machineType)。RTM は設置物の大半が ModelMachine_ なので
        // プレフィックスでなくキーワードで種類を判定する。
        String hay = lowerFile + " " + name + " " + machineType;

        // 明示プレフィックスを最優先。
        if (lowerFile.startsWith("modelsignal_")) {
            return InstalledObjectCategory.SIGNAL;
        }
        if (lowerFile.startsWith("modelwire_")) {
            return InstalledObjectCategory.WIRE;
        }
        // 踏切は改札より先に判定する(CrossingGate を "gate" で改札に誤分類しないため)。
        if (lowerFile.startsWith("modelcrossing_") || looksLikeCrossing
                || containsAny(hay, "crossing", "fumikiri", "踏切", "toryanse")) {
            return InstalledObjectCategory.CROSSING;
        }
        // 改札(Turnstile / TicketGate / 改札)。"gate" 単独では拾わない。
        if (containsAny(hay, "turnstile", "ticketgate", "ticket_gate", "ticketmachine",
                "kaisatsu", "改札", "automaticgate", "iccard")) {
            return InstalledObjectCategory.TICKET_GATE;
        }
        if (looksLikeSpeaker || containsAny(hay, "speaker", "スピーカ")) {
            return InstalledObjectCategory.SPEAKER;
        }
        if (containsAny(hay, "linepole", "line_pole", "catenarypole", "catenary_pole",
                "poleglay", "架線柱", "架線")) {
            return InstalledObjectCategory.OVERHEAD_LINE_POLE;
        }
        if (containsAny(hay, "signboard", "sign_board", "billboard", "看板")) {
            return InstalledObjectCategory.SIGNBOARD;
        }
        // 照明系(明確なキーワードを持つものだけ)。これで照明カテゴリが何でも箱にならない。
        if (containsAny(hay, "light", "lamp", "lantern", "照明", "ライト", "beacon")) {
            return InstalledObjectCategory.LIGHT;
        }
        // それ以外の汎用 ModelMachine_(鳥居/モニタ/自販機等)は照明に置く(従来の落とし先)。
        if (lowerFile.startsWith("modelmachine_")) {
            return InstalledObjectCategory.LIGHT;
        }
        return InstalledObjectCategory.INSULATOR;
    }

    private static boolean containsAny(String hay, String... needles) {
        if (hay == null) return false;
        for (String n : needles) {
            if (hay.contains(n)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value.replace('\\', '/');
    }

    private static String leaf(String value) {
        int idx = value.lastIndexOf('/');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || key == null || key.isBlank()) {
            return null;
        }
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || key == null || key.isBlank()) {
            return null;
        }
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || key.isBlank()) {
            return fallback;
        }
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        if (object == null || key == null || key.isBlank()) {
            return fallback;
        }
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float parseFloat(JsonObject object, String key, float fallback) {
        return (float) getDouble(object, key, fallback);
    }

    private static Vec3 parseVec3(JsonObject object, String key, double scale) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return Vec3.ZERO;
        }
        JsonArray array = object.getAsJsonArray(key);
        if (array.size() < 3) {
            return Vec3.ZERO;
        }
        try {
            return new Vec3(array.get(0).getAsDouble() * scale, array.get(1).getAsDouble() * scale, array.get(2).getAsDouble() * scale);
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    private static Map<String, String> parseTextures(JsonObject modelObj) {
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return Map.of();
        }
        Map<String, String> textures = new HashMap<>();
        JsonArray array = modelObj.getAsJsonArray("textures");
        for (JsonElement element : array) {
            if (!element.isJsonArray()) {
                continue;
            }
            JsonArray pair = element.getAsJsonArray();
            if (pair.size() < 2) {
                continue;
            }
            String material = pair.get(0).getAsString();
            String texture = pair.get(1).getAsString();
            if (!material.isBlank() && !texture.isBlank()) {
                textures.put(material, encodeTextureDescriptor(pair));
            }
        }
        return textures;
    }

    private static List<String> parseRenderObjects(JsonObject... objects) {
        List<String> result = new ArrayList<>();
        for (JsonObject object : objects) {
            if (object == null || !object.has("objects") || !object.get("objects").isJsonArray()) {
                continue;
            }
            JsonArray array = object.getAsJsonArray("objects");
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String value = element.getAsString();
                if (value != null && !value.isBlank() && result.stream().noneMatch(value::equalsIgnoreCase)) {
                    result.add(value.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    private static String encodeTextureDescriptor(JsonArray pair) {
        String texture = pair.get(1).getAsString();
        if (pair.size() < 3) {
            return texture;
        }
        List<String> flags = new ArrayList<>();
        for (int i = 2; i < pair.size(); i++) {
            JsonElement option = pair.get(i);
            if (option == null || !option.isJsonPrimitive()) {
                continue;
            }
            String value = option.getAsString();
            if (!value.isBlank()) {
                flags.add(value.trim());
            }
        }
        if (flags.isEmpty()) {
            return texture;
        }
        return texture + "|ptmeta=" + String.join(",", flags);
    }

    private static Map<Integer, List<String>> parseSignalLights(JsonObject obj) {
        if (!obj.has("lights") || !obj.get("lights").isJsonArray()) {
            return Map.of();
        }
        Map<Integer, List<String>> lights = new HashMap<>();
        JsonArray array = obj.getAsJsonArray("lights");
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String line = element.getAsString();
            Matcher stateMatcher = LIGHT_STATE_PATTERN.matcher(line);
            Matcher partsMatcher = LIGHT_PARTS_PATTERN.matcher(line);
            if (!stateMatcher.find() || !partsMatcher.find()) {
                continue;
            }
            int state = Integer.parseInt(stateMatcher.group(1));
            String[] parts = partsMatcher.group(1).trim().split("\\s+");
            List<String> groups = new ArrayList<>();
            for (String part : parts) {
                if (!part.isBlank()) {
                    groups.add(part);
                }
            }
            if (!groups.isEmpty()) {
                lights.put(state, List.copyOf(groups));
            }
        }
        return lights;
    }

    public static Path resolvePackPath(String packName) {
        return RailPackLoader.resolvePackPath(packName);
    }

    private record EntryData(String path, byte[] bytes) {
    }

    private record NestedArchive(String name, byte[] bytes) {
    }
}
