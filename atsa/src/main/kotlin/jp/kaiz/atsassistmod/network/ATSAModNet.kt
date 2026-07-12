// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.network

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.ATSAssistMod
import jp.kaiz.atsassistmod.block.GroundUnitBlock
import jp.kaiz.atsassistmod.block.GroundUnitType
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity
import jp.kaiz.atsassistmod.client.hud.TrainHudClientManager
import jp.kaiz.atsassistmod.controller.TrainControllerManager
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer
import jp.kaiz.atsassistmod.ifttt.IFTTTUtil
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.EmergencyBrake
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.ManualDrive
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SaveGroundUnit
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SetGroundUnitType
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SetNotchController
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.SetTrainState
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.TrainDriveMode
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.TrainProtectionSetter
import jp.kaiz.atsassistmod.network.payload.HudPayload
import jp.kaiz.atsassistmod.network.payload.IftttPayloads
import jp.kaiz.atsassistmod.network.payload.SoundPayloads
import jp.kaiz.atsassistmod.registry.ATSAModBlocks
import jp.kaiz.atsassistmod.rtm.RtmTrains
import jp.kaiz.atsassistmod.util.TrainStateType
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

/** NeoForge payload registration + handlers, replacing the old SimpleNetworkWrapper. */
@EventBusSubscriber(modid = ATSAssistMod.MODID)
object ATSAModNet {
    @SubscribeEvent
    @JvmStatic
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        registrar.playToServer(SetNotchController.TYPE, SetNotchController.CODEC, ::onSetNotchController)
        registrar.playToServer(SetTrainState.TYPE, SetTrainState.CODEC, ::onSetTrainState)
        registrar.playToServer(EmergencyBrake.TYPE, EmergencyBrake.CODEC, ::onEmergencyBrake)
        registrar.playToServer(ManualDrive.TYPE, ManualDrive.CODEC, ::onManualDrive)
        registrar.playToServer(TrainDriveMode.TYPE, TrainDriveMode.CODEC, ::onTrainDriveMode)
        registrar.playToServer(TrainProtectionSetter.TYPE, TrainProtectionSetter.CODEC, ::onTrainProtectionSetter)
        registrar.playToServer(SetGroundUnitType.TYPE, SetGroundUnitType.CODEC, ::onSetGroundUnitType)
        registrar.playToServer(SaveGroundUnit.TYPE, SaveGroundUnit.CODEC, ::onSaveGroundUnit)
        registrar.playToServer(IftttPayloads.SaveIfttt.TYPE, IftttPayloads.SaveIfttt.CODEC, ::onSaveIfttt)

