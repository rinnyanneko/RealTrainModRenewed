package cc.mirukuneko.realtrainmodrenewed.model;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem;
import cc.mirukuneko.realtrainmodrenewed.modelpack.VehicleModelPackManager;
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModelLoader {
    private static final Map<String, MQOModel> MODEL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static MQOModel loadModel(VehicleDefinition definition) {
        String cacheKey = definition.getPackName() + ":" + definition.getModelFile();
        if (MODEL_CACHE.containsKey(cacheKey)) {
            RealTrainModRenewed.LOGGER.info("Returning cached model: {}", cacheKey);
            return MODEL_CACHE.get(cacheKey);
        }

        RealTrainModRenewed.LOGGER.info("Loading model: {} from pack: {}", definition.getModelFile(), definition.getPackName());
        
        try {
            Path packPath = resolvePackPath(definition.getPackName());
            if (packPath == null) {
                RealTrainModRenewed.LOGGER.error("Pack path not found for: {}", definition.getPackName());
                return null;
            }

            RealTrainModRenewed.LOGGER.info("Pack path resolved: {}", packPath);

            MQOModel model;
            if (Files.isDirectory(packPath)) {
                model = loadFromDirectory(packPath, definition.getModelFile());
            } else {
                model = loadFromZip(packPath, definition.getModelFile());
            }

            if (model != null) {
                RealTrainModRenewed.LOGGER.info("Model loaded successfully: {}", definition.getModelFile());
                // Load script if defined
                if (definition.hasScript()) {
                    loadScriptForModel(model, definition);
                }
                MODEL_CACHE.put(cacheKey, model);
            } else {
                RealTrainModRenewed.LOGGER.warn("Model returned null: {}", definition.getModelFile());
            }
            return model;
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.error("Failed to load model {} from pack {}", definition.getModelFile(), definition.getPackName(), e);
            return null;
        }
    }

    private static void loadScriptForModel(MQOModel model, VehicleDefinition definition) {
        try {
            Path packPath = resolvePackPath(definition.getPackName());
            if (packPath == null) {
                return;
            }
            String normalized = normalizeScriptPath(definition.getScriptPath());
            String scriptLeaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;

            if (!normalized.isBlank()) {
                try {
                    String legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized);
                    if (legacyScript == null || legacyScript.isBlank()) {
                        legacyScript = VehicleModelPackManager.INSTANCE.getScript(scriptLeaf);
                    }
                    if (legacyScript != null && !legacyScript.isBlank()) {
                        RealTrainModRenewed.LOGGER.info("Loaded legacy script from resource manager: {}", normalized);
                        TrainScriptSystem.loadScript(normalized, legacyScript, model);
                        return;
                    }
                } catch (Exception ignored) {
                    // Fallback to direct pack lookup when legacy resource manager cannot resolve script
                }
            }

            if (Files.isDirectory(packPath)) {
                Path scriptPath = packPath.resolve(normalized);
                if (!Files.exists(scriptPath)) {
                    scriptPath = resolveFilePath(packPath, normalized);
                }
                if (scriptPath == null || !Files.exists(scriptPath)) {
                    scriptPath = resolveFilePath(packPath, scriptLeaf);
                }
                if (scriptPath != null && Files.exists(scriptPath)) {
                    TrainScriptSystem.loadScript(scriptPath.toString(), model);
                } else {
                    Path fallback = findFallbackScriptFile(packPath);
                    if (fallback != null) {
                        RealTrainModRenewed.LOGGER.warn("Script {} not found in pack {}; using fallback {}", normalized, packPath, fallback);
                        TrainScriptSystem.loadScript(fallback.toString(), model);
                    } else {
                        RealTrainModRenewed.LOGGER.warn("Script {} not found in pack {}", normalized, packPath);
                    }
                }
            } else {
                try (ZipFile zf = PackZipReader.openZipFile(packPath)) {
                    ZipEntry entry = findEntry(zf, normalized);
                    if (entry != null) {
                        try (InputStream in = zf.getInputStream(entry)) {
                            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            TrainScriptSystem.loadScript(normalized, script, model);
                        }
                    } else {
                        ZipEntry fallback = findFallbackScriptEntry(zf);
                        if (fallback != null) {
                            try (InputStream in = zf.getInputStream(fallback)) {
                                String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                RealTrainModRenewed.LOGGER.warn("Script {} not found in pack {}; using fallback {}", normalized, packPath, fallback.getName());
                                TrainScriptSystem.loadScript(fallback.getName(), script, model);
                            }
                        } else {
                            RealTrainModRenewed.LOGGER.warn("Script {} not found in pack {}", normalized, packPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.error("Failed to load script for model {}", definition.getModelFile(), e);
        }
    }

    private static Path resolveFilePath(Path root, String relative) throws IOException {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        Path candidate = root.resolve(norm);
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate;
        candidate = root.resolve("assets/minecraft").resolve(norm);
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate;
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
                    return null; // ambiguous fallback
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
                return null; // ambiguous fallback
            }
            fallback = entry;
        }
        return fallback;
    }

    private static ZipEntry findEntry(ZipFile zf, String relative) {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        ZipEntry direct = zf.getEntry(norm);
        if (direct != null && !direct.isDirectory()) return direct;
        direct = zf.getEntry("assets/minecraft/" + norm);
        if (direct != null && !direct.isDirectory()) return direct;
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

    public static MQOModel loadBogieModel(String bogieModelFile, VehicleDefinition parentDef) {
        String cacheKey = parentDef.getPackName() + ":bogie:" + bogieModelFile;
        if (MODEL_CACHE.containsKey(cacheKey)) {
            return MODEL_CACHE.get(cacheKey);
        }

        try {
            Path packPath = resolvePackPath(parentDef.getPackName());
            if (packPath == null) {
                return null;
            }

            MQOModel model;
            if (Files.isDirectory(packPath)) {
                model = loadFromDirectory(packPath, bogieModelFile);
            } else {
                model = loadFromZip(packPath, bogieModelFile);
            }

            if (model != null) {
                MODEL_CACHE.put(cacheKey, model);
            }
            return model;
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.error("Failed to load bogie model {} from pack {}", bogieModelFile, parentDef.getPackName(), e);
            return null;
        }
    }

    private static MQOModel loadFromDirectory(Path packDir, String modelFile) throws IOException {
        Path modelPath = packDir.resolve(modelFile.replace('\\', '/'));
        if (!Files.exists(modelPath)) {
            return null;
        }

        boolean compressed = modelFile.toLowerCase().endsWith(".mqoz");
        try (InputStream is = Files.newInputStream(modelPath)) {
            return MQOParser.parse(is, compressed);
        }
    }

    private static MQOModel loadFromZip(Path zipPath, String modelFile) throws IOException {
        final MQOModel[] result = {null};
        try (InputStream input = Files.newInputStream(zipPath)) {
            PackZipReader.read(input, (entry, entryInput) -> {
                if (result[0] != null || entry.isDirectory()) {
                    return;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.equalsIgnoreCase(modelFile) || name.endsWith("/" + modelFile)) {
                    boolean compressed = modelFile.toLowerCase().endsWith(".mqoz");
                    result[0] = MQOParser.parse(entryInput, compressed);
                }
            });
            if (result[0] != null) {
                return result[0];
            }
        }
        return null;
    }

    private static Path resolvePackPath(String packName) {
        // Match the pack resolution logic from VehiclePackLoader
        try {
            // Try mods directory first
            Path gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
            Path modsDir = gameDir.resolve("mods");
            Path packPath = modsDir.resolve(packName);
            if (java.nio.file.Files.exists(packPath)) {
                return packPath;
            }
            
            // Try vehicle_packs directory
            Path vehiclePacksDir = gameDir.resolve("vehicle_packs");
            packPath = vehiclePacksDir.resolve(packName);
            if (java.nio.file.Files.exists(packPath)) {
                return packPath;
            }
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.error("Failed to resolve pack path for {}", packName, e);
        }
        return null;
    }

    private static String normalizeScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return "";
        }
        return scriptPath.replace('\\', '/').replaceFirst("^/+", "");
    }

    public static Identifier resolveTexture(String texturePath) {
        // Convert texture path to Identifier
        // Handle various texture path formats used in legacy
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        
        // Remove file extension if present
        if (texturePath.endsWith(".png")) {
            texturePath = texturePath.substring(0, texturePath.length() - 4);
        }
        
        // Handle legacy-style texture paths
        if (texturePath.startsWith("textures/")) {
            return Identifier.tryParse(texturePath);
        }
        
        // Try to parse as namespace:path format
        Identifier loc = Identifier.tryParse(texturePath);
        if (loc != null) {
            return loc;
        }
        
        // Fallback to realtrainmodunofficial namespace
        return Identifier.tryParse("realtrainmodunofficial:" + texturePath);
    }
}
