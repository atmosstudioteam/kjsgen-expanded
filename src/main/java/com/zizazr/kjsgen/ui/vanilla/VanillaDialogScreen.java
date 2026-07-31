package com.zizazr.kjsgen.ui.vanilla;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared scaffolding for the small modal dialogs of the vanilla editor: it draws
 * the parent editor as a static, dimmed backdrop and centres a titled panel.
 * Closing returns to the parent (which re-reads the shared model on {@code init}).
 */
abstract class VanillaDialogScreen extends Screen {
    protected final Screen parent;
    protected int dialogX, dialogY, dialogW, dialogH;

    protected VanillaDialogScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    /** Sub-classes set {@link #dialogW}/{@link #dialogH}; this centres the panel. */
    protected void centerDialog(int w, int h) {
        this.dialogW = Math.min(w, this.width - 20);
        this.dialogH = Math.min(h, this.height - 20);
        this.dialogX = (this.width - dialogW) / 2;
        this.dialogY = (this.height - dialogH) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // A standard blurred/dimmed backdrop. We deliberately do NOT re-render the
        // parent editor here: Minecraft batches text and item rendering and flushes
        // it at the end of the frame, so anything the parent draws would end up
        // painted on top of this dialog.
        //? if >=1.21 {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        //?}
        //? if <1.21 {
        /*this.renderBackground(g);*/
        //?}

        VanillaTheme.panel(g, dialogX, dialogY, dialogW, dialogH);
        g.drawString(this.font, this.title, dialogX + 8, dialogY + 8, VanillaTheme.TEXT, true);
        VanillaTheme.separator(g, dialogX + 6, dialogX + dialogW - 6, dialogY + 20);

        renderContent(g, mouseX, mouseY, partialTick);

        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    /** Draw dialog-specific content between the title bar and the widgets. */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
