package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.CreateSpecialModel;
import com.zizazr.kjsgen.core.CreateSpecialModel.StepType;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The non-standard editor surface for Create sequenced assembly, drawn inside the
 * editor's canvas box instead of the fixed slot layout.
 *
 * <p>Top strip: the starting {@code input}, the {@code transitional} item and up
 * to three result slots. Below: a vertical, scrollable list of processing stages
 * — each row shows the stage's ingredient slot (when it has one), the stage name
 * (click to cycle deploying → pressing → cutting → filling), a delete button and,
 * on the right edge, a drag grip for reordering. A trailing row appends a stage.
 */
final class VanillaSequencePanel {
    private static final int ROW_H = 20;
    private static final int GRIP_W = 10;

    // ---- geometry captured each frame (reused by the click handlers) -----
    private int rx, ry, rw, rh;
    private int listTop, listBottom;
    private int[] slotX = new int[2];
    private int slotY;
    /** The two positional slots that always exist; the results follow as a dynamic list. */
    private static final String[] FIXED = {"input", "transitional"};
    /** Horizontal gap between one result slot and the next (18px slot + 2px). */
    private static final int OUT_STEP = 20;
    private int outputX0;
    private int outputShown;

    // ---- scroll / drag state --------------------------------------------
    private int scroll;
    private int dragIndex = -1;
    private int dragMouseY;

    // ================================================================ render

    void render(GuiGraphics g, VanillaEditorScreen editor, RecipeInstance recipe,
                RecipeTypeDefinition type, int x, int y, int w, int h, int mouseX, int mouseY) {
        this.rx = x;
        this.ry = y;
        this.rw = w;
        this.rh = h;
        Font font = Minecraft.getInstance().font;
        VanillaTheme.section(g, x, y, w, h);

        // ---- top strip: two fixed slots + the dynamic result list ------
        slotY = y + 16;
        int inX = x + 6;
        int transX = inX + 22 + 12;
        outputX0 = transX + 22 + 18;
        slotX[0] = inX;
        slotX[1] = transX;
        label(g, font, "in", inX, slotY - 9);
        label(g, font, "item", transX, slotY - 9);
        label(g, font, "out", outputX0, slotY - 9);
        for (int i = 0; i < FIXED.length; i++) {
            int sx = slotX[i];
            boolean hovered = hover(mouseX, mouseY, sx, slotY);
            VanillaTheme.slot(g, sx, slotY, hovered);
            editor.drawSlotContent(g, sx, slotY, recipe.slot(FIXED[i]));
            if (editor.hasSlotIssue(FIXED[i])) {
                g.renderOutline(sx, slotY, 18, 18, VanillaTheme.ERROR);
            }
        }
        // results: one slot per entry (at least one placeholder), then a trailing '+'
        java.util.List<SlotContent> outputs = recipe.listSlots("output");
        outputShown = Math.max(1, outputs.size());
        boolean outIssue = editor.hasSlotIssue("output");
        for (int i = 0; i < outputShown; i++) {
            int sx = outputX0 + i * OUT_STEP;
            boolean hovered = hover(mouseX, mouseY, sx, slotY);
            VanillaTheme.slot(g, sx, slotY, hovered);
            editor.drawSlotContent(g, sx, slotY, i < outputs.size() ? outputs.get(i) : SlotContent.EMPTY);
            if (outIssue) {
                g.renderOutline(sx, slotY, 18, 18, VanillaTheme.ERROR);
            }
        }
        int plusX = outputX0 + outputShown * OUT_STEP;
        editor.drawPlusButton(g, plusX, slotY, hover(mouseX, mouseY, plusX, slotY));

        // ---- stages heading + divider ----------------------------------
        int headY = slotY + 22;
        g.drawString(font, Component.translatable("kjsgen.ui.seq_stages"),
                x + 6, headY, VanillaTheme.TEXT_DIM, true);
        VanillaTheme.separator(g, x + 4, x + w - 4, headY + 10);

        // ---- stage list (scrollable) -----------------------------------
        listTop = headY + 13;
        listBottom = y + h - 2;
        int n = CreateSpecialModel.seqLen(recipe, type);
        int listViewH = listBottom - listTop;
        int maxScroll = Math.max(0, (n + 1) * ROW_H - listViewH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(x + 1, listTop, x + w - 1, listBottom);
        for (int i = 0; i < n; i++) {
            if (i == dragIndex) {
                continue; // drawn floating on top below
            }
            int rowY = listTop + i * ROW_H - scroll;
            drawRow(g, editor, font, recipe, type, i, rx + 4, rowY, rw - 8, mouseX, mouseY, false);
        }
        // trailing "add stage" row
        int addY = listTop + n * ROW_H - scroll;
        drawAddRow(g, font, addY, mouseX, mouseY);
        g.disableScissor();

        // floating dragged row on top of everything
        if (dragIndex >= 0 && dragIndex < n) {
            int rowY = Math.max(listTop, Math.min(listBottom - ROW_H, dragMouseY - ROW_H / 2));
            drawRow(g, editor, font, recipe, type, dragIndex, rx + 4, rowY, rw - 8, mouseX, mouseY, true);
        }

        drawScrollbar(g, x + w - 3, listTop, listViewH, (n + 1) * ROW_H, scroll);
    }

    private void drawRow(GuiGraphics g, VanillaEditorScreen editor, Font font, RecipeInstance recipe,
                         RecipeTypeDefinition type, int i, int rowX, int rowY, int rowW,
                         int mouseX, int mouseY, boolean dragging) {
        StepType st = CreateSpecialModel.stepType(CreateSpecialModel.stepTypeId(recipe, type, i));
        boolean rowHovered = !dragging && mouseY >= rowY && mouseY < rowY + ROW_H
                && mouseX >= rowX && mouseX < rowX + rowW;
        int bg = dragging ? VanillaTheme.SELECT_BG : (rowHovered ? VanillaTheme.ROW_HOVER : 0);
        if (bg != 0) {
            g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H, bg);
        }
        g.fill(rowX, rowY, rowX + 2, rowY + ROW_H, VanillaTheme.ACCENT);

        int gripX = rowX + rowW - GRIP_W;
        int delX = gripX - 14;

        // ingredient slot (only for steps that consume something) or index badge
        if (st.hasItem()) {
            int sx = rowX + 4;
            int sy = rowY + 1;
            boolean sh = hover(mouseX, mouseY, sx, sy);
            VanillaTheme.slot(g, sx, sy, sh);
            editor.drawSlotContent(g, sx, sy, CreateSpecialModel.stepItem(recipe, i));
            if (editor.hasSlotIssue("seqItem" + i)) {
                g.renderOutline(sx, sy, 18, 18, VanillaTheme.ERROR);
            }
        } else {
            g.drawString(font, "#" + (i + 1), rowX + 8, rowY + 6, VanillaTheme.TEXT_DIM, true);
        }

        String name = Component.translatableWithFallback(
                "kjsgen.seq_step." + st.id(), st.id()).getString();
        String shown = font.plainSubstrByWidth(name, delX - (rowX + 26));
        g.drawString(font, shown, rowX + 26, rowY + 6, VanillaTheme.TEXT, true);

        // delete "x"
        int dc = (!dragging && mouseX >= delX && mouseX < delX + 9 && mouseY >= rowY && mouseY < rowY + ROW_H)
                ? VanillaTheme.ERROR : VanillaTheme.TEXT_DIM;
        for (int k = 0; k < 7; k++) {
            g.fill(delX + k, rowY + 6 + k, delX + k + 1, rowY + 7 + k, dc);
            g.fill(delX + 6 - k, rowY + 6 + k, delX + 7 - k, rowY + 7 + k, dc);
        }

        // drag grip (two columns of dots) on the right edge
        int gc = VanillaTheme.TEXT_DIM;
        for (int row = 0; row < 3; row++) {
            int gy = rowY + 6 + row * 3;
            g.fill(gripX + 2, gy, gripX + 4, gy + 2, gc);
            g.fill(gripX + 6, gy, gripX + 8, gy + 2, gc);
        }
    }

