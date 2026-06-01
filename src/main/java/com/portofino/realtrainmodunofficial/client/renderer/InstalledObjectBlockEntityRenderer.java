package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstalledObjectBlockEntityRenderer implements BlockEntityRenderer<InstalledObjectBlockEntity> {
    private static final Set<String> GREEN_GROUPS = Set.of("light1", "light2");
    private static final Set<String> YELLOW_GROUPS = Set.of("light3", "light5");
    private static final Set<String> RED_GROUPS = Set.of("light4");
    private static final Set<String> CROSSING_SCRIPT_ONLY_GROUPS = Set.of("light_l", "light_r");
    private static final List<String> CROSSING_LIGHT_LEFT = List.of("light_l", "lightl", "light-left", "lightleft", "lighta", "light_a");
    private static final List<String> CROSSING_LIGHT_RIGHT = List.of("light_r", "lightr", "light-right", "lightright", "lightb", "light_b");
    private static final List<String> CROSSING_LIGHT_LEFT_LEGACY = List.of("light1");
    private static final List<String> CROSSING_LIGHT_RIGHT_LEGACY = List.of("light2");
    private static final List<String> CROSSING_LIGHT_COMMON_LEGACY = List.of("light3");

    public InstalledObjectBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(InstalledObjectBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (definition == null) {
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
        }
        Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = blockEntity.getRenderCenter();
        double cameraDistanceSq = cameraPos.distanceToSqr(center);
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            renderWire(blockEntity, poseStack, buffer, cameraDistanceSq);
        }
        if (definition.getModelFile() != null && !definition.getModelFile().isBlank()) {
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
                definition.getPackName(),
                definition.getModelFile(),
                definition.getTextureOverrides(),
                definition.getScriptPath(),
                definition.isSmoothing()
            );
            if (model != null) {
                boolean compatibilityHeavy = shouldUseCompatibilityRendering(definition, model);
                boolean customCrossingGateRendering = shouldUseCustomCrossingGateRendering(blockEntity, definition);
                double farThreshold = compatibilityHeavy ? 56.0D : 80.0D;
                double veryFarThreshold = compatibilityHeavy ? 96.0D : 140.0D;
                double translucentThreshold = compatibilityHeavy ? 44.0D : 72.0D;
                boolean far = cameraDistanceSq > farThreshold * farThreshold;
                boolean veryFar = cameraDistanceSq > veryFarThreshold * veryFarThreshold;
                poseStack.pushPose();
                poseStack.translate(0.5D, 0.0D, 0.5D);
                Vec3 renderOffset = blockEntity.getRenderOffset();
                poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - blockEntity.getYaw()));
                poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                MqoModelLoader.GroupPredicate filter = (far || compatibilityHeavy || customCrossingGateRendering)
                    ? groupName -> shouldRenderInstalledObjectGroup(groupName, blockEntity, definition, cameraDistanceSq, compatibilityHeavy)
                    : null;
                MqoModelLoader.GroupTransform transform = customCrossingGateRendering
                    ? (stack, groupName) -> applyCrossingGateTransform(stack, blockEntity, groupName)
                    : null;
                if (!customCrossingGateRendering && !veryFar && !compatibilityHeavy && definition.getScriptPath() != null && !definition.getScriptPath().isBlank()) {
                    MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, blockEntity);
                } else {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, filter, transform, blockEntity);
                    if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
                        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, filter, transform, blockEntity);
                    }
                }
                if (!veryFar) {
                    renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                }
                poseStack.popPose();
                ClientRenderProfiler.endInstalledObject(profilerStart);
                return;
            }
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            renderSignboard(blockEntity, definition, poseStack, buffer, packedLight, packedOverlay);
        }
        ClientRenderProfiler.endInstalledObject(profilerStart);
    }

    private void renderWire(InstalledObjectBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer, double cameraDistanceSq) {
        BlockPos start = blockEntity.getWireStart();
        BlockPos end = blockEntity.getWireEnd();
        if (start == null || end == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(blockEntity.getBlockPos());
        Vec3 from = Vec3.atCenterOf(start).add(-center.x, -center.y, -center.z);
        Vec3 to = Vec3.atCenterOf(end).add(-center.x, -center.y, -center.z);
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        int samples = cameraDistanceSq > 14400.0D ? 4 : cameraDistanceSq > 4900.0D ? 8 : 16;
        for (int i = 0; i < samples; i++) {
            double t0 = i / (double) samples;
            double t1 = (i + 1.0D) / samples;
            Vec3 p0 = from.lerp(to, t0).add(0.0D, sag(t0), 0.0D);
            Vec3 p1 = from.lerp(to, t1).add(0.0D, sag(t1), 0.0D);
            LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                Math.min(p0.x, p1.x) - 0.01D,
                Math.min(p0.y, p1.y) - 0.01D,
                Math.min(p0.z, p1.z) - 0.01D,
                Math.max(p0.x, p1.x) + 0.01D,
                Math.max(p0.y, p1.y) + 0.01D,
                Math.max(p0.z, p1.z) + 0.01D,
                0.75F, 0.9F, 1.0F, 0.7F
            );
        }
    }

    private static double sag(double t) {
        return -0.35D * Math.sin(Math.PI * t);
    }

    private static boolean shouldRenderInstalledObjectGroup(String groupName, InstalledObjectBlockEntity blockEntity,
                                                            InstalledObjectDefinition definition, double cameraDistanceSq,
                                                            boolean compatibilityHeavy) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        String normalized = groupName.toLowerCase(java.util.Locale.ROOT);
        if (usesBuiltinCrossingGateLayout(definition) && CROSSING_SCRIPT_ONLY_GROUPS.contains(normalized)) {
            return false;
        }
        if (cameraDistanceSq > 140.0D * 140.0D) {
            if (normalized.contains("detail")
                || normalized.contains("under")
                || normalized.contains("inside")
                || normalized.contains("step")
                || normalized.contains("ladder")
                || normalized.contains("handle")
                || normalized.contains("lever")) {
                return false;
            }
        }
        if (cameraDistanceSq > 80.0D * 80.0D) {
            if (normalized.contains("glass")
                || normalized.contains("alpha")
                || normalized.contains("screen")
                || normalized.contains("panel")) {
                return false;
            }
        }
        if (compatibilityHeavy) {
            if (normalized.contains("glass")
                || normalized.contains("alpha")
                || normalized.contains("window")
                || normalized.contains("screen")
                || normalized.contains("display")) {
                return false;
            }
            if (cameraDistanceSq > 56.0D * 56.0D && (normalized.contains("detail")
                || normalized.contains("cover")
                || normalized.contains("frame")
                || normalized.contains("inside")
                || normalized.contains("back"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldUseCustomCrossingGateRendering(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition) {
        return blockEntity != null
            && definition != null
            && blockEntity.getCategory() == InstalledObjectCategory.CROSSING
            && definition.getScriptPath() != null
            && usesBuiltinCrossingGateLayout(definition);
    }

    private static void applyCrossingGateTransform(PoseStack poseStack, InstalledObjectBlockEntity blockEntity, String groupName) {
        if (blockEntity == null || groupName == null) {
            return;
        }
        String normalized = groupName.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("bar0") && !normalized.equals("bar1")
            && !normalized.equals("bar") && !normalized.equals("bar2")) {
            return;
        }
        CrossingTransform transform = resolveCrossingTransform(blockEntity, normalized);
        if (transform == null) {
            return;
        }
        float move = (float) ((blockEntity.getBarMoveCount() / 90.0F) * transform.degrees());
        poseStack.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ());
        poseStack.mulPose(Axis.ZP.rotationDegrees(move));
        poseStack.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ());
    }

    private static CrossingTransform resolveCrossingTransform(InstalledObjectBlockEntity blockEntity, String groupName) {
        String scriptPath = getCrossingScriptPath(InstalledObjectRegistry.getById(blockEntity.getDefinitionId()));
        boolean turnRight = blockEntity.getModelName().endsWith("R");
        if (scriptPath.contains("hi03rendercrossinggate")) {
            double degrees = turnRight ? 85.0D : -85.0D;
            if ("bar2".equals(groupName)) {
                return new CrossingTransform(-0.5303D, 6.0287D, 0.0D, -degrees);
            }
            return new CrossingTransform(0.0D, 0.9056D, 0.0D, degrees);
        }
        if (scriptPath.contains("masacrossinggate")) {
            double degrees = turnRight ? 90.0D : -90.0D;
            return new CrossingTransform(0.02D, 0.92D, 0.0D, degrees);
        }
        if ("bar0".equals(groupName) || "bar1".equals(groupName)) {
            double degrees = turnRight ? 90.0D : -90.0D;
            return new CrossingTransform(0.0D, 0.5337D, -0.24D, degrees);
        }
        return null;
    }

    private static boolean isSupportedCustomCrossingScript(String scriptPath) {
        String normalized = scriptPath == null ? "" : scriptPath.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("rendercrossinggate")
            || normalized.contains("crossinggate");
    }

    private static boolean usesBuiltinCrossingGateLayout(InstalledObjectDefinition definition) {
        String scriptPath = getCrossingScriptPath(definition);
        return scriptPath.contains("rendercrossinggate01");
    }

    private static String getCrossingScriptPath(InstalledObjectDefinition definition) {
        return definition == null || definition.getScriptPath() == null
            ? ""
            : definition.getScriptPath().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean shouldUseCompatibilityRendering(InstalledObjectDefinition definition, MqoModelLoader.MqoModel model) {
        if (definition == null || model == null) {
            return false;
        }
        boolean hasScript = definition.getScriptPath() != null && !definition.getScriptPath().isBlank();
        return model.getTotalVertexCount() >= 12_000
            || model.getBatchCount() >= 96
            || model.getTranslucentBatchCount() >= 16
            || (hasScript && model.getBatchCount() >= 64);
    }

    private void renderSignboard(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        String signTexture = definition.getSignTexture();
        if (signTexture == null || signTexture.isBlank()) {
            renderSignboardOutline(definition, poseStack, buffer);
            return;
        }
        ResourceLocation texture = MqoModelLoader.resolvePackTexture(definition.getPackName(), signTexture);
        if (texture == null) {
            renderSignboardOutline(definition, poseStack, buffer);
            return;
        }

        double halfWidth = definition.getWidth() * 0.5D;
        double height = definition.getHeight();
        double halfDepth = Math.max(0.02D, definition.getDepth() * 0.5D);
        int frame = definition.getSignFrame();
        int backTex = definition.getBackTexture();
        // RTM: frame>1 の場合、最初の1フレーム分だけ表示 (V: 0 → 1/frame)
        float vMax = frame > 1 ? (1.0F / frame) : 1.0F;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        Vec3 renderOffset = blockEntity.getRenderOffset();
        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - blockEntity.getYaw()));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();

        // 前面
        addSignVertex(consumer, pose, -halfWidth, height, -halfDepth, 0.0F, 0.0F, packedOverlay, packedLight, -1.0F);
        addSignVertex(consumer, pose, -halfWidth, 0.0D, -halfDepth, 0.0F, vMax, packedOverlay, packedLight, -1.0F);
        addSignVertex(consumer, pose, halfWidth, 0.0D, -halfDepth, 1.0F, vMax, packedOverlay, packedLight, -1.0F);
        addSignVertex(consumer, pose, halfWidth, height, -halfDepth, 1.0F, 0.0F, packedOverlay, packedLight, -1.0F);

        // 背面: backTexture=0 の場合は省略
        if (backTex != 0) {
            addSignVertex(consumer, pose, halfWidth, height, halfDepth, 0.0F, 0.0F, packedOverlay, packedLight, 1.0F);
            addSignVertex(consumer, pose, halfWidth, 0.0D, halfDepth, 0.0F, vMax, packedOverlay, packedLight, 1.0F);
            addSignVertex(consumer, pose, -halfWidth, 0.0D, halfDepth, 1.0F, vMax, packedOverlay, packedLight, 1.0F);
            addSignVertex(consumer, pose, -halfWidth, height, halfDepth, 1.0F, 0.0F, packedOverlay, packedLight, 1.0F);
        }
        poseStack.popPose();
    }

    private void renderSignboardOutline(InstalledObjectDefinition definition, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        double halfWidth = definition.getWidth() * 0.5D;
        double height = definition.getHeight();
        double halfDepth = Math.max(0.02D, definition.getDepth() * 0.5D);
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            0.5D - halfWidth, 0.0D, 0.5D - halfDepth,
            0.5D + halfWidth, height, 0.5D + halfDepth,
            1.0F, 0.95F, 0.6F, 0.9F
        );
    }

    private static void addSignVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                      double x, double y, double z, float u, float v,
                                      int packedOverlay, int packedLight, float normalZ) {
        consumer.addVertex(pose.pose(), (float) x, (float) y, (float) z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(0.0F, 0.0F, normalZ);
    }

    private void renderActiveLights(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedOverlay) {
        List<String> groups = resolveActiveLightGroups(blockEntity, definition);
        if (groups.isEmpty()) {
            return;
        }
        MqoModelLoader.MqoModel emissiveModel = MqoModelLoader.loadModelFromPack(
            definition.getPackName(),
            definition.getModelFile(),
            definition.getTextureOverrides(),
            "",
            definition.isSmoothing()
        );
        if (emissiveModel == null) {
            return;
        }
        // RTM signal scripts treat the active lamp groups as a separate emissive pass.
        // We mirror that here so packs light up even when the legacy script only toggles groups.
        for (String group : groups) {
            int[] color = signalColorForGroup(group);
            MqoModelLoader.renderModelColorOverlay(
                emissiveModel,
                poseStack,
                buffer,
                packedOverlay,
                candidate -> groupMatches(candidate, group),
                color[0], color[1], color[2], color[3]
            );
        }
    }

    private static List<String> resolveActiveLightGroups(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition) {
        if (blockEntity == null || definition == null) {
            return List.of();
        }
        if (blockEntity.isSignal()) {
            List<String> groups = definition.getSignalLightGroups().get(blockEntity.getLegacySignalState());
            if (groups == null || groups.isEmpty()) {
                groups = fallbackSignalGroups(blockEntity.getLegacySignalState());
            }
            return groups == null ? List.of() : groups;
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.CROSSING && blockEntity.isPowered()) {
            int state = Math.floorMod(blockEntity.getLightCount(), 2);
            InstalledObjectDefinition crossingDefinition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
            String scriptPath = getCrossingScriptPath(crossingDefinition);
            if (scriptPath.contains("rendercrossinggate01")) {
                return state == 0 ? CROSSING_LIGHT_RIGHT : CROSSING_LIGHT_LEFT;
            }
            java.util.ArrayList<String> groups = new java.util.ArrayList<>();
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT_LEGACY : CROSSING_LIGHT_LEFT_LEGACY);
            if (scriptPath.contains("masacrossing")) {
                groups.addAll(CROSSING_LIGHT_COMMON_LEGACY);
            }
            return groups;
        }
        return List.of();
    }

    private record CrossingTransform(double pivotX, double pivotY, double pivotZ, double degrees) {}

    private static boolean groupMatches(String candidate, String expected) {
        if (candidate == null || expected == null) {
            return false;
        }
        String normalizedCandidate = candidate.toLowerCase(java.util.Locale.ROOT);
        String normalizedExpected = expected.toLowerCase(java.util.Locale.ROOT);
        return normalizedCandidate.equals(normalizedExpected);
    }

    private static List<String> fallbackSignalGroups(int legacyState) {
        return switch (legacyState) {
            case 1 -> List.of("light4");
            case 2 -> List.of("light4", "light3");
            case 3 -> List.of("light3");
            case 4 -> List.of("light3", "light5");
            case 5 -> List.of("light2");
            case 6 -> List.of("light1", "light5");
            case 7 -> List.of("light1", "light2");
            default -> List.of();
        };
    }

    private static int[] signalColorForGroup(String group) {
        String lower = group == null ? "" : group.toLowerCase();
        if (CROSSING_LIGHT_LEFT.contains(lower) || CROSSING_LIGHT_RIGHT.contains(lower)) {
            return new int[] {255, 72, 48, 220};
        }
        if (RED_GROUPS.contains(lower)) {
            return new int[] {255, 56, 32, 218};
        }
        if (YELLOW_GROUPS.contains(lower)) {
            return new int[] {255, 210, 64, 206};
        }
        if (GREEN_GROUPS.contains(lower)) {
            return new int[] {64, 255, 120, 198};
        }
        return new int[] {255, 255, 255, 180};
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(InstalledObjectBlockEntity blockEntity) {
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            Vec3 a = Vec3.atCenterOf(blockEntity.getWireStart());
            Vec3 b = Vec3.atCenterOf(blockEntity.getWireEnd());
            return new AABB(a, b).inflate(2.0D);
        }
        return new AABB(blockEntity.getBlockPos()).inflate(4.0D);
    }

    @Override
    public boolean shouldRenderOffScreen(InstalledObjectBlockEntity blockEntity) {
        return blockEntity.getCategory() == InstalledObjectCategory.WIRE;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
