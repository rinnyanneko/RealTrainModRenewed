// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.ClientRenderProfiler
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.Collections
import java.util.HashSet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

open class TrainEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<TrainEntity, LegacyEntityRenderState<TrainEntity>>(context) {

    init {
        shadowRadius = 0.0f
    }

    open fun getTextureLocation(entity: TrainEntity): Identifier = Identifier.withDefaultNamespace("missingno")

    override fun createRenderState(): LegacyEntityRenderState<TrainEntity> = LegacyEntityRenderState()

    override fun extractRenderState(entity: TrainEntity, state: LegacyEntityRenderState<TrainEntity>, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        state.entity = entity
        state.entityYaw = entity.yRot
    }

    override fun shouldRender(entity: TrainEntity, frustum: Frustum, camX: Double, camY: Double, camZ: Double): Boolean {
        val halfLength = max(3.0, entity.trainDistance + 3.0)
        val renderBounds = AABB(
            entity.x - halfLength,
            entity.y - 1.5,
            entity.z - halfLength,
            entity.x + halfLength,
            entity.y + 5.0,
            entity.z + halfLength,
        )
        return frustum.isVisible(renderBounds)
    }

    override fun submit(
        state: LegacyEntityRenderState<TrainEntity>,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        val entity = state.entity ?: return
        val buffer = Minecraft.getInstance().renderBuffers().bufferSource()
        renderLegacy(entity, state.entityYaw, state.partialTick, poseStack, buffer, state.lightCoords)
        super.submit(state, poseStack, submitNodeCollector, camera)
    }

