// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * DataComponentsを追加するクラス<br></br>
 * DataComponentsは、1.20.5よりNBTタグの代替としてItemStackに導入された状態管理手段。<br></br>
 * 今後のアップデートでアイテムだけでなくNBTを使用するあらゆる要素に拡大していくと予測されており、<br></br>
 * これからはNBTタグではなくこちらを利用することが推奨されている。
 */
object RealTrainModRenewedComponents {
    @JvmField
    val REGISTRAR: DeferredRegister.DataComponents = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE,
        RealTrainModRenewed.MODID,
    )

    /**
     * 列車・レールアイテムで選択中のモデルID
     */
    @JvmField
    val SELECTED_MODEL_ID: DeferredHolder<DataComponentType<*>, DataComponentType<String>> =
        REGISTRAR.registerComponentType("selected_model_id") { builder ->
            builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
        }

    /**
     * モデル選択画面で指定した datamap 引数
     */
    @JvmField
    val SELECTED_MODEL_DATA_MAP: DeferredHolder<DataComponentType<*>, DataComponentType<String>> =
        REGISTRAR.registerComponentType("selected_model_data_map") { builder ->
            builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
        }

    @JvmField
    val RAIL_PREVIEW_START: DeferredHolder<DataComponentType<*>, DataComponentType<CompoundTag>> =
        REGISTRAR.registerComponentType("rail_preview_start") { builder ->
            builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
        }

    /**
     * 1.20.5+ DataComponent: TRAIN_FORMATION
     * Stores train formation data including vehicle IDs and formation name
     */
    @JvmField
    val TRAIN_FORMATION: DeferredHolder<DataComponentType<*>, DataComponentType<CompoundTag>> =
        REGISTRAR.registerComponentType("train_formation") { builder ->
            builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
        }

    @JvmField
    val WIRE_PLACEMENT_START: DeferredHolder<DataComponentType<*>, DataComponentType<CompoundTag>> =
        REGISTRAR.registerComponentType("wire_placement_start") { builder ->
            builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
        }
}
