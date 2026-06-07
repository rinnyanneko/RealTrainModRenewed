package jp.kaiz.atsassistmod.controller;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.network.ATSAModNet;
import jp.kaiz.atsassistmod.network.payload.HudPayload;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks one {@link TrainController} per formation, keyed by the head car's entity
 * id (the old code keyed by {@code Formation.id}, which RTM no longer exposes; see
 * {@code RtmTrains#formationKey}). Driven once per server tick.
 */
public final class TrainControllerManager {
    private static final Map<Long, TrainController> trackingTrainMap = new HashMap<>();

    private TrainControllerManager() {}

    public static TrainController getTrainController(TrainEntity train) {
        if (train == null) {
            return TrainController.NULL;
        }
        long key = RtmTrains.formationKey(train);
        TrainController controller = trackingTrainMap.computeIfAbsent(key, k -> new TrainController(RtmTrains.head(train)));
        controller.bind(RtmTrains.head(train));
        return controller;
    }

    /** Server-side: advance every tracked controller; drop stale ones; sync HUD. */
    public static void onTick(MinecraftServer server) {
        if (trackingTrainMap.isEmpty()) {
            return;
        }
        List<Long> delList = new ArrayList<>();
        trackingTrainMap.forEach((key, controller) -> {
            TrainEntity train = controller.getTrain();
            if (train == null || train.isRemoved() || !RtmTrains.isControlCar(train)
                    || RtmTrains.formationKey(train) != key) {
                delList.add(key);
                return;
            }
            try {
                controller.onUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ATSAModNet.broadcastHud(server, toHud(key, controller));
        });
        delList.forEach(key -> {
            ATSAModNet.broadcastHud(server, HudPayload.remove(key));
            trackingTrainMap.remove(key);
        });
    }

    private static HudPayload toHud(long key, TrainController c) {
        return new HudPayload(1, key,
                c.isATO(), c.tascController.isEnable(), c.getTrainProtectionType().id,
                c.getATOSpeedLimit(), (int) c.tascController.getStopDistance(),
                c.getSpeedLimit(), c.getTrainProtectionSpeedLimit(), c.isManualDrive());
    }

    public static TrainController find(long key) {
        return trackingTrainMap.get(key);
    }

    public static void clear() {
        trackingTrainMap.clear();
    }
}
