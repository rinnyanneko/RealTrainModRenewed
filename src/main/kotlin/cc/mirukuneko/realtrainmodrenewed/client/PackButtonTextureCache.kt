// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.BundledPackStore
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.rail.RailPackLoader
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import cc.mirukuneko.realtrainmodrenewed.util.buttonTexturePathCandidates
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.neoforged.fml.loading.FMLPaths
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

object PackButtonTextureCache {
    @JvmRecord
    data class ButtonTextureInfo(
        val location: Identifier,
        val width: Int,
        val height: Int,
        val sourceX: Int,
        val sourceY: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
    )

    private val cacheLock = Any()
    private val CACHE = ConcurrentHashMap<String, ButtonTextureInfo?>()
    private val DYNAMIC_TEXTURES = ConcurrentHashMap<Identifier, DynamicTexture>()
    @Volatile
    private var packCandidates: List<Path>? = null

    @JvmStatic
    fun clear() {
        synchronized(cacheLock) {
            CACHE.clear()
            packCandidates = null
            val textureManager = Minecraft.getInstance().textureManager
            for ((location, texture) in DYNAMIC_TEXTURES) {
                try {
                    textureManager.release(location)
                } catch (exception: Exception) {
                    RealTrainModRenewed.LOGGER.debug("Failed to close cached button texture", exception)
                    try {
                        texture.close()
                    } catch (_: Exception) {
                    }
                }
            }
            DYNAMIC_TEXTURES.clear()
        }
    }

    @JvmStatic
    fun get(packName: String?, texturePath: String?): ButtonTextureInfo? {
        return get(packName, texturePath, "", "")
    }

    @JvmStatic
    fun get(packName: String?, texturePath: String?, modelId: String?, displayName: String?): ButtonTextureInfo? {
        if (packName == null || packName.isBlank()) {
            return null
        }
        val key = if (texturePath == null || texturePath.isBlank()) {
            "$packName|fallback|${safe(modelId)}|${safe(displayName)}"
        } else {
            "$packName|$texturePath|${safe(modelId)}|${safe(displayName)}"
        }
        synchronized(cacheLock) {
            val cached = CACHE[key]
            if (cached != null) {
                return cached
            }
            val loaded = if (texturePath == null || texturePath.isBlank()) {
                loadFallbackForModel(packName, modelId, displayName)
            } else {
                try {
                    load(packName, texturePath) ?: loadFallbackForModel(packName, modelId, displayName)
                } catch (exception: Exception) {
                    RealTrainModRenewed.LOGGER.debug("Could not resolve buttonTexture {} from {}", texturePath, packName, exception)
                    null
                }
            }
            if (loaded != null) {
                CACHE[key] = loaded
            }
            return loaded
        }
    }

    private fun load(packName: String, texturePath: String): ButtonTextureInfo? {
        val packPath = RailPackLoader.resolvePackPath(packName)
        if (packPath == null) {
            return try {
                val fallbackImage = loadBySearchingAllPacks(texturePath) ?: return null
                registerDynamicTexture(packName, texturePath, fallbackImage)
            } catch (exception: Exception) {
                RealTrainModRenewed.LOGGER.debug("Could not globally resolve buttonTexture {} from {}", texturePath, packName, exception)
                null
            }
        }
        return try {
            var image = if (Files.isDirectory(packPath)) {
                loadFromDirectory(packPath, texturePath)
            } else {
                loadFromArchive(packPath, texturePath)
            }
            if (image == null) {
                image = loadBySearchingAllPacks(texturePath)
            }
            if (image == null) {
                return null
            }
            registerDynamicTexture(packName, texturePath, image)
        } catch (exception: Exception) {
            RealTrainModRenewed.LOGGER.debug("Could not load buttonTexture {} from {}", texturePath, packName, exception)
            null
        }
    }

