package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.integration.mekanism.MekanismChemicals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * Colour palette and small drawing helpers for the kjsgen editor screens.
 * Everything here uses only {@link GuiGraphics} and Minecraft registry APIs.
 */
final class VanillaTheme {
    private VanillaTheme() {
    }

    // ---- colours (ARGB) --------------------------------------------------
    static final int PANEL_BG = 0xF01B1B22;
    static final int PANEL_BORDER = 0xFF3A3A46;
    static final int SECTION_BG = 0xFF24242C;
    static final int SECTION_BORDER = 0xFF3A3A46;
    static final int SLOT_BG = 0xFF0D0D11;
    static final int SLOT_BORDER = 0xFF4A4A57;
    static final int SLOT_HOVER = 0xFF7C93FF;
    static final int TEXT = 0xFFE7E7EC;
    static final int TEXT_DIM = 0xFF9A9AA8;
    static final int ACCENT = 0xFF5C79FF;
    static final int SELECT_BG = 0x66516CFF;
    static final int ROW_HOVER = 0x22FFFFFF;
    static final int ERROR = 0xFFFF6B6B;
    static final int WARN = 0xFFFFC857;
    static final int OK_GREEN = 0xFF6BD46B;

    // ---- box helpers -----------------------------------------------------

    /** Outer window panel: filled background + 1px border. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    /** Inset section (list / canvas / preview backdrop). */
    static void section(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SECTION_BG);
        g.renderOutline(x, y, w, h, SECTION_BORDER);
    }

    /** A single 18x18 inventory-style slot with optional hover highlight. */
    static void slot(GuiGraphics g, int x, int y, boolean hovered) {
        g.fill(x, y, x + 18, y + 18, SLOT_BG);
        g.renderOutline(x, y, 18, 18, hovered ? SLOT_HOVER : SLOT_BORDER);
    }

    /** A thin horizontal separator line. */
    static void separator(GuiGraphics g, int x1, int x2, int y) {
        g.hLine(x1, x2, y, PANEL_BORDER);
    }

    /** A small themed checkbox box (shared by the import list and boolean toggles). */
    static void checkbox(GuiGraphics g, int x, int y, int size, boolean on, boolean highlight) {
        g.fill(x, y, x + size, y + size, SECTION_BG);
        g.renderOutline(x, y, size, size, on || highlight ? ACCENT : TEXT_DIM);
        if (on) {
            int inset = Math.max(2, size / 4);
            g.fill(x + inset, y + inset, x + size - inset, y + size - inset, ACCENT);
        }
    }

    // ---- content -> renderable icon --------------------------------------

    /** The item stack used to visualise a slot's content (bucket proxy for fluids). */
    static ItemStack iconStackFor(SlotContent content) {
        return switch (content.kind()) {
            case EMPTY -> ItemStack.EMPTY;
            case ITEM -> stackOf(content);
            case ITEM_TAG -> {
                List<ItemStack> stacks = tagStacks(content.id());
                yield stacks.isEmpty() ? new ItemStack(Items.BARRIER) : stacks.get(0);
            }
            case FLUID -> fluidBucket(content.id());
            case FLUID_TAG -> new ItemStack(Items.WATER_BUCKET);
            // chemicals are drawn as atlas sprites, see drawChemical(); no item proxy
            case CHEMICAL, CHEMICAL_TAG -> ItemStack.EMPTY;
        };
    }

    /** Draw a Mekanism chemical (or a chemical tag's first member) into a 16x16 box. */
    static void drawChemical(GuiGraphics g, int x, int y, SlotContent content) {
        var info = content.kind() == ContentKind.CHEMICAL_TAG
                ? MekanismChemicals.tagSample(content.id())
                : MekanismChemicals.byId(content.id());
        drawChemical(g, x, y, info.orElse(null));
    }

    /** Draw a chemical's tinted atlas sprite; barrier icon when it can't be resolved. */
    static void drawChemical(GuiGraphics g, int x, int y, MekanismChemicals.ChemicalInfo info) {
        if (info == null || info.icon() == null) {
            g.renderItem(new ItemStack(Items.BARRIER), x, y);
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(info.icon());
        int tint = info.tint();
        float red = (tint >> 16 & 0xFF) / 255f;
        float green = (tint >> 8 & 0xFF) / 255f;
        float blue = (tint & 0xFF) / 255f;
        g.blit(x, y, 0, 16, 16, sprite, red, green, blue, 1.0f);
    }

    static ItemStack stackOf(SlotContent content) {
        ResourceLocation rl = ResourceLocation.tryParse(content.id());
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl), Math.max(1, content.count()));
    }

    /** A single-count stack for a raw item id, or a barrier when the id is unknown. */
    static ItemStack itemStack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    /** A bucket item proxy for a fluid id (water bucket fallback). */
    static ItemStack fluidBucket(String fluidId) {
        ResourceLocation rl = ResourceLocation.tryParse(fluidId);
        if (rl != null && BuiltInRegistries.FLUID.containsKey(rl)) {
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            ItemStack bucket = new ItemStack(fluid.getBucket());
            if (!bucket.isEmpty()) {
                return bucket;
            }
        }
        return new ItemStack(Items.WATER_BUCKET);
    }

    /** Bucket proxies for every member of a fluid tag (empty list when unknown/empty). */
    static List<ItemStack> fluidTagStacks(String tagId) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, rl);
            var holders = BuiltInRegistries.FLUID.getTag(tagKey);
            if (holders.isPresent()) {
                List<ItemStack> stacks = holders.get().stream()
                        .map(holder -> {
                            ItemStack bucket = new ItemStack(holder.value().getBucket());
                            return bucket.isEmpty() ? new ItemStack(Items.WATER_BUCKET) : bucket;
                        })
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (!stacks.isEmpty()) {
                    return stacks;
                }
            }
        }
        return List.of();
    }

    /** All items of an item tag, or a single barrier when the tag is unknown/empty. */
    static List<ItemStack> tagStacks(String tagId) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, rl);
            var holders = BuiltInRegistries.ITEM.getTag(tagKey);
            if (holders.isPresent()) {
                List<ItemStack> stacks = holders.get().stream()
                        .map(holder -> new ItemStack(holder.value()))
                        .toList();
                if (!stacks.isEmpty()) {
                    return stacks;
                }
            }
        }
        return List.of(new ItemStack(Items.BARRIER));
    }

}
