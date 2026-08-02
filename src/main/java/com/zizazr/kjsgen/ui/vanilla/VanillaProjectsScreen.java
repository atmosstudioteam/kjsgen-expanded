package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.integration.net.ClientEditSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Vanilla dialog to open, create or delete saved projects.
 */
public final class VanillaProjectsScreen extends VanillaDialogScreen {
    private static final int ROW_H = 18;

    private List<String> names = List.of();
    private int scroll;
    private int listX, listY, listW, listH;
    private EditBox nameField;

    public VanillaProjectsScreen(VanillaEditorScreen parent) {
        super(parent, Component.translatable("kjsgen.ui.projects"));
    }

    @Override
    protected void init() {
        centerDialog(220, 210);
        int x = dialogX + 10;
        int w = dialogW - 20;

        listX = x;
        listY = dialogY + 26;
        listW = w;
        listH = dialogH - 26 - 52;

        if (ClientEditSession.isRemote()) {
            names = ClientEditSession.projectNames();
            ClientEditSession.requestList(); // refreshed async via setNames(...)
        } else {
            names = ProjectManager.listProjects();
        }

        // new project row
        int ny = listY + listH + 6;
        nameField = new EditBox(this.font, x, ny, w - 62, 16, Component.translatable("kjsgen.ui.new_project"));
        nameField.setHint(Component.literal("my_project"));
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> createProject())
                .bounds(x + w - 58, ny, 58, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.close"), b -> onClose())
                .bounds(x + w - 60, dialogY + dialogH - 24, 60, 18).build());
    }

    private void createProject() {
        String name = nameField.getValue();
        if (name != null && !name.isBlank()) {
            if (ClientEditSession.isRemote()) {
                // Server creates it and replies with a snapshot that refreshes the editor.
                ClientEditSession.createProject(name.trim());
            } else {
                ((VanillaEditorScreen) parent).setProject(new RecipeProject(name.trim()));
            }
            onClose();
        }
    }

    /** Called from {@link ClientEditSession} when the server sends an updated project list. */
    public void setNames(List<String> names) {
        this.names = names;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaTheme.section(g, listX, listY, listW, listH);
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int i = 0; i < names.size(); i++) {
            int ry = listY + i * ROW_H - scroll;
            if (ry + ROW_H < listY || ry > listY + listH) {
                continue;
            }
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= Math.max(ry, listY) && mouseY < Math.min(ry + ROW_H, listY + listH);
            if (hovered) {
                g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, VanillaTheme.ROW_HOVER);
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(names.get(i), listW - 22),
                    listX + 4, ry + 5, VanillaTheme.TEXT, true);
            // delete "x" on the right
            int dx = listX + listW - 14;
            int c = hovered ? VanillaTheme.ERROR : VanillaTheme.TEXT_DIM;
            for (int k = 0; k < 7; k++) {
                g.fill(dx + k, ry + 5 + k, dx + k + 1, ry + 6 + k, c);
                g.fill(dx + 6 - k, ry + 5 + k, dx + 7 - k, ry + 6 + k, c);
            }
        }
        g.disableScissor();
        if (names.isEmpty()) {
            g.drawString(this.font, "(no saved projects)", listX + 6, listY + 6, VanillaTheme.TEXT_DIM, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int index = (int) ((mouseY - listY + scroll) / ROW_H);
            if (index >= 0 && index < names.size()) {
                String name = names.get(index);
                if (mouseX >= listX + listW - 16) {
                    ClientEditSession.deleteProject(name);
                    if (!ClientEditSession.isRemote()) {
                        names = ClientEditSession.projectNames();
                    }
                } else if (ClientEditSession.isRemote()) {
                    // Server sends a snapshot of the opened project, refreshing the editor.
                    ClientEditSession.requestOpen(name);
                    onClose();
                } else {
                    ProjectManager.load(name).ifPresent(project -> {
                        ((VanillaEditorScreen) parent).setProject(project);
                        onClose();
                    });
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
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int max = Math.max(0, names.size() * ROW_H - listH);
            scroll = Math.max(0, Math.min(scroll + (int) (-scrollY * ROW_H), max));
            return true;
        }
        return false;
    }
}
