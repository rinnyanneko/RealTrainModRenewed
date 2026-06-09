package jp.kaiz.atsassistmod.controller.trainprotection;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;

/**
 * Rn-ATS uses the signal aspect ahead of the train through RTM's legacy
 * {@code getSignal()} compatibility API.
 */
public class RnATSController extends TrainProtection {
    private int limitSpeed;

    @Override
    public void onTick(TrainEntity train, double distance) throws Exception {
        super.onTick(train, distance);
        switch (train.getSignal()) {
            case 1 -> this.limitSpeed = 0;
            case 2 -> this.limitSpeed = 15;
            case 3 -> this.limitSpeed = 25;
            case 4 -> this.limitSpeed = 35;
            case 5 -> this.limitSpeed = 45;
            case 6 -> this.limitSpeed = 55;
            case 7 -> this.limitSpeed = 65;
            case 8 -> this.limitSpeed = 75;
            case 9 -> this.limitSpeed = 85;
            case 10 -> this.limitSpeed = 95;
            case 11 -> this.limitSpeed = 100;
            case 12 -> this.limitSpeed = 110;
            case 13 -> this.limitSpeed = 120;
            case 14 -> this.limitSpeed = 130;
            default -> this.limitSpeed = 25;
        }
    }

    @Override
    public int getNotch(float speedH) {
        float overSpeed = speedH - this.limitSpeed;
        if (overSpeed > 5) {
            return -7;
        } else if (overSpeed > 0 || this.train.getSignal() == 1) {
            return -4;
        } else {
            return 1;
        }
    }

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.RnATS;
    }

    @Override
    public int getDisplaySpeed() {
        return this.limitSpeed;
    }
}
