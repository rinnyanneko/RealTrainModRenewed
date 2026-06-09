package cc.mirukuneko.realtrainmodrenewed.item

data class SelectableModelInfo @JvmOverloads constructor(
    val id: String,
    val displayName: String,
    val packName: String,
    val buttonTexture: String,
    val category: String = packName,
)
