package com.flashbacksettings.client.mixin;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(value = SelectReplayScreen.class, remap = false)
public abstract class SelectReplayScreenMixin extends Screen {
    @Unique private static final int ROW_WIDTH = 302;
    @Unique private static final int GAP = 2;

    protected SelectReplayScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void flashbacksettings$addOpenFolderButton(CallbackInfo ci) {
        int rowY = this.height - 28;
        List<AbstractWidget> row = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.getY() == rowY && widget.getHeight() == 20) {
                row.add(widget);
            }
        }
        row.sort(Comparator.comparingInt(AbstractWidget::getX));

        Button openFolder = Button.builder(Component.literal("Open Folder"), btn -> this.flashbacksettings$openFolder())
            .tooltip(Tooltip.create(Component.literal("Open this replay folder in your file explorer.")))
            .build();

        if (row.size() == 3) {
            int w = (ROW_WIDTH - GAP * 3) / 4;
            int x = this.width / 2 - ROW_WIDTH / 2;
            List<AbstractWidget> ordered = List.of(row.get(0), row.get(1), openFolder, row.get(2));
            for (AbstractWidget widget : ordered) {
                widget.setPosition(x, rowY);
                widget.setWidth(w);
                x += w + GAP;
            }
        } else {
            openFolder.setRectangle(20, 20, this.width / 2 - 151, 22);
            openFolder.setMessage(Component.literal("\u2197"));
        }
        this.addRenderableWidget(openFolder);
    }

    @Unique
    private void flashbacksettings$openFolder() {
        Path folder = ((SelectReplayScreen) (Object) this).path;
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
            FlashbackSettings.LOGGER.warn("[FlashbackSettings] Could not open replay folder {}", folder, e);
        }
    }
}
