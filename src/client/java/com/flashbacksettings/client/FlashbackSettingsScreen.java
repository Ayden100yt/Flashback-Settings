package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FlashbackSettingsScreen extends Screen {

    private final Screen parent;
    private EditBox replayFolderBox;
    private static final String HINT = "Leave empty to use the default";

    public FlashbackSettingsScreen(Screen parent) {
        super(Component.literal("Flashback Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int baseY = this.height / 2 - 40;

        this.replayFolderBox = new EditBox(
            this.font,
            cx - 155, baseY,
            310, 20,
            Component.literal("Replay Folder")
        );
        this.replayFolderBox.setMaxLength(512);

        FlashbackSettings mod = FlashbackSettings.getInstance();
        String current = (mod != null && mod.getConfig().replayFolder != null)
            ? mod.getConfig().replayFolder : "";
        this.replayFolderBox.setValue(current);

        this.addRenderableWidget(this.replayFolderBox);

        FlashbackSettings.ModConfig config = FlashbackSettings.getInstance().getConfig();
        int optionsY = baseY + 30;
        this.addRenderableWidget(Button.builder(
            dynamicLabel(config.dynamicSubfolders), btn -> {
                config.dynamicSubfolders = !config.dynamicSubfolders;
                btn.setMessage(dynamicLabel(config.dynamicSubfolders));
            }
        ).bounds(cx - 155, optionsY, 310, 20).build());

        this.addRenderableWidget(Button.builder(
            componentLabel("Date", config.subfolderByDate), btn -> {
                config.subfolderByDate = !config.subfolderByDate;
                btn.setMessage(componentLabel("Date", config.subfolderByDate));
            }
        ).bounds(cx - 155, optionsY + 24, 100, 20).build());

        this.addRenderableWidget(Button.builder(
            componentLabel("Server", config.subfolderByServer), btn -> {
                config.subfolderByServer = !config.subfolderByServer;
                btn.setMessage(componentLabel("Server", config.subfolderByServer));
            }
        ).bounds(cx - 50, optionsY + 24, 100, 20).build());

        this.addRenderableWidget(Button.builder(
            componentLabel("Version", config.subfolderByVersion), btn -> {
                config.subfolderByVersion = !config.subfolderByVersion;
                btn.setMessage(componentLabel("Version", config.subfolderByVersion));
            }
        ).bounds(cx + 55, optionsY + 24, 100, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Done"),
            btn -> this.saveAndClose()
        ).bounds(cx - 155, optionsY + 52, 150, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            btn -> this.minecraft.setScreenAndShow(this.parent)
        ).bounds(cx + 5, optionsY + 52, 150, 20).build());
    }

    private void saveAndClose() {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod != null) {
            mod.getConfig().replayFolder = this.replayFolderBox.getValue().trim();
            mod.saveConfig();
            FlashbackSettings.LOGGER.info("[FlashbackSettings] Config updated via in-game screen.");
        }
        this.minecraft.setScreenAndShow(this.parent);
    }

    private static Component dynamicLabel(boolean enabled) {
        return Component.literal("Dynamic Subfolders: " + (enabled ? "ON" : "OFF"));
    }

    private static Component componentLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        this.extractBackground(gfx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int baseY = this.height / 2 - 40;

        gfx.centeredText(this.font, this.title, cx, baseY - 28, 0xFFFFFFFF);
        gfx.text(this.font, "Replay Folder Path:", cx - 155, baseY - 12, 0xFFA0A0A0, false);
        gfx.centeredText(this.font, HINT, cx, baseY + 84, 0xFF666666);

        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }
}
