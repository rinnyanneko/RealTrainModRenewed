package jp.kaiz.atsassistmod.util;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;

/**
 * Mirror of RTM's old {@code TrainState.TrainStateType} indices used by the
 * "Train State Set" ground unit. Values below {@link #min} mean "leave unchanged"
 * (the original GUI seeds the array with sentinels so nothing changes by default).
 *
 * <p>Index → effect follows RealTrainModRenewed's own {@code syncVehicleState}
 * mapping. State_TrainDir has no safe public setter on the new API, so it is left
 * as a no-op (documented approximation; see PORTING_NOTES.md).</p>
 */
public enum TrainStateType {
    State_Reverse(0, 0),
    State_Notch(1, -8),
    State_RailProgress(2, 0),
    /** index 3 is intentionally skipped by the ground unit. */
    State_Unused3(3, Integer.MAX_VALUE),
    State_Door(4, 0),
    State_Light(5, 0),
    State_Pantograph(6, 0),
    State_TrainDir(7, 0),
    State_Destination(8, 0),
    State_Sound(9, 0),
    State_Unused10(10, Integer.MAX_VALUE),
    State_InteriorLight(11, 0);

    public final int id;
    public final int min;

    TrainStateType(int id, int min) {
        this.id = id;
        this.min = min;
    }

    public static TrainStateType byId(int id) {
        for (TrainStateType t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return State_Unused3;
    }

    /** Applies state index {@code id} with {@code value} to the train. */
    public static void apply(TrainEntity train, int id, byte value) {
        switch (id) {
            case 0 -> train.setReverser(value < 0 ? -1 : 1);
            case 1 -> train.setNotch(value);
            case 2 -> train.setRailProgress(value);
            case 4 -> {
                train.setDoorRightOpen((value & 1) != 0);
                train.setDoorLeftOpen((value & 2) != 0);
            }
            case 5 -> train.setLightMode(value);
            case 6 -> train.setPantographUp(value > 0);
            case 8 -> train.setDestinationIndex(Math.max(0, value));
            case 9 -> train.setSoundIndex(Math.max(0, value));
            case 11 -> train.setInteriorLightOn(value > 0);
            // 7 (TrainDir), 3, 10: no safe equivalent — no-op.
            default -> {
            }
        }
    }
}
