// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.util

import com.fasterxml.jackson.annotation.JsonTypeInfo

/** Comparison operators for IFTTT conditions. */
class ComparisonManager private constructor() {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    interface ComparisonBase<T> {
        fun getName(): kotlin.String

        fun isTrue(o0: T, o1: Any?): kotlin.Boolean

        fun parseT(str: kotlin.String): T
    }

    enum class Integer(private val displayName: kotlin.String) : ComparisonBase<kotlin.Int> {
        EQUAL("==") {
            override fun isTrue(o0: kotlin.Int, o1: Any?): kotlin.Boolean = o0 == o1
        },
        GREATER_THAN(">") {
            override fun isTrue(o0: kotlin.Int, o1: Any?): kotlin.Boolean = o0 > o1 as kotlin.Int
        },
        GREATER_EQUAL(">=") {
            override fun isTrue(o0: kotlin.Int, o1: Any?): kotlin.Boolean = o0 >= o1 as kotlin.Int
        },
        LESS_THAN("<") {
            override fun isTrue(o0: kotlin.Int, o1: Any?): kotlin.Boolean = o0 < o1 as kotlin.Int
        },
        LESS_EQUAL("<=") {
            override fun isTrue(o0: kotlin.Int, o1: Any?): kotlin.Boolean = o0 <= o1 as kotlin.Int
        },
        NOT_EQUAL("!=") {
            override fun isTrue(o0: kotlin.Int, o1: Any?): kotlin.Boolean = o0 != o1
        };

        override fun getName(): kotlin.String = displayName

        override fun parseT(str: kotlin.String): kotlin.Int =
            try {
                str.toInt()
            } catch (_: Exception) {
                0
            }
    }

    enum class Double(private val displayName: kotlin.String) : ComparisonBase<kotlin.Double> {
        EQUAL("==") {
            override fun isTrue(o0: kotlin.Double, o1: Any?): kotlin.Boolean = o0 == o1
        },
        GREATER_THAN(">") {
            override fun isTrue(o0: kotlin.Double, o1: Any?): kotlin.Boolean = o0 > o1 as kotlin.Double
        },
        GREATER_EQUAL(">=") {
            override fun isTrue(o0: kotlin.Double, o1: Any?): kotlin.Boolean = o0 >= o1 as kotlin.Double
        },
        LESS_THAN("<") {
            override fun isTrue(o0: kotlin.Double, o1: Any?): kotlin.Boolean = o0 < o1 as kotlin.Double
        },
        LESS_EQUAL("<=") {
            override fun isTrue(o0: kotlin.Double, o1: Any?): kotlin.Boolean = o0 <= o1 as kotlin.Double
        },
        NOT_EQUAL("!=") {
            override fun isTrue(o0: kotlin.Double, o1: Any?): kotlin.Boolean = o0 != o1
        };

        override fun getName(): kotlin.String = displayName

        override fun parseT(str: kotlin.String): kotlin.Double =
            try {
                str.toDouble()
            } catch (_: Exception) {
                0.0
            }
    }

    enum class String(private val displayName: kotlin.String) : ComparisonBase<kotlin.String> {
        EQUAL("==") {
            override fun isTrue(o0: kotlin.String, o1: Any?): kotlin.Boolean = o0 == o1
        },
        NOT_EQUAL("!=") {
            override fun isTrue(o0: kotlin.String, o1: Any?): kotlin.Boolean = o0 != o1
        },
        CONTAINS(" contains ") {
            override fun isTrue(o0: kotlin.String, o1: Any?): kotlin.Boolean = o0.contains(o1 as kotlin.String)
        },
        NOT_CONTAINS(" !contains ") {
            override fun isTrue(o0: kotlin.String, o1: Any?): kotlin.Boolean = !o0.contains(o1 as kotlin.String)
        };

        override fun getName(): kotlin.String = displayName

        override fun parseT(str: kotlin.String): kotlin.String = str
    }

    enum class Boolean(private val displayName: kotlin.String) : ComparisonBase<kotlin.Boolean> {
        TRUE("==True") {
            override fun isTrue(o0: kotlin.Boolean, o1: Any?): kotlin.Boolean = o0
        },
        FALSE("==False") {
            override fun isTrue(o0: kotlin.Boolean, o1: Any?): kotlin.Boolean = !o0
        };

        override fun getName(): kotlin.String = displayName

        override fun parseT(str: kotlin.String): kotlin.Boolean =
            try {
                str.toBoolean()
            } catch (_: Exception) {
                false
            }
    }
}
