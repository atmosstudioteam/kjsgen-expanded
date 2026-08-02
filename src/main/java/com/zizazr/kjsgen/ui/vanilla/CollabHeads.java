package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.integration.net.ClientPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The head-icon presence bar: a row of the other operators' skin faces, each framed in that
 * operator's colour, laid out growing leftwards from a right edge (placed just before the project
 * name field in the editor header). Hovering a head yields a tooltip with the nickname and the
 * screen they are currently on. Kept separate from {@link CollabCursors} on purpose — the heads are
 * a normal part of the editor UI, the cursors are a global top-most overlay.
 */
final class CollabHeads {
    private CollabHeads() {
    }

    static final int CELL = 16;
    private static final int FACE = 12;
    private static final int GAP = 2;

    /**
     * Draw the head row ending at {@code rightEdgeX} (exclusive) on baseline {@code y}. Returns the
     * tooltip for a hovered head, or {@code null}.
     */
    static List<Component> render(GuiGraphics g, int rightEdgeX, int y, int mouseX, int mouseY) {
        List<ClientPresence.Viewer> viewers = ClientPresence.viewers();
        if (viewers.isEmpty()) {
            return null;
        }
        UUID self = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID() : null;
        List<Component> tooltip = null;
        int j = 0;
        for (ClientPresence.Viewer v : viewers) {
            if (v.uuid().equals(self)) {
                continue;
            }
            int cellX = rightEdgeX - CELL - j * (CELL + GAP);
            drawHead(g, v.uuid(), cellX, y, CollabColors.colorFor(v.color()));
            if (mouseX >= cellX && mouseX < cellX + CELL && mouseY >= y && mouseY < y + CELL) {
                tooltip = tooltipFor(v);
            }
            j++;
        }
        return tooltip;
    }

    private static List<Component> tooltipFor(ClientPresence.Viewer v) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(v.name()));
        String screenKey = ClientPresence.screen(v.uuid());
        if (screenKey != null) {
            tip.add(Component.translatable("kjsgen.ui.on_screen",
                            Component.translatable(screenKey))
                    .withStyle(s -> s.withColor(VanillaTheme.TEXT_DIM)));
        }
        return tip;
    }

    private static void drawHead(GuiGraphics g, UUID uuid, int x, int y, int color) {
        g.fill(x, y, x + CELL, y + CELL, color);
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFF202028);
        int fx = x + (CELL - FACE) / 2;
        int fy = y + (CELL - FACE) / 2;
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn != null ? conn.getPlayerInfo(uuid) : null;
        if (info != null) {
            // 1.21 resolves a PlayerSkin; 1.20.1 exposes the skin as a ResourceLocation.
            //? if >=1.21 {
            /*PlayerFaceRenderer.draw(g, info.getSkin(), fx, fy, FACE);
            *///?}
            //? if <1.21 {
            PlayerFaceRenderer.draw(g, info.getSkinLocation(), fx, fy, FACE);
            //?}
        } else {
            g.fill(fx, fy, fx + FACE, fy + FACE, color);
        }
    }
}
