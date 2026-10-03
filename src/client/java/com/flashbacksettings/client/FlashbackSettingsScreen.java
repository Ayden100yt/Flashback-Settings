package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.Flashback;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;

public class FlashbackSettingsScreen extends Screen {

    // Layout
    private static final int CONTENT_WIDTH = 310;
    private static final int PANEL_PADDING = 10;
    private static final int TOTAL_HEIGHT = 196;

    // Colors (ARGB)
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_OK = 0xFF7BE07B;
    private static final int COLOR_WARN = 0xFFF2C14E;
    private static final int COLOR_ERROR = 0xFFFF6B6B;
    private static final int COLOR_PANEL = 0xA0101010;
    private static final int COLOR_PANEL_BORDER = 0xFF3C3C3C;
    private static final int COLOR_DIVIDER = 0xFF444444;

    private final Screen parent;

    // Working copies — only written to the config when "Done" is pressed,
    // so "Cancel" / Escape really discard changes.
    private boolean subfolders;
    private boolean byDate;
    private boolean byServer;
    private boolean byVersion;

    private EditBox replayFolderBox;
    private Button dateButton;
    private Button serverButton;
    private Button versionButton;
    private Button doneButton;

    private int left;
    private int top;

    // Cached folder status, recomputed whenever the text changes
    private Component pathStatus = Component.empty();
    private int pathStatusColor = COLOR_MUTED;
    private boolean pathValid = true;

    public FlashbackSettingsScreen(Screen parent) {
        super(Component.literal("Flashback Settings"));
        this.parent = parent;

        FlashbackSettings mod = FlashbackSettings.getInstance();
        FlashbackSettings.ModConfig config = mod != null ? mod.getConfig() : new FlashbackSettings.ModConfig();
        this.subfolders = config.dynamicSubfolders;
        this.byDate = config.subfolderByDate;
        this.byServer = config.subfolderByServer;
        this.byVersion = config.subfolderByVersion;
    }

    @Override
    protected void init() {
        this.left = this.width / 2 - CONTENT_WIDTH / 2;
        this.top = Math.max(8, (this.height - TOTAL_HEIGHT) / 2);

        // ---- Replay folder ----------------------------------------------
        String previous = this.replayFolderBox != null ? this.replayFolderBox.getValue() : null;

        this.replayFolderBox = new EditBox(this.font, left, top + 44, CONTENT_WIDTH - 64, 20,
            Component.literal("Replay Folder"));
        this.replayFolderBox.setMaxLength(512);
        this.replayFolderBox.setHint(Component.literal("Default: .flashback/replays").withStyle(ChatFormatting.DARK_GRAY));

        if (previous != null) {
            this.replayFolderBox.setValue(previous); // keep text on window resize
        } else {
            FlashbackSettings mod = FlashbackSettings.getInstance();
            String current = (mod != null && mod.getConfig().replayFolder != null) ? mod.getConfig().replayFolder : "";
            this.replayFolderBox.setValue(current);
        }
        this.replayFolderBox.setResponder(value -> this.updatePathStatus());
        this.addRenderableWidget(this.replayFolderBox);

        this.addRenderableWidget(Button.builder(Component.literal("Default"), btn -> {
                this.replayFolderBox.setValue("");
                this.updatePathStatus();
            })
            .bounds(left + CONTENT_WIDTH - 60, top + 44, 60, 20)
            .tooltip(Tooltip.create(Component.literal("Clear the path and use Flashback's default replay folder.")))
            .build());

        // ---- Subfolders -------------------------------------------------
        this.addRenderableWidget(Button.builder(toggleLabel("Subfolders", this.subfolders), btn -> {
                this.subfolders = !this.subfolders;
                btn.setMessage(toggleLabel("Subfolders", this.subfolders));
                this.updateSubfolderButtons();
            })
            .bounds(left, top + 100, CONTENT_WIDTH, 20)
            .tooltip(Tooltip.create(Component.literal("Sort new replays into nested folders inside the replay folder.")))
            .build());

        int third = (CONTENT_WIDTH - 8) / 3;

        // Ordered the same way the folders are nested: server / date / version
        this.serverButton = this.addRenderableWidget(Button.builder(toggleLabel("Server", this.byServer), btn -> {
                this.byServer = !this.byServer;
                btn.setMessage(toggleLabel("Server", this.byServer));
            })
            .bounds(left, top + 124, third, 20)
            .tooltip(Tooltip.create(Component.literal("One folder per server address (\"singleplayer\" for local worlds).")))
            .build());

        this.dateButton = this.addRenderableWidget(Button.builder(toggleLabel("Date", this.byDate), btn -> {
                this.byDate = !this.byDate;
                btn.setMessage(toggleLabel("Date", this.byDate));
            })
            .bounds(left + third + 4, top + 124, third, 20)
            .tooltip(Tooltip.create(Component.literal("One folder per day, e.g. " + LocalDate.now() + ".")))
            .build());

        this.versionButton = this.addRenderableWidget(Button.builder(toggleLabel("Version", this.byVersion), btn -> {
                this.byVersion = !this.byVersion;
                btn.setMessage(toggleLabel("Version", this.byVersion));
            })
            .bounds(left + (third + 4) * 2, top + 124, CONTENT_WIDTH - (third + 4) * 2, 20)
            .tooltip(Tooltip.create(Component.literal("One folder per Minecraft version, e.g. " + currentVersion() + ".")))
            .build());

        // ---- Footer -----------------------------------------------------
        int half = (CONTENT_WIDTH - 6) / 2;
        this.doneButton = this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.saveAndClose())
            .bounds(left, top + TOTAL_HEIGHT - 20, half, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
            .bounds(left + CONTENT_WIDTH - half, top + TOTAL_HEIGHT - 20, half, 20)
            .build());

