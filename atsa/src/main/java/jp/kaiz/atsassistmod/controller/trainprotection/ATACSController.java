package jp.kaiz.atsassistmod.controller.trainprotection;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * ATACS (moving block) train protection.
 *
 * <p>The original walked RTM's large-rail graph ahead of the train to measure the
 * gap to the preceding train. RealTrainModRenewed's rail API is entirely
 * different and exposes no equivalent rail-walk, so this port measures the gap by
 * directly scanning for the nearest train ahead and feeds it into the original
 * braking-pattern formulas. The deceleration curves and notch logic are unchanged,
 * so on straight/gently-curved track behaviour closely matches the original; it is
 * an approximation across complex junctions. (See PORTING_NOTES.md.)</p>
 */
public class ATACSController extends TrainProtection {
    /** [display, pattern, emergency] km/h limits. */
    private final int[] speed = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};

    /** How far ahead to look for a preceding train, beyond braking distance. */
    private static final double SEARCH_MARGIN = 100.0D;

    @Override
    public void onTick(TrainEntity train, double distance) throws Exception {
        super.onTick(train, distance);

        double necessaryDistance = this.getBreakingDistance(RtmTrains.speed(train));
        double gap = this.getAnotherTrainDistance(train, necessaryDistance + SEARCH_MARGIN);

        if (gap < 0d) {
            this.speed[0] = Integer.MAX_VALUE;
            this.speed[1] = Integer.MAX_VALUE;
            this.speed[2] = Integer.MAX_VALUE;
        } else {
            this.setPatternSpeed(gap);
        }
    }

    private void setPatternSpeed(double trainDistance) {
        if (trainDistance > 100d) {
            this.speed[0] = (int) this.getPattern(trainDistance - 120d);
            this.speed[1] = (int) this.getPattern(trainDistance - 110d);
            this.speed[2] = (int) this.getPattern(trainDistance - 100d);
        } else {
            this.speed[0] = 0;
            this.speed[1] = 0;
            this.speed[2] = 0;
        }
    }

    public int getDisplaySpeed() {
        return this.speed[0];
    }

    public int getPatternSpeed() {
        return this.speed[1];
    }

    public int getEmergencySpeed() {
        return this.speed[2];
    }

    @Override
    public int getNotch(float speedH) {
        if (speedH > this.getEmergencySpeed()) {
            return -8;
        } else if (speedH > this.getPatternSpeed()) {
            return -7;
        } else if (this.getDisplaySpeed() == 0) {
            return -5;
        } else {
            return 1;
        }
    }

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.ATACS;
    }

    /**
     * Distance (m) to the nearest train ahead of this formation, or -1 if none
     * within {@code searchDistance}. "Ahead" is determined by the head car's
     * facing direction. Subtracts this train's half-length, matching the original.
     */
    private double getAnotherTrainDistance(TrainEntity train, double searchDistance) {
        TrainEntity head = RtmTrains.head(train);
        Vec3 origin = head.position();
        float yaw = head.getYRot();
        // forward unit vector in the XZ plane (Minecraft yaw: 0 = +Z, 90 = -X)
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));

        Level level = head.level();
        AABB box = new AABB(origin, origin).inflate(searchDistance, 8.0D, searchDistance);
        List<TrainEntity> candidates = level.getEntitiesOfClass(TrainEntity.class, box);

        long selfKey = RtmTrains.formationKey(train);
        double best = -1d;
        for (TrainEntity other : candidates) {
            if (other == null || RtmTrains.formationKey(other) == selfKey) {
                continue;
            }
            Vec3 delta = other.position().subtract(origin);
            double along = delta.x * fx + delta.z * fz; // projection onto forward axis
            if (along <= 0d) {
                continue; // behind us
            }
            // reject trains far off to the side (not on our path)
            double lateral = Math.abs(delta.x * fz - delta.z * fx);
            if (lateral > 4.0D) {
                continue;
            }
            if (along <= searchDistance && (best < 0d || along < best)) {
                best = along;
            }
        }

        if (best < 0d) {
            return -1d;
        }
        return Math.max(0d, best - RtmTrains.trainDistance(train));
    }

    private double getPattern(double distance) {
        return Math.sqrt((1.4f * 3.6f) * 7.2f * (distance));
    }

    private double getBreakingDistance(float trainSpeedT) {
        float trainSpeedH = trainSpeedT * 72f + 20f;
        return Math.pow(trainSpeedH, 2) / ((0.8f * 3.6f) * 7.2f);
    }
}
