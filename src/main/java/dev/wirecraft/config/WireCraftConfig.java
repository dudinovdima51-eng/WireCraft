package dev.wirecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.wirecraft.WireCraftClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WireCraftConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("wirecraft.json");
    private static WireCraftConfig instance = new WireCraftConfig();

    /** Public WireCraft Relay hosted on the project VPS; can be overridden in config/wirecraft.json. */
    public String relayUrl = "ws://192.124.189.132:8080/ws";
    public int maxPlayers = 8;
    public int directConnectTimeoutMs = 3500;

    public static WireCraftConfig get() { return instance; }
    public static void load() {
        try {
            if (Files.exists(PATH)) instance = GSON.fromJson(Files.readString(PATH), WireCraftConfig.class);
            else save();
        } catch (Exception ignored) { instance = new WireCraftConfig(); }
    }
    public static void save() throws IOException {
        Files.createDirectories(PATH.getParent());
        Files.writeString(PATH, GSON.toJson(instance));
    }
}
