package cc.mirukuneko.realtrainmodrenewed.installedobject

import net.minecraft.world.phys.Vec3
import kotlin.math.max

class InstalledObjectDefinition(
    @JvmField val id: String,
    @JvmField val displayName: String,
    @JvmField val packName: String,
    @JvmField val category: InstalledObjectCategory,
    @JvmField val modelFile: String,
    @JvmField val scriptPath: String,
    buttonTexture: String?,
    textureOverrides: Map<String, String>?,
    modelOffset: Vec3?,
    modelScale: Float,
    @JvmField val smoothing: Boolean,
    width: Float,
    height: Float,
    depth: Float,
    signTexture: String?,
    emissiveTexture: String?,
    runningSound: String?,
    signalLightGroups: Map<Int, List<String>>?,
    renderObjects: List<String>?,
    scriptBodyPos: Vec3?,
    signFrame: Int,
    @JvmField val backTexture: Int
) {
    @JvmField val buttonTexture: String = buttonTexture ?: ""
    @JvmField val textureOverrides: Map<String, String> = textureOverrides ?: emptyMap()
    @JvmField val modelOffset: Vec3 = modelOffset ?: Vec3.ZERO
    @JvmField val modelScale: Float = if (modelScale <= 0.0F) 1.0F else modelScale
    @JvmField val width: Float = if (width <= 0.0F) 1.0F else width
    @JvmField val height: Float = if (height <= 0.0F) 1.0F else height
    @JvmField val depth: Float = if (depth <= 0.0F) 0.125F else depth
    @JvmField val signTexture: String = signTexture ?: ""
    @JvmField val emissiveTexture: String = emissiveTexture ?: ""
    @JvmField val runningSound: String = runningSound ?: ""
    @JvmField val signalLightGroups: Map<Int, List<String>> = signalLightGroups ?: emptyMap()
    @JvmField val renderObjects: List<String> = renderObjects ?: emptyList()
    @JvmField val scriptBodyPos: Vec3 = scriptBodyPos ?: Vec3.ZERO
    @JvmField val signFrame: Int = max(1, signFrame)

    var wireAttachPos: Vec3 = Vec3.ZERO
        private set

    var sectionLength: Float = 0.5F
        private set
    var deflectionCoefficient: Float = 0.0F
        private set

    fun setWireParams(sectionLength: Float, deflectionCoefficient: Float) {
        if (sectionLength > 0.0F) this.sectionLength = sectionLength
        this.deflectionCoefficient = max(0.0F, deflectionCoefficient)
    }

    fun setWireAttachPos(pos: Vec3?) {
        wireAttachPos = pos ?: Vec3.ZERO
    }

    // Convenience constructors matching Java overloads
    constructor(
        id: String, displayName: String, packName: String, category: InstalledObjectCategory,
        modelFile: String, scriptPath: String, textureOverrides: Map<String, String>?,
        modelOffset: Vec3?, modelScale: Float, smoothing: Boolean,
        width: Float, height: Float, depth: Float, signTexture: String?
    ) : this(id, displayName, packName, category, modelFile, scriptPath, "",
        textureOverrides, modelOffset, modelScale, smoothing, width, height, depth, signTexture,
        "", "", emptyMap(), Vec3.ZERO, 1, 1)

    constructor(
        id: String, displayName: String, packName: String, category: InstalledObjectCategory,
        modelFile: String, scriptPath: String, buttonTexture: String?, textureOverrides: Map<String, String>?,
        modelOffset: Vec3?, modelScale: Float, smoothing: Boolean,
        width: Float, height: Float, depth: Float, signTexture: String?,
        emissiveTexture: String?, runningSound: String?, signalLightGroups: Map<Int, List<String>>?,
        scriptBodyPos: Vec3?
    ) : this(id, displayName, packName, category, modelFile, scriptPath, buttonTexture,
        textureOverrides, modelOffset, modelScale, smoothing, width, height, depth, signTexture,
        emissiveTexture, runningSound, signalLightGroups, emptyList(), scriptBodyPos, 1, 1)

    constructor(
        id: String, displayName: String, packName: String, category: InstalledObjectCategory,
        modelFile: String, scriptPath: String, buttonTexture: String?, textureOverrides: Map<String, String>?,
        modelOffset: Vec3?, modelScale: Float, smoothing: Boolean,
        width: Float, height: Float, depth: Float, signTexture: String?,
        emissiveTexture: String?, runningSound: String?, signalLightGroups: Map<Int, List<String>>?,
        scriptBodyPos: Vec3?, signFrame: Int, backTexture: Int
    ) : this(id, displayName, packName, category, modelFile, scriptPath, buttonTexture,
        textureOverrides, modelOffset, modelScale, smoothing, width, height, depth, signTexture,
        emissiveTexture, runningSound, signalLightGroups, emptyList(), scriptBodyPos, signFrame, backTexture)
}
