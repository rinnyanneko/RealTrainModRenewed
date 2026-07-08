// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.model.mqo

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object MQOLoader {
    @JvmStatic
    fun load(modelFilePath: String): MQOModel? {
        val modelLocation = Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, modelFilePath)
        val resourceManager = Minecraft.getInstance().resourceManager

        var model: MQOModel? = null

        try {
            resourceManager.open(modelLocation).use { modelInputStream ->
                BufferedReader(InputStreamReader(modelInputStream, StandardCharsets.UTF_8)).use { modelReader ->
                    // TODO: 文字エンコーディングの判別に対応
                    val parseResult = MQOParser.parse(modelReader)
                    if (parseResult.status == MQOParseResultStatus.SUCCESS) {
                        model = parseResult.model
                    }
                }
            }
        } catch (e: IOException) {
            RealTrainModRenewed.LOGGER.error("MQOファイルの読み込みに失敗しました。", e)
        }
        return model
    }
}
