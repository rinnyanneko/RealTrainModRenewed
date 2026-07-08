package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.BundledPackStore
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipFile

object PackRequirementWarnings {
    private val warnings: MutableList<String> = ArrayList()
    private val readmeCharsets: List<Charset> = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("MS932"),
        Charset.forName("Shift_JIS"),
    )

    @JvmStatic
    @Synchronized
    fun refresh() {
        warnings.clear()
        try {
            val archiveInfos = collectPackArchives().map(::readArchiveInfo)
            val availableNames = LinkedHashSet<String>()
            for (archiveInfo in archiveInfos) {
                availableNames.addAll(archiveInfo.aliases)
            }
            val missing = LinkedHashSet<String>()
            for (archiveInfo in archiveInfos) {
                inspectArchive(archiveInfo, availableNames, missing)
            }
            warnings.addAll(missing)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Failed to inspect prerequisite packs", e)
        }
    }

    @JvmStatic
    @Synchronized
    fun getWarnings(): List<String> = warnings.toList()

    @Throws(IOException::class)
    private fun collectPackArchives(): List<Path> {
        val unique = LinkedHashSet<Path>()
        val gameDir = FMLPaths.GAMEDIR.get()
        val roots = listOf(
            gameDir,
            gameDir.resolve("mods"),
            gameDir.resolve("content"),
            gameDir.resolve("vehicle_packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("vehicle_packs"),
        )
        for (root in roots) {
            if (!Files.isDirectory(root)) {
                continue
            }
            Files.walk(root, 6).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter(::isArchive)
                    .forEach(unique::add)
            }
        }
        for (category in listOf("rail", "vehicle", "installed_object")) {
            unique.addAll(BundledPackStore.listBundledPacks(category))
        }
        return ArrayList(unique)
    }

    private fun readArchiveInfo(archive: Path): ArchiveInfo {
        val aliases = LinkedHashSet<String>()
        aliases.add(cleanDisplayName(archive.fileName.toString()))
        val prerequisites = LinkedHashSet<String>()
        try {
            PackZipReader.openZipFile(archive).use { zipFile ->
                val readme = readReadme(zipFile)
                if (readme.isNotBlank()) {
                    val title = extractTitle(readme)
                    if (title.isNotBlank()) {
                        aliases.add(title)
                    }
                    val prerequisitePack = inferPrerequisitePackName(readme, archive.fileName.toString())
                    if (prerequisitePack.isNotBlank()) {
                        prerequisites.add(prerequisitePack)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ArchiveInfo(archive, aliases.toList(), prerequisites.toList())
    }

    private fun inspectArchive(archiveInfo: ArchiveInfo, availableNames: Set<String>, missing: MutableSet<String>) {
        for (prerequisitePack in archiveInfo.prerequisites) {
            if (prerequisitePack.isBlank()) {
                continue
            }
            val present = availableNames
                .map(::normalizeName)
                .any { name -> matchesPackName(name, prerequisitePack) }
            if (!present) {
                missing.add("前提パックの $prerequisitePack が入ってません！")
            }
        }
    }

    @Throws(IOException::class)
    private fun readReadme(zipFile: ZipFile): String {
        for (entry in Collections.list(zipFile.entries())) {
            val name = entry.name.lowercase(Locale.ROOT)
            if (entry.isDirectory || !name.endsWith(".txt")) {
                continue
            }
            val bytes = zipFile.getInputStream(entry).readAllBytes()
            var fallback = ""
            for (charset in readmeCharsets) {
                val text = String(bytes, charset)
                if (text.contains("前提パック")) {
                    return text
                }
                if (fallback.isBlank() && !looksCorrupted(text)) {
                    fallback = text
                }
            }
            if (fallback.isNotBlank()) {
                return fallback
            }
        }
        return ""
    }

    private fun inferPrerequisitePackName(readme: String, archiveName: String): String {
        val title = extractTitle(readme)
        if (title.isBlank()) {
            return ""
        }
        if (normalizeName(archiveName).contains(normalizeName(title))) {
            return ""
        }
        return title
    }

    private fun extractTitle(readme: String): String {
        return readme.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .filter { !looksCorrupted(it) }
            .firstOrNull()
            .orEmpty()
    }

    private fun isArchive(path: Path): Boolean {
        val name = path.fileName.toString().lowercase(Locale.ROOT)
        return name.endsWith(".zip") || name.endsWith(".jar")
    }

    private fun looksCorrupted(value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return true
        }
        return value.indexOf('\uFFFD') >= 0 || value.contains("�") || value.contains("縺") || value.contains("繧")
    }

    private fun matchesPackName(normalizedAvailable: String, prerequisitePack: String): Boolean {
        val normalizedRequired = normalizeName(prerequisitePack)
        if (normalizedAvailable.isBlank() || normalizedRequired.isBlank()) {
            return false
        }
        return normalizedAvailable.contains(normalizedRequired) || normalizedRequired.contains(normalizedAvailable)
    }

    private fun cleanDisplayName(value: String?): String {
        return value
            ?.replace(".zip", "")
            ?.replace(".jar", "")
            ?.trim()
            .orEmpty()
    }

    private fun normalizeName(value: String?): String {
        return value
            ?.lowercase(Locale.ROOT)
            ?.replace(".zip", "")
            ?.replace(".jar", "")
            ?.replace("_", "")
            ?.replace("-", "")
            ?.replace(" ", "")
            ?.replace("　", "")
            .orEmpty()
    }

    private data class ArchiveInfo(
        val path: Path,
        val aliases: List<String>,
        val prerequisites: List<String>,
    )
}
