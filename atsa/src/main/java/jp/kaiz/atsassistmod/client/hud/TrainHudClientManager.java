package jp.kaiz.atsassistmod.client.hud;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.rtm.RtmTrains;

import java.util.HashMap;
import java.util.Map;

/** Client store of per-formation HUD state, keyed like the server (head entity id). */
public final class TrainHudClientManager {
    private static final Map<Long, TrainHudClient> MAP = new HashMap<>();

    private TrainHudClientManager() {}

    public static void set(long formationId, boolean ato, boolean tasc, int tpType, int atoSpeed,
                           int tascDistance, int atcSpeed, int tpLimit, boolean manual) {
        MAP.computeIfAbsent(formationId, k -> new TrainHudClient())
                .set(ato, tasc, tpType, atoSpeed, tascDistance, atcSpeed, tpLimit, manual);
    }

    public static TrainHudClient get(TrainEntity train) {
        if (train == null) {
            return null;
        }
        return MAP.get(RtmTrains.formationKey(train));
    }

    public static TrainHudClient getOrCreate(TrainEntity train) {
        if (train == null) {
            return null;
        }
        return MAP.computeIfAbsent(RtmTrains.formationKey(train), k -> new TrainHudClient());
    }

    public static void remove(long formationId) {
        MAP.remove(formationId);
    }
}
