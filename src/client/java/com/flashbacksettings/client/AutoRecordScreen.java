package com.flashbacksettings.client;

import com.flashbacksettings.client.FancyUi.FancyButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class AutoRecordScreen extends Screen {
    private static final int CONTENT_WIDTH = 310;
    private static final int PANEL_PADDING = 10;
    private static final int TOTAL_HEIGHT = 196;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_OK = 0xFF7BE07B;
    private static final int COLOR_WARN = 0xFFF2C14E;
    private static final int COLOR_PANEL = 0xA0101010;
    private static final int COLOR_PANEL_BORDER = 0xFF3C3C3C;
    private static final int COLOR_DIVIDER = 0xFF444444;

    private final FlashbackSettingsScreen parent;
    private final boolean fancy;

    private boolean enabled;
    private boolean singleplayer;

    private EditBox serversBox;
    private Button addCurrentButton;
    private Button singleplayerButton;

    private int left;
    private int top;

    public AutoRecordScreen(FlashbackSettingsScreen parent) {
        super(Component.literal("Auto-Record"));
        this.parent = parent;
        this.fancy = parent.isFancy();
        this.enabled = parent.autoPerServer;
        this.singleplayer = parent.autoSingleplayer;
    }

    @Override
    protected void init() {
        this.left = Math.max(PANEL_PADDING, this.width / 2 - CONTENT_WIDTH / 2);
        this.top = Math.max(8, (this.height - TOTAL_HEIGHT) / 2);

        this.addRenderableWidget(this.toggle("Per-Server Auto-Record", () -> this.enabled, () -> {
                this.enabled = !this.enabled;
                this.updateActive();
            }, left, top + 26, CONTENT_WIDTH,
            "When ON, these rules decide when recording starts automatically and replace Flashback's own \"Automatically start recording\" option."));

        String previous = this.serversBox != null ? this.serversBox.getValue() : AutoRecord.formatList(this.parent.autoServers);
        int boxWidth = CONTENT_WIDTH - 64;
        if (this.fancy) {
            this.serversBox = new EditBox(this.font, left + 6, top + 74 + 6, boxWidth - 12, 10, Component.literal("Servers"));
            this.serversBox.setBordered(false);
            this.serversBox.setTextShadow(false);
            this.serversBox.setHint(FancyUi.text("e.g. hypixel.net, play.example.com").withColor(0xFF6A6A6A));
        } else {
            this.serversBox = new EditBox(this.font, left, top + 74, boxWidth, 20, Component.literal("Servers"));
            this.serversBox.setHint(Component.literal("e.g. hypixel.net, play.example.com").withStyle(ChatFormatting.DARK_GRAY));
        }
        this.serversBox.setMaxLength(2048);
        this.serversBox.setValue(previous);
        this.serversBox.setResponder(value -> this.updateActive());
        this.addRenderableWidget(this.serversBox);

        this.addCurrentButton = this.addRenderableWidget(this.button("+ Current", this::addCurrentServer,
            left + CONTENT_WIDTH - 60, top + 74, 60,
            "Add the server you're playing on right now."));

        this.singleplayerButton = this.addRenderableWidget(this.toggle("Record in Singleplayer", () -> this.singleplayer,
            () -> this.singleplayer = !this.singleplayer,
            left, top + 134, CONTENT_WIDTH,
            "Start recording automatically when you open a singleplayer world."));

        int half = (CONTENT_WIDTH - 6) / 2;
        this.addRenderableWidget(this.button("Done", this::applyAndClose, left, top + TOTAL_HEIGHT - 20, half, null));
        this.addRenderableWidget(this.button("Cancel", this::onClose, left + CONTENT_WIDTH - half, top + TOTAL_HEIGHT - 20, half, null));

        this.updateActive();
        this.setInitialFocus(this.serversBox);
    }

    private void addCurrentServer() {
        String host = AutoRecord.currentHost();
        if (host == null) {
            return;
        }
        List<String> list = new ArrayList<>(AutoRecord.parseList(this.serversBox.getValue()));
        if (!AutoRecord.matchesAny(host, list)) {
            list.add(host);
            this.serversBox.setValue(AutoRecord.formatList(list));
        }
        this.updateActive();
    }

    private void updateActive() {
        if (this.serversBox == null) {
            return;
        }
        this.serversBox.setEditable(this.enabled);
        this.singleplayerButton.active = this.enabled;
        String host = AutoRecord.currentHost();
        this.addCurrentButton.active = this.enabled && host != null
            && !AutoRecord.matchesAny(host, AutoRecord.parseList(this.serversBox.getValue()));
    }

    private void applyAndClose() {
        this.parent.autoPerServer = this.enabled;
        this.parent.autoServers = AutoRecord.parseList(this.serversBox.getValue());
        this.parent.autoSingleplayer = this.singleplayer;
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    private Component statusText() {
        if (!this.enabled) {
            return Component.literal("Off - Flashback's own auto-start option is used");
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            return Component.literal("Subdomains match too: hypixel.net = mc.hypixel.net");
        }
        String host = AutoRecord.currentHost();
        boolean records = host == null
            ? this.singleplayer
            : AutoRecord.matchesAny(host, AutoRecord.parseList(this.serversBox.getValue()));
        String where = host == null ? "Singleplayer" : host;
        return Component.literal(where + ": " + (records ? "will auto-record" : "won't auto-record"));
    }

    private int statusColor() {
        if (!this.enabled || this.minecraft == null || this.minecraft.level == null) {
            return this.fancy ? FancyUi.TEXT_MUTED : COLOR_MUTED;
        }
        String host = AutoRecord.currentHost();
        boolean records = host == null
            ? this.singleplayer
            : AutoRecord.matchesAny(host, AutoRecord.parseList(this.serversBox.getValue()));
        return records ? COLOR_OK : COLOR_WARN;
    }

    private Button button(String label, Runnable action, int x, int y, int w, String tooltip) {
        if (this.fancy) {
            return this.fancyButton(label, action, x, y, w, null, tooltip);
        }
        Button.Builder builder = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y, w, 20);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return builder.build();
    }

    private Button toggle(String label, BooleanSupplier state, Runnable flip, int x, int y, int w, String tooltip) {
        if (this.fancy) {
            return this.fancyButton(label, flip, x, y, w, state, tooltip);
        }
        Button.Builder builder = Button.builder(toggleLabel(label, state.getAsBoolean()), btn -> {
                flip.run();
                btn.setMessage(toggleLabel(label, state.getAsBoolean()));
            })
            .bounds(x, y, w, 20);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return builder.build();
    }

    private Button fancyButton(String label, Runnable action, int x, int y, int w, BooleanSupplier toggle, String tooltip) {
        FancyButton button = new FancyButton(x, y, w, 20, Component.literal(label), btn -> action.run(), toggle, null);
        if (tooltip != null) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return button;
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": ").append(enabled
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private Component styled(String text) {
        return this.fancy ? FancyUi.text(text) : Component.literal(text);
    }

    private Component styled(Component text) {
        return this.fancy ? FancyUi.text(text) : text;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int right = left + CONTENT_WIDTH;
        int panelTop = top + 16;
        int panelBottom = top + TOTAL_HEIGHT - 28;
        int label = this.fancy ? FancyUi.TEXT_MUTED : COLOR_LABEL;
        int divider = this.fancy ? FancyUi.DIVIDER : COLOR_DIVIDER;

        if (this.fancy) {
            gfx.centeredText(this.font, styled(this.title), cx, top + 1, FancyUi.TEXT);
            int panelX = left - PANEL_PADDING;
            int panelW = CONTENT_WIDTH + PANEL_PADDING * 2;
            FancyUi.rounded(gfx, panelX, panelTop, panelW, panelBottom - panelTop, FancyUi.PANEL);
            FancyUi.roundedOutline(gfx, panelX, panelTop, panelW, panelBottom - panelTop, FancyUi.PANEL_BORDER);

            int boxW = CONTENT_WIDTH - 64;
            FancyUi.rounded(gfx, left, top + 74, boxW, 20, FancyUi.FIELD);
            FancyUi.roundedOutline(gfx, left, top + 74, boxW, 20,
                this.serversBox.isFocused() ? 0x80FFFFFF : 0x24FFFFFF);
        } else {
            gfx.centeredText(this.font, this.title, cx, top, COLOR_TITLE);
            gfx.fill(left - PANEL_PADDING, panelTop, right + PANEL_PADDING, panelBottom, COLOR_PANEL);
            gfx.outline(left - PANEL_PADDING, panelTop, CONTENT_WIDTH + PANEL_PADDING * 2, panelBottom - panelTop, COLOR_PANEL_BORDER);
        }

        gfx.fill(left, top + 52, right, top + 53, divider);
        gfx.text(this.font, styled("Always record on these servers"), left, top + 60, label, !this.fancy);
        gfx.text(this.font, styled("Separate with commas"), left + 2, top + 98, this.fancy ? FancyUi.TEXT_MUTED : COLOR_MUTED, false);

        gfx.fill(left, top + 112, right, top + 113, divider);
        gfx.text(this.font, styled("Singleplayer"), left, top + 120, label, !this.fancy);

        gfx.text(this.font, styled(this.statusText()), left + 2, top + 157, this.statusColor(), false);

        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }
}
