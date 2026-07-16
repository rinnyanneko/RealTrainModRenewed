// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientRenderProfiler
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

open class InstalledObjectBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context,
) : BlockEntityRenderer<InstalledObjectBlockEntity, LegacyBlockEntityRenderState<InstalledObjectBlockEntity>> {

    override fun createRenderState(): LegacyBlockEntityRenderState<InstalledObjectBlockEntity> =
        LegacyBlockEntityRenderState()

    override fun extractRenderState(
        blockEntity: InstalledObjectBlockEntity,
        state: LegacyBlockEntityRenderState<InstalledObjectBlockEntity>,
        partialTick: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?,
    ) {
        super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress)
        state.blockEntity = blockEntity
        state.partialTick = partialTick
    }

    override fun submit(
        state: LegacyBlockEntityRenderState<InstalledObjectBlockEntity>,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        val blockEntity = state.blockEntity ?: return
        val buffer = Minecraft.getInstance().renderBuffers().bufferSource()
        renderLegacy(blockEntity, state.partialTick, poseStack, buffer, state.lightCoords, OverlayTexture.NO_OVERLAY)
        buffer.endBatch()
    }

    private fun renderLegacy(
        blockEntity: InstalledObjectBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val profilerStart = ClientRenderProfiler.begin()
        val definition = InstalledObjectRegistry.getById(blockEntity.definitionId)
        if (definition == null) {
            ClientRenderProfiler.endInstalledObject(profilerStart)
            return
        }
        val failedUntil = FAILED_RENDER_UNTIL_NANOS[definition.id]
        if (failedUntil != null) {
            if (System.nanoTime() < failedUntil) {
                ClientRenderProfiler.endInstalledObject(profilerStart)
                return
            }
            FAILED_RENDER_UNTIL_NANOS.remove(definition.id, failedUntil)
        }
        val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position()
        val center = blockEntity.renderCenter
        val cameraDistanceSq = cameraPos.distanceToSqr(center)
        if (blockEntity.category == InstalledObjectCategory.WIRE) {
            if (blockEntity.wireStart != null && blockEntity.wireEnd != null) {
                renderWire(blockEntity, definition, poseStack, buffer, cameraDistanceSq, cameraPos, packedLight, packedOverlay)
            }
            ClientRenderProfiler.endInstalledObject(profilerStart)
            return
        }
        if (definition.modelFile.isNotBlank()) {
            val model = MqoModelLoader.loadModelFromPack(
                definition.packName,
                definition.modelFile,
                definition.textureOverrides,
                definition.scriptPath,
                definition.isSmoothing(),
            )
            if (model != null) {
                if (blockEntity.category == InstalledObjectCategory.TICKET_GATE &&
                    TICKET_GATE_LOGGED.add(definition.id)
                ) {
                    RealTrainModRenewed.LOGGER.debug(
                        "[RTM-DBG] ticketGate id={} barMove={} groups={}",
                        definition.id,
                        blockEntity.barMoveCount,
                        model.allNormalizedGroupNames,
                    )
                }
                var pushed = false
                try {
                    val compatibilityHeavy = shouldUseCompatibilityRendering(definition, model)
                    val customCrossingGateRendering = shouldUseCustomCrossingGateRendering(blockEntity, definition)
                    val farThreshold = if (compatibilityHeavy) 56.0 else 80.0
                    val veryFarThreshold = if (compatibilityHeavy) 96.0 else 140.0
                    val translucentThreshold = if (compatibilityHeavy) 44.0 else 72.0
                    val far = cameraDistanceSq > farThreshold * farThreshold
                    val veryFar = cameraDistanceSq > veryFarThreshold * veryFarThreshold
                    poseStack.pushPose()
                    pushed = true
                    poseStack.translate(0.5, 0.0, 0.5)
                    val renderOffset = blockEntity.renderOffset
                    poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z)
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - blockEntity.yaw))
                    if (blockEntity.mountPitch != 0.0f) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(blockEntity.mountPitch))
                    }
                    poseStack.translate(definition.modelOffset.x, definition.modelOffset.y, definition.modelOffset.z)
                    poseStack.scale(definition.modelScale, definition.modelScale, definition.modelScale)
                    val filter = MqoModelLoader.GroupPredicate { groupName ->
                        shouldRenderDefinedObjectGroup(groupName, definition) &&
                            (!(far || compatibilityHeavy || customCrossingGateRendering) ||
                                shouldRenderInstalledObjectGroup(groupName, blockEntity, definition, cameraDistanceSq, compatibilityHeavy))
                    }
                    val ticketGateRendering = blockEntity.category == InstalledObjectCategory.TICKET_GATE
                    val transformModel = model
                    val transform: MqoModelLoader.GroupTransform? = if (customCrossingGateRendering) {
                        MqoModelLoader.GroupTransform { stack, groupName -> applyCrossingGateTransform(stack, blockEntity, groupName) }
                    } else if (ticketGateRendering) {
                        MqoModelLoader.GroupTransform { stack, groupName -> applyTicketGateTransform(stack, blockEntity, transformModel, groupName) }
                    } else {
                        null
                    }
                    if (!customCrossingGateRendering &&
                        !ticketGateRendering &&
                        !veryFar &&
                        !compatibilityHeavy &&
                        definition.scriptPath.isNotBlank()
                    ) {
                        MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, blockEntity)
                    } else {
                        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, filter, transform, blockEntity)
                        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
                            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, filter, transform, blockEntity)
                        }
                    }
                    if (!veryFar && shouldRenderSupplementalActiveLights(blockEntity, definition, customCrossingGateRendering)) {
                        renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay)
                    }
                    poseStack.popPose()
                    pushed = false
                } catch (throwable: Throwable) {
                    if (pushed) {
                        try {
                            poseStack.popPose()
                        } catch (ignored: Throwable) {
                        }
                    }
                    FAILED_RENDER_UNTIL_NANOS[definition.id] = System.nanoTime() + 5_000_000_000L
                    RealTrainModRenewed.LOGGER.warn(
                        "Skipping installed object render for {} for 5 seconds after renderer failure.",
                        definition.id,
                        throwable,
                    )
                }
                ClientRenderProfiler.endInstalledObject(profilerStart)
                return
            }
        }
        if (blockEntity.category == InstalledObjectCategory.SIGNBOARD) {
            renderSignboard(blockEntity, definition, poseStack, buffer, packedLight, packedOverlay)
        }
        ClientRenderProfiler.endInstalledObject(profilerStart)
    }

    private fun renderWire(
        blockEntity: InstalledObjectBlockEntity,
        definition: InstalledObjectDefinition,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        cameraDistanceSq: Double,
        cameraPos: Vec3,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val start = blockEntity.wireStart
        val end = blockEntity.wireEnd
        if (start == null || end == null) {
            return
        }
        val origin = Vec3.atLowerCornerOf(blockEntity.blockPos)
        val fromWorld = resolveWireAttachPoint(blockEntity.level, start)
        val toWorld = resolveWireAttachPoint(blockEntity.level, end)
        val from = fromWorld.subtract(origin)
        val to = toWorld.subtract(origin)

        val wireScript = definition.scriptPath
        val hasScript = wireScript.isNotBlank()
        val normalizedScript = if (hasScript) wireScript.lowercase(Locale.ROOT).replace('\\', '/') else ""
        val hasRenderableModel = hasRenderableWireModel(definition)
        val model = if (hasRenderableModel) {
            MqoModelLoader.loadModelFromPack(
                definition.packName,
                definition.modelFile,
                definition.textureOverrides,
                definition.scriptPath,
                definition.isSmoothing(),
            )
        } else {
            null
        }

        if (model != null && renderKnownScriptWireModel(blockEntity, definition, model, from, to, normalizedScript, poseStack, buffer, packedLight, packedOverlay)) {
            return
        }

        if (hasScript || model == null) {
            renderBasicWireCable(from, to, packedLight, poseStack, buffer)
            return
        }

        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 1.0e-4) {
            return
        }
        val yaw = Math.toDegrees(Math.atan2(delta.x, delta.z)).toFloat()
        val xz = sqrt(delta.x * delta.x + delta.z * delta.z)
        val pitch = Math.toDegrees(Math.atan2(delta.y, xz)).toFloat()
        val sectionLength = definition.sectionLength
        var split = max(1, floor(length / sectionLength).toInt())
        split = min(split, 256)
        val scaleY = ((length / split.toDouble()) / sectionLength).toFloat()

        poseStack.pushPose()
        try {
            poseStack.translate(from.x, from.y, from.z)
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f))
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch - 90.0f))
            poseStack.scale(1.0f, scaleY, 1.0f)
            val hasTranslucent = model.hasTranslucentBatches()
            for (i in 0 until split) {
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, null, null, blockEntity)
                if (hasTranslucent) {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, null, null, blockEntity)
                }
                poseStack.translate(0.0, sectionLength.toDouble(), 0.0)
            }
        } finally {
            poseStack.popPose()
        }
    }

    private fun renderKnownScriptWireModel(
        blockEntity: InstalledObjectBlockEntity,
        definition: InstalledObjectDefinition,
        model: MqoModelLoader.MqoModel,
        from: Vec3,
        to: Vec3,
        script: String?,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ): Boolean {
        if (script.isNullOrBlank()) {
            return false
        }
        if (script.endsWith("wire51/renderbeam1.js")) {
            renderWire51Beam(blockEntity, definition, model, from, to, poseStack, buffer, packedLight, packedOverlay)
            return true
        }
        if (script.endsWith("wire51/renderwire.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 10.0, "obj1", poseStack, buffer, packedLight, packedOverlay)
            return true
        }
        if (script.endsWith("wire51/renderbracket.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 3.0, "obj1", poseStack, buffer, packedLight, packedOverlay)
            return true
        }
        if (script.endsWith("wire51/renderbracketd.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 4.0, "obj1", poseStack, buffer, packedLight, packedOverlay)
            return true
        }
        return false
    }

    private fun renderWire51Beam(
        blockEntity: InstalledObjectBlockEntity,
        definition: InstalledObjectDefinition,
        model: MqoModelLoader.MqoModel,
        from: Vec3,
        to: Vec3,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 1.0e-4) {
            return
        }

        var maxPos = max(1, floor(length / 2.0).toInt() * 2)
        maxPos = min(maxPos, 256)
        val move = length / maxPos.toDouble()
        val scale = move.toFloat()
        val halfMaxPos = maxPos / 2

        poseStack.pushPose()
        try {
            applyZWireOrientation(poseStack, from, delta)
            poseStack.scale(definition.modelScale, definition.modelScale, definition.modelScale)
            for (i in 0 until maxPos) {
                val group: String
                var offsetZ = 0.0
                if (i == 0) {
                    group = "BeamR1"
                    offsetZ = 2.0
                } else if (i < halfMaxPos) {
                    group = "BeamR2"
                    offsetZ = 1.0
                } else if (i < maxPos - 1) {
                    group = "BeamL2"
                } else {
                    group = "BeamL1"
                    offsetZ = -1.0
                }

                poseStack.pushPose()
                poseStack.translate(0.0, 0.0, move * i + offsetZ)
                poseStack.scale(1.0f, 1.0f, scale)
                renderWireModelGroup(model, poseStack, buffer, packedLight, packedOverlay, blockEntity, group)
                poseStack.popPose()
            }
        } finally {
            poseStack.popPose()
        }
    }

    private fun renderScaledZWireModel(
        blockEntity: InstalledObjectBlockEntity,
        definition: InstalledObjectDefinition,
        model: MqoModelLoader.MqoModel,
        from: Vec3,
        to: Vec3,
        baseLength: Double,
        groupName: String,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 1.0e-4 || baseLength <= 0.0) {
            return
        }

        val rate = (length / baseLength).toFloat()
        poseStack.pushPose()
        try {
            applyZWireOrientation(poseStack, from, delta)
            val modelScale = definition.modelScale
            poseStack.scale(modelScale, modelScale, modelScale)
            poseStack.scale(1.0f, 1.0f, rate)
            renderWireModelGroup(model, poseStack, buffer, packedLight, packedOverlay, blockEntity, groupName)
        } finally {
            poseStack.popPose()
        }
    }

    private fun renderBasicWireCable(from: Vec3, to: Vec3, packedLight: Int, poseStack: PoseStack, buffer: MultiBufferSource) {
        val x = to.x - from.x
        val y = to.y - from.y
        val z = to.z - from.z
        var horizontal = sqrt(x * x + z * z)
        if (horizontal < 1.0e-6) horizontal = 1.0e-6
        val x1 = x / horizontal
        val z1 = z / horizontal
        val split = 16
        val width = 0.025
        val consumer = buffer.getBuffer(RenderTypes.leash())
        val matrix = poseStack.last().pose()
        val xr = 26
        val xg = 26
        val xb = 26
        val yr = 6
        val yg = 6
        val yb = 6
        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f
        for (j in 0..split) {
            val ft = j / split.toDouble()
            val f2 = (j - 8.0) / split
            val fh = (f2 * f2 - 0.25) * 1.5
            val px = from.x + x * ft
            val py = from.y + y * ft + fh
            val pz = from.z + z * ft
            consumer.addVertex(matrix, (px - width * z1).toFloat(), py.toFloat(), (pz + width * x1).toFloat())
                .setColor(xr, xg, xb, 255).setLight(packedLight)
            lastX = (px + width * z1).toFloat()
            lastY = py.toFloat()
            lastZ = (pz - width * x1).toFloat()
            consumer.addVertex(matrix, lastX, lastY, lastZ).setColor(xr, xg, xb, 255).setLight(packedLight)
        }
        val fh0 = (((0 - 8.0) / split) * ((0 - 8.0) / split) - 0.25) * 1.5
        val firstYx = from.x.toFloat()
        val firstYy = (from.y + fh0 + width).toFloat()
        val firstYz = from.z.toFloat()
        consumer.addVertex(matrix, lastX, lastY, lastZ).setColor(yr, yg, yb, 255).setLight(packedLight)
        consumer.addVertex(matrix, firstYx, firstYy, firstYz).setColor(yr, yg, yb, 255).setLight(packedLight)
        for (j in 0..split) {
            val ft = j / split.toDouble()
            val f2 = (j - 8.0) / split
            val fh = (f2 * f2 - 0.25) * 1.5
            val px = from.x + x * ft
            val py = from.y + y * ft + fh
            val pz = from.z + z * ft
            consumer.addVertex(matrix, px.toFloat(), (py + width).toFloat(), pz.toFloat()).setColor(yr, yg, yb, 255).setLight(packedLight)
            consumer.addVertex(matrix, px.toFloat(), (py - width).toFloat(), pz.toFloat()).setColor(yr, yg, yb, 255).setLight(packedLight)
        }
    }

    private fun renderSignboard(
        blockEntity: InstalledObjectBlockEntity,
        definition: InstalledObjectDefinition,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val signTexture = definition.signTexture
        if (signTexture.isBlank()) {
            renderSignboardOutline(definition, poseStack, buffer)
            return
        }
        val texture = MqoModelLoader.resolvePackTexture(definition.packName, signTexture)
        if (texture == null) {
            renderSignboardOutline(definition, poseStack, buffer)
            return
        }

        val halfWidth = definition.width * 0.5
        val height = definition.height.toDouble()
        val halfDepth = max(0.02, definition.depth * 0.5)
        val frame = definition.signFrame
        val backTexture = definition.backTexture
        val vMax = if (frame > 1) 1.0f / frame else 1.0f
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        val renderOffset = blockEntity.renderOffset
        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z)
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - blockEntity.yaw))
        val consumer = buffer.getBuffer(RenderTypes.entityCutout(texture))
        val pose = poseStack.last()

        addSignVertex(consumer, pose, -halfWidth, height, -halfDepth, 0.0f, 0.0f, packedOverlay, packedLight, -1.0f)
        addSignVertex(consumer, pose, -halfWidth, 0.0, -halfDepth, 0.0f, vMax, packedOverlay, packedLight, -1.0f)
        addSignVertex(consumer, pose, halfWidth, 0.0, -halfDepth, 1.0f, vMax, packedOverlay, packedLight, -1.0f)
        addSignVertex(consumer, pose, halfWidth, height, -halfDepth, 1.0f, 0.0f, packedOverlay, packedLight, -1.0f)

        if (backTexture != 0) {
            addSignVertex(consumer, pose, halfWidth, height, halfDepth, 0.0f, 0.0f, packedOverlay, packedLight, 1.0f)
            addSignVertex(consumer, pose, halfWidth, 0.0, halfDepth, 0.0f, vMax, packedOverlay, packedLight, 1.0f)
            addSignVertex(consumer, pose, -halfWidth, 0.0, halfDepth, 1.0f, vMax, packedOverlay, packedLight, 1.0f)
            addSignVertex(consumer, pose, -halfWidth, height, halfDepth, 1.0f, 0.0f, packedOverlay, packedLight, 1.0f)
        }
        poseStack.popPose()
    }

    private fun renderSignboardOutline(definition: InstalledObjectDefinition, poseStack: PoseStack, buffer: MultiBufferSource) {
        val consumer = buffer.getBuffer(RenderTypes.lines())
        val halfWidth = definition.width * 0.5
        val height = definition.height.toDouble()
        val halfDepth = max(0.02, definition.depth * 0.5)
        renderLineBox(
            poseStack,
            consumer,
            0.5 - halfWidth,
            0.0,
            0.5 - halfDepth,
            0.5 + halfWidth,
            height,
            0.5 + halfDepth,
            1.0f,
            0.95f,
            0.6f,
            0.9f,
        )
    }

    private fun renderActiveLights(
        blockEntity: InstalledObjectBlockEntity,
        definition: InstalledObjectDefinition,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedOverlay: Int,
    ) {
        val groups = resolveActiveLightGroups(blockEntity, definition)
        if (groups.isEmpty()) {
            return
        }
        val emissiveModel = MqoModelLoader.loadModelFromPack(
            definition.packName,
            definition.modelFile,
            definition.textureOverrides,
            "",
            definition.isSmoothing(),
        ) ?: return

        for (group in groups) {
            val color = signalColorForGroup(group)
            MqoModelLoader.renderModelColorOverlay(
                emissiveModel,
                poseStack,
                buffer,
                packedOverlay,
                MqoModelLoader.GroupPredicate { candidate -> groupMatches(candidate, group) },
                color[0],
                color[1],
                color[2],
                color[3],
            )
        }
    }

    override fun getRenderBoundingBox(blockEntity: InstalledObjectBlockEntity): AABB {
        val wireStart = blockEntity.wireStart
        val wireEnd = blockEntity.wireEnd
        if (blockEntity.category == InstalledObjectCategory.WIRE && wireStart != null && wireEnd != null) {
            val a = Vec3.atCenterOf(wireStart)
            val b = Vec3.atCenterOf(wireEnd)
            return AABB(a, b).inflate(2.0)
        }
        return AABB(blockEntity.blockPos).inflate(4.0)
    }

    override fun shouldRenderOffScreen(): Boolean = true

    override fun getViewDistance(): Int = 192

    private data class CrossingTransform(val pivotX: Double, val pivotY: Double, val pivotZ: Double, val degrees: Double)

    private companion object {
        private val GREEN_GROUPS = setOf("light1", "light2")
        private val YELLOW_GROUPS = setOf("light3", "light5")
        private val RED_GROUPS = setOf("light4")
        private val CROSSING_SCRIPT_ONLY_GROUPS = setOf("light_l", "light_r")
        private val CROSSING_LIGHT_LEFT = listOf("light_l", "lightl", "light-left", "lightleft", "lighta", "light_a")
        private val CROSSING_LIGHT_RIGHT = listOf("light_r", "lightr", "light-right", "lightright", "lightb", "light_b")
        private val CROSSING_LIGHT_LEFT_LEGACY = listOf("light1")
        private val CROSSING_LIGHT_RIGHT_LEGACY = listOf("light2")
        private val CROSSING_LIGHT_COMMON_LEGACY = listOf("light3")
        private val FAILED_RENDER_UNTIL_NANOS: MutableMap<String, Long> = ConcurrentHashMap()
        private val TICKET_GATE_LOGGED = ConcurrentHashMap.newKeySet<String>()

        private fun hasRenderableWireModel(definition: InstalledObjectDefinition?): Boolean {
            if (definition == null) {
                return false
            }
            val modelFile = definition.modelFile
            if (modelFile.isBlank()) {
                return false
            }
            val normalized = modelFile.lowercase(Locale.ROOT).replace('\\', '/')
            return !normalized.endsWith("model_none.mqo")
        }

        private fun resolveWireAttachPoint(level: Level?, pos: BlockPos): Vec3 {
            val endpoint = level?.getBlockEntity(pos) as? InstalledObjectBlockEntity
            if (endpoint != null) {
                val endpointDefinition = InstalledObjectRegistry.getById(endpoint.definitionId)
                if (endpointDefinition != null) {
                    val wirePos = endpointDefinition.wireAttachPos
                    var attachY = wirePos.y
                    val attachModel = MqoModelLoader.loadModelFromPack(
                        endpointDefinition.packName,
                        endpointDefinition.modelFile,
                        endpointDefinition.textureOverrides,
                        endpointDefinition.scriptPath,
                        endpointDefinition.isSmoothing(),
                    )
                    val modelTop = modelTopY(attachModel)
                    if (!modelTop.isNaN()) {
                        attachY = modelTop * endpointDefinition.modelScale
                    }
                    val local = Vec3(wirePos.x, attachY, wirePos.z)
                    val tilted = rotateX(local, endpoint.mountPitch.toDouble())
                    val rotated = rotateY(tilted, 180.0 - endpoint.yaw)
                    return Vec3.atLowerCornerOf(pos)
                        .add(0.5, 0.0, 0.5)
                        .add(endpoint.renderOffset)
                        .add(rotated)
                }
            }
            return Vec3.atCenterOf(pos)
        }

        private fun rotateY(vec: Vec3?, degrees: Double): Vec3 {
            if (vec == null || vec == Vec3.ZERO) {
                return Vec3.ZERO
            }
            val radians = Math.toRadians(degrees)
            val cos = cos(radians)
            val sin = sin(radians)
            return Vec3(vec.x * cos + vec.z * sin, vec.y, vec.z * cos - vec.x * sin)
        }

        private fun rotateX(vec: Vec3?, degrees: Double): Vec3 {
            if (degrees == 0.0 || vec == null || vec == Vec3.ZERO) {
                return vec ?: Vec3.ZERO
            }
            val radians = Math.toRadians(degrees)
            val cos = cos(radians)
            val sin = sin(radians)
            return Vec3(vec.x, vec.y * cos - vec.z * sin, vec.y * sin + vec.z * cos)
        }

        private fun applyZWireOrientation(poseStack: PoseStack, from: Vec3, delta: Vec3) {
            val xz = sqrt(delta.x * delta.x + delta.z * delta.z)
            val yaw = Math.toDegrees(Math.atan2(delta.x, delta.z)).toFloat()
            val pitch = Math.toDegrees(Math.atan2(delta.y, xz)).toFloat()
            poseStack.translate(from.x, from.y, from.z)
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw))
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch))
        }

        private fun renderWireModelGroup(
            model: MqoModelLoader.MqoModel,
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int,
            blockEntity: InstalledObjectBlockEntity,
            groupName: String?,
        ) {
            val filter = if (groupName.isNullOrBlank()) {
                null
            } else {
                MqoModelLoader.GroupPredicate { candidate -> groupMatches(candidate, groupName) }
            }
            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, filter, null, blockEntity)
            if (model.hasTranslucentBatches()) {
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, filter, null, blockEntity)
            }
        }

        private fun shouldRenderInstalledObjectGroup(
            groupName: String?,
            blockEntity: InstalledObjectBlockEntity,
            definition: InstalledObjectDefinition,
            cameraDistanceSq: Double,
            compatibilityHeavy: Boolean,
        ): Boolean {
            if (groupName.isNullOrBlank()) {
                return true
            }
            val normalized = groupName.lowercase(Locale.ROOT)
            if (usesBuiltinCrossingGateLayout(definition) && CROSSING_SCRIPT_ONLY_GROUPS.contains(normalized)) {
                return false
            }
            if (cameraDistanceSq > 140.0 * 140.0) {
                if (normalized.contains("detail") ||
                    normalized.contains("under") ||
                    normalized.contains("inside") ||
                    normalized.contains("step") ||
                    normalized.contains("ladder") ||
                    normalized.contains("handle") ||
                    normalized.contains("lever")
                ) {
                    return false
                }
            }
            if (cameraDistanceSq > 80.0 * 80.0) {
                if (normalized.contains("glass") ||
                    normalized.contains("alpha") ||
                    normalized.contains("screen") ||
                    normalized.contains("panel")
                ) {
                    return false
                }
            }
            if (compatibilityHeavy) {
                if (normalized.contains("glass") ||
                    normalized.contains("alpha") ||
                    normalized.contains("window") ||
                    normalized.contains("screen") ||
                    normalized.contains("display")
                ) {
                    return false
                }
                if (cameraDistanceSq > 56.0 * 56.0 &&
                    (normalized.contains("detail") ||
                        normalized.contains("cover") ||
                        normalized.contains("frame") ||
                        normalized.contains("inside") ||
                        normalized.contains("back"))
                ) {
                    return false
                }
            }
            return true
        }

        private fun shouldUseCustomCrossingGateRendering(
            blockEntity: InstalledObjectBlockEntity?,
            definition: InstalledObjectDefinition?,
        ): Boolean =
            blockEntity != null &&
                definition != null &&
                blockEntity.category == InstalledObjectCategory.CROSSING &&
                definition.scriptPath.isNotBlank() &&
                usesBuiltinCrossingGateLayout(definition)

        private fun applyCrossingGateTransform(poseStack: PoseStack, blockEntity: InstalledObjectBlockEntity?, groupName: String?) {
            if (blockEntity == null || groupName == null) {
                return
            }
            val normalized = groupName.lowercase(Locale.ROOT)
            if (normalized != "bar0" && normalized != "bar1" && normalized != "bar" && normalized != "bar2") {
                return
            }
            val transform = resolveCrossingTransform(blockEntity, normalized) ?: return
            val move = ((blockEntity.barMoveCount / 90.0f) * transform.degrees).toFloat()
            poseStack.translate(transform.pivotX, transform.pivotY, transform.pivotZ)
            poseStack.mulPose(Axis.ZP.rotationDegrees(move))
            poseStack.translate(-transform.pivotX, -transform.pivotY, -transform.pivotZ)
        }

        private fun applyTicketGateTransform(
            poseStack: PoseStack,
            blockEntity: InstalledObjectBlockEntity?,
            model: MqoModelLoader.MqoModel?,
            groupName: String?,
        ) {
            if (blockEntity == null || model == null || groupName == null) {
                return
            }
            val normalized = groupName.lowercase(Locale.ROOT)
            if (!normalized.contains("door")) {
                return
            }
            val openness = Mth.clamp(blockEntity.barMoveCount / 90.0f, 0.0f, 1.0f)
            if (openness <= 0.001f) {
                return
            }
            val bounds = groupBounds(model, groupName) ?: return
            val left = normalized.endsWith("l") || normalized.contains("doorl") || normalized.contains("door_l")
            val hingeX = if (left) bounds[0].toDouble() else bounds[3].toDouble()
            val hingeZ = bounds[2].toDouble()
            val angle = openness * if (left) 90.0f else -90.0f
            poseStack.translate(hingeX, 0.0, hingeZ)
            poseStack.mulPose(Axis.YP.rotationDegrees(angle))
            poseStack.translate(-hingeX, 0.0, -hingeZ)
        }

        private fun modelTopY(model: MqoModelLoader.MqoModel?): Double {
            if (model == null) {
                return Double.NaN
            }
            val groups = model.allNormalizedGroupNames
            if (groups.isEmpty()) {
                return Double.NaN
            }
            val quads = model.getGroupQuadCorners(groups)
            if (quads.isEmpty()) {
                return Double.NaN
            }
            var maxY = -Double.MAX_VALUE
            for (quad in quads) {
                if (quad == null) {
                    continue
                }
                for (corner in 0 until 4) {
                    maxY = max(maxY, quad[corner * 3 + 1].toDouble())
                }
            }
            return if (maxY == -Double.MAX_VALUE) Double.NaN else maxY
        }

        private fun groupBounds(model: MqoModelLoader.MqoModel, groupName: String): FloatArray? {
            val quads = model.getGroupQuadCorners(mutableSetOf(groupName))
            if (quads.isEmpty()) {
                return null
            }
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE
            for (quad in quads) {
                if (quad == null) {
                    continue
                }
                for (corner in 0 until 4) {
                    val x = quad[corner * 3]
                    val y = quad[corner * 3 + 1]
                    val z = quad[corner * 3 + 2]
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                    minZ = min(minZ, z)
                    maxZ = max(maxZ, z)
                }
            }
            return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        }

        private fun resolveCrossingTransform(blockEntity: InstalledObjectBlockEntity, groupName: String): CrossingTransform? {
            val scriptPath = getCrossingScriptPath(InstalledObjectRegistry.getById(blockEntity.definitionId))
            val turnRight = blockEntity.modelName.endsWith("R")
            if (scriptPath.contains("hi03rendercrossinggate")) {
                val degrees = if (turnRight) 85.0 else -85.0
                if (groupName == "bar2") {
                    return CrossingTransform(-0.5303, 6.0287, 0.0, -degrees)
                }
                return CrossingTransform(0.0, 0.9056, 0.0, degrees)
            }
            if (scriptPath.contains("masacrossinggate")) {
                val degrees = if (turnRight) 90.0 else -90.0
                return CrossingTransform(0.02, 0.92, 0.0, degrees)
            }
            if (groupName == "bar0" || groupName == "bar1") {
                val degrees = if (turnRight) 90.0 else -90.0
                return CrossingTransform(0.0, 0.5337, -0.24, degrees)
            }
            return null
        }

        private fun isSupportedCustomCrossingScript(scriptPath: String?): Boolean {
            val normalized = scriptPath?.lowercase(Locale.ROOT) ?: ""
            return normalized.contains("rendercrossinggate") || normalized.contains("crossinggate")
        }

        private fun usesBuiltinCrossingGateLayout(definition: InstalledObjectDefinition?): Boolean {
            val scriptPath = getCrossingScriptPath(definition)
            return scriptPath.contains("rendercrossinggate01")
        }

        private fun getCrossingScriptPath(definition: InstalledObjectDefinition?): String =
            definition?.scriptPath?.lowercase(Locale.ROOT) ?: ""

        private fun shouldUseCompatibilityRendering(definition: InstalledObjectDefinition?, model: MqoModelLoader.MqoModel?): Boolean {
            if (definition == null || model == null) {
                return false
            }
            val hasScript = definition.scriptPath.isNotBlank()
            return model.totalVertexCount >= 12_000 ||
                model.batchCount >= 96 ||
                model.translucentBatchCount >= 16 ||
                (hasScript && model.batchCount >= 64)
        }

        private fun shouldRenderDefinedObjectGroup(groupName: String?, definition: InstalledObjectDefinition?): Boolean {
            if (definition == null || definition.renderObjects.isEmpty()) {
                return true
            }
            for (expected in definition.renderObjects) {
                if (groupMatches(groupName, expected)) {
                    return true
                }
            }
            return false
        }

        private fun renderLineBox(
            poseStack: PoseStack,
            consumer: VertexConsumer,
            minX: Double,
            minY: Double,
            minZ: Double,
            maxX: Double,
            maxY: Double,
            maxZ: Double,
            red: Float,
            green: Float,
            blue: Float,
            alpha: Float,
        ) {
            val pose = poseStack.last()
            line(consumer, pose, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha)
            line(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha)
            line(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha)
            line(consumer, pose, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha)
            line(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha)
            line(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha)
            line(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha)
            line(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha)
            line(consumer, pose, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha)
            line(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha)
            line(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha)
            line(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha)
        }

        private fun line(
            consumer: VertexConsumer,
            pose: PoseStack.Pose,
            x1: Double,
            y1: Double,
            z1: Double,
            x2: Double,
            y2: Double,
            z2: Double,
            red: Float,
            green: Float,
            blue: Float,
            alpha: Float,
        ) {
            consumer.addVertex(pose.pose(), x1.toFloat(), y1.toFloat(), z1.toFloat()).setColor(red, green, blue, alpha).setLineWidth(1.0f).setNormal(0.0f, 1.0f, 0.0f)
            consumer.addVertex(pose.pose(), x2.toFloat(), y2.toFloat(), z2.toFloat()).setColor(red, green, blue, alpha).setLineWidth(1.0f).setNormal(0.0f, 1.0f, 0.0f)
        }

        private fun addSignVertex(
            consumer: VertexConsumer,
            pose: PoseStack.Pose,
            x: Double,
            y: Double,
            z: Double,
            u: Float,
            v: Float,
            packedOverlay: Int,
            packedLight: Int,
            normalZ: Float,
        ) {
            consumer.addVertex(pose.pose(), x.toFloat(), y.toFloat(), z.toFloat())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(0.0f, 0.0f, normalZ)
        }

        private fun shouldRenderSupplementalActiveLights(
            blockEntity: InstalledObjectBlockEntity?,
            definition: InstalledObjectDefinition?,
            customCrossingGateRendering: Boolean,
        ): Boolean {
            if (blockEntity == null || definition == null) {
                return false
            }
            if (customCrossingGateRendering) {
                return true
            }
            return true
        }

        private fun resolveActiveLightGroups(blockEntity: InstalledObjectBlockEntity?, definition: InstalledObjectDefinition?): List<String> {
            if (blockEntity == null || definition == null) {
                return emptyList()
            }
            if (blockEntity.isSignal) {
                var groups = definition.signalLightGroups[blockEntity.getLegacySignalState()]
                if (groups == null || groups.isEmpty()) {
                    groups = fallbackSignalGroups(blockEntity.getLegacySignalState())
                }
                return groups
            }
            if (blockEntity.category == InstalledObjectCategory.CROSSING && blockEntity.isPowered) {
                val state = Math.floorMod(blockEntity.getLightCount(), 2)
                val crossingDefinition = InstalledObjectRegistry.getById(blockEntity.definitionId)
                val scriptPath = getCrossingScriptPath(crossingDefinition)
                if (scriptPath.contains("rendercrossinggate01")) {
                    return if (state == 0) CROSSING_LIGHT_RIGHT else CROSSING_LIGHT_LEFT
                }
                val groups = ArrayList<String>()
                groups.addAll(if (state == 0) CROSSING_LIGHT_RIGHT_LEGACY else CROSSING_LIGHT_LEFT_LEGACY)
                groups.addAll(CROSSING_LIGHT_COMMON_LEGACY)
                groups.addAll(if (state == 0) CROSSING_LIGHT_RIGHT else CROSSING_LIGHT_LEFT)
                return groups
            }
            if (blockEntity.category == InstalledObjectCategory.LIGHT && blockEntity.isPowered) {
                val lit = ArrayList<String>()
                for (group in definition.signalLightGroups.values) {
                    lit.addAll(group)
                }
                return lit
            }
            return emptyList()
        }

        private fun groupMatches(candidate: String?, expected: String?): Boolean {
            if (candidate == null || expected == null) {
                return false
            }
            val normalizedCandidate = candidate.lowercase(Locale.ROOT)
            val normalizedExpected = expected.lowercase(Locale.ROOT)
            if (normalizedCandidate == normalizedExpected) {
                return true
            }
            val compactCandidate = normalizedCandidate.replace("_", "").replace("-", "")
            val compactExpected = normalizedExpected.replace("_", "").replace("-", "")
            return compactCandidate == compactExpected
        }

        private fun fallbackSignalGroups(legacyState: Int): List<String> =
            when (legacyState) {
                1 -> listOf("light4")
                2 -> listOf("light4", "light3")
                3 -> listOf("light3")
                4 -> listOf("light3", "light5")
                5 -> listOf("light2")
                6 -> listOf("light1", "light5")
                7 -> listOf("light1", "light2")
                else -> emptyList()
            }

        private fun signalColorForGroup(group: String?): IntArray {
            val lower = group?.lowercase(Locale.ROOT) ?: ""
            if (CROSSING_LIGHT_LEFT.contains(lower) ||
                CROSSING_LIGHT_RIGHT.contains(lower) ||
                CROSSING_LIGHT_LEFT_LEGACY.contains(lower) ||
                CROSSING_LIGHT_RIGHT_LEGACY.contains(lower) ||
                CROSSING_LIGHT_COMMON_LEGACY.contains(lower)
            ) {
                return intArrayOf(255, 72, 48, 220)
            }
            if (RED_GROUPS.contains(lower)) {
                return intArrayOf(255, 56, 32, 218)
            }
            if (YELLOW_GROUPS.contains(lower)) {
                return intArrayOf(255, 210, 64, 206)
            }
            if (GREEN_GROUPS.contains(lower)) {
                return intArrayOf(64, 255, 120, 198)
            }
            return intArrayOf(255, 255, 255, 180)
        }
    }
}
