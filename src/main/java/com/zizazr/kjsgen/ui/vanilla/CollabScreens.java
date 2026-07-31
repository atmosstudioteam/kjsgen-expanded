package com.zizazr.kjsgen.ui.vanilla;

import net.minecraft.client.gui.screens.Screen;

/**
 * Maps the current Minecraft {@link Screen} to a kjsgen "which screen am I on" label (a translation
 * key) for the collaborative presence tooltip, and tells the cursor overlay when a screen is part of
 * the editor context (the editor itself or any of its sub-dialogs).
 */
public final class CollabScreens {
    private CollabScreens() {
    }

    /** Translation key for the screen, or {@code null} when it is not a kjsgen editor screen. */
    public static String labelKey(Screen s) {
        if (s instanceof VanillaEditorScreen) {
            return "kjsgen.screen.editor";
        }
        if (s instanceof VanillaSlotEditScreen) {
            return "kjsgen.screen.slot";
        }
        if (s instanceof VanillaTypePickerScreen) {
            return "kjsgen.screen.type_picker";
        }
        if (s instanceof VanillaProjectsScreen) {
            return "kjsgen.screen.projects";
        }
        if (s instanceof VanillaExportScreen) {
            return "kjsgen.screen.export";
        }
        if (s instanceof VanillaRemovalsScreen) {
            return "kjsgen.screen.removals";
        }
        if (s instanceof VanillaCodePreviewScreen) {
            return "kjsgen.screen.code_preview";
        }
        if (s instanceof VanillaDialogScreen) {
            return "kjsgen.screen.editor"; // any other kjsgen dialog counts as "in the editor"
        }
        return null;
    }

    /** True when {@code s} is the editor or one of its dialogs (so cursors should overlay it). */
    public static boolean isEditorContext(Screen s) {
        return labelKey(s) != null;
    }
}
