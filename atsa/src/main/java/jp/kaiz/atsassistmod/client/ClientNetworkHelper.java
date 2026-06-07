package jp.kaiz.atsassistmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientNetworkHelper {
    private ClientNetworkHelper() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(payload);
        }
    }
}
