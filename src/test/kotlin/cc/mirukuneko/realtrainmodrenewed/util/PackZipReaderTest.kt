package cc.mirukuneko.realtrainmodrenewed.util

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class PackZipReaderTest {
    @Test
    fun readsMs932EncodedEntryNamesFromBytes() {
        val bytes = createZip(Charset.forName("MS932"), "assets/minecraft/scripts/方向幕.js", "script")
        val entries = mutableListOf<String>()

        PackZipReader.read(bytes) { entry, input ->
            entries.add(entry.name)
            assertEquals("script", input.readAllBytes().decodeToString())
        }

        assertEquals(listOf("assets/minecraft/scripts/方向幕.js"), entries)
    }

    @Test
    fun opensMs932EncodedZipFile() {
        val bytes = createZip(Charset.forName("MS932"), "textures/train/試験.png", "png")
        val path = Files.createTempFile("rtmr-ms932", ".zip")
        try {
            path.writeBytes(bytes)

            PackZipReader.openZipFile(path).use { zipFile ->
                assertEquals("textures/train/試験.png", zipFile.entries().nextElement().name)
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun createZip(charset: Charset, entryName: String, content: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output, charset).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content.encodeToByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
