// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.model.mqo

import java.io.BufferedReader
import java.io.IOException
import java.util.Arrays

object MQOParser {
    @JvmStatic
    @Throws(IOException::class)
    fun parse(modelReader: BufferedReader): MQOParseResult {
        var line: String?

        var materials: Array<MQOMaterial>? = null
        val objects = ArrayList<MQOObject>()

        while (true) {
            line = modelReader.readLine() ?: break
            val isGlobalChunkInitialLine = isGlobalChunkInitialLine(line)
            if (!isGlobalChunkInitialLine) continue

            val currentGlobalChunk = extractGlobalChunkName(line)
            if (currentGlobalChunk == MQOGlobalChunk.FORBIDDEN) {
                return MQOParseResult(null, MQOParseResultStatus.FORBIDDEN)
            }

            when (currentGlobalChunk) {
                MQOGlobalChunk.MATERIAL -> {
                    val materialQuantity = extractChunkQuantity(line)
                    val currentMaterials = arrayOfNulls<MQOMaterial>(materialQuantity)
                    var matI = 0

                    while (true) {
                        line = modelReader.readLine()
                        if (!isNotChunkFinish(line) || matI >= materialQuantity) break
                        currentMaterials[matI++] = parseMaterialLine(line!!)
                    }
                    @Suppress("UNCHECKED_CAST")
                    materials = currentMaterials as Array<MQOMaterial>
                }
                MQOGlobalChunk.OBJECT -> {
                    val name = extractFirstQuotedName(line)
                    var isSmoothShadingEnabled = false
                    var autoSmoothAngle = 59.5f
                    var mirrorType = 0
                    var isMirrorAxisXEnabled = false
                    var isMirrorAxisYEnabled = false
                    var isMirrorAxisZEnabled = false
                    var mirrorDistance = 0.0f
                    var vertices: Array<MQOVertex>? = null
                    var faces: Array<MQOFace>? = null

                    while (true) {
                        line = modelReader.readLine()
                        if (!isNotChunkFinish(line)) break
                        val propKey = extractObjectPropKey(line!!)
                        when (propKey) {
                            "shading" -> {
                                val shadingValue = line[line.length - 1]
                                isSmoothShadingEnabled = shadingValue == '1'
                            }
                            "facet" -> autoSmoothAngle = extractLastStringAsFloat(line)
                            "mirror" -> mirrorType = extractLastStringAsInt(line)
                            "mirror_axis" -> {
                                val mirrorAxisValue = extractLastStringAsInt(line)
                                isMirrorAxisXEnabled = mirrorAxisValue and 1 != 0
                                isMirrorAxisYEnabled = mirrorAxisValue and 2 != 0
                                isMirrorAxisZEnabled = mirrorAxisValue and 4 != 0
                            }
                            "mirror_dis" -> mirrorDistance = extractLastStringAsFloat(line)
                            "vertex" -> {
                                val vertexQuantity = extractChunkQuantity(line)
                                val currentVertices = arrayOfNulls<MQOVertex>(vertexQuantity)
                                var vertexI = 0
                                while (true) {
                                    line = modelReader.readLine()
                                    if (!isNotChunkFinish(line) || vertexI >= vertexQuantity) break
                                    currentVertices[vertexI++] = parseVertexLine(line!!)
                                }
                                @Suppress("UNCHECKED_CAST")
                                vertices = currentVertices as Array<MQOVertex>
                            }
                            "face" -> {
                                val faceQuantity = extractChunkQuantity(line)
                                val currentFaces = arrayOfNulls<MQOFace>(faceQuantity)
                                var faceI = 0
                                while (true) {
                                    line = modelReader.readLine()
                                    if (!isNotChunkFinish(line) || faceI >= faceQuantity) break
                                    currentFaces[faceI++] = parseFaceLine(line!!)
                                }
                                @Suppress("UNCHECKED_CAST")
                                faces = currentFaces as Array<MQOFace>
                            }
                        }
                    }

                    val obj = MQOObject(
                        name,
                        isSmoothShadingEnabled,
                        autoSmoothAngle,
                        mirrorType,
                        isMirrorAxisXEnabled,
                        isMirrorAxisYEnabled,
                        isMirrorAxisZEnabled,
                        mirrorDistance,
                        vertices!!,
                        faces!!,
                    )

                    objects.add(obj)
                }
                else -> {
                }
            }
        }

        if (materials == null || objects.isEmpty()) {
            return MQOParseResult(null, MQOParseResultStatus.MISSING)
        }

        val model = MQOModel(materials, objects)
        return MQOParseResult(model, MQOParseResultStatus.SUCCESS)
    }

