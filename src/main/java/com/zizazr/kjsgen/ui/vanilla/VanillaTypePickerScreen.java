package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla dialog: a scrollable grid of recipe-type cards with a name/mod filter.
 */
public final class VanillaTypePickerScreen extends VanillaDialogScreen {
    private static final int CARD_W = 58;
    private static final int CARD_H = 42;
    private static final int GAP = 4;

    private final List<RecipeTypeDefinition> filtered = new ArrayList<>();
    private String query = "";
    private int scroll;
    private int gridX, gridY, gridW, gridH, cols;

    public VanillaTypePickerScreen(VanillaEditorScreen parent) {
        super(parent, Component.translatable("kjsgen.ui.pick_type"));
    }

    @Override
    protected void init() {
        centerDialog(272, 240);
        int x = dialogX + 10;
        int w = dialogW - 20;

        EditBox search = new EditBox(this.font, x, dialogY + 26, w, 16,
                Component.translatable("kjsgen.ui.search_types"));
        search.setHint(Component.translatable("kjsgen.ui.search_types"));
        search.setResponder(text -> {
            query = text == null ? "" : text.toLowerCase().trim();
            scroll = 0;
            refilter();
        });
        addRenderableWidget(search);

        gridX = x;
        gridY = dialogY + 48;
        gridW = w;
        gridH = dialogH - 48 - 28;
        cols = Math.max(1, (gridW + GAP) / (CARD_W + GAP));

        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.cancel"), b -> onClose())
                .bounds(x + w - 60, dialogY + dialogH - 24, 60, 18).build());

        refilter();
    }

    private void refilter() {
        filtered.clear();
        for (RecipeTypeDefinition type : RecipeTypeRegistry.all()) {
            if (!type.isAvailable()) {
                continue;
            }
            String name = VanillaEditorScreen.typeName(type).toLowerCase();
            if (query.isEmpty() || name.contains(query)
                    || type.id().contains(query) || type.modId().contains(query)) {
                filtered.add(type);
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaTheme.section(g, gridX, gridY, gridW, gridH);
        g.enableScissor(gridX + 1, gridY + 1, gridX + gridW - 1, gridY + gridH - 1);
        for (int i = 0; i < filtered.size(); i++) {
            RecipeTypeDefinition type = filtered.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = gridX + 2 + col * (CARD_W + GAP);
            int cy = gridY + 2 + row * (CARD_H + GAP) - scroll;
            if (cy + CARD_H < gridY || cy > gridY + gridH) {
                continue;
            }
            boolean hovered = mouseX >= cx && mouseX < cx + CARD_W
                    && mouseY >= Math.max(cy, gridY) && mouseY < Math.min(cy + CARD_H, gridY + gridH);
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hovered ? 0xFF33333E : VanillaTheme.PANEL_BG);
            g.renderOutline(cx, cy, CARD_W, CARD_H, hovered ? VanillaTheme.ACCENT : VanillaTheme.SECTION_BORDER);

            ItemStack icon = VanillaTheme.stackOf(SlotContent.item(type.iconItem(), 1));
            g.renderItem(icon, cx + CARD_W / 2 - 8, cy + 4);

            String name = VanillaEditorScreen.typeName(type);
            drawWrapped(g, name, cx + 2, cy + 22, CARD_W - 4, hovered ? VanillaTheme.TEXT : VanillaTheme.TEXT_DIM);
        }
        g.disableScissor();

        int rows = (filtered.size() + cols - 1) / cols;
        drawGridScrollbar(g, rows);
    }

    /** Draws up to two centred lines of the type name inside a card. */
    private void drawWrapped(GuiGraphics g, String text, int x, int y, int w, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(text), w);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            int lw = this.font.width(lines.get(i));
            g.drawString(this.font, lines.get(i), x + (w - lw) / 2, y + i * 9, color, true);
        }
    }

    private void drawGridScrollbar(GuiGraphics g, int rows) {
        int contentH = rows * (CARD_H + GAP);
        if (contentH <= gridH) {
            return;
        }
        int barH = Math.max(12, gridH * gridH / contentH);
        int maxScroll = contentH - gridH;
        int barY = gridY + (int) ((gridH - barH) * (scroll / (float) maxScroll));
        g.fill(gridX + gridW - 3, gridY, gridX + gridW - 1, gridY + gridH, 0x40000000);
        g.fill(gridX + gridW - 3, barY, gridX + gridW - 1, barY + barH, VanillaTheme.ACCENT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            for (int i = 0; i < filtered.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = gridX + 2 + col * (CARD_W + GAP);
                int cy = gridY + 2 + row * (CARD_H + GAP) - scroll;
                if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
                    ((VanillaEditorScreen) parent).addRecipeOfType(filtered.get(i));
                    onClose();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    //? if >=1.21 {
    /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
    *///?}
    //? if <1.21 {
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollY)) {
    //?}
            return true;
        }
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int rows = (filtered.size() + cols - 1) / cols;
            int max = Math.max(0, rows * (CARD_H + GAP) - gridH);
            scroll = Math.max(0, Math.min(scroll + (int) (-scrollY * (CARD_H + GAP)), max));
            return true;
        }
        return false;
    }
}
