package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ReplayStorage {

    public static final long PROTECT_RECENT_MILLIS = 2 * 60 * 1000L;
    private static final long POLL_SECONDS = 5;
    private static final long STARTUP_CLEANUP_SECONDS = 30;
    private static final long SETTLE_MILLIS = 2000;
    private static final long FRESH_MILLIS = 10 * 60 * 1000L;
    private static final long GB = 1024L * 1024L * 1024L;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "FlashbackSettings-Storage");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    private static final Map<Path, Meta> META = new HashMap<>();
    private static final Map<Path, Long> PENDING = new HashMap<>();
    private static Map<Path, FileInfo> known;
    private static Path watchedBase;
    private static boolean started;

    private ReplayStorage() {
    }

    public record Replay(Path path, long size, long modified, int ticks) {
    }

    public record Plan(List<Replay> remove, long bytes) {
        public int count() {
            return this.remove.size();
        }
    }

    public record Result(int removed, long bytes, int failed) {
    }

    public record Limits(boolean limitSize, int maxGb, boolean limitAge, int maxDays) {
        public boolean any() {
            return this.limitSize || this.limitAge;
        }

        public static Limits fromConfig(FlashbackSettings.ModConfig config) {
            return new Limits(config.limitStorageSize, config.maxStorageGb, config.deleteOldReplays, config.maxReplayAgeDays);
        }
    }

    private record FileInfo(long size, long modified) {
    }

    private record Meta(long size, long modified, boolean replay, int ticks) {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        EXECUTOR.scheduleWithFixedDelay(ReplayStorage::poll, 1, POLL_SECONDS, TimeUnit.SECONDS);
        EXECUTOR.schedule(ReplayStorage::startupCleanup, STARTUP_CLEANUP_SECONDS, TimeUnit.SECONDS);
    }

    public static CompletableFuture<List<Replay>> scanAsync(Path base) {
        return CompletableFuture.supplyAsync(() -> scan(base), EXECUTOR);
    }

    public static CompletableFuture<Result> cleanupAsync(Path base, Limits limits) {
        return CompletableFuture.supplyAsync(() -> execute(base, plan(scan(base), limits, System.currentTimeMillis())), EXECUTOR);
    }

    public static Plan plan(List<Replay> replays, Limits limits, long now) {
        List<Replay> remove = new ArrayList<>();
        if (replays == null || limits == null || !limits.any()) {
            return new Plan(remove, 0);
        }
        List<Replay> sorted = new ArrayList<>(replays);
        sorted.sort(Comparator.comparingLong(Replay::modified));

        long total = 0;
        for (Replay replay : sorted) {
            total += replay.size();
        }
        long sizeLimit = Math.max(1, limits.maxGb()) * GB;
        long ageCutoff = now - Math.max(1, limits.maxDays()) * 24L * 60L * 60L * 1000L;
        long protectedAfter = now - PROTECT_RECENT_MILLIS;

        long bytes = 0;
        for (Replay replay : sorted) {
            if (replay.modified() > protectedAfter) {
                continue;
            }
            boolean tooOld = limits.limitAge() && replay.modified() < ageCutoff;
            boolean overLimit = limits.limitSize() && total > sizeLimit;
            if (tooOld || overLimit) {
                remove.add(replay);
                total -= replay.size();
                bytes += replay.size();
            }
        }
        return new Plan(remove, bytes);
    }

    private static List<Replay> scan(Path base) {
        List<Replay> replays = new ArrayList<>();
        if (base == null) {
            return replays;
        }
        Map<Path, FileInfo> files = listZips(base);
        for (Map.Entry<Path, FileInfo> entry : files.entrySet()) {
            Meta meta = meta(entry.getKey(), entry.getValue());
            if (meta.replay()) {
                replays.add(new Replay(entry.getKey(), meta.size(), meta.modified(), meta.ticks()));
            }
        }
        META.keySet().removeIf(path -> path.startsWith(base) && !files.containsKey(path));
        replays.sort(Comparator.comparingLong(Replay::modified));
        return replays;
    }

    private static Result execute(Path base, Plan plan) {
        int removed = 0;
        int failed = 0;
        long bytes = 0;
        for (Replay replay : plan.remove()) {
            if (delete(base, replay.path())) {
                removed++;
                bytes += replay.size();
            } else {
                failed++;
            }
        }
        if (removed > 0) {
            FlashbackSettings.LOGGER.info("[FlashbackSettings] Storage cleanup removed {} replay(s), {}.", removed, formatBytes(bytes));
        }
        return new Result(removed, bytes, failed);
    }

    private static boolean delete(Path base, Path file) {
        try {
            Files.deleteIfExists(file);
            META.remove(file);
            if (known != null) {
                known.remove(file);
            }
            Path parent = file.getParent();
            while (parent != null && base != null && parent.startsWith(base) && !parent.equals(base)) {
                try {
                    Files.delete(parent);
                } catch (DirectoryNotEmptyException e) {
                    break;
                }
                parent = parent.getParent();
            }
            return true;
        } catch (IOException e) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Could not delete replay {}", file, e);
            return false;
        }
    }

    private static void poll() {
        try {
            FlashbackSettings.ModConfig config = config();
            if (config == null) {
                return;
            }
            Path base = ReplayPaths.baseFolder();
            Map<Path, FileInfo> now = listZips(base);
            if (known == null || !base.equals(watchedBase)) {
                known = now;
                watchedBase = base;
                PENDING.clear();
                return;
            }

            for (Path path : now.keySet()) {
                if (!known.containsKey(path)) {
                    PENDING.putIfAbsent(path, -1L);
                }
            }
            known = now;

            boolean finishedAny = false;
            long time = System.currentTimeMillis();
            Iterator<Map.Entry<Path, Long>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Path, Long> entry = it.next();
                FileInfo info = now.get(entry.getKey());
                if (info == null) {
                    it.remove();
                    continue;
                }
                if (info.size() == entry.getValue() && time - info.modified() > SETTLE_MILLIS) {
                    it.remove();
                    finishedAny |= onNewReplay(base, entry.getKey(), info, config);
                } else {
                    entry.setValue(info.size());
                }
            }

            if (finishedAny) {
                autoCleanup(base, config);
            }
        } catch (Throwable t) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Replay folder check failed.", t);
        }
    }

    private static boolean onNewReplay(Path base, Path file, FileInfo info, FlashbackSettings.ModConfig config) {
        if (System.currentTimeMillis() - info.modified() > FRESH_MILLIS) {
            return false;
        }
        Meta meta = meta(file, info);
        if (!meta.replay()) {
            return false;
        }
        if (config.discardShortReplays && meta.ticks() >= 0 && meta.ticks() < config.minReplaySeconds * 20L) {
            if (delete(base, file)) {
                FlashbackSettings.LOGGER.info("[FlashbackSettings] Discarded short replay {} ({}).", file.getFileName(), formatTicks(meta.ticks()));
                notify(config, "Short replay discarded",
                    file.getFileName() + " was only " + formatTicks(meta.ticks()));
            }
        }
        return true;
    }

    private static void startupCleanup() {
        try {
            FlashbackSettings.ModConfig config = config();
            if (config != null) {
                autoCleanup(ReplayPaths.baseFolder(), config);
            }
        } catch (Throwable t) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Startup cleanup failed.", t);
        }
    }

    private static void autoCleanup(Path base, FlashbackSettings.ModConfig config) {
        Limits limits = Limits.fromConfig(config);
        if (!limits.any()) {
            return;
        }
        Result result = execute(base, plan(scan(base), limits, System.currentTimeMillis()));
        if (result.removed() > 0) {
            notify(config, "Replay storage cleaned up",
                "Removed " + result.removed() + (result.removed() == 1 ? " replay" : " replays") + " (" + formatBytes(result.bytes()) + ")");
        }
    }

    private static void notify(FlashbackSettings.ModConfig config, String title, String message) {
        if (!config.cleanupNotifications) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.gui != null) {
                SystemToast.addOrUpdate(client.gui.toastManager(), TOAST,
                    Component.literal(title), Component.literal(message));
            }
        });
    }

    private static FlashbackSettings.ModConfig config() {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        return mod == null ? null : mod.getConfig();
    }

    private static Map<Path, FileInfo> listZips(Path base) {
        Map<Path, FileInfo> files = new HashMap<>();
        if (base == null || !Files.isDirectory(base)) {
            return files;
        }
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (!dir.equals(base) && name != null && name.toString().startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        files.put(file, new FileInfo(attrs.size(), attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Could not scan replay folder {}", base, e);
        }
        return files;
    }

    private static Meta meta(Path file, FileInfo info) {
        Meta cached = META.get(file);
        if (cached != null && cached.size() == info.size() && cached.modified() == info.modified()) {
            return cached;
        }
        boolean replay = false;
        int ticks = -1;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry metadata = zip.getEntry("metadata.json");
            if (metadata != null) {
                boolean hasChunks = false;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().getName().endsWith(".flashback")) {
                        hasChunks = true;
                        break;
                    }
                }
                try (Reader reader = new InputStreamReader(zip.getInputStream(metadata), StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root != null && root.isJsonObject()) {
                        JsonObject object = root.getAsJsonObject();
                        ticks = readInt(object, "total_ticks", readInt(object, "totalTicks", -1));
                        replay = hasChunks || ticks >= 0;
                    }
                }
            }
        } catch (Exception e) {
            replay = false;
            ticks = -1;
        }
        Meta meta = new Meta(info.size(), info.modified(), replay, ticks);
        META.put(file, meta);
        return meta;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        try {
            JsonElement element = object.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            String target = folder.toAbsolutePath().toString();
            ProcessBuilder process = switch (Util.getPlatform()) {
                case WINDOWS -> new ProcessBuilder("explorer.exe", target);
                case OSX -> new ProcessBuilder("open", target);
                default -> new ProcessBuilder("xdg-open", target);
            };
            process.start();
        } catch (Exception e) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Could not open folder {}", folder, e);
        }
    }

    public static long totalBytes(List<Replay> replays) {
        long total = 0;
        if (replays != null) {
            for (Replay replay : replays) {
                total += replay.size();
            }
        }
        return total;
    }

    public static long gigabytes(int gb) {
        return gb * GB;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return (value >= 100 ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f", value)) + " " + units[unit];
    }

    public static String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return formatSeconds(seconds);
    }

    public static String formatSeconds(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
    }

    public static String formatAge(long modified, long now) {
        long minutes = Math.max(0, (now - modified) / 60000L);
        if (minutes < 60) {
            return minutes <= 1 ? "just now" : minutes + " minutes ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        }
        long days = hours / 24;
        if (days < 60) {
            return days == 1 ? "yesterday" : days + " days ago";
        }
        long months = days / 30;
        if (months < 24) {
            return months + " months ago";
        }
        return (days / 365) + " years ago";
    }
}
