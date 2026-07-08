// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client

import java.lang.reflect.Method

/**
 * Detects whether an Iris/Oculus shader pack is currently active, via reflection so
 * Iris is not a hard dependency.
 *
 * Used by the model renderer: the "fullbright" fast direct-GL path renders the
 * train body with flat shading and bypasses the shader pipeline, which makes vertex
 * normal smoothing disappear under shaders. When a shader pack is in use we fall back
 * to the buffered, normal-lit render path so smoothing works. No model/normal/light
 * numeric values are changed — only the render path is chosen.
 */
object ShaderCompat {
    private var available: Boolean? = null
    private var irisApiInstance: Any? = null
    private var isShaderPackInUseMethod: Method? = null

    @JvmStatic
    fun isShaderPackInUse(): Boolean {
        return try {
            if (available == null) {
                init()
            }
            if (available != true) {
                return false
            }
            val result = isShaderPackInUseMethod?.invoke(irisApiInstance)
            result is Boolean && result
        } catch (_: Throwable) {
            false
        }
    }

    private fun init() {
        try {
            val api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            irisApiInstance = api.getMethod("getInstance").invoke(null)
            isShaderPackInUseMethod = api.getMethod("isShaderPackInUse")
            available = irisApiInstance != null
        } catch (_: Throwable) {
            available = false
        }
    }
}
