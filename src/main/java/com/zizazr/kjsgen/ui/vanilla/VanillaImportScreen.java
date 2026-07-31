package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.integration.kubejs.JsRecipeParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to import recipes from an existing KubeJS server script on the user's
 * machine. A native file picker (LWJGL {@code tinyfd}) chooses a {@code .js} file;
 * {@link JsRecipeParser} reconstructs every recipe it recognises, each shown with a
 * checkbox so the user can pick which ones to add to the current project.
 *
 * <p>The file picker is modal native code, so it runs on a background thread and
 * marshals its result back onto the client thread via {@link Minecraft#execute}.
 */
public final class VanillaImportScreen extends VanillaDialogScreen {
    private static final int ROW_H = 18;

    private final VanillaEditorScreen editor;

    private String fileName = "";
    private Component status = Component.translatable("kjsgen.ui.import_hint");
    private List<RecipeInstance> detected = new ArrayList<>();
    private boolean[] selected = new boolean[0];
    private int scroll;
    private boolean picking;

    private int listX, listY, listW, listH;
    private Button importButton;

    public VanillaImportScreen(VanillaEditorScreen parent) {
        super(parent, Component.translatable("kjsgen.ui.import_js"));
        this.editor = parent;
    }

    @Override
    protected void init() {
        centerDialog(240, 214);
        int x = dialogX + 10;
        int w = dialogW - 20;

        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.import_choose"), b -> chooseFile())
                .bounds(x, dialogY + 24, w, 16).build());

        listX = x;
        listY = dialogY + 56;
        listW = w;
        listH = dialogH - 56 - 30;

        int by = dialogY + dialogH - 24;
        int bw = (w - 4) / 2;
        importButton = Button.builder(Component.translatable("kjsgen.ui.import_add"), b -> doImport())
                .bounds(x, by, bw, 18).build();
        importButton.active = selectedCount() > 0;
        addRenderableWidget(importButton);
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.close"), b -> onClose())
                .bounds(x + bw + 4, by, w - bw - 4, 18).build());
    }

    // ------------------------------------------------------------------ file picking

    private void chooseFile() {
        if (picking) {
            return;
        }
        picking = true;
        status = Component.translatable("kjsgen.ui.import_choosing");
        Minecraft mc = this.minecraft;
        Thread thread = new Thread(() -> {
            String path = openFileDialog();
            String text = null;
            String error = null;
            if (path != null) {
                try {
                    text = Files.readString(Path.of(path));
                } catch (Exception e) {
                    KjsGen.LOGGER.error("kjsgen: failed to read import file {}", path, e);
                    error = e.getMessage();
                }
            }
            String finalPath = path;
            String finalText = text;
            String finalError = error;
            mc.execute(() -> onFileChosen(finalPath, finalText, finalError));
        }, "kjsgen-import-picker");
        thread.setDaemon(true);
        thread.start();
    }

    /** Opens the native "open file" dialog. Returns the chosen path or {@code null} if cancelled. */
    private static String openFileDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.js"));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                    Component.translatable("kjsgen.ui.import_js").getString(),
                    "", filters, "KubeJS scripts (*.js)", false);
        } catch (Throwable t) {
            KjsGen.LOGGER.error("kjsgen: native file dialog failed", t);
            return null;
        }
    }

    private void onFileChosen(String path, String text, String error) {
        picking = false;
        if (this.minecraft == null || this.minecraft.screen != this) {
            return; // dialog was closed while the picker was open
        }
        if (path == null) {
            status = Component.translatable("kjsgen.ui.import_cancelled");
            return;
        }
        fileName = Path.of(path).getFileName().toString();
        if (error != null || text == null) {
            status = Component.translatable("kjsgen.ui.import_read_failed").withStyle(s -> s.withColor(VanillaTheme.ERROR));
            return;
        }
        JsRecipeParser.Result result = JsRecipeParser.parse(text);
        detected = new ArrayList<>(result.recipes());
        selected = new boolean[detected.size()];
        java.util.Arrays.fill(selected, true);
        scroll = 0;
        status = Component.translatable("kjsgen.ui.import_found", result.recognized(), result.skipped());
        refreshImportButton();
    }

    // ------------------------------------------------------------------ import

    private int selectedCount() {
        int n = 0;
        for (boolean b : selected) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    private void refreshImportButton() {
        if (importButton != null) {
            importButton.active = selectedCount() > 0;
            importButton.setMessage(Component.translatable("kjsgen.ui.import_add_n", selectedCount()));
        }
    }

    private void doImport() {
        List<RecipeInstance> chosen = new ArrayList<>();
        for (int i = 0; i < detected.size(); i++) {
            if (selected[i]) {
                chosen.add(detected.get(i));
            }
        }
        if (!chosen.isEmpty()) {
            editor.importRecipes(chosen);
        }
        onClose();
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = dialogX + 10;
        int w = dialogW - 20;

        // file name + status line above the list
        String head = fileName.isEmpty() ? "" : this.font.plainSubstrByWidth(fileName, w);
        if (!head.isEmpty()) {
            g.drawString(this.font, head, x, dialogY + 44, VanillaTheme.TEXT, true);
        }

        VanillaTheme.section(g, listX, listY, listW, listH);
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        if (detected.isEmpty()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), listW - 10),
                    listX + 5, listY + 6, VanillaTheme.TEXT_DIM, true);
        } else {
            for (int i = 0; i < detected.size(); i++) {
                int ry = listY + i * ROW_H - scroll;
                if (ry + ROW_H < listY || ry > listY + listH) {
                    continue;
                }
                boolean hovered = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= Math.max(ry, listY) && mouseY < Math.min(ry + ROW_H, listY + listH);
                if (hovered) {
                    g.fill(listX + 1, ry, listX + listW - 1, ry + ROW_H, VanillaTheme.ROW_HOVER);
                }
                VanillaTheme.checkbox(g, listX + 4, ry + 5, 8, selected[i], false);

                ItemStack icon = iconFor(detected.get(i));
                if (!icon.isEmpty()) {
                    g.renderItem(icon, listX + 16, ry + 1);
                }
                g.drawString(this.font, this.font.plainSubstrByWidth(labelFor(detected.get(i)), listW - 38),
                        listX + 35, ry + 5, VanillaTheme.TEXT, true);
            }
        }
        g.disableScissor();

        // status under the list (also shows skipped count after a parse)
        if (!detected.isEmpty()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), w),
                    x, listY + listH + 3, VanillaTheme.TEXT_DIM, true);
        }
    }

    private ItemStack iconFor(RecipeInstance recipe) {
        for (var entry : recipe.slots().entrySet()) {
            if (entry.getKey().startsWith("output") && !entry.getValue().isEmpty()) {
                return VanillaTheme.iconStackFor(entry.getValue());
            }
        }
        return recipe.slots().values().stream().filter(c -> !c.isEmpty()).findFirst()
                .map(VanillaTheme::iconStackFor).orElse(ItemStack.EMPTY);
    }

    private String labelFor(RecipeInstance recipe) {
        RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        String typeName = type != null ? VanillaEditorScreen.typeName(type) : recipe.typeId();
        return typeName + ": " + recipe.describe();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!detected.isEmpty() && mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int index = (int) ((mouseY - listY + scroll) / ROW_H);
            if (index >= 0 && index < selected.length) {
                selected[index] = !selected[index];
                refreshImportButton();
            }
            return true;
        }
        return false;
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
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int max = Math.max(0, detected.size() * ROW_H - listH);
            scroll = Math.max(0, Math.min(scroll + (int) (-scrollY * ROW_H), max));
            return true;
        }
        return false;
    }
}
