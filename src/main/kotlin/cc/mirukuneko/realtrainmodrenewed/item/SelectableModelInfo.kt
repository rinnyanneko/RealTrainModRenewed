// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

data class SelectableModelInfo @JvmOverloads constructor(
    val id: String,
    val displayName: String,
    val packName: String,
    val buttonTexture: String,
    val category: String = packName,
)
