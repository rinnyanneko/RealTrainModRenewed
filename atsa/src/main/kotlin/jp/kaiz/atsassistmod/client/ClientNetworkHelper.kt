// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

object ClientNetworkHelper {
    @JvmStatic
    fun sendToServer(payload: CustomPacketPayload) {
        Minecraft.getInstance().connection?.send(payload)
    }
}
