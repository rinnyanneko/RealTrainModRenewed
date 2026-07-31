// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.client.model

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import it.unimi.dsi.fastutil.floats.FloatArrayList
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.AtlasIds
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.IdentityHashMap
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reader for NGTO Builder's voxel model formats.
 *
 * NGTO is an outer NBT containing a gzip-compressed inner NBT. NGTZ is a zip
 * of named NGTO files; entry names are preserved as model group names.
 */
object NgtoModelGeometry {
    private const val MAX_MINIATURE_DEPTH = 8
    private const val MAX_NBT_BYTES = 128L * 1024L * 1024L
    private const val MAX_NGTZ_ENTRY_BYTES = 64 * 1024 * 1024
    private const val MAX_NGTZ_TOTAL_BYTES = 256L * 1024L * 1024L
    private const val MAX_NGTZ_ENTRIES = 1_024
    private const val MAX_STRUCTURE_CELLS = 8_000_000L
    private const val MAX_EXPANDED_CELLS = 16_000_000L
    private const val MAX_GEOMETRY_BYTES = 64 * 1024 * 1024
    private const val FLOATS_PER_QUAD = 4 * 8
    private const val MAX_RENDERED_QUADS = MAX_GEOMETRY_BYTES / (FLOATS_PER_QUAD * Float.SIZE_BYTES)

    internal data class PaletteEntry(
        val legacyName: String,
        val block: Block,
        val translucent: Boolean,
        val tileNbt: CompoundTag? = null,
    )

    internal data class Structure(
        val sizeX: Int,
        val sizeY: Int,
        val sizeZ: Int,
        val cells: IntArray,
        val palette: Map<Int, PaletteEntry>,
        val cellNbt: Map<Int, CompoundTag>,
    )

    private data class RenderBudget(
        var remainingCells: Long = MAX_EXPANDED_CELLS,
        var remainingQuads: Int = MAX_RENDERED_QUADS,
        var reportedCellLimit: Boolean = false,
        var reportedQuadLimit: Boolean = false,
    )

    fun isNgto(path: String?): Boolean {
        return NgtoFormat.isModelPath(path)
    }

