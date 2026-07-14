package dev.wirecraft.transport;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

abstract class Tunnel implements AutoCloseable {
    final Map<String, Socket> streams = new ConcurrentHashMap<>();
    final RelaySocket relay;
    final Consumer<String> status;
    Tunnel(String url, String code, String proof, String hostToken, boolean isHost, Consumer<String> status) {
        this.status = status; this.relay = new RelaySocket(url, code, proof, hostToken, isHost, this::receive, status);
    }
    void receive(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";
        if (type.equals("ready")) status.accept("WireCraft Relay подключён");
        else if (type.equals("data")) write(message.get("stream").getAsString(), message.get("payload").getAsString());
        else if (type.equals("close")) closeStream(message.get("stream").getAsString());
        else if (type.equals("error")) status.accept(message.get("error").getAsString());
        else receiveControl(message);
    }
    abstract void receiveControl(JsonObject message);
    void pump(String id, Socket socket) {
        Thread.ofVirtual().start(() -> {
            try (InputStream input = socket.getInputStream()) {
                byte[] buffer = new byte[16 * 1024]; int count;
                while ((count = input.read(buffer)) >= 0) {
                    JsonObject data = new JsonObject(); data.addProperty("type", "data"); data.addProperty("stream", id);
                    data.addProperty("payload", Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer, count))); relay.send(data);
                }
            } catch (IOException ignored) { } finally { sendClose(id); closeStream(id); }
        });
    }
    private void write(String id, String encoded) {
        Socket socket = streams.get(id); if (socket == null) return;
        try { OutputStream output = socket.getOutputStream(); output.write(Base64.getDecoder().decode(encoded)); output.flush(); }
        catch (IOException ignored) { closeStream(id); }
    }
    void sendClose(String id) { JsonObject close = new JsonObject(); close.addProperty("type", "close"); close.addProperty("stream", id); relay.send(close); }
    void closeStream(String id) { Socket socket = streams.remove(id); if (socket != null) try { socket.close(); } catch (IOException ignored) { } }
    @Override public void close() { streams.keySet().forEach(this::closeStream); relay.close(); }
}
