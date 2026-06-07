package jp.kaiz.atsassistmod.network.payload;

import jp.kaiz.atsassistmod.ATSAssistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server → client sound sequence playback (mirrors PacketPlaySounds / PacketPlaySoundsEntity). */
public final class SoundPayloads {
    private SoundPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, path);
    }

    private static void writeOrders(RegistryFriendlyByteBuf buf, List<String> orders) {
        buf.writeVarInt(orders.size());
        for (String s : orders) {
            buf.writeUtf(s == null ? "" : s);
        }
    }

    private static List<String> readOrders(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    /** Plays a sound sequence at one or more positions (anchored to an IFTTT block). */
    public record PlaySoundsAt(List<int[]> positions, List<String> orders, float volume) implements CustomPacketPayload {
        public static final Type<PlaySoundsAt> TYPE = new Type<>(id("play_sounds_at"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlaySoundsAt> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.positions.size());
                    for (int[] pos : p.positions) {
                        buf.writeVarInt(pos[0]);
                        buf.writeVarInt(pos[1]);
                        buf.writeVarInt(pos[2]);
                    }
                    writeOrders(buf, p.orders);
                    buf.writeFloat(p.volume);
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<int[]> positions = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        positions.add(new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt()});
                    }
                    List<String> orders = readOrders(buf);
                    return new PlaySoundsAt(positions, orders, buf.readFloat());
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Plays a sound sequence following an entity. */
    public record PlaySoundsEntity(int entityId, List<String> orders, float volume) implements CustomPacketPayload {
        public static final Type<PlaySoundsEntity> TYPE = new Type<>(id("play_sounds_entity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlaySoundsEntity> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId);
                    writeOrders(buf, p.orders);
                    buf.writeFloat(p.volume);
                },
                buf -> new PlaySoundsEntity(buf.readVarInt(), readOrders(buf), buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
