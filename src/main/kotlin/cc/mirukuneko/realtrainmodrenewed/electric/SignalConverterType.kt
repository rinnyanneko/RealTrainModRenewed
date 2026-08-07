// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.electric

enum class SignalConverterType(val id: Int) {
    RS_INPUT(0),
    RS_OUTPUT(1),
    INCREMENT(2),
    DECREMENT(3),
    WIRELESS(4);

    companion object {
        @JvmStatic
        fun byId(id: Int): SignalConverterType = entries.firstOrNull { it.id == id } ?: RS_INPUT
    }
}

enum class SignalComparator(val id: Int, val operator: String) {
    EQUAL(0, "=="),
    GREATER_THAN(1, ">"),
    GREATER_EQUAL(2, ">="),
    LESS_THAN(3, "<"),
    LESS_EQUAL(4, "<="),
    NOT_EQUAL(5, "!=");

    fun test(input: Int, threshold: Int): Boolean = when (this) {
        EQUAL -> input == threshold
        GREATER_THAN -> input > threshold
        GREATER_EQUAL -> input >= threshold
        LESS_THAN -> input < threshold
        LESS_EQUAL -> input <= threshold
        NOT_EQUAL -> input != threshold
    }

    companion object {
        @JvmStatic
        fun byId(id: Int): SignalComparator = entries.firstOrNull { it.id == id } ?: EQUAL
    }
}

object SignalConverterLogic {
    @JvmStatic
    fun transform(type: SignalConverterType, input: Int): Int = when (type) {
        SignalConverterType.INCREMENT -> if (input > 0) input + 1 else 0
        SignalConverterType.DECREMENT -> if (input > 1) input - 1 else input
        else -> input
    }
}
