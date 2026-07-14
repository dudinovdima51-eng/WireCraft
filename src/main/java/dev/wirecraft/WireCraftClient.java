package dev.wirecraft;

import dev.wirecraft.config.WireCraftConfig;
import dev.wirecraft.room.RoomManager;
import dev.wirecraft.ui.WireCraftScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class WireCraftClient implements ClientModInitializer {
    public static final String MOD_ID = "wirecraft";
    private static final KeyBinding OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.wirecraft.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.wirecraft"));

    @Override public void onInitializeClient() {
        WireCraftConfig.load();
        RoomManager.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.wasPressed()) client.setScreen(new WireCraftScreen(client.currentScreen));
        });
    }
}
