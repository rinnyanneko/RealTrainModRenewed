import java.util.jar.JarFile
import javax.script.ScriptEngineManager

fun main(args: Array<String>) {
    require(args.size == 1) { "Expected path to the built mod jar" }

    JarFile(args[0]).use { jar ->
        val multiRelease = jar.manifest.mainAttributes.getValue("Multi-Release")
        check(multiRelease.equals("true", ignoreCase = true)) {
            "Built jar must preserve Multi-Release: true for Graal/Truffle"
        }
    }

    val engine = ScriptEngineManager(Thread.currentThread().contextClassLoader)
        .getEngineByName("js")
        ?: error("GraalJS script engine is not discoverable from the built jar")
    val result = engine.eval("1 + 2")
    check(result is Number && result.toInt() == 3) {
        "GraalJS script engine returned unexpected eval result: $result"
    }
    println("Verified GraalJS script engine: ${engine.javaClass.name}")
}
