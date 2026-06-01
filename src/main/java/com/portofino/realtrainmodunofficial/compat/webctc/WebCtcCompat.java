package com.portofino.realtrainmodunofficial.compat.webctc;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.compat.atsassist.AtsaTrainController;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;
import java.util.stream.Collectors;

public final class WebCtcCompat {
    private static final int PORT = Integer.getInteger("webctc.port", 8080);
    private static HttpServer httpServer;
    private static MinecraftServer minecraftServer;

    private WebCtcCompat() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        minecraftServer = event.getServer();
        stop();
        try {
            httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
            httpServer.createContext("/", WebCtcCompat::handle);
            httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "RTMU-WebCTC");
                thread.setDaemon(true);
                return thread;
            }));
            httpServer.start();
            RealTrainModUnofficial.LOGGER.info("WebCTC compatibility server started on http://127.0.0.1:{}/", PORT);
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to start WebCTC compatibility server", e);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        stop();
        minecraftServer = null;
    }

    private static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/trains".equals(path)) {
            send(exchange, 200, "application/json", trainsJson());
        } else if (path.startsWith("/api/trains/") && path.endsWith("/notch")) {
            setTrainNotch(exchange, path);
        } else if (path.startsWith("/api/trains/") && path.endsWith("/state")) {
            setTrainState(exchange, path);
        } else if ("/api/rails".equals(path)) {
            send(exchange, 200, "application/json", railsJson());
        } else if ("/api/formations".equals(path)) {
            send(exchange, 200, "application/json", formationsJson());
        } else if ("/api/signals".equals(path)) {
            send(exchange, 200, "application/json", signalsJson());
        } else if ("/api/waypoints".equals(path) || "/api/railgroups".equals(path) || "/api/tecons".equals(path)) {
            storedJson(exchange, path.substring("/api/".length()));
        } else if (path.startsWith("/api")) {
            send(exchange, 404, "application/json", "{\"error\":\"not found\"}");
        } else {
            sendStatic(exchange, path);
        }
    }

    private static String trainsJson() {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return "[]";
        }
        return minecraftServer.overworld().getAllEntities().spliterator().hasCharacteristics(0)
            ? StreamSupport.stream(minecraftServer.overworld().getEntities().getAll().spliterator(), false)
                .filter(TrainEntity.class::isInstance)
                .map(TrainEntity.class::cast)
                .map(WebCtcCompat::trainJson)
                .collect(Collectors.joining(",", "[", "]"))
            : "[]";
    }

    private static String trainJson(TrainEntity train) {
        return "{"
            + "\"entityId\":" + train.getId()
            + ",\"modelName\":\"" + escape(train.getVehicleId()) + "\""
            + ",\"speed\":" + train.getSpeed()
            + ",\"notch\":" + train.getNotch()
            + ",\"doorOpen\":" + train.isDoorOpen()
            + ",\"reverser\":" + train.getReverser()
            + ",\"trainProtection\":\"" + escape(train.getScriptDataValue(AtsaTrainController.KEY_TP)) + "\""
            + ",\"speedLimit\":\"" + escape(train.getScriptDataValue(AtsaTrainController.KEY_SPEED_LIMIT)) + "\""
            + ",\"x\":" + train.getX()
            + ",\"y\":" + train.getY()
            + ",\"z\":" + train.getZ()
            + "}";
    }

    private static String railsJson() {
        return RailRegistry.getAll().stream()
            .map(rail -> "{\"id\":\"" + escape(rail.getId()) + "\",\"name\":\"" + escape(rail.getDisplayName()) + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static String formationsJson() {
        return trainsJson();
    }

    private static String signalsJson() {
        return "[]";
    }

    private static void storedJson(HttpExchange exchange, String key) throws IOException {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            send(exchange, 503, "application/json", "{\"error\":\"server not ready\"}");
            return;
        }
        WebCtcSavedData data = WebCtcSavedData.get(minecraftServer.overworld());
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 200, "application/json", data.get(key));
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            data.set(key, readBody(exchange));
            send(exchange, 200, "application/json", data.get(key));
            return;
        }
        send(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}");
    }

    private static void setTrainNotch(HttpExchange exchange, String path) throws IOException {
        TrainEntity train = trainFromPath(path);
        if (train == null) {
            send(exchange, 404, "application/json", "{\"error\":\"train not found\"}");
            return;
        }
        int notch = parseBodyInt(exchange, "notch", 0);
        minecraftServer.execute(() -> train.setNotch(notch));
        send(exchange, 200, "application/json", trainJson(train));
    }

    private static void setTrainState(HttpExchange exchange, String path) throws IOException {
        TrainEntity train = trainFromPath(path);
        if (train == null) {
            send(exchange, 404, "application/json", "{\"error\":\"train not found\"}");
            return;
        }
        String body = readBody(exchange);
        int state = parseJsonInt(body, "state", -1);
        float value = (float) parseJsonDouble(body, "value", 0.0D);
        minecraftServer.execute(() -> {
            if (state >= 0) {
                train.syncVehicleState(state, value);
            }
        });
        send(exchange, 200, "application/json", trainJson(train));
    }

    private static TrainEntity trainFromPath(String path) {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return null;
        }
        String[] parts = path.split("/");
        if (parts.length < 4) {
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        return StreamSupport.stream(minecraftServer.overworld().getEntities().getAll().spliterator(), false)
            .filter(TrainEntity.class::isInstance)
            .map(TrainEntity.class::cast)
            .filter(train -> train.getId() == id)
            .findFirst()
            .orElse(null);
    }

    private static String indexHtml() {
        return """
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
                  document.querySelector('#trains tbody').innerHTML=trains.map(t=>`<tr><td>${t.entityId}</td><td>${t.modelName}</td><td>${Number(t.speed).toFixed(3)}</td><td><button onclick="setNotch(${t.entityId},${t.notch-1})">-</button> ${t.notch} <button onclick="setNotch(${t.entityId},${t.notch+1})">+</button></td><td>${t.doorOpen}</td><td>${t.x.toFixed(1)}, ${t.y.toFixed(1)}, ${t.z.toFixed(1)}</td></tr>`).join('');
                  const rails=await fetch('/api/rails').then(r=>r.json());
                  document.querySelector('#rails').textContent=rails.map(r=>r.name||r.id).join(' / ');
                }
                async function setNotch(id,notch){await fetch(`/api/trains/${id}/notch`,{method:'POST',body:JSON.stringify({notch})}); refresh();}
                async function loadStore(k){storeKey=k; document.querySelector('#store').value=JSON.stringify(await fetch('/api/'+k).then(r=>r.json()),null,2);}
                async function saveStore(){await fetch('/api/'+storeKey,{method:'POST',body:document.querySelector('#store').value});}
                refresh(); setInterval(refresh, 1000);
                loadStore('waypoints');
              </script>
            </body>
            </html>
            """;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendBytes(exchange, status, contentType, bytes);
    }

    private static void sendStatic(HttpExchange exchange, String path) throws IOException {
        String safePath = path == null || path.equals("/") || path.isBlank() ? "/index.html" : path;
        if (safePath.contains("..")) {
            send(exchange, 400, "text/plain; charset=utf-8", "bad path");
            return;
        }
        String resourcePath = "/assets/webctc/html" + safePath;
        try (InputStream input = WebCtcCompat.class.getResourceAsStream(resourcePath)) {
            if (input != null) {
                sendBytes(exchange, 200, contentType(safePath), input.readAllBytes());
                return;
            }
        }
        if (!safePath.contains(".")) {
            try (InputStream input = WebCtcCompat.class.getResourceAsStream("/assets/webctc/html/index.html")) {
                if (input != null) {
                    sendBytes(exchange, 200, "text/html; charset=utf-8", input.readAllBytes());
                    return;
                }
            }
        }
        send(exchange, 200, "text/html; charset=utf-8", indexHtml());
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        return "application/octet-stream";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int parseBodyInt(HttpExchange exchange, String key, int fallback) throws IOException {
        return parseJsonInt(readBody(exchange), key, fallback);
    }

    private static int parseJsonInt(String body, String key, int fallback) {
        return (int) parseJsonDouble(body, key, fallback);
    }

    private static double parseJsonDouble(String body, String key, double fallback) {
        String needle = "\"" + key + "\"";
        int index = body.indexOf(needle);
        if (index < 0) {
            return fallback;
        }
        int colon = body.indexOf(':', index + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int end = colon + 1;
        while (end < body.length() && "-0123456789.".indexOf(body.charAt(end)) < 0) {
            end++;
        }
        int start = end;
        while (end < body.length() && "-0123456789.".indexOf(body.charAt(end)) >= 0) {
            end++;
        }
        try {
            return Double.parseDouble(body.substring(start, end));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
