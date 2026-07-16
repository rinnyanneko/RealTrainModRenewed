// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.model

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object MQOParser {
    @JvmStatic
    @Throws(IOException::class)
    fun parse(inputStream: InputStream, compressed: Boolean): MQOModel {
        return if (compressed) {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName != null && entryName.lowercase().endsWith(".mqo")) {
                        val model = parseMQO(zis)
                        zis.closeEntry()
                        return@use model
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                throw IOException("No .mqo file found in .mqoz archive")
            }
        } else {
            parseMQO(inputStream)
        }
    }

    @Throws(IOException::class)
    private fun parseMQO(inputStream: InputStream): MQOModel {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val model = MQOModel()
        RealTrainModRenewed.LOGGER.info("Starting MQO parsing")

        var line: String?
        var lineNum = 0
        while (reader.readLine().also { line = it } != null) {
            var trimmed = line!!.trim()
            lineNum++
            if (lineNum < 50) RealTrainModRenewed.LOGGER.info("Line {}: {}", lineNum, trimmed)
            when {
                trimmed.startsWith("Material") -> {
                    RealTrainModRenewed.LOGGER.info("Found Material section")
                    parseMaterials(reader, model)
                }
                trimmed.startsWith("Vertex") -> {
                    RealTrainModRenewed.LOGGER.info("Found Vertex section")
                    parseVertices(reader, model)
                }
                trimmed.startsWith("Face") -> {
                    RealTrainModRenewed.LOGGER.info("Found Face section")
                    parseFaces(reader, model)
                }
                trimmed.startsWith("Object") -> {
                    RealTrainModRenewed.LOGGER.info("Found Object section")
                    parseObject(reader, model)
                }
            }
        }
        RealTrainModRenewed.LOGGER.info("MQO parsing complete")
        reader.close()
        return model
    }

    @Throws(IOException::class)
    private fun parseMaterials(reader: BufferedReader, model: MQOModel) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed == "}") break
            val parenStart = trimmed.indexOf('(')
            val parenEnd = trimmed.indexOf(')')
            if (parenStart > 0 && parenEnd > parenStart) {
                val matName = trimmed.substring(0, parenStart).trim()
                val texFile = trimmed.substring(parenStart + 1, parenEnd).trim()
                if (texFile.isNotEmpty()) {
                    val tex = ModelLoader.resolveTexture(texFile) ?: continue
                    model.addMaterial(matName, tex)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun parseVertices(reader: BufferedReader, model: MQOModel) {
        var line: String?
        var vertexCount = 0
        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed == "}") {
                RealTrainModRenewed.LOGGER.info("Vertex section ended, parsed {} vertices", vertexCount)
                break
            }
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size >= 3) {
                try {
                    model.addVertex(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
                    vertexCount++
                } catch (_: NumberFormatException) { }
            }
        }
    }

    @Throws(IOException::class)
    private fun parseFaces(reader: BufferedReader, model: MQOModel) {
        var line: String?
        var faceCount = 0
        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed == "}") {
                RealTrainModRenewed.LOGGER.info("Face section ended, parsed {} faces", faceCount)
                break
            }
            val parts = trimmed.split("\\s+".toRegex())
            val vertexIndices = mutableListOf<Int>()
            val uvList = mutableListOf<FloatArray>()
            var materialName = "default"

            for (part in parts) {
                when {
                    part.startsWith("M(") -> {
                        val end = part.indexOf(')')
                        if (end > 2) materialName = part.substring(2, end)
                    }
                    part.startsWith("U(") || part.startsWith("V(") -> { /* handled below */ }
                    else -> try { vertexIndices.add(part.toInt()) } catch (_: NumberFormatException) { }
                }
            }

            var i = 0
            while (i < parts.size) {
                val part = parts[i]
                if (part.startsWith("U(") && i + 1 < parts.size && parts[i + 1].startsWith("V(")) {
                    try {
                        val uIndex = part.substring(2, part.length - 1).toInt()
                        val vIndex = parts[i + 1].substring(2, parts[i + 1].length - 1).toInt()
                        uvList.add(floatArrayOf(uIndex.toFloat(), vIndex.toFloat()))
                    } catch (_: NumberFormatException) { }
                }
                i++
            }

            if (vertexIndices.isNotEmpty()) {
                model.addFace(vertexIndices.toIntArray(), uvList.toTypedArray(), materialName)
                faceCount++
            }
        }
    }

    @Throws(IOException::class)
    private fun parseObject(reader: BufferedReader, model: MQOModel) {
        var line = reader.readLine() ?: return
        line = line.trim()
        RealTrainModRenewed.LOGGER.info("parseObject called with line: {}", line)

        val name = if (line.startsWith("{")) line.substring(1).trim() else "unknown"
        model.addGroup(name)
        RealTrainModRenewed.LOGGER.info("Parsing Object: {}", name)

        var lineCount = 0
        var braceDepth = if (line.startsWith("{")) 1 else 0

        while (true) {
            val trimmed = reader.readLine()?.trim() ?: break
            lineCount++
            if (lineCount < 10) RealTrainModRenewed.LOGGER.info("Object line {}: {}", lineCount, trimmed)

            when (trimmed) {
                "{" -> braceDepth++
                "}" -> {
                    braceDepth--
                    if (braceDepth <= 0) {
                        RealTrainModRenewed.LOGGER.info("Object section ended")
                        break
                    }
                }
            }

            if (trimmed.matches(Regex("vertex\\s+\\d+\\s*\\{")) || trimmed.matches(Regex("Vertex\\s+\\d+\\s*\\{"))) {
                RealTrainModRenewed.LOGGER.info("Found vertex section in object: {}", trimmed)
                parseVertices(reader, model)
            } else if (trimmed.matches(Regex("face\\s+\\d+\\s*\\{")) || trimmed.matches(Regex("Face\\s+\\d+\\s*\\{"))) {
                RealTrainModRenewed.LOGGER.info("Found face section in object: {}", trimmed)
                parseFaces(reader, model)
            }
        }
    }
}
