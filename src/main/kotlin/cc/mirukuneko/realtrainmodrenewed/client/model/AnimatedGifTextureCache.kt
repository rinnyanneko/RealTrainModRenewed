// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.model

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.w3c.dom.Node
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadata

internal object AnimatedGifTextureCache {
    private val entries = ConcurrentHashMap<String, Entry>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun resolve(cacheKey: String, tick: Double, opener: () -> InputStream?): Identifier? {
        if (failed.contains(cacheKey)) {
            return null
        }
        val entry = entries[cacheKey] ?: synchronized(entries) {
            entries[cacheKey] ?: runCatching { decode(cacheKey, opener) }
                .onFailure {
                    failed.add(cacheKey)
                    RealTrainModRenewed.LOGGER.warn("Failed to load GIF texture {}: {}", cacheKey, it.toString())
                }
                .getOrNull()
                ?.also { entries[cacheKey] = it }
        } ?: return null
        entry.update(tick)
        return entry.location
    }

    fun clear() {
        val textureManager = Minecraft.getInstance().textureManager
        entries.values.forEach { entry ->
            runCatching { textureManager.release(entry.location) }
            entry.frames.forEach { frame -> runCatching { frame.close() } }
        }
        entries.clear()
        failed.clear()
    }

    private fun decode(cacheKey: String, opener: () -> InputStream?): Entry? {
        val frames = mutableListOf<BufferedImage>()
        val delaysMs = mutableListOf<Int>()
        val input = opener() ?: return null
        input.use { stream ->
            ImageIO.createImageInputStream(stream).use { imageInput ->
                if (imageInput == null) return null
                val readers = ImageIO.getImageReadersByFormatName("gif")
                if (!readers.hasNext()) return null
                val reader = readers.next()
                try {
                    reader.setInput(imageInput, false)
                    val frameCount = reader.getNumImages(true)
                    if (frameCount <= 0) return null
                    val screenSize = logicalScreenSize(reader) ?: frameBounds(reader, frameCount)
                    var canvas = BufferedImage(screenSize.first, screenSize.second, BufferedImage.TYPE_INT_ARGB)
                    for (index in 0 until frameCount) {
                        val frame = reader.read(index)
                        val metadata = frameMetadata(reader.getImageMetadata(index))
                        val previous = if (metadata.disposal == Disposal.RESTORE_PREVIOUS) copyImage(canvas) else null
                        canvas.createGraphics().useGraphics {
                            it.drawImage(frame, metadata.left, metadata.top, null)
                        }
                        frames += copyImage(canvas)
                        delaysMs += metadata.delayMs.coerceAtLeast(20)
                        when (metadata.disposal) {
                            Disposal.RESTORE_BACKGROUND -> clearRegion(
                                canvas,
                                metadata.left,
                                metadata.top,
                                frame.width,
                                frame.height,
                            )
                            Disposal.RESTORE_PREVIOUS -> if (previous != null) canvas = previous
                            Disposal.NONE -> Unit
                        }
                    }
                } finally {
                    reader.dispose()
                }
            }
        }
        if (frames.isEmpty()) return null

        val nativeFrames = frames.map(::toNativeImage)
        val cumulativeMs = IntArray(delaysMs.size)
        var totalMs = 0
        delaysMs.forEachIndexed { index, delay ->
            totalMs += delay
            cumulativeMs[index] = totalMs
        }
        val first = NativeImage(nativeFrames[0].width, nativeFrames[0].height, false)
        first.copyFrom(nativeFrames[0])
        val texture = DynamicTexture({ "realtrainmodrenewed animated GIF" }, first)
        val location = Identifier.fromNamespaceAndPath(
            RealTrainModRenewed.MODID,
            "dynamic/gif/${Integer.toHexString(cacheKey.hashCode())}_${cacheKey.length and 0xFFFF}",
        )
        Minecraft.getInstance().textureManager.register(location, texture)
        return Entry(location, texture, nativeFrames, cumulativeMs, totalMs)
    }

    private fun logicalScreenSize(reader: ImageReader): Pair<Int, Int>? = runCatching {
        val metadata = reader.streamMetadata ?: return@runCatching null
        val root = metadata.getAsTree(metadata.nativeMetadataFormatName)
        val descriptor = findNode(root, "LogicalScreenDescriptor") ?: return@runCatching null
        val width = attributeInt(descriptor, "logicalScreenWidth", 0)
        val height = attributeInt(descriptor, "logicalScreenHeight", 0)
        if (width > 0 && height > 0) width to height else null
    }.getOrNull()

