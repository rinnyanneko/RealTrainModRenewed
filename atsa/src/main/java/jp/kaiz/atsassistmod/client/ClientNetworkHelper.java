// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
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
