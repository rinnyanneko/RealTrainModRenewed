package com.portofino.realtrainmodunofficial.compat.atsassist.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.compat.atsassist.blockentity.AtsaSimpleBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigureAtsaSimpleBlockPayload(
    BlockPos pos,
    String condition,
    String action,
    String announce,
    boolean anyMatch
) implements CustomPacketPayload {
    public static final Type<ConfigureAtsaSimpleBlockPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "configure_atsa_simple_block")
    );

    public static final StreamCodec<ByteBuf, ConfigureAtsaSimpleBlockPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ConfigureAtsaSimpleBlockPayload::pos,
        ByteBufCodecs.STRING_UTF8,
        ConfigureAtsaSimpleBlockPayload::condition,
        ByteBufCodecs.STRING_UTF8,
        ConfigureAtsaSimpleBlockPayload::action,
        ByteBufCodecs.STRING_UTF8,
        ConfigureAtsaSimpleBlockPayload::announce,
        ByteBufCodecs.BOOL,
        ConfigureAtsaSimpleBlockPayload::anyMatch,
        ConfigureAtsaSimpleBlockPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ConfigureAtsaSimpleBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof AtsaSimpleBlockEntity blockEntity)) {
                return;
            }
            blockEntity.configure(payload.condition(), payload.action(), payload.announce(), payload.anyMatch());
            player.level().sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            player.displayClientMessage(Component.literal("ATS Assist ブロックを更新しました"), true);
        });
    }
}
