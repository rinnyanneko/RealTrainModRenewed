package cc.mirukuneko.realtrainmodrenewed

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.Locale

/**
 * Resolves pack archives bundled inside the mod jar and materializes them into a private cache
 * directory when file-based model loaders need a real path.
 */
object BundledPackStore {
    private const val ROOT = "bundled_packs"

    @JvmStatic
    fun listBundledPacks(category: String?): List<Path> {
        val result = LinkedHashSet<Path>()
        addBundledPacks(category, result)
        if (category != "official") {
            addBundledPacks("official", result)
        }
        return ArrayList(result)
    }

    private fun addBundledPacks(category: String?, result: MutableSet<Path>) {
        val resourcePath = "assets/${RealTrainModRenewed.MODID}/$ROOT/$category"
        try {
            val modPath = ModList.get().getModFileById(RealTrainModRenewed.MODID).file.filePath
            addBundledPacksFromDirectory(modPath.resolve(resourcePath), result)
            addDevResourceDirectories(modPath, resourcePath, result)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not list bundled {} packs", category, e)
        }
        try {
            val url = BundledPackStore::class.java.classLoader.getResource(resourcePath)
            if (url != null && "file".equals(url.protocol, ignoreCase = true)) {
                addBundledPacksFromDirectory(Path.of(URI.create(url.toString())), result)
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not list classpath bundled {} packs", category, e)
        }
        try {
            addBundledPacksFromDirectory(Path.of("build", "resources", "main").resolve(resourcePath), result)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not list build bundled {} packs", category, e)
        }
        try {
            addBundledPacksFromDirectory(Path.of("src", "main", "resources").resolve(resourcePath), result)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not list source bundled {} packs", category, e)
        }
    }

    @Throws(IOException::class)
    private fun addDevResourceDirectories(modPath: Path?, resourcePath: String, result: MutableSet<Path>) {
        if (modPath == null) {
            return
        }
        val normalized = modPath.toAbsolutePath().normalize()
        val name = normalized.fileName
        if (name == null || name.toString() != "main") {
            return
        }
        val javaDir = normalized.parent
        val classesDir = javaDir?.parent
        val buildDir = classesDir?.parent
        if (buildDir == null || buildDir.fileName.toString() != "build") {
            return
        }
        addBundledPacksFromDirectory(buildDir.resolve("resources").resolve("main").resolve(resourcePath), result)
        val projectDir = buildDir.parent
        if (projectDir != null) {
            addBundledPacksFromDirectory(
                projectDir.resolve("src").resolve("main").resolve("resources").resolve(resourcePath),
                result,
            )
        }
    }

    @Throws(IOException::class)
    private fun addBundledPacksFromDirectory(dir: Path?, result: MutableSet<Path>) {
        if (dir == null || !Files.isDirectory(dir)) {
            return
        }
        Files.list(dir).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter(::isArchive)
                .map { path -> path.toAbsolutePath().normalize() }
                .forEach(result::add)
        }
    }

    @JvmStatic
    fun resolveBundledPack(packName: String?): Path? {
        if (packName == null || packName.isBlank()) {
            return null
        }
        for (category in arrayOf("rail", "vehicle", "installed_object", "official")) {
            for (path in listBundledPacks(category)) {
                if (path.fileName.toString().equals(packName, ignoreCase = true)) {
                    return path
                }
            }
        }
        return null
    }

    @JvmStatic
    fun materializeBundledPack(packName: String?): Path? {
        val source = resolveBundledPack(packName) ?: return null
        try {
            val cacheDir = FMLPaths.GAMEDIR.get()
                .resolve("config")
                .resolve("realtrainmodunofficial")
                .resolve("bundled_pack_cache")
            Files.createDirectories(cacheDir)
            val target = cacheDir.resolve(source.fileName.toString())
            var sourceSize = -1L
            try {
                sourceSize = Files.size(source)
            } catch (_: Exception) {
            }
            var needsCopy = !Files.exists(target)
            if (!needsCopy && sourceSize >= 0) {
                try {
                    needsCopy = Files.size(target) != sourceSize
                } catch (_: Exception) {
                    needsCopy = true
                }
            }
            if (needsCopy) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not materialize bundled pack {}", packName, e)
            return null
        }
    }

    @JvmStatic
    fun isBundledPackName(packName: String?): Boolean {
        if (packName == null || packName.isBlank()) return false
        return resolveBundledPack(packName) != null
    }

    @JvmStatic
    fun getModJarPath(): Path? {
        try {
            val modFileEntry = ModList.get().getModFileById(RealTrainModRenewed.MODID)
            if (modFileEntry == null) return null
            val path = modFileEntry.file.filePath
            if (path != null && Files.exists(path)) return path.toAbsolutePath().normalize()
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not get mod JAR path", e)
        }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun openBundledPack(packName: String?): InputStream? {
        val source = resolveBundledPack(packName)
        return if (source == null) null else Files.newInputStream(source)
    }

    private fun isArchive(path: Path): Boolean {
        val name = path.fileName.toString().lowercase(Locale.getDefault())
        return name.endsWith(".zip") || name.endsWith(".jar")
    }
}
