package com.flashbacksettings.client.mixin;

import com.flashbacksettings.client.ReplayPaths;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.file.Path;

@Mixin(value = SelectReplayScreen.class, remap = false)
public class FlashbackReplayMenuMixin {
    @ModifyArg(
        method = "<init>(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/screen/select_replay/SelectReplayScreen;<init>(Lnet/minecraft/client/gui/screens/Screen;Ljava/nio/file/Path;)V", remap = false),
        index = 1,
        remap = false
    )
    private static Path flashbacksettings$redirectReplayFolderForMenu(Path original) {
        return ReplayPaths.baseFolder();
    }
}
