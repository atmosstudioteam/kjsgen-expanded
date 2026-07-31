package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.RecipeValidator;
import com.zizazr.kjsgen.integration.kubejs.KubeJsExporter;
import com.zizazr.kjsgen.integration.net.ClientEditSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Vanilla dialog showing export options and a per-file summary, then writing the
 * KubeJS scripts through {@link KubeJsExporter}.
 */
public final class VanillaExportScreen extends VanillaDialogScreen {
    private final VanillaEditorScreen editor;
    private final RecipeProject project;

    public VanillaExportScreen(VanillaEditorScreen parent) {
        super(parent, Component.translatable("kjsgen.ui.export_title"));
        this.editor = parent;
        this.project = parent.project();
    }

    @Override
    protected void init() {
        centerDialog(260, 228);
        int x = dialogX + 10;
        int w = dialogW - 20;

        int fieldY = dialogY + dialogH - 92;
        EditBox fileField = new EditBox(this.font, x + 70, fieldY, w - 70, 16,
                Component.translatable("kjsgen.ui.default_file"));
        fileField.setValue(project.defaultTargetFile());
        fileField.setResponder(v -> {
            project.setDefaultTargetFile(v);
            editor.scheduleMetaPush();
        });
        addRenderableWidget(fileField);

        addRenderableWidget(new VanillaCheckbox(x, fieldY + 20, w, 16,
                Component.translatable("kjsgen.ui.export_comments"), true, project.exportComments(),
                v -> {
                    project.setExportComments(v);
                    editor.scheduleMetaPush();
                }));

        addRenderableWidget(new VanillaCheckbox(x, fieldY + 38, w, 16,
                Component.translatable("kjsgen.ui.reload_on_export"), true, project.reloadOnExport(),
                v -> {
                    project.setReloadOnExport(v);
                    editor.scheduleMetaPush();
                }));

        int by = dialogY + dialogH - 24;
        int bw = (w - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.preview_code"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new VanillaCodePreviewScreen(this, project));
            }
        }).bounds(x, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.export"), b -> doExport())
                .bounds(x + bw + 4, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.cancel"), b -> onClose())
                .bounds(x + 2 * (bw + 4), by, w - 2 * (bw + 4), 18).build());
    }

    private void doExport() {
        ((VanillaEditorScreen) parent).saveProject(false);
        if (ClientEditSession.isRemote()) {
            // Export runs on the server (writes to the server's kubejs dir, runs /reload there).
            // The per-file summary / warnings come back as a chat message via ClientEditSession.
            ClientEditSession.requestExport();
            onClose();
            return;
        }
        try {
            KubeJsExporter.ExportResult result = KubeJsExporter.exportProject(project);
            StringBuilder info = new StringBuilder(Component.translatable("kjsgen.ui.export_done").getString());
            for (KubeJsExporter.FileResult file : result.files()) {
                info.append(" ").append(file.fileName()).append(".js(").append(file.recipeCount()).append(")");
            }
            message(info.toString());
            if (project.reloadOnExport()) {
                VanillaEditorScreen.sendReloadCommand();
            }
        } catch (IOException ex) {
            KjsGen.LOGGER.error("kjsgen export failed", ex);
            message(Component.translatable("kjsgen.error.export_failed").getString() + ": " + ex.getMessage());
        }
        onClose();
    }

    private void message(String text) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal(text), false);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = dialogX + 10;
        int w = dialogW - 20;
        int y = dialogY + 26;

        Map<String, List<RecipeInstance>> byFile = project.recipesByTargetFile();
        long removalCount = project.removals().stream().filter(r -> !r.isEmpty()).count();
        boolean hasRemovals = removalCount > 0;
        if (byFile.isEmpty() && !hasRemovals) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.export_empty"), x, y, VanillaTheme.ERROR, true);
            y += 12;
        } else {
            if (hasRemovals) {
                g.drawString(this.font, Component.translatable("kjsgen.ui.export_removals", removalCount),
                        x, y, VanillaTheme.TEXT, true);
                y += 11;
            }
            for (Map.Entry<String, List<RecipeInstance>> e : byFile.entrySet()) {
                String line = "server_scripts/" + KubeJsExporter.sanitizeFileName(e.getKey())
                        + ".js  —  " + e.getValue().size();
                g.drawString(this.font, this.font.plainSubstrByWidth(line, w), x, y, VanillaTheme.TEXT, true);
                y += 11;
                if (y > dialogY + dialogH - 108) {
                    break;
                }
            }
        }

        // warnings
        int wy = dialogY + dialogH - 110;
        if (!KjsGen.isKubeJsLoaded()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(
                            Component.translatable("kjsgen.export.warn_no_kubejs").getString(), w),
                    x, wy, VanillaTheme.WARN, true);
        }
        long invalid = project.recipes().stream()
                .filter(recipe -> RecipeTypeRegistry.get(recipe.typeId())
                        .map(type -> RecipeValidator.hasErrors(RecipeValidator.validate(recipe, type)))
                        .orElse(true))
                .count();
        if (invalid > 0) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.export_invalid", invalid),
                    x, wy + 10, VanillaTheme.ERROR, true);
        }

        // labels for the option widgets
        g.drawString(this.font, Component.translatable("kjsgen.ui.default_file"),
                x, dialogY + dialogH - 88, VanillaTheme.TEXT_DIM, true);
    }
}
