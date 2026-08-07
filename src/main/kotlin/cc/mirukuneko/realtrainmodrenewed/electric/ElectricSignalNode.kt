// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.electric

/** A loaded endpoint in the RTM-style integer signal wiring network. */
interface ElectricSignalNode {
    fun getElectricity(): Int
    fun receiveElectricity(level: Int): Int
}
