// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.rail

import net.minecraft.world.phys.Vec3

class RailDefinition @JvmOverloads constructor(
    val id: String,
    val displayName: String,
    val packName: String,
    val packResourcePath: String,
    val modelFile: String,
    val scriptPath: String,
    buttonTexture: String?,
    textureOverrides: Map<String, String>?,
    modelOffset: Vec3?,
    modelScale: Float,
    ballastWidth: Int,
    ballastBlockId: String? = "",
) {
    val buttonTexture: String = buttonTexture ?: ""
    val textureOverrides: Map<String, String> = textureOverrides?.toMap() ?: emptyMap()
    val modelOffset: Vec3 = modelOffset ?: Vec3.ZERO
    val modelScale: Float = if (modelScale <= 0f) 1.0f else modelScale
    val ballastWidth: Int = ballastWidth.coerceAtLeast(0)
    val ballastBlockId: String = ballastBlockId ?: ""
}
