package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RemovalRule;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;
import com.zizazr.kjsgen.integration.kubejs.ScriptAssembler;
import com.zizazr.kjsgen.integration.net.ClientEditSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vanilla dialog managing the project's recipe-removal rules ({@code event.remove(...)}).
 * Left: the list of rules (icon + short label). Right: the selected rule's filters —
 * output item, input item(s), mod, recipe id; filled fields combine (all must match),
 * several inputs match any of them. Output/input items are picked through the same
 * {@link VanillaSlotEditScreen} used for recipe slots. A live preview of the generated
 * line sits below the fields.
 */
public final class VanillaRemovalsScreen extends VanillaDialogScreen {
    private static final int ROW_H = 18;
    private static final long PUSH_DELAY_MS = 350;

    /** Item/tag/fluid picker definitions for the output and input filter fields. */
    private static final SlotDefinition OUTPUT_DEF = new SlotDefinition(
            "output", SlotRole.OUTPUT, 0, 0, false, true, true, true, false, false, false, false);
    private static final SlotDefinition INPUT_DEF = new SlotDefinition(
            "input", SlotRole.INPUT, 0, 0, false, true, true, true, false, false, false, false);

    private final VanillaEditorScreen editor;
    /** When opened from the JEI "remove" button: the recipe id to ensure a rule exists for. */
    private final String pendingRecipeId;
    private String selectedUid;
    private int scroll;

    // geometry (computed in init)
    private int listX, listY, listW, listH;
    private int fieldX, ctrlX, ctrlW, labelW = 46;
    private int outSlotX, outSlotY;
    private int inX, inY, inPerRow;
    private int modLabelY, idLabelY, hintY;
    private int previewX, previewY, previewW, previewH;

    // debounced upstream push of the rule being edited (mod / recipe-id typing)
    private String pushUid;
    private long pushDueAt;

    public VanillaRemovalsScreen(VanillaEditorScreen parent) {
        this(parent, null);
    }

    private VanillaRemovalsScreen(VanillaEditorScreen parent, String pendingRecipeId) {
        super(parent, Component.translatable("kjsgen.ui.removals"));
        this.editor = parent;
        this.pendingRecipeId = pendingRecipeId;
    }

    /** Opens the screen ensuring a removal rule exists for {@code recipeId} (JEI "remove" button). */
    public static VanillaRemovalsScreen forRecipeId(VanillaEditorScreen parent, String recipeId) {
        return new VanillaRemovalsScreen(parent, recipeId);
    }

    private RecipeProject project() {
        return ClientEditSession.project();
    }

    private Optional<RemovalRule> selectedRule() {
        return selectedUid == null ? Optional.empty() : project().removalByUid(selectedUid);
    }

    /**
     * If this screen was opened for a specific recipe id, make sure a rule for it exists and is
     * selected. Re-runs on every (re)init, so a server snapshot that replaced the project — which
     * can drop a rule added before the session went remote — re-adds and re-pushes it.
     */
    private void ensurePendingRule() {
        if (pendingRecipeId == null) {
            return;
        }
        RemovalRule rule = project().removalByRecipeId(pendingRecipeId).orElse(null);
        if (rule == null) {
            rule = new RemovalRule();
            rule.setRecipeId(pendingRecipeId);
            project().addRemoval(rule);
            ClientEditSession.pushRemoval(rule);
        }
        selectedUid = rule.uid();
    }

    @Override
    protected void init() {
        centerDialog(384, 248);
        int pad = 10;

        ensurePendingRule();
        if (selectedUid == null && !project().removals().isEmpty()) {
            selectedUid = project().removals().get(0).uid();
        }

        // left column: rule list + add button
        listX = dialogX + pad;
        listY = dialogY + 26;
        listW = 128;
        listH = dialogH - 26 - 6 - 16 - pad;
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.removal_add"), b -> addRule())
                .bounds(listX, listY + listH + 4, listW, 16).build());

        // right column geometry
        fieldX = listX + listW + 8;
        ctrlX = fieldX + labelW;
        ctrlW = dialogX + dialogW - pad - ctrlX;

