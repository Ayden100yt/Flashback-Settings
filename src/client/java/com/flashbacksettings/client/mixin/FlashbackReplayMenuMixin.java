package com.flashbacksettings.client.mixin;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

@Mixin(value = com.moulberry.flashback.screen.select_replay.SelectReplayScreen.class, remap = false)
public class FlashbackReplayMenuMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKESTATIC", target = "Lcom/moulberry/flashback/Flashback;getReplayFolder:()Ljava/nio/file/Path;", remap = false))
    private static Path redirectReplayFolderForMenu() {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod == null || mod.getConfig() == null) {
            return Flashback.getDataDirectory().resolve("replays");
        }

        String customFolder = mod.getConfig().replayFolder;
        if (customFolder == null || customFolder.isBlank()) {
            return Flashback.getDataDirectory().resolve("replays");
        }

        return Path.of(customFolder).toAbsolutePath().normalize();
    }
}
