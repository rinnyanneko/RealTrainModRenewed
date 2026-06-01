package com.portofino.realtrainmodunofficial.compat.atsassist.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaGroundUnitBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigureAtsaGroundUnitPayload(
    BlockPos pos,
    int unitType,
    int speedLimitKmh,
    int stopDistance,
    boolean tascEnabled,
    String trainProtection,
    boolean linkRedstone,
    boolean autoBrake,
    boolean useTrainDistance,
    byte[] trainStates
) implements CustomPacketPayload {
    public static final Type<ConfigureAtsaGroundUnitPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "configure_atsa_ground_unit")
    );

    public static final StreamCodec<ByteBuf, ConfigureAtsaGroundUnitPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigureAtsaGroundUnitPayload decode(ByteBuf buffer) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            int unitType = ByteBufCodecs.INT.decode(buffer);
            int speedLimitKmh = ByteBufCodecs.INT.decode(buffer);
            int stopDistance = ByteBufCodecs.INT.decode(buffer);
            boolean tascEnabled = ByteBufCodecs.BOOL.decode(buffer);
            String trainProtection = ByteBufCodecs.STRING_UTF8.decode(buffer);
            boolean linkRedstone = ByteBufCodecs.BOOL.decode(buffer);
            boolean autoBrake = ByteBufCodecs.BOOL.decode(buffer);
            boolean useTrainDistance = ByteBufCodecs.BOOL.decode(buffer);
            int length = Math.max(0, Math.min(64, ByteBufCodecs.INT.decode(buffer)));
            byte[] states = new byte[length];
            buffer.readBytes(states);
            return new ConfigureAtsaGroundUnitPayload(pos, unitType, speedLimitKmh, stopDistance, tascEnabled,
                trainProtection, linkRedstone, autoBrake, useTrainDistance, states);
        }

        @Override
        public void encode(ByteBuf buffer, ConfigureAtsaGroundUnitPayload payload) {
            BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
            ByteBufCodecs.INT.encode(buffer, payload.unitType());
            ByteBufCodecs.INT.encode(buffer, payload.speedLimitKmh());
            ByteBufCodecs.INT.encode(buffer, payload.stopDistance());
            ByteBufCodecs.BOOL.encode(buffer, payload.tascEnabled());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.trainProtection());
            ByteBufCodecs.BOOL.encode(buffer, payload.linkRedstone());
            ByteBufCodecs.BOOL.encode(buffer, payload.autoBrake());
            ByteBufCodecs.BOOL.encode(buffer, payload.useTrainDistance());
            byte[] states = payload.trainStates() == null ? new byte[0] : payload.trainStates();
            ByteBufCodecs.INT.encode(buffer, states.length);
            buffer.writeBytes(states);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ConfigureAtsaGroundUnitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof AtsaGroundUnitBlockEntity blockEntity)) {
                return;
            }
            blockEntity.configure(payload.unitType(), payload.speedLimitKmh(), payload.stopDistance(), payload.tascEnabled(),
                payload.trainProtection(), payload.linkRedstone(), payload.autoBrake(), payload.useTrainDistance(), payload.trainStates());
            player.level().sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            player.displayClientMessage(Component.literal("ATS Assist 地上子を更新しました"), true);
        });
    }
}