        int y = dialogY + 26;
        outSlotX = ctrlX;
        outSlotY = y;
        y += 22;
        inX = ctrlX;
        inY = y;
        inPerRow = Math.max(1, ctrlW / ROW_H);
        y += 40; // up to two rows of input slots
        RemovalRule rule = selectedRule().orElse(null);

        // mod + recipe-id text fields (only when a rule is selected — the slots and these boxes
        // are hidden and the right column just shows a "select a rule" hint otherwise)
        modLabelY = y + 3;
        if (rule != null) {
            EditBox modBox = new EditBox(this.font, ctrlX, y, ctrlW, 14,
                    Component.translatable("kjsgen.ui.removal_mod"));
            modBox.setMaxLength(128);
            modBox.setValue(rule.mod());
            modBox.setHint(Component.literal("mekanism"));
            RemovalRule r = rule;
            modBox.setResponder(v -> {
                r.setMod(v);
                markDirty(r);
            });
            addRenderableWidget(modBox);
        }
        y += 22;

        idLabelY = y + 3;
        if (rule != null) {
            EditBox idBox = new EditBox(this.font, ctrlX, y, ctrlW, 14,
                    Component.translatable("kjsgen.ui.removal_recipe_id"));
            idBox.setMaxLength(256);
            idBox.setValue(rule.recipeId());
            idBox.setHint(Component.literal("namespace:path"));
            RemovalRule r = rule;
            idBox.setResponder(v -> {
                r.setRecipeId(v);
                markDirty(r);
            });
            addRenderableWidget(idBox);
        }
        y += 20;

