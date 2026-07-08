package cc.mirukuneko.realtrainmodrenewed.model

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import net.minecraft.resources.Identifier

class MQOModel {
    data class Vertex(@JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float)

    data class Face(
        @JvmField val vertexIndices: IntArray,
        @JvmField val uvs: Array<FloatArray>?,
        @JvmField val materialName: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Face) return false
            return vertexIndices.contentEquals(other.vertexIndices) &&
                uvs.contentDeepEquals(other.uvs) &&
                materialName == other.materialName
        }

        override fun hashCode(): Int {
            var result = vertexIndices.contentHashCode()
            result = 31 * result + (uvs?.contentDeepHashCode() ?: 0)
            result = 31 * result + materialName.hashCode()
            return result
        }
    }

    data class Material(@JvmField val name: String, @JvmField val texture: Identifier)

    data class ObjectGroup(@JvmField val name: String)

    private val vertices: MutableList<Vertex> = ArrayList()
    private val faces: MutableList<Face> = ArrayList()
    private val materials: MutableMap<String, Material> = HashMap()
    private val groups: MutableList<ObjectGroup> = ArrayList()
    private var scriptEngine: Any? = null

    fun addVertex(x: Float, y: Float, z: Float) {
        vertices.add(Vertex(x, y, z))
    }

    fun addFace(vertexIndices: IntArray, uvs: Array<FloatArray>?, materialName: String) {
        faces.add(Face(vertexIndices, uvs, materialName))
    }

    fun addMaterial(name: String, texture: Identifier) {
        materials[name] = Material(name, texture)
    }

    fun addGroup(name: String) {
        groups.add(ObjectGroup(name))
    }

    @JvmOverloads
    fun render(poseStack: PoseStack, consumer: VertexConsumer, packedLight: Int, pass: Int = 0) {
        if (scriptEngine != null) executeScript(poseStack, pass)
        RealTrainModRenewed.LOGGER.debug(
            "Rendering MQO model: {} vertices, {} faces, {} materials",
            vertices.size, faces.size, materials.size
        )
        for (face in faces) {
            val mat = materials[face.materialName] ?: continue
            for (i in face.vertexIndices.indices) {
                val vi = face.vertexIndices[i]
                if (vi < 0 || vi >= vertices.size) continue
                val v = vertices[vi]
                val u = face.uvs?.let { if (i < it.size) it[i][0] else 0.0f } ?: 0.0f
                val vCoord = face.uvs?.let { if (i < it.size) it[i][1] else 0.0f } ?: 0.0f
                consumer.addVertex(poseStack.last().pose(), v.x, v.y, v.z)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                    .setUv(u, vCoord)
                    .setUv2((packedLight and 0xFFFF), (packedLight shr 16))
                    .setNormal(0.0f, 1.0f, 0.0f)
            }
        }
    }

    private fun executeScript(poseStack: PoseStack, pass: Int) {
        try {
            val engine = scriptEngine
            if (engine is javax.script.ScriptEngine) {
                engine.put("poseStack", poseStack)
                engine.put("pass", pass)
                engine.eval("if (typeof render === 'function') render(poseStack, pass);")
            }
        } catch (_: Exception) {
            // Script execution failed, continue without script
        }
    }

    fun setScriptEngine(engine: Any?) {
        scriptEngine = engine
    }
}
