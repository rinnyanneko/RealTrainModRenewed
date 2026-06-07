package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cc.mirukuneko.realtrainmodrenewed.client.ScriptClientCompat;
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader;
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import static cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed.MODID;

/**
 * 自動車の描画。列車と同じ MQO パイプライン(MqoModelLoader)を使い、
 * エンティティの vehicleId が指す車両定義のモデル/テクスチャ/レンダースクリプトを描画する。
 * これにより RTM 標準車(CV33 等)や追加パックの車(SuperRailBuilder3 等)が正しく表示される。
 */
@OnlyIn(Dist.CLIENT)
public final class CarRenderer extends EntityRenderer<CarEntity, LegacyEntityRenderState<CarEntity>> {
    private static final Identifier FALLBACK_TEXTURE =
        Identifier.fromNamespaceAndPath(MODID, "textures/car/toyota_prius-phv.png");

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @NotNull
    public Identifier getTextureLocation(@NotNull CarEntity entity) {
        return FALLBACK_TEXTURE;
    }

    @Override
    public LegacyEntityRenderState<CarEntity> createRenderState() {
        return new LegacyEntityRenderState<>();
    }

    @Override
    public void extractRenderState(CarEntity entity, LegacyEntityRenderState<CarEntity> state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.entityYaw = entity.getYRot();
    }

    @Override
    public void submit(LegacyEntityRenderState<CarEntity> state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.entity == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        renderLegacy(state.entity, state.entityYaw, state.partialTick, poseStack, bufferSource, state.lightCoords);
        bufferSource.endBatch();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private void renderLegacy(@NotNull CarEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                              @NotNull MultiBufferSource bufferSource, int packedLight) {
        // SRB のマーカー描画基準(MCWrapper.getPosX → renderPosX)が PoseStack 原点と同じ
        // partialTick で entity 補間位置を出せるよう、現在の partialTick を共有する。
        ScriptClientCompat.currentRenderPartialTick = partialTick;
        VehicleDefinition def = VehicleRegistry.getById(entity.getVehicleId());
        MqoModelLoader.MqoModel model = def != null ? MqoModelLoader.loadModelForVehicle(def) : null;
        if (model != null) {
            poseStack.pushPose();
            try {
                // 車体の向き(EntityのYawは時計回り、GLは反時計回りなので符号反転)。
                poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
                // モデルのオフセット/スケール(列車と同じ定義)。
                poseStack.translate(def.getModelOffset().x, def.getModelOffset().y, def.getModelOffset().z);
                poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());
                // 列車同様 MQO レンダラへ委譲(レンダースクリプト・テクスチャ・半透明2パス処理を再利用)。
                MqoModelLoader.renderModel(model, poseStack, bufferSource, packedLight, null, null, entity);
            } catch (Throwable ignored) {
                // 個別車両の描画失敗で他を巻き込まない。
            } finally {
                poseStack.popPose();
            }
        }
    }
}
