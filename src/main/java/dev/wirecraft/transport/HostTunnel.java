package dev.wirecraft.transport;

import com.google.gson.JsonObject;
import java.net.Socket;
import java.util.function.Consumer;

public final class HostTunnel extends Tunnel {
    private final int minecraftPort;
    public HostTunnel(String url, String code, String proof, String token, int minecraftPort, Consumer<String> status) {
        super(url, code, proof, token, true, status); this.minecraftPort = minecraftPort;
    }
    public void start() { }
    @Override void receiveControl(JsonObject message) {
        if (!message.has("type") || !message.get("type").getAsString().equals("open")) return;
        String id = message.get("stream").getAsString();
        try { Socket socket = new Socket("127.0.0.1", minecraftPort); streams.put(id, socket); pump(id, socket); }
        catch (Exception ex) { sendClose(id); status.accept("Не удалось подключить гостя к миру"); }
    }
}
