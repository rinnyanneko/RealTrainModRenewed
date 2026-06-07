package jp.kaiz.atsassistmod.block;

/**
 * Ground-unit variants. The original used block metadata 0-15; on 1.21 the variant
 * is stored in the {@link jp.kaiz.atsassistmod.block.GroundUnitBlock#TYPE} blockstate
 * property and the {@code GroundUnitBlockEntity} branches on it.
 */
public enum GroundUnitType {
    None(0),
    ATC_SpeedLimit_Notice(1),
    ATC_SpeedLimit_Cancel(2),
    ATC_SpeedLimit_Reset(3),
    TASC_StopPotion_Notice(4),
    TASC_Cancel(5),
    TASC_StopPotion_Correction(6),
    TASC_StopPotion(7),
    ATO_Departure_Signal(9),
    ATO_Cancel(10),
    ATO_Change_Speed(11),
    TrainState_Set(13),
    CHANGE_TP(14),
    ATACS_Disable(15);

    public final int id;

    GroundUnitType(int id) {
        this.id = id;
    }

    public static GroundUnitType getType(int id) {
        for (GroundUnitType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return None;
    }
}
