package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.client.ClientRenderProfiler;
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;

public class InstalledObjectBlockEntityRenderer implements BlockEntityRenderer<InstalledObjectBlockEntity, LegacyBlockEntityRenderState<InstalledObjectBlockEntity>> {
    private static final Set<String> GREEN_GROUPS = Set.of("light1", "light2");
    private static final Set<String> YELLOW_GROUPS = Set.of("light3", "light5");
    private static final Set<String> RED_GROUPS = Set.of("light4");
    private static final Set<String> CROSSING_SCRIPT_ONLY_GROUPS = Set.of("light_l", "light_r");
    private static final List<String> CROSSING_LIGHT_LEFT = List.of("light_l", "lightl", "light-left", "lightleft", "lighta", "light_a");
    private static final List<String> CROSSING_LIGHT_RIGHT = List.of("light_r", "lightr", "light-right", "lightright", "lightb", "light_b");
    private static final List<String> CROSSING_LIGHT_LEFT_LEGACY = List.of("light1");
    private static final List<String> CROSSING_LIGHT_RIGHT_LEGACY = List.of("light2");
    private static final List<String> CROSSING_LIGHT_COMMON_LEGACY = List.of("light3");
    private static final Map<String, Long> FAILED_RENDER_UNTIL_NANOS = new ConcurrentHashMap<>();
    /** 診断: 改札のグループ名を定義IDごとに1回だけログするための記録。 */
    private static final Set<String> TICKET_GATE_LOGGED = ConcurrentHashMap.newKeySet();

    public InstalledObjectBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public LegacyBlockEntityRenderState<InstalledObjectBlockEntity> createRenderState() {
        return new LegacyBlockEntityRenderState<>();
    }

