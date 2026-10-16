package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.Flashback;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;

public final class ReplayPaths {

    private ReplayPaths() {
    }

    /** Trims whitespace and surrounding quotes (Windows "Copy as path" adds them). */
    public static String cleanPath(String value) {
        String s = value == null ? "" : value.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    public static Path defaultFolder() {
        return Flashback.getDataDirectory().resolve("replays");
    }

    public static Path baseFolder() {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        String raw = mod == null || mod.getConfig() == null ? "" : cleanPath(mod.getConfig().replayFolder);
        if (raw.isEmpty()) {
            return defaultFolder();
        }
        try {
            return Path.of(raw).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Invalid replay folder '{}', using Flashback's default.", raw);
            return defaultFolder();
        }
    }

    public static Path replayFolder() {
        Path folder = baseFolder();
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod == null || mod.getConfig() == null) {
            return folder;
        }
        FlashbackSettings.ModConfig config = mod.getConfig();
        if (config.dynamicSubfolders) {
            if (config.subfolderByServer) folder = folder.resolve(currentServer());
            if (config.subfolderByDate) folder = folder.resolve(currentDate());
            if (config.subfolderByVersion) folder = folder.resolve(currentVersion());
        }
        return folder;
    }

    public static String currentDate() {
        return LocalDate.now().toString();
    }

    public static String currentVersion() {
        try {
            return sanitize(SharedConstants.getCurrentVersion().name(), "unknown-version");
        } catch (Throwable t) {
            return "unknown-version";
        }
    }

    public static String currentServer() {
        Minecraft client = Minecraft.getInstance();
        ServerData server = client == null ? null : client.getCurrentServer();
        return server == null ? "singleplayer" : sanitize(server.ip, "singleplayer");
    }

    public static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (sanitized.isBlank() || sanitized.chars().allMatch(c -> c == '.')) {
            return fallback;
        }
        return sanitized;
    }
}
