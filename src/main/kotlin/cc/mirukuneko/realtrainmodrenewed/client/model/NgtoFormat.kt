// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.client.model

object NgtoFormat {
    @JvmStatic
    fun isModelPath(path: String?): Boolean {
        val lower = path?.lowercase() ?: return false
        return lower.endsWith(".ngto") || lower.endsWith(".ngtz")
    }

    @JvmStatic
    fun decodeLegacyByteIds(bytes: ByteArray): IntArray =
        bytes.map { (it.toInt() + 128) and 0xFF }.toIntArray()
}