        registrar.playToClient(HudPayload.TYPE, HudPayload.CODEC, ::onHud)
        registrar.playToClient(SoundPayloads.PlaySoundsAt.TYPE, SoundPayloads.PlaySoundsAt.CODEC, ::onPlaySoundsAt)
        registrar.playToClient(
            SoundPayloads.PlaySoundsEntity.TYPE,
            SoundPayloads.PlaySoundsEntity.CODEC,
            ::onPlaySoundsEntity,
        )
    }

    private fun riddenTrain(player: Player?): TrainEntity? = player?.vehicle as? TrainEntity

    private fun riddenControlCar(player: Player?): TrainEntity? {
        val train = riddenTrain(player)
        return if (train != null && RtmTrains.isControlCar(train)) train else null
    }

    private fun onSetNotchController(msg: SetNotchController, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val train = riddenControlCar(ctx.player())
            if (train != null) {
                TrainControllerManager.getTrainController(train).setControllerNotch(msg.notch().toByte())
            }
        }
    }

    private fun onSetTrainState(msg: SetTrainState, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val train = riddenTrain(ctx.player())
            if (train != null) {
                TrainStateType.apply(train, msg.stateId(), msg.value().toByte())
            }
        }
    }

    private fun onEmergencyBrake(msg: EmergencyBrake, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val train = riddenControlCar(ctx.player())
            if (train != null) {
                TrainControllerManager.getTrainController(train).setEB()
            }
        }
    }

    private fun onManualDrive(msg: ManualDrive, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val train = riddenControlCar(ctx.player())
            if (train != null) {
                TrainControllerManager.getTrainController(train).setManualDrive(msg.manual())
            }
        }
    }

    private fun onTrainDriveMode(msg: TrainDriveMode, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val train = riddenControlCar(ctx.player()) ?: return@enqueueWork
            val controller = TrainControllerManager.getTrainController(train)
            when (msg.mode()) {
                0 -> {
                    controller.disableATO()
                    controller.tascController.disable()
                }
                1 -> controller.disableATO()
                else -> Unit
            }
        }
    }

    private fun onTrainProtectionSetter(msg: TrainProtectionSetter, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val train = riddenTrain(ctx.player())
            if (train != null) {
                TrainControllerManager.getTrainController(train).setTrainProtection(TrainProtectionType.getType(msg.typeId()))
            }
        }
    }

    private fun onSetGroundUnitType(msg: SetGroundUnitType, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val level: Level = ctx.player().level()
            val pos = msg.pos()
            if (!level.getBlockState(pos).`is`(ATSAModBlocks.GROUND_UNIT.get())) {
                return@enqueueWork
            }
            val state = level.getBlockState(pos).setValue(
                GroundUnitBlock.TYPE,
                GroundUnitType.getType(msg.typeId()).id,
            )
            level.setBlock(pos, state, 3)
            level.sendBlockUpdated(pos, state, state, 3)
        }
    }

    private fun onSaveGroundUnit(msg: SaveGroundUnit, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val level = ctx.player().level()
            val pos = msg.pos()
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity is GroundUnitBlockEntity) {
                blockEntity.setLinkRedStone(msg.linkRedstone())
                blockEntity.setSpeedLimit(msg.speed())
                blockEntity.setDistance(msg.distance())
                blockEntity.setAutoBrake(msg.autoBrake())
                blockEntity.setUseTrainDistance(msg.useTrainDistance())
                if (msg.states().size == 12) {
                    blockEntity.setStates(msg.states())
                }
                blockEntity.setTPType(TrainProtectionType.getType(msg.tpType()))
                blockEntity.setChanged()
                level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, 3)
            }
        }
    }

    private fun onSaveIfttt(msg: IftttPayloads.SaveIfttt, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val level = ctx.player().level()
            val blockEntity = level.getBlockEntity(msg.pos())
            if (blockEntity is IftttBlockEntity) {
                val thisList = ArrayList<IFTTTContainer>()
                for (bytes in msg.thisData()) {
                    val container = IFTTTUtil.fromBytes(bytes)
                    if (container is IFTTTContainer.This) {
                        thisList.add(container)
                    }
                }
                val thatList = ArrayList<IFTTTContainer>()
                for (bytes in msg.thatData()) {
                    val container = IFTTTUtil.fromBytes(bytes)
                    if (container is IFTTTContainer.That) {
                        thatList.add(container)
                    }
                }
                blockEntity.replaceLists(thisList, thatList, msg.anyMatch())
            }
        }
    }

    private fun onHud(msg: HudPayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            if (msg.updateType() == 0) {
                TrainHudClientManager.remove(msg.formationId())
            } else {
                TrainHudClientManager.set(
                    msg.formationId(),
                    msg.ato(),
                    msg.tasc(),
                    msg.tpType(),
                    msg.atoSpeed(),
                    msg.tascDistance(),
                    msg.atcSpeed(),
                    msg.tpLimit(),
                    msg.manual(),
                )
            }
        }
    }

    private fun onPlaySoundsAt(msg: SoundPayloads.PlaySoundsAt, ctx: IPayloadContext) {
        ctx.enqueueWork {
            jp.kaiz.atsassistmod.client.sound.SoundSequence.play(msg.positions(), msg.orders(), msg.volume())
        }
    }

    private fun onPlaySoundsEntity(msg: SoundPayloads.PlaySoundsEntity, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val minecraft = net.minecraft.client.Minecraft.getInstance()
            val entity = minecraft.level?.getEntity(msg.entityId())
            if (entity != null) {
                jp.kaiz.atsassistmod.client.sound.SoundSequence.play(entity, msg.orders(), msg.volume())
            }
        }
    }

    @JvmStatic
    fun broadcastHud(server: MinecraftServer, payload: HudPayload) {
        for (player in server.playerList.players) {
            PacketDistributor.sendToPlayer(player, payload)
        }
    }

    /** Broadcasts a sound payload to all players (was sendToAll). */
    @JvmStatic
    fun broadcastSound(server: MinecraftServer, payload: CustomPacketPayload) {
        for (player in server.playerList.players) {
            PacketDistributor.sendToPlayer(player, payload)
        }
    }
}
