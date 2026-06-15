package cc.mirukuneko.realtrainmodrenewed.client.model.mqo

data class MQOFace(
    val vertices: Int,
    val vertexIndices: IntArray,
    val material: Int,
    val uvs: Array<FloatArray>,
    val normals: Array<MQOVector>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MQOFace) return false
        return vertices == other.vertices && vertexIndices.contentEquals(other.vertexIndices) &&
            material == other.material && uvs.contentDeepEquals(other.uvs) &&
            normals.contentEquals(other.normals)
    }
    override fun hashCode(): Int {
        var result = vertices; result = 31 * result + vertexIndices.contentHashCode()
        result = 31 * result + material; result = 31 * result + uvs.contentDeepHashCode()
        result = 31 * result + (normals?.contentHashCode() ?: 0); return result
    }
}

data class MQOObject(
    val name: String, val isSmoothShadingEnabled: Boolean, val autoSmoothAngle: Float,
    val mirrorType: Int, val isMirrorAxisXEnabled: Boolean, val isMirrorAxisYEnabled: Boolean,
    val isMirrorAxisZEnabled: Boolean, val mirrorDistance: Float,
    val vertices: Array<MQOVertex>, val faces: Array<MQOFace>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MQOObject) return false
        return name == other.name && isSmoothShadingEnabled == other.isSmoothShadingEnabled &&
            autoSmoothAngle == other.autoSmoothAngle && mirrorType == other.mirrorType &&
            isMirrorAxisXEnabled == other.isMirrorAxisXEnabled && isMirrorAxisYEnabled == other.isMirrorAxisYEnabled &&
            isMirrorAxisZEnabled == other.isMirrorAxisZEnabled && mirrorDistance == other.mirrorDistance &&
            vertices.contentEquals(other.vertices) && faces.contentEquals(other.faces)
    }
    override fun hashCode(): Int {
        var result = name.hashCode(); result = 31 * result + isSmoothShadingEnabled.hashCode()
        result = 31 * result + autoSmoothAngle.hashCode(); result = 31 * result + mirrorType
        result = 31 * result + isMirrorAxisXEnabled.hashCode(); result = 31 * result + isMirrorAxisYEnabled.hashCode()
        result = 31 * result + isMirrorAxisZEnabled.hashCode(); result = 31 * result + mirrorDistance.hashCode()
        result = 31 * result + vertices.contentHashCode(); result = 31 * result + faces.contentHashCode(); return result
    }
}
