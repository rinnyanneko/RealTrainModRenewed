package jp.kaiz.atsassistmod.rtm;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Adapter bridging ATSAssist's old {@code EntityTrainBase} usage onto the real
 * RealTrainModRenewed {@link TrainEntity} API.
 *
 * <p>RTM's own {@code getFormation()}/{@code isControlCar()} are render-script
 * stubs (formation size always 1, control-car always true), so this class uses
 * the genuine formation API instead. See {@code PORTING_NOTES.md}.</p>
 */
public final class RtmTrains {
    private RtmTrains() {}

    /** Speed in blocks/tick, as the old {@code getSpeed()} returned. */
    public static float speed(TrainEntity train) {
        return train.getSpeed();
    }

    /** Speed in km/h (old code multiplied {@code getSpeed()} by 72). */
    public static float speedKmh(TrainEntity train) {
        return train.getSpeed() * 72f;
    }

    /** The head (driving) car of the train's formation; never null. */
    public static TrainEntity head(TrainEntity train) {
        TrainEntity h = train.getFormationHead();
        return h != null ? h : train;
    }

    /** True when {@code train} is the formation's driving/control car. */
    public static boolean isControlCar(TrainEntity train) {
        return head(train) == train;
    }

    /**
     * Stable per-formation key. The old code used {@code Formation.id}; we use the
     * head car's entity id, which is stable while the formation exists.
     */
    public static long formationKey(TrainEntity train) {
        return head(train).getId();
    }

    /** Number of cars in the formation (old {@code getFormation().size()}). */
    public static int formationSize(TrainEntity train) {
        List<TrainEntity> cars = train.getFormationTrainsForDisplay();
        return cars == null || cars.isEmpty() ? 1 : cars.size();
    }

    /** All cars of the formation. */
    public static List<TrainEntity> cars(TrainEntity train) {
        return train.getFormationTrainsForDisplay();
    }

    /** Connected train at end {@code dir} (0/1), or null. */
    public static TrainEntity connected(TrainEntity train, int dir) {
        return train.getConnectedTrain(dir);
    }

    /** Center-to-end distance; replaces {@code getModelSet().getConfig().trainDistance}. */
    public static double trainDistance(TrainEntity train) {
        return train.getTrainDistance();
    }

    public static Vec3 pos(TrainEntity train) {
        return train.position();
    }

    /** The entity controlling/riding this car, or null (old {@code riddenByEntity}). */
    public static Entity rider(TrainEntity train) {
        Entity e = train.getControllingPassenger();
        if (e != null) return e;
        return train.getPassengers().isEmpty() ? null : train.getPassengers().get(0);
    }
}
