package jp.kaiz.atsassistmod.network;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.ATSAssistMod;
import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.GroundUnitType;
import jp.kaiz.atsassistmod.block.entity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.client.hud.TrainHudClientManager;
import jp.kaiz.atsassistmod.controller.TrainController;
import jp.kaiz.atsassistmod.controller.TrainControllerManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.network.payload.ControlPayloads.*;
import jp.kaiz.atsassistmod.network.payload.HudPayload;
import jp.kaiz.atsassistmod.network.payload.IftttPayloads;
import jp.kaiz.atsassistmod.network.payload.SoundPayloads;
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import jp.kaiz.atsassistmod.ifttt.IFTTTUtil;
import java.util.ArrayList;
import java.util.List;
import jp.kaiz.atsassistmod.registry.ATSAModBlocks;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import jp.kaiz.atsassistmod.util.TrainStateType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge payload registration + handlers, replacing the old SimpleNetworkWrapper.
 */
@EventBusSubscriber(modid = ATSAssistMod.MODID)
public final class ATSAModNet {
    private ATSAModNet() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");

        r.playToServer(SetNotchController.TYPE, SetNotchController.CODEC, ATSAModNet::onSetNotchController);
        r.playToServer(SetTrainState.TYPE, SetTrainState.CODEC, ATSAModNet::onSetTrainState);
        r.playToServer(EmergencyBrake.TYPE, EmergencyBrake.CODEC, ATSAModNet::onEmergencyBrake);
        r.playToServer(ManualDrive.TYPE, ManualDrive.CODEC, ATSAModNet::onManualDrive);
        r.playToServer(TrainDriveMode.TYPE, TrainDriveMode.CODEC, ATSAModNet::onTrainDriveMode);
        r.playToServer(TrainProtectionSetter.TYPE, TrainProtectionSetter.CODEC, ATSAModNet::onTrainProtectionSetter);
        r.playToServer(SetGroundUnitType.TYPE, SetGroundUnitType.CODEC, ATSAModNet::onSetGroundUnitType);
        r.playToServer(SaveGroundUnit.TYPE, SaveGroundUnit.CODEC, ATSAModNet::onSaveGroundUnit);
        r.playToServer(IftttPayloads.SaveIfttt.TYPE, IftttPayloads.SaveIfttt.CODEC, ATSAModNet::onSaveIfttt);

