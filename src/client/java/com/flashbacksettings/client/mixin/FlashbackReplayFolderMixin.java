package com.flashbacksettings.client.mixin;

import com.flashbacksettings.client.ReplayPaths;
import com.moulberry.flashback.Flashback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(value = Flashback.class, remap = false)
public class FlashbackReplayFolderMixin {

    @Inject(method = "getReplayFolder", at = @At("RETURN"), cancellable = true, remap = false)
    private static void flashbacksettings$redirectReplayFolder(CallbackInfoReturnable<Path> cir) {
        cir.setReturnValue(ReplayPaths.replayFolder());
    }
}
