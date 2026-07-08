package cc.mirukuneko.realtrainmodrenewed.util

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LegacyResourcePathUtilTest {
    @Test
    fun expandsLegacyButtonTexturePaths() {
        val candidates = buttonTexturePathCandidates("minecraft:textures/train/button/JRE127")

        assertContains(candidates, "textures/train/button/JRE127")
        assertContains(candidates, "textures/train/button/JRE127.png")
        assertContains(candidates, "train/button/JRE127.png")
    }

    @Test
    fun expandsAssetsMinecraftButtonTexturePaths() {
        val candidates = buttonTexturePathCandidates("assets/minecraft/textures/train/button/JR701.png")

        assertContains(candidates, "assets/minecraft/textures/train/button/JR701.png")
        assertContains(candidates, "textures/train/button/JR701.png")
        assertContains(candidates, "train/button/JR701.png")
    }

    @Test
    fun findsLegacyRunFolderVariantByTrailingPath() {
        val root = Files.createTempDirectory("rtmr-sounds")
        try {
            val expected = root.resolve("rtmlib/run2/209igbt/loop.ogg")
            expected.parent.createDirectories()
            expected.writeBytes(byteArrayOf(1, 2, 3))
            val unrelated = root.resolve("rtmlib/run2/other/loop.ogg")
            unrelated.parent.createDirectories()
            unrelated.writeBytes(byteArrayOf(4, 5, 6))

            val replacement = findBestReplacementSound(root, "rtmlib/run/209igbt/loop")

            assertEquals(expected, replacement)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun leavesAmbiguousLeafOnlyMatchesUnresolved() {
        val root = Files.createTempDirectory("rtmr-sounds")
        try {
            root.resolve("a/loop.ogg").also {
                it.parent.createDirectories()
                it.writeBytes(byteArrayOf(1))
            }
            root.resolve("b/loop.ogg").also {
                it.parent.createDirectories()
                it.writeBytes(byteArrayOf(2))
            }

            val replacement = findBestReplacementSound(root, "missing/path/loop")

            assertEquals(null, replacement)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
