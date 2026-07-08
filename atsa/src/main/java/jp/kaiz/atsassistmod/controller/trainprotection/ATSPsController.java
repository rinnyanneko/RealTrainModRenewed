// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection;

public class ATSPsController extends TrainProtection {

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.ATSPs;
    }
}
