package cc.mirukuneko.realtrainmodrenewed.util

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class PackTextDecoderTest {
    @Test
    fun decodesUtf8Text() {
        assertEquals("RealTrainMod Renewed", decodeText("RealTrainMod Renewed".toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun stripsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "marker".toByteArray(StandardCharsets.UTF_8)
        assertEquals("marker", decodeText(bytes))
    }

    @Test
    fun fallsBackToMs932() {
        val bytes = "試験列車".toByteArray(Charset.forName("MS932"))
        assertEquals("試験列車", decodeText(bytes))
    }

    @Test
    fun fallsBackToShiftJis() {
        val bytes = "方向幕".toByteArray(Charset.forName("Shift_JIS"))
        assertEquals("方向幕", decodeJson(bytes))
    }

    @Test
    fun readsInputStream() {
        val input = ByteArrayInputStream("script".toByteArray(StandardCharsets.UTF_8))
        assertEquals("script", readText(input))
    }
}