    private fun frameBounds(reader: ImageReader, frameCount: Int): Pair<Int, Int> {
        var width = 1
        var height = 1
        for (index in 0 until frameCount) {
            val metadata = runCatching { frameMetadata(reader.getImageMetadata(index)) }.getOrDefault(FrameMetadata())
            width = maxOf(width, metadata.left + reader.getWidth(index))
            height = maxOf(height, metadata.top + reader.getHeight(index))
        }
        return width to height
    }

    private fun frameMetadata(metadata: IIOMetadata): FrameMetadata = runCatching {
        val root = metadata.getAsTree(metadata.nativeMetadataFormatName)
        val descriptor = findNode(root, "ImageDescriptor")
        val control = findNode(root, "GraphicControlExtension")
        FrameMetadata(
            left = attributeInt(descriptor, "imageLeftPosition", 0),
            top = attributeInt(descriptor, "imageTopPosition", 0),
            delayMs = attributeInt(control, "delayTime", 0) * 10,
            disposal = when (attribute(control, "disposalMethod")) {
                "restoreToBackgroundColor" -> Disposal.RESTORE_BACKGROUND
                "restoreToPrevious" -> Disposal.RESTORE_PREVIOUS
                else -> Disposal.NONE
            },
        )
    }.getOrDefault(FrameMetadata())

    private fun findNode(node: Node?, name: String): Node? {
        if (node == null) return null
        if (node.nodeName.equals(name, ignoreCase = true)) return node
        var child = node.firstChild
        while (child != null) {
            findNode(child, name)?.let { return it }
            child = child.nextSibling
        }
        return null
    }

    private fun attribute(node: Node?, name: String): String? =
        node?.attributes?.getNamedItem(name)?.nodeValue

    private fun attributeInt(node: Node?, name: String, fallback: Int): Int =
        attribute(node, name)?.toIntOrNull() ?: fallback

    private fun copyImage(source: BufferedImage): BufferedImage =
        BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB).also { copy ->
            copy.createGraphics().useGraphics { it.drawImage(source, 0, 0, null) }
        }

    private fun clearRegion(image: BufferedImage, x: Int, y: Int, width: Int, height: Int) {
        for (pixelY in maxOf(0, y) until minOf(image.height, y + height)) {
            for (pixelX in maxOf(0, x) until minOf(image.width, x + width)) {
                image.setRGB(pixelX, pixelY, 0)
            }
        }
    }

    private fun toNativeImage(image: BufferedImage): NativeImage {
        val nativeImage = NativeImage(image.width, image.height, false)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xFF
                val red = argb ushr 16 and 0xFF
                val green = argb ushr 8 and 0xFF
                val blue = argb and 0xFF
                nativeImage.setPixel(x, y, alpha shl 24 or blue shl 16 or green shl 8 or red)
            }
        }
        return nativeImage
    }

    private inline fun <T : Graphics2D> T.useGraphics(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }

    private data class Entry(
        val location: Identifier,
        val texture: DynamicTexture,
        val frames: List<NativeImage>,
        val cumulativeMs: IntArray,
        val totalMs: Int,
        var shownFrame: Int = 0,
    ) {
        fun update(tick: Double) {
            if (frames.size <= 1 || totalMs <= 0) return
            val elapsedMs = (tick * 50.0).toLong()
            val frame = animatedGifFrameIndex(cumulativeMs, elapsedMs)
            if (frame == shownFrame) return
            shownFrame = frame
            val pixels = texture.pixels
            pixels.copyFrom(frames[frame])
            texture.upload()
        }
    }

    private data class FrameMetadata(
        val left: Int = 0,
        val top: Int = 0,
        val delayMs: Int = 50,
        val disposal: Disposal = Disposal.NONE,
    )

    private enum class Disposal {
        NONE,
        RESTORE_BACKGROUND,
        RESTORE_PREVIOUS,
    }
}

internal fun animatedGifFrameIndex(cumulativeMs: IntArray, elapsedMs: Long): Int {
    if (cumulativeMs.isEmpty()) return 0
    val totalMs = cumulativeMs.last()
    if (totalMs <= 0) return 0
    val time = Math.floorMod(elapsedMs, totalMs.toLong()).toInt()
    return cumulativeMs.indexOfFirst { time < it }.takeIf { it >= 0 } ?: cumulativeMs.lastIndex
}
