package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.integration.mekanism.MekanismChemicals;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla dialog to edit one slot. Two panels: a parameter column on the left
 * (kind / id / count / amount / chance / components) and a search panel on the
 * right that shows items and fluids as an inventory-style icon grid (tags as a
 * list with a once-per-second cycling member icon), plus rows of recently used
 * and favourite picks. Left-click selects, right-click toggles a favourite.
 */
public final class VanillaSlotEditScreen extends VanillaDialogScreen {

    /** Fluid/chemical amount display unit. Content is always stored in mB. */
    private enum Unit {
        MB("kjsgen.ui.unit_mb", 1),
        B("kjsgen.ui.unit_b", 1000);

        final String key;
        final int factor;

        Unit(String key, int factor) {
            this.key = key;
            this.factor = factor;
        }
    }

    /** One pickable entry, pre-resolved for the current {@link #kind}. */
    private record Entry(String id, String label, ItemStack icon,
                         List<ItemStack> members, MekanismChemicals.ChemicalInfo chemical) {
    }

    private final SlotDefinition slotDef;
    /** Where the edited content is written on OK / Clear (a plain slot or one list entry). */
    private final java.util.function.Consumer<SlotContent> sink;

    // ---- mutable edit state (survives widget rebuilds on kind change) ----
    private ContentKind kind;
    private String id;
    private int count;
    private int amount;
    private Unit amountUnit = Unit.MB;
    private float chance;
    private String components;
    private String searchQuery = "";

    // ---- widgets kept for cross-updates ----------------------------------
    private EditBox idField;
    private EditBox countField;
    private EditBox amountField;
    private EditBox chanceField;

    // ---- search / picks state --------------------------------------------
    /** All matching ids for the current query (cheap ids, not materialised entries). */
    private final List<String> matchedIds = new ArrayList<>();
    /** Materialised window of {@link #matchedIds}, the only entries actually rendered. */
    private final List<Entry> results = new ArrayList<>();
    /** Index into {@link #matchedIds} that {@code results.get(0)} corresponds to. */
    private int resultWindowStart;
    private static final int WINDOW_SIZE = 300;
    private int resultScroll;
    private int favScroll;

    // region rectangles (computed in init)
    private int leftX, leftW, wx, ww;
    private int resultsX, resultsY, resultsW, resultsH;
    private int recentX, recentY, recentW, recentH, recentLabelY;
    private int favX, favY, favW, favH, favLabelY;

    // per-frame hover, drawn as a tooltip after the widgets
    private Entry hoverEntry;

    public VanillaSlotEditScreen(VanillaEditorScreen parent, RecipeInstance recipe, SlotDefinition slotDef) {
        this(parent, slotDef, slotDef.key(), recipe.slot(slotDef.key()),
                content -> recipe.setSlot(slotDef.key(), content));
    }

    /**
     * Edit an arbitrary piece of content, writing the result back through {@code sink}.
     * Used for list-slot entries, where the storage key ("in0", "in1", ...) differs
     * from the layout slot key and clearing must compact the list. The parent may be the
     * editor or any other kjsgen dialog (e.g. the removals screen); {@code markDirty} is only
     * dispatched when it actually is the editor.
     */
    public VanillaSlotEditScreen(Screen parent, SlotDefinition slotDef, String titleKey,
                                 SlotContent initial, java.util.function.Consumer<SlotContent> sink) {
        super(parent, Component.translatable("kjsgen.ui.edit_slot", titleKey));
        this.slotDef = slotDef;
        this.sink = sink;
        this.kind = initial.kind() == ContentKind.EMPTY ? firstAllowedKind(slotDef) : initial.kind();
        this.id = initial.id();
        this.count = initial.count();
        this.amount = initial.amount();
        this.chance = initial.chance();
        this.components = initial.components();
    }

    @Override
    protected void init() {
        centerDialog(548, 322);

        int m = 8;
        leftX = dialogX + m;
        leftW = 190;
        int labelW = 44;
        wx = leftX + labelW + 4;
        ww = leftW - labelW - 4;
        int rightX = leftX + leftW + 10;
        int rightW = dialogX + dialogW - m - rightX;

        buildLeftColumn();
        buildRightColumn(rightX, rightW);

        runSearch(searchQuery);
    }

