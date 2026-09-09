package com.flashbacksettings.client.mixin;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.Flashback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.file.Path;

@Mixin(value = com.moulberry.flashback.screen.select_replay.SelectReplayScreen.class, remap = false)
public class FlashbackReplayMenuMixin {
    @ModifyArg(
        method = "<init>(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/screen/select_replay/SelectReplayScreen;<init>(Lnet/minecraft/client/gui/screens/Screen;Ljava/nio/file/Path;)V", remap = false),
        index = 1,
        remap = false
    )
    private static Path redirectReplayFolderForMenu(Path original) {
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
