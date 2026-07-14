package dev.wirecraft.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

final class RelaySocket implements WebSocket.Listener, AutoCloseable {
    private final String code, proof, hostToken;
    private final boolean host;
    private final Consumer<JsonObject> messages;
    private final Consumer<String> status;
    private volatile WebSocket socket;

    RelaySocket(String url, String code, String proof, String hostToken, boolean host, Consumer<JsonObject> messages, Consumer<String> status) {
        this.code = code; this.proof = proof; this.hostToken = hostToken; this.host = host; this.messages = messages; this.status = status;
        HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(url), this).thenAccept(value -> socket = value)
                .exceptionally(ex -> { status.accept("Relay недоступен: " + ex.getMessage()); return null; });
    }
    @Override public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        JsonObject auth = new JsonObject(); auth.addProperty("type", "auth"); auth.addProperty("role", host ? "host" : "guest");
        auth.addProperty("code", code); auth.addProperty("proof", proof); if (host) auth.addProperty("hostToken", hostToken);
        send(auth); webSocket.request(1);
    }
    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (last) { try { messages.accept(JsonParser.parseString(data.toString()).getAsJsonObject()); } catch (Exception ignored) { } }
        webSocket.request(1); return CompletableFuture.completedFuture(null);
    }
    @Override public void onError(WebSocket webSocket, Throwable error) { status.accept("Соединение с Relay потеряно"); }
    void send(JsonObject object) { WebSocket value = socket; if (value != null) value.sendText(object.toString(), true); }
    @Override public void close() { if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "closed"); }
}
