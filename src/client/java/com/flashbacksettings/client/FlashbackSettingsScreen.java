package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import com.flashbacksettings.client.FancyUi.FancyButton;
import com.flashbacksettings.client.FancyUi.OptionSlider;
import com.flashbacksettings.client.FancyUi.PrimaryButton;
import com.flashbacksettings.client.FancyUi.SidebarTab;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class FlashbackSettingsScreen extends Screen {

    private static final Identifier ICON = Identifier.fromNamespaceAndPath(FlashbackSettings.MOD_ID, "icon.png");

    private static final int MAX_WIDTH = 430;
    private static final int MAX_HEIGHT = 262;
    private static final int HEADER = 32;
    private static final int ROW = 20;
    private static final int TAB_GAP = 3;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_OK = 0xFF7BE07B;
    private static final int COLOR_WARN = 0xFFF2C14E;
    private static final int COLOR_ERROR = 0xFFFF6B6B;
    private static final int COLOR_DIVIDER = 0xFF444444;
    private static final int COLOR_BAR_BG = 0xFF2A2A2A;

    private static final int[] SECONDS = {3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300};
    private static final int[] GIGABYTES = {1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 300, 500, 1000};
    private static final int[] DAYS = {1, 2, 3, 5, 7, 14, 21, 30, 45, 60, 90, 120, 180, 365};

    private enum Tab {
        FOLDER("Folder", "Where your replays are saved"),
        RECORDING("Recording", "When to record and what to keep"),
        STORAGE("Storage", "Keep your replay folder from filling up"),
        INTERFACE("Interface", "How this mod looks and talks to you");

        private final String label;
        private final String description;

        Tab(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    private static Tab lastTab = Tab.FOLDER;

    private final Screen parent;
    private Tab tab = lastTab;

    private String folderText;
    private boolean subfolders;
    private boolean byDate;
    private boolean byServer;
    private boolean byVersion;
    private boolean buttonInBar;
    private boolean fancy;

    boolean autoPerServer;
    List<String> autoServers;
    boolean autoSingleplayer;

    private boolean discardShort;
    private int minSeconds;
    private boolean limitSize;
    private int maxGb;
    private boolean limitAge;
    private int maxDays;
    private boolean notifications;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int sideW;
    private int cx;
    private int cy;
    private int cw;
    private int footerY;
    private int footerSepY;

    private TabManager tabManager;
    private final List<net.minecraft.client.gui.components.tabs.Tab> vanillaTabs = new ArrayList<>();

    private EditBox folderBox;
    private Button serverButton;
    private Button dateButton;
    private Button versionButton;
    private Button doneButton;
    private Button cleanButton;
    private OptionSlider minSecondsSlider;
    private OptionSlider maxGbSlider;
    private OptionSlider maxDaysSlider;

    private Component pathStatus = Component.empty();
    private int pathStatusColor = COLOR_MUTED;
    private boolean pathValid = true;

    private List<ReplayStorage.Replay> replays;
    private Path scannedBase;
    private int scanGeneration;
    private long lastFolderEdit;
    private boolean cleaning;
    private long confirmUntil;
    private String cleanupMessage;
    private int cleanupMessageColor = COLOR_OK;

    public FlashbackSettingsScreen(Screen parent) {
        super(Component.literal("Flashback Settings"));
        this.parent = parent;

        FlashbackSettings mod = FlashbackSettings.getInstance();
        FlashbackSettings.ModConfig config = mod != null ? mod.getConfig() : new FlashbackSettings.ModConfig();
        this.folderText = config.replayFolder == null ? "" : config.replayFolder;
        this.subfolders = config.dynamicSubfolders;
        this.byDate = config.subfolderByDate;
        this.byServer = config.subfolderByServer;
        this.byVersion = config.subfolderByVersion;
        this.buttonInBar = config.replayButtonInButtonBar;
        this.fancy = config.isFancyStyle();
        this.autoPerServer = config.autoRecordPerServer;
        this.autoServers = new ArrayList<>(config.autoRecordServers);
        this.autoSingleplayer = config.autoRecordSingleplayer;
        this.discardShort = config.discardShortReplays;
        this.minSeconds = config.minReplaySeconds;
        this.limitSize = config.limitStorageSize;
        this.maxGb = config.maxStorageGb;
        this.limitAge = config.deleteOldReplays;
        this.maxDays = config.maxReplayAgeDays;
        this.notifications = config.cleanupNotifications;
    }

    boolean isFancy() {
        return this.fancy;
    }

    @Override
    protected void init() {
        this.folderBox = null;
        this.serverButton = null;
        this.dateButton = null;
        this.versionButton = null;
        this.cleanButton = null;
        this.minSecondsSlider = null;
        this.maxGbSlider = null;
        this.maxDaysSlider = null;
        this.tabManager = null;
        this.vanillaTabs.clear();

        if (this.fancy) {
            this.layoutFancy();
        } else {
            this.layoutVanilla();
        }

        switch (this.tab) {
            case FOLDER -> this.initFolder();
            case RECORDING -> this.initRecording();
            case STORAGE -> this.initStorage();
            case INTERFACE -> this.initInterface();
        }

        if (this.fancy) {
            int bw = 66;
            Button cancel = this.button("Cancel", this::onClose, this.px + this.pw - 8 - bw, this.footerY, bw,
                "Close without saving your changes.");
            PrimaryButton done = new PrimaryButton(cancel.getX() - 4 - bw, this.footerY, bw, ROW,
                Component.literal("Save"), btn -> this.saveAndClose());
            done.setTooltip(Tooltip.create(Component.literal("Save all changes and close.")));
            this.doneButton = done;
            this.addRenderableWidget(this.doneButton);
            this.addRenderableWidget(cancel);
        } else {
            int bw = Math.min(150, (this.width - 28) / 2);
            this.doneButton = this.addRenderableWidget(this.button("Done", this::saveAndClose,
                this.width / 2 - 4 - bw, this.footerY, bw, "Save all changes and close."));
            this.addRenderableWidget(this.button("Cancel", this::onClose,
                this.width / 2 + 4, this.footerY, bw, "Close without saving your changes."));
        }

        this.updatePathStatus();
        this.updateActive();
        this.requestScan(false);

        if (this.folderBox != null) {
            this.setInitialFocus(this.folderBox);
        }
    }

    private void layoutFancy() {
        this.pw = Math.min(MAX_WIDTH, this.width - 12);
        this.ph = Math.min(MAX_HEIGHT, this.height - 12);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.sideW = this.pw < 380 ? 76 : 96;
        this.cx = this.px + this.sideW + 18;
        this.cw = this.px + this.pw - 10 - this.cx;
        this.cy = this.py + HEADER + 8;
        this.footerY = this.py + this.ph - ROW - 6;
        this.footerSepY = this.footerY - 5;

        int ty = this.cy;
        for (Tab t : Tab.values()) {
            SidebarTab button = new SidebarTab(this.px + 8, ty, this.sideW, ROW, Component.literal(t.label),
                btn -> this.selectTab(t), () -> this.tab == t);
            button.setTooltip(Tooltip.create(Component.literal(t.description)));
            this.addRenderableWidget(button);
            ty += ROW + TAB_GAP;
        }
    }

    private void layoutVanilla() {
        this.tabManager = new TabManager(widget -> {
        }, widget -> {
        });
        for (Tab t : Tab.values()) {
            this.vanillaTabs.add(new GridLayoutTab(Component.literal(t.label)));
        }
        MenuTabBar bar = MenuTabBar.builder(this.tabManager, this.width)
            .addTabs(this.vanillaTabs.toArray(new net.minecraft.client.gui.components.tabs.Tab[0]))
            .build();
        this.addRenderableWidget(bar);
        for (Tab t : Tab.values()) {
            bar.setTabTooltip(t.ordinal(), Tooltip.create(Component.literal(t.description)));
        }
        bar.selectTab(this.tab.ordinal(), false);
        bar.arrangeElements(this.width);

        int barBottom = bar.getRectangle().bottom();
        this.footerY = this.height - 28;
        this.footerSepY = this.height - 36;
        this.cw = Math.min(310, this.width - 40);
        this.cx = (this.width - this.cw) / 2;
        int free = this.footerSepY - 16 - barBottom - 140;
        this.cy = barBottom + Math.max(8, Math.min(22, free / 2));

        this.px = 0;
        this.py = 0;
        this.pw = this.width;
        this.ph = this.height;
    }

    private void selectTab(Tab t) {
        if (this.tab != t) {
            this.tab = t;
            lastTab = t;
            this.confirmUntil = 0;
            this.rebuildWidgets();
        }
    }

    private void syncVanillaTab() {
        if (this.tabManager == null) {
            return;
        }
        int index = this.vanillaTabs.indexOf(this.tabManager.getCurrentTab());
        if (index >= 0 && index < Tab.values().length) {
            this.selectTab(Tab.values()[index]);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        this.syncVanillaTab();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean handled = super.keyPressed(event);
        this.syncVanillaTab();
        return handled;
    }

    private void initFolder() {
        int y = this.cy;
        int small = 46;
        int boxW = this.cw - small * 2 - 8;
        int boxY = y + 11;

        if (this.fancy) {
            this.folderBox = new EditBox(this.font, this.cx + 6, boxY + 6, boxW - 12, 10, Component.literal("Replay Folder"));
            this.folderBox.setBordered(false);
            this.folderBox.setTextShadow(false);
            this.folderBox.setHint(FancyUi.text("Default: .flashback/replays").withColor(0xFF6A6A6A));
        } else {
            this.folderBox = new EditBox(this.font, this.cx, boxY, boxW, ROW, Component.literal("Replay Folder"));
            this.folderBox.setHint(Component.literal("Default: .flashback/replays").withStyle(ChatFormatting.DARK_GRAY));
        }
        this.folderBox.setMaxLength(512);
        this.folderBox.setValue(this.folderText);
        this.folderBox.setResponder(value -> {
            this.folderText = value;
            this.lastFolderEdit = System.currentTimeMillis();
            this.updatePathStatus();
        });
        this.addRenderableWidget(this.folderBox);

        this.addRenderableWidget(this.button("Reset", () -> this.folderBox.setValue(""),
            this.cx + boxW + 4, boxY, small, "Use Flashback's default replay folder."));
        this.addRenderableWidget(this.button("Open", this::openFolder,
            this.cx + boxW + 8 + small, boxY, small, "Open the replay folder in your file explorer."));

        int sy = y + 64;
        this.addRenderableWidget(this.toggle("Sort into subfolders", () -> this.subfolders, () -> {
                this.subfolders = !this.subfolders;
                this.updateActive();
            }, this.cx, sy, this.cw,
            "Put new replays into nested folders inside the replay folder."));

        int third = (this.cw - 8) / 3;
        this.serverButton = this.addRenderableWidget(this.toggle("Server", () -> this.byServer,
            () -> this.byServer = !this.byServer, this.cx, sy + 24, third,
            "One folder per server address (\"singleplayer\" for local worlds)."));
        this.dateButton = this.addRenderableWidget(this.toggle("Date", () -> this.byDate,
            () -> this.byDate = !this.byDate, this.cx + third + 4, sy + 24, third,
            "One folder per day, e.g. " + LocalDate.now() + "."));
        this.versionButton = this.addRenderableWidget(this.toggle("Version", () -> this.byVersion,
            () -> this.byVersion = !this.byVersion, this.cx + (third + 4) * 2, sy + 24, this.cw - (third + 4) * 2,
            "One folder per Minecraft version, e.g. " + ReplayPaths.currentVersion() + "."));
    }

    private void initRecording() {
        int y = this.cy;
        this.addRenderableWidget(this.button(this.autoRecordLabel(),
            () -> this.minecraft.setScreenAndShow(new AutoRecordScreen(this)),
            this.cx, y + 11, this.cw,
            "Choose which servers start recording automatically when you join, and whether singleplayer does."));

        int sy = y + 64;
        this.addRenderableWidget(this.toggle("Discard short replays", () -> this.discardShort, () -> {
                this.discardShort = !this.discardShort;
                this.updateActive();
            }, this.cx, sy, this.cw,
            "Automatically delete replays that are shorter than the minimum length, right after they're saved."));

        this.minSecondsSlider = this.addRenderableWidget(this.slider(this.cx, sy + 24, this.cw, SECONDS, this.minSeconds,
            value -> "Minimum length: " + ReplayStorage.formatSeconds(value),
            value -> this.minSeconds = value,
            "Replays shorter than this are thrown away."));
    }

    private void initStorage() {
        int y = this.cy + 44;
        int half = (this.cw - 4) / 2;

        this.addRenderableWidget(this.toggle("Size limit", () -> this.limitSize, () -> {
                this.limitSize = !this.limitSize;
                this.onLimitsChanged();
            }, this.cx, y, half,
            "Delete the oldest replays when the folder gets bigger than this."));
        this.maxGbSlider = this.addRenderableWidget(this.slider(this.cx + this.cw - half, y, half, GIGABYTES, this.maxGb,
            value -> "Max " + value + " GB",
            value -> {
                this.maxGb = value;
                this.onLimitsChanged();
            },
            "The most space all replays together may use."));

        this.addRenderableWidget(this.toggle("Age limit", () -> this.limitAge, () -> {
                this.limitAge = !this.limitAge;
                this.onLimitsChanged();
            }, this.cx, y + 24, half,
            "Delete replays that are older than this."));
        this.maxDaysSlider = this.addRenderableWidget(this.slider(this.cx + this.cw - half, y + 24, half, DAYS, this.maxDays,
            value -> "Older than " + value + (value == 1 ? " day" : " days"),
            value -> {
                this.maxDays = value;
                this.onLimitsChanged();
            },
            "Replays older than this are deleted."));

        this.cleanButton = this.addRenderableWidget(this.button("Clean up now", this::cleanNow,
            this.cx, y + 62, 112,
            "Apply the limits above right now. Replays from the last 2 minutes are never touched."));
    }

    private void initInterface() {
        int y = this.cy;
        int half = (this.cw - 4) / 2;
        String styleTip = "Vanilla looks like the normal Minecraft menus. Fancy uses rounded, see-through buttons and a smooth font.";
        if (this.fancy) {
            this.addRenderableWidget(this.fancyButton("Vanilla", () -> this.setStyle(false),
                this.cx, y + 11, half, null, () -> !this.fancy, styleTip));
            this.addRenderableWidget(this.fancyButton("Fancy", () -> this.setStyle(true),
                this.cx + this.cw - half, y + 11, half, null, () -> this.fancy, styleTip));
        } else {
            Button vanilla = this.button("Vanilla", () -> this.setStyle(false), this.cx, y + 11, half, styleTip);
            vanilla.active = false;
            this.addRenderableWidget(vanilla);
            this.addRenderableWidget(this.button("Fancy", () -> this.setStyle(true), this.cx + this.cw - half, y + 11, half, styleTip));
        }

        this.addRenderableWidget(this.toggle("Replay button in icon bar", () -> this.buttonInBar,
            () -> this.buttonInBar = !this.buttonInBar, this.cx, y + 58, this.cw,
            "Move Flashback's replay button on the title screen into the row of small icon buttons, if your mods/modpack add one."));
        this.addRenderableWidget(this.toggle("Cleanup notifications", () -> this.notifications,
            () -> this.notifications = !this.notifications, this.cx, y + 82, this.cw,
            "Show a pop-up when replays are discarded or cleaned up."));
    }

    private Button button(String label, Runnable action, int x, int y, int w, String tooltip) {
        if (this.fancy) {
            return this.fancyButton(label, action, x, y, w, null, null, tooltip);
        }
        Button.Builder builder = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y, w, ROW);
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
            .bounds(x, y, w, ROW);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return builder.build();
    }

    private Button fancyButton(String label, Runnable action, int x, int y, int w,
                               BooleanSupplier toggle, BooleanSupplier selected, String tooltip) {
        FancyButton button = new FancyButton(x, y, w, ROW, Component.literal(label), btn -> action.run(), toggle, selected);
        if (tooltip != null) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return button;
    }

    private OptionSlider slider(int x, int y, int w, int[] values, int current,
                                IntFunction<String> label, IntConsumer onChange, String tooltip) {
        OptionSlider slider = new OptionSlider(x, y, w, ROW, values, current, label, onChange, this.fancy);
        if (tooltip != null) {
            slider.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return slider;
    }

    private void setStyle(boolean fancy) {
        if (this.fancy == fancy) {
            return;
        }
        this.fancy = fancy;
        this.rebuildWidgets();
    }

    private void updateActive() {
        if (this.serverButton != null) {
            this.serverButton.active = this.subfolders;
            this.dateButton.active = this.subfolders;
            this.versionButton.active = this.subfolders;
        }
        if (this.minSecondsSlider != null) {
            this.minSecondsSlider.active = this.discardShort;
        }
        if (this.maxGbSlider != null) {
            this.maxGbSlider.active = this.limitSize;
        }
        if (this.maxDaysSlider != null) {
            this.maxDaysSlider.active = this.limitAge;
        }
        this.updateCleanButton();
    }

    private void onLimitsChanged() {
        this.confirmUntil = 0;
        this.cleanupMessage = null;
        this.updateActive();
    }

    private ReplayStorage.Limits limits() {
        return new ReplayStorage.Limits(this.limitSize, this.maxGb, this.limitAge, this.maxDays);
    }

    private ReplayStorage.Plan currentPlan() {
        return ReplayStorage.plan(this.replays, this.limits(), System.currentTimeMillis());
    }

    private void updateCleanButton() {
        if (this.cleanButton == null) {
            return;
        }
        if (this.cleaning) {
            this.cleanButton.active = false;
            this.cleanButton.setMessage(Component.literal("Cleaning..."));
            return;
        }
        if (this.replays == null || !this.limits().any()) {
            this.cleanButton.active = false;
            this.cleanButton.setMessage(Component.literal("Clean up now"));
            return;
        }
        int count = this.currentPlan().count();
        this.cleanButton.active = count > 0;
        if (count > 0 && System.currentTimeMillis() < this.confirmUntil) {
            this.cleanButton.setMessage(Component.literal("Confirm delete (" + count + ")")
                .withStyle(this.fancy ? ChatFormatting.WHITE : ChatFormatting.RED));
        } else {
            this.cleanButton.setMessage(Component.literal(count > 0 ? "Clean up now" : "Nothing to clean"));
        }
    }

    private void cleanNow() {
        Path base = this.resolveBase();
        if (base == null || this.cleaning) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= this.confirmUntil) {
            this.confirmUntil = now + 4000;
            this.updateCleanButton();
            return;
        }
        this.confirmUntil = 0;
        this.cleaning = true;
        this.cleanupMessage = null;
        this.updateCleanButton();
        ReplayStorage.cleanupAsync(base, this.limits()).whenComplete((result, error) -> this.minecraft.execute(() -> {
            this.cleaning = false;
            if (error != null || result == null) {
                this.cleanupMessage = "Cleanup failed, see the log for details";
                this.cleanupMessageColor = COLOR_ERROR;
            } else if (result.failed() > 0) {
                this.cleanupMessage = "Removed " + result.removed() + ", " + result.failed() + " couldn't be deleted";
                this.cleanupMessageColor = COLOR_WARN;
            } else {
                this.cleanupMessage = "Removed " + result.removed() + plural(result.removed(), " replay", " replays")
                    + " and freed " + ReplayStorage.formatBytes(result.bytes());
                this.cleanupMessageColor = COLOR_OK;
            }
            this.requestScan(true);
            this.updateCleanButton();
        }));
    }

    private void openFolder() {
        Path base = this.resolveBase();
        if (base != null) {
            ReplayStorage.openFolder(base);
        }
    }

    private Path resolveBase() {
        String raw = ReplayPaths.cleanPath(this.folderText);
        if (raw.isEmpty()) {
            try {
                return ReplayPaths.defaultFolder();
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            return Files.exists(path) && !Files.isDirectory(path) ? null : path;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private void requestScan(boolean force) {
        Path base = this.resolveBase();
        if (base == null) {
            this.replays = null;
            this.scannedBase = null;
            return;
        }
        if (!force && base.equals(this.scannedBase)) {
            return;
        }
        this.scannedBase = base;
        if (!force) {
            this.replays = null;
        }
        int generation = ++this.scanGeneration;
        ReplayStorage.scanAsync(base).whenComplete((list, error) -> this.minecraft.execute(() -> {
            if (generation == this.scanGeneration) {
                this.replays = error == null && list != null ? list : List.of();
                this.updateCleanButton();
            }
        }));
    }

    @Override
    public void tick() {
        super.tick();
        this.syncVanillaTab();
        if (this.lastFolderEdit != 0 && System.currentTimeMillis() - this.lastFolderEdit > 500) {
            this.lastFolderEdit = 0;
            this.requestScan(false);
        }
        if (this.confirmUntil != 0 && System.currentTimeMillis() >= this.confirmUntil) {
            this.confirmUntil = 0;
        }
        this.updateCleanButton();
        if (this.doneButton != null) {
            this.doneButton.active = this.pathValid;
        }
    }

    private void updatePathStatus() {
        String raw = ReplayPaths.cleanPath(this.folderText);
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
                    this.pathStatus = Component.literal("⚠ Doesn't exist yet, it will be created");
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

    private boolean isDirty() {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod == null) {
            return false;
        }
        FlashbackSettings.ModConfig c = mod.getConfig();
        return !ReplayPaths.cleanPath(this.folderText).equals(ReplayPaths.cleanPath(c.replayFolder))
            || this.subfolders != c.dynamicSubfolders
            || this.byDate != c.subfolderByDate
            || this.byServer != c.subfolderByServer
            || this.byVersion != c.subfolderByVersion
            || this.buttonInBar != c.replayButtonInButtonBar
            || this.fancy != c.isFancyStyle()
            || this.autoPerServer != c.autoRecordPerServer
            || !this.autoServers.equals(c.autoRecordServers)
            || this.autoSingleplayer != c.autoRecordSingleplayer
            || this.discardShort != c.discardShortReplays
            || this.minSeconds != c.minReplaySeconds
            || this.limitSize != c.limitStorageSize
            || this.maxGb != c.maxStorageGb
            || this.limitAge != c.deleteOldReplays
            || this.maxDays != c.maxReplayAgeDays
            || this.notifications != c.cleanupNotifications;
    }

    private void saveAndClose() {
        if (!this.pathValid) {
            return;
        }
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod != null) {
            FlashbackSettings.ModConfig config = mod.getConfig();
            config.replayFolder = ReplayPaths.cleanPath(this.folderText);
            config.dynamicSubfolders = this.subfolders;
            config.subfolderByDate = this.byDate;
            config.subfolderByServer = this.byServer;
            config.subfolderByVersion = this.byVersion;
            config.replayButtonInButtonBar = this.buttonInBar;
            config.autoRecordPerServer = this.autoPerServer;
            config.autoRecordServers = new ArrayList<>(this.autoServers);
            config.autoRecordSingleplayer = this.autoSingleplayer;
            config.discardShortReplays = this.discardShort;
            config.minReplaySeconds = this.minSeconds;
            config.limitStorageSize = this.limitSize;
            config.maxStorageGb = this.maxGb;
            config.deleteOldReplays = this.limitAge;
            config.maxReplayAgeDays = this.maxDays;
            config.cleanupNotifications = this.notifications;
            config.menuStyle = this.fancy ? FlashbackSettings.ModConfig.STYLE_FANCY : FlashbackSettings.ModConfig.STYLE_VANILLA;
            config.clamp();
            mod.saveConfig();
            FlashbackSettings.LOGGER.info("[FlashbackSettings] Config updated via in-game screen.");
        }
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    private String autoRecordLabel() {
        if (!this.autoPerServer) {
            return "Set up Auto-Record...";
        }
        int n = this.autoServers.size();
        return "Auto-Record: " + n + plural(n, " server", " servers") + (this.autoSingleplayer ? " + singleplayer" : "");
    }

    private String autoRecordStatus() {
        if (!this.autoPerServer) {
            return "Off: Flashback's own auto-start option is used";
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            return "On: recording starts by itself on the servers you picked";
        }
        String host = AutoRecord.currentHost();
        boolean records = host == null ? this.autoSingleplayer : AutoRecord.matchesAny(host, this.autoServers);
        return (host == null ? "Singleplayer" : host) + (records ? ": will auto-record" : ": won't auto-record");
    }

    private static String plural(int n, String one, String many) {
        return n == 1 ? one : many;
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": ").append(enabled
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private String previewPath() {
        Path base = this.resolveBase();
        String root = base != null ? base.toString() : ReplayPaths.cleanPath(this.folderText);
        String sep = root.contains("\\") ? "\\" : "/";
        StringBuilder sb = new StringBuilder(root);
        if (this.subfolders) {
            if (this.byServer) sb.append(sep).append(ReplayPaths.currentServer());
            if (this.byDate) sb.append(sep).append(ReplayPaths.currentDate());
            if (this.byVersion) sb.append(sep).append(ReplayPaths.currentVersion());
        }
        return sb.toString();
    }

    private Component styled(String text) {
        return this.fancy ? FancyUi.text(text) : Component.literal(text);
    }

    private Component styled(Component text) {
        return this.fancy ? FancyUi.text(text) : text;
    }

    private int textWidth(String text) {
        return this.font.width(this.styled(text));
    }

    private String fitLeft(String text, int maxWidth) {
        if (this.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (maxWidth <= this.textWidth(ellipsis)) {
            return ellipsis;
        }
        int start = 0;
        while (start < text.length() && this.textWidth(ellipsis + text.substring(start)) > maxWidth) {
            start++;
        }
        return ellipsis + text.substring(start);
    }

    private String fitRight(String text, int maxWidth) {
        if (this.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && this.textWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private int label() {
        return this.fancy ? FancyUi.TEXT_MUTED : COLOR_LABEL;
    }

    private int muted() {
        return this.fancy ? FancyUi.TEXT_MUTED : COLOR_MUTED;
    }

    private int bright() {
        return this.fancy ? FancyUi.TEXT : COLOR_TITLE;
    }

    private void text(GuiGraphicsExtractor gfx, String text, int x, int y, int color) {
        gfx.text(this.font, this.styled(text), x, y, color, false);
    }

    private void heading(GuiGraphicsExtractor gfx, String text, int x, int y) {
        gfx.text(this.font, this.styled(text), x, y, this.label(), !this.fancy);
    }

    private void divider(GuiGraphicsExtractor gfx, int y) {
        gfx.fill(this.cx, y, this.cx + this.cw, y + 1, this.fancy ? FancyUi.DIVIDER : COLOR_DIVIDER);
    }

    private void card(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
        if (this.fancy) {
            FancyUi.rounded(gfx, x, y, w, h, FancyUi.CARD);
            FancyUi.roundedOutline(gfx, x, y, w, h, FancyUi.PANEL_BORDER);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (!this.fancy || this.isInGameUi()) {
            super.extractBackground(gfx, mouseX, mouseY, delta);
            return;
        }
        if (this.minecraft.level == null) {
            this.extractPanorama(gfx, delta);
        }
        this.extractBlurredBackground(gfx);
        gfx.fillGradient(0, 0, this.width, this.height, FancyUi.BACKDROP_TOP, FancyUi.BACKDROP_BOTTOM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (this.fancy) {
            this.extractFancyFrame(gfx);
        } else {
            this.extractVanillaFrame(gfx);
        }
        switch (this.tab) {
            case FOLDER -> this.extractFolder(gfx);
            case RECORDING -> this.extractRecording(gfx);
            case STORAGE -> this.extractStorage(gfx);
            case INTERFACE -> this.extractInterface(gfx);
        }
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    private String statsLine() {
        if (this.replays == null) {
            return this.scannedBase == null ? "Folder not available" : "Counting replays...";
        }
        int n = this.replays.size();
        return n + plural(n, " replay", " replays") + " · " + ReplayStorage.formatBytes(ReplayStorage.totalBytes(this.replays));
    }

    private void extractVanillaFrame(GuiGraphicsExtractor gfx) {
        Identifier separator = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
        gfx.blit(RenderPipelines.GUI_TEXTURED, separator, 0, this.footerSepY, 0f, 0f, this.width, 2, 32, 2);

        Component line = Component.literal(this.statsLine()).withColor(COLOR_MUTED);
        if (this.isDirty()) {
            line = Component.literal(this.statsLine()).withColor(COLOR_MUTED)
                .append(Component.literal("   ● Unsaved changes").withColor(COLOR_WARN));
        }
        gfx.centeredText(this.font, line, this.width / 2, this.footerSepY - 12, 0xFFFFFFFF);
    }

    private void extractFancyFrame(GuiGraphicsExtractor gfx) {
        int right = this.px + this.pw;
        int line = FancyUi.DIVIDER;

        FancyUi.rounded(gfx, this.px, this.py, this.pw, this.ph, FancyUi.PANEL);
        FancyUi.roundedOutline(gfx, this.px, this.py, this.pw, this.ph, FancyUi.PANEL_BORDER);

        gfx.blit(RenderPipelines.GUI_TEXTURED, ICON, this.px + 8, this.py + 6, 0f, 0f, 20, 20, 128, 128, 128, 128);
        gfx.text(this.font, this.styled(this.title), this.px + 34, this.py + 7, FancyUi.TEXT, false);
        this.text(gfx, this.fitRight(this.tab.description, this.pw - 120), this.px + 34, this.py + 18, FancyUi.TEXT_MUTED);

        if (this.isDirty()) {
            String unsaved = "Unsaved";
            int w = this.textWidth(unsaved) + 14;
            int bx = right - 10 - w;
            FancyUi.rounded(gfx, bx, this.py + 9, w, 13, 0x40F2C14E);
            gfx.fill(bx + 5, this.py + 14, bx + 8, this.py + 17, COLOR_WARN);
            this.text(gfx, unsaved, bx + 11, this.py + 12, COLOR_WARN);
        }

        gfx.fill(this.px + 6, this.py + HEADER, right - 6, this.py + HEADER + 1, line);
        gfx.fill(this.px + 6, this.py + HEADER, this.px + 34, this.py + HEADER + 1, FancyUi.ACCENT);
        int dividerX = this.px + this.sideW + 12;
        gfx.fill(dividerX, this.py + HEADER + 6, dividerX + 1, this.footerSepY - 1, line);
        gfx.fill(this.px + 6, this.footerSepY, right - 6, this.footerSepY + 1, line);

        int statsMax = this.doneButton != null ? this.doneButton.getX() - this.px - 18 : this.pw / 2;
        this.text(gfx, this.fitRight(this.statsLine(), statsMax), this.px + 10, this.footerY + 6, FancyUi.TEXT_MUTED);
    }

    private void extractFolder(GuiGraphicsExtractor gfx) {
        int y = this.cy;
        this.heading(gfx, "Replay folder", this.cx, y);

        if (this.fancy && this.folderBox != null) {
            int boxW = this.cw - 46 * 2 - 8;
            FancyUi.rounded(gfx, this.cx, y + 11, boxW, ROW, FancyUi.FIELD);
            FancyUi.roundedOutline(gfx, this.cx, y + 11, boxW, ROW, this.folderBox.isFocused() ? 0x80FFFFFF : 0x24FFFFFF);
        }
        gfx.text(this.font, this.styled(this.pathStatus), this.cx + 2, y + 35, this.pathStatusColor, false);

        this.divider(gfx, y + 47);
        this.heading(gfx, "Subfolders", this.cx, y + 53);

        int py0 = y + 114;
        int in = this.fancy ? 6 : 0;
        this.card(gfx, this.cx, py0, this.cw, 26);
        this.text(gfx, "Next replay saves to", this.cx + in, py0 + 4, this.muted());
        this.text(gfx, this.fitLeft(this.previewPath(), this.cw - in * 2), this.cx + in, py0 + 15, this.bright());
    }

    private void extractRecording(GuiGraphicsExtractor gfx) {
        int y = this.cy;
        this.heading(gfx, "Auto-record", this.cx, y);
        String status = this.autoRecordStatus();
        int color = !this.autoPerServer ? this.muted() : status.endsWith("won't auto-record") ? COLOR_WARN : COLOR_OK;
        this.text(gfx, this.fitRight(status, this.cw - 4), this.cx + 2, y + 35, color);

        this.divider(gfx, y + 47);
        this.heading(gfx, "Short recordings", this.cx, y + 53);

        String hint = this.discardShort
            ? "Replays under " + ReplayStorage.formatSeconds(this.minSeconds) + " are deleted as soon as they're saved, so quick rejoins and accidental recordings don't pile up."
            : "Turn this on to throw away tiny accidental recordings automatically.";
        gfx.textWithWordWrap(this.font, this.styled(hint), this.cx + 2, y + 114, this.cw - 4, this.muted());
    }

    private void extractStorage(GuiGraphicsExtractor gfx) {
        int y = this.cy;
        long now = System.currentTimeMillis();
        int in = this.fancy ? 8 : 0;
        this.card(gfx, this.cx, y, this.cw, 38);

        if (this.replays == null) {
            this.text(gfx, this.scannedBase == null ? "Replay folder not available" : "Scanning replays...", this.cx + in, y + 8, this.muted());
        } else {
            int n = this.replays.size();
            long total = ReplayStorage.totalBytes(this.replays);
            gfx.text(this.font, this.styled(n + plural(n, " replay", " replays")), this.cx + in, y + 6, this.bright(), !this.fancy);

            String size = ReplayStorage.formatBytes(total) + (this.limitSize ? " / " + this.maxGb + " GB" : " used");
            this.text(gfx, size, this.cx + this.cw - in - this.textWidth(size), y + 6, this.bright());

            String oldest = n == 0 ? "No replays yet" : "Oldest: " + ReplayStorage.formatAge(this.replays.get(0).modified(), now);
            this.text(gfx, oldest, this.cx + in, y + 17, this.muted());

            int barX = this.cx + in;
            int barW = this.cw - in * 2;
            int barY = y + 29;
            gfx.fill(barX, barY, barX + barW, barY + 4, COLOR_BAR_BG);
            if (this.limitSize) {
                double ratio = Math.min(1.0, total / (double) ReplayStorage.gigabytes(this.maxGb));
                int fillColor = ratio < 0.7 ? COLOR_OK : ratio < 0.9 ? COLOR_WARN : COLOR_ERROR;
                gfx.fill(barX, barY, barX + (int) Math.round(barW * ratio), barY + 4, fillColor);
            } else if (n > 0) {
                gfx.fill(barX, barY, barX + barW, barY + 4, 0xFF555555);
            }
        }

        int py0 = y + 94;
        String preview;
        int color;
        if (!this.limits().any()) {
            preview = "Turn on a limit to keep the folder tidy automatically.";
            color = this.muted();
        } else if (this.replays == null) {
            preview = "Waiting for scan...";
            color = this.muted();
        } else {
            ReplayStorage.Plan plan = this.currentPlan();
            if (plan.count() == 0) {
                preview = "✔ Everything is within your limits";
                color = COLOR_OK;
            } else {
                preview = "Would delete " + plan.count() + plural(plan.count(), " replay", " replays") + " (" + ReplayStorage.formatBytes(plan.bytes()) + ")";
                color = COLOR_WARN;
            }
        }
        this.text(gfx, this.fitRight(preview, this.cw - 4), this.cx + 2, py0, color);

        int by = y + 106;
        String side = this.cleanupMessage != null ? this.cleanupMessage
            : this.limits().any() ? "Also runs after every recording" : "";
        int sideColor = this.cleanupMessage != null ? this.cleanupMessageColor : this.muted();
        if (!side.isEmpty()) {
            this.text(gfx, this.fitRight(side, this.cw - 120), this.cx + 118, by + 6, sideColor);
        }
    }

    private void extractInterface(GuiGraphicsExtractor gfx) {
        int y = this.cy;
        this.heading(gfx, "Menu style", this.cx, y);
        this.divider(gfx, y + 39);
        this.heading(gfx, "Behaviour", this.cx, y + 46);
        gfx.textWithWordWrap(this.font,
            this.styled("Tip: bind a key to open this screen in Options → Controls → Key Binds → Flashback Settings."),
            this.cx + 2, y + 112, this.cw - 4, this.muted());
    }
}
