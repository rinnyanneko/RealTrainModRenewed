package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader;
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.MqoModel;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;

public class BogieRenderer {
    static final double BOGIE_VISUAL_LIFT = 0.24D;
    private static final double RTM_BOGIE_RENDER_LIFT = 1.1875D;
    private static final double WORLD_BOGIE_RENDER_LIFT = 0.02D;
    private static final String DEFAULT_CLASS_BOGIE_MODEL = "ModelBogie_ft1.obj";

    public static void renderBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef, 
                                   VehicleDefinition parentDef, TrainEntity entity, MultiBufferSource buffer, int packedLight) {
        renderBogie(poseStack, 0, bogieDef, parentDef, entity, buffer, packedLight, entity != null ? entity.getYRot() : 0.0F, 1.0F);
    }

    public static void renderBogie(PoseStack poseStack, int bogieIndex, VehicleDefinition.BogieDefinition bogieDef,
                                   VehicleDefinition parentDef, TrainEntity entity, MultiBufferSource buffer, int packedLight) {
        renderBogie(poseStack, bogieIndex, bogieDef, parentDef, entity, buffer, packedLight, entity != null ? entity.getYRot() : 0.0F, 1.0F);
    }

    public static void renderBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                   VehicleDefinition parentDef, TrainEntity entity, MultiBufferSource buffer, int packedLight,
                                   float baseYaw) {
        renderBogie(poseStack, 0, bogieDef, parentDef, entity, buffer, packedLight, baseYaw, 1.0F);
    }

    public static void renderBogie(PoseStack poseStack, int bogieIndex, VehicleDefinition.BogieDefinition bogieDef,
                                   VehicleDefinition parentDef, TrainEntity entity, MultiBufferSource buffer, int packedLight,
                                   float baseYaw, float partialTicks) {
        if (entity != null) {
            Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            double distanceSq = cameraPos.distanceToSqr(entity.getX(), entity.getY() + 1.0D, entity.getZ());
            if (distanceSq > 96.0D * 96.0D) {
                return;
            }
        }
        MqoModel bogieModel = loadBogieModel(bogieDef, parentDef);
        if (bogieModel == null) {
            return;
        }

        poseStack.pushPose();
        try {
            Vec3 offset = entity != null ? entity.getBogieRenderOffset(bogieIndex, bogieDef, baseYaw, partialTicks) : bogieDef.position();
            // 本家RTM準拠: 台車は車体と同じレール面基準(getBogieRenderOffset 側で算出済み)へ置く。
            // 本家は func_70033_W()=0 で視覚リフトを持たないため、ここでも一切リフトを足さない
            // (従来の BOGIE_VISUAL_LIFT/-0.05 は車種ごとの浮き・沈みの原因だったので撤去)。
            poseStack.translate(offset.x, offset.y, offset.z);
            if (entity != null) {
                float yawApplied = entity.getBogieYawOffset(bogieIndex, bogieDef, baseYaw, partialTicks);
                poseStack.mulPose(Axis.YP.rotationDegrees(yawApplied));
                float bogiePitch = entity.getBogiePitch(bogieIndex);
                if (Math.abs(bogiePitch) > 0.001F) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(-bogiePitch));
                }
            }
            MqoModelLoader.renderModel(bogieModel, poseStack, buffer, packedLight, entity);
        } finally {
            poseStack.popPose();
        }
    }

    public static void renderStandaloneBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                             VehicleDefinition parentDef, MultiBufferSource buffer, int packedLight,
                                             float yaw, float pitch, float partialTicks) {
        renderStandaloneBogie(poseStack, bogieDef, parentDef, buffer, packedLight, yaw, pitch, partialTicks, 0.0D, 0.0D, 0.0D);
    }

    public static void renderWorldBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                        VehicleDefinition parentDef, MultiBufferSource buffer, int packedLight,
                                        float yaw, float pitch, float partialTicks) {
        renderWorldBogie(poseStack, bogieDef, parentDef, buffer, packedLight, yaw, pitch, partialTicks, 0.0D, 0.0D, 0.0D);
    }

    public static double getStandaloneRenderLift(VehicleDefinition parentDef) {
        if (parentDef == null) {
            return 0.0D;
        }
        return (RTM_BOGIE_RENDER_LIFT + BOGIE_VISUAL_LIFT) * parentDef.getModelScale();
    }

    public static void renderStandaloneBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                             VehicleDefinition parentDef, MultiBufferSource buffer, int packedLight,
                                             float yaw, float pitch, float partialTicks,
                                             double visualOffsetX, double visualOffsetY, double visualOffsetZ) {
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
            visualOffsetZ
        );
    }

    public static void renderWorldBogie(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                        VehicleDefinition parentDef, MultiBufferSource buffer, int packedLight,
                                        float yaw, float pitch, float partialTicks,
                                        double visualOffsetX, double visualOffsetY, double visualOffsetZ) {
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
            visualOffsetY + (parentDef != null ? WORLD_BOGIE_RENDER_LIFT * parentDef.getModelScale() : WORLD_BOGIE_RENDER_LIFT),
            visualOffsetZ
        );
    }

    private static void renderBogieModel(PoseStack poseStack, VehicleDefinition.BogieDefinition bogieDef,
                                         VehicleDefinition parentDef, MultiBufferSource buffer, int packedLight,
                                         float yaw, float pitch, float partialTicks,
                                         double visualOffsetX, double visualOffsetY, double visualOffsetZ) {
        MqoModel bogieModel = loadBogieModel(bogieDef, parentDef);
        if (bogieModel == null || parentDef == null) {
            return;
        }

        poseStack.pushPose();
        try {
            float renderYaw = Mth.rotLerp(partialTicks, yaw, yaw);
            poseStack.translate(
                visualOffsetX,
                visualOffsetY,
                visualOffsetZ
            );
            poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw));
            if (Math.abs(pitch) > 0.001F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            }
            poseStack.scale(parentDef.getModelScale(), parentDef.getModelScale(), parentDef.getModelScale());
            MqoModelLoader.renderModel(bogieModel, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    static MqoModel loadBogieModel(VehicleDefinition.BogieDefinition bogieDef, VehicleDefinition parentDef) {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return null;
        }

        String modelFile = bogieDef.modelFile();
        if (isDummyBogieModel(modelFile)) {
            return null;
        }
        Map<String, String> textureOverrides = bogieDef.textureOverrides();
        if (modelFile.toLowerCase(Locale.ROOT).endsWith(".class")) {
            modelFile = DEFAULT_CLASS_BOGIE_MODEL;
            if (textureOverrides == null || textureOverrides.isEmpty()) {
                textureOverrides = Map.of("default", "textures/train/bogie.png");
            }
        }

        MqoModel bogieModel = MqoModelLoader.loadModelForVehiclePart(parentDef, modelFile, textureOverrides, bogieDef.scriptPath());
        if (bogieModel == null) {
            // 台車モデルが見つからない/ロード失敗時は組み込みデフォルト台車にフォールバック。
            // 透明になるよりは何か見えていたほうが利用者の混乱が少ない。
            Map<String, String> fallbackOverrides = textureOverrides;
            if (fallbackOverrides == null || fallbackOverrides.isEmpty()) {
                fallbackOverrides = Map.of("default", "textures/train/bogie.png");
            }
            bogieModel = MqoModelLoader.loadModelForVehiclePart(parentDef, DEFAULT_CLASS_BOGIE_MODEL, fallbackOverrides);
        }
        return bogieModel;
    }

    public static boolean isDummyBogieModel(String modelFile) {
        if (modelFile == null) {
            return true;
        }
        String normalized = modelFile.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = leaf.lastIndexOf('.');
        String stem = dot > 0 ? leaf.substring(0, dot) : leaf;
        return stem.equals("air")
            || stem.equals("none")
            || stem.equals("null")
            || stem.equals("dummy")
            || stem.equals("empty")
            || stem.equals("transparent");
    }
}
