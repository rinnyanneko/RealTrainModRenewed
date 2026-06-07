package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public final class TrainSeatEntityRenderer extends EntityRenderer<TrainSeatEntity, LegacyEntityRenderState<TrainSeatEntity>> {
    private static final Identifier EMPTY_TEXTURE =
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/misc/empty.png");

    public TrainSeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public Identifier getTextureLocation(TrainSeatEntity entity) {
        return EMPTY_TEXTURE;
    }

    @Override
    public LegacyEntityRenderState<TrainSeatEntity> createRenderState() {
        return new LegacyEntityRenderState<>();
    }

    @Override
    public void extractRenderState(TrainSeatEntity entity, LegacyEntityRenderState<TrainSeatEntity> state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
    }

    @Override
    public boolean shouldRender(TrainSeatEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return false;
    }
}
