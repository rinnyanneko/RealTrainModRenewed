package cc.mirukuneko.realtrainmodrenewed.client.sound;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.util.LegacyResourcePathUtil;
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ExternalSoundPackBridge {
    private static final String PACK_ID = "realtrainmodunofficial:external_sound_bridge";
    private static final Component PACK_TITLE = Component.literal("RTM External Sounds");
    private static final Path GENERATED_PACK_ROOT = FMLPaths.GAMEDIR.get()
        .resolve("config")
        .resolve("realtrainmodunofficial")
        .resolve("generated_sound_pack");

    private ExternalSoundPackBridge() {
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path packRoot = rebuildGeneratedPack();
        if (packRoot == null) {
            return;
        }
        Pack pack = Pack.readMetaAndCreate(
            new PackLocationInfo(PACK_ID, PACK_TITLE, PackSource.BUILT_IN, Optional.empty()),
            new PathPackResources.PathResourcesSupplier(packRoot),
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
        if (pack == null) {
            RealTrainModRenewed.LOGGER.warn("Generated external sound bridge pack could not be registered");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private static Path rebuildGeneratedPack() {
        try {
            deleteDirectoryIfExists(GENERATED_PACK_ROOT);
            Files.createDirectories(GENERATED_PACK_ROOT);
            Map<String, JsonObject> mergedSoundDefs = new HashMap<>();
            boolean copiedAnySoundAsset = false;
            for (Path candidate : collectCandidatePacks()) {
                try {
                    if (Files.isDirectory(candidate)) {
                        copiedAnySoundAsset |= collectFromDirectory(candidate, mergedSoundDefs);
                    } else if (isSupportedArchive(candidate)) {
                        copiedAnySoundAsset |= collectFromArchive(candidate, mergedSoundDefs);
                    }
                } catch (Exception e) {
                    RealTrainModRenewed.LOGGER.debug("Could not scan external sound assets from {}", candidate, e);
                }
            }
            boolean wroteAnyJson = writeMergedSoundsJson(mergedSoundDefs);
            repairEventKeySoundReferences();
            if (!copiedAnySoundAsset && !wroteAnyJson) {
                deleteDirectoryIfExists(GENERATED_PACK_ROOT);
                return null;
            }
            writePackMeta();
            return GENERATED_PACK_ROOT;
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.warn("Could not build external sound bridge pack", e);
            return null;
        }
    }

    private static List<Path> collectCandidatePacks() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        List<Path> roots = List.of(
            gameDir.resolve("mods"),
            gameDir.resolve("content"),
            gameDir.resolve("vehicle_packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("vehicle_packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("rail_packs")
        );
        Set<Path> unique = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream.forEach(path -> {
                    if (!path.equals(GENERATED_PACK_ROOT)) {
                        unique.add(path);
                    }
                });
            } catch (IOException e) {
                RealTrainModRenewed.LOGGER.debug("Could not list sound candidate root {}", root, e);
            }
        }
        return new ArrayList<>(unique);
    }

    private static boolean collectFromDirectory(Path packDir, Map<String, JsonObject> mergedSoundDefs) throws IOException {
        String rootNamespace = namespaceFromPackName(packDir.getFileName().toString());
        boolean copiedAny = false;
        Path rootSoundsJson = packDir.resolve("sounds.json");
        if (Files.isRegularFile(rootSoundsJson)) {
            mergeSoundDefinitions(rootNamespace, Files.readString(rootSoundsJson), mergedSoundDefs);
            copiedAny = true;
        }
        Path rootSoundsDir = packDir.resolve("sounds");
        if (Files.isDirectory(rootSoundsDir)) {
            try (var walk = Files.walk(rootSoundsDir)) {
                for (Path source : walk.filter(Files::isRegularFile).toList()) {
                    Path relative = rootSoundsDir.relativize(source);
                    Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(rootNamespace).resolve("sounds")
                        .resolve(sanitizedSoundAssetPath(relative));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    registerCopiedSound(mergedSoundDefs, rootNamespace, relative);
                    copiedAny = true;
                }
            }
        }
        Path assetsDir = packDir.resolve("assets");
        if (!Files.isDirectory(assetsDir)) {
            return copiedAny;
        }
        try (var namespaces = Files.list(assetsDir)) {
            for (Path namespaceDir : namespaces.toList()) {
                if (!Files.isDirectory(namespaceDir)) {
                    continue;
                }
                String namespace = namespaceDir.getFileName().toString().toLowerCase(Locale.ROOT);
                Path soundsJson = namespaceDir.resolve("sounds.json");
                if (Files.isRegularFile(soundsJson)) {
                    mergeSoundDefinitions(namespace, Files.readString(soundsJson), mergedSoundDefs);
                    copiedAny = true;
                }
                Path soundsDir = namespaceDir.resolve("sounds");
                if (Files.isDirectory(soundsDir)) {
                    try (var walk = Files.walk(soundsDir)) {
                        for (Path source : walk.filter(Files::isRegularFile).toList()) {
                            Path relative = soundsDir.relativize(source);
                            Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds")
                                .resolve(sanitizedSoundAssetPath(relative));
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            registerCopiedSound(mergedSoundDefs, namespace, relative);
                            copiedAny = true;
                        }
                    }
                }
            }
        }
        return copiedAny;
    }

    private static boolean collectFromArchive(Path archive, Map<String, JsonObject> mergedSoundDefs) throws IOException {
        boolean copiedAny = false;
        String rootNamespace = namespaceFromPackName(archive.getFileName().toString());
        try (ZipFile zipFile = PackZipReader.openZipFile(archive)) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = normalize(entry.getName());
                String lower = normalized.toLowerCase(Locale.ROOT);
                if (lower.equals("sounds.json")) {
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        mergeSoundDefinitions(rootNamespace, readUtf8(input), mergedSoundDefs);
                        copiedAny = true;
                    }
                    continue;
                }
                if (lower.startsWith("sounds/")) {
                    String[] parts = normalized.split("/");
                    Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(rootNamespace).resolve("sounds");
                    for (int i = 1; i < parts.length; i++) {
                        target = target.resolve(sanitizePathSegment(parts[i]));
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        Files.write(target, input.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    }
                    registerCopiedSound(mergedSoundDefs, rootNamespace, Path.of(String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length))));
                    copiedAny = true;
                    continue;
                }
                if (!lower.startsWith("assets/")) {
                    continue;
                }
                String[] parts = normalized.split("/");
                if (parts.length < 3) {
                    continue;
                }
                // 名前空間は小文字化必須(MC のリソース名前空間は小文字のみ)。RTM パックには
                // "sound_MasaCrossings" 等の大文字名前空間があり、そのままだと MC が読めず音が鳴らない。
                String namespace = parts[1].toLowerCase(Locale.ROOT);
                if (lower.equals("assets/" + namespace + "/sounds.json")) {
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        mergeSoundDefinitions(namespace, readUtf8(input), mergedSoundDefs);
                        copiedAny = true;
                    }
                    continue;
                }
                if (parts.length >= 4 && "sounds".equalsIgnoreCase(parts[2])) {
                    Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds");
                    for (int i = 3; i < parts.length; i++) {
                        target = target.resolve(sanitizePathSegment(parts[i]));
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        Files.write(target, input.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    }
                    registerCopiedSound(mergedSoundDefs, namespace, Path.of(String.join("/", java.util.Arrays.copyOfRange(parts, 3, parts.length))));
                    copiedAny = true;
                }
            }
        }
        return copiedAny;
    }

    private static Path sanitizedSoundAssetPath(Path path) {
        Path lowered = Path.of("");
        for (Path part : path) {
            lowered = lowered.resolve(sanitizePathSegment(part.toString()));
        }
        return lowered;
    }

    private static void registerCopiedSound(Map<String, JsonObject> mergedSoundDefs, String namespace, Path relativePath) {
        if (relativePath == null) {
            return;
        }
        String soundPath = normalize(relativePath.toString()).toLowerCase(Locale.ROOT);
        if (!soundPath.endsWith(".ogg")) {
            return;
        }
        soundPath = soundPath.substring(0, soundPath.length() - ".ogg".length());
        soundPath = sanitizeSoundPath(soundPath);
        if (soundPath.isBlank()) {
            return;
        }
        String eventKey = soundPath.replace('/', '.');
        JsonObject target = mergedSoundDefs.computeIfAbsent(namespace, ignored -> new JsonObject());
        if (target.has(eventKey)) {
            return;
        }
        JsonObject event = new JsonObject();
        event.addProperty("replace", true);
        JsonArray sounds = new JsonArray();
        sounds.add(namespace + ":" + soundPath);
        event.add("sounds", sounds);
        target.add(eventKey, event);
    }

    private static void mergeSoundDefinitions(String namespace, String jsonText, Map<String, JsonObject> mergedSoundDefs) {
        try (Reader reader = new StringReader(jsonText)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject target = mergedSoundDefs.computeIfAbsent(namespace, ignored -> new JsonObject());
            JsonObject source = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                String eventKey = sanitizeSoundEventKey(entry.getKey());
                if (!eventKey.isBlank()) {
                    target.add(eventKey, normalizeSoundEvent(namespace, entry.getValue()));
                }
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.debug("Could not merge sounds.json for namespace {}", namespace, e);
        }
    }

    private static JsonElement normalizeSoundEvent(String namespace, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(normalizeSoundEvent(namespace, child));
            }
            return result;
        }
        if (!element.isJsonObject()) {
            return element.deepCopy();
        }
        JsonObject copy = element.getAsJsonObject().deepCopy();
        copy.addProperty("replace", true);
        JsonElement sounds = copy.get("sounds");
        if (sounds != null && sounds.isJsonArray()) {
            JsonArray normalizedSounds = new JsonArray();
            for (JsonElement soundEntry : sounds.getAsJsonArray()) {
                normalizedSounds.add(normalizeSoundReference(namespace, soundEntry));
            }
            copy.add("sounds", normalizedSounds);
        }
        return copy;
    }

    private static JsonElement normalizeSoundReference(String namespace, JsonElement soundEntry) {
        if (soundEntry == null || soundEntry.isJsonNull()) {
            return soundEntry;
        }
        if (soundEntry.isJsonPrimitive() && soundEntry.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(namespacedSoundPath(namespace, soundEntry.getAsString()));
        }
        if (!soundEntry.isJsonObject()) {
            return soundEntry.deepCopy();
        }
        JsonObject copy = soundEntry.getAsJsonObject().deepCopy();
        JsonElement name = copy.get("name");
        if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
            copy.addProperty("name", namespacedSoundPath(namespace, name.getAsString()));
        }
        return copy;
    }

    private static String namespacedSoundPath(String namespace, String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        // 既に "ns:path" 形式ならその名前空間を小文字化、無ければ与えられた名前空間を前置(小文字)。
        // RTM パックの sounds.json は "sound_MasaCrossings:machine/..." のように大文字名前空間を
        // 含むことがあり、小文字化しないと MC が .ogg を見つけられず無音になる。
        int colon = raw.indexOf(':');
        String ns = colon >= 0 ? raw.substring(0, colon) : namespace;
        String path = colon >= 0 ? raw.substring(colon + 1) : raw;
        if (ns == null || ns.isBlank() || "minecraft".equalsIgnoreCase(ns)) {
            return raw;
        }
        String normalizedPath = normalize(path);
        if (normalizedPath.startsWith("sounds/")) {
            normalizedPath = normalizedPath.substring("sounds/".length());
        }
        if (normalizedPath.endsWith(".ogg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - ".ogg".length());
        }
        normalizedPath = sanitizeSoundPath(normalizedPath);
        if (normalizedPath.isBlank()) {
            return raw;
        }
        return ns.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_") + ":" + normalizedPath;
    }

    private static boolean writeMergedSoundsJson(Map<String, JsonObject> mergedSoundDefs) throws IOException {
        boolean wroteAny = false;
        for (Map.Entry<String, JsonObject> entry : mergedSoundDefs.entrySet()) {
            Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(entry.getKey()).resolve("sounds.json");
            Files.createDirectories(target.getParent());
            Files.writeString(
                target,
                entry.getValue().toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            wroteAny = true;
        }
        return wroteAny;
    }

    private static void writePackMeta() throws IOException {
        int packFormat = Math.min(64, SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major());
        String packMeta = """
            {
              "pack": {
                "pack_format": %d,
                "description": "RTM external sound bridge"
              }
            }
            """.formatted(packFormat);
        Files.writeString(
            GENERATED_PACK_ROOT.resolve("pack.mcmeta"),
            packMeta,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private static void repairEventKeySoundReferences() {
        Path assetsRoot = GENERATED_PACK_ROOT.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) {
            return;
        }
        try (var namespaces = Files.list(assetsRoot)) {
            for (Path namespaceDir : namespaces.toList()) {
                Path soundsJson = namespaceDir.resolve("sounds.json");
                Path soundsDir = namespaceDir.resolve("sounds");
                if (!Files.isRegularFile(soundsJson) || !Files.isDirectory(soundsDir)) {
                    continue;
                }
                String namespace = namespaceDir.getFileName().toString();
                JsonElement parsed = JsonParser.parseString(Files.readString(soundsJson));
                if (!parsed.isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                    repairEventKeySoundElement(namespace, soundsDir, entry.getKey(), entry.getValue());
                }
                Files.writeString(soundsJson, parsed.getAsJsonObject().toString(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.debug("Could not repair event-key sound references", e);
        }
    }

    private static void repairEventKeySoundElement(String namespace, Path soundsDir, String eventKey, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                repairEventKeySoundElement(namespace, soundsDir, eventKey, child);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement sounds = object.get("sounds");
        if (sounds != null) {
            repairEventKeySoundElement(namespace, soundsDir, eventKey, sounds);
        }
        JsonElement name = object.get("name");
        if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
            String repaired = repairEventKeySoundReference(namespace, soundsDir, eventKey, name.getAsString());
            if (repaired != null) {
                object.addProperty("name", repaired);
            }
        }
    }

    private static String repairEventKeySoundReference(String namespace, Path soundsDir, String eventKey, String rawReference) {
        String ref = rawReference == null ? "" : rawReference;
        int colon = ref.indexOf(':');
        String refNamespace = colon >= 0 ? ref.substring(0, colon) : namespace;
        if (!namespace.equals(refNamespace)) {
            return null;
        }
        String soundPath = normalize(colon >= 0 ? ref.substring(colon + 1) : ref);
        if (soundPath.startsWith("sounds/")) {
            soundPath = soundPath.substring("sounds/".length());
        }
        if (soundPath.endsWith(".ogg")) {
            soundPath = soundPath.substring(0, soundPath.length() - ".ogg".length());
        }
        Path expected = soundsDir.resolve(sanitizedSoundAssetPath(Path.of(soundPath + ".ogg")));
        if (Files.isRegularFile(expected)) {
            return null;
        }
        String eventPath = sanitizeSoundPath(eventKey.replace('.', '/'));
        Path eventSound = soundsDir.resolve(sanitizedSoundAssetPath(Path.of(eventPath + ".ogg")));
        if (Files.isRegularFile(eventSound)) {
            return namespace + ":" + eventPath;
        }
        return null;
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static String sanitizeSoundPath(String path) {
        return LegacyResourcePathUtil.sanitizeSoundPath(path);
    }

    private static String sanitizePathSegment(String segment) {
        String sanitized = sanitizeSoundPath(segment);
        return sanitized.isBlank() ? "_" : sanitized;
    }

    private static String sanitizeSoundEventKey(String key) {
        String sanitized = sanitizeSoundPath(key == null ? "" : key.replace('\\', '/'));
        return sanitized.replace('/', '.');
    }

    private static String namespaceFromPackName(String packName) {
        String base = packName == null ? "rtm_pack" : packName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String normalized = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return normalized.isBlank() ? "rtm_pack" : normalized;
    }

    private static boolean isSupportedArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        input.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void deleteDirectoryIfExists(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

