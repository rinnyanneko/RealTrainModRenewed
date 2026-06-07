package jp.kaiz.atsassistmod.controller.trainprotection;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;

public class TrainProtection {
    protected TrainEntity train;

    public void onTick(TrainEntity train, double distance) throws Exception {
        this.train = train;
    }

    public int getNotch(float speedH) {
        return 1;
    }

    public TrainProtectionType getType() {
        return TrainProtectionType.NONE;
    }

    public int getDisplaySpeed() {
        return Integer.MAX_VALUE;
    }
}