    private void drawAddRow(GuiGraphics g, Font font, int rowY, int mouseX, int mouseY) {
        boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_H
                && mouseX >= rx + 4 && mouseX < rx + rw - 4;
        int c = hovered ? VanillaTheme.TEXT : VanillaTheme.TEXT_DIM;
        g.drawString(font, Component.translatable("kjsgen.ui.seq_add_stage"),
                rx + 10, rowY + 6, c, true);
    }

    private static void label(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, VanillaTheme.TEXT_DIM, true);
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int scroll) {
        if (contentH <= viewH) {
            return;
        }
        int barH = Math.max(12, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int barY = y + (int) ((viewH - barH) * (scroll / (float) maxScroll));
        g.fill(x, y, x + 2, y + viewH, 0x40000000);
        g.fill(x, barY, x + 2, barY + barH, VanillaTheme.ACCENT);
    }

    // ================================================================ input

    boolean mouseClicked(VanillaEditorScreen editor, RecipeInstance recipe, RecipeTypeDefinition type,
                         double mouseX, double mouseY, int button) {
        // top-strip fixed slots
        for (int i = 0; i < FIXED.length; i++) {
            if (hit(mouseX, mouseY, slotX[i], slotY)) {
                SlotDefinition def = type.slot(FIXED[i]).orElse(null);
                if (def == null) {
                    return true;
                }
                if (button == 1) {
                    recipe.setSlot(FIXED[i], SlotContent.EMPTY);
                    editor.markDirty();
                } else {
                    editor.editSlotDef(recipe, def);
                }
                return true;
            }
        }
        // result list entries + the trailing '+' cell (which appends)
        for (int i = 0; i <= outputShown; i++) {
            if (hit(mouseX, mouseY, outputX0 + i * OUT_STEP, slotY)) {
                int size = recipe.listSlots("output").size();
                if (button == 1 && i < size) {
                    recipe.setListSlot("output", i, SlotContent.EMPTY);
                    editor.markDirty();
                } else if (button != 1) {
                    openOutputEditor(editor, recipe, type, i);
                }
                return true;
            }
        }

        if (mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        int n = CreateSpecialModel.seqLen(recipe, type);
        int rowW = rw - 8;
        int rowX = rx + 4;
        int idx = (int) ((mouseY - listTop + scroll) / ROW_H);
        if (idx < 0) {
            return false;
        }
        if (idx >= n) {
            // the trailing "+ add stage" row
            if (idx == n) {
                CreateSpecialModel.addStep(recipe, type, CreateSpecialModel.STEP_TYPES.get(1).id());
                editor.markDirty();
                return true;
            }
            return false;
        }

        int rowY = listTop + idx * ROW_H - scroll;
        int gripX = rowX + rowW - GRIP_W;
        int delX = gripX - 14;
        StepType st = CreateSpecialModel.stepType(CreateSpecialModel.stepTypeId(recipe, type, idx));

        if (mouseX >= gripX) {
            // start a reorder drag from the right-edge grip
            dragIndex = idx;
            dragMouseY = (int) mouseY;
            return true;
        }
        if (mouseX >= delX && mouseX < delX + 9) {
            CreateSpecialModel.removeStep(recipe, type, idx);
            editor.markDirty();
            return true;
        }
        if (st.hasItem() && mouseX >= rowX + 4 && mouseX < rowX + 22
                && mouseY >= rowY + 1 && mouseY < rowY + 19) {
            if (button == 1) {
                CreateSpecialModel.setStepItem(recipe, idx, SlotContent.EMPTY);
                editor.markDirty();
            } else {
                openStepItemEditor(editor, recipe, idx, st);
            }
            return true;
        }
        // clicking the name cycles the step type
        cycleStepType(recipe, idx, st, button);
        editor.markDirty();
        return true;
    }

    private void cycleStepType(RecipeInstance recipe, int idx, StepType current, int button) {
        var types = CreateSpecialModel.STEP_TYPES;
        int at = 0;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).id().equals(current.id())) {
                at = i;
                break;
            }
        }
        int step = button == 1 ? -1 : 1;
        int next = (at + step + types.size()) % types.size();
        CreateSpecialModel.setStepType(recipe, idx, types.get(next).id());
    }

    private void openOutputEditor(VanillaEditorScreen editor, RecipeInstance recipe,
                                  RecipeTypeDefinition type, int index) {
        SlotDefinition def = type.slot("output").orElse(null);
        if (def == null) {
            return;
        }
        java.util.List<SlotContent> outputs = recipe.listSlots("output");
        SlotContent initial = index < outputs.size() ? outputs.get(index) : SlotContent.EMPTY;
        Minecraft.getInstance().setScreen(new VanillaSlotEditScreen(editor, def, "output[" + (index + 1) + "]",
                initial, content -> recipe.setListSlot("output", index, content)));
    }

    private void openStepItemEditor(VanillaEditorScreen editor, RecipeInstance recipe, int idx, StepType st) {
        Minecraft mc = Minecraft.getInstance();
        SlotDefinition def = CreateSpecialModel.stepItemSlot(idx, st);
        SlotContent initial = CreateSpecialModel.stepItem(recipe, idx);
        mc.setScreen(new VanillaSlotEditScreen(editor, def, st.id() + " #" + (idx + 1), initial,
                content -> CreateSpecialModel.setStepItem(recipe, idx, content)));
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (dragIndex < 0) {
            return false;
        }
        dragMouseY = (int) mouseY;
        return true;
    }

    void mouseReleased(RecipeInstance recipe, RecipeTypeDefinition type) {
        if (dragIndex < 0) {
            return;
        }
        int n = CreateSpecialModel.seqLen(recipe, type);
        int target = (dragMouseY - listTop + scroll) / ROW_H;
        target = Math.max(0, Math.min(n - 1, target));
        if (target != dragIndex) {
            CreateSpecialModel.moveStep(recipe, type, dragIndex, target);
        }
        dragIndex = -1;
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll + (int) (-scrollY * ROW_H));
    }

    /** True while a stage row is being dragged for reordering (drives the "grabbing" cursor). */
    boolean isReordering() {
        return dragIndex >= 0;
    }

    // ---- hit helpers -----------------------------------------------------

    private boolean hover(int mouseX, int mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
    }

    private boolean hit(double mouseX, double mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
    }
}
