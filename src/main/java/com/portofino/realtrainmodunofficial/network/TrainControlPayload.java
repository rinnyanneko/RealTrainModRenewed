package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record TrainControlPayload(int trainEntityId, String action, int value) implements CustomPacketPayload {

    public static final Type<TrainControlPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "train_control")
    );

    public static final StreamCodec<ByteBuf, TrainControlPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        TrainControlPayload::trainEntityId,
        ByteBufCodecs.STRING_UTF8,
        TrainControlPayload::action,
        ByteBufCodecs.INT,
        TrainControlPayload::value,
        TrainControlPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(TrainControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getEntity(payload.trainEntityId()) instanceof TrainEntity train)) {
                RealTrainModUnofficial.LOGGER.info("Train control ignored: train {} not found for action {}", payload.trainEntityId(), payload.action());
                return;
            }
            TrainEntity sourceTrain = train;
            if (player.getVehicle() instanceof TrainEntity ridden && ridden.isAlive()) {
                TrainEntity riddenHead = ridden.getFormationHead();
                TrainEntity requestedHead = train.getFormationHead();
                if (ridden == train || riddenHead == requestedHead) {
                    sourceTrain = ridden;
                }
            } else if (player.getVehicle() instanceof TrainSeatEntity seat) {
                TrainEntity seatedTrain = seat.getTrain();
                if (seatedTrain != null && seatedTrain.isAlive()) {
                    TrainEntity riddenHead = seatedTrain.getFormationHead();
                    TrainEntity requestedHead = train.getFormationHead();
                    if (seatedTrain == train || riddenHead == requestedHead) {
                        sourceTrain = seatedTrain;
                    }
                }
            }
            TrainEntity controlTrain = sourceTrain.getFormationHead();
            boolean sameFormationRide =
                (player.getVehicle() instanceof TrainEntity ridden
                    && ridden.isAlive()
                    && ridden.getFormationHead() == controlTrain)
                || (player.getVehicle() instanceof TrainSeatEntity seat
                    && seat.getTrain() != null
                    && seat.getTrain().isAlive()
                    && seat.getTrain().getFormationHead() == controlTrain);
            boolean assignedSeat = controlTrain.formationHasAssignedSeat(player.getUUID());
            boolean driverPassenger = sourceTrain.isDriverPassenger(player) || train.isDriverPassenger(player);
            boolean dismountAction = "dismount".equals(payload.action());
            if (!dismountAction && !sameFormationRide && !assignedSeat && !driverPassenger) {
                RealTrainModUnofficial.LOGGER.info(
                    "Train control ignored: player={} action={} requestedTrain={} sourceTrain={} sameFormationRide={} assignedSeat={}",
                    player.getName().getString(),
                    payload.action(),
                    train.getVehicleId(),
                    sourceTrain.getVehicleId(),
                    sameFormationRide,
                    assignedSeat
                );
                return;
            }
            if (!dismountAction && driverPassenger) {
                controlTrain.markDriverControl(player);
                sourceTrain.markDriverControl(player);
            }
            RealTrainModUnofficial.LOGGER.info(
                "Train control accepted: player={} action={} train={} head={} notch={} reverser={}",
                player.getName().getString(),
                payload.action(),
                sourceTrain.getVehicleId(),
                controlTrain.getVehicleId(),
                controlTrain.getNotch(),
                controlTrain.getReverser()
            );

            switch (payload.action()) {
                case "mascon_power" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    sourceTrain.ensureDriverReady(player);
                    controlTrain.ensureDriverReady(player);
                    controlTrain.stepMascon(1);
                }
                case "mascon_brake" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    sourceTrain.ensureDriverReady(player);
                    controlTrain.ensureDriverReady(player);
                    controlTrain.stepMascon(-1);
                }
                case "mascon_neutral" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    controlTrain.setNotch(0);
                }
                case "dismount" -> {
                    player.stopRiding();
                    controlTrain.clearSeatAssignment(player.getUUID());
                    sourceTrain.clearSeatAssignment(player.getUUID());
                }
                case "toggle_headlight" -> controlTrain.setHeadlightOn(!controlTrain.isHeadlightOn());
                case "set_light_mode" -> controlTrain.setLightModeForFormation(payload.value());
                case "toggle_interior_light" -> controlTrain.setInteriorLightOnForFormation(!controlTrain.isInteriorLightOn());
                case "toggle_door" -> controlTrain.toggleDoorForFormation();
                case "toggle_door_left" -> controlTrain.toggleDoorSideForFormation(true);
                case "toggle_door_right" -> controlTrain.toggleDoorSideForFormation(false);
                case "toggle_pantograph" -> controlTrain.setPantographUpForFormation(!controlTrain.isPantographUp());
                case "toggle_reverse" -> controlTrain.setReverse(!controlTrain.isReverse());
                case "set_reverser" -> controlTrain.setReverser(payload.value());
                case "next_destination" -> {
                    int count = Math.max(1, controlTrain.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                    controlTrain.setDestinationIndexForFormation((controlTrain.getDestinationIndex() + 1) % count);
                }
                case "prev_destination" -> {
                    int count = Math.max(1, controlTrain.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                    controlTrain.setDestinationIndexForFormation(Math.floorMod(controlTrain.getDestinationIndex() - 1, count));
                }
                case "next_sound" -> controlTrain.setSoundIndex(resolveNextSoundIndex(controlTrain, 1));
                case "prev_sound" -> controlTrain.setSoundIndex(resolveNextSoundIndex(controlTrain, -1));
                case "play_selected_announcement" -> playSelectedAnnouncement(sourceTrain, controlTrain);
                case "play_horn" -> playHorn(sourceTrain, controlTrain);
                case "couple_nearest" -> sourceTrain.coupleNearest();
                case "decouple" -> sourceTrain.decouple();
                case "toggle_custom_button" -> controlTrain.toggleCustomButton(payload.value());
                case "cycle_custom_button" -> {
                    int index = (payload.value() >>> 8) & 0xFF;
                    int currentValue = payload.value() & 0xFF;
                    VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
                    int nextValue = currentValue == 0 ? 1 : 0;
                    if (definition != null && index >= 0 && index < definition.getCustomButtonOptions().size()) {
                        List<String> options = definition.getCustomButtonOptions().get(index);
                        if (!options.isEmpty()) {
                            nextValue = (currentValue + 1) % options.size();
                        }
                    }
                    controlTrain.setCustomButtonValue(index, nextValue);
                }
                default -> {
                }
            }
        });
    }

    private static int resolveNextSoundIndex(TrainEntity controlTrain, int delta) {
        VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
        List<String> announcements = definition != null ? definition.getAnnouncementSounds() : List.of();
        if (announcements.isEmpty()) {
            return Math.max(0, controlTrain.getSoundIndex() + delta);
        }
        return Math.floorMod(controlTrain.getSoundIndex() + delta, announcements.size());
    }

    private static void playSelectedAnnouncement(TrainEntity sourceTrain, TrainEntity controlTrain) {
        VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
        if (definition == null || definition.getAnnouncementSounds().isEmpty()) {
            return;
        }
        int index = Math.floorMod(controlTrain.getSoundIndex(), definition.getAnnouncementSounds().size());
        controlTrain.setSoundIndex(index);
        broadcastTrainSound(sourceTrain, definition.getAnnouncementSounds().get(index), 1.0F, 1.0F);
    }

    private static void playHorn(TrainEntity sourceTrain, TrainEntity controlTrain) {
        VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
        if (definition == null || definition.getHornSound().isBlank()) {
            return;
        }
        broadcastTrainSound(sourceTrain, definition.getHornSound(), 1.0F, 1.0F);
    }

    private static void broadcastTrainSound(TrainEntity sourceTrain, String soundId, float volume, float pitch) {
        if (sourceTrain == null || soundId == null || soundId.isBlank()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            sourceTrain,
            new TrainSoundPayload(sourceTrain.getId(), soundId, volume, pitch)
        );
    }
}
