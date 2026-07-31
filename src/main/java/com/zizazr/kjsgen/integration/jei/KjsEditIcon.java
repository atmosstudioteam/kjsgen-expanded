package com.zizazr.kjsgen.integration.jei;

// JEI 19 only (bundled with 1.21.x); compiled out on 1.20.1-forge's JEI 15. See KjsgenJeiPlugin.
//? if >=1.21 {
import com.zizazr.kjsgen.KjsGen;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The icon drawn on a kjsgen JEI recipe button ("edit" / "remove"). A white-glyph
 * PNG (transparent background, so JEI's grayscale button tint brightens it on hover
 * and dims it when disabled, exactly like the bookmark icon) scaled to a
 * {@value #SIZE}px footprint so it sits inside JEI's 13px button like the bookmark star.
 */
final class KjsEditIcon implements IDrawable {
    /** Drawn size in px (JEI recipe buttons are 13x13; the bookmark icon is 9x9). */
    private static final int SIZE = 11;

    private final ResourceLocation texture;
    /** Source texture pixel dimensions (square). */
    private final int tex;

    /**
     * @param textureFile file name under {@code textures/gui/}, e.g. {@code "jei_edit.png"}
     * @param texSize     the PNG's pixel size (square), e.g. 16
     */
    KjsEditIcon(String textureFile, int texSize) {
        this.texture = KjsGen.rl("textures/gui/" + textureFile);
        this.tex = texSize;
    }

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
        // 1.21.1 blit: (texture, x, y, destW, destH, u, v, regionW, regionH, texW, texH) — scales tex -> SIZE
        guiGraphics.blit(texture, xOffset, yOffset, SIZE, SIZE, 0f, 0f, tex, tex, tex, tex);
    }
}
//?}
