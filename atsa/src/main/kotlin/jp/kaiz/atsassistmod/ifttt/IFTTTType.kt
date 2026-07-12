// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.ifttt

/** IFTTT condition/action type ids. Names resolve to lang keys. */
object IFTTTType {
    interface IFTTTEnumBase {
        fun getId(): Int

        fun getTranslationKey(): String = "atsassistmod.IFTTTType.${getId()}"
    }

    enum class This(private val id: Int) : IFTTTEnumBase {
        Select(100);

        override fun getId(): Int = id

        enum class Minecraft(private val id: Int) : IFTTTEnumBase {
            RedStoneInput(110);

            override fun getId(): Int = id
        }

        enum class RTM(private val id: Int) : IFTTTEnumBase {
            OnTrain(120),
            Cars(121),
            Speed(122),
            TrainDataMap(124),
            TrainDirection(125);

            override fun getId(): Int = id
        }

        enum class ATSAssist(private val id: Int) : IFTTTEnumBase {
            CODD(130);

            override fun getId(): Int = id
        }
    }

    enum class That(private val id: Int) : IFTTTEnumBase {
        Select(200);

        override fun getId(): Int = id

        enum class Minecraft(private val id: Int) : IFTTTEnumBase {
            RedStoneOutput(210),
            PlaySound(211),
            ExecuteCommand(212),
            SetBlock(213);

            override fun getId(): Int = id
        }

        enum class RTM(private val id: Int) : IFTTTEnumBase {
            TrainDataMap(221),
            Signal(223);

            override fun getId(): Int = id
        }

        enum class ATSAssist(private val id: Int) : IFTTTEnumBase {
            JavaScript(230);

            override fun getId(): Int = id
        }
    }

    @JvmStatic
    fun getType(id: Int): IFTTTEnumBase? {
        if (id in 100 until 106) return This.Select
        for (type in This.Minecraft.entries) if (type.getId() == id) return type
        for (type in This.RTM.entries) if (type.getId() == id) return type
        for (type in This.ATSAssist.entries) if (type.getId() == id) return type
        if (id in 200 until 206) return That.Select
        for (type in That.Minecraft.entries) if (type.getId() == id) return type
        for (type in That.RTM.entries) if (type.getId() == id) return type
        for (type in That.ATSAssist.entries) if (type.getId() == id) return type
        return null
    }
}
