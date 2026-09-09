package com.flashbacksettings.client.mixin;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.Flashback;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

@Mixin(value = Flashback.class, remap = false)
public class FlashbackReplayFolderMixin {

    @Inject(method = "getReplayFolder", at = @At("RETURN"), cancellable = true, remap = false)
    private static void redirectReplayFolder(CallbackInfoReturnable<Path> cir) {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod == null) {
            return;
        }

        FlashbackSettings.ModConfig config = mod.getConfig();
        String customFolder = config.replayFolder;
        Path folder = customFolder == null || customFolder.isBlank()
            ? Flashback.getDataDirectory().resolve("replays")
            : Path.of(customFolder).toAbsolutePath().normalize();

        if (config.dynamicSubfolders) {
            if (config.subfolderByServer) {
                folder = folder.resolve(serverFolderName());
            }
            if (config.subfolderByDate) {
                folder = folder.resolve(LocalDate.now().toString());
            }
            if (config.subfolderByVersion) {
                folder = folder.resolve(sanitize(SharedConstants.getCurrentVersion().name(), "unknown-version"));
            }
        }

        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            FlashbackSettings.LOGGER.error("[FlashbackSettings] Failed to create replay folder: {}", folder, e);
            return;
        }
        cir.setReturnValue(folder);
    }

    private static String serverFolderName() {
        Minecraft client = Minecraft.getInstance();
        ServerData server = client.getCurrentServer();
        String serverName = server == null ? "singleplayer" : server.ip;
        return sanitize(serverName, "singleplayer");
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }
}