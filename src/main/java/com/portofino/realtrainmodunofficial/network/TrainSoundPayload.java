package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrainSoundPayload(int trainEntityId, String soundId, float volume, float pitch) implements CustomPacketPayload {
    public static final Type<TrainSoundPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "train_sound")
    );

    public static final StreamCodec<ByteBuf, TrainSoundPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        TrainSoundPayload::trainEntityId,
        ByteBufCodecs.STRING_UTF8,
        TrainSoundPayload::soundId,
        ByteBufCodecs.FLOAT,
        TrainSoundPayload::volume,
        ByteBufCodecs.FLOAT,
        TrainSoundPayload::pitch,
        TrainSoundPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(TrainSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || payload.soundId() == null || payload.soundId().isBlank()) {
                return;
            }
            if (minecraft.level.getEntity(payload.trainEntityId()) instanceof TrainEntity train) {
                LegacyScriptSoundManager.playLegacyId(train, payload.soundId(), payload.volume(), payload.pitch(), false);
            }
        });
    }
}