    private fun renderLegacy(
        entity: TrainEntity,
        @Suppress("UNUSED_PARAMETER") entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        val profilerStart = ClientRenderProfiler.begin()
        val definition = VehicleRegistry.getById(entity.vehicleId)
        if (definition == null) {
            RealTrainModRenewed.LOGGER.error("Vehicle definition not found for ID: {}", entity.vehicleId)
            ClientRenderProfiler.endTrain(profilerStart)
            return
        }

        val model = MqoModelLoader.loadModelForVehicle(definition)
        if (model == null) {
            RealTrainModRenewed.LOGGER.warn("Model is null for vehicle {}", entity.vehicleId)
            ClientRenderProfiler.endTrain(profilerStart)
            return
        }

        if (model.getScriptEngine() != null && entity.scriptEngine !== model.getScriptEngine()) {
            entity.scriptEngine = model.getScriptEngine()
        }
        if (entity.getSoundScriptEngine() == null && definition.hasSoundScript()) {
            entity.setSoundScriptEngine(MqoModelLoader.loadSoundScriptForVehicle(definition))
        }

        poseStack.pushPose()
        try {
            val renderYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.yRot)
            poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw))

            var renderPitch = Mth.lerp(partialTicks, entity.xRotO, entity.xRot)
            renderPitch = Mth.clamp(renderPitch, -45.0f, 45.0f)
            if (abs(renderPitch) > 0.001f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-renderPitch))
            }

            val yawDelta = Mth.wrapDegrees(entity.yRot - entity.yRotO)
            val horizSpeed = entity.deltaMovement.horizontalDistance().toFloat()
            val bankAngle = Mth.clamp(-yawDelta * horizSpeed * 5.0f, -10.0f, 10.0f)
            if (abs(bankAngle) > 0.01f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(bankAngle))
            }

            poseStack.translate(definition.modelOffset.x, definition.modelOffset.y, definition.modelOffset.z)
            poseStack.scale(definition.modelScale, definition.modelScale, definition.modelScale)

            val minecraft = Minecraft.getInstance()
            var ridingThisTrain = false
            val player = minecraft.player
            if (player != null) {
                val vehicle = player.vehicle
                if (vehicle is TrainEntity) {
                    ridingThisTrain = vehicle.formationHead === entity.formationHead
                } else if (vehicle is TrainSeatEntity && vehicle.train != null) {
                    ridingThisTrain = vehicle.train!!.formationHead === entity.formationHead
                }
            }
            val cameraDistanceSq = minecraft.gameRenderer.mainCamera.position()
                .distanceToSqr(entity.x, entity.y + 1.5, entity.z)
            val compatibilityHeavy = definition.isDoCulling() || shouldUseCompatibilityRendering(definition, model)
            val nearThreshold = if (compatibilityHeavy) 34.0 else 48.0
            val aggressiveThreshold = if (compatibilityHeavy) 40.0 else 56.0
            val rollsignThreshold = if (compatibilityHeavy) 42.0 else 64.0
            val lightThreshold = if (compatibilityHeavy) 64.0 else 96.0
            val nearTrain = cameraDistanceSq < nearThreshold * nearThreshold
            val modelScriptRunning = model.hasRenderScript()
            val modelHasScript = modelScriptRunning || definition.hasScript()
            val renderInterior = ridingThisTrain || nearTrain || modelHasScript && model.hasGroupNamed("alpha")
            val aggressiveDistanceCulling = !ridingThisTrain && cameraDistanceSq > aggressiveThreshold * aggressiveThreshold
            val renderRollsigns = ridingThisTrain || cameraDistanceSq < rollsignThreshold * rollsignThreshold
            val renderLights = ridingThisTrain || cameraDistanceSq < lightThreshold * lightThreshold
            val trainPackedLight = resolveTrainPackedLight(entity, packedLight)
            val groupFilter = MqoModelLoader.GroupPredicate { groupName ->
                shouldRenderTrainGroup(
                    groupName,
                    renderInterior,
                    aggressiveDistanceCulling,
                    compatibilityHeavy,
                    definition,
                    modelHasScript,
                    modelScriptRunning,
                )
            }
            val doorTransform = object : MqoModelLoader.GroupTransform {
                override fun apply(stack: PoseStack, groupName: String?) {
                    applyRunningGearTransform(stack, entity, definition, model, groupName, renderYaw, partialTicks)
                    applyDoorTransform(stack, definition.leftDoors, groupName, entity.doorMoveL, true)
                    applyDoorTransform(stack, definition.rightDoors, groupName, entity.doorMoveR, false)
                }

                override fun mayModify(groupName: String?): Boolean {
                    if (groupName == null || groupName.length < 4) return false
                    if (isRunningGearGroup(groupName)) return true
                    var i = 0
                    val end = groupName.length - 4
                    while (i <= end) {
                        val c0 = groupName[i]
                        if (c0 != 'd' && c0 != 'D') {
                            i++
                            continue
                        }
                        val c1 = groupName[i + 1]
                        val c2 = groupName[i + 2]
                        val c3 = groupName[i + 3]
                        if ((c1 == 'o' || c1 == 'O') && (c2 == 'o' || c2 == 'O') && (c3 == 'r' || c3 == 'R')) {
                            return true
                        }
                        i++
                    }
                    return false
                }
            }

            if (LOGGED_VEHICLES.add(entity.vehicleId ?: "")) {
                val allGroups = model.allNormalizedGroupNames
                RealTrainModRenewed.LOGGER.info(
                    "[Render] vehicle={} script={} scriptRunning={} bogies={} groupsCount={}",
                    entity.vehicleId,
                    definition.hasScript(),
                    modelScriptRunning,
                    definition.bogies.size,
                    allGroups.size,
                )
                RealTrainModRenewed.LOGGER.info("[Render] all groups: {}", allGroups)
            }
            MqoModelLoader.renderModel(model, poseStack, buffer, trainPackedLight, groupFilter, doorTransform, entity)
            try {
                renderBogiesInline(entity, definition, model, poseStack, buffer, trainPackedLight, partialTicks)
            } catch (throwable: Throwable) {
                RealTrainModRenewed.LOGGER.debug("Inline bogie render failed for {}: {}", entity.vehicleId, throwable.toString())
            }
            if (renderRollsigns) {
                renderConfiguredRollsigns(entity, definition, poseStack, buffer, trainPackedLight)
            }
            if (renderLights) {
                renderConfiguredLights(entity, definition, model, poseStack, buffer, renderYaw, ridingThisTrain)
            }
        } catch (throwable: Throwable) {
            RealTrainModRenewed.LOGGER.error("Failed to render model", throwable)
        } finally {
            try {
                poseStack.popPose()
            } catch (ignored: Throwable) {
            }
        }
        ClientRenderProfiler.endTrain(profilerStart)
    }

    private companion object {
        private var glowTexture: Identifier? = null
        private val LOGGED_VEHICLES: MutableSet<String> = Collections.synchronizedSet(HashSet())
        private val LOGGED_BOGIE_VEHICLES: MutableSet<String> = Collections.synchronizedSet(HashSet())

        private fun getGlowTexture(): Identifier {
            if (glowTexture == null) {
                glowTexture = buildGlowTexture()
            }
            return glowTexture!!
        }

        private fun buildGlowTexture(): Identifier {
            val location = Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "dynamic/effect/train_light_glow")
            val image = NativeImage(64, 64, false)
            for (y in 0 until 64) {
                for (x in 0 until 64) {
                    val dx = (x - 31.5f) / 32.0f
                    val dy = (y - 31.5f) / 32.0f
                    val radius = sqrt(dx * dx + dy * dy)
                    val alpha = if (radius >= 1.0f) 0.0f else (1.0f - radius).pow(1.5f)
                    val a = min(255, (alpha * 255).toInt())
                    image.setPixel(x, y, (a shl 24) or 0x00FFFFFF)
                }
            }
            Minecraft.getInstance().textureManager.register(
                location,
                DynamicTexture({ "realtrainmodunofficial train light glow" }, image),
            )
            return location
        }

        private fun resolveTrainPackedLight(entity: TrainEntity?, fallbackPackedLight: Int): Int {
            if (entity == null || entity.level() == null) {
                return fallbackPackedLight
            }
            return try {
                val bodyPos = BlockPos.containing(entity.x, entity.y + 1.5, entity.z)
                LevelRenderer.getLightCoords(entity.level(), bodyPos)
            } catch (ignored: Throwable) {
                fallbackPackedLight
            }
        }

        private fun applyRunningGearTransform(
            poseStack: PoseStack?,
            entity: TrainEntity?,
            definition: VehicleDefinition?,
            model: MqoModelLoader.MqoModel?,
            groupName: String?,
            renderYaw: Float,
            partialTicks: Float,
        ) {
            if (poseStack == null || entity == null || definition == null || model == null || !isRunningGearGroup(groupName)) {
                return
            }
            if (definition.bogies.isEmpty()) {
                return
            }
            val center = model.getGroupCenter(groupName) ?: return
            var bestIndex = -1
            var bestDistance = Double.POSITIVE_INFINITY
            for (i in definition.bogies.indices) {
                val bogiePos = definition.bogies[i].position()
                val dz = center.z - bogiePos.z
                val dx = center.x - bogiePos.x
                val distance = dz * dz + dx * dx * 0.25
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            if (bestIndex < 0) {
                return
            }
            val bogie = definition.bogies[bestIndex]
            val corrected = entity.getBogieRenderOffset(bestIndex, bogie, renderYaw, partialTicks)
            val delta = corrected.subtract(bogie.position())
            if (delta.lengthSqr() > 1.0E-8) {
                poseStack.translate(delta.x, delta.y, delta.z)
            }
        }

        private fun isRunningGearGroup(groupName: String?): Boolean {
            if (groupName.isNullOrBlank()) {
                return false
            }
            val lower = groupName.lowercase(Locale.ROOT)
            return lower.contains("bogie") ||
                lower.contains("wheel") ||
                lower.contains("truck") ||
                lower.contains("daisya") ||
                lower.contains("daisha") ||
                lower.contains("sharin") ||
                lower.contains("車輪") ||
                lower.contains("台車")
        }

        private fun applyDoorTransform(
            poseStack: PoseStack,
            doors: List<VehicleDefinition.DoorAnimationDefinition>?,
            groupName: String?,
            progressTicks: Float,
            leftSide: Boolean,
        ) {
            if (groupName == null) return
            if (groupName.indexOf('d') < 0 && groupName.indexOf('D') < 0) return
            var mayBeDoor = false
            var i = 0
            val end = groupName.length - 4
            while (i <= end) {
                val c0 = groupName[i]
                if (c0 != 'd' && c0 != 'D') {
                    i++
                    continue
                }
                val c1 = groupName[i + 1]
                val c2 = groupName[i + 2]
                val c3 = groupName[i + 3]
                if ((c1 == 'o' || c1 == 'O') && (c2 == 'o' || c2 == 'O') && (c3 == 'r' || c3 == 'R')) {
                    mayBeDoor = true
                    break
                }
                i++
            }
            if (!mayBeDoor) return
            val progress = smoothstep(Mth.clamp(progressTicks / 60.0f, 0.0f, 1.0f))
            if (doors == null || doors.isEmpty()) {
                applyLegacyDoorFallback(poseStack, groupName, progress, leftSide)
                return
            }
            for (door in doors) {
                if (!matchesDoorGroup(door.objects(), groupName)) {
                    continue
                }
                poseStack.translate(
                    door.openTranslation().x * progress,
                    door.openTranslation().y * progress,
                    door.openTranslation().z * progress,
                )
                return
            }
        }

        private fun applyLegacyDoorFallback(poseStack: PoseStack, groupName: String?, progress: Float, leftSide: Boolean) {
            val normalized = groupName?.lowercase(Locale.ROOT) ?: ""
            if (!normalized.contains("door")) {
                return
            }
            val isDoorLeaf = normalized.matches(Regex(".*(?:^|_)[0-9]+[lr](?:_|$).*")) ||
                normalized.matches(Regex(".*(?:^|_)[lr](?:_|$).*")) ||
                normalized.matches(Regex(".*door[fb]?[lr][0-9_]*.*")) ||
                normalized.matches(Regex(".*door[0-9]+[lr].*"))
            if (!isDoorLeaf) {
                return
            }
            val slide = 0.72 * progress
            val opensTowardPositiveZ = normalized.matches(Regex(".*[0-9]+l(?:_|$).*")) ||
                normalized.contains("_l_") ||
                normalized.endsWith("_l") ||
                normalized.matches(Regex(".*door[fb]?l[0-9_]*.*")) ||
                normalized.matches(Regex(".*door[0-9]+l.*"))
            poseStack.translate(0.0, 0.0, if (opensTowardPositiveZ) slide else -slide)
        }

        private fun matchesDoorGroup(objects: List<String>?, groupName: String?): Boolean {
            if (objects == null || objects.isEmpty() || groupName.isNullOrBlank()) {
                return false
            }
            for (objectName in objects) {
                if (objectName != null && objectName.equals(groupName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        private fun smoothstep(x: Float): Float = x * x * (3.0f - 2.0f * x)

        private fun renderConfiguredRollsigns(
            entity: TrainEntity,
            definition: VehicleDefinition?,
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
        ) {
            if (definition == null || definition.rollsigns.isEmpty()) {
                return
            }
            val texturePath = definition.rollsignTexture
            if (texturePath.isNullOrBlank()) {
                return
            }
            val texture = MqoModelLoader.resolvePackTexture(definition.packName, texturePath)
            val count = max(1, if (definition.rollsignNames.isEmpty()) 1 else definition.rollsignNames.size)
            val destinationIndex = Math.floorMod(entity.destinationIndex, count)
            val segmentV0 = destinationIndex / count.toFloat()
            val segmentV1 = (destinationIndex + 1.0f) / count.toFloat()
            val consumer = buffer.getBuffer(RenderTypes.entityCutout(texture ?: return))
            val pose = poseStack.last()
            val matrix = pose.pose()
            val normalMatrix = pose.normal()

            for (rollsign in definition.rollsigns) {
                val uv = rollsign.uv()
                if (uv == null || uv.size < 4) {
                    continue
                }
                val uMin = uv[0]
                val uMax = uv[1]
                val baseVMin = uv[2]
                val baseVMax = uv[3]
                val vMin = Mth.lerp(segmentV0, baseVMin, baseVMax)
                val vMax = Mth.lerp(segmentV1, baseVMin, baseVMax)
                val signLight = if (rollsign.disableLighting()) 0x00F000F0 else packedLight

                for (quad in rollsign.pos()) {
                    if (quad == null || quad.size < 4) {
                        continue
                    }
                    emitRollsignQuad(
                        matrix,
                        normalMatrix,
                        consumer,
                        signLight,
                        toPoint(quad[3]),
                        toPoint(quad[2]),
                        toPoint(quad[1]),
                        toPoint(quad[0]),
                        uMin,
                        vMin,
                        uMin,
                        vMax,
                        uMax,
                        vMax,
                        uMax,
                        vMin,
                    )
                }
            }
        }

        private fun toPoint(point: FloatArray): Vector3f = Vector3f(point[0], point[1], point[2])

        private fun emitRollsignQuad(
            matrix: Matrix4f,
            normalMatrix: Matrix3f,
            consumer: VertexConsumer,
            packedLight: Int,
            p0: Vector3f,
            p1: Vector3f,
            p2: Vector3f,
            p3: Vector3f,
            u0: Float,
            v0: Float,
            u1: Float,
            v1: Float,
            u2: Float,
            v2: Float,
            u3: Float,
            v3: Float,
        ) {
            val edge1 = Vector3f(p1).sub(p0)
            val edge2 = Vector3f(p2).sub(p0)
            val normal = edge1.cross(edge2)
            if (normal.lengthSquared() <= 1.0E-8f) {
                return
            }
            normal.normalize()

            val offset = Vector3f(normal).mul(0.0015f)
            val nx = normalMatrix.m00() * normal.x + normalMatrix.m10() * normal.y + normalMatrix.m20() * normal.z
            val ny = normalMatrix.m01() * normal.x + normalMatrix.m11() * normal.y + normalMatrix.m21() * normal.z
            val nz = normalMatrix.m02() * normal.x + normalMatrix.m12() * normal.y + normalMatrix.m22() * normal.z

            putRollsignVertex(consumer, matrix, p0, offset, u0, v0, packedLight, nx, ny, nz)
            putRollsignVertex(consumer, matrix, p1, offset, u1, v1, packedLight, nx, ny, nz)
            putRollsignVertex(consumer, matrix, p2, offset, u2, v2, packedLight, nx, ny, nz)
            putRollsignVertex(consumer, matrix, p3, offset, u3, v3, packedLight, nx, ny, nz)
        }

        private fun putRollsignVertex(
            consumer: VertexConsumer,
            matrix: Matrix4f,
            point: Vector3f,
            offset: Vector3f,
            u: Float,
            v: Float,
            packedLight: Int,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            consumer.addVertex(matrix, point.x + offset.x, point.y + offset.y, point.z + offset.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(nx, ny, nz)
        }

        private fun isAngleBendVariant(normalized: String): Boolean {
            val source = if (normalized.endsWith("(mx)")) normalized.substring(0, normalized.length - 4) else normalized
            val dash = source.lastIndexOf('-')
            if (dash <= 0 || dash == source.length - 1) return false
            for (i in dash + 1 until source.length) {
                if (!Character.isDigit(source[i])) return false
            }
            return try {
                source.substring(dash + 1).toInt() >= 10
            } catch (e: NumberFormatException) {
                false
            }
        }

        private fun shouldRenderTrainGroup(
            groupName: String?,
            renderInterior: Boolean,
            aggressiveDistanceCulling: Boolean,
            compatibilityHeavy: Boolean,
            definition: VehicleDefinition,
            hasScript: Boolean,
            scriptActuallyRunning: Boolean,
        ): Boolean {
            if (groupName.isNullOrBlank()) {
                return true
            }
            val normalized = groupName.lowercase(Locale.ROOT)
            if (isAngleBendVariant(normalized)) {
                return false
            }
            val isBogieOrWheel = normalized.contains("bogie") ||
                normalized.contains("wheel") ||
                normalized.contains("daisya") ||
                normalized.contains("daisha") ||
                normalized.contains("sharin")
            if (isBogieOrWheel) {
                val hasSeparateBogieModel = definition.bogies.any { bogie -> !bogie.modelFile().isNullOrBlank() }
                if (hasSeparateBogieModel && !scriptActuallyRunning && !definition.hasScript()) return false
            }
            if (normalized.contains("shadow")) {
                return false
            }
            if (normalized.endsWith("_ms") || normalized.endsWith("_kage") ||
                normalized.contains("_ms_") || normalized.contains("_kage_")
            ) {
                return false
            }
            if (normalized.endsWith("_guide") || normalized.endsWith("[obj]") ||
                normalized.endsWith("_atari") || normalized.endsWith(" atari")
            ) {
                return false
            }
            if (!renderInterior) {
                if (normalized.contains("seat") ||
                    normalized.contains("chair") ||
                    normalized.contains("interior") ||
                    normalized.contains("inside") ||
                    normalized.contains("floor") ||
                    normalized.contains("ceiling") ||
                    normalized.contains("handrail") ||
                    normalized.contains("strap") ||
                    normalized.contains("shelf") ||
                    normalized.contains("cab") ||
                    normalized.contains("desk") ||
                    normalized.contains("instrument") ||
                    normalized.contains("panel")
                ) {
                    return false
                }
            }
            if (definition.hasScript() && !scriptActuallyRunning &&
                definition.headLights.isEmpty() && definition.tailLights.isEmpty()
            ) {
                val isLightGroup = normalized == "light" ||
                    normalized == "lightf" ||
                    normalized == "lightb" ||
                    normalized == "lightr" ||
                    normalized == "lightl" ||
                    normalized.startsWith("light_f") ||
                    normalized.startsWith("light_b") ||
                    normalized.startsWith("lightf_") ||
                    normalized.startsWith("lightb_") ||
                    normalized.endsWith("_light") ||
                    normalized.endsWith("-light")
                if (isLightGroup) {
                    // Preserve Java behavior: intentionally allow through.
                }
            }
            if (aggressiveDistanceCulling) {
                if (normalized.contains("wiper") ||
                    normalized.contains("coupler") ||
                    normalized.contains("connector") ||
                    normalized.contains("hoses") ||
                    normalized.contains("step") ||
                    normalized.contains("pantograph") ||
                    normalized.contains("under") ||
                    normalized.contains("detail")
                ) {
                    return false
                }
            }
            if (compatibilityHeavy) {
                if (aggressiveDistanceCulling &&
                    (normalized.contains("cooler") ||
                        normalized.contains("fan") ||
                        normalized.contains("antenna"))
                ) {
                    return false
                }
            }
            return true
        }

        private fun renderBogiesInline(
            entity: TrainEntity,
            definition: VehicleDefinition?,
            bodyModel: MqoModelLoader.MqoModel?,
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            packedLight: Int,
            partialTicks: Float,
        ) {
            if (definition == null || definition.bogies.isEmpty()) {
                return
            }
            val selfDrawsRunningGear = bodyModel != null && bodyModel.hasOwnWheelGroups()
            val baseYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.yRot)
            for (i in definition.bogies.indices) {
                val bogieDef = definition.bogies[i]
                if (shouldSkipInlineBogie(selfDrawsRunningGear, bogieDef)) {
                    continue
                }
                try {
                    BogieRenderer.renderBogie(poseStack, i, bogieDef, definition, entity, buffer, packedLight, baseYaw, partialTicks)
                } catch (ignored: Throwable) {
                }
            }
        }

        private fun shouldSkipInlineBogie(selfDrawsRunningGear: Boolean, bogieDef: VehicleDefinition.BogieDefinition?): Boolean {
            if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
                return true
            }
            if (BogieRenderer.isDummyBogieModel(bogieDef.modelFile())) {
                return true
            }
            return selfDrawsRunningGear && bogieDef.modelFile().lowercase(Locale.ROOT).endsWith(".class")
        }

        private fun shouldUseCompatibilityRendering(definition: VehicleDefinition?, model: MqoModelLoader.MqoModel?): Boolean {
            if (definition == null || model == null) {
                return false
            }
            val translucentBatches = model.translucentBatchCount
            val totalVertices = model.totalVertexCount
            val batchCount = model.batchCount
            val hasLegacyScript = !definition.scriptPath.isNullOrBlank()
            val hasManyOverlayFeatures = definition.rollsigns.isNotEmpty() ||
                definition.headLights.isNotEmpty() ||
                definition.tailLights.isNotEmpty() ||
                definition.interiorLights.isNotEmpty()
            return totalVertices >= 18_000 ||
                batchCount >= 160 ||
                translucentBatches >= 28 ||
                (hasLegacyScript && translucentBatches >= 12) ||
                (hasManyOverlayFeatures && totalVertices >= 12_000 && batchCount >= 96)
        }

        private fun renderConfiguredLights(
            entity: TrainEntity,
            definition: VehicleDefinition?,
            model: MqoModelLoader.MqoModel,
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            renderYaw: Float,
            ridingThisTrain: Boolean,
        ) {
            if (definition == null) return
            val mode = entity.lightMode
            val interiorOn = entity.isInteriorLightOn
            if (mode <= 0 && !interiorOn) return

            val singleTrainActive = definition.isSingleTrain() && !entity.isConnected
            val renderHeadLights = mode == 1 || mode == 2 || mode == 3
            var renderTailLights = mode == 2 || mode == 3
            if (singleTrainActive && mode == 1) renderTailLights = true

            val billRight = Vector3f(1f, 0f, 0f)
            val billUp = Vector3f(0f, 1f, 0f)
            val invYaw = Axis.YP.rotationDegrees(renderYaw).conjugate()
            val cameraRotation = Minecraft.getInstance().gameRenderer.mainCamera.rotation()
            cameraRotation.transform(billRight)
            cameraRotation.transform(billUp)
            invYaw.transform(billRight)
            invYaw.transform(billUp)
            billRight.normalize()
            billUp.normalize()

            val consumer = buffer.getBuffer(RenderTypes.entityTranslucentEmissive(getGlowTexture()))
            val pose = poseStack.last()
            val matrix = pose.pose()
            val normalMatrix = pose.normal()

            if (renderHeadLights) {
                for (light in definition.headLights) {
                    renderLightGlow(consumer, matrix, normalMatrix, light, true, billRight, billUp)
                }
            }
            if (renderTailLights) {
                for (light in definition.tailLights) {
                    renderLightGlow(consumer, matrix, normalMatrix, light, false, billRight, billUp)
                }
            }
            if (interiorOn && ridingThisTrain) {
                for (light in definition.interiorLights) {
                    renderLightGlow(consumer, matrix, normalMatrix, light, true, billRight, billUp)
                }
            }
            if (!definition.hasScript() && definition.headLights.isEmpty() && definition.tailLights.isEmpty()) {
                renderLegacyFallbackLights(entity, consumer, matrix, normalMatrix, mode, billRight, billUp)
            }
        }

        private fun renderLegacyFallbackLights(
            entity: TrainEntity,
            consumer: VertexConsumer,
            matrix: Matrix4f,
            normalMatrix: Matrix3f,
            mode: Int,
            billRight: Vector3f,
            billUp: Vector3f,
        ) {
            val halfLength = max(3.5f, entity.trainDistance - 0.45f)
            val lampY = 1.52f
            val lampX = 0.58f
            if (mode == 1 || mode == 2 || mode == 3) {
                renderLightGlow(
                    consumer,
                    matrix,
                    normalMatrix,
                    VehicleDefinition.LightDefinition(0.toByte(), 0xFFF6F0C8.toInt(), Vec3((-lampX).toDouble(), lampY.toDouble(), halfLength.toDouble()), 0.6f, false),
                    true,
                    billRight,
                    billUp,
                )
                renderLightGlow(
                    consumer,
                    matrix,
                    normalMatrix,
                    VehicleDefinition.LightDefinition(0.toByte(), 0xFFF6F0C8.toInt(), Vec3(lampX.toDouble(), lampY.toDouble(), halfLength.toDouble()), 0.6f, false),
                    true,
                    billRight,
                    billUp,
                )
            }
            if (mode == 2 || mode == 3) {
                renderLightGlow(
                    consumer,
                    matrix,
                    normalMatrix,
                    VehicleDefinition.LightDefinition(0.toByte(), 0xFFFF4040.toInt(), Vec3((-lampX).toDouble(), lampY.toDouble(), (-halfLength).toDouble()), 0.45f, true),
                    false,
                    billRight,
                    billUp,
                )
                renderLightGlow(
                    consumer,
                    matrix,
                    normalMatrix,
                    VehicleDefinition.LightDefinition(0.toByte(), 0xFFFF4040.toInt(), Vec3(lampX.toDouble(), lampY.toDouble(), (-halfLength).toDouble()), 0.45f, true),
                    false,
                    billRight,
                    billUp,
                )
            }
        }

        private fun renderLightGlow(
            consumer: VertexConsumer,
            matrix: Matrix4f,
            normalMatrix: Matrix3f,
            light: VehicleDefinition.LightDefinition?,
            frontFacing: Boolean,
            billRight: Vector3f,
            billUp: Vector3f,
        ) {
            if (light == null || light.position() == null) return

            val argb = if (light.color() == 0) -1 else light.color()
            var baseAlpha = argb ushr 24 and 0xFF
            if (baseAlpha == 0) baseAlpha = 230
            val red = argb ushr 16 and 0xFF
            val green = argb ushr 8 and 0xFF
            val blue = argb and 0xFF

            val cx = light.position().x.toFloat()
            val cy = light.position().y.toFloat()
            val cz = light.position().z.toFloat()
            val baseSize = max(light.radius() * 0.4f, 0.10f)

            val nx = normalMatrix.m20()
            val ny = normalMatrix.m21()
            val nz = normalMatrix.m22()

            putBillboardQuad(consumer, matrix, cx, cy, cz, baseSize * 0.45f, red, green, blue, baseAlpha, billRight, billUp, nx, ny, nz)
            putBillboardQuad(consumer, matrix, cx, cy, cz, baseSize, red, green, blue, (baseAlpha * 0.55f).toInt(), billRight, billUp, nx, ny, nz)
            putBillboardQuad(consumer, matrix, cx, cy, cz, baseSize * 2.0f, red, green, blue, (baseAlpha * 0.22f).toInt(), billRight, billUp, nx, ny, nz)
        }

        private fun putBillboardQuad(
            consumer: VertexConsumer,
            matrix: Matrix4f,
            cx: Float,
            cy: Float,
            cz: Float,
            size: Float,
            red: Int,
            green: Int,
            blue: Int,
            alpha: Int,
            right: Vector3f,
            up: Vector3f,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            val rx = right.x * size
            val ry = right.y * size
            val rz = right.z * size
            val ux = up.x * size
            val uy = up.y * size
            val uz = up.z * size
            putLightVertex(consumer, matrix, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0f, 0f, red, green, blue, alpha, nx, ny, nz)
            putLightVertex(consumer, matrix, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 0f, red, green, blue, alpha, nx, ny, nz)
            putLightVertex(consumer, matrix, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, 1f, red, green, blue, alpha, nx, ny, nz)
            putLightVertex(consumer, matrix, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0f, 1f, red, green, blue, alpha, nx, ny, nz)
        }

        private fun putLightVertex(
            consumer: VertexConsumer,
            matrix: Matrix4f,
            x: Float,
            y: Float,
            z: Float,
            u: Float,
            v: Float,
            red: Int,
            green: Int,
            blue: Int,
            alpha: Int,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(nx, ny, nz)
        }
    }
}

