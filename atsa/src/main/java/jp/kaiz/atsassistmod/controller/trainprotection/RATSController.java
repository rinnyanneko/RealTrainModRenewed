package jp.kaiz.atsassistmod.controller.trainprotection;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;

/**
 * R-ATS uses the signal aspect ahead of the train. RTM's old {@code getSignal()}
 * is a stub in RealTrainModRenewed (returns 0), so until the new signal network
 * is bridged this resolves to "no limit". Behaviour is otherwise faithful.
 */
public class RATSController extends TrainProtection {
    private int limitSpeed;

    @Override
    public void onTick(TrainEntity train, double distance) throws Exception {
        super.onTick(train, distance);
        switch (train.getSignal()) {
            case 2 -> this.limitSpeed = 30;
            case 3 -> this.limitSpeed = 45;
            case 4 -> this.limitSpeed = 65;
            case 5 -> this.limitSpeed = 95;
            default -> this.limitSpeed = Integer.MAX_VALUE;
        }
    }

    @Override
    public int getNotch(float speedH) {
        if (this.limitSpeed == Integer.MAX_VALUE) {
            return 1;
        }
        float overSpeed = speedH - this.limitSpeed;
        if (overSpeed > 5) {
            return -7;
        } else if (overSpeed > 0) {
            return -4;
        } else {
            return 1;
        }
    }

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.RATS;
    }

    @Override
    public int getDisplaySpeed() {
        return this.limitSpeed;
    }
}
