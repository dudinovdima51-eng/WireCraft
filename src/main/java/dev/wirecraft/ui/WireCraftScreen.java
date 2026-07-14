package dev.wirecraft.ui;

import dev.wirecraft.room.RoomManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class WireCraftScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget code, password;
    public WireCraftScreen(Screen parent) { super(Text.literal("WireCraft")); this.parent = parent; }
    @Override protected void init() {
        int left = width / 2 - 100;
        code = new TextFieldWidget(textRenderer, left, height / 2 - 34, 200, 20, Text.literal("Код комнаты"));
        code.setPlaceholder(Text.literal("Код комнаты")); code.setMaxLength(6); addDrawableChild(code);
        password = new TextFieldWidget(textRenderer, left, height / 2 - 8, 200, 20, Text.literal("Пароль"));
        password.setPlaceholder(Text.literal("Пароль (минимум 6)")); password.setMaxLength(128); password.setDrawsBackground(true); addDrawableChild(password);
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать комнату"), button -> create()).dimensions(left, height / 2 + 20, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Войти"), button -> join()).dimensions(left + 102, height / 2 + 20, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Новый мир"), button -> newWorld()).dimensions(left, height / 2 + 46, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть комнату"), button -> RoomManager.closeRoom()).dimensions(left + 102, height / 2 + 46, 98, 20).build());
    }
    private void create() { RoomManager.createRoom(password.getText()).thenAccept(room -> { if (room != null) code.setText(room); }); }
    private void join() { RoomManager.joinRoom(code.getText(), password.getText()).thenAccept(port -> client.execute(() -> RoomManager.connectToLocal(client, this, port))); }
    private void newWorld() {
        try {
            Class<?> screen = Class.forName("net.minecraft.client.gui.screen.world.CreateWorldScreen");
            var create = java.util.Arrays.stream(screen.getMethods()).filter(method -> method.getName().equals("create") && method.getParameterCount() == 2).findFirst().orElseThrow();
            client.setScreen((Screen) create.invoke(null, client, this));
        } catch (Exception ex) { client.setScreen(parent); }
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 70, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, RoomManager.detail(), width / 2, height / 2 + 76, 0xA0FFA0);
    }
}
