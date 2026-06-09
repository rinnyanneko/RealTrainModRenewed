package cc.mirukuneko.realtrainmodrenewed.item

data class SelectableModelInfo @JvmOverloads constructor(
    private val id: String,
    private val displayName: String,
    private val packName: String,
    private val buttonTexture: String,
    private val category: String = packName,
) {
    fun id(): String = id

    fun displayName(): String = displayName

    fun packName(): String = packName

    fun buttonTexture(): String = buttonTexture

    fun category(): String = category
}
