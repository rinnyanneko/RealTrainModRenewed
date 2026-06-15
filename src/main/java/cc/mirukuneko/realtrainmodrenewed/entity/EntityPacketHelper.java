package cc.mirukuneko.realtrainmodrenewed.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

public final class EntityPacketHelper {
    public static Packet<ClientGamePacketListener> createAddEntityPacket(Entity entity, ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(entity, serverEntity);
    }
}
