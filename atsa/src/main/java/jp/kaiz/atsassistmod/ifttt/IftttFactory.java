package jp.kaiz.atsassistmod.ifttt;

import jp.kaiz.atsassistmod.ifttt.IFTTTContainer.That;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer.This;

import java.util.List;

/** Creates default IFTTT containers by type id (for the editor screen). */
public final class IftttFactory {
    private IftttFactory() {}

    /** THIS condition type ids, in editor order. */
    public static final List<Integer> THIS_TYPES = List.of(110, 120, 121, 122, 124, 125, 130);
    /** THAT action type ids, in editor order. */
    public static final List<Integer> THAT_TYPES = List.of(210, 211, 212, 213, 221, 223, 230);

    public static IFTTTContainer create(int typeId) {
        return switch (typeId) {
            case 110 -> new This.Minecraft.RedStoneInput();
            case 120 -> new This.RTM.SimpleDetectTrain();
            case 121 -> new This.RTM.Cars();
            case 122 -> new This.RTM.Speed();
            case 124 -> new This.RTM.TrainDataMap();
            case 125 -> new This.RTM.TrainDirection();
            case 130 -> new This.ATSAssist.CrossingObstacleDetection();
            case 210 -> new That.Minecraft.RedStoneOutput();
            case 211 -> new That.Minecraft.PlaySound();
            case 212 -> new That.Minecraft.ExecuteCommand();
            case 213 -> new That.Minecraft.SetBlock();
            case 221 -> new That.RTM.DataMap();
            case 223 -> new That.RTM.TrainSignal();
            case 230 -> new That.ATSAssist.JavaScript();
            default -> null;
        };
    }
}
