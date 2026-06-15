package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class EntityTypeHelper {
    public static EntityType<TrainEntity> createTrainType() {
        return EntityType.Builder.of(TrainEntity::new, MobCategory.MISC)
            .sized(2.0f, 2.0f).fireImmune().clientTrackingRange(10)
            .build("realtrainmodrenewed:train");
    }

    public static EntityType<TrainBogieEntity> createBogieType() {
        return EntityType.Builder.of(TrainBogieEntity::new, MobCategory.MISC)
            .sized(1.4f, 1.6f).fireImmune().clientTrackingRange(10).updateInterval(1)
            .build("realtrainmodrenewed:train_bogie");
    }

    public static EntityType<TrainSeatEntity> createSeatType() {
        return EntityType.Builder.of(TrainSeatEntity::new, MobCategory.MISC)
            .sized(0.9f, 0.25f).fireImmune().clientTrackingRange(10).updateInterval(1)
            .build("realtrainmodrenewed:train_seat");
    }
}