    private fun parseMaterialLine(materialLine: String): MQOMaterial {
        val name = extractFirstQuotedName(materialLine)
        return MQOMaterial(name)
    }

    private fun parseVertexLine(vertexLine: String): MQOVertex {
        val len = vertexLine.length
        var i = 0

        while (i < len && vertexLine[i] <= ' ') i++
        var start = i
        while (i < len && vertexLine[i] > ' ') i++
        val x = parseFloat(vertexLine.substring(start, i))

        while (i < len && vertexLine[i] <= ' ') i++
        start = i
        while (i < len && vertexLine[i] > ' ') i++
        val y = parseFloat(vertexLine.substring(start, i))

        while (i < len && vertexLine[i] <= ' ') i++
        start = i
        while (i < len && vertexLine[i] > ' ') i++
        val z = parseFloat(vertexLine.substring(start, i))

        return MQOVertex(x, y, z)
    }

    private fun parseFaceLine(faceLine: String): MQOFace {
        val len = faceLine.length
        val chars = faceLine.toCharArray()
        var i = 0

        var vertices = -1
        var vertexIndices: IntArray? = null
        var material = -2
        var uvs: Array<FloatArray>? = null
        var normals: Array<MQOVector?>? = null

        while (i < len) {
            val c = chars[i]

            if (c.isDigit() && vertices == -1) {
                val start = i
                while (i < len && chars[i].isDigit()) i++
                vertices = String(chars, start, i - start).toInt()
                continue
            }

            if (c == 'V' && i + 1 < len && chars[i + 1] == '(') {
                i += 2

                var temp = IntArray(if (vertices == -1) 16 else vertices)
                var count = 0

                while (i < len && chars[i] != ')') {
                    if (chars[i].isDigit()) {
                        val start = i
                        while (i < len && chars[i].isDigit()) i++
                        if (count == temp.size) {
                            temp = Arrays.copyOf(temp, temp.size * 2)
                        }
                        temp[count++] = String(chars, start, i - start).toInt()
                    } else {
                        i++
                    }
                }
                i++

                vertexIndices = Arrays.copyOf(temp, count)
                continue
            }

            if (c == 'M' && i + 1 < len && chars[i + 1] == '(') {
                i += 2

                val start = i
                while (i < len && ((chars[i] >= '0' && chars[i] <= '9') || chars[i] == '-')) i++
                material = String(chars, start, i - start).toInt()
                i++
                continue
            }

            if (c == 'U' && i + 2 < len && chars[i + 1] == 'V' && chars[i + 2] == '(') {
                i += 3

                var temp = FloatArray(if (vertices == -1) 32 else vertices * 2)
                var count = 0

                while (i < len && chars[i] != ')') {
                    if ((chars[i] >= '0' && chars[i] <= '9') || chars[i] == '-' || chars[i] == '.') {
                        val start = i
                        while (i < len && "0123456789eE+-.".indexOf(chars[i]) >= 0) i++
                        if (count == temp.size) {
                            temp = Arrays.copyOf(temp, temp.size * 2)
                        }
                        temp[count++] = parseFloat(String(chars, start, i - start))
                    } else {
                        i++
                    }
                }
                i++

                val pairCount = count / 2
                uvs = Array(pairCount) { FloatArray(2) }
                for (j in 0 until pairCount) {
                    uvs[j][0] = temp[j * 2]
                    uvs[j][1] = temp[j * 2 + 1]
                }
                continue
            }

            if (c == 'N' && i + 1 < len && chars[i + 1] == '(') {
                i += 2

                var temp = FloatArray(if (vertices == -1) 48 else vertices)
                var count = 0

                while (i < len && chars[i] != ')') {
                    if ((chars[i] >= '0' && chars[i] <= '9') || chars[i] == '-' || chars[i] == '.') {
                        val start = i
                        while (i < len && "0123456789.eE+-".indexOf(chars[i]) >= 0) i++
                        if (count == temp.size) {
                            temp = Arrays.copyOf(temp, temp.size * 2)
                        }
                        temp[count++] = parseFloat(String(chars, start, i - start))
                    } else {
                        i++
                    }
                }
                i++

                val indices = IntArray(vertices)
                for (j in 0 until vertices) {
                    indices[j] = temp[j].toInt()
                }

                val remaining = count - vertices
                val normalCount = remaining / 3

                val normalTable = arrayOfNulls<MQOVector>(normalCount)
                for (j in 0 until normalCount) {
                    normalTable[j] = MQOVector(
                        temp[vertices + j * 3],
                        temp[vertices + j * 3 + 1],
                        temp[vertices + j * 3 + 2],
                    )
                }

                normals = arrayOfNulls(vertices)

                var normalCursor = 0
                for (j in 0 until vertices) {
                    normals[j] = if (indices[j] == 0) {
                        null
                    } else {
                        normalTable[normalCursor++]
                    }
                }
                continue
            }

            i++
        }

        @Suppress("UNCHECKED_CAST")
        return MQOFace(vertices, vertexIndices!!, material, uvs!!, normals as Array<MQOVector>?)
    }