    @Override
    public void extractRenderState(InstalledObjectBlockEntity blockEntity,
                                   LegacyBlockEntityRenderState<InstalledObjectBlockEntity> state,
                                   float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(LegacyBlockEntityRenderState<InstalledObjectBlockEntity> state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.blockEntity == null) {
            return;
        }
        MultiBufferSource.BufferSource buffer = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        renderLegacy(state.blockEntity, state.partialTick, poseStack, buffer, state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        buffer.endBatch();
    }

    private void renderLegacy(InstalledObjectBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (definition == null) {
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
        }
        Long failedUntil = FAILED_RENDER_UNTIL_NANOS.get(definition.getId());
        if (failedUntil != null) {
            if (System.nanoTime() < failedUntil) {
                ClientRenderProfiler.endInstalledObject(profilerStart);
                return;
            }
            FAILED_RENDER_UNTIL_NANOS.remove(definition.getId(), failedUntil);
        }
        Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Vec3 center = blockEntity.getRenderCenter();
        double cameraDistanceSq = cameraPos.distanceToSqr(center);
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE) {
            // ワイヤーはケーブル(ジオメトリ)だけを描く。中間に置いた設置物ブロックの定義モデル
            // (鎖/コネクタ)は描画しない(真ん中に余計なモデルが出ないように)。
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                renderWire(blockEntity, definition, poseStack, buffer, cameraDistanceSq, cameraPos, packedLight, packedOverlay);
            }
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
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
                // 診断: 改札の扉グループ名と barMoveCount を1回だけ記録(扉トランスフォームの対象特定用)。
                if (blockEntity.getCategory() == InstalledObjectCategory.TICKET_GATE
                    && TICKET_GATE_LOGGED.add(definition.getId())) {
                    RealTrainModRenewed.LOGGER.debug(
                        "[RTM-DBG] ticketGate id={} barMove={} groups={}",
                        definition.getId(), blockEntity.getBarMoveCount(), model.getAllNormalizedGroupNames());
                }
                boolean pushed = false;
                try {
                    boolean compatibilityHeavy = shouldUseCompatibilityRendering(definition, model);
                    boolean customCrossingGateRendering = shouldUseCustomCrossingGateRendering(blockEntity, definition);
                    double farThreshold = compatibilityHeavy ? 56.0D : 80.0D;
                    double veryFarThreshold = compatibilityHeavy ? 96.0D : 140.0D;
                    double translucentThreshold = compatibilityHeavy ? 44.0D : 72.0D;
                    boolean far = cameraDistanceSq > farThreshold * farThreshold;
                    boolean veryFar = cameraDistanceSq > veryFarThreshold * veryFarThreshold;
                    poseStack.pushPose();
                    pushed = true;
                    poseStack.translate(0.5D, 0.0D, 0.5D);
                    Vec3 renderOffset = blockEntity.getRenderOffset();
                    poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - blockEntity.getYaw()));
                    // 壁挿し碍子は横倒し(mountPitch)にする。0なら通常の縦置き。
                    if (blockEntity.getMountPitch() != 0.0F) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(blockEntity.getMountPitch()));
                    }
                    poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                    poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    MqoModelLoader.GroupPredicate filter = groupName ->
                        shouldRenderDefinedObjectGroup(groupName, definition)
                            && (!(far || compatibilityHeavy || customCrossingGateRendering)
                                || shouldRenderInstalledObjectGroup(groupName, blockEntity, definition, cameraDistanceSq, compatibilityHeavy));
                    boolean ticketGateRendering = blockEntity.getCategory() == InstalledObjectCategory.TICKET_GATE;
                    final MqoModelLoader.MqoModel transformModel = model;
                    MqoModelLoader.GroupTransform transform = customCrossingGateRendering
                        ? (stack, groupName) -> applyCrossingGateTransform(stack, blockEntity, groupName)
                        : (ticketGateRendering
                            ? (stack, groupName) -> applyTicketGateTransform(stack, blockEntity, transformModel, groupName)
                            : null);
                    if (!customCrossingGateRendering && !ticketGateRendering && !veryFar && !compatibilityHeavy && definition.getScriptPath() != null && !definition.getScriptPath().isBlank()) {
                        // 改札(TICKET_GATE)はスクリプト経路を使わない。スクリプト経路は扉の開閉 transform を
                        // 渡さないため、扉が静止位置(=開)のまま「ずっと開いてる」状態になる。transform 付きの
                        // renderModelWithoutScript を通して barMoveCount に応じ扉を閉じる(本家RTM挙動)。
                        MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, blockEntity);
                    } else {
                        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, filter, transform, blockEntity);
                        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
                            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, filter, transform, blockEntity);
                        }
                    }
                    if (!veryFar && shouldRenderSupplementalActiveLights(blockEntity, definition, customCrossingGateRendering)) {
                        renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                    }
                    poseStack.popPose();
                    pushed = false;
                } catch (Throwable t) {
                    if (pushed) {
                        try { poseStack.popPose(); } catch (Throwable ignored) {}
                    }
                    FAILED_RENDER_UNTIL_NANOS.put(definition.getId(), System.nanoTime() + 5_000_000_000L);
                    RealTrainModRenewed.LOGGER.warn(
                        "Skipping installed object render for {} for 5 seconds after renderer failure.",
                        definition.getId(), t);
                }
                ClientRenderProfiler.endInstalledObject(profilerStart);
                return;
            }
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            renderSignboard(blockEntity, definition, poseStack, buffer, packedLight, packedOverlay);
        }
        ClientRenderProfiler.endInstalledObject(profilerStart);
    }

    private void renderWire(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                            PoseStack poseStack, MultiBufferSource buffer,
                            double cameraDistanceSq, Vec3 cameraPos, int packedLight, int packedOverlay) {
        BlockPos start = blockEntity.getWireStart();
        BlockPos end = blockEntity.getWireEnd();
        if (start == null || end == null) {
            return;
        }
        // BlockEntityRenderer の poseStack 原点はブロックの「角(atLowerCornerOf)」なので、
        // 接続点(world)も角基準の相対座標に変換する。中心基準で引くと水平に 0.5 ズレる。
        Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        Vec3 fromWorld = resolveWireAttachPoint(blockEntity.getLevel(), start);
        Vec3 toWorld = resolveWireAttachPoint(blockEntity.getLevel(), end);
        Vec3 from = fromWorld.subtract(origin);
        Vec3 to = toWorld.subtract(origin);

        String wireScript = definition.getScriptPath();
        boolean hasScript = wireScript != null && !wireScript.isBlank();
        String normalizedScript = hasScript ? wireScript.toLowerCase(java.util.Locale.ROOT).replace('\\', '/') : "";
        boolean hasRenderableModel = hasRenderableWireModel(definition);
        MqoModelLoader.MqoModel model = hasRenderableModel
            ? MqoModelLoader.loadModelFromPack(definition.getPackName(), definition.getModelFile(),
                definition.getTextureOverrides(), definition.getScriptPath(), definition.isSmoothing())
            : null;

        if (model != null && renderKnownScriptWireModel(blockEntity, definition, model, from, to,
            normalizedScript, poseStack, buffer, packedLight, packedOverlay)) {
            return;
        }

        // BasicWire / SimpleCatenary / モデル無しは本家の単線系スクリプトとして描く。
        if (hasScript || model == null) {
            renderBasicWireCable(from, to, packedLight, poseStack, buffer);
            return;
        }

        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0e-4) {
            return;
        }
        // NGT Vec3.getYaw/getPitch と同じ式。
        float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float pitch = (float) Math.toDegrees(Math.atan2(d.y, xz));
        float sectionLength = definition.getSectionLength(); // 定義(JSON)の sectionLength を使う(隙間防止)
        int split = Math.max(1, (int) Math.floor(length / sectionLength));
        // 描画負荷の上限(長すぎる電線でセクション数が爆発しないように)。
        split = Math.min(split, 256);
        float scaleY = (float) ((length / (double) split) / sectionLength);

        poseStack.pushPose();
        try {
            poseStack.translate(from.x, from.y, from.z);
            // モデルの +Y 軸を線方向へ向ける(本家と同じ yaw+180 / pitch-90)。
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch - 90.0F));
            poseStack.scale(1.0F, scaleY, 1.0F);
            boolean hasTranslucent = model.hasTranslucentBatches();
            for (int i = 0; i < split; i++) {
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                    false, null, null, blockEntity);
                if (hasTranslucent) {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                        true, null, null, blockEntity);
                }
                poseStack.translate(0.0F, sectionLength, 0.0F);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static boolean hasRenderableWireModel(InstalledObjectDefinition definition) {
        if (definition == null) {
            return false;
        }
        String modelFile = definition.getModelFile();
        if (modelFile == null || modelFile.isBlank()) {
            return false;
        }
        String normalized = modelFile.toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        return !normalized.endsWith("model_none.mqo");
    }

    private static Vec3 resolveWireAttachPoint(Level level, BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity endpoint) {
            InstalledObjectDefinition endpointDef = InstalledObjectRegistry.getById(endpoint.getDefinitionId());
            if (endpointDef != null) {
                // 本家RTM(TileEntityConnectorBase.getWirePos)準拠: 接続点は wirePos(ブロック単位)を
                // ブロック底面中央から積む。X/Z は wirePos、Y は「碍子モデルの実描画上端」を採用する。
                // (パックによっては wirePos.y とモデル上端がズレており、本家設定どおりだと浮く。
                //  実モデルの先端から出すことで、どの碍子でも確実にモデルから直接電線が出る。)
                Vec3 wp = endpointDef.getWireAttachPos();
                double attachY = wp.y;
                MqoModelLoader.MqoModel attachModel = MqoModelLoader.loadModelFromPack(
                    endpointDef.getPackName(), endpointDef.getModelFile(), endpointDef.getTextureOverrides(),
                    endpointDef.getScriptPath(), endpointDef.isSmoothing());
                double modelTop = modelTopY(attachModel);
                if (!Double.isNaN(modelTop)) {
                    attachY = modelTop * endpointDef.getModelScale();
                }
                Vec3 local = new Vec3(wp.x, attachY, wp.z);
                // 描画と同じ順序で回転(まず mountPitch を X、次に yaw を Y)。壁挿し碍子の
                // 取付点もモデルと同じ位置に来るようにする。
                Vec3 tilted = rotateX(local, endpoint.getMountPitch());
                Vec3 rotated = rotateY(tilted, 180.0D - endpoint.getYaw());
                // 碍子モデルは translate(0.5, 0.0, 0.5)(底面中央)で描画されるので、接続点も同じ基準。
                return Vec3.atLowerCornerOf(pos)
                    .add(0.5D, 0.0D, 0.5D)
                    .add(endpoint.getRenderOffset())
                    .add(rotated);
            }
        }
        return Vec3.atCenterOf(pos);
    }

    private static Vec3 rotateY(Vec3 vec, double degrees) {
        if (vec == null || vec.equals(Vec3.ZERO)) {
            return Vec3.ZERO;
        }
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos + vec.z * sin, vec.y, vec.z * cos - vec.x * sin);
    }

    // Axis.XP.rotationDegrees と同じ +X 軸まわりの右手回転(描画と接続点を一致させる用)。
    private static Vec3 rotateX(Vec3 vec, double degrees) {
        if (degrees == 0.0D || vec == null || vec.equals(Vec3.ZERO)) {
            return vec == null ? Vec3.ZERO : vec;
        }
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x, vec.y * cos - vec.z * sin, vec.y * sin + vec.z * cos);
    }

    private boolean renderKnownScriptWireModel(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                               MqoModelLoader.MqoModel model, Vec3 from, Vec3 to, String script,
                                               PoseStack poseStack, MultiBufferSource buffer,
                                               int packedLight, int packedOverlay) {
        if (script == null || script.isBlank()) {
            return false;
        }
        if (script.endsWith("wire51/renderbeam1.js")) {
            renderWire51Beam(blockEntity, definition, model, from, to, poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        if (script.endsWith("wire51/renderwire.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 10.0D, "obj1",
                poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        if (script.endsWith("wire51/renderbracket.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 3.0D, "obj1",
                poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        if (script.endsWith("wire51/renderbracketd.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 4.0D, "obj1",
                poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        return false;
    }

    private void renderWire51Beam(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                  MqoModelLoader.MqoModel model, Vec3 from, Vec3 to, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0e-4D) {
            return;
        }

        int maxPos = Math.max(1, (int) Math.floor(length / 2.0D) * 2);
        maxPos = Math.min(maxPos, 256);
        double move = length / (double) maxPos;
        float scale = (float) move;
        int halfMaxPos = maxPos / 2;

        poseStack.pushPose();
        try {
            applyZWireOrientation(poseStack, from, d);
            poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
            for (int i = 0; i < maxPos; i++) {
                String group;
                double offsetZ = 0.0D;
                if (i == 0) {
                    group = "BeamR1";
                    offsetZ = 2.0D;
                } else if (i < halfMaxPos) {
                    group = "BeamR2";
                    offsetZ = 1.0D;
                } else if (i < maxPos - 1) {
                    group = "BeamL2";
                } else {
                    group = "BeamL1";
                    offsetZ = -1.0D;
                }

                poseStack.pushPose();
                poseStack.translate(0.0D, 0.0D, move * i + offsetZ);
                poseStack.scale(1.0F, 1.0F, scale);
                renderWireModelGroup(model, poseStack, buffer, packedLight, packedOverlay, blockEntity, group);
                poseStack.popPose();
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderScaledZWireModel(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                        MqoModelLoader.MqoModel model, Vec3 from, Vec3 to, double baseLength,
                                        String groupName, PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, int packedOverlay) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0e-4D || baseLength <= 0.0D) {
            return;
        }

        // 本家Wire51の描画スクリプト(renderWire.js 等)を忠実移植:
        //   rate = length / baseLength;            // wire=10, bracket=3, bracketD=4
        //   rotate(yaw,'Y'); rotate(-pitch,'X');   // applyZWireOrientation
        //   glScalef(1, 1, rate);                  // +Z(線方向)のみを電線長へ伸ばす
        //   wire.render();                         // 1回だけ(タイルしない)
        // Catenary1 は +Z 軸長 1000(×0.01=10ブロック)で作られているため、Z を rate 倍すれば
        // 碍子から碍子へ正しい太さ・たるみ(Y方向 -0.81)で張られる。
        float rate = (float) (length / baseLength);

        poseStack.pushPose();
        try {
            applyZWireOrientation(poseStack, from, d);
            float modelScale = definition.getModelScale();
            poseStack.scale(modelScale, modelScale, modelScale);
            poseStack.scale(1.0F, 1.0F, rate);
            renderWireModelGroup(model, poseStack, buffer, packedLight, packedOverlay, blockEntity, groupName);
        } finally {
            poseStack.popPose();
        }
    }

    private static void applyZWireOrientation(PoseStack poseStack, Vec3 from, Vec3 d) {
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        // 本家Wire51スクリプト(renderWire/renderBeam/renderBracket)準拠:
        //   yaw = vec.getYaw(); pit = -vec.getPitch();
        //   rotate(yaw,'Y'); rotate(pit,'X');
        // モデルは +Z 軸が線方向に作られている(Catenary1 dZ=1000≒10ブロック)。
        float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        float pitch = (float) Math.toDegrees(Math.atan2(d.y, xz));
        poseStack.translate(from.x, from.y, from.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
    }

    private static void renderWireModelGroup(MqoModelLoader.MqoModel model, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight, int packedOverlay,
                                             InstalledObjectBlockEntity blockEntity, String groupName) {
        MqoModelLoader.GroupPredicate filter = groupName == null || groupName.isBlank()
            ? null
            : candidate -> groupMatches(candidate, groupName);
        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
            false, filter, null, blockEntity);
        if (model.hasTranslucentBatches()) {
            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                true, filter, null, blockEntity);
        }
    }

    /**
     * 本家RTM の基本ワイヤー(RenderBasicWire.js renderWireDynamic)を忠実再現したケーブル描画。
     * XZ面リボン(ワイヤー色)と Y面リボン(黒)の十字を、たるみ式 fh=((j-8)/16)^2-0.25)*1.5 で描く。
     * 1本の TRIANGLE_STRIP(leash) に縮退頂点で2リボンをつないで描画する。
     */
    private void renderBasicWireCable(Vec3 from, Vec3 to, int packedLight, PoseStack poseStack, MultiBufferSource buffer) {
        double x = to.x - from.x;
        double y = to.y - from.y;
        double z = to.z - from.z;
        double hor = Math.sqrt(x * x + z * z);
        if (hor < 1.0e-6) hor = 1.0e-6;
        double x1 = x / hor, z1 = z / hor;
        final int split = 16;
        final double w = 0.025D;
        VertexConsumer c = buffer.getBuffer(RenderTypes.leash());
        Matrix4f mat = poseStack.last().pose();
        // XZ リボン色(暗灰)/ Y リボン色(黒)。RTM は XZ=ワイヤー色, Y=0(黒)。
        final int xr = 26, xg = 26, xb = 26;
        final int yr = 6, yg = 6, yb = 6;
        // --- XZ 面リボン ---
        float lastX = 0, lastY = 0, lastZ = 0;
        for (int j = 0; j <= split; j++) {
            double ft = j / (double) split;
            double f2 = (j - 8.0) / split;
            double fh = (f2 * f2 - 0.25) * 1.5;
            double px = from.x + x * ft, py = from.y + y * ft + fh, pz = from.z + z * ft;
            c.addVertex(mat, (float) (px - w * z1), (float) py, (float) (pz + w * x1))
                .setColor(xr, xg, xb, 255).setLight(packedLight);
            lastX = (float) (px + w * z1); lastY = (float) py; lastZ = (float) (pz - w * x1);
            c.addVertex(mat, lastX, lastY, lastZ).setColor(xr, xg, xb, 255).setLight(packedLight);
        }
        // --- 縮退ブリッジ(XZ最後の頂点 → Y最初の頂点)で strip を分離 ---
        double fh0 = (((0 - 8.0) / split) * ((0 - 8.0) / split) - 0.25) * 1.5;
        float firstYx = (float) from.x, firstYy = (float) (from.y + fh0 + w), firstYz = (float) from.z;
        c.addVertex(mat, lastX, lastY, lastZ).setColor(yr, yg, yb, 255).setLight(packedLight);
        c.addVertex(mat, firstYx, firstYy, firstYz).setColor(yr, yg, yb, 255).setLight(packedLight);
        // --- Y 面リボン ---
        for (int j = 0; j <= split; j++) {
            double ft = j / (double) split;
            double f2 = (j - 8.0) / split;
            double fh = (f2 * f2 - 0.25) * 1.5;
            double px = from.x + x * ft, py = from.y + y * ft + fh, pz = from.z + z * ft;
            c.addVertex(mat, (float) px, (float) (py + w), (float) pz).setColor(yr, yg, yb, 255).setLight(packedLight);
            c.addVertex(mat, (float) px, (float) (py - w), (float) pz).setColor(yr, yg, yb, 255).setLight(packedLight);
        }
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

    /**
     * 改札(TICKET_GATE)の扉(doorL/doorR)を、閉時にヒンジ周りで回して通路を塞ぐ。
     * 本家RTM: モデル静止位置=開、閉(canThrough=false)で扉を回転。RTMUは barMoveCount を
     * 開度(0=閉, 90=開)として使い、closedness=1-(barMoveCount/90) で扉を閉じる。
     */
    private static void applyTicketGateTransform(PoseStack poseStack, InstalledObjectBlockEntity blockEntity,
                                                 MqoModelLoader.MqoModel model, String groupName) {
        if (blockEntity == null || model == null || groupName == null) {
            return;
        }
        String n = groupName.toLowerCase(java.util.Locale.ROOT);
        if (!n.contains("door")) {
            return;
        }
        // モデルの静止位置=閉(扉が通路を塞ぐ)。ICカード等で powered になり barMoveCount が増えると
        // 扉が開く。openness=bar/90 (0=閉=静止, 1=全開=回転)。以前は closedness で駆動していたため
        // 開閉が反転し「既定で開きっぱなし」「通れる時に閉じる」状態だった(ユーザー報告)。
        float openness = Mth.clamp(blockEntity.getBarMoveCount() / 90.0F, 0.0F, 1.0F);
        if (openness <= 0.001F) {
            return; // 閉=モデル静止位置のまま(扉が通路を塞ぐ)
        }
        float[] b = groupBounds(model, groupName);
        if (b == null) {
            return;
        }
        // doorL を +90°・doorR を -90° 回して通路脇へ退避(開)。ヒンジは扉の外側X端・前側Z端。
        // (回転方向はユーザー指摘により反転)
        boolean left = n.endsWith("l") || n.contains("doorl") || n.contains("door_l");
        double hingeX = left ? b[0] : b[3];   // 外側X端
        double hingeZ = b[2];                 // 前側Z端
        float angle = openness * (left ? 90.0F : -90.0F);
        poseStack.translate(hingeX, 0.0D, hingeZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-hingeX, 0.0D, -hingeZ);
    }

    /** group(s) のモデル座標 AABB {minX,minY,minZ,maxX,maxY,maxZ}。取得できなければ null。 */
    // 碍子モデルの実描画上端Y(ベイク座標=×0.01適用済み)。電線をモデル先端から出すために使う。
    private static double modelTopY(MqoModelLoader.MqoModel model) {
        if (model == null) {
            return Double.NaN;
        }
        java.util.Set<String> groups = model.getAllNormalizedGroupNames();
        if (groups == null || groups.isEmpty()) {
            return Double.NaN;
        }
        java.util.List<float[]> quads = model.getGroupQuadCorners(groups);
        if (quads == null || quads.isEmpty()) {
            return Double.NaN;
        }
        double maxY = -Double.MAX_VALUE;
        for (float[] q : quads) {
            for (int c = 0; c < 4; c++) {
                maxY = Math.max(maxY, q[c * 3 + 1]);
            }
        }
        return maxY == -Double.MAX_VALUE ? Double.NaN : maxY;
    }

    private static float[] groupBounds(MqoModelLoader.MqoModel model, String groupName) {
        java.util.List<float[]> quads = model.getGroupQuadCorners(java.util.Set.of(groupName));
        if (quads == null || quads.isEmpty()) {
            return null;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] q : quads) {
            for (int c = 0; c < 4; c++) {
                float x = q[c * 3], y = q[c * 3 + 1], z = q[c * 3 + 2];
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
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

    private static boolean shouldRenderDefinedObjectGroup(String groupName, InstalledObjectDefinition definition) {
        if (definition == null || definition.getRenderObjects().isEmpty()) {
            return true;
        }
        for (String expected : definition.getRenderObjects()) {
            if (groupMatches(groupName, expected)) {
                return true;
            }
        }
        return false;
    }

    private void renderSignboard(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        String signTexture = definition.getSignTexture();
        if (signTexture == null || signTexture.isBlank()) {
            renderSignboardOutline(definition, poseStack, buffer);
            return;
        }
        Identifier texture = MqoModelLoader.resolvePackTexture(definition.getPackName(), signTexture);
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
        VertexConsumer consumer = buffer.getBuffer(RenderTypes.entityCutout(texture));
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
        VertexConsumer consumer = buffer.getBuffer(RenderTypes.lines());
        double halfWidth = definition.getWidth() * 0.5D;
        double height = definition.getHeight();
        double halfDepth = Math.max(0.02D, definition.getDepth() * 0.5D);
        renderLineBox(
            poseStack, consumer,
            0.5D - halfWidth, 0.0D, 0.5D - halfDepth,
            0.5D + halfWidth, height, 0.5D + halfDepth,
            1.0F, 0.95F, 0.6F, 0.9F);
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer consumer,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        line(consumer, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(consumer, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        line(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        line(consumer, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        consumer.addVertex(pose.pose(), (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose.pose(), (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(0.0F, 1.0F, 0.0F);
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

    private static boolean shouldRenderSupplementalActiveLights(InstalledObjectBlockEntity blockEntity,
                                                                InstalledObjectDefinition definition,
                                                                boolean customCrossingGateRendering) {
        if (blockEntity == null || definition == null) {
            return false;
        }
        if (customCrossingGateRendering) {
            return true;
        }
        // 踏切の警報灯は、スクリプト付きパックでも本体描画側で発光オーバーレイを出す。
        // 本家RTMの踏切スクリプトは pass2(全光量)で警報灯を交互描画するが、腕スクリプト等が
        // pass2 を出さない/RTMUのpass最適化で省かれると点灯しないため、ここで確実に発光させる
        // (resolveActiveLightGroups が getLightCount に応じて light1/light2 を交互+light3 を返す)。
        return true;
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
            // 本家スクリプト準拠: light=0 → light2+light3、light=1 → light1+light3 を点灯。
            // light3(common)は両状態で常時点灯させる(モデルに無ければ無視される)。
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT_LEGACY : CROSSING_LIGHT_LEFT_LEGACY);
            groups.addAll(CROSSING_LIGHT_COMMON_LEGACY);
            // 近代命名(light_l/light_r)のパックにも対応。
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT : CROSSING_LIGHT_LEFT);
            return groups;
        }
        // 照明(LIGHT): レッドストーンで電力が入っている間、定義された発光パーツを全て点灯する。
        // パックは信号と同じ "lights": ["S(1) P(部品名)", ...] 形式で発光部を定義する。
        if (blockEntity.getCategory() == InstalledObjectCategory.LIGHT && blockEntity.isPowered()) {
            java.util.List<String> lit = new java.util.ArrayList<>();
            for (List<String> group : definition.getSignalLightGroups().values()) {
                if (group != null) lit.addAll(group);
            }
            return lit;
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
        if (normalizedCandidate.equals(normalizedExpected)) {
            return true;
        }
        String compactCandidate = normalizedCandidate.replace("_", "").replace("-", "");
        String compactExpected = normalizedExpected.replace("_", "").replace("-", "");
        return compactCandidate.equals(compactExpected);
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
        if (CROSSING_LIGHT_LEFT.contains(lower) || CROSSING_LIGHT_RIGHT.contains(lower)
            || CROSSING_LIGHT_LEFT_LEGACY.contains(lower) || CROSSING_LIGHT_RIGHT_LEGACY.contains(lower)
            || CROSSING_LIGHT_COMMON_LEGACY.contains(lower)) {
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
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
