package dev.wirecraft.transport;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.function.Consumer;

public final class GuestTunnel extends Tunnel {
    private ServerSocket listener;
    public GuestTunnel(String url, String code, String proof, Consumer<String> status) { super(url, code, proof, "", false, status); }
    public int start() {
        try {
            listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            Thread.ofVirtual().start(() -> { while (!listener.isClosed()) try { open(listener.accept()); } catch (IOException ignored) { } });
            return listener.getLocalPort();
        } catch (IOException ex) { throw new IllegalStateException("Нельзя запустить локальный прокси", ex); }
    }
    private void open(Socket socket) {
        String id = UUID.randomUUID().toString(); streams.put(id, socket);
        JsonObject open = new JsonObject(); open.addProperty("type", "open"); open.addProperty("stream", id); relay.send(open); pump(id, socket);
    }
    @Override void receiveControl(JsonObject message) { }
    @Override public void close() { super.close(); if (listener != null) try { listener.close(); } catch (IOException ignored) { } }
}
