package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RealTrainModRenewedEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RealTrainModRenewed.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TrainEntity>> TRAIN =
        ENTITIES.register("train",
            () -> EntityType.Builder.<TrainEntity>of(TrainEntity::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .fireImmune()
                .clientTrackingRange(10)
                .build(key("train")));

    public static final DeferredHolder<EntityType<?>, EntityType<TrainBogieEntity>> TRAIN_BOGIE =
        ENTITIES.register("train_bogie",
            () -> EntityType.Builder.<TrainBogieEntity>of(TrainBogieEntity::new, MobCategory.MISC)
                .sized(1.4F, 1.6F)
                .fireImmune()
                .clientTrackingRange(10)
                .updateInterval(1)
                .build(key("train_bogie")));

    public static final DeferredHolder<EntityType<?>, EntityType<TrainSeatEntity>> TRAIN_SEAT =
        ENTITIES.register("train_seat",
            () -> EntityType.Builder.<TrainSeatEntity>of(TrainSeatEntity::new, MobCategory.MISC)
                .sized(0.9F, 0.25F)
                .fireImmune()
                .clientTrackingRange(10)
                .updateInterval(1)
                .build(key("train_seat")));

    private RealTrainModRenewedEntities() {
    }

    private static ResourceKey<EntityType<?>> key(String path) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, path));
    }
}
