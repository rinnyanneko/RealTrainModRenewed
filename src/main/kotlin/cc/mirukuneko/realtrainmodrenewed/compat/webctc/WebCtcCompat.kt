package cc.mirukuneko.realtrainmodrenewed.compat.webctc

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object WebCtcCompat {
    private val PORT: Int = Integer.getInteger("webctc.port", 8080)
    private var httpServer: HttpServer? = null
    private var minecraftServer: MinecraftServer? = null

    @JvmStatic
    fun onServerStarted(event: ServerStartedEvent) {
        minecraftServer = event.server
        stop()
        try {
            httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0).apply {
                createContext("/", WebCtcCompat::handle)
                executor = Executors.newCachedThreadPool { runnable ->
                    Thread(runnable, "RTMU-WebCTC").apply { isDaemon = true }
                }
                start()
            }
            RealTrainModRenewed.LOGGER.info("WebCTC compatibility server started on http://127.0.0.1:{}/", PORT)
        } catch (e: IOException) {
            RealTrainModRenewed.LOGGER.warn("Failed to start WebCTC compatibility server", e)
        }
    }

    @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        stop()
        minecraftServer = null
    }

    private fun stop() {
        httpServer?.stop(0)
        httpServer = null
    }

    @Throws(IOException::class)
    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        when {
            path == "/api/trains" -> send(exchange, 200, "application/json", trainsJson())
            path.startsWith("/api/trains/") && path.endsWith("/notch") -> setTrainNotch(exchange, path)
            path.startsWith("/api/trains/") && path.endsWith("/state") -> setTrainState(exchange, path)
            path == "/api/rails" -> send(exchange, 200, "application/json", railsJson())
            path == "/api/formations" -> send(exchange, 200, "application/json", formationsJson())
            path == "/api/signals" -> send(exchange, 200, "application/json", signalsJson())
            path == "/api/waypoints" || path == "/api/railgroups" || path == "/api/tecons" ->
                storedJson(exchange, path.substring("/api/".length))
            path.startsWith("/api") -> send(exchange, 404, "application/json", "{\"error\":\"not found\"}")
            else -> sendStatic(exchange, path)
        }
    }

    private fun trainsJson(): String {
        val level = minecraftServer?.overworld() ?: return "[]"
        return level.entities.all.asSequence()
            .filterIsInstance<TrainEntity>()
            .joinToString(prefix = "[", postfix = "]") { trainJson(it) }
    }

    private fun trainJson(train: TrainEntity): String = buildString {
        append("{")
        append("\"entityId\":").append(train.id)
        append(",\"modelName\":\"").append(escape(train.vehicleId)).append("\"")
        append(",\"speed\":").append(train.speed)
        append(",\"notch\":").append(train.notch)
        append(",\"doorOpen\":").append(train.isDoorOpen)
        append(",\"reverser\":").append(train.reverser)
        append(",\"trainProtection\":\"").append(escape(train.getScriptDataValue("ATSAssist_CurrentTP"))).append("\"")
        append(",\"speedLimit\":\"").append(escape(train.getScriptDataValue("ATSAssist_SpeedLimit"))).append("\"")
        append(",\"x\":").append(train.x)
        append(",\"y\":").append(train.y)
        append(",\"z\":").append(train.z)
        append("}")
    }

    private fun railsJson(): String =
        RailRegistry.getAll().joinToString(prefix = "[", postfix = "]") { rail ->
            "{\"id\":\"${escape(rail.id)}\",\"name\":\"${escape(rail.displayName)}\"}"
        }

    private fun formationsJson(): String = trainsJson()

    private fun signalsJson(): String = "[]"

    @Throws(IOException::class)
    private fun storedJson(exchange: HttpExchange, key: String) {
        val level = minecraftServer?.overworld()
        if (level == null) {
            send(exchange, 503, "application/json", "{\"error\":\"server not ready\"}")
            return
        }
        val data = WebCtcSavedData.get(level)
        when {
            exchange.requestMethod.equals("GET", ignoreCase = true) ->
                send(exchange, 200, "application/json", data.get(key))
            exchange.requestMethod.equals("POST", ignoreCase = true) ||
                exchange.requestMethod.equals("PUT", ignoreCase = true) -> {
                data.set(key, readBody(exchange))
                send(exchange, 200, "application/json", data.get(key))
            }
            else -> send(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}")
        }
    }

    @Throws(IOException::class)
    private fun setTrainNotch(exchange: HttpExchange, path: String) {
        val train = trainFromPath(path)
        if (train == null) {
            send(exchange, 404, "application/json", "{\"error\":\"train not found\"}")
            return
        }
        val notch = parseBodyInt(exchange, "notch", 0)
        minecraftServer?.execute { train.notch = notch }
        send(exchange, 200, "application/json", trainJson(train))
    }

    @Throws(IOException::class)
    private fun setTrainState(exchange: HttpExchange, path: String) {
        val train = trainFromPath(path)
        if (train == null) {
            send(exchange, 404, "application/json", "{\"error\":\"train not found\"}")
            return
        }
        val body = readBody(exchange)
        val state = parseJsonInt(body, "state", -1)
        val value = parseJsonDouble(body, "value", 0.0).toFloat()
        minecraftServer?.execute {
            if (state >= 0) {
                train.syncVehicleState(state, value)
            }
        }
        send(exchange, 200, "application/json", trainJson(train))
    }

    private fun trainFromPath(path: String): TrainEntity? {
        val level = minecraftServer?.overworld() ?: return null
        val parts = path.split("/")
        if (parts.size < 4) return null
        val id = parts[3].toIntOrNull() ?: return null
        return level.entities.all.asSequence()
            .filterIsInstance<TrainEntity>()
            .firstOrNull { it.id == id }
    }

    private fun indexHtml(): String = """
        <!doctype html>
        <html lang="ja">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>WebCTC</title>
          <style>
            body{margin:0;background:#101418;color:#e8eef2;font-family:system-ui,sans-serif}
            header{height:48px;display:flex;align-items:center;padding:0 18px;background:#1b252c;border-bottom:1px solid #2f424c}
            h1{font-size:18px;margin:0;color:#8de36f}main{padding:18px;display:grid;gap:16px}
            table{border-collapse:collapse;width:100%;background:#151d23}th,td{border:1px solid #2f424c;padding:7px 9px;text-align:left}
            th{background:#20303a}.muted{color:#9fb0b8}
          </style>
        </head>
        <body>
          <header><h1>WebCTC</h1></header>
          <main>
            <section><h2>Trains</h2><table id="trains"><thead><tr><th>ID</th><th>Model</th><th>Speed</th><th>Notch</th><th>Door</th><th>Pos</th></tr></thead><tbody></tbody></table></section>
            <section><h2>Editor</h2><textarea id="store" rows="8" style="width:100%;box-sizing:border-box;background:#151d23;color:#e8eef2;border:1px solid #2f424c"></textarea><p><button onclick="loadStore('waypoints')">Waypoints</button><button onclick="loadStore('railgroups')">Railgroups</button><button onclick="loadStore('tecons')">TeCons</button><button onclick="saveStore()">Save</button></p></section>
            <section><h2>Rails</h2><div id="rails" class="muted"></div></section>
          </main>
          <script>
            let storeKey='waypoints';
            async function refresh(){
              const trains=await fetch('/api/trains').then(r=>r.json());
              document.querySelector('#trains tbody').innerHTML=trains.map(t=>`<tr><td>${'$'}{t.entityId}</td><td>${'$'}{t.modelName}</td><td>${'$'}{Number(t.speed).toFixed(3)}</td><td><button onclick="setNotch(${'$'}{t.entityId},${'$'}{t.notch-1})">-</button> ${'$'}{t.notch} <button onclick="setNotch(${'$'}{t.entityId},${'$'}{t.notch+1})">+</button></td><td>${'$'}{t.doorOpen}</td><td>${'$'}{t.x.toFixed(1)}, ${'$'}{t.y.toFixed(1)}, ${'$'}{t.z.toFixed(1)}</td></tr>`).join('');
              const rails=await fetch('/api/rails').then(r=>r.json());
              document.querySelector('#rails').textContent=rails.map(r=>r.name||r.id).join(' / ');
            }
            async function setNotch(id,notch){await fetch(`/api/trains/${'$'}{id}/notch`,{method:'POST',body:JSON.stringify({notch})}); refresh();}
            async function loadStore(k){storeKey=k; document.querySelector('#store').value=JSON.stringify(await fetch('/api/'+k).then(r=>r.json()),null,2);}
            async function saveStore(){await fetch('/api/'+storeKey,{method:'POST',body:document.querySelector('#store').value});}
            refresh(); setInterval(refresh, 1000);
            loadStore('waypoints');
          </script>
        </body>
        </html>
    """.trimIndent()

    @Throws(IOException::class)
    private fun send(exchange: HttpExchange, status: Int, contentType: String, body: String) {
        sendBytes(exchange, status, contentType, body.toByteArray(StandardCharsets.UTF_8))
    }

    @Throws(IOException::class)
    private fun sendStatic(exchange: HttpExchange, path: String?) {
        val safePath = if (path.isNullOrBlank() || path == "/") "/index.html" else path
        if (safePath.contains("..")) {
            send(exchange, 400, "text/plain; charset=utf-8", "bad path")
            return
        }
        val resourcePath = "/assets/webctc/html$safePath"
        WebCtcCompat::class.java.getResourceAsStream(resourcePath).useIfPresent {
            sendBytes(exchange, 200, contentType(safePath), it.readAllBytes())
            return
        }
        if (!safePath.contains(".")) {
            WebCtcCompat::class.java.getResourceAsStream("/assets/webctc/html/index.html").useIfPresent {
                sendBytes(exchange, 200, "text/html; charset=utf-8", it.readAllBytes())
                return
            }
        }
        send(exchange, 200, "text/html; charset=utf-8", indexHtml())
    }

    @Throws(IOException::class)
    private fun sendBytes(exchange: HttpExchange, status: Int, contentType: String, bytes: ByteArray) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { output -> output.write(bytes) }
    }

    private fun contentType(path: String): String = when {
        path.endsWith(".js") -> "application/javascript; charset=utf-8"
        path.endsWith(".css") -> "text/css; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        path.endsWith(".html") -> "text/html; charset=utf-8"
        else -> "application/octet-stream"
    }

    @Throws(IOException::class)
    private fun readBody(exchange: HttpExchange): String =
        exchange.requestBody.use { input -> String(input.readAllBytes(), StandardCharsets.UTF_8) }

    @Throws(IOException::class)
    private fun parseBodyInt(exchange: HttpExchange, key: String, fallback: Int): Int =
        parseJsonInt(readBody(exchange), key, fallback)

    private fun parseJsonInt(body: String, key: String, fallback: Int): Int =
        parseJsonDouble(body, key, fallback.toDouble()).toInt()

    private fun parseJsonDouble(body: String, key: String, fallback: Double): Double {
        val needle = "\"$key\""
        val index = body.indexOf(needle)
        if (index < 0) return fallback
        val colon = body.indexOf(':', index + needle.length)
        if (colon < 0) return fallback
        var end = colon + 1
        while (end < body.length && "-0123456789.".indexOf(body[end]) < 0) end++
        val start = end
        while (end < body.length && "-0123456789.".indexOf(body[end]) >= 0) end++
        return body.substring(start, end).toDoubleOrNull() ?: fallback
    }

    private fun escape(value: String?): String =
        value?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: ""

    private inline fun InputStream?.useIfPresent(block: (InputStream) -> Unit) {
        this?.use(block)
    }
}
