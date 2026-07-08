// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.model.mqo

/// MQOモデルのパース結果のステータス
enum class MQOParseResultStatus {
    /// 正常に終了したときの値
    SUCCESS,
    /// ロードが禁止されていたときの値
    FORBIDDEN,
    /// ロードに必要なデータが欠落していたときの値
    MISSING
}
