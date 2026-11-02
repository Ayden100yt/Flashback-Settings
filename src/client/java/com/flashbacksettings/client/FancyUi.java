package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/** Drawing helpers and widgets for the "Fancy" menu style (rounded, translucent, smooth font). */
public final class FancyUi {

    private FancyUi() {
    }

    public static final FontDescription FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(FlashbackSettings.MOD_ID, "fancy"));

    private static final Identifier ROUNDED = Identifier.fromNamespaceAndPath(FlashbackSettings.MOD_ID, "fancy/rounded");
    private static final Identifier ROUNDED_OUTLINE = Identifier.fromNamespaceAndPath(FlashbackSettings.MOD_ID, "fancy/rounded_outline");

    // Colors (ARGB)
    public static final int TEXT = 0xFFF2F2F2;
    public static final int TEXT_MUTED = 0xFF9C9C9C;
    public static final int TEXT_DISABLED = 0xFF6E6E6E;
    public static final int PANEL = 0xB8121214;
    public static final int PANEL_BORDER = 0x1FFFFFFF;
    public static final int DIVIDER = 0x1CFFFFFF;
    public static final int FIELD = 0xC0161618;

    private static final int BUTTON = 0xA02A2A2D;
    private static final int BUTTON_HOVER = 0xC0424246;
    private static final int BUTTON_DISABLED = 0x60202022;
    private static final int BUTTON_SELECTED = 0xD0505056;
    private static final int OUTLINE = 0x24FFFFFF;
    private static final int OUTLINE_HOVER = 0x60FFFFFF;
    private static final int SWITCH_ON = 0xFF4CC26A;
    private static final int SWITCH_OFF = 0xFF55555A;
    private static final int KNOB = 0xFFF5F5F5;

    /** Text in the smooth fancy font (falls back to the vanilla font for missing symbols). */
    public static MutableComponent text(String s) {
        return Component.literal(s).withStyle(style -> style.withFont(FONT));
    }

    public static MutableComponent text(Component c) {
        return c.copy().withStyle(style -> style.withFont(FONT));
    }

    public static void rounded(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, ROUNDED, x, y, w, h, color);
    }

    public static void roundedOutline(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, ROUNDED_OUTLINE, x, y, w, h, color);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * A rounded, translucent button. Can optionally show an on/off switch
     * (toggle) or a "selected" state (segmented control).
     */
    public static class FancyButton extends Button {

        private final BooleanSupplier toggle;   // null = plain button
        private final BooleanSupplier selected; // null = never selected

        public FancyButton(int x, int y, int w, int h, Component label, OnPress onPress,
                           BooleanSupplier toggle, BooleanSupplier selected) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.toggle = toggle;
            this.selected = selected;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
            Font font = net.minecraft.client.Minecraft.getInstance().font;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            boolean hot = this.active && this.isHoveredOrFocused();
            boolean isSelected = this.selected != null && this.selected.getAsBoolean();

            int bg = !this.active ? BUTTON_DISABLED : isSelected ? BUTTON_SELECTED : hot ? BUTTON_HOVER : BUTTON;
            rounded(gfx, x, y, w, h, withAlpha(bg, this.alpha));
            roundedOutline(gfx, x, y, w, h, withAlpha(hot || isSelected ? OUTLINE_HOVER : OUTLINE, this.alpha));

            int textColor = withAlpha(this.active ? TEXT : TEXT_DISABLED, this.alpha);
            Component label = text(this.getMessage());
            int textY = y + (h - 8) / 2;

            if (this.toggle == null) {
                gfx.centeredText(font, label, x + w / 2, textY, textColor);
                return;
            }

            // Label on the left, switch on the right
            gfx.text(font, label, x + 8, textY, textColor, false);

            boolean on = this.toggle.getAsBoolean();
            int sw = 18;
            int sh = 10;
            int sx = x + w - sw - 6;
            int sy = y + (h - sh) / 2;
            int track = this.active ? (on ? SWITCH_ON : SWITCH_OFF) : 0xFF3A3A3D;
            rounded(gfx, sx, sy, sw, sh, withAlpha(track, this.alpha));
            int knob = 8;
            int kx = on ? sx + sw - knob - 1 : sx + 1;
            rounded(gfx, kx, sy + 1, knob, knob, withAlpha(this.active ? KNOB : 0xFF8A8A8A, this.alpha));
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            MutableComponent base = super.createNarrationMessage();
            if (this.toggle != null) {
                return base.append(", ").append(this.toggle.getAsBoolean() ? "ON" : "OFF");
            }
            return base;
        }
    }
}