    fun build(bytes: ByteArray, modelFile: String): MqoModelLoader.MqoModel? {
        val parts = if (modelFile.lowercase(Locale.ROOT).endsWith(".ngtz")) {
            readNgtz(bytes)
        } else {
            listOf("default" to bytes)
        }
        val batches = mutableListOf<MqoModelLoader.Batch?>()
        val atlasTexture = Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.BLOCKS).location()
        val renderBudget = RenderBudget()
        var order = 0
        for ((name, partBytes) in parts) {
            val structure = readStructure(partBytes) ?: continue
            val dataByTexture = linkedMapOf<Pair<Identifier, Boolean>, FloatArrayList>()
            val rootTransform = AffineTransform.translation(
                -structure.sizeX * 0.5f,
                0f,
                -structure.sizeZ * 0.5f,
            )
            appendStructure(
                structure,
                dataByTexture,
                rootTransform,
                0,
                IdentityHashMap(),
                renderBudget,
            )
            val dataIterator = dataByTexture.entries.iterator()
            while (dataIterator.hasNext()) {
                val (key, values) = dataIterator.next()
                val data = values.toFloatArray()
                dataIterator.remove()
                val batch = MqoModelLoader.Batch(
                    order++, name, atlasTexture, emptyArray(), data, data.size / 8,
                    0, key.second, 0f, 1f, 0f, 1f,
                )
                batch.glassTranslucent = key.second
                batch.explicitGlassOnly = key.second
                batch.opaqueTexture = atlasTexture
                batch.windowTexture = atlasTexture
                batches.add(batch)
            }
        }
        if (batches.isEmpty()) return null
        return MqoModelLoader.MqoModel(batches, mutableListOf(atlasTexture))
    }

    internal fun readStructure(bytes: ByteArray): Structure? {
        if (bytes.isEmpty()) return null
        return try {
            val outer = NbtIo.read(
                DataInputStream(ByteArrayInputStream(bytes)),
                NbtAccounter.create(MAX_NBT_BYTES),
            )
            readStructure(outer)
        } catch (error: Exception) {
            RealTrainModRenewed.LOGGER.warn("[NGTO] Failed to read voxel NBT", error)
            null
        }
    }

    internal fun readStructure(container: CompoundTag): Structure? {
        return try {
            val innerBytes = container.getByteArray("ByteData").orElse(null)
            val root = if (innerBytes != null) {
                GZIPInputStream(ByteArrayInputStream(innerBytes)).use {
                    NbtIo.read(DataInputStream(it), NbtAccounter.create(MAX_NBT_BYTES))
                }
            } else {
                container
            }
            val sizeX = root.getIntOr("SizeX", 1).coerceAtLeast(1)
            val sizeY = root.getIntOr("SizeY", 1).coerceAtLeast(1)
            val sizeZ = root.getIntOr("SizeZ", 1).coerceAtLeast(1)
            val expectedLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
            if (expectedLong > MAX_STRUCTURE_CELLS) {
                RealTrainModRenewed.LOGGER.warn(
                    "[NGTO] Dimensions exceed the {} cell limit: {}x{}x{}",
                    MAX_STRUCTURE_CELLS,
                    sizeX,
                    sizeY,
                    sizeZ,
                )
                return null
            }
            val expected = expectedLong.toInt()
            val cells = root.getIntArray("IData").orElse(null)
                ?: root.getIntArray("Blocks").orElse(null)
                ?: NgtoFormat.decodeLegacyByteIds(root.getByteArray("BData").orElse(ByteArray(0)))
            if (cells.size < expected) {
                RealTrainModRenewed.LOGGER.warn("[NGTO] Cell data is shorter than dimensions: {} < {}", cells.size, expected)
            }
            val palette = mutableMapOf(
                0 to PaletteEntry("minecraft:air", Blocks.AIR, false),
            )
            for (tag in root.getListOrEmpty("IdList")) {
                val entry = tag as? CompoundTag ?: continue
                val set = entry.getCompoundOrEmpty("Set")
                val legacyName = set.getStringOr("Block", "air")
                val meta = set.getIntOr("Meta", 0)
                val block = resolveLegacyBlock(legacyName, meta)
                val path = BuiltInRegistries.BLOCK.getKey(block).path
                palette[entry.getIntOr("Id", 0)] = PaletteEntry(
                    legacyName,
                    block,
                    path.contains("glass") || path.contains("ice"),
                    set.getCompound("TagData").orElse(null),
                )
            }
            val cellNbt = mutableMapOf<Int, CompoundTag>()
            val perCellNbt = root.getCompoundOrEmpty("NBTs")
            for (key in perCellNbt.keySet()) {
                val index = key.toIntOrNull() ?: continue
                val tag = perCellNbt.getCompound(key).orElse(null) ?: continue
                cellNbt[index] = tag
            }
            Structure(sizeX, sizeY, sizeZ, cells, palette, cellNbt)
        } catch (error: Exception) {
            RealTrainModRenewed.LOGGER.warn("[NGTO] Failed to read voxel NBT", error)
            null
        }
    }

    internal fun readNgtz(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val parts = mutableListOf<Pair<String, ByteArray>>()
        var totalBytes = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(".ngto", true)) continue
                    if (parts.size >= MAX_NGTZ_ENTRIES) {
                        RealTrainModRenewed.LOGGER.warn(
                            "[NGTZ] Archive exceeds the {} model entry limit",
                            MAX_NGTZ_ENTRIES,
                        )
                        break
                    }
                    val leaf = entry.name.substringAfterLast('/').substringBeforeLast('.')
                    val part = readZipEntry(zip, entry.name) ?: continue
                    totalBytes += part.size
                    if (totalBytes > MAX_NGTZ_TOTAL_BYTES) {
                        RealTrainModRenewed.LOGGER.warn(
                            "[NGTZ] Expanded archive exceeds the {} byte limit",
                            MAX_NGTZ_TOTAL_BYTES,
                        )
                        break
                    }
                    parts.add(leaf to part)
                }
            }
        } catch (error: NgtoLimitException) {
            RealTrainModRenewed.LOGGER.warn("[NGTZ] {}", error.message)
        } catch (error: Exception) {
            RealTrainModRenewed.LOGGER.warn("[NGTZ] Failed to read archive", error)
        }
        return parts
    }

    private fun readZipEntry(zip: ZipInputStream, name: String): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_NGTZ_ENTRY_BYTES) {
                throw NgtoLimitException(
                    "$name exceeds the $MAX_NGTZ_ENTRY_BYTES byte entry limit; aborting archive",
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private class NgtoLimitException(message: String) : Exception(message)

    private fun appendStructure(
        structure: Structure,
        sinks: MutableMap<Pair<Identifier, Boolean>, FloatArrayList>,
        transform: AffineTransform,
        depth: Int,
        nestedCache: IdentityHashMap<CompoundTag, Structure?>,
        budget: RenderBudget,
    ) {
        val structureCells = structure.sizeX.toLong() * structure.sizeY.toLong() * structure.sizeZ.toLong()
        if (structureCells > budget.remainingCells) {
            if (!budget.reportedCellLimit) {
                budget.reportedCellLimit = true
                RealTrainModRenewed.LOGGER.warn(
                    "[NGTO] Expanded miniature geometry exceeds the {} cell render limit",
                    MAX_EXPANDED_CELLS,
                )
            }
            return
        }
        budget.remainingCells -= structureCells
        val atlas = Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.BLOCKS)
        fun index(x: Int, y: Int, z: Int) = x * structure.sizeY * structure.sizeZ + y * structure.sizeZ + z
        fun cell(x: Int, y: Int, z: Int): Pair<PaletteEntry, CompoundTag?>? {
            if (x !in 0 until structure.sizeX || y !in 0 until structure.sizeY || z !in 0 until structure.sizeZ) return null
            val cellIndex = index(x, y, z)
            val id = structure.cells.getOrNull(cellIndex) ?: 0
            val entry = structure.palette[id] ?: return null
            return entry to (structure.cellNbt[cellIndex] ?: entry.tileNbt)
        }

        for (x in 0 until structure.sizeX) for (y in 0 until structure.sizeY) for (z in 0 until structure.sizeZ) {
            val (current, tileNbt) = cell(x, y, z) ?: continue
            if (current.block == Blocks.AIR) continue

            val miniatureData = tileNbt?.getCompound("BlocksData")?.orElse(null)
            if (isMiniature(current.legacyName) && miniatureData != null) {
                if (depth >= MAX_MINIATURE_DEPTH) {
                    RealTrainModRenewed.LOGGER.warn(
                        "[NGTO] Skipping miniature nested deeper than {} levels",
                        MAX_MINIATURE_DEPTH,
                    )
                    continue
                }
                val nested = if (nestedCache.containsKey(miniatureData)) {
                    nestedCache[miniatureData]
                } else {
                    readStructure(miniatureData).also { nestedCache[miniatureData] = it }
                }
                if (nested != null) {
                    appendStructure(
                        nested,
                        sinks,
                        transform.compose(miniatureTransform(x, y, z, nested, tileNbt)),
                        depth + 1,
                        nestedCache,
                        budget,
                    )
                    continue
                }
            }

            val blockId = BuiltInRegistries.BLOCK.getKey(current.block)
            val spriteId = Identifier.fromNamespaceAndPath(blockId.namespace, "block/${blockId.path}")
            val sprite = atlas.getSprite(spriteId)
            val sink = sinks.getOrPut(spriteId to current.translucent) { FloatArrayList() }
            for (direction in Direction.entries) {
                val adjacentCell = cell(x + direction.stepX, y + direction.stepY, z + direction.stepZ)
                val adjacent = adjacentCell?.first
                val adjacentIsMiniature = adjacentCell?.let {
                    isMiniature(it.first.legacyName) && it.second?.contains("BlocksData") == true
                } == true
                if (!adjacentIsMiniature && adjacent != null && adjacent.block != Blocks.AIR &&
                    (adjacent.block == current.block || !current.translucent || !adjacent.translucent)
                ) continue
                if (budget.remainingQuads <= 0) {
                    if (!budget.reportedQuadLimit) {
                        budget.reportedQuadLimit = true
                        RealTrainModRenewed.LOGGER.warn(
                            "[NGTO] Geometry exceeds the {} rendered quad limit",
                            MAX_RENDERED_QUADS,
                        )
                    }
                    return
                }
                budget.remainingQuads--
                appendFace(
                    sink,
                    direction,
                    transform.compose(AffineTransform.translation(x.toFloat(), y.toFloat(), z.toFloat())),
                    sprite.u0, sprite.u1, sprite.v0, sprite.v1,
                )
            }
        }
    }

    private fun miniatureTransform(
        x: Int,
        y: Int,
        z: Int,
        nested: Structure,
        tileNbt: CompoundTag,
    ): AffineTransform {
        val legacyRate = tileNbt.getIntOr("MinimizeRate", 1).coerceAtLeast(1)
        val rawScale = if (tileNbt.contains("Scale")) {
            tileNbt.getFloatOr("Scale", 1f)
        } else {
            1f / legacyRate.toFloat()
        }
        val scale = rawScale.takeIf { it.isFinite() && it != 0f } ?: 1f
        val attachSide = tileNbt.getByteOr("AttachSide", 1).toInt()
        val attachRotation = when (attachSide) {
            0 -> AffineTransform.rotateZ(180f)
            2 -> AffineTransform.rotateX(-90f)
            3 -> AffineTransform.rotateX(90f)
            4 -> AffineTransform.rotateZ(90f)
            5 -> AffineTransform.rotateZ(-90f)
            else -> AffineTransform.IDENTITY
        }
        return AffineTransform.translation(x + 0.5f, y + 0.5f, z + 0.5f)
            .compose(attachRotation)
            .compose(AffineTransform.translation(0f, -0.5f, 0f))
            .compose(AffineTransform.rotateY(tileNbt.getFloatOr("Yaw", 0f)))
            .compose(
                AffineTransform.translation(
                    tileNbt.getFloatOr("OffsetX", 0f),
                    tileNbt.getFloatOr("OffsetY", 0f),
                    tileNbt.getFloatOr("OffsetZ", 0f),
                ),
            )
            .compose(AffineTransform.scale(scale))
            .compose(
                AffineTransform.translation(
                    -nested.sizeX * 0.5f,
                    0f,
                    -nested.sizeZ * 0.5f,
                ),
            )
    }

    private fun appendFace(
        out: FloatArrayList,
        direction: Direction,
        transform: AffineTransform,
        u0: Float,
        u1: Float,
        v0: Float,
        v1: Float,
    ) {
        val vertices = when (direction) {
            Direction.DOWN -> arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f))
            Direction.UP -> arrayOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f))
            Direction.NORTH -> arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 1f, 0f))
            Direction.SOUTH -> arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f))
            Direction.WEST -> arrayOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 1f), floatArrayOf(0f, 1f, 0f))
            Direction.EAST -> arrayOf(floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 1f, 1f))
        }
        val uv = arrayOf(floatArrayOf(u0, v1), floatArrayOf(u1, v1), floatArrayOf(u1, v0), floatArrayOf(u0, v0))
        val normal = transform.normal(
            direction.stepX.toFloat(),
            direction.stepY.toFloat(),
            direction.stepZ.toFloat(),
        )
        for (index in vertices.indices) {
            val vertex = transform.point(
                vertices[index][0],
                vertices[index][1],
                vertices[index][2],
            )
            out.add(vertex[0])
            out.add(vertex[1])
            out.add(vertex[2])
            out.add(normal[0])
            out.add(normal[1])
            out.add(normal[2])
            out.add(uv[index][0])
            out.add(uv[index][1])
        }
    }

    private fun isMiniature(rawName: String): Boolean {
        return rawName.equals("mcte:miniature", true)
    }

    /**
     * Small affine transform used while flattening nested miniature structures.
     * `compose(local)` preserves the legacy OpenGL call order: the local
     * transform is applied first, followed by this transform.
     */
    internal data class AffineTransform(
        val m00: Float,
        val m01: Float,
        val m02: Float,
        val m10: Float,
        val m11: Float,
        val m12: Float,
        val m20: Float,
        val m21: Float,
        val m22: Float,
        val tx: Float,
        val ty: Float,
        val tz: Float,
    ) {
        fun compose(local: AffineTransform): AffineTransform {
            return AffineTransform(
                m00 * local.m00 + m01 * local.m10 + m02 * local.m20,
                m00 * local.m01 + m01 * local.m11 + m02 * local.m21,
                m00 * local.m02 + m01 * local.m12 + m02 * local.m22,
                m10 * local.m00 + m11 * local.m10 + m12 * local.m20,
                m10 * local.m01 + m11 * local.m11 + m12 * local.m21,
                m10 * local.m02 + m11 * local.m12 + m12 * local.m22,
                m20 * local.m00 + m21 * local.m10 + m22 * local.m20,
                m20 * local.m01 + m21 * local.m11 + m22 * local.m21,
                m20 * local.m02 + m21 * local.m12 + m22 * local.m22,
                m00 * local.tx + m01 * local.ty + m02 * local.tz + tx,
                m10 * local.tx + m11 * local.ty + m12 * local.tz + ty,
                m20 * local.tx + m21 * local.ty + m22 * local.tz + tz,
            )
        }

        fun point(x: Float, y: Float, z: Float): FloatArray {
            return floatArrayOf(
                m00 * x + m01 * y + m02 * z + tx,
                m10 * x + m11 * y + m12 * z + ty,
                m20 * x + m21 * y + m22 * z + tz,
            )
        }

        fun normal(x: Float, y: Float, z: Float): FloatArray {
            val nx = m00 * x + m01 * y + m02 * z
            val ny = m10 * x + m11 * y + m12 * z
            val nz = m20 * x + m21 * y + m22 * z
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            if (length <= 0f) return floatArrayOf(x, y, z)
            return floatArrayOf(nx / length, ny / length, nz / length)
        }

        companion object {
            val IDENTITY = AffineTransform(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                0f, 0f, 0f,
            )

            fun translation(x: Float, y: Float, z: Float): AffineTransform {
                return IDENTITY.copy(tx = x, ty = y, tz = z)
            }

            fun scale(value: Float): AffineTransform {
                return IDENTITY.copy(m00 = value, m11 = value, m22 = value)
            }

            fun rotateX(degrees: Float): AffineTransform {
                val radians = Math.toRadians(degrees.toDouble())
                val c = cos(radians).toFloat()
                val s = sin(radians).toFloat()
                return IDENTITY.copy(
                    m11 = c,
                    m12 = -s,
                    m21 = s,
                    m22 = c,
                )
            }

            fun rotateY(degrees: Float): AffineTransform {
                val radians = Math.toRadians(degrees.toDouble())
                val c = cos(radians).toFloat()
                val s = sin(radians).toFloat()
                return IDENTITY.copy(
                    m00 = c,
                    m02 = s,
                    m20 = -s,
                    m22 = c,
                )
            }

            fun rotateZ(degrees: Float): AffineTransform {
                val radians = Math.toRadians(degrees.toDouble())
                val c = cos(radians).toFloat()
                val s = sin(radians).toFloat()
                return IDENTITY.copy(
                    m00 = c,
                    m01 = -s,
                    m10 = s,
                    m11 = c,
                )
            }
        }
    }

    private fun resolveLegacyBlock(rawName: String, meta: Int): Block {
        val normalized = rawName.lowercase(Locale.ROOT)
        val namespace = normalized.substringBefore(':', "minecraft")
        val name = normalized.substringAfter(':')
        val colors = arrayOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
        val color = colors[meta and 15]
        val modern = when (name) {
            "wool" -> "${color}_wool"
            "carpet" -> "${color}_carpet"
            "stained_glass" -> "${color}_stained_glass"
            "stained_glass_pane" -> "${color}_stained_glass_pane"
            "stained_hardened_clay" -> "${color}_terracotta"
            "hardened_clay" -> "terracotta"
            "concrete" -> "${color}_concrete"
            "concrete_powder" -> "${color}_concrete_powder"
            "planks" -> arrayOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak")[meta.coerceIn(0, 5)] + "_planks"
            else -> name
        }
        return BuiltInRegistries.BLOCK.getOptional(
            Identifier.fromNamespaceAndPath(namespace, modern),
        ).orElse(Blocks.STONE)
    }
}
