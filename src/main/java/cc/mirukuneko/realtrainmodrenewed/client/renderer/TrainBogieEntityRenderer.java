package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.culling.Frustum;

public final class TrainBogieEntityRenderer extends EntityRenderer<TrainBogieEntity, LegacyEntityRenderState<TrainBogieEntity>> {
    public TrainBogieEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    public Identifier getTextureLocation(TrainBogieEntity entity) {
        return Identifier.withDefaultNamespace("missingno");
    }

    @Override
    public LegacyEntityRenderState<TrainBogieEntity> createRenderState() {
        return new LegacyEntityRenderState<>();
    }

    @Override
    public void extractRenderState(TrainBogieEntity entity, LegacyEntityRenderState<TrainBogieEntity> state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
    }

    @Override
    public boolean shouldRender(TrainBogieEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        if (entity == null || entity.isRemoved()) return false;
        // 台車のフラスタムカリングは AABB が小さすぎて誤って消えやすい。
        // 親列車の AABB を含めた広めの範囲で判定し、見えるはずの台車が
        // 一瞬でも消失するのを防ぐ。
        TrainEntity train = entity.getTrain();
        if (frustum.isVisible(entity.getBoundingBoxForCulling().inflate(2.0D))) {
            return true;
        }
        if (train != null && frustum.isVisible(train.getBoundingBox().inflate(Math.max(3.0D, train.getTrainDistance() + 3.0D), 3.0D, Math.max(3.0D, train.getTrainDistance() + 3.0D)))) {
            return true;
        }
        return false;
    }

    @Override
    public void submit(LegacyEntityRenderState<TrainBogieEntity> state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        // Hitbox-only entity. Visual bogies are rendered from TrainEntityRenderer
        // so they inherit the same carbody transform and only correct their rail height.
    }
}
