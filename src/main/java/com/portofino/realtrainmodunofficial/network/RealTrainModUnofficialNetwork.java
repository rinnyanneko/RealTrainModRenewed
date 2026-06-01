package com.portofino.realtrainmodunofficial.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RealTrainModUnofficialNetwork {
    private RealTrainModUnofficialNetwork() {
    }

    /**
     * Registers custom payload handlers used by the mod.
     */
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SelectModelPayload.TYPE, SelectModelPayload.STREAM_CODEC, SelectModelPayload::handleOnServer);
        registrar.playToServer(TrainControlPayload.TYPE, TrainControlPayload.STREAM_CODEC, TrainControlPayload::handleOnServer);
        registrar.playToClient(TrainSoundPayload.TYPE, TrainSoundPayload.STREAM_CODEC, TrainSoundPayload::handleOnClient);
        registrar.playToServer(MountTrainPayload.TYPE, MountTrainPayload.STREAM_CODEC, MountTrainPayload::handleOnServer);
        registrar.playToServer(RailPreviewAdjustPayload.TYPE, RailPreviewAdjustPayload.STREAM_CODEC, RailPreviewAdjustPayload::handleOnServer);
        registrar.playToServer(BindSignalReceiverPayload.TYPE, BindSignalReceiverPayload.STREAM_CODEC, BindSignalReceiverPayload::handleOnServer);
        registrar.playToServer(SetSignalAspectPayload.TYPE, SetSignalAspectPayload.STREAM_CODEC, SetSignalAspectPayload::handleOnServer);
        registrar.playToServer(SetSignalValuePayload.TYPE, SetSignalValuePayload.STREAM_CODEC, SetSignalValuePayload::handleOnServer);
        registrar.playToServer(ConfigureTrainDetectorPayload.TYPE, ConfigureTrainDetectorPayload.STREAM_CODEC, ConfigureTrainDetectorPayload::handleOnServer);
        registrar.playToServer(ConfigureMarkerPayload.TYPE, ConfigureMarkerPayload.STREAM_CODEC, ConfigureMarkerPayload::handleOnServer);
        registrar.playToServer(UpdateScriptBlockPayload.TYPE, UpdateScriptBlockPayload.STREAM_CODEC, UpdateScriptBlockPayload::handleOnServer);
        registrar.playToClient(TrainScriptDataPayload.TYPE, TrainScriptDataPayload.STREAM_CODEC, TrainScriptDataPayload::handleOnClient);
        registrar.playToClient(SpeakerPlayPayload.TYPE, SpeakerPlayPayload.STREAM_CODEC, SpeakerPlayPayload::handleOnClient);
        registrar.playToServer(ConfigureSpeakerPayload.TYPE, ConfigureSpeakerPayload.STREAM_CODEC, ConfigureSpeakerPayload::handleOnServer);
        registrar.playToClient(SyncSpeakerSoundsPayload.TYPE, SyncSpeakerSoundsPayload.STREAM_CODEC, SyncSpeakerSoundsPayload::handleOnClient);
        registrar.playToServer(com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaGroundUnitPayload.TYPE,
            com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaGroundUnitPayload.STREAM_CODEC,
            com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaGroundUnitPayload::handleOnServer);
        registrar.playToServer(com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaSimpleBlockPayload.TYPE,
            com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaSimpleBlockPayload.STREAM_CODEC,
            com.portofino.realtrainmodunofficial.compat.atsassist.network.ConfigureAtsaSimpleBlockPayload::handleOnServer);
        registrar.playToServer(com.portofino.realtrainmodunofficial.compat.atsassist.network.AtsaTrainToolPayload.TYPE,
            com.portofino.realtrainmodunofficial.compat.atsassist.network.AtsaTrainToolPayload.STREAM_CODEC,
            com.portofino.realtrainmodunofficial.compat.atsassist.network.AtsaTrainToolPayload::handleOnServer);
    }
}