        this.updateSubfolderButtons();
        this.updatePathStatus();
        this.setInitialFocus(this.replayFolderBox);
    }

    // ------------------------------------------------------------------------

    private void updateSubfolderButtons() {
        this.serverButton.active = this.subfolders;
        this.dateButton.active = this.subfolders;
        this.versionButton.active = this.subfolders;
    }

    private void updatePathStatus() {
        String raw = cleanPath(this.replayFolderBox.getValue());
        if (raw.isEmpty()) {
            this.pathStatus = Component.literal("Using Flashback's default folder");
            this.pathStatusColor = COLOR_MUTED;
            this.pathValid = true;
        } else {
            try {
                Path path = Path.of(raw).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) {
                    this.pathStatus = Component.literal("✔ Folder found");
                    this.pathStatusColor = COLOR_OK;
                    this.pathValid = true;
                } else if (Files.exists(path)) {
                    this.pathStatus = Component.literal("✖ That path is a file, not a folder");
                    this.pathStatusColor = COLOR_ERROR;
                    this.pathValid = false;
                } else {
                    this.pathStatus = Component.literal("⚠ Folder doesn't exist yet — it will be created");
                    this.pathStatusColor = COLOR_WARN;
                    this.pathValid = true;
                }
            } catch (InvalidPathException e) {
                this.pathStatus = Component.literal("✖ Invalid path");
                this.pathStatusColor = COLOR_ERROR;
                this.pathValid = false;
            }
        }
        if (this.doneButton != null) {
            this.doneButton.active = this.pathValid;
        }
    }

    /** Trims whitespace and surrounding quotes (Windows "Copy as path" adds them). */
    private static String cleanPath(String value) {
        String s = value == null ? "" : value.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private void saveAndClose() {
        if (!this.pathValid) {
            return;
        }
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod != null) {
            FlashbackSettings.ModConfig config = mod.getConfig();
            config.replayFolder = cleanPath(this.replayFolderBox.getValue());
            config.dynamicSubfolders = this.subfolders;
            config.subfolderByDate = this.byDate;
            config.subfolderByServer = this.byServer;
            config.subfolderByVersion = this.byVersion;
            mod.saveConfig();
            FlashbackSettings.LOGGER.info("[FlashbackSettings] Config updated via in-game screen.");
        }
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void onClose() {
        // Escape / Cancel: discard changes and go back
        this.minecraft.setScreenAndShow(this.parent);
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": ").append(enabled
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private static String currentVersion() {
        try {
            return sanitize(SharedConstants.getCurrentVersion().name(), "unknown-version");
        } catch (Throwable t) {
            return "unknown-version";
        }
    }

    private String currentServer() {
        ServerData server = this.minecraft != null ? this.minecraft.getCurrentServer() : null;
        return server == null ? "singleplayer" : sanitize(server.ip, "singleplayer");
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }

    /** Example of where the next replay will be saved, mirroring FlashbackReplayFolderMixin. */
    private String previewPath() {
        String raw = cleanPath(this.replayFolderBox.getValue());
        String base;
        if (raw.isEmpty()) {
            try {
                base = Flashback.getDataDirectory().resolve("replays").toString();
            } catch (Throwable t) {
                base = ".flashback/replays";
            }
        } else {
            base = raw;
        }

        String sep = base.contains("\\") ? "\\" : "/";
        StringBuilder sb = new StringBuilder(base);
        if (this.subfolders) {
            if (this.byServer) sb.append(sep).append(currentServer());
            if (this.byDate) sb.append(sep).append(LocalDate.now());
            if (this.byVersion) sb.append(sep).append(currentVersion());
        }
        return sb.toString();
    }

    /** Cuts text from the left so the end of a long path stays visible. */
    private String fitLeft(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int start = 0;
        while (start < text.length() && this.font.width(ellipsis + text.substring(start)) > maxWidth) {
            start++;
        }
        return ellipsis + text.substring(start);
    }

    // ------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        this.extractBackground(gfx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int right = left + CONTENT_WIDTH;

        // Title
        gfx.centeredText(this.font, this.title, cx, top, COLOR_TITLE);

        // Panel behind the options
        int panelTop = top + 16;
        int panelBottom = top + TOTAL_HEIGHT - 28;
        gfx.fill(left - PANEL_PADDING, panelTop, right + PANEL_PADDING, panelBottom, COLOR_PANEL);
        gfx.outline(left - PANEL_PADDING, panelTop, CONTENT_WIDTH + PANEL_PADDING * 2, panelBottom - panelTop, COLOR_PANEL_BORDER);

        // Replay folder section
        gfx.text(this.font, "Replay Folder", left, top + 32, COLOR_LABEL, true);
        gfx.text(this.font, this.pathStatus, left, top + 68, this.pathStatusColor, false);

        // Divider + subfolder section
        gfx.horizontalLine(left, right - 1, top + 82, COLOR_DIVIDER);
        gfx.text(this.font, "Subfolders", left, top + 88, COLOR_LABEL, true);

        // Live preview of the resulting folder
        String label = "Saves to: ";
        int labelWidth = this.font.width(label);
        gfx.text(this.font, label, left, top + 152, COLOR_MUTED, false);
        gfx.text(this.font, fitLeft(previewPath(), CONTENT_WIDTH - labelWidth), left + labelWidth, top + 152, COLOR_LABEL, false);

        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }
}
