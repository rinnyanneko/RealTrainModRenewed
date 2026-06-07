package jp.kaiz.atsassistmod.controller;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtection;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TrainController {
    private final List<SpeedOrder> speedOrderList = new ArrayList<>();
    private final List<Integer> speedLimit = new ArrayList<>();
    private int maxSpeed = 0;

    private boolean ATO = false;
    private boolean acceleratorControlling = false;
    public final TASCController tascController = new TASCController();
    private boolean brakingControlling = false;

    private TrainProtection tp;
    private TrainProtectionType tpType = TrainProtectionType.NONE;

    private TrainEntity train;

    private Vec3 coordinates;

    private final int savedEntityID;

    private byte controllerNotchA = -1, controllerNotchB = 1;
    private boolean controllerControl = false;
    private boolean emergencyBrake = false;
    private boolean manualDrive = false;

    public static final TrainController NULL = new TrainController();

    public TrainController() {
        this.savedEntityID = -1;
        this.setTrainProtection(TrainProtectionType.NONE);
    }

    public TrainController(TrainEntity train) {
        this.savedEntityID = train.getId();
        this.train = train;
        this.setTrainProtection(TrainProtectionType.NONE);
    }

    /** Keeps the controller pointed at the live train instance for its formation. */
    public void bind(TrainEntity train) {
        this.train = train;
    }

    public TrainEntity getTrain() {
        return this.train;
    }

    public void setEB() {
        this.emergencyBrake = true;
        if (this.train != null) {
            this.train.setNotch(-8);
        }
    }

    public void setManualDrive(boolean manualDrive) {
        this.manualDrive = manualDrive;
    }

    public boolean isManualDrive() {
        return this.manualDrive;
    }

    public void setControllerNotch(byte notch) {
        this.controllerControl = true;
        if (notch > 0) {
            this.controllerNotchA = notch;
            this.controllerNotchB = 1;
        } else if (notch < 0) {
            this.controllerNotchA = 0;
            this.controllerNotchB = notch;
        } else {
            this.controllerNotchA = 0;
            this.controllerNotchB = 1;
        }
    }

    public int getSavedEntityID() {
        return savedEntityID;
    }

    public void addSpeedOrder(SpeedOrder speedOrder) {
        this.speedOrderList.add(speedOrder);
    }

    public void setMaxSpeed(int maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public void removeSpeedLimit() {
        if (!this.speedLimit.isEmpty()) {
            this.speedLimit.remove(0);
        }
    }

    public void removeAllSpeedLimit() {
        if (!this.speedLimit.isEmpty()) {
            this.speedLimit.clear();
        }
    }

    public int getSpeedLimit() {
        return this.speedLimit.stream().mapToInt(v -> v).min().orElse(Integer.MAX_VALUE);
    }

    public int getATOSpeedLimit() {
        int minLimit = this.speedLimit.stream().mapToInt(v -> v).min().orElse(this.maxSpeed);
        return Math.min(this.getTrainProtectionSpeedLimit(), Math.min(minLimit, this.maxSpeed));
    }

    public int getTrainProtectionSpeedLimit() {
        return this.tp.getDisplaySpeed();
    }

    public void enableATO(int speed) {
        this.ATO = true;
        this.maxSpeed = speed;
    }

    public void disableATO() {
        this.ATO = false;
    }

    public boolean isATO() {
        return ATO;
    }

    public void setTrainProtection(TrainProtectionType type) {
        this.tpType = type;
        this.tp = type.newInstance();
    }

    public TrainProtectionType getTrainProtectionType() {
        return this.tp.getType();
    }

    /** Tick processing (server side). */
    public void onUpdate() throws Exception {
        if (train == null) {
            return;
        }

        double movedDistance = this.getMovedDistance();
        float speedH = RtmTrains.speedKmh(train);

        List<Integer> brakeNotch = new ArrayList<>();
        List<Integer> acceleratorNotch = new ArrayList<>();

        List<SpeedOrder> removeList = new ArrayList<>();
        this.speedOrderList.forEach(speedOrder -> {
            if (speedOrder.isEnable()) {
                this.speedLimit.add(speedOrder.getTargetSpeed());
                removeList.add(speedOrder);
            } else {
                speedOrder.moveDistance(movedDistance);
                if (speedOrder.isAutoBrake() || (this.ATO && !this.isManualDrive())) {
                    brakeNotch.add(speedOrder.getNeedNotch(speedH));
                }
            }
        });
        this.speedOrderList.removeAll(removeList);

        if (speedH > this.getSpeedLimit()) {
            float overSpeed = speedH - this.getSpeedLimit();
            if (overSpeed < 5f) {
                brakeNotch.add(-4);
            } else {
                brakeNotch.add(-7);
            }
        } else {
            if (this.ATO && !this.isManualDrive()) {
                if (!this.acceleratorControlling) {
                    if ((this.getATOSpeedLimit() - speedH) > 10) {
                        acceleratorNotch.add(5);
                    } else if (speedH == 0) {
                        acceleratorNotch.add(5);
                    }
                } else if (this.getATOSpeedLimit() - speedH < 2) {
                    acceleratorNotch.add(0);
                }
            }
        }

        this.tascController.changeTargetDistance(movedDistance);
        if (this.tascController.isEnable()) {
            int needNotch = this.tascController.getNeedNotch(speedH);
            if (this.tascController.isBreaking()) {
                this.disableATO();
                if (!this.isManualDrive()) {
                    brakeNotch.add(needNotch);
                }
            }
        }

        this.tp.onTick(train, movedDistance);
        int notch = this.tp.getNotch(speedH);
        brakeNotch.add(notch);

        if (this.emergencyBrake) {
            int notchLevel = this.train.getNotch();
            if (notchLevel != -8) {
                this.emergencyBrake = false;
                this.brakingControlling = false;
            } else {
                return;
            }
        }

        int minBrakeNotch = brakeNotch.stream().mapToInt(v -> v).min().orElse(1);

        if (RtmTrains.rider(this.train) == null) {
            this.controllerNotchA = -1;
            this.controllerNotchB = 1;
            if (this.controllerControl) {
                this.controllerControl = false;
                this.brakingControlling = false;
            }
        } else {
            if (this.controllerControl) {
                minBrakeNotch = Math.min(minBrakeNotch, this.controllerNotchB);
            }
        }

        if (minBrakeNotch > 0) {
            int maxAcceleratorNotch = acceleratorNotch.stream().mapToInt(v -> v).max().orElse(-1);

            if (RtmTrains.rider(this.train) != null) {
                if (this.controllerControl) {
                    maxAcceleratorNotch = Math.max(maxAcceleratorNotch, this.controllerNotchA);
                }
            }

            if (maxAcceleratorNotch < 0) {
                if (this.brakingControlling) {
                    this.brakingControlling = false;
                    this.train.setNotch(0);
                }
            } else if (maxAcceleratorNotch == 0) {
                this.brakingControlling = false;
                if (!this.controllerControl || this.controllerNotchA != 0) {
                    this.acceleratorControlling = false;
                }
                this.train.setNotch(0);
            } else {
                this.brakingControlling = false;
                this.acceleratorControlling = true;
                this.train.setNotch(maxAcceleratorNotch);
            }
        } else if (minBrakeNotch == 0) {
            if (this.tascController.isEnable() && this.tascController.isStopPosition()) {
                this.brakingControlling = false;
                this.ATO = false;
                if (speedH <= 0F) {
                    this.tascController.disable();
                    if (!this.isManualDrive()) {
                        return;
                    }
                }
            }
            this.acceleratorControlling = false;
            this.brakingControlling = false;
            this.train.setNotch(0);
        } else {
            this.acceleratorControlling = false;
            if (this.tascController.isEnable() && this.tascController.isStopPosition()) {
                this.brakingControlling = false;
                this.ATO = false;
                if (speedH <= 0F) {
                    this.tascController.disable();
                    if (!this.isManualDrive()) {
                        return;
                    }
                }
            }
            this.brakingControlling = true;
            this.train.setNotch(minBrakeNotch);
        }

        if (this.tascController.isEnable() && this.tascController.isStopPosition()) {
            this.ATO = false;
            if (this.isManualDrive()) {
                this.tascController.disable();
            }
        }
    }

    private double getMovedDistance() {
        Vec3 now = RtmTrains.pos(train);
        if (this.coordinates == null) {
            this.coordinates = now;
            return 0d;
        }
        Vec3 old = this.coordinates;
        this.coordinates = now;
        // horizontal moved distance (no gradient), matching the original
        double dx = old.x - now.x;
        double dz = old.z - now.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- accessors used by HUD/network sync ----
    public boolean isTASCEnable() { return this.tascController.isEnable(); }
    public boolean isEmergencyBrake() { return this.emergencyBrake; }
    public TrainProtectionType getTpType() { return this.tpType; }
}
