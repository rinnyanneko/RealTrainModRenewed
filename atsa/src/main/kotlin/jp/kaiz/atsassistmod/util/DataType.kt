// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.util

/** Local copy of RTM's modelpack DataType (used by IFTTT data-map rules). */
enum class DataType(@JvmField val key: String) {
    BOOLEAN("bool"),
    INT("int"),
    HEX("hex"),
    DOUBLE("double"),
    STRING("string"),
    VEC("vec"),
}
