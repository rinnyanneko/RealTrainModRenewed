package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.rail.util.RailMap;
import com.portofino.realtrainmodunofficial.rail.util.RailPosition;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RailCoreBlockEntityRenderer implements BlockEntityRenderer<LargeRailCoreBlockEntity> {
    private record RailSample(double x, double y, double z, float yaw, float pitch, float roll) {}
    private record RailCacheKey(BlockPos corePos, int mapId, int sampleMax) {}
    private static final Map<RailCacheKey, RailSample[]> SAMPLE_CACHE = new ConcurrentHashMap<>();
    /**
     * RTM本家の RailPartsRendererBase#createRailPos と同じ length*2 を基準にする。
     * Baru系は pos 番号で枕木・バラスト周期を決めるため、ここを増やすと部品間隔が詰まって壊れる。
     */
    private static int computeRailSampleMax(RailMap map, double length, RailDefinition definition, double cameraDistanceSq) {
        // RTM本家 RailPartsRendererBase#createRailPos と同じ length*2 固定。
        // 枕木・バラスト・レールの間隔は視点距離で変えない (ユーザー要望「視界でレールの間隔が
        // 変わる、変えないで」)。以前はカメラ距離で density / cap を下げて LOD していたが、
        // 近づくと枕木の数が変わって見えていた。距離非依存の固定密度・固定キャップにする。
        double density = 2.0D;
        int samples = Math.max(2, (int) Math.ceil(length * density));
        if (length < 2.5D) {
            samples = Math.min(samples, 12);
        }
        return Math.min(samples, 768);
    }

/**
     * 直線のみ: Baru 等は全サンプルでレールを描くため Z-fighting しやすい。極小の Y オフセットで深度を分散。
     * 曲線ではベジェと整合したサンプル間隔のためオフセットしない。
     */
    private static float depthJitter(int pos) {
        return ((pos & 15) - 7.5f) * 1.2e-6f;
    }

    private static int computeRenderStride(double cameraDistanceSq, boolean compatibilityHeavy) {
        // 枕木・バラストの周期が視点で変わらないことを優先する。
        return 1;
    }

    private enum RenderSwitchLayout {
        NONE,
        BASIC,
        SINGLE_CROSS,
        SCISSORS,
        DIAMOND
    }

    public RailCoreBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(LargeRailCoreBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        try {
            if (!be.isLoaded()) return;
            RailDefinition def = RailRegistry.getById(be.getRailDefinitionId());
            if (def == null) def = RailRegistry.getSelected();
            if (def == null) return;
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForRail(def);
            if (model == null) return;
            boolean compatibilityHeavy = shouldUseCompatibilityRendering(def, model);
            RailMap[] maps = be.getAllRailMaps();
            if (maps.length == 0) return;
            net.minecraft.world.phys.Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double cameraDistanceSq = cameraPos.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5);

            BlockPos origin = be.getBlockPos();
            double ox = origin.getX();
            double oy = origin.getY();
            double oz = origin.getZ();
            net.minecraft.world.phys.Vec3 mo = def.getModelOffset();
            float scale = def.getModelScale();

            if (maps.length > 1) {
                RenderSwitchLayout layout = detectSwitchLayout(be.getRailPositions());
                int activeIndex = Mth.clamp(be.getActiveSegmentIndex(), 0, maps.length - 1);
                int previousIndex = Mth.clamp(be.getPreviousSegmentIndex(), 0, maps.length - 1);
                float switchProgress = be.getSwitchProgress(partialTick);
                for (int mapIndex = 0; mapIndex < maps.length; mapIndex++) {
                    RailMap map = maps[mapIndex];
                    if (map != null) {
                        renderRailMap(be, map, mapIndex, layout, activeIndex, previousIndex, switchProgress,
                            poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy);
                    }
                }
                return;
            }

            int activeIndex = Mth.clamp(be.getActiveSegmentIndex(), 0, maps.length - 1);
            RailMap activeMap = maps[activeIndex];
            if (activeMap != null) {
                renderRailMap(be, activeMap, activeIndex, RenderSwitchLayout.NONE, activeIndex, activeIndex, 1.0F,
                    poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy);
            }
        } catch (Throwable t) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("Skipping rail render at {} after renderer failure", be.getBlockPos(), t);
        } finally {
            ClientRenderProfiler.endRail(profilerStart);
        }
    }

    private void renderRailMap(
        LargeRailCoreBlockEntity blockEntity,
        RailMap map,
        int mapIndex,
        RenderSwitchLayout layout,
        int activeIndex,
        int previousIndex,
        float switchProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        RailDefinition definition,
        double cameraDistanceSq,
        boolean compatibilityHeavy
    ) {
        double length = map.getLength();
        if (length < 1.0e-4) {
            return;
        }

        int max = computeRailSampleMax(map, length, definition, cameraDistanceSq);
        RailSample[] samples = getOrCreateSamples(map, new BlockPos((int) ox, (int) oy, (int) oz), max);
        int stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy);
        int[] clip = computeSwitchClip(map, mapIndex, layout, activeIndex, previousIndex, switchProgress, max);
        int startIndex = Math.min(Math.max(0, clip[0]), samples.length - 1);
        int endIndex = Math.max(startIndex, samples.length - 1 - Math.max(0, clip[1]));
        for (int i = startIndex; i <= endIndex; i += stride) {
            RailSample sample = samples[i];
            renderRailSample(
                blockEntity,
                sample.x,
                sample.y,
                sample.z,
                sample.yaw,
                sample.pitch,
                sample.roll,
                i,
                max,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                mo,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy
            );
        }
        if (endIndex > startIndex && (endIndex - startIndex) % stride != 0) {
            RailSample sample = samples[endIndex];
            renderRailSample(
                blockEntity,
                sample.x,
                sample.y,
                sample.z,
                sample.yaw,
                sample.pitch,
                sample.roll,
                endIndex,
                max,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                mo,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy
            );
        }
    }

    /**
     * RTM 互換: 分岐の非アクティブ方向をレールの「分岐点側」から徐々に切り詰めて
     * 「割れている」見た目を作る。
     * activeIndex / previousIndex / switchProgress で smoothstep 補間する。
     *
     * 戻り値は {clipFromStart, clipFromEnd} で、サンプル番号で何個分カットするかを表す。
     * pos=0 を分岐点側と仮定し、非アクティブ側は clipFromStart を増やしてレールの根本を隠す。
     */
    private static int[] computeSwitchClip(RailMap map, int mapIndex, RenderSwitchLayout layout,
                                           int activeIndex, int previousIndex, float switchProgress, int sampleMax) {
        if (layout == RenderSwitchLayout.NONE || sampleMax <= 0) {
            return new int[]{0, 0};
        }
        // DIAMOND は単純な交差で、両方向とも常に有効
        if (layout == RenderSwitchLayout.DIAMOND) {
            return new int[]{0, 0};
        }

        boolean active = isMapActiveForLayout(mapIndex, activeIndex, layout);
        boolean previouslyActive = isMapActiveForLayout(mapIndex, previousIndex, layout);

        float t = Mth.clamp(switchProgress, 0.0F, 1.0F);
        t = t * t * (3.0F - 2.0F * t); // smoothstep

        float clipRatio;
        if (active && previouslyActive) {
            clipRatio = 0.0F;
        } else if (active) {
            // 切り替わって有効になる: 切り詰め量 1→0
            clipRatio = 1.0F - t;
        } else if (previouslyActive) {
            // 有効から無効になる: 切り詰め量 0→1
            clipRatio = t;
        } else {
            clipRatio = 1.0F;
        }

        // 最大 70% まで切り詰め、根本付近で「割れて」見えるようにする
        int maxClip = Math.max(1, sampleMax * 7 / 10);
        int clipStart = Math.round(maxClip * clipRatio);
        return new int[]{clipStart, 0};
    }

    private static boolean isMapActiveForLayout(int mapIndex, int referenceIndex, RenderSwitchLayout layout) {
        if (layout == RenderSwitchLayout.SINGLE_CROSS) {
            // 0,1 = 通常 (シングルクロスのストレート), 2 = 渡り
            if (referenceIndex == 2) return mapIndex == 2;
            return mapIndex == 0 || mapIndex == 1;
        }
        if (layout == RenderSwitchLayout.SCISSORS) {
            // 信号 ON でストレート区間が有効、OFF で対角区間が有効。
            // referenceIndex は LargeRailCoreBlockEntity で計算された active 番号なので
            // そのままピンポイント比較する。
            return mapIndex == referenceIndex;
        }
        return mapIndex == referenceIndex;
    }

    private static RenderSwitchLayout detectSwitchLayout(RailPosition[] railPositions) {
        if (railPositions == null) {
            return RenderSwitchLayout.NONE;
        }
        int count = railPositions.length;
        int switchCount = 0;
        for (RailPosition rp : railPositions) {
            if (rp == null) {
                return RenderSwitchLayout.NONE;
            }
            if (rp.switchType == 1) {
                switchCount++;
            }
        }
        if (count == 4 && switchCount == 2) {
            return RenderSwitchLayout.BASIC;
        }
        if (count == 6 && switchCount == 4) {
            return RenderSwitchLayout.SINGLE_CROSS;
        }
        if (count == 8 && switchCount == 8) {
            return hasSameDirectionPair(railPositions) ? RenderSwitchLayout.SCISSORS : RenderSwitchLayout.DIAMOND;
        }
        if (count == 4 && switchCount == 4) {
            return RenderSwitchLayout.DIAMOND;
        }
        return RenderSwitchLayout.NONE;
    }

    private static boolean hasSameDirectionPair(RailPosition[] railPositions) {
        for (int i = 0; i < railPositions.length; i++) {
            RailPosition a = railPositions[i];
            if (a == null || a.switchType != 1) {
                continue;
            }
            for (int j = i + 1; j < railPositions.length; j++) {
                RailPosition b = railPositions[j];
                if (b != null && b.switchType == 1 && (a.direction & 7) == (b.direction & 7)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RailSample[] getOrCreateSamples(RailMap map, BlockPos corePos, int sampleMax) {
        RailCacheKey key = new RailCacheKey(corePos, System.identityHashCode(map), sampleMax);
        return SAMPLE_CACHE.computeIfAbsent(key, unused -> {
            RailSample[] samples = new RailSample[sampleMax + 1];
            for (int i = 0; i <= sampleMax; i++) {
                double[] point = map.getRailPos(sampleMax, i);
                samples[i] = new RailSample(
                    point[1],
                    map.getRailHeight(sampleMax, i),
                    point[0],
                    map.getRailYaw(sampleMax, i),
                    map.getRailPitch(sampleMax, i),
                    map.getCant(sampleMax, i)
                );
            }
            return samples;
        });
    }

    private void renderInterpolatedMap(
        LargeRailCoreBlockEntity blockEntity,
        RailMap previousMap,
        RailMap activeMap,
        float progress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        RailDefinition definition,
        double cameraDistanceSq,
        boolean compatibilityHeavy
    ) {
        int previousMax = computeRailSampleMax(previousMap, previousMap.getLength(), definition, cameraDistanceSq);
        int activeMax = computeRailSampleMax(activeMap, activeMap.getLength(), definition, cameraDistanceSq);
        int max = Math.max(previousMax, activeMax);
        int stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy);
        for (int i = 0; i <= max; i += stride) {
            float t = max <= 0 ? 0.0F : i / (float) max;
            int previousIndex = Mth.clamp(Math.round(t * previousMax), 0, previousMax);
            int activeIndex = Mth.clamp(Math.round(t * activeMax), 0, activeMax);
            double[] previousPoint = previousMap.getRailPos(previousMax, previousIndex);
            double[] activePoint = activeMap.getRailPos(activeMax, activeIndex);
            double wx = Mth.lerp(progress, previousPoint[1], activePoint[1]);
            double wy = Mth.lerp(progress, previousMap.getRailHeight(previousMax, previousIndex), activeMap.getRailHeight(activeMax, activeIndex));
            double wz = Mth.lerp(progress, previousPoint[0], activePoint[0]);
            float yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousIndex), activeMap.getRailYaw(activeMax, activeIndex));
            float pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousIndex), activeMap.getRailPitch(activeMax, activeIndex));
            float roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousIndex), activeMap.getCant(activeMax, activeIndex));
            renderRailSample(blockEntity, wx, wy, wz, yaw, pitch, roll, i, max, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, cameraDistanceSq, compatibilityHeavy);
        }
        if (max > 0 && max % stride != 0) {
            double[] previousPoint = previousMap.getRailPos(previousMax, previousMax);
            double[] activePoint = activeMap.getRailPos(activeMax, activeMax);
            double wx = Mth.lerp(progress, previousPoint[1], activePoint[1]);
            double wy = Mth.lerp(progress, previousMap.getRailHeight(previousMax, previousMax), activeMap.getRailHeight(activeMax, activeMax));
            double wz = Mth.lerp(progress, previousPoint[0], activePoint[0]);
            float yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousMax), activeMap.getRailYaw(activeMax, activeMax));
            float pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousMax), activeMap.getRailPitch(activeMax, activeMax));
            float roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousMax), activeMap.getCant(activeMax, activeMax));
            renderRailSample(blockEntity, wx, wy, wz, yaw, pitch, roll, max, max, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, cameraDistanceSq, compatibilityHeavy);
        }
    }

    private void renderRailSample(
        LargeRailCoreBlockEntity blockEntity,
        double wx,
        double wy,
        double wz,
        float yaw,
        float pitch,
        float roll,
        int pos,
        int max,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        double cameraDistanceSq,
        boolean compatibilityHeavy
    ) {
        poseStack.pushPose();
        float yBump = depthJitter(pos);
        poseStack.translate(wx - ox, wy - oy - 0.0625 + yBump, wz - oz);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(mo.x, mo.y, mo.z);
        poseStack.scale(scale, scale, scale);
        MqoModelLoader.renderModelWithoutScript(
            model,
            poseStack,
            buffer,
            packedLight,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            false,
            groupName -> shouldRenderRailGroup(model, groupName, pos, max, compatibilityHeavy, cameraDistanceSq),
            null
        );
        double translucentThreshold = compatibilityHeavy ? 38.0D : 72.0D;
        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
            MqoModelLoader.renderModelWithoutScript(
                model,
                poseStack,
                buffer,
                packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                true,
                groupName -> shouldRenderRailGroup(model, groupName, pos, max, compatibilityHeavy, cameraDistanceSq),
                null
            );
        }
        poseStack.popPose();
    }

    private static boolean shouldRenderRailGroup(MqoModelLoader.MqoModel model, String groupName, int pos, int max,
                                                 boolean compatibilityHeavy, double cameraDistanceSq) {
        String lower = groupName == null ? "" : groupName.toLowerCase(java.util.Locale.ROOT);
        if (lower.matches("[lr][0-9]+")) {
            return false;
        }
        if (lower.startsWith("ballast")) {
            int ballastIndex = parseTrailingNumber(lower);
            if (ballastIndex <= 0) {
                return true;
            }
            if (model != null && model.hasGroupNamed("ballast04")) {
                return ballastIndex == (pos % 16) + 1;
            }
            if (model != null && model.hasGroupNamed("ballast03")) {
                if (pos % 10 == 0) {
                    return ballastIndex == 2;
                }
                if ((pos + 1) % 10 == 0) {
                    return ballastIndex == 3;
                }
                return ballastIndex == 1;
            }
            return ballastIndex == 1;
        }
        if (lower.startsWith("sleeper_point")) {
            return false;
        }
        if (lower.equals("ladder")) {
            return (pos + 1) % 10 == 0 || (pos + 5) % 10 == 0 || (pos + 9) % 10 == 0;
        }
        if (compatibilityHeavy) {
            if (lower.contains("glass")
                || lower.contains("alpha")
                || lower.contains("window")) {
                return false;
            }
            if (cameraDistanceSq > 2500.0D && (lower.contains("detail")
                || lower.contains("bolt")
                || lower.contains("plate")
                || lower.contains("side"))) {
                return false;
            }
        }
        boolean endpoint = pos == 0 || pos == max;
        // 一部パックは end/cap 名で端面パーツを持つ。中間で出すと長い先端が飛び出す。
        if (lower.contains("end") || lower.contains("cap") || lower.contains("terminal")) {
            return endpoint;
        }
        return true;
    }

    private static boolean shouldUseCompatibilityRendering(RailDefinition definition, MqoModelLoader.MqoModel model) {
        if (definition == null || model == null) {
            return false;
        }
        return model.getTotalVertexCount() >= 9_000
            || model.getBatchCount() >= 72
            || model.getTranslucentBatchCount() >= 10;
    }

    private static int parseTrailingNumber(String value) {
        int end = value.length();
        int start = end;
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return -1;
        }
        try {
            return Integer.parseInt(value.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(LargeRailCoreBlockEntity be) {
        return be.getCachedRenderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(LargeRailCoreBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
