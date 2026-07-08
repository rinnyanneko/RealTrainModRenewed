import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.jar.JarFile;

public final class VerifyJsEngine {
    private VerifyJsEngine() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected path to the built mod jar");
        }
        try (JarFile jar = new JarFile(args[0])) {
            String multiRelease = jar.getManifest().getMainAttributes().getValue("Multi-Release");
            if (!"true".equalsIgnoreCase(multiRelease)) {
                throw new IllegalStateException("Built jar must preserve Multi-Release: true for Graal/Truffle");
            }
        }

        ScriptEngine engine = new ScriptEngineManager(Thread.currentThread().getContextClassLoader())
            .getEngineByName("js");
        if (engine == null) {
            throw new IllegalStateException("GraalJS script engine is not discoverable from the built jar");
        }
        Object result = engine.eval("1 + 2");
        if (!(result instanceof Number number) || number.intValue() != 3) {
            throw new IllegalStateException("GraalJS script engine returned unexpected eval result: " + result);
        }
        System.out.println("Verified GraalJS script engine: " + engine.getClass().getName());
    }
}
