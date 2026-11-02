package com.flashbacksettings.client.mixin;

import com.flashbacksettings.client.AutoRecord;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Flashback.class, remap = false)
public class FlashbackAutoStartMixin {
    @Redirect(
        method = "*",
        at = @At(
            value = "FIELD",
            target = "Lcom/moulberry/flashback/configuration/FlashbackConfigV1$SubcategoryRecordingControls;automaticallyStart:Z",
            opcode = Opcodes.GETFIELD,
            remap = false
        ),
        remap = false
    )
    private static boolean flashbacksettings$perServerAutoStart(FlashbackConfigV1.SubcategoryRecordingControls controls) {
        return AutoRecord.shouldAutoStart(controls.automaticallyStart);
    }
}
