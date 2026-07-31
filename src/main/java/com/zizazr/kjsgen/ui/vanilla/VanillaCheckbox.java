package com.zizazr.kjsgen.ui.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;

/**
 * A flat, theme-styled checkbox (same look as the Import JS selection list) used
 * in place of vanilla on/off {@code CycleButton}s. The label is optional: the
 * editor's parameter rows already draw their label in the left column, while
 * dialogs draw it next to the box.
 */
final class VanillaCheckbox extends AbstractButton {
    private static final int BOX = 10;

    private final boolean drawLabel;
    private final Consumer<Boolean> onChange;
    private boolean value;

    VanillaCheckbox(int x, int y, int width, int height, Component label, boolean drawLabel,
                    boolean initial, Consumer<Boolean> onChange) {
        super(x, y, width, height, label);
        this.drawLabel = drawLabel;
        this.value = initial;
        this.onChange = onChange;
    }

    @Override
    public void onPress() {
        value = !value;
        onChange.accept(value);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int boxY = getY() + (getHeight() - BOX) / 2;
        VanillaTheme.checkbox(g, getX(), boxY, BOX, value, isHoveredOrFocused());
        if (drawLabel) {
            Font font = Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(getMessage(), getWidth() - BOX - 5);
            if (!lines.isEmpty()) {
                g.drawString(font, lines.get(0), getX() + BOX + 5,
                        getY() + (getHeight() - 8) / 2, VanillaTheme.TEXT, true);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
