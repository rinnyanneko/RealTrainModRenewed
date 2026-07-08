// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities
import net.neoforged.neoforge.client.event.EntityRenderersEvent

object RealTrainModRenewedRenderers {
    @JvmStatic
    fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(RealTrainModRenewedEntities.CAR.get(), ::CarRenderer)
    }
}
