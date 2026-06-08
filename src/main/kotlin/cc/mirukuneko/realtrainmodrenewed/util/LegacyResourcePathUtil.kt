@file:JvmName("LegacyResourcePathUtil")

package cc.mirukuneko.realtrainmodrenewed.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isRegularFile

fun buttonTexturePathCandidates(raw: String?): List<String> {
    val normalized = normalize(raw)
    val result = mutableListOf<String>()
    addPathCandidate(result, normalized)
    if (normalized.startsWith("assets/minecraft/")) {
        addPathCandidate(result, normalized.removePrefix("assets/minecraft/"))
    }
    if (normalized.startsWith("minecraft/")) {
        addPathCandidate(result, normalized.removePrefix("minecraft/"))
    }
    if (normalized.startsWith("textures/")) {
        addPathCandidate(result, normalized.removePrefix("textures/"))
    } else {
        addPathCandidate(result, "textures/$normalized")
    }
    for (candidate in result.toList()) {
        if (candidate.startsWith("textures/")) {
            addPathCandidate(result, candidate.removePrefix("textures/"))
        }
    }
    if (result.isEmpty()) {
        result.add("")
    }
    return result
}

fun findBestReplacementSound(soundsDir: Path, soundPath: String?): Path? {
    var sanitizedPath = sanitizeSoundPath(soundPath)
    if (sanitizedPath.endsWith(".ogg")) {
        sanitizedPath = sanitizedPath.removeSuffix(".ogg")
    }
    if (sanitizedPath.isBlank()) {
        return null
    }
    val targetSegments = sanitizedPath.split('/').toTypedArray()
    val fileName = "${targetSegments.last()}.ogg"
    var best: Path? = null
    var bestScore = 0
    var ambiguous = false

    Files.walk(soundsDir).use { walk ->
        walk.filter { it.isRegularFile() }.forEach { candidate ->
            if (candidate.fileName.toString() != fileName) {
                return@forEach
            }
            var candidateSoundPath = sanitizeSoundPath(soundsDir.relativize(candidate).toString())
            if (candidateSoundPath.endsWith(".ogg")) {
                candidateSoundPath = candidateSoundPath.removeSuffix(".ogg")
            }
            val score = replacementScore(targetSegments, candidateSoundPath.split('/').toTypedArray())
            if (score > bestScore) {
                bestScore = score
                best = candidate
                ambiguous = false
            } else if (score == bestScore && score > 0) {
                ambiguous = true
            }
        }
    }
    return if (ambiguous) null else best
}

fun sanitizeSoundPath(path: String?): String {
    val normalized = normalize(path).lowercase(Locale.ROOT)
    val out = StringBuilder(normalized.length)
    var previousSlash = false
    for (c in normalized) {
        if (c == '/') {
            if (!previousSlash && out.isNotEmpty()) {
                out.append('/')
                previousSlash = true
            }
            continue
        }
        previousSlash = false
        if (c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-' || c == '.') {
            out.append(c)
        } else {
            out.append('_')
        }
    }
    while (out.isNotEmpty() && out.last() == '/') {
        out.deleteAt(out.lastIndex)
    }
    return out.toString()
}

fun normalize(raw: String?): String {
    if (raw == null) {
        return ""
    }
    var normalized = raw.trim().replace('\\', '/').replace(Regex("^/+"), "")
    val namespaceSeparator = normalized.indexOf(':')
    if (namespaceSeparator >= 0) {
        normalized = normalized.substring(namespaceSeparator + 1)
    }
    return normalized.replace(Regex("^/+"), "")
}

private fun addPathCandidate(result: MutableList<String>, path: String?) {
    if (path.isNullOrBlank()) {
        return
    }
    val normalized = path.replace('\\', '/').replace(Regex("^/+"), "")
    if (normalized !in result) {
        result.add(normalized)
    }
    if (!normalized.lowercase(Locale.ROOT).endsWith(".png")) {
        val withPng = "$normalized.png"
        if (withPng !in result) {
            result.add(withPng)
        }
    }
}

private fun replacementScore(targetSegments: Array<String>, candidateSegments: Array<String>): Int {
    if (targetSegments.isEmpty() || candidateSegments.isEmpty()) {
        return 0
    }
    val targetLeaf = targetSegments.last()
    val candidateLeaf = candidateSegments.last()
    if (targetLeaf != candidateLeaf) {
        return 0
    }
    var score = 25
    val pairs = minOf(targetSegments.size, candidateSegments.size)
    for (offset in 2..pairs) {
        val target = targetSegments[targetSegments.size - offset]
        val candidate = candidateSegments[candidateSegments.size - offset]
        if (target == candidate) {
            score += 50
        } else if (stripTrailingDigits(target) == stripTrailingDigits(candidate)) {
            score += 25
        } else {
            break
        }
    }
    return score
}

private fun stripTrailingDigits(value: String?): String {
    return value?.replace(Regex("\\d+$"), "") ?: ""
}
