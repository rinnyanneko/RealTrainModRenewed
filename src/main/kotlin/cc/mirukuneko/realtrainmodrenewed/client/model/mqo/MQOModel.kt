// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.model.mqo

import java.util.Arrays
import java.util.Collections

@JvmRecord
data class MQOModel(
    val materials: Array<MQOMaterial>?,
    val objects: List<MQOObject>?
) {
    override fun toString(): String =
        "MQOModel[materials=${Arrays.toString(materials)}, objects=${objects!!.toString()}]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MQOModel) return false
        return materials === other.materials && objects == other.objects
    }

    override fun hashCode(): Int {
        var result = materials?.hashCode() ?: 0
        result = 31 * result + (objects?.hashCode() ?: 0)
        return result
    }

    companion object {
        @JvmField
        val forbiddenGlobalChunkNames: Set<String> = setOf("TrialNoise").mapToSet()

        @JvmField
        val necessaryGlobalChunkNames: Set<String> = setOf("Material", "Object").mapToSet()

        @JvmField
        val omittableGlobalChunkNames: Set<String> = setOf(
            "Metasequoia",
            "Format",
            "CodePage",
            "IncludeXml",
            "Thumbnail",
            "Scene",
            "Scene2",
            "BackImage",
            "MaterialEx2",
            "Blob",
            "Eof"
        ).mapToSet()

        private fun Set<String>.mapToSet(): Set<String> =
            Collections.unmodifiableSet(mapTo(LinkedHashSet()) { it.lowercase() })
    }
}
