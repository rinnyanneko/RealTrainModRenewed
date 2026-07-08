// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.ifttt;

/** Decouples IFTTT container types from the concrete editor screen (was GUIIFTTTMaterial). */
public interface IftttEditView {
    String getTextFieldText(int index);
    int getTextFieldInt(int index);
    int textFieldLength();
}
