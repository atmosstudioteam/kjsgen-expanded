package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RemovalRule;
import com.zizazr.kjsgen.integration.kubejs.ScriptAssembler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vanilla dialog showing the full generated KubeJS code, grouped per target
 * file, before it is written to disk.
 */
public final class VanillaCodePreviewScreen extends VanillaDialogScreen {
    private record Line(String text, boolean header) {
    }

    private final RecipeProject project;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int codeX, codeY, codeW, codeH;

    public VanillaCodePreviewScreen(Screen parent, RecipeProject project) {
        super(parent, Component.translatable("kjsgen.ui.preview_code"));
        this.project = project;
    }

    @Override
    protected void init() {
        centerDialog(340, 240);
        codeX = dialogX + 8;
        codeY = dialogY + 24;
        codeW = dialogW - 16;
        codeH = dialogH - 24 - 28;

        lines.clear();
        Map<String, List<RecipeInstance>> byFile = project.recipesByTargetFile();
        List<RemovalRule> removals = project.removals().stream().filter(r -> !r.isEmpty()).toList();
        if (!removals.isEmpty()) {
            byFile.computeIfAbsent(project.defaultTargetFile(), f -> List.of());
        }
        for (Map.Entry<String, List<RecipeInstance>> e : byFile.entrySet()) {
            lines.add(new Line("// " + e.getKey() + ".js", true));
            String code = ScriptAssembler.assemble(project, e.getValue(),
                    e.getKey().equals(project.defaultTargetFile()) ? removals : List.of());
            for (String line : code.split("\n", -1)) {
                lines.add(new Line(line, false));
            }
            lines.add(new Line("", false));
        }
        if (lines.isEmpty()) {
            lines.add(new Line("// nothing to export", true));
        }

        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.close"), b -> onClose())
                .bounds(dialogX + dialogW - 68, dialogY + dialogH - 24, 60, 18).build());
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaTheme.section(g, codeX, codeY, codeW, codeH);
        float scale = 0.85f;
        int lineH = 9;
        g.enableScissor(codeX + 1, codeY + 1, codeX + codeW - 1, codeY + codeH - 1);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        int tx = (int) ((codeX + 4) / scale);
        int startY = codeY + 3 - scroll;
        for (int i = 0; i < lines.size(); i++) {
            int ly = startY + i * (int) (lineH * scale);
            if (ly + lineH < codeY || ly > codeY + codeH) {
                continue;
            }
            Line line = lines.get(i);
            g.drawString(this.font, line.text(), tx, (int) (ly / scale),
                    line.header() ? VanillaTheme.OK_GREEN : VanillaTheme.TEXT, true);
        }
        g.pose().popPose();
        g.disableScissor();

        int contentH = lines.size() * (int) (lineH * scale);
        if (contentH > codeH) {
            int barH = Math.max(12, codeH * codeH / contentH);
            int maxScroll = contentH - codeH;
            int barY = codeY + (int) ((codeH - barH) * (scroll / (float) maxScroll));
            g.fill(codeX + codeW - 3, codeY, codeX + codeW - 1, codeY + codeH, 0x40000000);
            g.fill(codeX + codeW - 3, barY, codeX + codeW - 1, barY + barH, VanillaTheme.ACCENT);
        }
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
        int lineH = (int) (9 * 0.85f);
        int max = Math.max(0, lines.size() * lineH - codeH);
        scroll = Math.max(0, Math.min(scroll + (int) (-scrollY * 12), max));
        return true;
    }
}
