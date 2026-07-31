package com.zizazr.kjsgen.integration.jei;

// JEI 19 only (bundled with 1.21.x); compiled out on 1.20.1-forge's JEI 15. See KjsgenJeiPlugin.
//? if >=1.21 {
import com.zizazr.kjsgen.KjsGen;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The icon drawn on the "edit in kjsgen" recipe button. A 16x16 source PNG
 * (white glyph on transparent, so JEI's grayscale button tint brightens it on
 * hover and dims it when disabled, exactly like the bookmark icon) scaled to a
 * {@value #SIZE}px footprint so it sits inside JEI's 13px button like the
 * bookmark star.
 */
final class KjsEditIcon implements IDrawable {
    /** Drawn size in px (JEI recipe buttons are 13x13; the bookmark icon is 9x9). */
    private static final int SIZE = 11;
    /** Source texture pixel dimensions. */
    private static final int TEX = 16;
    private static final ResourceLocation TEXTURE = KjsGen.rl("textures/gui/jei_edit.png");

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
        // 1.21.1 blit: (texture, x, y, destW, destH, u, v, regionW, regionH, texW, texH) — scales 16 -> SIZE
        guiGraphics.blit(TEXTURE, xOffset, yOffset, SIZE, SIZE, 0f, 0f, TEX, TEX, TEX, TEX);
    }
}
//?}
