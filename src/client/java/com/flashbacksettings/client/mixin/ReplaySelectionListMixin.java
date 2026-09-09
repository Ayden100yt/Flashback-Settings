package com.flashbacksettings.client.mixin;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.screen.select_replay.PendingSelectionEntry;
import com.moulberry.flashback.screen.select_replay.ReplaySelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(value = ReplaySelectionList.class, remap = false)
public class ReplaySelectionListMixin {
    @Inject(method = "loadReplays", at = @At("RETURN"), cancellable = true, remap = false)
    private void flashbacksettings$fixReplayFolderCounts(CallbackInfoReturnable<CompletableFuture<List<PendingSelectionEntry>>> cir) {
        CompletableFuture<List<PendingSelectionEntry>> originalFuture = cir.getReturnValue();
        if (originalFuture == null) {
            return;
        }

        cir.setReturnValue(originalFuture.thenApply(entries -> {
            List<PendingSelectionEntry> updated = new ArrayList<>(entries.size());
            for (PendingSelectionEntry entry : entries) {
                if (entry instanceof PendingSelectionEntry.Folder folder) {
                    updated.add(new PendingSelectionEntry.Folder(
                        folder.path(),
                        folder.modifiedTime(),
                        countReplaysRecursively(folder.path())
                    ));
                } else {
                    updated.add(entry);
                }
            }
            return updated;
        }));
    }

    private static int countReplaysRecursively(Path folder) {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            int total = 0;
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    total += countReplaysRecursively(child);
                } else if (child.toString().endsWith(".zip")) {
                    total++;
                }
            }
            return total;
        } catch (IOException e) {
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Failed to count replays under {}", folder, e);
            return 0;
        }
    }
}
