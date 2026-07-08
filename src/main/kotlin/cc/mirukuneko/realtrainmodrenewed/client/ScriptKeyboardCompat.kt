package cc.mirukuneko.realtrainmodrenewed.client

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * 1.12.2 RTM スクリプト(SuperRailBuilder3 等)が使う LWJGL2 の `Keyboard.isKeyDown(code)` を、
 * 1.21.1 の GLFW 実キー状態に橋渡しするクライアント専用ヘルパー。
 *
 * SRB の GUI はキー駆動(KeyMaps: LCONTROL/UP/DOWN/F/LEFT/RIGHT/RETURN/C/P/O/I/DELETE/H/Q 等)で、
 * 旧 RTMU の Keyboard スタブは常に false を返していたため操作が一切効かなかった。ここで実入力に繋ぐ。
 */
class ScriptKeyboardCompat {

    /** LWJGL2 のキーコードを受け取り、現在押下中かを返す(GLFW へ変換して読む)。 */
    fun isKeyDown(lwjgl2Code: Int): Boolean {
        return try {
            val mc = Minecraft.getInstance()
            if (mc == null || mc.window == null) {
                return false
            }
            val glfw = toGlfw(lwjgl2Code)
            if (glfw < 0) {
                return false
            }
            val window = mc.window.handle()
            GLFW.glfwGetKey(window, glfw) == GLFW.GLFW_PRESS
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        /** LWJGL2 キーコード → GLFW キーコード。SRB が使うキーを網羅。 */
        private fun toGlfw(code: Int): Int {
            return when (code) {
                1   -> GLFW.GLFW_KEY_ESCAPE
                14  -> GLFW.GLFW_KEY_BACKSPACE
                15  -> GLFW.GLFW_KEY_TAB
                16  -> GLFW.GLFW_KEY_Q
                17  -> GLFW.GLFW_KEY_W
                18  -> GLFW.GLFW_KEY_E
                19  -> GLFW.GLFW_KEY_R
                20  -> GLFW.GLFW_KEY_T
                21  -> GLFW.GLFW_KEY_Y
                22  -> GLFW.GLFW_KEY_U
                23  -> GLFW.GLFW_KEY_I
                24  -> GLFW.GLFW_KEY_O
                25  -> GLFW.GLFW_KEY_P
                26  -> GLFW.GLFW_KEY_LEFT_BRACKET
                27  -> GLFW.GLFW_KEY_RIGHT_BRACKET
                28  -> GLFW.GLFW_KEY_ENTER
                29  -> GLFW.GLFW_KEY_LEFT_CONTROL
                30  -> GLFW.GLFW_KEY_A
                31  -> GLFW.GLFW_KEY_S
                32  -> GLFW.GLFW_KEY_D
                33  -> GLFW.GLFW_KEY_F
                34  -> GLFW.GLFW_KEY_G
                35  -> GLFW.GLFW_KEY_H
                36  -> GLFW.GLFW_KEY_J
                37  -> GLFW.GLFW_KEY_K
                38  -> GLFW.GLFW_KEY_L
                42  -> GLFW.GLFW_KEY_LEFT_SHIFT
                44  -> GLFW.GLFW_KEY_Z
                45  -> GLFW.GLFW_KEY_X
                46  -> GLFW.GLFW_KEY_C
                47  -> GLFW.GLFW_KEY_V
                48  -> GLFW.GLFW_KEY_B
                49  -> GLFW.GLFW_KEY_N
                50  -> GLFW.GLFW_KEY_M
                57  -> GLFW.GLFW_KEY_SPACE
                157 -> GLFW.GLFW_KEY_RIGHT_CONTROL
                184 -> GLFW.GLFW_KEY_RIGHT_ALT
                199 -> GLFW.GLFW_KEY_HOME
                200 -> GLFW.GLFW_KEY_UP
                203 -> GLFW.GLFW_KEY_LEFT
                205 -> GLFW.GLFW_KEY_RIGHT
                207 -> GLFW.GLFW_KEY_END
                208 -> GLFW.GLFW_KEY_DOWN
                210 -> GLFW.GLFW_KEY_INSERT
                211 -> GLFW.GLFW_KEY_DELETE
                else -> -1
            }
        }
    }
}
