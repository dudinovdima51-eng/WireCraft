package dev.wirecraft.room;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wirecraft.config.WireCraftConfig;
import dev.wirecraft.transport.GuestTunnel;
import dev.wirecraft.transport.HostTunnel;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Owns a single room per client. Network work is deliberately kept off the render thread. */
public final class RoomManager {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile RoomState state = RoomState.IDLE;
    private static volatile String detail = "";
    private static HostTunnel hostTunnel;
    private static GuestTunnel guestTunnel;

    private RoomManager() { }
    public static void initialize() { }
    public static RoomState state() { return state; }
    public static String detail() { return detail; }

    public static CompletableFuture<String> createRoom(String password) {
        if (password == null || password.length() < 6) return fail("Пароль должен содержать не меньше 6 символов");
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() == null) return fail("Сначала откройте одиночный мир");
        state = RoomState.CREATING; detail = "Открываем мир для комнаты…";
        return openIntegratedServer(client).thenCompose(port -> {
            String code = code();
            String salt = random(16);
            String hostToken = random(32);
            String proof = proof(salt, password);
            JsonObject request = new JsonObject();
            request.addProperty("code", code); request.addProperty("salt", salt);
            request.addProperty("proof", proof); request.addProperty("hostToken", hostToken);
            request.addProperty("maxPlayers", WireCraftConfig.get().maxPlayers);
            HttpRequest http = HttpRequest.newBuilder(restUri("/v1/rooms"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toString())).build();
            return HTTP.sendAsync(http, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.statusCode() != 201) throw new IllegalStateException(error(response.body()));
                hostTunnel = new HostTunnel(WireCraftConfig.get().relayUrl, code, proof, hostToken, port, RoomManager::setDetail);
                hostTunnel.start(); state = RoomState.HOSTING;
                detail = "Комната " + code + " открыта (Relay)";
                return code;
            });
        }).exceptionally(ex -> { state = RoomState.FAILED; detail = message(ex); return null; });
    }

    public static CompletableFuture<Integer> joinRoom(String code, String password) {
        if (!code.matches("[A-Za-z0-9]{6}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Код состоит из 6 символов"));
        if (password == null || password.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Введите пароль"));
        state = RoomState.JOINING; detail = "Подключаемся к комнате…";
        // The salt is deliberately public; it prevents the same password producing the same proof in every room.
        HttpRequest request = HttpRequest.newBuilder(restUri("/v1/rooms/" + code.toUpperCase(Locale.ROOT)))
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException(error(response.body()));
            String salt = JsonParser.parseString(response.body()).getAsJsonObject().get("salt").getAsString();
            return proof(salt, password);
        }).thenApply(roomProof -> {
            guestTunnel = new GuestTunnel(WireCraftConfig.get().relayUrl, code.toUpperCase(Locale.ROOT), roomProof, RoomManager::setDetail);
            int port = guestTunnel.start(); state = RoomState.CONNECTED;
            detail = "Готово. Подключитесь к 127.0.0.1:" + port;
            return port;
        }).exceptionally(ex -> { state = RoomState.FAILED; detail = message(ex); throw new IllegalStateException(detail); });
    }

    public static void closeRoom() {
        if (hostTunnel != null) hostTunnel.close();
        if (guestTunnel != null) guestTunnel.close();
        hostTunnel = null; guestTunnel = null; state = RoomState.CLOSED; detail = "Комната закрыта";
    }

    public static void connectToLocal(MinecraftClient client, Object parent, int port) {
        try {
            Class<?> infoClass = Class.forName("net.minecraft.client.network.ServerInfo");
            Object type = java.util.Arrays.stream(Class.forName("net.minecraft.client.network.ServerInfo$ServerType").getEnumConstants())
                    .filter(v -> v.toString().equals("OTHER")).findFirst().orElseThrow();
            Object info = java.util.Arrays.stream(infoClass.getConstructors()).filter(c -> c.getParameterCount() == 3).findFirst()
                    .orElseThrow().newInstance("WireCraft", "127.0.0.1:" + port, type);
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screen.multiplayer.ConnectScreen");
            var connect = java.util.Arrays.stream(screenClass.getMethods()).filter(m -> m.getName().equals("connect") && m.getParameterCount() >= 4).findFirst().orElseThrow();
            Object[] args = new Object[connect.getParameterCount()]; args[0] = parent; args[1] = client; args[2] = info; args[3] = false;
            for (int index = 4; index < args.length; index++) args[index] = null;
            connect.invoke(null, args);
        } catch (Exception ex) { detail = "Прокси готов: подключитесь к 127.0.0.1:" + port; }
    }

    private static CompletableFuture<String> fail(String reason) { state = RoomState.FAILED; detail = reason; return CompletableFuture.completedFuture(null); }
    private static void setDetail(String value) { detail = value; }
    private static URI restUri(String path) {
        String base = WireCraftConfig.get().relayUrl.replaceFirst("^ws", "http").replaceFirst("/ws$", "");
        return URI.create(base + path);
    }
    private static String code() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; StringBuilder value = new StringBuilder(6);
        for (int i = 0; i < 6; i++) value.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return value.toString();
    }
    private static String random(int bytes) { byte[] raw = new byte[bytes]; RANDOM.nextBytes(raw); return Base64.getUrlEncoder().withoutPadding().encodeToString(raw); }
    public static String proof(String salt, String password) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static String error(String body) { try { return JsonParser.parseString(body).getAsJsonObject().get("error").getAsString(); } catch (Exception ignored) { return "Relay недоступен"; } }
    private static String message(Throwable ex) { return ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage(); }

    // Uses reflection to remain compatible with Yarn name changes within 1.21.x.
    private static CompletableFuture<Integer> openIntegratedServer(MinecraftClient client) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                Object server = client.getServer();
                var open = java.util.Arrays.stream(server.getClass().getMethods()).filter(m -> m.getName().equals("openToLan") && m.getParameterCount() == 3).findFirst().orElseThrow();
                Class<?> gameMode = open.getParameterTypes()[0]; Object survival = gameMode.getEnumConstants()[0];
                for (Object candidate : gameMode.getEnumConstants()) if (candidate.toString().equals("SURVIVAL")) { survival = candidate; break; }
                open.invoke(server, survival, false, 0);
                var port = java.util.Arrays.stream(server.getClass().getMethods()).filter(m -> m.getName().equals("getServerPort") && m.getParameterCount() == 0).findFirst().orElseThrow();
                result.complete((Integer) port.invoke(server));
            } catch (Exception ex) { result.completeExceptionally(new IllegalStateException("Не удалось открыть одиночный мир для WireCraft", ex)); }
        });
        return result;
    }
}
