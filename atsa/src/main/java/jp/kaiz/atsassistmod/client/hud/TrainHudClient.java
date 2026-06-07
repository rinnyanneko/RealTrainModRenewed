package jp.kaiz.atsassistmod.client.hud;

import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;

/** Client-side mirror of a formation's controller state (was {@code TrainControllerClient}). */
public class TrainHudClient {
    private int atoSpeed, tascDistance, atcSpeed, tpLimit, tpType;
    private boolean ato, tasc, manual;
    private boolean notShowHud;

    public void set(boolean ato, boolean tasc, int tpType, int atoSpeed, int tascDistance,
                    int atcSpeed, int tpLimit, boolean manual) {
        this.ato = ato;
        this.tasc = tasc;
        this.tpType = tpType;
        this.atoSpeed = atoSpeed;
        this.tascDistance = tascDistance;
        this.atcSpeed = atcSpeed;
        this.tpLimit = tpLimit;
        this.manual = manual;
    }

    public boolean isATO() { return ato; }
    public boolean isTASC() { return tasc; }
    public void setATO(boolean b) { this.ato = b; }
    public void setTASC(boolean b) { this.tasc = b; }
    public boolean isATACS() { return tpType == TrainProtectionType.ATACS.id; }
    public TrainProtectionType getTrainProtectionType() { return TrainProtectionType.getType(tpType); }
    public void setTrainProtectionType(TrainProtectionType type) { this.tpType = type.id; }
    public int getATOSpeed() { return atoSpeed; }
    public int getTASCDistance() { return tascDistance; }
    public int getATCSpeed() { return atcSpeed; }
    public int getTrainProtectionSpeed() { return tpLimit; }
    public boolean isManualDrive() { return manual; }
    public boolean isNotShowHud() { return notShowHud; }
    public void setNotShowHud(boolean v) { this.notShowHud = v; }
}
