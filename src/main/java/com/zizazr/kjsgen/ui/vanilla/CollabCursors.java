package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.integration.net.ClientPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Draws the other operators' live cursors. This is rendered from a global
 * {@code ScreenEvent.Render.Post} hook (see {@code KjsGenClient}) so the cursors float ON TOP of
 * everything — the editor's own widgets and tooltips, and any sub-dialog that is open.
 *
 * <p>Cursor positions are panel-relative; the editor publishes its panel origin via
 * {@link #setPanel} every frame, and the overlay adds it back so cursors line up on the same
 * widget regardless of each client's window size.
 */
public final class CollabCursors {
    private CollabCursors() {
    }

    /** Native sprite size (all cursor pngs are 32x32) and the size we draw them at. */
    private static final int TEX = 32;
    private static final int CURSOR_SIZE = 18;

    /** Editor panel origin, published by the editor so the overlay can place panel-relative cursors. */
    private static int panelX;
    private static int panelY;

    public static void setPanel(int x, int y) {
        panelX = x;
        panelY = y;
    }

    /** Cursor sprite + hot-spot (the pixel that points at the reported coordinate). */
    private record CursorSprite(ResourceLocation texture, int hotX, int hotY) {
    }

    private static final Map<String, CursorSprite> SPRITES = Map.of(
            "default", sprite("default", 0, 0),
            "pointing_hand", sprite("pointing_hand", 7, 0),
            "ibeam", sprite("ibeam", 6, 8),
            "grabbing", sprite("grabbing", 7, 0),
            "crosshair", sprite("crosshair", 10, 10),
            "not_allowed", sprite("not_allowed", 7, 7));

    private static CursorSprite sprite(String name, int hotX, int hotY) {
        return new CursorSprite(
                KjsGen.rl("textures/cursor/" + name + ".png"),
                hotX, hotY);
    }

    /**
     * Draw the remote operators' cursors on top of the current frame. Only fires when the local
     * player is on the main editor screen and only for operators who are on that same screen with
     * the same recipe selected — so a cursor never floats over an unrelated screen or a recipe the
     * other client isn't actually looking at (their recipe is instead highlighted in the list).
     */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        // "Screens must match": only overlay cursors while we ourselves are on the main editor.
        if (!(mc.screen instanceof VanillaEditorScreen editor)) {
            return;
        }
        var viewers = ClientPresence.viewers();
        if (viewers.isEmpty()) {
            return;
        }
        UUID self = mc.player != null ? mc.player.getUUID() : null;
        String localUid = editor.selectedRecipeUid();
        // Paint everything the editor batched this frame (widgets, items, text) BEFORE the cursors,
        // otherwise the still-pending batch flushes on top of them and hides the cursors.
        g.flush();
        boolean drewAny = false;
        for (ClientPresence.Viewer v : viewers) {
            if (v.uuid().equals(self)) {
                continue;
            }
            // Same screen (the editor) and same recipe, or we don't draw their cursor here.
            if (!"kjsgen.screen.editor".equals(ClientPresence.screen(v.uuid()))
                    || !Objects.equals(localUid, ClientPresence.recipeUid(v.uuid()))) {
                continue;
            }
            ClientPresence.Cursor c = ClientPresence.cursor(v.uuid());
            if (c == null || !c.visible) {
                continue;
            }
            // An item they're carrying is drawn under the cursor sprite (same offset the local editor
            // uses in render()), so it reads as "held in hand" on this client too.
            if (c.held != null && !c.held.isEmpty()) {
                editor.drawSlotContent(g, panelX + c.x - 9, panelY + c.y - 9, c.held);
            }
            drawCursor(g, panelX + c.x, panelY + c.y, c.state, CollabColors.colorFor(v.color()));
            drewAny = true;
        }
        if (drewAny) {
            g.flush(); // and land the cursors immediately, above the freshly-painted batch
        }
    }

    private static void drawCursor(GuiGraphics g, int px, int py, String state, int color) {
        CursorSprite s = SPRITES.getOrDefault(state, SPRITES.get("default"));
        int drawX = px - s.hotX() * CURSOR_SIZE / TEX;
        int drawY = py - s.hotY() * CURSOR_SIZE / TEX;
        float r = (color >> 16 & 0xFF) / 255f;
        float gr = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 500.0F);

        g.setColor(r, gr, b, 1.0f);
        g.blit(s.texture(), drawX, drawY, CURSOR_SIZE, CURSOR_SIZE, 0f, 0f, TEX, TEX, TEX, TEX);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        g.pose().popPose();
    }
}
