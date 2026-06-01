package com.portofino.realtrainmodunofficial.compat.atsassist;

import com.portofino.realtrainmodunofficial.entity.TrainEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AtsaTrainController {
    public static final String KEY_SPEED_LIMIT = "ATSAssist_SpeedLimit";
    public static final String KEY_ATO_SPEED = "ATSAssist_ATOSpeed";
    public static final String KEY_TASC_DISTANCE = "ATSAssist_TASCDistance";
    public static final String KEY_TP = "ATSAssist_CurrentTP";
    public static final String KEY_EB = "ATSAssist_EB";

    private static final Map<UUID, Double> TASC_DISTANCE = new HashMap<>();

    private AtsaTrainController() {
    }

    public static void setSpeedLimit(TrainEntity train, int kmh) {
        train.applyScriptDataSync(Map.of(KEY_SPEED_LIMIT, Integer.toString(Math.max(0, kmh))));
    }

    public static void clearSpeedLimit(TrainEntity train) {
        train.applyScriptDataSync(Map.of(KEY_SPEED_LIMIT, ""));
    }

    public static void enableAto(TrainEntity train, int kmh) {
        train.applyScriptDataSync(Map.of(KEY_ATO_SPEED, Integer.toString(Math.max(0, kmh))));
    }

    public static void disableAto(TrainEntity train) {
        train.applyScriptDataSync(Map.of(KEY_ATO_SPEED, ""));
    }

    public static void setTascDistance(TrainEntity train, double distance) {
        TASC_DISTANCE.put(train.getUUID(), Math.max(0.0D, distance));
        train.applyScriptDataSync(Map.of(KEY_TASC_DISTANCE, Double.toString(Math.max(0.0D, distance))));
    }

    public static void disableTasc(TrainEntity train) {
        TASC_DISTANCE.remove(train.getUUID());
        train.applyScriptDataSync(Map.of(KEY_TASC_DISTANCE, ""));
    }

    public static void setTrainProtection(TrainEntity train, String type) {
        train.applyScriptDataSync(Map.of(KEY_TP, type == null ? "NONE" : type));
    }

    public static void emergencyBrake(TrainEntity train) {
        train.applyScriptDataSync(Map.of(KEY_EB, "true"));
        train.setNotch(-train.getMaxBrakeNotch());
    }

    public static void tick(TrainEntity train) {
        if (train.level().isClientSide()) {
            return;
        }
        int brake = 1;
        float speedKmh = Math.abs(train.getSpeed()) * 72.0F;

        if (Boolean.parseBoolean(train.getScriptDataValue(KEY_EB))) {
            brake = Math.min(brake, -train.getMaxBrakeNotch());
        }

        int speedLimit = parseInt(train.getScriptDataValue(KEY_SPEED_LIMIT), -1);
        if (speedLimit >= 0 && speedKmh > speedLimit) {
            brake = Math.min(brake, speedKmh - speedLimit > 5.0F ? -train.getMaxBrakeNotch() : -Math.max(1, train.getMaxBrakeNotch() - 1));
        }

        int atoSpeed = parseInt(train.getScriptDataValue(KEY_ATO_SPEED), -1);
        if (atoSpeed > 0 && brake > 0) {
            if (speedKmh < atoSpeed - 8.0F) {
                train.setNotch(Math.max(train.getNotch(), Math.min(5, train.getMaxPowerNotch())));
            } else if (speedKmh > atoSpeed) {
                brake = Math.min(brake, -2);
            } else if (speedKmh > atoSpeed - 2.0F) {
                train.setNotch(0);
            }
        }

        double tascDistance = getTascDistance(train);
        if (tascDistance >= 0.0D) {
            double moved = Math.abs(train.getSpeed()) * 20.0D / 72.0D;
            tascDistance = Math.max(0.0D, tascDistance - moved);
            TASC_DISTANCE.put(train.getUUID(), tascDistance);
            train.applyScriptDataSync(Map.of(KEY_TASC_DISTANCE, Double.toString(tascDistance)));
            int tascBrake = getTascNotch(speedKmh, tascDistance, train.getMaxBrakeNotch());
            if (tascBrake < 0) {
                brake = Math.min(brake, tascBrake);
            }
        }

        if (!"NONE".equalsIgnoreCase(train.getScriptDataValue(KEY_TP)) && speedKmh > 130.0F) {
            brake = Math.min(brake, -train.getMaxBrakeNotch());
        }

        if (brake <= 0) {
            train.setNotch(brake);
        }
    }

    private static double getTascDistance(TrainEntity train) {
        Double cached = TASC_DISTANCE.get(train.getUUID());
        if (cached != null) {
            return cached;
        }
        double parsed = parseDouble(train.getScriptDataValue(KEY_TASC_DISTANCE), -1.0D);
        if (parsed >= 0.0D) {
            TASC_DISTANCE.put(train.getUUID(), parsed);
        }
        return parsed;
    }

    private static int getTascNotch(float speedKmh, double distance, int maxBrake) {
        if (distance <= 1.0D) {
            return speedKmh <= 1.0F ? 0 : -maxBrake;
        }
        double deceleration = Math.pow(speedKmh, 2.0D) / (distance * 7.2D) / 3.6D;
        if (deceleration > 1.2D) return -maxBrake;
        if (deceleration > 1.0D) return -Math.max(1, maxBrake - 1);
        if (deceleration > 0.7D) return -Math.max(1, maxBrake - 2);
        return 0;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