    private fun isGlobalChunkInitialLine(mqoLine: String?): Boolean {
        if (mqoLine == null || mqoLine.isEmpty()) return false

        val c = mqoLine[0]
        return isAlphabet(c)
    }

    private fun isNotChunkFinish(mqoLine: String?): Boolean {
        if (mqoLine == null) return false
        if (mqoLine.isEmpty()) return true

        val lastChar = mqoLine[mqoLine.length - 1]
        return lastChar != '}'
    }

    private fun extractGlobalChunkName(globalChunkInitialLine: String): MQOGlobalChunk {
        val len = globalChunkInitialLine.length
        var chunkName = ""
        var isNameFound = false
        for (i in 0 until len) {
            if (globalChunkInitialLine[i] == ' ') {
                isNameFound = true
                chunkName = globalChunkInitialLine.substring(0, i)
                break
            }
        }
        if (!isNameFound) chunkName = globalChunkInitialLine

        if (chunkName.isEmpty()) return MQOGlobalChunk.OTHER
        if ("Material".equals(chunkName, ignoreCase = true)) return MQOGlobalChunk.MATERIAL
        if ("Object".equals(chunkName, ignoreCase = true)) return MQOGlobalChunk.OBJECT
        if (MQOModel.forbiddenGlobalChunkNames.contains(chunkName.lowercase())) return MQOGlobalChunk.FORBIDDEN
        return MQOGlobalChunk.OTHER
    }

    private fun extractChunkQuantity(chunkInitialLine: String): Int {
        val len = chunkInitialLine.length
        var i = 0

        while (i < len && chunkInitialLine[i] <= ' ') i++
        while (i < len && chunkInitialLine[i] > ' ') i++
        while (i < len && chunkInitialLine[i] <= ' ') i++

        val start = i
        while (i < len && chunkInitialLine[i].isDigit()) i++

        return chunkInitialLine.substring(start, i).toInt()
    }

    private fun extractFirstQuotedName(lineIncludingQuotedName: String): String {
        val len = lineIncludingQuotedName.length
        var start = -1

        for (i in 0 until len) {
            val c = lineIncludingQuotedName[i]
            if (c != '"') continue

            if (start == -1) {
                start = i + 1
            } else {
                return lineIncludingQuotedName.substring(start, i)
            }
        }
        return ""
    }

    private fun extractObjectPropKey(objectLine: String): String {
        var start = -1

        for (i in objectLine.indices) {
            val c = objectLine[i]

            if (start == -1) {
                if (isAlphabet(c)) {
                    start = i
                }
            } else {
                if (c == ' ') {
                    return objectLine.substring(start, i)
                }
            }
        }
        return ""
    }

    private fun extractLastString(mqoLine: String): String {
        for (i in mqoLine.length - 1 downTo 1) {
            val c = mqoLine[i]
            if (c != ' ') continue

            return mqoLine.substring(i + 1)
        }
        return ""
    }

    private fun extractLastStringAsFloat(line: String): Float = parseFloat(extractLastString(line))

    private fun extractLastStringAsInt(line: String): Int = extractLastString(line).toInt()

    private fun isAlphabet(c: Char): Boolean = (c in 'A'..'Z') || (c in 'a'..'z')

    private fun parseFloat(str: String): Float = str.toFloat()

    @JvmRecord
    data class MQOParseResult(val model: MQOModel?, val status: MQOParseResultStatus)
}
