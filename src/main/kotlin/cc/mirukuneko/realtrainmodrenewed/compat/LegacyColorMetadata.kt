// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.compat

object LegacyColorMetadata {
    @JvmField
    val names: Array<String> = arrayOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )

    @JvmStatic
    fun decodeBlockPath(path: String): Pair<String, Int> {
        for (index in names.indices) {
            val prefix = names[index] + "_"
            if (path.startsWith(prefix)) return "white_" + path.removePrefix(prefix) to index
        }
        return path to 0
    }
}