        hintY = y;
        y += 12;
        previewX = fieldX;
        previewY = y;
        previewW = dialogX + dialogW - pad - fieldX;
        previewH = dialogY + dialogH - 30 - previewY;

        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.close"), b -> onClose())
                .bounds(dialogX + dialogW - pad - 60, dialogY + dialogH - 24, 60, 18).build());
    }

    // ------------------------------------------------------------------ actions

    private void addRule() {
        flushPush();
        RemovalRule rule = new RemovalRule();
        project().addRemoval(rule);
        ClientEditSession.pushRemoval(rule);
        selectedUid = rule.uid();
        rebuildWidgets();
    }

    private void deleteRule(RemovalRule rule) {
        flushPush();
        project().removeRemoval(rule.uid());
        ClientEditSession.removeRemoval(rule.uid());
        if (rule.uid().equals(selectedUid)) {
            selectedUid = project().removals().isEmpty() ? null : project().removals().get(0).uid();
        }
        rebuildWidgets();
    }

    private void pickOutput() {
        RemovalRule rule = selectedRule().orElse(null);
        if (rule == null || this.minecraft == null) {
            return;
        }
        flushPush();
        this.minecraft.setScreen(new VanillaSlotEditScreen(this, OUTPUT_DEF, "kjsgen.ui.removal_output",
                contentFromFilter(rule.output()), content -> {
                    rule.setOutput(filterString(content));
                    ClientEditSession.pushRemoval(rule);
                }));
    }

    private void pickInput(int index) {
        RemovalRule rule = selectedRule().orElse(null);
        if (rule == null || this.minecraft == null) {
            return;
        }
        flushPush();
        List<String> ins = rule.inputs();
        SlotContent initial = index < ins.size() ? contentFromFilter(ins.get(index)) : SlotContent.EMPTY;
        this.minecraft.setScreen(new VanillaSlotEditScreen(this, INPUT_DEF, "kjsgen.ui.removal_inputs",
                initial, content -> setInput(rule, index, content)));
    }

    /** Set (or clear/append) one input entry, then re-serialise the comma-joined input field. */
    private void setInput(RemovalRule rule, int index, SlotContent content) {
        List<String> ins = new ArrayList<>(rule.inputs());
        String s = filterString(content);
        if (s.isEmpty()) {
            if (index < ins.size()) {
                ins.remove(index);
            }
        } else if (index < ins.size()) {
            ins.set(index, s);
        } else {
            ins.add(s);
        }
        rule.setInput(String.join(", ", ins));
        ClientEditSession.pushRemoval(rule);
    }

    private void markDirty(RemovalRule rule) {
        pushUid = rule.uid();
        pushDueAt = System.currentTimeMillis() + PUSH_DELAY_MS;
    }

    private void flushPush() {
        if (pushUid == null) {
            return;
        }
        String uid = pushUid;
        pushUid = null;
        project().removalByUid(uid).ifPresent(ClientEditSession::pushRemoval);
    }

    @Override
    public void tick() {
        super.tick();
        if (pushUid != null && System.currentTimeMillis() >= pushDueAt) {
            flushPush();
        }
    }

    @Override
    public void removed() {
        flushPush();
    }

    /** Re-sync after a server snapshot/delta arrived (this screen is the current one). */
    public void refreshFromSession() {
        editor.refreshFromSession();
        ensurePendingRule();
        if (selectedUid != null && project().removalByUid(selectedUid).isEmpty()) {
            selectedUid = null;
        }
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawRuleList(g, mouseX, mouseY);

        RemovalRule rule = selectedRule().orElse(null);
        if (rule == null) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.removal_none_selected"),
                    fieldX, dialogY + 30, VanillaTheme.TEXT_DIM, true);
            return;
        }

        // output
        g.drawString(this.font, Component.translatable("kjsgen.ui.removal_output"),
                fieldX, outSlotY + 5, VanillaTheme.TEXT_DIM, true);
        boolean outHover = inRect(mouseX, mouseY, outSlotX, outSlotY, 18, 18);
        VanillaTheme.slot(g, outSlotX, outSlotY, outHover);
        drawFilterIcon(g, outSlotX, outSlotY, rule.output());

        // inputs: one slot per entry + a trailing '+'
        g.drawString(this.font, Component.translatable("kjsgen.ui.removal_inputs"),
                fieldX, inY + 5, VanillaTheme.TEXT_DIM, true);
        List<String> ins = rule.inputs();
        for (int i = 0; i <= ins.size(); i++) {
            int sx = inX + (i % inPerRow) * ROW_H;
            int sy = inY + (i / inPerRow) * ROW_H;
            boolean hov = inRect(mouseX, mouseY, sx, sy, 18, 18);
            if (i < ins.size()) {
                VanillaTheme.slot(g, sx, sy, hov);
                drawFilterIcon(g, sx, sy, ins.get(i));
            } else {
                editor.drawPlusButton(g, sx, sy, hov);
            }
        }

        // mod / recipe-id labels (the boxes are widgets)
        g.drawString(this.font, Component.translatable("kjsgen.ui.removal_mod"),
                fieldX, modLabelY, VanillaTheme.TEXT_DIM, true);
        g.drawString(this.font, Component.translatable("kjsgen.ui.removal_recipe_id"),
                fieldX, idLabelY, VanillaTheme.TEXT_DIM, true);

        drawPreview(g, rule);
    }

    private void drawRuleList(GuiGraphics g, int mouseX, int mouseY) {
        List<RemovalRule> rules = project().removals();
        VanillaTheme.section(g, listX, listY, listW, listH);
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int i = 0; i < rules.size(); i++) {
            RemovalRule rule = rules.get(i);
            int ryy = listY + i * ROW_H - scroll;
            if (ryy + ROW_H < listY || ryy > listY + listH) {
                continue;
            }
            boolean selected = rule.uid().equals(selectedUid);
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= Math.max(ryy, listY) && mouseY < Math.min(ryy + ROW_H, listY + listH);
            if (selected) {
                g.fill(listX + 1, ryy, listX + listW - 1, ryy + ROW_H, VanillaTheme.SELECT_BG);
                g.fill(listX + 1, ryy, listX + 3, ryy + ROW_H, VanillaTheme.ACCENT);
            } else if (hovered) {
                g.fill(listX + 1, ryy, listX + listW - 1, ryy + ROW_H, VanillaTheme.ROW_HOVER);
            }

            int textX = listX + 5;
            ItemStack icon = iconForFilter(rule.iconId());
            if (!icon.isEmpty()) {
                g.renderItem(icon, listX + 3, ryy + 1);
                textX = listX + 22;
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(rule.describe(), listX + listW - 16 - textX),
                    textX, ryy + 5, VanillaTheme.TEXT, true);

            // delete "x" on the right edge of the row
            int dx = listX + listW - 13;
            int c = hovered ? VanillaTheme.ERROR : VanillaTheme.TEXT_DIM;
            for (int k = 0; k < 7; k++) {
                g.fill(dx + k, ryy + 6 + k, dx + k + 1, ryy + 7 + k, c);
                g.fill(dx + 6 - k, ryy + 6 + k, dx + 7 - k, ryy + 7 + k, c);
            }
        }
        g.disableScissor();
        if (rules.isEmpty()) {
            int y = listY + 6;
            for (FormattedCharSequence line : this.font.split(
                    Component.translatable("kjsgen.ui.removal_empty"), listW - 12)) {
                g.drawString(this.font, line, listX + 6, y, VanillaTheme.TEXT_DIM, true);
                y += 10;
            }
        }
    }

    /** Draw the item/tag icon of one filter string into an 18x18 slot at (x,y). */
    private void drawFilterIcon(GuiGraphics g, int x, int y, String filter) {
        ItemStack icon = iconForFilter(filter);
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 1, y + 1);
        }
        if (filter.startsWith("#")) {
            g.drawString(this.font, "#", x + 1, y + 1, VanillaTheme.WARN, true);
        }
    }

    /** The item icon for a filter string ("id" or "#tag"); empty for a blank filter. */
    private ItemStack iconForFilter(String filter) {
        if (filter == null || filter.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return filter.startsWith("#")
                ? VanillaTheme.iconStackFor(SlotContent.itemTag(filter.substring(1)))
                : VanillaTheme.itemStack(filter);
    }

    private void drawPreview(GuiGraphics g, RemovalRule rule) {
        VanillaTheme.section(g, previewX, previewY, previewW, previewH);
        String text = ScriptAssembler.removalStatement(rule)
                .orElse(Component.translatable("kjsgen.ui.removal_empty_rule").getString());
        int color = rule.isEmpty() ? VanillaTheme.TEXT_DIM : VanillaTheme.TEXT;
        int y = previewY + 4;
        for (FormattedCharSequence line : this.font.split(Component.literal(text), previewW - 8)) {
            if (y + 9 > previewY + previewH) {
                break;
            }
            g.drawString(this.font, line, previewX + 4, y, color, true);
            y += 9;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // rule list rows
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int index = (int) ((mouseY - listY + scroll) / ROW_H);
            List<RemovalRule> rules = project().removals();
            if (index >= 0 && index < rules.size()) {
                RemovalRule rule = rules.get(index);
                if (mouseX >= listX + listW - 16) {
                    deleteRule(rule);
                } else if (!rule.uid().equals(selectedUid)) {
                    flushPush();
                    selectedUid = rule.uid();
                    rebuildWidgets();
                }
            }
            return true;
        }
        // output / input slot boxes (only meaningful with a rule selected)
        RemovalRule rule = selectedRule().orElse(null);
        if (rule != null && button == 0) {
            if (inRect(mouseX, mouseY, outSlotX, outSlotY, 18, 18)) {
                pickOutput();
                return true;
            }
            List<String> ins = rule.inputs();
            for (int i = 0; i <= ins.size(); i++) {
                int sx = inX + (i % inPerRow) * ROW_H;
                int sy = inY + (i / inPerRow) * ROW_H;
                if (inRect(mouseX, mouseY, sx, sy, 18, 18)) {
                    pickInput(i);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int max = Math.max(0, project().removals().size() * ROW_H - listH);
            scroll = Math.max(0, Math.min(scroll + (int) (-scrollY * ROW_H), max));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ filter <-> content

    /** Turn a picked {@link SlotContent} into a KubeJS filter token ("id" or "#tag"). */
    private static String filterString(SlotContent content) {
        if (content.isEmpty()) {
            return "";
        }
        return switch (content.kind()) {
            case ITEM_TAG, FLUID_TAG, CHEMICAL_TAG -> "#" + content.id();
            default -> content.id();
        };
    }

    /** Turn a stored filter token back into content to seed the picker (item, or item tag for '#'). */
    private static SlotContent contentFromFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return SlotContent.EMPTY;
        }
        return filter.startsWith("#")
                ? SlotContent.itemTag(filter.substring(1))
                : SlotContent.item(filter, 1);
    }
}
