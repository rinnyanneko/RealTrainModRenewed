// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.math.max

@JvmRecord
data class TrainControlPayload(
    val trainEntityId: Int,
    val action: String,
    val value: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<TrainControlPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "train_control")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, TrainControlPayload> = StreamCodec.composite(
            ByteBufCodecs.INT,
            { payload -> payload.trainEntityId },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.action },
            ByteBufCodecs.INT,
            { payload -> payload.value },
            ::TrainControlPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: TrainControlPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val train = player.level().getEntity(payload.trainEntityId) as? TrainEntity
                if (train == null) {
                    RealTrainModRenewed.LOGGER.info(
                        "Train control ignored: train {} not found for action {}",
                        payload.trainEntityId,
                        payload.action,
                    )
                    return@enqueueWork
                }

                val sourceTrain = resolveSourceTrain(player, train)
                val controlTrain = sourceTrain.formationHead
                val sameFormationRide = isSameFormationRide(player, controlTrain)
                val assignedSeat = controlTrain.formationHasAssignedSeat(player.uuid)
                val driverPassenger = sourceTrain.isDriverPassenger(player) || train.isDriverPassenger(player)
                val dismountAction = payload.action == "dismount"

                if (!dismountAction && !sameFormationRide && !assignedSeat && !driverPassenger) {
                    RealTrainModRenewed.LOGGER.info(
                        "Train control ignored: player={} action={} requestedTrain={} sourceTrain={} sameFormationRide={} assignedSeat={}",
                        player.name.string,
                        payload.action,
                        train.vehicleId,
                        sourceTrain.vehicleId,
                        sameFormationRide,
                        assignedSeat,
                    )
                    return@enqueueWork
                }

                if (!dismountAction && driverPassenger) {
                    controlTrain.markDriverControl(player)
                    sourceTrain.markDriverControl(player)
                }

                RealTrainModRenewed.LOGGER.info(
                    "Train control accepted: player={} action={} train={} head={} notch={} reverser={}",
                    player.name.string,
                    payload.action,
                    sourceTrain.vehicleId,
                    controlTrain.vehicleId,
                    controlTrain.notch,
                    controlTrain.reverser,
                )

                when (payload.action) {
                    "mascon_power" -> {
                        if (!driverPassenger) {
                            return@enqueueWork
                        }
                        sourceTrain.ensureDriverReady(player)
                        controlTrain.ensureDriverReady(player)
                        controlTrain.stepMascon(1)
                    }

                    "mascon_brake" -> {
                        if (!driverPassenger) {
                            return@enqueueWork
                        }
                        sourceTrain.ensureDriverReady(player)
                        controlTrain.ensureDriverReady(player)
                        controlTrain.stepMascon(-1)
                    }

                    "mascon_neutral" -> {
                        if (!driverPassenger) {
                            return@enqueueWork
                        }
                        controlTrain.notch = 0
                    }

                    "dismount" -> {
                        player.stopRiding()
                        controlTrain.clearSeatAssignment(player.uuid)
                        sourceTrain.clearSeatAssignment(player.uuid)
                    }

                    "toggle_headlight" -> controlTrain.isHeadlightOn = !controlTrain.isHeadlightOn
                    "set_light_mode" -> controlTrain.setLightModeForFormation(payload.value)
                    "toggle_interior_light" -> controlTrain.setInteriorLightOnForFormation(!controlTrain.isInteriorLightOn)
                    "toggle_door" -> {
                        val opening = !controlTrain.isDoorOpen
                        controlTrain.toggleDoorForFormation()
                        playDoorSound(sourceTrain, controlTrain, opening)
                    }

                    "toggle_door_left" -> {
                        val opening = !controlTrain.isDoorLeftOpen
                        controlTrain.toggleDoorSideForFormation(true)
                        playDoorSound(sourceTrain, controlTrain, opening)
                    }

                    "toggle_door_right" -> {
                        val opening = !controlTrain.isDoorRightOpen
                        controlTrain.toggleDoorSideForFormation(false)
                        playDoorSound(sourceTrain, controlTrain, opening)
                    }

                    "toggle_pantograph" -> controlTrain.setPantographUpForFormation(!controlTrain.isPantographUp)
                    "toggle_reverse" -> controlTrain.isReverse = !controlTrain.isReverse
                    "set_reverser" -> controlTrain.reverser = payload.value
                    "next_destination" -> {
                        val count = max(1, (controlTrain.resourceState.resourceSet.config.rollsignNames ?: emptyArray()).size)
                        controlTrain.setDestinationIndexForFormation((controlTrain.destinationIndex + 1) % count)
                    }

                    "prev_destination" -> {
                        val count = max(1, (controlTrain.resourceState.resourceSet.config.rollsignNames ?: emptyArray()).size)
                        controlTrain.setDestinationIndexForFormation(Math.floorMod(controlTrain.destinationIndex - 1, count))
                    }

                    "next_sound" -> controlTrain.soundIndex = resolveNextSoundIndex(controlTrain, 1)
                    "prev_sound" -> controlTrain.soundIndex = resolveNextSoundIndex(controlTrain, -1)
                    "play_selected_announcement" -> playSelectedAnnouncement(sourceTrain, controlTrain)
                    "play_horn" -> playHorn(sourceTrain, controlTrain)
                    "couple_nearest" -> sourceTrain.coupleNearest()
                    "decouple" -> sourceTrain.decouple()
                    "toggle_custom_button" -> controlTrain.toggleCustomButton(payload.value)
                    "cycle_custom_button" -> {
                        val index = payload.value ushr 8 and 0xFF
                        val currentValue = payload.value and 0xFF
                        val definition = VehicleRegistry.getById(controlTrain.vehicleId)
                        var nextValue = if (currentValue == 0) 1 else 0
                        if (definition != null && index >= 0 && index < definition.customButtonOptions.size) {
                            val options = definition.customButtonOptions[index]
                            if (options.isNotEmpty()) {
                                nextValue = (currentValue + 1) % options.size
                            }
                        }
                        controlTrain.setCustomButtonValue(index, nextValue)
                    }
                }
            }
        }

        private fun resolveSourceTrain(player: ServerPlayer, train: TrainEntity): TrainEntity {
            var sourceTrain = train
            when (val vehicle = player.vehicle) {
                is TrainEntity -> {
                    if (vehicle.isAlive) {
                        val riddenHead = vehicle.formationHead
                        val requestedHead = train.formationHead
                        if (vehicle === train || riddenHead === requestedHead) {
                            sourceTrain = vehicle
                        }
                    }
                }

                is TrainSeatEntity -> {
                    val seatedTrain = vehicle.train
                    if (seatedTrain != null && seatedTrain.isAlive) {
                        val riddenHead = seatedTrain.formationHead
                        val requestedHead = train.formationHead
                        if (seatedTrain === train || riddenHead === requestedHead) {
                            sourceTrain = seatedTrain
                        }
                    }
                }
            }
            return sourceTrain
        }

        private fun isSameFormationRide(player: ServerPlayer, controlTrain: TrainEntity): Boolean {
            return when (val vehicle = player.vehicle) {
                is TrainEntity -> vehicle.isAlive && vehicle.formationHead === controlTrain
                is TrainSeatEntity -> {
                    val seatTrain = vehicle.train
                    seatTrain != null && seatTrain.isAlive && seatTrain.formationHead === controlTrain
                }
                else -> false
            }
        }

        private fun resolveNextSoundIndex(controlTrain: TrainEntity, delta: Int): Int {
            val definition = VehicleRegistry.getById(controlTrain.vehicleId)
            val announcements = definition?.announcementSounds ?: emptyList()
            if (announcements.isEmpty()) {
                return max(0, controlTrain.soundIndex + delta)
            }
            return Math.floorMod(controlTrain.soundIndex + delta, announcements.size)
        }

        private fun playSelectedAnnouncement(sourceTrain: TrainEntity, controlTrain: TrainEntity) {
            val definition = VehicleRegistry.getById(controlTrain.vehicleId)
            if (definition == null || definition.announcementSounds.isEmpty()) {
                return
            }
            val index = Math.floorMod(controlTrain.soundIndex, definition.announcementSounds.size)
            controlTrain.soundIndex = index
            broadcastTrainSound(sourceTrain, definition.announcementSounds[index], 1.0f, 1.0f)
        }

        private fun playHorn(sourceTrain: TrainEntity, controlTrain: TrainEntity) {
            val definition = VehicleRegistry.getById(controlTrain.vehicleId)
            if (definition == null || definition.hornSound.isBlank()) {
                return
            }
            broadcastTrainSound(sourceTrain, definition.hornSound, 1.0f, 1.0f)
        }

        private fun playDoorSound(sourceTrain: TrainEntity, controlTrain: TrainEntity, opening: Boolean) {
            val definition = VehicleRegistry.getById(controlTrain.vehicleId) ?: return
            val sound = if (opening) definition.doorOpenSound else definition.doorCloseSound
            if (sound.isNotBlank()) {
                broadcastTrainSound(sourceTrain, sound, 1.0f, 1.0f)
            }
        }

        private fun broadcastTrainSound(sourceTrain: TrainEntity?, soundId: String?, volume: Float, pitch: Float) {
            if (sourceTrain == null || soundId.isNullOrBlank()) {
                return
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                sourceTrain,
                TrainSoundPayload(sourceTrain.id, soundId, volume, pitch),
            )
        }
    }
}