        r.playToClient(HudPayload.TYPE, HudPayload.CODEC, ATSAModNet::onHud);
        r.playToClient(SoundPayloads.PlaySoundsAt.TYPE, SoundPayloads.PlaySoundsAt.CODEC, ATSAModNet::onPlaySoundsAt);
        r.playToClient(SoundPayloads.PlaySoundsEntity.TYPE, SoundPayloads.PlaySoundsEntity.CODEC, ATSAModNet::onPlaySoundsEntity);
    }

    // ----------------------------------------------------------------- helpers
    private static TrainEntity riddenTrain(Player player) {
        Entity v = player == null ? null : player.getVehicle();
        return v instanceof TrainEntity train ? train : null;
    }

    private static TrainEntity riddenControlCar(Player player) {
        TrainEntity train = riddenTrain(player);
        return train != null && RtmTrains.isControlCar(train) ? train : null;
    }

    // ----------------------------------------------------------------- C→S
    private static void onSetNotchController(SetNotchController msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TrainEntity train = riddenControlCar(ctx.player());
            if (train != null) {
                TrainControllerManager.getTrainController(train).setControllerNotch((byte) msg.notch());
            }
        });
    }

    private static void onSetTrainState(SetTrainState msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TrainEntity train = riddenTrain(ctx.player());
            if (train != null) {
                TrainStateType.apply(train, msg.stateId(), (byte) msg.value());
            }
        });
    }

    private static void onEmergencyBrake(EmergencyBrake msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TrainEntity train = riddenControlCar(ctx.player());
            if (train != null) {
                TrainControllerManager.getTrainController(train).setEB();
            }
        });
    }

    private static void onManualDrive(ManualDrive msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TrainEntity train = riddenControlCar(ctx.player());
            if (train != null) {
                TrainControllerManager.getTrainController(train).setManualDrive(msg.manual());
            }
        });
    }

    private static void onTrainDriveMode(TrainDriveMode msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TrainEntity train = riddenControlCar(ctx.player());
            if (train == null) {
                return;
            }
            TrainController tc = TrainControllerManager.getTrainController(train);
            switch (msg.mode()) {
                case 0 -> { tc.disableATO(); tc.tascController.disable(); }
                case 1 -> tc.disableATO();
                default -> { }
            }
        });
    }

    private static void onTrainProtectionSetter(TrainProtectionSetter msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TrainEntity train = riddenTrain(ctx.player());
            if (train != null) {
                TrainControllerManager.getTrainController(train).setTrainProtection(TrainProtectionType.getType(msg.typeId()));
            }
        });
    }

    private static void onSetGroundUnitType(SetGroundUnitType msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            BlockPos pos = msg.pos();
            if (!level.getBlockState(pos).is(ATSAModBlocks.GROUND_UNIT.get())) {
                return;
            }
            BlockState state = level.getBlockState(pos).setValue(GroundUnitBlock.TYPE,
                    GroundUnitType.getType(msg.typeId()).id);
            level.setBlock(pos, state, 3);
            level.sendBlockUpdated(pos, state, state, 3);
        });
    }

    private static void onSaveGroundUnit(SaveGroundUnit msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            BlockPos pos = msg.pos();
            if (level.getBlockEntity(pos) instanceof GroundUnitBlockEntity be) {
                be.setLinkRedStone(msg.linkRedstone());
                be.setSpeedLimit(msg.speed());
                be.setDistance(msg.distance());
                be.setAutoBrake(msg.autoBrake());
                be.setUseTrainDistance(msg.useTrainDistance());
                if (msg.states() != null && msg.states().length == 12) {
                    be.setStates(msg.states());
                }
                be.setTPType(TrainProtectionType.getType(msg.tpType()));
                be.setChanged();
                level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    private static void onSaveIfttt(IftttPayloads.SaveIfttt msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(msg.pos()) instanceof IftttBlockEntity be) {
                List<IFTTTContainer> thisList = new ArrayList<>();
                for (byte[] b : msg.thisData()) {
                    IFTTTContainer c = IFTTTUtil.fromBytes(b);
                    if (c instanceof IFTTTContainer.This) thisList.add(c);
                }
                List<IFTTTContainer> thatList = new ArrayList<>();
                for (byte[] b : msg.thatData()) {
                    IFTTTContainer c = IFTTTUtil.fromBytes(b);
                    if (c instanceof IFTTTContainer.That) thatList.add(c);
                }
                be.replaceLists(thisList, thatList, msg.anyMatch());
            }
        });
    }

    // ----------------------------------------------------------------- S→C
    private static void onHud(HudPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (msg.updateType() == 0) {
                TrainHudClientManager.remove(msg.formationId());
            } else {
                TrainHudClientManager.set(msg.formationId(), msg.ato(), msg.tasc(), msg.tpType(),
                        msg.atoSpeed(), msg.tascDistance(), msg.atcSpeed(), msg.tpLimit(), msg.manual());
            }
        });
    }

    private static void onPlaySoundsAt(SoundPayloads.PlaySoundsAt msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> jp.kaiz.atsassistmod.client.sound.SoundSequence.play(msg.positions(), msg.orders(), msg.volume()));
    }

    private static void onPlaySoundsEntity(SoundPayloads.PlaySoundsEntity msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                net.minecraft.world.entity.Entity entity = mc.level.getEntity(msg.entityId());
                if (entity != null) {
                    jp.kaiz.atsassistmod.client.sound.SoundSequence.play(entity, msg.orders(), msg.volume());
                }
            }
        });
    }

    // ----------------------------------------------------------------- broadcast
    public static void broadcastHud(MinecraftServer server, HudPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Broadcasts a sound payload to all players (was {@code sendToAll}). */
    public static void broadcastSound(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
