// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ClientRenderProfiler
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.rail.RailDefinition
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry
import cc.mirukuneko.realtrainmodrenewed.rail.util.Point
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailDir
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

open class RailCoreBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") ctx: BlockEntityRendererProvider.Context,
) : BlockEntityRenderer<LargeRailCoreBlockEntity, LegacyBlockEntityRenderState<LargeRailCoreBlockEntity>> {
    private enum class RenderSwitchLayout {
        NONE,
        BASIC,
        SINGLE_CROSS,
        SCISSORS,
        DIAMOND,
    }

    private enum class RailGroup {
        BASE,
        NON_BRANCH,
        LEFT,
        RIGHT,
        TONG_FL,
        TONG_BL,
        TONG_FR,
        TONG_BR,
    }

    override fun createRenderState(): LegacyBlockEntityRenderState<LargeRailCoreBlockEntity> =
        LegacyBlockEntityRenderState()

    override fun extractRenderState(
        blockEntity: LargeRailCoreBlockEntity,
        state: LegacyBlockEntityRenderState<LargeRailCoreBlockEntity>,
        partialTick: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?,
    ) {
        super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress)
        state.blockEntity = blockEntity
        state.partialTick = partialTick
    }

    override fun submit(
        state: LegacyBlockEntityRenderState<LargeRailCoreBlockEntity>,
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
        be: LargeRailCoreBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        @Suppress("UNUSED_PARAMETER") packedOverlay: Int,
    ) {
        val profilerStart = ClientRenderProfiler.begin()
        try {
            if (!be.isLoaded) return
            var definition = RailRegistry.getById(be.railDefinitionId)
            if (definition == null) definition = RailRegistry.getSelected()
            if (definition == null) return
            val model = MqoModelLoader.loadModelForRail(definition) ?: return
            val compatibilityHeavy = shouldUseCompatibilityRendering(definition, model)
            val maps = be.allRailMaps
            if (maps.isEmpty()) return
            val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position()
            val cameraDistanceSq = cameraPos.distanceToSqr(
                be.blockPos.x + 0.5,
                be.blockPos.y + 0.5,
                be.blockPos.z + 0.5,
            )

            val origin = be.blockPos
            val ox = origin.x.toDouble()
            val oy = origin.y.toDouble()
            val oz = origin.z.toDouble()
            val modelOffset = definition.modelOffset
            val scale = definition.modelScale

            if (maps.size > 1) {
                val layout = detectSwitchLayout(be.railPositions)
                val activeIndex = Mth.clamp(be.activeSegmentIndex, 0, maps.size - 1)
                val previousIndex = Mth.clamp(be.previousSegmentIndex, 0, maps.size - 1)
                val switchProgress = be.getSwitchProgress(partialTick)
                val points = be.switchPoints
                if (points != null && points.isNotEmpty() && railModelHasSwitchParts(model)) {
                    for (mapIndex in maps.indices) {
                        val map = maps[mapIndex]
                        renderMapGroups(
                            be,
                            map,
                            poseStack,
                            buffer,
                            packedLight,
                            ox,
                            oy,
                            oz,
                            modelOffset,
                            scale,
                            model,
                            definition,
                            cameraDistanceSq,
                            compatibilityHeavy,
                            RailGroup.BASE,
                            mapIndex * 0.01f,
                        )
                    }
                    for (point in points) {
                        renderSwitchPoint(
                            be,
                            point,
                            poseStack,
                            buffer,
                            packedLight,
                            ox,
                            oy,
                            oz,
                            modelOffset,
                            scale,
                            model,
                            definition,
                            cameraDistanceSq,
                            compatibilityHeavy,
                        )
                    }
                    return
                }
                for (mapIndex in maps.indices) {
                    val map = maps[mapIndex]
                    renderRailMap(
                        be,
                        map,
                        mapIndex,
                        layout,
                        activeIndex,
                        previousIndex,
                        switchProgress,
                        poseStack,
                        buffer,
                        packedLight,
                        ox,
                        oy,
                        oz,
                        modelOffset,
                        scale,
                        model,
                        definition,
                        cameraDistanceSq,
                        compatibilityHeavy,
                        null,
                    )
                }
                return
            }

            val activeIndex = Mth.clamp(be.activeSegmentIndex, 0, maps.size - 1)
            val activeMap = maps[activeIndex]
            renderRailMap(
                be,
                activeMap,
                activeIndex,
                RenderSwitchLayout.NONE,
                activeIndex,
                activeIndex,
                1.0f,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                modelOffset,
                scale,
                model,
                definition,
                cameraDistanceSq,
                compatibilityHeavy,
                null,
            )
        } catch (throwable: Throwable) {
            RealTrainModRenewed.LOGGER.warn("Skipping rail render at {} after renderer failure", be.blockPos, throwable)
        } finally {
            ClientRenderProfiler.endRail(profilerStart)
        }
    }

    private fun renderRailMap(
        blockEntity: LargeRailCoreBlockEntity,
        map: RailMap,
        mapIndex: Int,
        layout: RenderSwitchLayout,
        activeIndex: Int,
        previousIndex: Int,
        switchProgress: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        modelOffset: Vec3,
        scale: Float,
        model: MqoModelLoader.MqoModel,
        definition: RailDefinition,
        cameraDistanceSq: Double,
        compatibilityHeavy: Boolean,
        primarySamples: Array<RailSample>?,
    ) {
        val length = map.getLength()
        if (length < 1.0e-4) {
            return
        }

        val sampleMax = computeRailSampleMax(map, length, definition, cameraDistanceSq)
        val samples = getOrCreateSamples(map, BlockPos(ox.toInt(), oy.toInt(), oz.toInt()), sampleMax)
        val stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy)
        val clip = computeSwitchClip(map, mapIndex, layout, activeIndex, previousIndex, switchProgress, sampleMax)
        val divergenceStart = if (primarySamples == null) 0 else computeDivergenceStart(samples, primarySamples, 0.15)
        val startIndex = min(max(0, max(clip[0], divergenceStart)), samples.size - 1)
        val endIndex = max(startIndex, samples.size - 1 - max(0, clip[1]))
        var i = startIndex
        while (i <= endIndex) {
            val sample = samples[i]
            renderRailSample(
                blockEntity,
                sample.x,
                sample.y,
                sample.z,
                sample.yaw,
                sample.pitch,
                sample.roll,
                i,
                sampleMax,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                modelOffset,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy,
            )
            i += stride
        }
        if (endIndex > startIndex && (endIndex - startIndex) % stride != 0) {
            val sample = samples[endIndex]
            renderRailSample(
                blockEntity,
                sample.x,
                sample.y,
                sample.z,
                sample.yaw,
                sample.pitch,
                sample.roll,
                endIndex,
                sampleMax,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                modelOffset,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy,
            )
        }
    }

    private fun renderModelGroup(
        model: MqoModelLoader.MqoModel,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        modelOffset: Vec3,
        scale: Float,
        group: RailGroup,
    ) {
        poseStack.pushPose()
        poseStack.translate(modelOffset.x, modelOffset.y, modelOffset.z)
        poseStack.scale(scale, scale, scale)
        val filter = MqoModelLoader.GroupPredicate { groupName -> matchesRailGroup(groupName, group) }
        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, false, filter, null)
        if (model.hasTranslucentBatches()) {
            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, true, filter, null)
        }
        poseStack.popPose()
    }

    private fun renderMapGroups(
        be: LargeRailCoreBlockEntity,
        map: RailMap,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        modelOffset: Vec3,
        scale: Float,
        model: MqoModelLoader.MqoModel,
        definition: RailDefinition,
        cameraDistanceSq: Double,
        compatibilityHeavy: Boolean,
        group: RailGroup,
        depthBias: Float,
    ) {
        val length = map.getLength()
        if (length < 1.0e-4) return
        val sampleMax = computeRailSampleMax(map, length, definition, cameraDistanceSq)
        val samples = getOrCreateSamples(map, BlockPos(ox.toInt(), oy.toInt(), oz.toInt()), sampleMax)
        val stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy)
        var i = 0
        while (i < samples.size) {
            val sample = samples[i]
            poseStack.pushPose()
            poseStack.translate(sample.x - ox, sample.y - oy - 0.0625 + depthJitter(i) + depthBias, sample.z - oz)
            poseStack.mulPose(Axis.YP.rotationDegrees(sample.yaw))
            poseStack.mulPose(Axis.XP.rotationDegrees(-sample.pitch))
            poseStack.mulPose(Axis.ZP.rotationDegrees(sample.roll))
            renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, group)
            poseStack.popPose()
            i += stride
        }
    }

    private fun renderSwitchPoint(
        be: LargeRailCoreBlockEntity,
        point: Point,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        modelOffset: Vec3,
        scale: Float,
        model: MqoModelLoader.MqoModel,
        definition: RailDefinition,
        cameraDistanceSq: Double,
        compatibilityHeavy: Boolean,
    ) {
        if (point.branchDir == RailDir.NONE || point.rmBranch == null) {
            renderRailMapDynamic(
                be,
                point.rmMain,
                RailDir.NONE,
                point.mainDirIsPositive,
                0.0f,
                0,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                modelOffset,
                scale,
                model,
                definition,
                cameraDistanceSq,
                compatibilityHeavy,
                0.0f,
            )
            return
        }
        val movement = point.getMovement()
        val tongIndex = floor(point.rmMain.getLength() * 2.0 * TONG_POS).toInt()
        var move = movement * TONG_MOVE
        renderRailMapDynamic(
            be,
            point.rmMain,
            point.branchDir,
            point.mainDirIsPositive,
            move,
            tongIndex,
            poseStack,
            buffer,
            packedLight,
            ox,
            oy,
            oz,
            modelOffset,
            scale,
            model,
            definition,
            cameraDistanceSq,
            compatibilityHeavy,
            0.0f,
        )
        move = (1.0f - movement) * TONG_MOVE
        renderRailMapDynamic(
            be,
            point.rmBranch,
            point.branchDir.invert(),
            point.branchDirIsPositive,
            move,
            tongIndex,
            poseStack,
            buffer,
            packedLight,
            ox,
            oy,
            oz,
            modelOffset,
            scale,
            model,
            definition,
            cameraDistanceSq,
            compatibilityHeavy,
            0.012f,
        )
    }

    private fun renderRailMapDynamic(
        be: LargeRailCoreBlockEntity,
        railMap: RailMap,
        dir: RailDir,
        par3: Boolean,
        move: Float,
        tongIndex: Int,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        modelOffset: Vec3,
        scale: Float,
        model: MqoModelLoader.MqoModel,
        definition: RailDefinition,
        cameraDistanceSq: Double,
        compatibilityHeavy: Boolean,
        depthBias: Float,
    ) {
        val left = RailDir.LEFT
        val right = RailDir.RIGHT
        val railLength = railMap.getLength()
        val sampleMax = computeRailSampleMax(railMap, railLength, definition, cameraDistanceSq)
        val halfMax = sampleMax / 2
        val startIndex = if (par3) 0 else halfMax + 1
        val endIndex = if (par3) halfMax else sampleMax
        val samples = getOrCreateSamples(railMap, BlockPos(ox.toInt(), oy.toInt(), oz.toInt()), sampleMax)
        val flip = (par3 && dir == left) || (!par3 && dir == right)
        val dirFixture = if (flip) -1.0f else 1.0f
        var i = startIndex
        while (i <= endIndex && i < samples.size) {
            val sample = samples[i]
            poseStack.pushPose()
            poseStack.translate(sample.x - ox, sample.y - oy - 0.0625 + depthJitter(i) + depthBias, sample.z - oz)
            poseStack.mulPose(Axis.YP.rotationDegrees(sample.yaw))
            poseStack.mulPose(Axis.XP.rotationDegrees(-sample.pitch))
            renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, if (flip) RailGroup.RIGHT else RailGroup.LEFT)
            if (dir != RailDir.NONE && halfMax > 0) {
                var sep = (if (par3) i else sampleMax - i).toFloat() / halfMax.toFloat()
                sep = (1.0f - sigmoid2(sep)) * move * dirFixture
                val halfGaugeMove = dirFixture * HALF_GAUGE
                poseStack.translate((sep - halfGaugeMove).toDouble(), 0.0, 0.0)
                val yaw2 = sep * YAW_RATE / railLength.toFloat() * if (par3) -1.0f else 1.0f
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw2))
                poseStack.translate(halfGaugeMove.toDouble(), 0.0, 0.0)
                if (dir == left) {
                    if (par3) {
                        if (i == tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.TONG_BL)
                        else if (i > tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.LEFT)
                    } else {
                        if (i == sampleMax - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.TONG_FR)
                        else if (i < sampleMax - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.RIGHT)
                    }
                } else {
                    if (par3) {
                        if (i == tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.TONG_BR)
                        else if (i > tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.RIGHT)
                    } else {
                        if (i == sampleMax - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.TONG_FL)
                        else if (i < sampleMax - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, RailGroup.LEFT)
                    }
                }
            } else {
                renderModelGroup(model, poseStack, buffer, packedLight, modelOffset, scale, if (flip) RailGroup.LEFT else RailGroup.RIGHT)
            }
            poseStack.popPose()
            i++
        }
    }

    private fun renderInterpolatedMap(
        blockEntity: LargeRailCoreBlockEntity,
        previousMap: RailMap,
        activeMap: RailMap,
        progress: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        modelOffset: Vec3,
        scale: Float,
        model: MqoModelLoader.MqoModel,
        definition: RailDefinition,
        cameraDistanceSq: Double,
        compatibilityHeavy: Boolean,
    ) {
        val previousMax = computeRailSampleMax(previousMap, previousMap.getLength(), definition, cameraDistanceSq)
        val activeMax = computeRailSampleMax(activeMap, activeMap.getLength(), definition, cameraDistanceSq)
        val sampleMax = max(previousMax, activeMax)
        val stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy)
        var i = 0
        while (i <= sampleMax) {
            val t = if (sampleMax <= 0) 0.0f else i / sampleMax.toFloat()
            val previousIndex = Mth.clamp((t * previousMax).roundToInt(), 0, previousMax)
            val activeIndex = Mth.clamp((t * activeMax).roundToInt(), 0, activeMax)
            val previousPoint = previousMap.getRailPos(previousMax, previousIndex)
            val activePoint = activeMap.getRailPos(activeMax, activeIndex)
            val wx = Mth.lerp(progress.toDouble(), previousPoint[1], activePoint[1])
            val wy = Mth.lerp(
                progress.toDouble(),
                previousMap.getRailHeight(previousMax, previousIndex),
                activeMap.getRailHeight(activeMax, activeIndex),
            )
            val wz = Mth.lerp(progress.toDouble(), previousPoint[0], activePoint[0])
            val yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousIndex), activeMap.getRailYaw(activeMax, activeIndex))
            val pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousIndex), activeMap.getRailPitch(activeMax, activeIndex))
            val roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousIndex), activeMap.getCant(activeMax, activeIndex))
            renderRailSample(
                blockEntity,
                wx,
                wy,
                wz,
                yaw,
                pitch,
                roll,
                i,
                sampleMax,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                modelOffset,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy,
            )
            i += stride
        }
        if (sampleMax > 0 && sampleMax % stride != 0) {
            val previousPoint = previousMap.getRailPos(previousMax, previousMax)
            val activePoint = activeMap.getRailPos(activeMax, activeMax)
            val wx = Mth.lerp(progress.toDouble(), previousPoint[1], activePoint[1])
            val wy = Mth.lerp(
                progress.toDouble(),
                previousMap.getRailHeight(previousMax, previousMax),
                activeMap.getRailHeight(activeMax, activeMax),
            )
            val wz = Mth.lerp(progress.toDouble(), previousPoint[0], activePoint[0])
            val yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousMax), activeMap.getRailYaw(activeMax, activeMax))
            val pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousMax), activeMap.getRailPitch(activeMax, activeMax))
            val roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousMax), activeMap.getCant(activeMax, activeMax))
            renderRailSample(
                blockEntity,
                wx,
                wy,
                wz,
                yaw,
                pitch,
                roll,
                sampleMax,
                sampleMax,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                modelOffset,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy,
            )
        }
    }

    private fun renderRailSample(
        blockEntity: LargeRailCoreBlockEntity,
        wx: Double,
        wy: Double,
        wz: Double,
        yaw: Float,
        pitch: Float,
        roll: Float,
        pos: Int,
        max: Int,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        modelOffset: Vec3,
        scale: Float,
        model: MqoModelLoader.MqoModel,
        cameraDistanceSq: Double,
        compatibilityHeavy: Boolean,
    ) {
        poseStack.pushPose()
        val yBump = depthJitter(pos)
        poseStack.translate(wx - ox, wy - oy - 0.0625 + yBump, wz - oz)
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw))
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch))
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll))
        poseStack.translate(modelOffset.x, modelOffset.y, modelOffset.z)
        poseStack.scale(scale, scale, scale)
        MqoModelLoader.renderModelWithoutScript(
            model,
            poseStack,
            buffer,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            false,
            MqoModelLoader.GroupPredicate { groupName -> shouldRenderRailGroup(model, groupName, pos, max, compatibilityHeavy, cameraDistanceSq) },
            null,
        )
        val translucentThreshold = if (compatibilityHeavy) 38.0 else 72.0
        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
            MqoModelLoader.renderModelWithoutScript(
                model,
                poseStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                true,
                MqoModelLoader.GroupPredicate { groupName -> shouldRenderRailGroup(model, groupName, pos, max, compatibilityHeavy, cameraDistanceSq) },
                null,
            )
        }
        poseStack.popPose()
    }

    override fun getRenderBoundingBox(blockEntity: LargeRailCoreBlockEntity): AABB =
        blockEntity.getCachedRenderBounds()

    override fun shouldRenderOffScreen(): Boolean = true

    override fun getViewDistance(): Int = 160

    private data class RailSample(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
    )

    private data class RailCacheKey(
        val corePos: BlockPos,
        val mapId: Int,
        val sampleMax: Int,
    )

    private companion object {
        private val SAMPLE_CACHE: MutableMap<RailCacheKey, Array<RailSample>> = ConcurrentHashMap()

        private const val TONG_MOVE = 0.35f
        private const val TONG_POS = 1.0f / 10.0f
        private const val HALF_GAUGE = 0.5647f
        private const val YAW_RATE = 450.0f

        private fun computeRailSampleMax(
            map: RailMap,
            length: Double,
            definition: RailDefinition,
            cameraDistanceSq: Double,
        ): Int {
            val density = 2.0
            var samples = max(2, ceil(length * density).toInt())
            if (length < 2.5) {
                samples = min(samples, 12)
            }
            return min(samples, 768)
        }

        private fun depthJitter(pos: Int): Float = ((pos and 15) - 7.5f) * 1.2e-6f

        private fun computeRenderStride(cameraDistanceSq: Double, compatibilityHeavy: Boolean): Int = 1

        private fun railModelHasSwitchParts(model: MqoModelLoader.MqoModel): Boolean {
            val groups = model.allNormalizedGroupNames
            var hasTong = false
            var hasRail = false
            for (group in groups) {
                if (group == null) {
                    continue
                }
                val normalized = group.lowercase(Locale.ROOT)
                if (normalized == "l0" || normalized == "l1" || normalized == "r0" || normalized == "r1") hasTong = true
                if (normalized == "raill" || normalized == "railr") hasRail = true
            }
            return hasTong && hasRail
        }

        private fun matchesRailGroup(groupName: String?, group: RailGroup): Boolean {
            if (groupName == null) return false
            val normalized = groupName.lowercase(Locale.ROOT)
            return when (group) {
                RailGroup.BASE -> normalized.contains("base") ||
                    normalized.contains("ballast") ||
                    normalized.contains("sleeper") ||
                    normalized.contains("guide")
                RailGroup.NON_BRANCH -> normalized == "raill" ||
                    normalized == "sidel" ||
                    normalized == "railr" ||
                    normalized == "sider" ||
                    normalized == "springl" ||
                    normalized == "boltl" ||
                    normalized == "springr" ||
                    normalized == "boltr"
                RailGroup.LEFT -> normalized == "raill" || normalized == "sidel"
                RailGroup.RIGHT -> normalized == "railr" || normalized == "sider"
                RailGroup.TONG_FL -> normalized == "l0"
                RailGroup.TONG_BL -> normalized == "l1"
                RailGroup.TONG_FR -> normalized == "r0"
                RailGroup.TONG_BR -> normalized == "r1"
            }
        }

        private fun sigmoid2(x: Float): Float {
            val d0 = x * 3.5f
            val d1 = d0 / sqrt(1.0f + d0 * d0)
            return d1 * 0.75f + 0.25f
        }

        private fun computeSwitchClip(
            map: RailMap,
            mapIndex: Int,
            layout: RenderSwitchLayout,
            activeIndex: Int,
            previousIndex: Int,
            switchProgress: Float,
            sampleMax: Int,
        ): IntArray {
            if (layout == RenderSwitchLayout.NONE || sampleMax <= 0) {
                return intArrayOf(0, 0)
            }
            if (layout == RenderSwitchLayout.DIAMOND) {
                return intArrayOf(0, 0)
            }

            val active = isMapActiveForLayout(mapIndex, activeIndex, layout)
            val previouslyActive = isMapActiveForLayout(mapIndex, previousIndex, layout)
            var t = Mth.clamp(switchProgress, 0.0f, 1.0f)
            t = t * t * (3.0f - 2.0f * t)

            val clipRatio = if (active && previouslyActive) {
                0.0f
            } else if (active) {
                1.0f - t
            } else if (previouslyActive) {
                t
            } else {
                1.0f
            }

            val maxClip = max(1, sampleMax * 7 / 10)
            val clipStart = (maxClip * clipRatio).roundToInt()
            return intArrayOf(clipStart, 0)
        }

        private fun computeDivergenceStart(samples: Array<RailSample>?, primary: Array<RailSample>?, threshold: Double): Int {
            if (samples == null || primary == null || samples.isEmpty() || primary.isEmpty()) {
                return 0
            }
            val thresholdSq = threshold * threshold
            for (i in samples.indices) {
                val sample = samples[i]
                var best = Double.MAX_VALUE
                for (candidate in primary) {
                    val dx = sample.x - candidate.x
                    val dy = sample.y - candidate.y
                    val dz = sample.z - candidate.z
                    val distanceSq = dx * dx + dy * dy + dz * dz
                    if (distanceSq < best) {
                        best = distanceSq
                        if (best <= thresholdSq) break
                    }
                }
                if (best > thresholdSq) {
                    return i
                }
            }
            return samples.size - 1
        }

        private fun isMapActiveForLayout(mapIndex: Int, referenceIndex: Int, layout: RenderSwitchLayout): Boolean {
            if (layout == RenderSwitchLayout.SINGLE_CROSS) {
                if (referenceIndex == 2) return mapIndex == 2
                return mapIndex == 0 || mapIndex == 1
            }
            if (layout == RenderSwitchLayout.SCISSORS) {
                return mapIndex == referenceIndex
            }
            return mapIndex == referenceIndex
        }

        private fun detectSwitchLayout(railPositions: Array<RailPosition?>?): RenderSwitchLayout {
            if (railPositions == null) {
                return RenderSwitchLayout.NONE
            }
            val count = railPositions.size
            var switchCount = 0
            for (railPosition in railPositions) {
                if (railPosition == null) {
                    return RenderSwitchLayout.NONE
                }
                if (railPosition.switchType.toInt() == 1) {
                    switchCount++
                }
            }
            if (count == 4 && switchCount == 2) {
                return RenderSwitchLayout.BASIC
            }
            if (count == 6 && switchCount == 4) {
                return RenderSwitchLayout.SINGLE_CROSS
            }
            if (count == 8 && switchCount == 8) {
                return if (hasSameDirectionPair(railPositions)) RenderSwitchLayout.SCISSORS else RenderSwitchLayout.DIAMOND
            }
            if (count == 4 && switchCount == 4) {
                return RenderSwitchLayout.DIAMOND
            }
            return RenderSwitchLayout.NONE
        }

        private fun hasSameDirectionPair(railPositions: Array<RailPosition?>): Boolean {
            for (i in railPositions.indices) {
                val a = railPositions[i]
                if (a == null || a.switchType.toInt() != 1) {
                    continue
                }
                for (j in i + 1 until railPositions.size) {
                    val b = railPositions[j]
                    if (b != null && b.switchType.toInt() == 1 && (a.direction.toInt() and 7) == (b.direction.toInt() and 7)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun getOrCreateSamples(map: RailMap, corePos: BlockPos, sampleMax: Int): Array<RailSample> {
            val key = RailCacheKey(corePos, System.identityHashCode(map), sampleMax)
            return SAMPLE_CACHE.computeIfAbsent(key) {
                Array(sampleMax + 1) { i ->
                    val point = map.getRailPos(sampleMax, i)
                    RailSample(
                        point[1],
                        map.getRailHeight(sampleMax, i),
                        point[0],
                        map.getRailYaw(sampleMax, i),
                        map.getRailPitch(sampleMax, i),
                        map.getCant(sampleMax, i),
                    )
                }
            }
        }

        private fun shouldRenderRailGroup(
            model: MqoModelLoader.MqoModel?,
            groupName: String?,
            pos: Int,
            max: Int,
            compatibilityHeavy: Boolean,
            cameraDistanceSq: Double,
        ): Boolean {
            val lower = groupName?.lowercase(Locale.ROOT) ?: ""
            if (lower.matches(Regex("[lr][0-9]+"))) {
                return false
            }
            if (lower.startsWith("ballast")) {
                val ballastIndex = parseTrailingNumber(lower)
                if (ballastIndex <= 0) {
                    return true
                }
                if (model != null && model.hasGroupNamed("ballast04")) {
                    return ballastIndex == pos % 16 + 1
                }
                if (model != null && model.hasGroupNamed("ballast03")) {
                    if (pos % 10 == 0) {
                        return ballastIndex == 2
                    }
                    if ((pos + 1) % 10 == 0) {
                        return ballastIndex == 3
                    }
                    return ballastIndex == 1
                }
                return ballastIndex == 1
            }
            if (lower.startsWith("sleeper_point")) {
                return false
            }
            if (lower == "ladder") {
                return (pos + 1) % 10 == 0 || (pos + 5) % 10 == 0 || (pos + 9) % 10 == 0
            }
            if (compatibilityHeavy) {
                if (lower.contains("glass") ||
                    lower.contains("alpha") ||
                    lower.contains("window")
                ) {
                    return false
                }
                if (cameraDistanceSq > 2500.0 &&
                    (lower.contains("detail") ||
                        lower.contains("bolt") ||
                        lower.contains("plate") ||
                        lower.contains("side"))
                ) {
                    return false
                }
            }
            val endpoint = pos == 0 || pos == max
            if (lower.contains("end") || lower.contains("cap") || lower.contains("terminal")) {
                return endpoint
            }
            return true
        }

        private fun shouldUseCompatibilityRendering(definition: RailDefinition?, model: MqoModelLoader.MqoModel?): Boolean {
            if (definition == null || model == null) {
                return false
            }
            return model.totalVertexCount >= 9_000 ||
                model.batchCount >= 72 ||
                model.translucentBatchCount >= 10
        }

        private fun parseTrailingNumber(value: String): Int {
            val end = value.length
            var start = end
            while (start > 0 && Character.isDigit(value[start - 1])) {
                start--
            }
            if (start == end) {
                return -1
            }
            return try {
                value.substring(start, end).toInt()
            } catch (e: NumberFormatException) {
                -1
            }
        }
    }
}
