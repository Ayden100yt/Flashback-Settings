package com.flashbacksettings.client.mixin;

import com.flashbacksettings.FlashbackSettings;
import com.moulberry.flashback.screen.FlashbackButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = TitleScreen.class, priority = 1500)
public abstract class TitleScreenButtonBarMixin extends Screen {

    @Unique private static final int MIN_ICON_SIZE = 12;
    @Unique private static final int MAX_ICON_SIZE = 32;
    @Unique private static final int MAX_BAR_GAP = 8;

    protected TitleScreenButtonBarMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void flashbacksettings$afterInit(CallbackInfo ci) {
        this.flashbacksettings$moveReplayButtonIntoBar();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void flashbacksettings$afterTick(CallbackInfo ci) {
        this.flashbacksettings$moveReplayButtonIntoBar();
    }

    @Unique
    private void flashbacksettings$moveReplayButtonIntoBar() {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod == null || mod.getConfig() == null || !mod.getConfig().replayButtonInButtonBar) {
            return;
        }

        AbstractWidget replayButton = null;
        List<AbstractWidget> icons = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            if (widget instanceof FlashbackButton) {
                replayButton = widget;
            } else if (widget.getWidth() == widget.getHeight()
                && widget.getWidth() >= MIN_ICON_SIZE && widget.getWidth() <= MAX_ICON_SIZE) {
                icons.add(widget);
            }
        }
        if (replayButton == null) {
            return;
        }

        List<AbstractWidget> bar = flashbacksettings$findBar(icons);
        if (bar == null) {
            return;
        }

        int size = bar.get(0).getWidth();
        int rowY = bar.get(0).getY();
        int gap = bar.get(1).getX() - bar.get(0).getRight();
        AbstractWidget last = bar.get(bar.size() - 1);

        boolean alreadyInBar = replayButton.getY() == rowY
            && replayButton.getWidth() == size && replayButton.getHeight() == size
            && replayButton.getX() == last.getRight() + gap;

        int minX = bar.get(0).getX();
        int maxX = alreadyInBar ? replayButton.getRight() : last.getRight();
        int center = (minX + maxX) / 2;

        int count = bar.size() + 1;
        int totalWidth = count * size + (count - 1) * gap;
        int x = center - totalWidth / 2;

        for (AbstractWidget icon : bar) {
            icon.setX(x);
            x += size + gap;
        }
        replayButton.setSize(size, size);
        replayButton.setPosition(x, rowY);
    }

    @Unique
    private static List<AbstractWidget> flashbacksettings$findBar(List<AbstractWidget> icons) {
        Map<Long, List<AbstractWidget>> rows = new LinkedHashMap<>();
        for (AbstractWidget icon : icons) {
            long key = ((long) icon.getY() << 32) | (icon.getWidth() & 0xFFFFFFFFL);
            rows.computeIfAbsent(key, k -> new ArrayList<>()).add(icon);
        }

        List<AbstractWidget> best = null;
        for (List<AbstractWidget> row : rows.values()) {
            row.sort(Comparator.comparingInt(AbstractWidget::getX));
            List<AbstractWidget> run = new ArrayList<>();
            for (AbstractWidget icon : row) {
                if (!run.isEmpty()) {
                    int gap = icon.getX() - run.get(run.size() - 1).getRight();
                    if (gap < 0 || gap > MAX_BAR_GAP) {
                        best = flashbacksettings$better(best, run);
                        run = new ArrayList<>();
                    }
                }
                run.add(icon);
            }
            best = flashbacksettings$better(best, run);
        }
        return best;
    }

    @Unique
    private static List<AbstractWidget> flashbacksettings$better(List<AbstractWidget> best, List<AbstractWidget> run) {
        if (run.size() < 2) {
            return best;
        }
        return best == null || run.size() > best.size() ? run : best;
    }
}
