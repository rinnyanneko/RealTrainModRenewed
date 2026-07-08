// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.MqoModel
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.util.Mth
import java.util.Locale

open class BogieRenderer {
    companion object {
    const val BOGIE_VISUAL_LIFT: Double = 0.24
    private const val RTM_BOGIE_RENDER_LIFT = 1.1875
    private const val WORLD_BOGIE_RENDER_LIFT = 0.02
    private const val DEFAULT_CLASS_BOGIE_MODEL = "ModelBogie_ft1.obj"

    @JvmStatic
    fun renderBogie(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        entity: TrainEntity?,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        renderBogie(poseStack, 0, bogieDef, parentDef, entity, buffer, packedLight, entity?.yRot ?: 0.0f, 1.0f)
    }

    @JvmStatic
    fun renderBogie(
        poseStack: PoseStack,
        bogieIndex: Int,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        entity: TrainEntity?,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        renderBogie(poseStack, bogieIndex, bogieDef, parentDef, entity, buffer, packedLight, entity?.yRot ?: 0.0f, 1.0f)
    }

    @JvmStatic
    fun renderBogie(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        entity: TrainEntity?,
        buffer: MultiBufferSource,
        packedLight: Int,
        baseYaw: Float,
    ) {
        renderBogie(poseStack, 0, bogieDef, parentDef, entity, buffer, packedLight, baseYaw, 1.0f)
    }

    @JvmStatic
    fun renderBogie(
        poseStack: PoseStack,
        bogieIndex: Int,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        entity: TrainEntity?,
        buffer: MultiBufferSource,
        packedLight: Int,
        baseYaw: Float,
        partialTicks: Float,
    ) {
        if (entity != null) {
            val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position()
            val distanceSq = cameraPos.distanceToSqr(entity.x, entity.y + 1.0, entity.z)
            if (distanceSq > 96.0 * 96.0) {
                return
            }
        }
        val bogieModel = loadBogieModel(bogieDef, parentDef) ?: return

        poseStack.pushPose()
        try {
            val offset = if (entity != null) {
                entity.getBogieRenderOffset(bogieIndex, bogieDef, baseYaw, partialTicks)
            } else {
                bogieDef!!.position()
            }
            poseStack.translate(offset.x, offset.y, offset.z)
            if (entity != null) {
                val yawApplied = entity.getBogieYawOffset(bogieIndex, bogieDef, baseYaw, partialTicks)
                poseStack.mulPose(Axis.YP.rotationDegrees(yawApplied))
                val bogiePitch = entity.getBogiePitch(bogieIndex)
                if (kotlin.math.abs(bogiePitch) > 0.001f) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(-bogiePitch))
                }
            }
            MqoModelLoader.renderModel(bogieModel, poseStack, buffer, packedLight, entity)
        } finally {
            poseStack.popPose()
        }
    }

    @JvmStatic
    fun renderStandaloneBogie(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        buffer: MultiBufferSource,
        packedLight: Int,
        yaw: Float,
        pitch: Float,
        partialTicks: Float,
    ) {
        renderStandaloneBogie(poseStack, bogieDef, parentDef, buffer, packedLight, yaw, pitch, partialTicks, 0.0, 0.0, 0.0)
    }

    @JvmStatic
    fun renderWorldBogie(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        buffer: MultiBufferSource,
        packedLight: Int,
        yaw: Float,
        pitch: Float,
        partialTicks: Float,
    ) {
        renderWorldBogie(poseStack, bogieDef, parentDef, buffer, packedLight, yaw, pitch, partialTicks, 0.0, 0.0, 0.0)
    }

    @JvmStatic
    fun getStandaloneRenderLift(parentDef: VehicleDefinition?): Double {
        if (parentDef == null) {
            return 0.0
        }
        return (RTM_BOGIE_RENDER_LIFT + BOGIE_VISUAL_LIFT) * parentDef.getModelScale()
    }

    @JvmStatic
    fun renderStandaloneBogie(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        buffer: MultiBufferSource,
        packedLight: Int,
        yaw: Float,
        pitch: Float,
        partialTicks: Float,
        visualOffsetX: Double,
        visualOffsetY: Double,
        visualOffsetZ: Double,
    ) {
        renderBogieModel(
            poseStack,
            bogieDef,
            parentDef,
            buffer,
            packedLight,
            yaw,
            pitch,
            partialTicks,
            visualOffsetX,
            visualOffsetY + getStandaloneRenderLift(parentDef),
            visualOffsetZ,
        )
    }

    @JvmStatic
    fun renderWorldBogie(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        buffer: MultiBufferSource,
        packedLight: Int,
        yaw: Float,
        pitch: Float,
        partialTicks: Float,
        visualOffsetX: Double,
        visualOffsetY: Double,
        visualOffsetZ: Double,
    ) {
        renderBogieModel(
            poseStack,
            bogieDef,
            parentDef,
            buffer,
            packedLight,
            yaw,
            pitch,
            partialTicks,
            visualOffsetX,
            visualOffsetY + if (parentDef != null) WORLD_BOGIE_RENDER_LIFT * parentDef.getModelScale() else WORLD_BOGIE_RENDER_LIFT,
            visualOffsetZ,
        )
    }

    private fun renderBogieModel(
        poseStack: PoseStack,
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
        buffer: MultiBufferSource,
        packedLight: Int,
        yaw: Float,
        pitch: Float,
        partialTicks: Float,
        visualOffsetX: Double,
        visualOffsetY: Double,
        visualOffsetZ: Double,
    ) {
        val bogieModel = loadBogieModel(bogieDef, parentDef)
        if (bogieModel == null || parentDef == null) {
            return
        }

        poseStack.pushPose()
        try {
            val renderYaw = Mth.rotLerp(partialTicks, yaw, yaw)
            poseStack.translate(visualOffsetX, visualOffsetY, visualOffsetZ)
            poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw))
            if (kotlin.math.abs(pitch) > 0.001f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-pitch))
            }
            poseStack.scale(parentDef.getModelScale(), parentDef.getModelScale(), parentDef.getModelScale())
            MqoModelLoader.renderModel(bogieModel, poseStack, buffer, packedLight)
        } finally {
            poseStack.popPose()
        }
    }

    @JvmStatic
    fun loadBogieModel(
        bogieDef: VehicleDefinition.BogieDefinition?,
        parentDef: VehicleDefinition?,
    ): MqoModel? {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return null
        }

        var modelFile = bogieDef.modelFile()
        if (isDummyBogieModel(modelFile)) {
            return null
        }
        var textureOverrides = bogieDef.textureOverrides()
        if (modelFile.lowercase(Locale.ROOT).endsWith(".class")) {
            modelFile = DEFAULT_CLASS_BOGIE_MODEL
            if (textureOverrides == null || textureOverrides.isEmpty()) {
                textureOverrides = mapOf("default" to "textures/train/bogie.png")
            }
        }

        var bogieModel = MqoModelLoader.loadModelForVehiclePart(parentDef, modelFile, textureOverrides, bogieDef.scriptPath())
        if (bogieModel == null) {
            var fallbackOverrides = textureOverrides
            if (fallbackOverrides == null || fallbackOverrides.isEmpty()) {
                fallbackOverrides = mapOf("default" to "textures/train/bogie.png")
            }
            bogieModel = MqoModelLoader.loadModelForVehiclePart(parentDef, DEFAULT_CLASS_BOGIE_MODEL, fallbackOverrides)
        }
        return bogieModel
    }

    @JvmStatic
    fun isDummyBogieModel(modelFile: String?): Boolean {
        if (modelFile == null) {
            return true
        }
        val normalized = modelFile.replace('\\', '/').trim().lowercase(Locale.ROOT)
        val slash = normalized.lastIndexOf('/')
        val leaf = if (slash >= 0) normalized.substring(slash + 1) else normalized
        val dot = leaf.lastIndexOf('.')
        val stem = if (dot > 0) leaf.substring(0, dot) else leaf
        return stem == "air" ||
            stem == "none" ||
            stem == "null" ||
            stem == "dummy" ||
            stem == "empty" ||
            stem == "transparent"
    }
    }
}