    // ------------------------------------------------------------- left column

    private void buildLeftColumn() {
        int y = dialogY + 24 + 20 + 6; // below the "selected" preview strip

        List<ContentKind> kinds = allowedKinds(slotDef);
        if (kinds.size() > 1) {
            addRenderableWidget(CycleButton.<ContentKind>builder(k -> Component.literal(kindLabel(k)))
                    .withValues(kinds)
                    .withInitialValue(kind)
                    .create(wx, y, ww, 16, Component.translatable("kjsgen.ui.kind"),
                            (btn, v) -> {
                                this.kind = v;
                                this.resultScroll = 0;
                                this.favScroll = 0;
                                rebuildWidgets();
                            }));
            y += 20;
        }

        // id
        idField = new EditBox(this.font, wx, y, ww, 16, Component.translatable("kjsgen.ui.id"));
        idField.setMaxLength(256);
        idField.setValue(id);
        idField.setHint(Component.literal("minecraft:stick"));
        idField.setResponder(v -> this.id = v.trim());
        addRenderableWidget(idField);
        y += 20;

        // count with -/+ (items only, when the slot allows editing it)
        if (kind == ContentKind.ITEM && slotDef.allowsCount()) {
            int bw = 16;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> nudgeCount(-1))
                    .bounds(wx, y, bw, 16).build());
            countField = new EditBox(this.font, wx + bw + 2, y, ww - 2 * bw - 4, 16,
                    Component.translatable("kjsgen.ui.count"));
            countField.setValue(Integer.toString(count));
            countField.setFilter(s -> s.matches("\\d*"));
            countField.setResponder(s -> count = clamp(parseInt(s, count), 1, 999));
            addRenderableWidget(countField);
            addRenderableWidget(Button.builder(Component.literal("+"), b -> nudgeCount(1))
                    .bounds(wx + ww - bw, y, bw, 16).build());
            y += 20;
        }

        // fluid / chemical amount with a mB<->B unit toggle
        if (kind.hasAmount()) {
            int unitW = 34;
            amountField = new EditBox(this.font, wx, y, ww - unitW - 2, 16,
                    Component.translatable("kjsgen.ui.amount"));
            amountField.setValue(formatAmount());
            amountField.setFilter(s -> s.matches("\\d*\\.?\\d*"));
            amountField.setResponder(this::onAmountEdited);
            addRenderableWidget(amountField);
            addRenderableWidget(CycleButton.<Unit>builder(u -> Component.translatable(u.key))
                    .withValues(Unit.MB, Unit.B)
                    .withInitialValue(amountUnit)
                    .create(wx + ww - unitW, y, unitW, 16, Component.empty(),
                            (btn, u) -> {
                                this.amountUnit = u;
                                if (amountField != null) {
                                    amountField.setValue(formatAmount());
                                }
                            }));
            y += 20;
        }

        // chance in percent (items only, when the slot allows it)
        if (kind == ContentKind.ITEM && slotDef.allowsChance()) {
            chanceField = new EditBox(this.font, wx, y, ww - 14, 16,
                    Component.translatable("kjsgen.ui.chance"));
            chanceField.setValue(Integer.toString(Math.round(chance * 100)));
            chanceField.setFilter(s -> s.matches("\\d*"));
            chanceField.setResponder(s -> chance = clamp(parseInt(s, Math.round(chance * 100)), 0, 100) / 100f);
            addRenderableWidget(chanceField);
            y += 20;
        }

        // components / NBT (items only)
        if (kind == ContentKind.ITEM) {
            EditBox componentsField = new EditBox(this.font, wx, y, ww, 16,
                    Component.translatable("kjsgen.ui.components"));
            componentsField.setMaxLength(512);
            componentsField.setValue(components);
            componentsField.setHint(Component.literal("[minecraft:damage=5]"));
            componentsField.setResponder(s -> components = s.trim());
            addRenderableWidget(componentsField);
        }

        // action buttons pinned to the bottom of the left column
        int by = dialogY + dialogH - 22;
        int bw3 = (leftW - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.ok"), b -> commit())
                .bounds(leftX, by, bw3, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.clear"), b -> {
            sink.accept(SlotContent.EMPTY);
            if (parent instanceof VanillaEditorScreen editor) {
                editor.markDirty();
            }
            onClose();
        }).bounds(leftX + bw3 + 4, by, bw3, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.cancel"), b -> onClose())
                .bounds(leftX + 2 * (bw3 + 4), by, leftW - 2 * (bw3 + 4), 18).build());
    }

    // ------------------------------------------------------------ right column

    private void buildRightColumn(int rightX, int rightW) {
        EditBox searchField = new EditBox(this.font, rightX, dialogY + 24, rightW, 16,
                Component.translatable("kjsgen.ui.search"));
        searchField.setValue(searchQuery);
        searchField.setHint(Component.translatable("kjsgen.ui.search"));
        searchField.setResponder(this::runSearch);
        addRenderableWidget(searchField);

        // stack the three regions: results fills the gap above recent + favourites
        int favBottomMargin = dialogY + dialogH - 8;
        favH = kind.isTag() ? 46 : 44;
        favX = rightX;
        favW = rightW;
        favY = favBottomMargin - favH;
        favLabelY = favY - 10;

        recentH = 20;
        recentX = rightX;
        recentW = rightW;
        recentY = favLabelY - 4 - recentH;
        recentLabelY = recentY - 10;

        resultsX = rightX;
        resultsY = dialogY + 24 + 20;
        resultsW = rightW;
        resultsH = recentLabelY - 4 - resultsY;
    }

    // ------------------------------------------------------------------ actions

    private void nudgeCount(int delta) {
        count = clamp(count + delta, 1, 999);
        if (countField != null) {
            countField.setValue(Integer.toString(count));
        }
    }

    private void onAmountEdited(String s) {
        try {
            double value = Double.parseDouble(s.trim());
            amount = Math.max(1, (int) Math.round(value * amountUnit.factor));
        } catch (NumberFormatException ignored) {
        }
    }

    private String formatAmount() {
        if (amountUnit == Unit.MB) {
            return Integer.toString(amount);
        }
        double b = amount / 1000.0;
        return b == Math.floor(b) ? Long.toString((long) b) : Double.toString(b);
    }

    /** Pick an entry into the slot (updates the id field + selected preview). */
    private void select(Entry entry) {
        this.id = entry.id();
        if (idField != null) {
            idField.setValue(this.id);
        }
    }

    private void commit() {
        if (!id.isBlank()) {
            SlotPicks.addRecent(kind, id);
        }
        sink.accept(buildContent());
        if (parent instanceof VanillaEditorScreen editor) {
            editor.markDirty();
        }
        onClose();
    }

    private SlotContent buildContent() {
        if (id.isBlank()) {
            return SlotContent.EMPTY;
        }
        return switch (kind) {
            case ITEM -> SlotContent.item(id, count).withChance(chance).withComponents(components);
            case ITEM_TAG -> SlotContent.itemTag(id);
            case FLUID -> SlotContent.fluid(id, amount);
            case FLUID_TAG -> SlotContent.fluidTag(id, amount);
            case CHEMICAL -> SlotContent.chemical(id, amount);
            case CHEMICAL_TAG -> SlotContent.chemicalTag(id, amount);
            case EMPTY -> SlotContent.EMPTY;
        };
    }

    // ------------------------------------------------------------------ search

    private void runSearch(String word) {
        this.searchQuery = word;
        matchedIds.clear();
        resultScroll = 0;
        String query = word.toLowerCase().trim();
        switch (kind) {
            case ITEM -> {
                for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                    Item item = BuiltInRegistries.ITEM.get(key);
                    String name = item.getDescription().getString();
                    if (query.isEmpty() || key.toString().contains(query)
                            || name.toLowerCase().contains(query)) {
                        matchedIds.add(key.toString());
                    }
                }
            }
            case ITEM_TAG -> {
                for (var tagKey : BuiltInRegistries.ITEM.getTagNames().toList()) {
                    String tagId = tagKey.location().toString();
                    if (query.isEmpty() || tagId.contains(query)) {
                        matchedIds.add(tagId);
                    }
                }
            }
            case FLUID -> {
                for (ResourceLocation key : BuiltInRegistries.FLUID.keySet()) {
                    if (query.isEmpty() || key.toString().contains(query)) {
                        matchedIds.add(key.toString());
                    }
                }
            }
            case FLUID_TAG -> {
                for (var tagKey : BuiltInRegistries.FLUID.getTagNames().toList()) {
                    String tagId = tagKey.location().toString();
                    if (query.isEmpty() || tagId.contains(query)) {
                        matchedIds.add(tagId);
                    }
                }
            }
            case CHEMICAL -> {
                for (var info : MekanismChemicals.search(query, Integer.MAX_VALUE)) {
                    matchedIds.add(info.id());
                }
            }
            case CHEMICAL_TAG -> {
                for (var info : MekanismChemicals.searchTags(query, Integer.MAX_VALUE)) {
                    matchedIds.add(info.id());
                }
            }
            default -> {
            }
        }
        loadWindow(0);
    }

    /**
     * Materialises {@link #results} as a slice of {@link #matchedIds} starting at
     * {@code start}, so only the items actually near the viewport pay for icon/tag
     * resolution. Scrolling past the loaded slice re-centres the window, discarding
     * the entries that scrolled out of range.
     */
    private void loadWindow(int start) {
        int max = Math.max(0, matchedIds.size() - WINDOW_SIZE);
        resultWindowStart = clamp(start, 0, max);
        int end = Math.min(matchedIds.size(), resultWindowStart + WINDOW_SIZE);
        results.clear();
        for (int i = resultWindowStart; i < end; i++) {
            results.add(makeEntry(matchedIds.get(i)));
        }
    }

    /** Re-centres the loaded window if the visible index range has scrolled outside it. */
    private void ensureWindowCovers(int firstVisible, int lastVisible) {
        if (firstVisible >= resultWindowStart && lastVisible < resultWindowStart + results.size()) {
            return;
        }
        loadWindow(firstVisible - WINDOW_SIZE / 3);
    }

    /** Resolve a raw id (from recents/favourites/tag search) into a renderable entry. */
    private Entry makeEntry(String rawId) {
        return switch (kind) {
            case ITEM -> new Entry(rawId, itemName(rawId), VanillaTheme.itemStack(rawId), List.of(), null);
            case FLUID -> new Entry(rawId, fluidName(rawId), VanillaTheme.fluidBucket(rawId), List.of(), null);
            case ITEM_TAG -> {
                List<ItemStack> members = VanillaTheme.tagStacks(rawId);
                yield new Entry(rawId, "#" + rawId, members.isEmpty() ? new ItemStack(Items.BARRIER) : members.get(0),
                        members, null);
            }
            case FLUID_TAG -> {
                List<ItemStack> members = VanillaTheme.fluidTagStacks(rawId);
                yield new Entry(rawId, "#" + rawId, members.isEmpty() ? new ItemStack(Items.WATER_BUCKET) : members.get(0),
                        members, null);
            }
            case CHEMICAL -> {
                var info = MekanismChemicals.byId(rawId).orElse(null);
                yield new Entry(rawId, info != null ? info.name() : rawId, ItemStack.EMPTY, List.of(), info);
            }
            case CHEMICAL_TAG -> {
                var info = MekanismChemicals.tagSample(rawId).orElse(null);
                yield new Entry(rawId, "#" + rawId, ItemStack.EMPTY, List.of(), info);
            }
            default -> new Entry(rawId, rawId, ItemStack.EMPTY, List.of(), null);
        };
    }

    private List<Entry> entriesFor(List<String> ids) {
        List<Entry> out = new ArrayList<>(ids.size());
        for (String rawId : ids) {
            out.add(makeEntry(rawId));
        }
        return out;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoverEntry = null;
        long cycle = System.currentTimeMillis() / 1000L;
        boolean tag = kind.isTag();

        // ---- left column: labels + selected preview ----
        drawLeftLabels(g);
        drawSelectedPreview(g);

        // ---- right column: results / recent / favourites ----
        Entry hovered = null;

        ensureWindowCovers(visibleFirstIndex(resultScroll, resultsW, resultsH, tag),
                visibleLastIndex(resultScroll, resultsW, resultsH, tag));
        VanillaTheme.section(g, resultsX, resultsY, resultsW, resultsH);
        List<Entry> shownResults = results;
        Entry h1 = tag
                ? drawList(g, shownResults, resultsX, resultsY, resultsW, resultsH, resultScroll, mouseX, mouseY, cycle, resultWindowStart)
                : drawGrid(g, shownResults, resultsX, resultsY, resultsW, resultsH, resultScroll, mouseX, mouseY, cycle, false, resultWindowStart);
        if (h1 != null) {
            hovered = h1;
        }
        if (matchedIds.isEmpty()) {
            g.drawString(this.font, "...", resultsX + 6, resultsY + 6, VanillaTheme.TEXT_DIM, true);
        }

        g.drawString(this.font, Component.translatable("kjsgen.ui.recent"),
                recentX, recentLabelY, VanillaTheme.TEXT_DIM, true);
        VanillaTheme.section(g, recentX, recentY, recentW, recentH);
        List<Entry> recent = entriesFor(SlotPicks.recent(kind));
        Entry h2 = drawGrid(g, recent, recentX, recentY, recentW, recentH, 0, mouseX, mouseY, cycle, true, 0);
        if (h2 != null) {
            hovered = h2;
        }

        g.drawString(this.font, Component.translatable("kjsgen.ui.favorites"),
                favX, favLabelY, VanillaTheme.TEXT_DIM, true);
        VanillaTheme.section(g, favX, favY, favW, favH);
        List<Entry> favorites = entriesFor(SlotPicks.favorites(kind));
        Entry h3 = tag
                ? drawList(g, favorites, favX, favY, favW, favH, favScroll, mouseX, mouseY, cycle, 0)
                : drawGrid(g, favorites, favX, favY, favW, favH, favScroll, mouseX, mouseY, cycle, false, 0);
        if (h3 != null) {
            hovered = h3;
        }

        hoverEntry = hovered;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // drawn last so the tooltip sits above every widget and item batch
        if (hoverEntry != null) {
            drawEntryTooltip(g, hoverEntry, mouseX, mouseY);
        }
    }

    private void drawLeftLabels(GuiGraphics g) {
        int y = dialogY + 24 + 20 + 6;
        if (allowedKinds(slotDef).size() > 1) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.kind"), leftX, y + 4, VanillaTheme.TEXT_DIM, true);
            y += 20;
        }
        g.drawString(this.font, Component.translatable("kjsgen.ui.id"), leftX, y + 4, VanillaTheme.TEXT_DIM, true);
        y += 20;
        if (kind == ContentKind.ITEM && slotDef.allowsCount()) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.count"), leftX, y + 4, VanillaTheme.TEXT_DIM, true);
            y += 20;
        }
        if (kind.hasAmount()) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.amount"), leftX, y + 4, VanillaTheme.TEXT_DIM, true);
            y += 20;
        }
        if (kind == ContentKind.ITEM && slotDef.allowsChance()) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.chance"), leftX, y + 4, VanillaTheme.TEXT_DIM, true);
            g.drawString(this.font, "%", wx + ww - 10, y + 4, VanillaTheme.TEXT_DIM, true);
            y += 20;
        }
        if (kind == ContentKind.ITEM) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.components"), leftX, y + 4, VanillaTheme.TEXT_DIM, true);
        }
    }

    private void drawSelectedPreview(GuiGraphics g) {
        int py = dialogY + 24;
        VanillaTheme.section(g, leftX, py, leftW, 20);
        String label;
        if (id.isBlank()) {
            label = "-";
        } else {
            Entry entry = makeEntry(id);
            drawEntryIcon(g, entry, leftX + 2, py + 2, System.currentTimeMillis() / 1000L);
            label = entry.label();
        }
        String clipped = this.font.plainSubstrByWidth(label, leftW - 26);
        g.drawString(this.font, clipped, leftX + 22, py + 6, VanillaTheme.TEXT, true);
    }

    /** Draw a scrollable icon grid; returns the hovered entry (or null). */
    private Entry drawGrid(GuiGraphics g, List<Entry> list, int x, int y, int w, int h,
                           int scroll, int mouseX, int mouseY, long cycle, boolean singleRow, int indexOffset) {
        int cols = Math.max(1, w / 20);
        int shown = singleRow ? Math.min(list.size(), cols) : list.size();
        Entry hovered = null;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for (int i = 0; i < shown; i++) {
            int abs = indexOffset + i;
            int col = abs % cols;
            int row = abs / cols;
            int cx = x + 1 + col * 20;
            int cy = y + 1 + row * 20 - (singleRow ? 0 : scroll);
            if (cy + 18 < y || cy > y + h) {
                continue;
            }
            Entry entry = list.get(i);
            boolean hov = mouseX >= cx && mouseX < cx + 18 && mouseX < x + w
                    && mouseY >= Math.max(cy, y) && mouseY < Math.min(cy + 18, y + h);
            VanillaTheme.slot(g, cx, cy, hov);
            drawEntryIcon(g, entry, cx + 1, cy + 1, cycle);
            if (SlotPicks.isFavorite(kind, entry.id())) {
                g.fill(cx + 14, cy + 1, cx + 17, cy + 4, VanillaTheme.WARN);
            }
            if (hov) {
                hovered = entry;
            }
        }
        g.disableScissor();
        return hovered;
    }

    /** Draw a scrollable list (tag rows: cycling icon + name + member count). */
    private Entry drawList(GuiGraphics g, List<Entry> list, int x, int y, int w, int h,
                           int scroll, int mouseX, int mouseY, long cycle, int indexOffset) {
        int rowH = 20;
        Entry hovered = null;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for (int i = 0; i < list.size(); i++) {
            int ry = y + 1 + (indexOffset + i) * rowH - scroll;
            if (ry + rowH < y || ry > y + h) {
                continue;
            }
            Entry entry = list.get(i);
            boolean hov = mouseX >= x && mouseX < x + w
                    && mouseY >= Math.max(ry, y) && mouseY < Math.min(ry + rowH, y + h);
            if (hov) {
                g.fill(x + 1, Math.max(ry, y + 1), x + w - 1, Math.min(ry + rowH, y + h - 1), VanillaTheme.ROW_HOVER);
            }
            drawEntryIcon(g, entry, x + 2, ry + 1, cycle);
            if (SlotPicks.isFavorite(kind, entry.id())) {
                g.fill(x + 15, ry + 1, x + 18, ry + 4, VanillaTheme.WARN);
            }
            String countStr = entry.members().isEmpty()
                    ? "" : Component.translatable("kjsgen.ui.tag_count", entry.members().size()).getString();
            int nameW = w - 26 - (countStr.isEmpty() ? 4 : this.font.width(countStr) + 8);
            String name = this.font.plainSubstrByWidth(entry.label(), nameW);
            g.drawString(this.font, name, x + 22, ry + 6, VanillaTheme.TEXT, true);
            if (!countStr.isEmpty()) {
                g.drawString(this.font, countStr, x + w - 4 - this.font.width(countStr), ry + 6,
                        VanillaTheme.TEXT_DIM, true);
            }
            if (hov) {
                hovered = entry;
            }
        }
        g.disableScissor();
        return hovered;
    }

    private void drawEntryIcon(GuiGraphics g, Entry entry, int x, int y, long cycle) {
        if (entry.chemical() != null) {
            VanillaTheme.drawChemical(g, x, y, entry.chemical());
        } else if (!entry.members().isEmpty()) {
            g.renderItem(entry.members().get((int) (cycle % entry.members().size())), x, y);
        } else {
            g.renderItem(entry.icon(), x, y);
        }
    }

    private void drawEntryTooltip(GuiGraphics g, Entry entry, int mouseX, int mouseY) {
        if (kind == ContentKind.ITEM) {
            g.renderTooltip(this.font, entry.icon(), mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(entry.label()));
        lines.add(Component.literal(entry.id()).withStyle(ChatFormatting.DARK_GRAY));
        if (!entry.members().isEmpty()) {
            lines.add(Component.translatable("kjsgen.ui.tag_count", entry.members().size())
                    .withStyle(ChatFormatting.GRAY));
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (hoverEntry != null && inPickRegions(mouseX, mouseY)) {
            if (button == 1) {
                SlotPicks.toggleFavorite(kind, hoverEntry.id());
            } else {
                select(hoverEntry);
            }
            return true;
        }
        return false;
    }

    private boolean inPickRegions(double mx, double my) {
        return inRect(mx, my, resultsX, resultsY, resultsW, resultsH)
                || inRect(mx, my, recentX, recentY, recentW, recentH)
                || inRect(mx, my, favX, favY, favW, favH);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    //? if >=1.21 {
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
    //?}
    //? if <1.21 {
    /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollY)) {
    *///?}
            return true;
        }
        if (inRect(mouseX, mouseY, resultsX, resultsY, resultsW, resultsH)) {
            resultScroll = clampScroll(resultScroll - (int) (scrollY * 20), matchedIds.size(), resultsW, resultsH);
            boolean tag = kind.isTag();
            ensureWindowCovers(visibleFirstIndex(resultScroll, resultsW, resultsH, tag),
                    visibleLastIndex(resultScroll, resultsW, resultsH, tag));
            return true;
        }
        if (inRect(mouseX, mouseY, favX, favY, favW, favH)) {
            favScroll = clampScroll(favScroll - (int) (scrollY * 20),
                    SlotPicks.favorites(kind).size(), favW, favH);
            return true;
        }
        return false;
    }

    /** Absolute index of the first row/cell that could be visible at this scroll offset. */
    private int visibleFirstIndex(int scroll, int w, int h, boolean tag) {
        int cols = tag ? 1 : Math.max(1, w / 20);
        return (scroll / 20) * cols;
    }

    /** Absolute index one past the last row/cell that could be visible at this scroll offset. */
    private int visibleLastIndex(int scroll, int w, int h, boolean tag) {
        int cols = tag ? 1 : Math.max(1, w / 20);
        int rows = h / 20 + 2;
        return visibleFirstIndex(scroll, w, h, tag) + rows * cols;
    }

    private int clampScroll(int value, int size, int w, int h) {
        int contentH;
        if (kind.isTag()) {
            contentH = size * 20;
        } else {
            int cols = Math.max(1, w / 20);
            contentH = ((size + cols - 1) / cols) * 20;
        }
        int max = Math.max(0, contentH - (h - 2));
        return Math.max(0, Math.min(value, max));
    }

    // ------------------------------------------------------------------ helpers

    private static String itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            return BuiltInRegistries.ITEM.get(rl).getDescription().getString();
        }
        return id;
    }

    private static String fluidName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.FLUID.containsKey(rl)) {
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            // Fluid display name: Forge/NeoForge both hang it off FluidType; Fabric uses the Transfer API.
            //? if neoforge || forge {
            return fluid.getFluidType().getDescription().getString();
            //?}
            //? if fabric {
            /*return net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(
                    net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid)).getString();*/
            //?}
        }
        return id;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<ContentKind> allowedKinds(SlotDefinition slotDef) {
        List<ContentKind> kinds = new ArrayList<>();
        if (slotDef.allowsItem()) {
            kinds.add(ContentKind.ITEM);
            if (slotDef.allowsTag()) {
                kinds.add(ContentKind.ITEM_TAG);
            }
        }
        if (slotDef.allowsFluid()) {
            kinds.add(ContentKind.FLUID);
            if (slotDef.allowsTag()) {
                kinds.add(ContentKind.FLUID_TAG);
            }
        }
        if (slotDef.allowsChemical() && MekanismChemicals.available()) {
            kinds.add(ContentKind.CHEMICAL);
            if (slotDef.allowsTag()) {
                kinds.add(ContentKind.CHEMICAL_TAG);
            }
        }
        return kinds;
    }

    private static ContentKind firstAllowedKind(SlotDefinition slotDef) {
        List<ContentKind> kinds = allowedKinds(slotDef);
        return kinds.isEmpty() ? ContentKind.ITEM : kinds.get(0);
    }

    private static String kindLabel(ContentKind kind) {
        return switch (kind) {
            case ITEM -> "item";
            case ITEM_TAG -> "item tag";
            case FLUID -> "fluid";
            case FLUID_TAG -> "fluid tag";
            case CHEMICAL -> "chemical";
            case CHEMICAL_TAG -> "chemical tag";
            case EMPTY -> "-";
        };
    }
}
