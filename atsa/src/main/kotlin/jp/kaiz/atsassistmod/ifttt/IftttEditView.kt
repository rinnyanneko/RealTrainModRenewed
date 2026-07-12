// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.ifttt

/** Decouples IFTTT container types from the concrete editor screen. */
interface IftttEditView {
    fun getTextFieldText(index: Int): String

    fun getTextFieldInt(index: Int): Int

    fun textFieldLength(): Int
}