    private fun registerDynamicTexture(packName: String, texturePath: String, image: NativeImage): ButtonTextureInfo {
        val location = Identifier.fromNamespaceAndPath(
            RealTrainModRenewed.MODID,
            "dynamic/button/" + uniquePathSegment(packName) + "/" + uniquePathSegment(texturePath),
        )
        val width = image.width
        val height = image.height
        val bounds = detectContentBounds(image, texturePath)
        val texture = DynamicTexture({ "realtrainmodrenewed button texture" }, image)
        val textureManager = Minecraft.getInstance().textureManager
        val previous = DYNAMIC_TEXTURES.put(location, texture)
        if (previous != null) {
            try {
                textureManager.release(location)
            } catch (exception: Exception) {
                RealTrainModRenewed.LOGGER.debug("Failed to release previous button texture {}", location, exception)
                try {
                    previous.close()
                } catch (_: Exception) {
                }
            }
        }
        textureManager.register(
            location,
            texture,
        )
        return ButtonTextureInfo(location, width, height, bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private fun loadFallbackForModel(packName: String, modelId: String?, displayName: String?): ButtonTextureInfo? {
        return try {
            var image: NativeImage? = null
            val packPath = RailPackLoader.resolvePackPath(packName)
            if (packPath != null) {
                image = if (Files.isDirectory(packPath)) {
                    loadBestButtonFromDirectory(packPath, modelId, displayName)
                } else {
                    loadBestButtonFromArchive(packPath, modelId, displayName)
                }
            }
            if (image == null) {
                for (candidate in listAllPackCandidates()) {
                    image = if (Files.isDirectory(candidate)) {
                        loadBestButtonFromDirectory(candidate, modelId, displayName)
                    } else {
                        loadBestButtonFromArchive(candidate, modelId, displayName)
                    }
                    if (image != null) {
                        break
                    }
                }
            }
            if (image == null) {
                null
            } else {
                registerDynamicTexture(packName, "fallback/" + safe(modelId) + "/" + safe(displayName), image)
            }
        } catch (exception: Exception) {
            RealTrainModRenewed.LOGGER.debug("Could not resolve fallback buttonTexture for {} in {}", modelId, packName, exception)
            null
        }
    }

    private fun loadFromDirectory(packPath: Path, texturePath: String): NativeImage? {
        val resolved = resolveDirectoryTexture(packPath, texturePath) ?: return null
        Files.newInputStream(resolved).use { input ->
            return NativeImage.read(input)
        }
    }

    private fun loadFromArchive(packPath: Path, texturePath: String): NativeImage? {
        PackZipReader.openZipFile(packPath).use { zipFile ->
            val entry = findEntry(zipFile, texturePath) ?: return null
            zipFile.getInputStream(entry).use { input ->
                return NativeImage.read(input)
            }
        }
    }

    private fun resolveDirectoryTexture(root: Path, texturePath: String): Path? {
        val candidates = buttonTexturePathCandidates(texturePath)
        for (candidate in candidates) {
            val direct = root.resolve(candidate)
            if (Files.isRegularFile(direct)) {
                return direct
            }
            val assets = root.resolve("assets").resolve("minecraft").resolve(candidate)
            if (Files.isRegularFile(assets)) {
                return assets
            }
            val textures = root.resolve("textures").resolve(candidate)
            if (Files.isRegularFile(textures)) {
                return textures
            }
        }
        val normalized = buttonTexturePathCandidates(texturePath)[0]
        val leaf = normalized.substring(normalized.lastIndexOf('/') + 1)
        Files.walk(root).use { stream ->
            return stream.filter(Files::isRegularFile)
                .filter { path -> path.fileName.toString().equals(leaf, ignoreCase = true) }
                .findFirst()
                .orElse(null)
        }
    }

    private fun loadBySearchingAllPacks(texturePath: String): NativeImage? {
        for (candidate in listAllPackCandidates()) {
            val image = if (Files.isDirectory(candidate)) {
                loadFromDirectory(candidate, texturePath)
            } else {
                loadFromArchive(candidate, texturePath)
            }
            if (image != null) {
                return image
            }
        }
        return null
    }

    private fun loadBestButtonFromDirectory(root: Path, modelId: String?, displayName: String?): NativeImage? {
        val exact = loadExactFallbackButtonFromDirectory(root, modelId, displayName)
        if (exact != null) {
            return exact
        }
        var best: ButtonCandidate? = null
        Files.walk(root).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path) || !isPng(path.fileName.toString())) {
                    continue
                }
                val relative = normalize(root.relativize(path).toString())
                val score = scoreButtonCandidate(relative, modelId, displayName)
                if (score <= 0 || (best != null && score <= best!!.score)) {
                    continue
                }
                best = ButtonCandidate(score, path, null)
            }
        }
        val path = best?.path ?: return null
        Files.newInputStream(path).use { input ->
            return NativeImage.read(input)
        }
    }

    private fun loadBestButtonFromArchive(archive: Path, modelId: String?, displayName: String?): NativeImage? {
        PackZipReader.openZipFile(archive).use { zipFile ->
            val exact = loadExactFallbackButtonFromArchive(zipFile, modelId, displayName)
            if (exact != null) {
                return exact
            }
            var best: ButtonCandidate? = null
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !isPng(entry.name)) {
                    continue
                }
                val score = scoreButtonCandidate(entry.name, modelId, displayName)
                if (score <= 0 || (best != null && score <= best!!.score)) {
                    continue
                }
                best = ButtonCandidate(score, null, entry)
            }
            val entry = best?.entry ?: return null
            zipFile.getInputStream(entry).use { input ->
                return NativeImage.read(input)
            }
        }
    }

    private fun loadExactFallbackButtonFromDirectory(root: Path, modelId: String?, displayName: String?): NativeImage? {
        for (candidate in exactButtonCandidates(modelId, displayName)) {
            val resolved = resolveDirectoryTexture(root, candidate)
            if (resolved != null) {
                Files.newInputStream(resolved).use { input ->
                    return NativeImage.read(input)
                }
            }
        }
        return null
    }

    private fun loadExactFallbackButtonFromArchive(zipFile: ZipFile, modelId: String?, displayName: String?): NativeImage? {
        for (candidate in exactButtonCandidates(modelId, displayName)) {
            val entry = findEntry(zipFile, candidate)
            if (entry != null) {
                zipFile.getInputStream(entry).use { input ->
                    return NativeImage.read(input)
                }
            }
        }
        return null
    }

    private fun exactButtonCandidates(modelId: String?, displayName: String?): List<String> {
        val candidates = ArrayList<String>()
        for (raw in arrayOf(modelId, displayName)) {
            val value = safe(raw).trim()
            if (value.isBlank()) {
                continue
            }
            var leaf = value.replace(' ', '_')
            val compactLeaf = compact(value)
            val namespaceSeparator = leaf.indexOf(':')
            if (namespaceSeparator >= 0) {
                leaf = leaf.substring(namespaceSeparator + 1)
            }
            val slash = leaf.lastIndexOf('/')
            if (slash >= 0) {
                leaf = leaf.substring(slash + 1)
            }
            addExactButtonCandidate(candidates, "textures/train/button_$leaf.png")
            addExactButtonCandidate(candidates, "textures/vehicle/button_$leaf.png")
            addExactButtonCandidate(candidates, "textures/button/button_$leaf.png")
            addExactButtonCandidate(candidates, "textures/buttons/button_$leaf.png")
            addExactButtonCandidate(candidates, "textures/button/$leaf.png")
            addExactButtonCandidate(candidates, "textures/buttons/$leaf.png")
            addExactButtonCandidate(candidates, "button_$leaf.png")
            addExactButtonCandidate(candidates, "$leaf.png")
            if (compactLeaf.isNotBlank() && compactLeaf != leaf) {
                addExactButtonCandidate(candidates, "textures/train/button_$compactLeaf.png")
                addExactButtonCandidate(candidates, "textures/vehicle/button_$compactLeaf.png")
                addExactButtonCandidate(candidates, "textures/button/button_$compactLeaf.png")
                addExactButtonCandidate(candidates, "textures/buttons/button_$compactLeaf.png")
                addExactButtonCandidate(candidates, "button_$compactLeaf.png")
            }
        }
        return candidates
    }

    private fun addExactButtonCandidate(candidates: MutableList<String>, value: String) {
        if (!candidates.contains(value)) {
            candidates.add(value)
        }
    }

    private fun findEntry(zipFile: ZipFile, texturePath: String): ZipEntry? {
        val candidates = buttonTexturePathCandidates(texturePath)
            .map { path -> path.lowercase(Locale.ROOT) }
        val normalized = candidates[0]
        val leaf = normalized.substring(normalized.lastIndexOf('/') + 1)
        return zipFile.stream()
            .filter { entry -> !entry.isDirectory }
            .filter { entry ->
                val name = normalize(entry.name).lowercase(Locale.ROOT)
                for (candidate in candidates) {
                    if (name == candidate ||
                        name.endsWith("/$candidate") ||
                        name.endsWith("/textures/$candidate") ||
                        name.contains("/textures/$candidate")
                    ) {
                        return@filter true
                    }
                }
                name.endsWith("/$leaf")
            }
            .findFirst()
            .orElse(null)
    }

    private fun normalize(raw: String?): String {
        if (raw == null) {
            return ""
        }
        var normalized = raw.trim().replace('\\', '/').replaceFirst(Regex("^/+"), "")
        val namespaceSeparator = normalized.indexOf(':')
        if (namespaceSeparator >= 0) {
            normalized = normalized.substring(namespaceSeparator + 1)
        }
        return normalized.replaceFirst(Regex("^/+"), "")
    }

    private fun safe(raw: String?): String = raw ?: ""

    private fun sanitize(raw: String): String =
        raw.replace('\\', '/').lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9/._-]"), "_")
            .replaceFirst(Regex("^[/_]+"), "")

    private fun uniquePathSegment(raw: String): String {
        val readable = sanitize(raw).ifBlank { "blank" }.take(96)
        return "$readable-${stableHash(raw)}"
    }

    private fun stableHash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte ->
            String.format(Locale.ROOT, "%02x", byte.toInt() and 0xFF)
        }
    }

    private fun isPng(path: String?): Boolean = path != null && path.lowercase(Locale.ROOT).endsWith(".png")

    private fun scoreButtonCandidate(path: String, modelId: String?, displayName: String?): Int {
        val normalizedPath = normalize(path).lowercase(Locale.ROOT)
        val compactPath = compact(normalizedPath)
        val looksLikeButton = normalizedPath.contains("button") || normalizedPath.contains("/btn") || normalizedPath.contains("_btn")
        var score = if (looksLikeButton) 20 else -20
        for (token in modelTokens(modelId, displayName)) {
            if (token.length < 3) {
                continue
            }
            if (compactPath.contains(token)) {
                score += if (token.length >= 6) 80 else 35
            }
        }
        if (looksLikeButton && (normalizedPath.contains("/textures/") || normalizedPath.contains("/texture/"))) {
            score += 10
        }
        return score
    }

    private fun modelTokens(modelId: String?, displayName: String?): List<String> {
        val tokens = ArrayList<String>()
        addToken(tokens, compact(modelId))
        addToken(tokens, compact(displayName))
        for (source in arrayOf(safe(modelId), safe(displayName))) {
            for (part in source.split(Regex("[^A-Za-z0-9]+"))) {
                addToken(tokens, compact(part))
            }
        }
        return tokens
    }

    private fun addToken(tokens: MutableList<String>, token: String?) {
        if (token != null && token.length >= 3 && !tokens.contains(token)) {
            tokens.add(token)
        }
    }

    private fun compact(raw: String?): String = safe(raw).lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private data class ButtonCandidate(val score: Int, val path: Path?, val entry: ZipEntry?)

    private fun listAllPackCandidates(): List<Path> {
        val cached = packCandidates
        if (cached != null) {
            return cached
        }
        return synchronized(cacheLock) {
            val current = packCandidates
            if (current != null) {
                current
            } else {
                collectAllPackCandidates().also { packCandidates = it }
            }
        }
    }

    private fun collectAllPackCandidates(): List<Path> {
        val seen = LinkedHashSet<Path>()
        val result = ArrayList<Path>()
        val gameDir = FMLPaths.GAMEDIR.get()
        addDirectoryChildren(gameDir, seen, result)
        addArchiveChildren(gameDir, seen, result)
        addDirectoryChildren(gameDir.resolve("mods"), seen, result)
        addArchiveChildren(gameDir.resolve("mods"), seen, result)
        addDirectoryChildren(gameDir.resolve("content"), seen, result)
        addArchiveChildren(gameDir.resolve("content"), seen, result)
        addDirectoryChildren(gameDir.resolve("vehicle_packs"), seen, result)
        addArchiveChildren(gameDir.resolve("vehicle_packs"), seen, result)
        addDirectoryChildren(gameDir.resolve("config").resolve("realtrainmodrenewed"), seen, result)
        addArchiveChildren(gameDir.resolve("config").resolve("realtrainmodrenewed"), seen, result)
        addPackRootChildren(gameDir.resolve("config").resolve("realtrainmodrenewed"), seen, result)
        addDirectoryChildren(gameDir.resolve("config").resolve("realtrainmodunofficial"), seen, result)
        addArchiveChildren(gameDir.resolve("config").resolve("realtrainmodunofficial"), seen, result)
        addPackRootChildren(gameDir.resolve("config").resolve("realtrainmodunofficial"), seen, result)
        for (category in arrayOf("vehicle", "rail", "installed_object", "official")) {
            for (path in BundledPackStore.listBundledPacks(category)) {
                if (seen.add(path)) {
                    result.add(path)
                }
            }
        }
        return result
    }

    private fun addPackRootChildren(root: Path, seen: MutableSet<Path>, result: MutableList<Path>) {
        for (child in arrayOf("packs", "rail_packs", "vehicle_packs", "installed_object_packs")) {
            val dir = root.resolve(child)
            addDirectoryChildren(dir, seen, result)
            addArchiveChildren(dir, seen, result)
        }
    }

    private fun addArchiveChildren(dir: Path?, seen: MutableSet<Path>, result: MutableList<Path>) {
        if (dir == null || !Files.isDirectory(dir)) {
            return
        }
        try {
            Files.list(dir).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { path ->
                        val name = path.fileName.toString().lowercase(Locale.ROOT)
                        name.endsWith(".zip") || name.endsWith(".jar")
                    }
                    .forEach { path ->
                        if (seen.add(path)) {
                            result.add(path)
                        }
                    }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun addDirectoryChildren(dir: Path?, seen: MutableSet<Path>, result: MutableList<Path>) {
        if (dir == null || !Files.isDirectory(dir)) {
            return
        }
        try {
            Files.list(dir).use { stream ->
                stream.filter(Files::isDirectory)
                    .forEach { path ->
                        if (seen.add(path)) {
                            result.add(path)
                        }
                    }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun detectContentBounds(image: NativeImage, texturePath: String): IntArray {
        val width = image.width
        val height = image.height
        val legacyAtlasBounds = detectLegacyRtmButtonAtlasBounds(image)
        if (legacyAtlasBounds != null) {
            return legacyAtlasBounds
        }
        if (width >= 160 && height >= 32) {
            val widthScale = width / 160
            val heightScale = height / 32
            if (width % 160 == 0 && height % 32 == 0 && widthScale == heightScale) {
                return intArrayOf(0, 0, width, height)
            }
        }
        val edgeTrimBounds = detectUniformEdgeBounds(image)
        if (edgeTrimBounds != null) {
            return edgeTrimBounds
        }
        val detectedBounds = detectDominantBackgroundBounds(image)
        if (detectedBounds != null) {
            return detectedBounds
        }
        if (width >= 160 && height >= 32) {
            val widthScale = width / 160
            val heightScale = height / 32
            if (width % 160 == 0 && height % 32 == 0 && widthScale == heightScale) {
                return intArrayOf(0, 0, width, height)
            }
            return intArrayOf(0, 0, 160, 32)
        }
        return intArrayOf(0, 0, width, height)
    }

    private fun detectLegacyRtmButtonAtlasBounds(image: NativeImage): IntArray? {
        val width = image.width
        val height = image.height
        if (width != height || width < 256) {
            return null
        }
        val background = image.getPixel(width - 1, height - 1)
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.getPixel(x, y)
                if (((pixel ushr 24) and 0xFF) <= 8 || colorDistanceSq(pixel, background) <= 8 * 8 * 4) {
                    continue
                }
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }
        if (maxX < 0 || maxY < 0 || maxX > width / 2 + 16 || maxY > height / 4) {
            return null
        }
        val sourceWidth = min(width, max(160, roundUp(maxX + 1, 16)))
        val sourceHeight = min(height, max(32, roundUp(maxY + 1, 16)))
        return intArrayOf(0, 0, sourceWidth, sourceHeight)
    }

    private fun roundUp(value: Int, step: Int): Int = ((max(1, value) + step - 1) / step) * step

    private fun detectDominantBackgroundBounds(image: NativeImage): IntArray? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) {
            return null
        }

        val counts = HashMap<Int, Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                counts.merge(image.getPixel(x, y), 1, Integer::sum)
            }
        }

        var dominantColor = 0
        var dominantCount = -1
        for ((color, count) in counts) {
            if (count > dominantCount) {
                dominantColor = color
                dominantCount = count
            }
        }
        if (dominantCount <= (width * height) / 3) {
            return null
        }

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.getPixel(x, y)
                if (((pixel ushr 24) and 0xFF) <= 8 || colorDistanceSq(pixel, dominantColor) <= 8 * 8 * 4) {
                    continue
                }
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }
        if (maxX < minX || maxY < minY) {
            return null
        }
        minX = max(0, minX - 1)
        minY = max(0, minY - 1)
        maxX = min(width - 1, maxX + 1)
        maxY = min(height - 1, maxY + 1)
        return intArrayOf(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun detectUniformEdgeBounds(image: NativeImage): IntArray? {
        val width = image.width
        val height = image.height
        if (width <= 2 || height <= 2) {
            return null
        }
        val frameColor = image.getPixel(0, 0)
        var minX = 0
        var minY = 0
        var maxX = width - 1
        var maxY = height - 1

        while (minY < maxY && rowMatches(image, minY, frameColor)) {
            minY++
        }
        while (maxY > minY && rowMatches(image, maxY, frameColor)) {
            maxY--
        }
        while (minX < maxX && columnMatches(image, minX, minY, maxY, frameColor)) {
            minX++
        }
        while (maxX > minX && columnMatches(image, maxX, minY, maxY, frameColor)) {
            maxX--
        }

        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) {
            return null
        }

        minX = max(0, minX - 1)
        minY = max(0, minY - 1)
        maxX = min(width - 1, maxX + 1)
        maxY = min(height - 1, maxY + 1)
        return intArrayOf(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun rowMatches(image: NativeImage, y: Int, referenceColor: Int): Boolean {
        for (x in 0 until image.width) {
            val pixel = image.getPixel(x, y)
            if (((pixel ushr 24) and 0xFF) > 8 && colorDistanceSq(pixel, referenceColor) > 4 * 4 * 4) {
                return false
            }
        }
        return true
    }

    private fun columnMatches(image: NativeImage, x: Int, minY: Int, maxY: Int, referenceColor: Int): Boolean {
        for (y in minY..maxY) {
            val pixel = image.getPixel(x, y)
            if (((pixel ushr 24) and 0xFF) > 8 && colorDistanceSq(pixel, referenceColor) > 4 * 4 * 4) {
                return false
            }
        }
        return true
    }

    private fun colorDistanceSq(a: Int, b: Int): Int {
        val ar = a and 0xFF
        val ag = (a ushr 8) and 0xFF
        val ab = (a ushr 16) and 0xFF
        val aa = (a ushr 24) and 0xFF
        val br = b and 0xFF
        val bg = (b ushr 8) and 0xFF
        val bb = (b ushr 16) and 0xFF
        val ba = (b ushr 24) and 0xFF
        val dr = ar - br
        val dg = ag - bg
        val db = ab - bb
        val da = aa - ba
        return dr * dr + dg * dg + db * db + da * da
    }
}
