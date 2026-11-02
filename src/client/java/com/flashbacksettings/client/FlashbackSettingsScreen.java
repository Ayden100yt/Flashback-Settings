package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import com.flashbacksettings.client.FancyUi.FancyButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class FlashbackSettingsScreen extends Screen {

    // Layout
    private static final int CONTENT_WIDTH = 310;
    private static final int PANEL_PADDING = 10;
    private static final int TOTAL_HEIGHT = 280;

    // Colors (ARGB) - vanilla style
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
    private boolean buttonInBar;
    private boolean fancy;

    boolean autoPerServer;
    List<String> autoServers;
    boolean autoSingleplayer;

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
        this.buttonInBar = config.replayButtonInButtonBar;
        this.fancy = config.isFancyStyle();
        this.autoPerServer = config.autoRecordPerServer;
        this.autoServers = new ArrayList<>(config.autoRecordServers);
        this.autoSingleplayer = config.autoRecordSingleplayer;
    }

    boolean isFancy() {
        return this.fancy;
    }

    @Override
    protected void init() {
        this.left = this.width / 2 - CONTENT_WIDTH / 2;
        this.top = Math.max(8, (this.height - TOTAL_HEIGHT) / 2);
        this.left = Math.max(PANEL_PADDING, this.left);

        // ---- Replay folder ----------------------------------------------
        String previous = this.replayFolderBox != null ? this.replayFolderBox.getValue() : null;

        int boxWidth = CONTENT_WIDTH - 64;
        if (this.fancy) {
            // Borderless box; the rounded background is drawn in extractRenderState
            this.replayFolderBox = new EditBox(this.font, left + 6, top + 44 + 6, boxWidth - 12, 10,
                Component.literal("Replay Folder"));
            this.replayFolderBox.setBordered(false);
            this.replayFolderBox.setTextShadow(false);
            this.replayFolderBox.setHint(FancyUi.text("Default: .flashback/replays").withColor(0xFF6A6A6A));
        } else {
            this.replayFolderBox = new EditBox(this.font, left, top + 44, boxWidth, 20,
                Component.literal("Replay Folder"));
            this.replayFolderBox.setHint(Component.literal("Default: .flashback/replays").withStyle(ChatFormatting.DARK_GRAY));
        }
        this.replayFolderBox.setMaxLength(512);

        if (previous != null) {
            this.replayFolderBox.setValue(previous); // keep text on window resize / style switch
        } else {
            FlashbackSettings mod = FlashbackSettings.getInstance();
            String current = (mod != null && mod.getConfig().replayFolder != null) ? mod.getConfig().replayFolder : "";
            this.replayFolderBox.setValue(current);
        }
        this.replayFolderBox.setResponder(value -> this.updatePathStatus());
        this.addRenderableWidget(this.replayFolderBox);

        this.addRenderableWidget(this.button("Default", () -> {
                this.replayFolderBox.setValue("");
                this.updatePathStatus();
            }, left + CONTENT_WIDTH - 60, top + 44, 60,
            "Clear the path and use Flashback's default replay folder."));

        // ---- Subfolders -------------------------------------------------
        this.addRenderableWidget(this.toggle("Subfolders", () -> this.subfolders, () -> {
                this.subfolders = !this.subfolders;
                this.updateSubfolderButtons();
            }, left, top + 100, CONTENT_WIDTH,
            "Sort new replays into nested folders inside the replay folder."));

        int third = (CONTENT_WIDTH - 8) / 3;

        // Ordered the same way the folders are nested: server / date / version
        this.serverButton = this.addRenderableWidget(this.toggle("Server", () -> this.byServer,
            () -> this.byServer = !this.byServer,
            left, top + 124, third,
            "One folder per server address (\"singleplayer\" for local worlds)."));

        this.dateButton = this.addRenderableWidget(this.toggle("Date", () -> this.byDate,
            () -> this.byDate = !this.byDate,
            left + third + 4, top + 124, third,
            "One folder per day, e.g. " + LocalDate.now() + "."));

        this.versionButton = this.addRenderableWidget(this.toggle("Version", () -> this.byVersion,
            () -> this.byVersion = !this.byVersion,
            left + (third + 4) * 2, top + 124, CONTENT_WIDTH - (third + 4) * 2,
            "One folder per Minecraft version, e.g. " + currentVersion() + "."));

        int halfRow = (CONTENT_WIDTH - 4) / 2;
        this.addRenderableWidget(this.toggle("Icon Bar Button", () -> this.buttonInBar,
            () -> this.buttonInBar = !this.buttonInBar,
            left, top + 184, halfRow,
            "Move Flashback's replay button on the title screen into the row of small icon buttons, if your mods/modpack add one."));

        this.addRenderableWidget(this.button(this.autoRecordLabel(),
            () -> this.minecraft.setScreenAndShow(new AutoRecordScreen(this)),
            left + CONTENT_WIDTH - halfRow, top + 184, halfRow,
            "Choose which servers start recording automatically when you join, and whether singleplayer does."));

        // ---- Menu style -------------------------------------------------
        String styleTip = "Vanilla looks like the normal Minecraft menus. Fancy uses rounded, see-through buttons and a smooth font.";
        if (this.fancy) {
            int half = (CONTENT_WIDTH - 4) / 2;
            this.addRenderableWidget(this.fancyButton("Vanilla", () -> this.setStyle(false),
                left, top + 228, half, null, () -> !this.fancy, styleTip));
            this.addRenderableWidget(this.fancyButton("Fancy", () -> this.setStyle(true),
                left + CONTENT_WIDTH - half, top + 228, half, null, () -> this.fancy, styleTip));
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Style: Vanilla"), btn -> this.setStyle(true))
                .bounds(left, top + 228, CONTENT_WIDTH, 20)
                .tooltip(Tooltip.create(Component.literal(styleTip)))
                .build());
        }

        // ---- Footer -----------------------------------------------------
        int half = (CONTENT_WIDTH - 6) / 2;
        this.doneButton = this.addRenderableWidget(this.button("Done", this::saveAndClose,
            left, top + TOTAL_HEIGHT - 20, half, null));

        this.addRenderableWidget(this.button("Cancel", this::onClose,
            left + CONTENT_WIDTH - half, top + TOTAL_HEIGHT - 20, half, null));

        this.updateSubfolderButtons();
        this.updatePathStatus();
        this.setInitialFocus(this.replayFolderBox);
    }

    // ---- Widget factories (pick vanilla or fancy look) -------------------

    private Button button(String label, Runnable action, int x, int y, int w, String tooltip) {
        if (this.fancy) {
            return this.fancyButton(label, action, x, y, w, null, null, tooltip);
        }
        Button.Builder builder = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y, w, 20);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return builder.build();
    }

    private Button toggle(String label, BooleanSupplier state, Runnable flip, int x, int y, int w, String tooltip) {
        if (this.fancy) {
            return this.fancyButton(label, flip, x, y, w, state, null, tooltip);
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

    private Button fancyButton(String label, Runnable action, int x, int y, int w,
                               BooleanSupplier toggle, BooleanSupplier selected, String tooltip) {
        FancyButton button = new FancyButton(x, y, w, 20, Component.literal(label), btn -> action.run(), toggle, selected);
        if (tooltip != null) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return button;
    }

    private void setStyle(boolean fancy) {
        if (this.fancy == fancy) {
            return;
        }
        this.fancy = fancy;
        this.rebuildWidgets(); // re-runs init() with the new look, keeps the typed path
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

    private static String cleanPath(String value) {
        return ReplayPaths.cleanPath(value);
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
            config.replayButtonInButtonBar = this.buttonInBar;
            config.autoRecordPerServer = this.autoPerServer;
            config.autoRecordServers = new ArrayList<>(this.autoServers);
            config.autoRecordSingleplayer = this.autoSingleplayer;
            config.menuStyle = this.fancy ? FlashbackSettings.ModConfig.STYLE_FANCY : FlashbackSettings.ModConfig.STYLE_VANILLA;
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

    private String autoRecordLabel() {
        if (!this.autoPerServer) {
            return "Auto-Record...";
        }
        int n = this.autoServers.size();
        return "Auto-Record: " + n + (n == 1 ? " Server" : " Servers");
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": ").append(enabled
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private static String currentVersion() {
        return ReplayPaths.currentVersion();
    }

    /** Example of where the next replay will be saved, mirroring ReplayPaths#replayFolder. */
    private String previewPath() {
        String raw = cleanPath(this.replayFolderBox.getValue());
        String base;
        if (raw.isEmpty()) {
            try {
                base = ReplayPaths.defaultFolder().toString();
            } catch (Throwable t) {
                base = ".flashback/replays";
            }
        } else {
            base = raw;
        }

        String sep = base.contains("\\") ? "\\" : "/";
        StringBuilder sb = new StringBuilder(base);
        if (this.subfolders) {
            if (this.byServer) sb.append(sep).append(ReplayPaths.currentServer());
            if (this.byDate) sb.append(sep).append(ReplayPaths.currentDate());
            if (this.byVersion) sb.append(sep).append(ReplayPaths.currentVersion());
        }
        return sb.toString();
    }

    /** Text in the current style's font. */
    private Component styled(String text) {
        return this.fancy ? FancyUi.text(text) : Component.literal(text);
    }

    private Component styled(Component text) {
        return this.fancy ? FancyUi.text(text) : text;
    }

    private int textWidth(String text) {
        return this.font.width(styled(text));
    }

    /** Cuts text from the left so the end of a long path stays visible. */
    private String fitLeft(String text, int maxWidth) {
        if (textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (maxWidth <= textWidth(ellipsis)) {
            return ellipsis;
        }
        int start = 0;
        while (start < text.length() && textWidth(ellipsis + text.substring(start)) > maxWidth) {
            start++;
        }
        return ellipsis + text.substring(start);
    }

    // ------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (this.fancy) {
            this.extractFancy(gfx);
        } else {
            this.extractVanilla(gfx);
        }
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    private void extractVanilla(GuiGraphicsExtractor gfx) {
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

        gfx.horizontalLine(left, right - 1, top + 166, COLOR_DIVIDER);
        gfx.text(this.font, "More", left, top + 172, COLOR_LABEL, true);

        gfx.horizontalLine(left, right - 1, top + 210, COLOR_DIVIDER);
        gfx.text(this.font, "Menu Style", left, top + 216, COLOR_LABEL, true);
    }

    private void extractFancy(GuiGraphicsExtractor gfx) {
        int cx = this.width / 2;
        int right = left + CONTENT_WIDTH;

        // Title
        gfx.centeredText(this.font, styled(this.title), cx, top + 1, FancyUi.TEXT);

        // Rounded glass panel behind the options
        int panelTop = top + 16;
        int panelBottom = top + TOTAL_HEIGHT - 28;
        int panelX = left - PANEL_PADDING;
        int panelW = CONTENT_WIDTH + PANEL_PADDING * 2;
        FancyUi.rounded(gfx, panelX, panelTop, panelW, panelBottom - panelTop, FancyUi.PANEL);
        FancyUi.roundedOutline(gfx, panelX, panelTop, panelW, panelBottom - panelTop, FancyUi.PANEL_BORDER);

        // Replay folder section
        gfx.text(this.font, styled("Replay Folder"), left, top + 31, FancyUi.TEXT_MUTED, false);

        int boxW = CONTENT_WIDTH - 64;
        FancyUi.rounded(gfx, left, top + 44, boxW, 20, FancyUi.FIELD);
        FancyUi.roundedOutline(gfx, left, top + 44, boxW, 20,
            this.replayFolderBox.isFocused() ? 0x80FFFFFF : 0x24FFFFFF);

        gfx.text(this.font, styled(this.pathStatus), left + 2, top + 68, this.pathStatusColor, false);

        // Divider + subfolder section
        gfx.fill(left, top + 82, right, top + 83, FancyUi.DIVIDER);
        gfx.text(this.font, styled("Subfolders"), left, top + 88, FancyUi.TEXT_MUTED, false);

        // Live preview of the resulting folder
        String label = "Saves to: ";
        int labelWidth = textWidth(label);
        gfx.text(this.font, styled(label), left + 2, top + 152, FancyUi.TEXT_MUTED, false);
        gfx.text(this.font, styled(fitLeft(previewPath(), CONTENT_WIDTH - labelWidth - 2)), left + 2 + labelWidth, top + 152, FancyUi.TEXT, false);

        gfx.fill(left, top + 166, right, top + 167, FancyUi.DIVIDER);
        gfx.text(this.font, styled("More"), left, top + 172, FancyUi.TEXT_MUTED, false);

        gfx.fill(left, top + 210, right, top + 211, FancyUi.DIVIDER);
        gfx.text(this.font, styled("Menu Style"), left, top + 216, FancyUi.TEXT_MUTED, false);
    }
}
