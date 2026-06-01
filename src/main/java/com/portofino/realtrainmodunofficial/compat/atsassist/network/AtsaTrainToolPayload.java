package com.portofino.realtrainmodunofficial.compat.atsassist.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.compat.atsassist.AtsaTrainController;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AtsaTrainToolPayload(String mode, String key, String value) implements CustomPacketPayload {
    public static final Type<AtsaTrainToolPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "atsa_train_tool")
    );

    public static final StreamCodec<ByteBuf, AtsaTrainToolPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        AtsaTrainToolPayload::mode,
        ByteBufCodecs.STRING_UTF8,
        AtsaTrainToolPayload::key,
        ByteBufCodecs.STRING_UTF8,
        AtsaTrainToolPayload::value,
        AtsaTrainToolPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(AtsaTrainToolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            TrainEntity train = player.level().getEntitiesOfClass(TrainEntity.class, new AABB(player.blockPosition()).inflate(12.0D, 6.0D, 12.0D), TrainEntity::isAlive)
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            if (train == null) {
                player.displayClientMessage(Component.literal("近くの列車が見つかりません"), true);
                return;
            }
            switch (payload.mode()) {
                case "protection" -> AtsaTrainController.setTrainProtection(train, payload.value());
                case "datamap" -> train.applyScriptDataSync(java.util.Map.of(payload.key(), payload.value()));
                case "eb" -> AtsaTrainController.emergencyBrake(train);
                case "ato" -> AtsaTrainController.enableAto(train, parseInt(payload.value(), 45));
                case "manual" -> AtsaTrainController.disableAto(train);
                default -> {
                }
            }
            player.displayClientMessage(Component.literal("ATS Assist: " + payload.mode() + " を更新しました"), true);
        });
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
