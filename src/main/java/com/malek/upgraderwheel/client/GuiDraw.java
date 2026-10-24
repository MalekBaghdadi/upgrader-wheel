package com.malek.upgraderwheel.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Vanilla-looking panels and slots drawn from plain rectangles, plus the shapes for the wheel.
 * Angles are in degrees, 0 at the top, growing clockwise.
 */
final class GuiDraw {

    static final int PANEL = 0xFFC6C6C6;
    static final int PANEL_LIGHT = 0xFFFFFFFF;
    static final int PANEL_DARK = 0xFF555555;
    static final int OUTLINE = 0xFF000000;
    static final int SLOT_FILL = 0xFF8B8B8B;
    static final int SLOT_DARK = 0xFF373737;
    static final int TEXT = 0x404040;

    private GuiDraw() {
    }

    static void panel(int x, int y, int w, int h) {
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        Gui.drawRect(x + 1, y + 1, x + w - 3, y + 3, PANEL_LIGHT);
        Gui.drawRect(x + 1, y + 1, x + 3, y + h - 3, PANEL_LIGHT);
        Gui.drawRect(x + 3, y + h - 3, x + w - 1, y + h - 1, PANEL_DARK);
        Gui.drawRect(x + w - 3, y + 3, x + w - 1, y + h - 1, PANEL_DARK);
        Gui.drawRect(x + 2, y, x + w - 2, y + 1, OUTLINE);
        Gui.drawRect(x + 2, y + h - 1, x + w - 2, y + h, OUTLINE);
        Gui.drawRect(x, y + 2, x + 1, y + h - 2, OUTLINE);
        Gui.drawRect(x + w - 1, y + 2, x + w, y + h - 2, OUTLINE);
        Gui.drawRect(x + 1, y + 1, x + 2, y + 2, OUTLINE);
        Gui.drawRect(x + w - 2, y + 1, x + w - 1, y + 2, OUTLINE);
        Gui.drawRect(x + 1, y + h - 2, x + 2, y + h - 1, OUTLINE);
        Gui.drawRect(x + w - 2, y + h - 2, x + w - 1, y + h - 1, OUTLINE);
    }

    /** A slot frame around a 16x16 item drawn at (x, y). */
    static void slot(int x, int y) {
        sunken(x - 1, y - 1, 18, 18, SLOT_FILL);
    }

    /** A recessed box: dark top and left edges, light bottom and right edges. */
    static void sunken(int x, int y, int w, int h, int fill) {
        Gui.drawRect(x, y, x + w, y + h, fill);
        Gui.drawRect(x, y, x + w - 1, y + 1, SLOT_DARK);
        Gui.drawRect(x, y, x + 1, y + h - 1, SLOT_DARK);
        Gui.drawRect(x + 1, y + h - 1, x + w, y + h, PANEL_LIGHT);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h, PANEL_LIGHT);
    }

    static void beginShapes() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    static void endShapes() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Pie slice from angle a0 to a1, colored from the center color to the rim color. */
    static void sector(double cx, double cy, double radius, double a0, double a1, int centerColor, int rimColor) {
        if (a1 <= a0) {
            return;
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(buffer, cx, cy, centerColor);
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 4.0));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / steps);
            vertex(buffer, cx + Math.sin(a) * radius, cy - Math.cos(a) * radius, rimColor);
        }
        tessellator.draw();
    }

    static void circle(double cx, double cy, double radius, int color) {
        sector(cx, cy, radius, 0, 360, color, color);
    }

    /** Band between two radii, from angle a0 to a1. */
    static void ring(double cx, double cy, double inner, double outer, double a0, double a1, int innerColor, int outerColor) {
        if (a1 <= a0) {
            return;
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 4.0));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / steps);
            double s = Math.sin(a);
            double c = Math.cos(a);
            vertex(buffer, cx + s * outer, cy - c * outer, outerColor);
            vertex(buffer, cx + s * inner, cy - c * inner, innerColor);
        }
        tessellator.draw();
    }

    /** A thin radial line from radius r0 to r1 at angle a. */
    static void spoke(double cx, double cy, double r0, double r1, double angle, double halfWidth, int color) {
        double a = Math.toRadians(angle);
        double dx = Math.sin(a);
        double dy = -Math.cos(a);
        double px = Math.cos(a) * halfWidth;
        double py = Math.sin(a) * halfWidth;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(buffer, cx + dx * r0 - px, cy + dy * r0 - py, color);
        vertex(buffer, cx + dx * r1 - px, cy + dy * r1 - py, color);
        vertex(buffer, cx + dx * r1 + px, cy + dy * r1 + py, color);
        vertex(buffer, cx + dx * r0 + px, cy + dy * r0 + py, color);
        tessellator.draw();
    }

    /** Needle: a triangle with its base at radius r0 and its tip at r1. */
    static void needle(double cx, double cy, double r0, double r1, double angle, double halfBase, int color) {
        double a = Math.toRadians(angle);
        double dx = Math.sin(a);
        double dy = -Math.cos(a);
        double px = Math.cos(a) * halfBase;
        double py = Math.sin(a) * halfBase;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        vertex(buffer, cx + dx * r0 - px, cy + dy * r0 - py, color);
        vertex(buffer, cx + dx * r1, cy + dy * r1, color);
        vertex(buffer, cx + dx * r0 + px, cy + dy * r0 + py, color);
        tessellator.draw();
    }

    private static void vertex(BufferBuilder buffer, double x, double y, int argb) {
        buffer.pos(x, y, 0.0D).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    static int withAlpha(int rgb, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static String value(double v) {
        if (v >= 1.0E9) {
            return String.format(Locale.ROOT, "%.1fB", v / 1.0E9);
        }
        if (v >= 1.0E6) {
            return String.format(Locale.ROOT, "%.1fM", v / 1.0E6);
        }
        if (v >= 1.0E4) {
            return String.format(Locale.ROOT, "%.1fk", v / 1.0E3);
        }
        if (v >= 100) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        if (v >= 1) {
            return String.format(Locale.ROOT, "%.1f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String chance(double c) {
        if (c >= 10) {
            return String.format(Locale.ROOT, "%.1f%%", c);
        }
        if (c >= 1) {
            return String.format(Locale.ROOT, "%.2f%%", c);
        }
        if (c >= 0.01) {
            return String.format(Locale.ROOT, "%.3f%%", c);
        }
        return "<0.01%";
    }

    /** Text color for a chance shown on a dark background. */
    static int chanceColor(double chance, boolean locked) {
        if (locked) {
            return 0x777777;
        }
        if (chance >= 50) {
            return 0x55FF55;
        }
        if (chance >= 20) {
            return 0xFFFF55;
        }
        if (chance >= 5) {
            return 0xFFAA00;
        }
        return 0xFF5555;
    }
}
