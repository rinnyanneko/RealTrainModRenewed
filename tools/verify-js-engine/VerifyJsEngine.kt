import java.util.jar.JarFile
import javax.script.ScriptEngineManager

fun main(args: Array<String>) {
    require(args.size == 1) { "Expected path to the built mod jar" }

    JarFile(args[0]).use { jar ->
        val multiRelease = jar.manifest.mainAttributes.getValue("Multi-Release")
        check(multiRelease.equals("true", ignoreCase = true)) {
            "Built jar must preserve Multi-Release: true for Graal/Truffle"
        }
        val providers = jar.getInputStream(
            jar.getJarEntry("META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider")
                ?: error("Truffle language provider service is missing"),
        ).bufferedReader().use { it.readText() }
        check("com.oracle.truffle.js.lang.JavaScriptLanguageProvider" in providers) {
            "JavaScript language provider is missing"
        }
        check("com.oracle.truffle.regex.RegexLanguageProvider" in providers) {
            "Regex language provider is missing"
        }
    }

    val engine = ScriptEngineManager(Thread.currentThread().contextClassLoader)
        .getEngineByName("js")
        ?: error("GraalJS script engine is not discoverable from the built jar")
    val result = engine.eval("1 + 2")
    check(result is Number && result.toInt() == 3) {
        "GraalJS script engine returned unexpected eval result: $result"
    }
    val regexResult = engine.eval("/rail-(\\d+)/.exec('rail-42')[1]")
    check(regexResult == "42") {
        "Bundled GraalJS regex language returned unexpected result: $regexResult"
    }
    println("Verified GraalJS script engine: ${engine.javaClass.name}")
}
