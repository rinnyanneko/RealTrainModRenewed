package cc.mirukuneko.realtrainmodrenewed.registry;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RealTrainModRenewedEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES
        = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, RealTrainModRenewed.MODID);

    public static final Supplier<EntityType<CarEntity>> CAR = ENTITY_TYPES.register(
        "car",
        () -> EntityType.Builder.of(CarEntity::new, MobCategory.MISC)
            .sized(2.0f, 2.0f)
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(key("car"))
    );

    private static ResourceKey<EntityType<?>> key(String path) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, path));
    }
}
