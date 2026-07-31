package com.zizazr.kjsgen.ui.vanilla;

import com.google.gson.JsonObject;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.LayoutDecoration;
import com.zizazr.kjsgen.core.ParameterDefinition;
import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.RecipeValidator;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.UndoStack;
import com.zizazr.kjsgen.core.UndoStack.UndoAction;
import com.zizazr.kjsgen.integration.kubejs.ScriptAssembler;
import com.zizazr.kjsgen.integration.net.ClientEditSession;
import com.zizazr.kjsgen.integration.net.ClientPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The kjsgen recipe editor: a {@link Screen}-based UI that renders everything
 * with {@link GuiGraphics} and stock Minecraft widgets.
 *
 * <p>Layout: a header bar (title + project name + Save/Projects/Export), then
 * three columns — recipe list, JEI-style canvas, and parameters + code preview.
 */
public class VanillaEditorScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int PARAM_ROW_H = 18;

    private static final long PUSH_DELAY_MS = 350;

    private RecipeProject project = ClientEditSession.project();
    private String selectedUid;

    // ---- derived caches (rebuilt when the model changes) -----------------
    private List<String> previewLines = List.of("");
    private List<RecipeValidator.Issue> issues = new ArrayList<>();
    private boolean derivedDirty = true;

    // ---- multiplayer sync (debounced pushes; see markDirty/tick) ---------
    private String recipePushUid;
    private long recipePushDueAt;
    private boolean metaPushDue;
    private long metaPushDueAt;
    /** Set while init()/buildParamWidgets programmatically fills widgets, so EditBox.setValue
     *  responders don't schedule spurious network pushes (which would ping-pong between clients). */
    private boolean suppressPush;

    // ---- local undo/redo (this client only; see the "Undo/Redo" design note) --------------------
    // Piggy-backs on the same debounce as the network push: a burst of edits inside one PUSH_DELAY_MS
    // window collapses into a single undo entry, just as it collapses into a single upstream push.
    private final UndoStack undoStack = new UndoStack();
    /** Settled ("clean") snapshot of the selected recipe, refreshed on init and after each flush. */
    private JsonObject recipeUndoBaseline;
    private String recipeUndoBaselineUid;
    /** The "before" snapshot frozen when the current recipe-edit window opened, finalized on flush. */
    private JsonObject pendingUndoBefore;
    private String pendingUndoUid;
    /** Settled snapshot of the four project meta fields, mirrored like {@link #recipeUndoBaseline}. */
    private String metaBaselineName, metaBaselineTargetFile;
    private boolean metaBaselineComments, metaBaselineReload;
    /** The "before" of the current meta-edit window (valid only while {@link #metaPushDue}). */
    private boolean pendingMetaValid;
    private String pendingMetaName, pendingMetaTargetFile;
    private boolean pendingMetaComments, pendingMetaReload;

    // ---- live cursor sharing (see tick(): our mouse is broadcast to the other viewers) ----
    private int localMouseX, localMouseY;
    private int lastSentX = Integer.MIN_VALUE;
    private int lastSentY = Integer.MIN_VALUE;
    private String lastSentState = "";
    private SlotContent lastSentHeld = SlotContent.EMPTY;

    // ---- scroll state ----------------------------------------------------
    private int listScroll;
    private int paramScroll;
    private int previewScroll;

    // ---- geometry (computed in init) -------------------------------------
    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listW, listViewH;
    private int canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH;
    private int canvasOriginX, canvasOriginY;
    private int paramX, paramY, paramW, paramViewH;
    private int previewX, previewY, previewW, previewH;

    // ---- widgets ---------------------------------------------------------
    private EditBox projectNameField;
    private final List<AbstractWidget> paramWidgets = new ArrayList<>();
    private final List<Component> paramLabels = new ArrayList<>();

    // ---- transient per-frame ---------------------------------------------
    private List<Component> hoverTooltip;

    // ---- carried item ("in hand") ----------------------------------------
    // Picked up from a slot (left-click) and dropped into another; middle-click stamps a copy
    // into the slot under the cursor without emptying the hand. See interactSlot().
    private SlotContent heldItem = SlotContent.EMPTY;
    /** Set by interactSlot when a click lands on a slot, so mouseClicked knows not to drop the hand. */
    private boolean slotClickedThisEvent;

    // ---- special editors (mechanical crafting grid / sequenced assembly) --
    private final VanillaSequencePanel sequencePanel = new VanillaSequencePanel();
    private int gridOriginX, gridOriginY, gridCols, gridRows;
    private int gridOutputX, gridOutputY;

    public VanillaEditorScreen() {
        super(Component.translatable("kjsgen.title"));
        ClientEditSession.requestOpen(project.name());
    }

    /** Opens with a specific recipe pre-selected (e.g. one just captured from JEI). */
    public VanillaEditorScreen(String selectedUid) {
        super(Component.translatable("kjsgen.title"));
        this.selectedUid = selectedUid;
        ClientEditSession.requestOpen(project.name());
    }

    @Override
    protected void init() {
        suppressPush = true;
        if (selectedUid == null && !project.recipes().isEmpty()) {
            selectedUid = project.recipes().get(0).uid();
        }
        computeGeometry();

        // ----- header: project name + action buttons (laid out right to left)
        int hy = panelY + 6;
        int hh = 16;
        int rightEdge = panelX + panelW - 8;

        Button reload = Button.builder(Component.translatable("kjsgen.ui.reload"), b -> sendReloadCommand())
                .bounds(0, hy, btnWidth("kjsgen.ui.reload"), hh).build();
        Button export = Button.builder(Component.translatable("kjsgen.ui.export"), b -> openExport())
                .bounds(0, hy, btnWidth("kjsgen.ui.export"), hh).build();
        Button projects = Button.builder(Component.translatable("kjsgen.ui.projects"), b -> openProjects())
                .bounds(0, hy, btnWidth("kjsgen.ui.projects"), hh).build();
        Button importJs = Button.builder(Component.translatable("kjsgen.ui.import_js"), b -> openImport())
                .bounds(0, hy, btnWidth("kjsgen.ui.import_js"), hh).build();
        Button save = Button.builder(Component.translatable("kjsgen.ui.save"), b -> saveProject(true))
                .bounds(0, hy, btnWidth("kjsgen.ui.save"), hh).build();

        reload.setX(rightEdge - reload.getWidth());
        export.setX(reload.getX() - 4 - export.getWidth());
        projects.setX(export.getX() - 4 - projects.getWidth());
        importJs.setX(projects.getX() - 4 - importJs.getWidth());
        save.setX(importJs.getX() - 4 - save.getWidth());

        int nameW = 96;
        projectNameField = new EditBox(this.font, save.getX() - 6 - nameW, hy + 1, nameW, 14,
                Component.translatable("kjsgen.ui.project_name"));
        projectNameField.setMaxLength(64);
        projectNameField.setValue(project.name());
        projectNameField.setResponder(name -> {
            if (!name.isBlank()) {
                project.setName(name.trim());
                scheduleMetaPush();
            }
        });

        addRenderableWidget(projectNameField);
        addRenderableWidget(save);
        addRenderableWidget(importJs);
        addRenderableWidget(projects);
        addRenderableWidget(export);
        addRenderableWidget(reload);

        // ----- left: add recipe button (pinned to the bottom of the list column)
        Button addRecipe = Button.builder(Component.translatable("kjsgen.ui.add_recipe"), b -> openTypePicker())
                .bounds(listX, listY + listViewH + 4, listW, 16).build();
        addRenderableWidget(addRecipe);

        // ----- right: parameter widgets
        buildParamWidgets();
        layoutParamWidgets();

        // Refresh the undo baselines to the current settled state (unless a debounced edit is still
        // pending — its "before" is already frozen and finalized on flush).
        if (recipePushUid == null) {
            captureRecipeBaseline();
        }
        if (!metaPushDue) {
            captureMetaBaseline();
        }

        derivedDirty = true;
        suppressPush = false;
    }

    private void computeGeometry() {
        panelW = Math.min(this.width - 16, 560);
        panelH = Math.min(this.height - 16, 320);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int pad = 8;
        int innerX = panelX + pad;
        int innerW = panelW - 2 * pad;
        int headerBottom = panelY + 6 + 16 + 6;
        int contentY = headerBottom;
        int contentBottom = panelY + panelH - pad;
        int contentH = contentBottom - contentY;

        int gap = 6;
        int leftW = 118;
        int rightW = 156;

        // left column (recipe list + add button at the bottom)
        listX = innerX;
        listY = contentY + 12;
        listW = leftW;
        listViewH = contentBottom - listY - 20; // leave room for the add button

        // center column (type header + canvas + validation)
        canvasBoxX = innerX + leftW + gap;
        canvasBoxW = innerW - leftW - rightW - 2 * gap;
        canvasBoxY = contentY + 12;
        canvasBoxH = contentH - 12 - 30; // leave room for validation lines

        // right column (parameters top, preview bottom)
        paramX = innerX + innerW - rightW;
        paramY = contentY + 12;
        paramW = rightW;
        int rightSplit = contentY + (int) (contentH * 0.55f);
        paramViewH = rightSplit - paramY;
        previewX = paramX;
        previewY = rightSplit + 12;
        previewW = rightW;
        previewH = contentBottom - previewY;
    }

    private int btnWidth(String key) {
        return this.font.width(Component.translatable(key)) + 12;
    }

    // ------------------------------------------------------------------ params

    private void buildParamWidgets() {
        paramWidgets.clear();
        paramLabels.clear();
        RecipeInstance recipe = selectedRecipe().orElse(null);
        if (recipe == null) {
            return;
        }
        RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        if (type == null) {
            return;
        }

        int ctrlW = paramW - 60;
        addTextParam("kjsgen.ui.recipe_id", recipe.recipeId(), "namespace:path", ctrlW, null, recipe::setRecipeId);
        addTextParam("kjsgen.ui.group", recipe.group(), "", ctrlW, null, recipe::setGroup);
        addTextParam("kjsgen.ui.comment", recipe.comment(), "", ctrlW, null, recipe::setComment);
        addTextParam("kjsgen.ui.target_file", recipe.targetFile(), project.defaultTargetFile(), ctrlW, null,
                recipe::setTargetFile);
        addTextParam("kjsgen.ui.if_mod_loaded", recipe.conditionModLoaded(), "", ctrlW, null,
                recipe::setConditionModLoaded);
        CycleButton<Boolean> replaceToggle = CycleButton.onOffBuilder(recipe.replaceRecipe())
                .create(0, 0, ctrlW, 14, Component.translatable("kjsgen.ui.replace_recipe"),
                        (btn, v) -> {
                            recipe.setReplaceRecipe(v);
                            markDirty();
                        });
        addParam(Component.translatable("kjsgen.ui.replace_recipe"), replaceToggle);

        for (ParameterDefinition param : type.parameters()) {
            if (param.key().startsWith("__")) {
                continue; // hidden codegen internals
            }
            String value = recipe.param(type, param.key());
            Component label = paramLabel(param.key());
            switch (param.type()) {
                case BOOL -> {
                    boolean cur = Boolean.parseBoolean(value.isEmpty() ? param.defaultValue() : value);
                    CycleButton<Boolean> toggle = CycleButton.<Boolean>builder(
                                    b -> Component.literal(b ? "true" : "false"))
                            .withValues(Boolean.TRUE, Boolean.FALSE)
                            .displayOnlyValue()
                            .withInitialValue(cur)
                            .create(0, 0, ctrlW, 14, Component.empty(),
                                    (btn, v) -> {
                                        recipe.setParam(param.key(), Boolean.toString(v));
                                        markDirty();
                                    });
                    addParam(label, toggle);
                }
                case ENUM -> {
                    List<String> options = param.options().isEmpty() ? List.of(value) : param.options();
                    String cur = value.isEmpty() ? param.defaultValue() : value;
                    if (!options.contains(cur) && !options.isEmpty()) {
                        cur = options.get(0);
                    }
                    CycleButton<String> selector = CycleButton.<String>builder(Component::literal)
                            .withValues(options)
                            .displayOnlyValue()
                            .withInitialValue(cur)
                            .create(0, 0, ctrlW, 14, Component.empty(),
                                    (btn, v) -> {
                                        recipe.setParam(param.key(), v);
                                        markDirty();
                                    });
                    addParam(label, selector);
                }
                case INT -> addTextParam(label, value, param.defaultValue(), ctrlW, "\\d*",
                        v -> recipe.setParam(param.key(), v));
                case FLOAT -> addTextParam(label, value, param.defaultValue(), ctrlW, "\\d*\\.?\\d*",
                        v -> recipe.setParam(param.key(), v));
                default -> addTextParam(label, value, param.defaultValue(), ctrlW, null,
                        v -> recipe.setParam(param.key(), v));
            }
        }
    }

    private void addTextParam(String labelKey, String value, String hint, int width, String filter,
                              java.util.function.Consumer<String> setter) {
        addTextParam(Component.translatable(labelKey), value, hint, width, filter, setter);
    }

    private void addTextParam(Component label, String value, String hint, int width, String filter,
                              java.util.function.Consumer<String> setter) {
        EditBox box = new EditBox(this.font, 0, 0, width, 14, label);
        box.setMaxLength(256);
        box.setValue(value);
        if (hint != null && !hint.isEmpty()) {
            box.setHint(Component.literal(hint));
        }
        if (filter != null) {
            box.setFilter(s -> s.matches(filter));
        }
        box.setResponder(v -> {
            setter.accept(v);
            markDirty();
        });
        addParam(label, box);
    }

    private void addParam(Component label, AbstractWidget widget) {
        paramLabels.add(label);
        paramWidgets.add(widget);
        addRenderableWidget(widget);
    }

    /** Positions/visibility of parameter widgets according to the scroll offset. */
    private void layoutParamWidgets() {
        int maxScroll = Math.max(0, paramWidgets.size() * PARAM_ROW_H - paramViewH);
        paramScroll = Math.max(0, Math.min(paramScroll, maxScroll));
        for (int i = 0; i < paramWidgets.size(); i++) {
            AbstractWidget w = paramWidgets.get(i);
            int rowTop = paramY + i * PARAM_ROW_H - paramScroll;
            boolean visible = rowTop >= paramY && rowTop + PARAM_ROW_H <= paramY + paramViewH;
            w.visible = visible;
            w.active = visible;
            w.setX(paramX + 58);
            w.setY(rowTop + 1);
        }
    }

    // ------------------------------------------------------------------ actions

    Optional<RecipeInstance> selectedRecipe() {
        return selectedUid == null ? Optional.empty() : project.recipeByUid(selectedUid);
    }

    /** The uid of the recipe currently selected here (used by the collab cursor overlay). */
    String selectedRecipeUid() {
        return selectedUid;
    }

    RecipeProject project() {
        return project;
    }

    void markDirty() {
        derivedDirty = true;
        // A user edit to the selected recipe: schedule a debounced upsert to the server.
        if (!suppressPush && selectedUid != null) {
            if (recipePushUid == null) {
                // Window opening: freeze the pre-edit snapshot so the whole burst collapses into one
                // undo entry. The edit already mutated the recipe, so use the settled baseline rather
                // than the (now-dirty) current state; it falls back to the live recipe only if the
                // baseline is for a different recipe (rare cross-recipe pending edit).
                pendingUndoBefore = recipeUndoBaseline != null && selectedUid.equals(recipeUndoBaselineUid)
                        ? recipeUndoBaseline
                        : selectedRecipe().map(RecipeInstance::toJson).orElse(null);
                pendingUndoUid = selectedUid;
            }
            recipePushUid = selectedUid;
            recipePushDueAt = System.currentTimeMillis() + PUSH_DELAY_MS;
        }
    }

    /** Schedule a debounced push of the project's meta fields (name + export options). */
    void scheduleMetaPush() {
        if (!suppressPush) {
            if (!metaPushDue) {
                // Window opening: freeze the pre-edit meta baseline (see markDirty()).
                pendingMetaName = metaBaselineName;
                pendingMetaTargetFile = metaBaselineTargetFile;
                pendingMetaComments = metaBaselineComments;
                pendingMetaReload = metaBaselineReload;
                pendingMetaValid = true;
            }
            metaPushDue = true;
            metaPushDueAt = System.currentTimeMillis() + PUSH_DELAY_MS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (recipePushUid != null && now >= recipePushDueAt) {
            flushRecipePush();
        }
        if (metaPushDue && now >= metaPushDueAt) {
            flushMetaPush();
        }
        // Tell the others which recipe we have open (so they can locate/highlight it, and only
        // show our cursor when they're on the same one). tickScreenReport() sends it on change.
        ClientEditSession.setLocalRecipe(selectedUid);
        // Broadcast our cursor to the other operators (panel-relative, only when it changes).
        if (ClientEditSession.isRemote()) {
            int px = localMouseX - panelX;
            int py = localMouseY - panelY;
            String state = cursorStateAt(localMouseX, localMouseY);
            if (px != lastSentX || py != lastSentY || !state.equals(lastSentState)
                    || !heldItem.equals(lastSentHeld)) {
                lastSentX = px;
                lastSentY = py;
                lastSentState = state;
                lastSentHeld = heldItem;
                ClientEditSession.sendCursor(px, py, state, heldItem);
            }
        }
    }

    /** The cursor sprite that best describes what the local pointer is over (sent to other viewers). */
    private String cursorStateAt(double mx, double my) {
        if (!heldItem.isEmpty()) {
            return "grabbing";
        }
        if (selectedTypeHasEditorKind("sequence") && sequencePanel.isReordering()) {
            return "grabbing";
        }
        for (var child : this.children()) {
            if (child instanceof EditBox eb && eb.visible && eb.isMouseOver(mx, my)) {
                return "ibeam";
            }
        }
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && !(child instanceof EditBox)
                    && w.visible && w.isMouseOver(mx, my)) {
                return "pointing_hand";
            }
        }
        if (inside(mx, my, canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH)) {
            return "crosshair";
        }
        if (inside(mx, my, listX, listY, listW, listViewH)) {
            return "pointing_hand";
        }
        return "default";
    }

    /** Send the pending recipe upsert immediately, if any, and finalize its undo entry. */
    private void flushRecipePush() {
        if (recipePushUid == null) {
            return;
        }
        String uid = recipePushUid;
        recipePushUid = null;
        RecipeInstance recipe = project.recipeByUid(uid).orElse(null);
        if (recipe != null) {
            ClientEditSession.pushRecipe(recipe);
            if (pendingUndoBefore != null && uid.equals(pendingUndoUid)) {
                JsonObject after = recipe.toJson();
                if (!after.equals(pendingUndoBefore)) {
                    undoStack.push(new UndoAction.RecipeChanged(uid, pendingUndoBefore, after));
                }
            }
        }
        pendingUndoBefore = null;
        pendingUndoUid = null;
        captureRecipeBaseline(); // the recipe is settled again
    }

    /** Push the pending meta edit immediately, if any, and finalize its undo entry. */
    private void flushMetaPush() {
        if (!metaPushDue) {
            return;
        }
        metaPushDue = false;
        ClientEditSession.pushMeta();
        if (pendingMetaValid) {
            boolean changed = !pendingMetaName.equals(project.name())
                    || !pendingMetaTargetFile.equals(project.defaultTargetFile())
                    || pendingMetaComments != project.exportComments()
                    || pendingMetaReload != project.reloadOnExport();
            if (changed) {
                undoStack.push(new UndoAction.ProjectMetaChanged(
                        pendingMetaName, pendingMetaTargetFile, pendingMetaComments, pendingMetaReload,
                        project.name(), project.defaultTargetFile(),
                        project.exportComments(), project.reloadOnExport()));
            }
            pendingMetaValid = false;
        }
        captureMetaBaseline();
    }

    /** Refresh the settled recipe baseline to the currently selected recipe's state. */
    private void captureRecipeBaseline() {
        RecipeInstance recipe = selectedRecipe().orElse(null);
        recipeUndoBaseline = recipe == null ? null : recipe.toJson();
        recipeUndoBaselineUid = recipe == null ? null : recipe.uid();
    }

    /** Refresh the settled meta baseline to the project's current meta fields. */
    private void captureMetaBaseline() {
        metaBaselineName = project.name();
        metaBaselineTargetFile = project.defaultTargetFile();
        metaBaselineComments = project.exportComments();
        metaBaselineReload = project.reloadOnExport();
    }

    // ------------------------------------------------------------------ undo/redo

    void applyUndo() {
        // Any pending debounced edit must be finalized first, so it becomes its own undo entry
        // rather than being silently discarded by the state we are about to restore.
        flushPending();
        undoStack.undo().ifPresent(action -> applyAction(action, false));
    }

    void applyRedo() {
        flushPending();
        undoStack.redo().ifPresent(action -> applyAction(action, true));
    }

    /** Apply an action in the given direction ({@code redo} = re-apply, else undo). */
    private void applyAction(UndoAction action, boolean redo) {
        suppressPush = true;
        try {
            if (action instanceof UndoAction.RecipeChanged rc) {
                RecipeInstance restored = RecipeInstance.fromJson(redo ? rc.after() : rc.before());
                project.replace(restored);
                selectedUid = rc.uid();
                ClientEditSession.pushRecipe(restored);
            } else if (action instanceof UndoAction.RecipeAdded ra) {
                if (redo) {
                    restoreRecipe(ra.snapshot());
                } else {
                    dropRecipe(ra.uid());
                }
            } else if (action instanceof UndoAction.RecipeRemoved rr) {
                if (redo) {
                    dropRecipe(rr.uid());
                } else {
                    restoreRecipe(rr.snapshot());
                }
            } else if (action instanceof UndoAction.ProjectMetaChanged pm) {
                applyMeta(pm, redo);
            }
        } finally {
            suppressPush = false;
        }
        paramScroll = 0;
        rebuildWidgets();
    }

    /** Re-insert a recipe from a snapshot, select it, and push it upstream. */
    private void restoreRecipe(JsonObject snapshot) {
        RecipeInstance recipe = RecipeInstance.fromJson(snapshot);
        project.replace(recipe);
        selectedUid = recipe.uid();
        ClientEditSession.pushRecipe(recipe);
    }

    /** Remove the recipe with {@code uid} (if present) and push the removal upstream. */
    private void dropRecipe(String uid) {
        project.recipeByUid(uid).ifPresent(recipe -> {
            project.remove(recipe);
            ClientEditSession.removeRecipe(recipe);
        });
        if (uid.equals(selectedUid)) {
            selectedUid = project.recipes().isEmpty() ? null : project.recipes().get(0).uid();
        }
    }

    private void applyMeta(UndoAction.ProjectMetaChanged pm, boolean redo) {
        project.setName(redo ? pm.afterName() : pm.beforeName());
        project.setDefaultTargetFile(redo ? pm.afterTargetFile() : pm.beforeTargetFile());
        project.setExportComments(redo ? pm.afterComments() : pm.beforeComments());
        project.setReloadOnExport(redo ? pm.afterReload() : pm.beforeReload());
        ClientEditSession.pushMeta();
    }

    @Override
    public void removed() {
        // Called whenever the editor stops being the current screen — including when it merely
        // opens a sub-dialog (Export/Projects). So only flush pending edits here; do NOT unregister
        // as a viewer (that happens in onClose, the real "leave the editor" path).
        flushPending();
        // Our cursor is no longer on this screen; hide it for the others (we stay a viewer).
        ClientEditSession.hideCursor();
        lastSentX = Integer.MIN_VALUE;
        lastSentState = "";
    }

    @Override
    public void onClose() {
        // ESC on the editor itself: flush, stop being a viewer, then return to the game.
        flushPending();
        ClientEditSession.closeProject();
        super.onClose();
    }

    private void flushPending() {
        flushRecipePush();
        flushMetaPush();
    }

    /** Re-sync the editor to the session's project after a server snapshot/delta arrived. */
    public void refreshFromSession() {
        String prevName = project == null ? null : project.name();
        this.project = ClientEditSession.project();
        // A different project arrived (a fresh snapshot / project switch): our history is now stale.
        if (!java.util.Objects.equals(prevName, project.name())) {
            undoStack.clear();
        }
        if (selectedUid != null && project.recipeByUid(selectedUid).isEmpty()) {
            selectedUid = project.recipes().isEmpty() ? null : project.recipes().get(0).uid();
        }
        rebuildWidgets();
    }

    void saveProject(boolean notify) {
        if (ClientEditSession.isRemote()) {
            // The server is authoritative and persists every op; just flush pending edits.
            flushRecipePush();
            ClientEditSession.pushMeta();
        } else {
            try {
                ProjectManager.save(project);
            } catch (IOException e) {
                KjsGen.LOGGER.error("Failed to save project", e);
            }
        }
        if (notify && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("kjsgen.ui.saved"), true);
        }
    }

    /** Switch to another project (called back from the projects screen, local mode only). */
    void setProject(RecipeProject newProject) {
        this.project = newProject;
        ClientEditSession.setLocalProject(newProject);
        this.selectedUid = newProject.recipes().isEmpty() ? null : newProject.recipes().get(0).uid();
        this.listScroll = 0;
        this.paramScroll = 0;
        undoStack.clear();
        rebuildWidgets();
    }

    /** Add a freshly-picked recipe type as a new recipe and select it. */
    void addRecipeOfType(RecipeTypeDefinition type) {
        RecipeInstance recipe = new RecipeInstance(type.id());
        project.add(recipe);
        selectedUid = recipe.uid();
        ClientEditSession.pushRecipe(recipe);
        undoStack.push(new UndoAction.RecipeAdded(recipe.uid(), recipe.toJson()));
        rebuildWidgets();
    }

    private void openTypePicker() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VanillaTypePickerScreen(this));
        }
    }

    private void openProjects() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VanillaProjectsScreen(this));
        }
    }

    private void openImport() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VanillaImportScreen(this));
        }
    }

    /**
     * Add recipes parsed from an existing KubeJS script (see {@link VanillaImportScreen}):
     * append each to the project, push it in remote mode, and select the last one.
     */
    void importRecipes(List<RecipeInstance> recipes) {
        String lastUid = null;
        for (RecipeInstance recipe : recipes) {
            project.add(recipe);
            ClientEditSession.pushRecipe(recipe);
            undoStack.push(new UndoAction.RecipeAdded(recipe.uid(), recipe.toJson()));
            lastUid = recipe.uid();
        }
        if (lastUid != null) {
            selectedUid = lastUid;
            listScroll = 0;
            paramScroll = 0;
        }
        rebuildWidgets();
    }

    private void openExport() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VanillaExportScreen(this));
        }
    }

    /** Runs the vanilla "/reload" command through the client connection, if connected. */
    static void sendReloadCommand() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand("reload");
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21 {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        //?}
        //? if <1.21 {
        /*this.renderBackground(g);*/
        //?}
        this.localMouseX = mouseX;
        this.localMouseY = mouseY;
        this.hoverTooltip = null;
        if (derivedDirty) {
            recomputeDerived();
            derivedDirty = false;
        }

        VanillaTheme.panel(g, panelX, panelY, panelW, panelH);
        g.drawString(this.font, this.title, panelX + 8, panelY + 10, VanillaTheme.TEXT, true);

        drawRecipeList(g, mouseX, mouseY);
        drawCanvas(g, mouseX, mouseY);
        drawParams(g);
        drawPreview(g);

        // widgets on top of the custom panels
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }

        // Presence head-icons sit in the header, just before the project name field. The remote
        // cursors themselves are drawn as a global top-most overlay (see KjsGenClient), so they
        // float above every widget and tooltip; here we only publish where our panel is.
        CollabCursors.setPanel(panelX, panelY);
        if (projectNameField != null) {
            List<Component> headTooltip = CollabHeads.render(g, projectNameField.getX() - 4, panelY + 6, mouseX, mouseY);
            if (headTooltip != null) {
                hoverTooltip = headTooltip;
            }
        }

        // A carried item hides the slot tooltip (it'd only cover the cursor) and follows the pointer.
        if (!heldItem.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300f);
            drawSlotContent(g, mouseX - 9, mouseY - 9, heldItem);
            g.pose().popPose();
        } else if (hoverTooltip != null) {
            g.renderComponentTooltip(this.font, hoverTooltip, mouseX, mouseY);
        }
    }

    private void recomputeDerived() {
        RecipeInstance recipe = selectedRecipe().orElse(null);
        RecipeTypeDefinition type = recipe == null ? null
                : RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        issues = new ArrayList<>();
        if (recipe != null && type != null) {
            issues.addAll(RecipeValidator.validate(recipe, type));
            issues.addAll(RecipeValidator.validateProject(project));
        }
        String text = recipe == null ? "" : ScriptAssembler.previewSingle(project, recipe);
        previewLines = List.of(text.split("\n", -1));
    }

    // ---- recipe list -----------------------------------------------------

    private void drawRecipeList(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font,
                Component.translatable("kjsgen.ui.recipes").getString() + " (" + project.recipes().size() + ")",
                listX, listY - 10, VanillaTheme.TEXT_DIM, true);
        VanillaTheme.section(g, listX, listY, listW, listViewH);

        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listViewH - 1);
        List<RecipeInstance> recipes = project.recipes();
        for (int i = 0; i < recipes.size(); i++) {
            RecipeInstance recipe = recipes.get(i);
            int rowY = listY + i * ROW_H - listScroll;
            if (rowY + ROW_H < listY || rowY > listY + listViewH) {
                continue;
            }
            boolean selected = recipe.uid().equals(selectedUid);
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= Math.max(rowY, listY) && mouseY < Math.min(rowY + ROW_H, listY + listViewH);
            if (selected) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, VanillaTheme.SELECT_BG);
                g.fill(listX + 1, rowY, listX + 3, rowY + ROW_H, VanillaTheme.ACCENT);
            } else if (hovered) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, VanillaTheme.ROW_HOVER);
            }

            ItemStack icon = iconForRecipe(recipe);
            if (!icon.isEmpty()) {
                g.renderItem(icon, listX + 3, rowY + 2);
            }
            String label = this.font.plainSubstrByWidth(recipe.describe(), listW - 24 - 30);
            g.drawString(this.font, label, listX + 22, rowY + 6, VanillaTheme.TEXT, true);

            // duplicate + delete glyph buttons on the right edge of the row
            int delX = listX + listW - 16;
            int dupX = delX - 15;
            drawDuplicateIcon(g, dupX, rowY + 5, hovered);
            drawDeleteIcon(g, delX + 3, rowY + 6, hovered);

            // Frame the row in the colour of any other operator who has this recipe open.
            drawRemoteRecipeMarkers(g, recipe.uid(), rowY);
        }
        g.disableScissor();

        drawScrollbar(g, listX + listW - 3, listY, listViewH, recipes.size() * ROW_H, listScroll);
    }

    /**
     * Outline a recipe row in the colour of every other operator who currently has that recipe
     * open. Multiple operators nest inwards. This is how you find where the others are working when
     * their cursor isn't on your screen (a matching recipe additionally shows their live cursor).
     */
    private void drawRemoteRecipeMarkers(GuiGraphics g, String uid, int rowY) {
        UUID self = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getUUID() : null;
        int inset = 0;
        for (ClientPresence.Viewer v : ClientPresence.viewers()) {
            if (v.uuid().equals(self) || !uid.equals(ClientPresence.recipeUid(v.uuid()))) {
                continue;
            }
            g.renderOutline(listX + 1 + inset, rowY + inset,
                    listW - 2 - 2 * inset, ROW_H - 2 * inset, CollabColors.colorFor(v.color()));
            if (++inset > 3) {
                break;
            }
        }
    }

    private ItemStack iconForRecipe(RecipeInstance recipe) {
        for (var entry : recipe.slots().entrySet()) {
            if (entry.getKey().startsWith("output") && !entry.getValue().isEmpty()) {
                return VanillaTheme.iconStackFor(entry.getValue());
            }
        }
        return recipe.slots().values().stream().filter(c -> !c.isEmpty()).findFirst()
                .map(VanillaTheme::iconStackFor).orElse(ItemStack.EMPTY);
    }

    private void drawDuplicateIcon(GuiGraphics g, int x, int y, boolean hovered) {
        int c = hovered ? VanillaTheme.TEXT : VanillaTheme.TEXT_DIM;
        g.renderOutline(x + 2, y, 7, 8, c);
        g.fill(x, y + 2, x + 6, y + 3, VanillaTheme.SECTION_BG);
        g.renderOutline(x, y + 2, 7, 8, c);
    }

    /** A small font-independent "×" drawn from two diagonals. */
    private void drawDeleteIcon(GuiGraphics g, int x, int y, boolean hovered) {
        int c = hovered ? VanillaTheme.ERROR : VanillaTheme.TEXT_DIM;
        for (int i = 0; i < 7; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, c);
        }
    }

    // ---- canvas ----------------------------------------------------------

    private void drawCanvas(GuiGraphics g, int mouseX, int mouseY) {
        RecipeInstance recipe = selectedRecipe().orElse(null);
        if (recipe == null) {
            g.drawString(this.font, Component.translatable("kjsgen.ui.no_recipe"),
                    canvasBoxX, canvasBoxY - 10, VanillaTheme.TEXT_DIM, true);
            VanillaTheme.section(g, canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH);
            drawCenteredHint(g, Component.translatable("kjsgen.ui.no_recipe_hint"));
            return;
        }
        RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        String typeName = type != null ? typeName(type) : recipe.typeId();
        g.drawString(this.font, typeName, canvasBoxX, canvasBoxY - 10, VanillaTheme.TEXT, true);
        VanillaTheme.section(g, canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH);
        if (type == null) {
            drawCenteredHint(g, Component.translatable("kjsgen.ui.unknown_type"));
            return;
        }

        if (type.editorKind().equals("sequence")) {
            sequencePanel.render(g, this, recipe, type, canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH,
                    mouseX, mouseY);
            drawValidation(g);
            return;
        }
        if (type.editorKind().equals("grid")) {
            drawGridCanvas(g, recipe, type, mouseX, mouseY);
            drawValidation(g);
            return;
        }

        canvasOriginX = canvasBoxX + Math.max(4, (canvasBoxW - type.canvasWidth()) / 2);
        canvasOriginY = canvasBoxY + Math.max(4, (canvasBoxH - type.canvasHeight()) / 2);

        g.enableScissor(canvasBoxX + 1, canvasBoxY + 1, canvasBoxX + canvasBoxW - 1, canvasBoxY + canvasBoxH - 1);
        for (LayoutDecoration decoration : type.decorations()) {
            drawDecoration(g, decoration, canvasOriginX + decoration.x(), canvasOriginY + decoration.y());
        }
        for (SlotDefinition slotDef : type.slots()) {
            if (slotDef.list()) {
                drawListSlot(g, slotDef, recipe, mouseX, mouseY);
                continue;
            }
            int sx = canvasOriginX + slotDef.x();
            int sy = canvasOriginY + slotDef.y();
            boolean hovered = slotHovered(mouseX, mouseY, sx, sy);
            VanillaTheme.slot(g, sx, sy, hovered);

            SlotContent content = recipe.slot(slotDef.key());
            drawSlotContent(g, sx, sy, content);
            boolean hasIssue = issues.stream().anyMatch(is -> is.slotKey().equals(slotDef.key()));
            if (hasIssue) {
                g.renderOutline(sx, sy, 18, 18, VanillaTheme.ERROR);
            }
            if (hovered) {
                hoverTooltip = slotTooltip(slotDef, content);
            }
        }
        g.disableScissor();

        drawValidation(g);
    }

    // ---- mechanical crafting grid canvas --------------------------------

    private void drawGridCanvas(GuiGraphics g, RecipeInstance recipe, RecipeTypeDefinition type,
                               int mouseX, int mouseY) {
        gridCols = com.zizazr.kjsgen.core.CreateSpecialModel.gridW(recipe, type);
        gridRows = com.zizazr.kjsgen.core.CreateSpecialModel.gridH(recipe, type);
        int gridPixW = gridCols * 18;
        int gridPixH = gridRows * 18;
        int arrowGap = 6, arrowW = 22, outGap = 8, outW = 18;
        int contentW = gridPixW + arrowGap + arrowW + outGap + outW;
        int contentH = Math.max(gridPixH, 18);
        gridOriginX = canvasBoxX + Math.max(4, (canvasBoxW - contentW) / 2);
        gridOriginY = canvasBoxY + Math.max(4, (canvasBoxH - contentH) / 2);
        gridOutputX = gridOriginX + gridPixW + arrowGap + arrowW + outGap;
        gridOutputY = gridOriginY + gridPixH / 2 - 9;

        g.enableScissor(canvasBoxX + 1, canvasBoxY + 1, canvasBoxX + canvasBoxW - 1, canvasBoxY + canvasBoxH - 1);
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                int sx = gridOriginX + col * 18;
                int sy = gridOriginY + row * 18;
                boolean hovered = slotHovered(mouseX, mouseY, sx, sy);
                VanillaTheme.slot(g, sx, sy, hovered);
                SlotContent content = recipe.slot(
                        com.zizazr.kjsgen.core.CreateSpecialModel.cellKey(row, col));
                drawSlotContent(g, sx, sy, content);
            }
        }
        drawDecoration(g, LayoutDecoration.arrow(0, 0),
                gridOriginX + gridPixW + arrowGap, gridOriginY + gridPixH / 2 - 8);
        boolean outHover = slotHovered(mouseX, mouseY, gridOutputX, gridOutputY);
        VanillaTheme.slot(g, gridOutputX, gridOutputY, outHover);
        SlotContent output = recipe.slot("output");
        drawSlotContent(g, gridOutputX, gridOutputY, output);
        if (issues.stream().anyMatch(is -> is.slotKey().equals("output"))) {
            g.renderOutline(gridOutputX, gridOutputY, 18, 18, VanillaTheme.ERROR);
        }
        g.disableScissor();

        g.drawString(this.font, gridCols + "x" + gridRows,
                gridOriginX, gridOriginY - 10, VanillaTheme.TEXT_DIM, true);
    }

    /** Grid-canvas click: edit a cell or the output slot. Returns true if handled. */
    private boolean clickGridCanvas(double mouseX, double mouseY, int button, RecipeInstance recipe) {
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                int sx = gridOriginX + col * 18;
                int sy = gridOriginY + row * 18;
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    String key = com.zizazr.kjsgen.core.CreateSpecialModel.cellKey(row, col);
                    SlotDefinition cellDef = com.zizazr.kjsgen.core.CreateSpecialModel.cellSlot(row, col);
                    interactSlot(button, recipe.slot(key),
                            content -> recipe.setSlot(key, content),
                            () -> editSlotDef(recipe, cellDef));
                    return true;
                }
            }
        }
        if (mouseX >= gridOutputX && mouseX < gridOutputX + 18
                && mouseY >= gridOutputY && mouseY < gridOutputY + 18) {
            SlotDefinition outDef = RecipeTypeRegistry.get(recipe.typeId())
                    .flatMap(t -> t.slot("output")).orElse(null);
            if (outDef != null) {
                interactSlot(button, recipe.slot("output"),
                        content -> recipe.setSlot("output", content),
                        () -> editSlotDef(recipe, outDef));
            }
            return true;
        }
        return false;
    }

    // ---- shared helpers used by the sequence panel ----------------------

    boolean hasSlotIssue(String key) {
        return issues.stream().anyMatch(is -> is.slotKey().equals(key));
    }

    void editSlotDef(RecipeInstance recipe, SlotDefinition def) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new VanillaSlotEditScreen(this, recipe, def));
        }
    }

    // ---- list slots (dynamic ingredient list + '+' button) --------------

    /** Columns before a list slot wraps to the next row. */
    private static final int LIST_COLS = 3;

    private int listEntryX(SlotDefinition def, int index) {
        return canvasOriginX + def.x() + (index % LIST_COLS) * 18;
    }

    private int listEntryY(SlotDefinition def, int index) {
        return canvasOriginY + def.y() + (index / LIST_COLS) * 18;
    }

    /** Number of item slots to draw: the entries, or a single empty placeholder. */
    private int listShownCount(RecipeInstance recipe, SlotDefinition def) {
        return Math.max(1, recipe.listSlots(def.key()).size());
    }

    private void drawListSlot(GuiGraphics g, SlotDefinition def, RecipeInstance recipe, int mouseX, int mouseY) {
        List<SlotContent> entries = recipe.listSlots(def.key());
        boolean hasIssue = issues.stream().anyMatch(is -> is.slotKey().equals(def.key()));
        int shown = Math.max(1, entries.size());
        for (int i = 0; i < shown; i++) {
            int sx = listEntryX(def, i);
            int sy = listEntryY(def, i);
            boolean hovered = slotHovered(mouseX, mouseY, sx, sy);
            VanillaTheme.slot(g, sx, sy, hovered);
            SlotContent content = i < entries.size() ? entries.get(i) : SlotContent.EMPTY;
            drawSlotContent(g, sx, sy, content);
            if (hasIssue) {
                g.renderOutline(sx, sy, 18, 18, VanillaTheme.ERROR);
            }
            if (hovered) {
                hoverTooltip = slotTooltip(def, content);
            }
        }
        // the '+' button sits right after the last entry
        int px = listEntryX(def, shown);
        int py = listEntryY(def, shown);
        boolean plusHover = slotHovered(mouseX, mouseY, px, py);
        drawPlusButton(g, px, py, plusHover);
        if (plusHover) {
            hoverTooltip = List.of(Component.translatable("kjsgen.ui.list_add"));
        }
    }

    private void drawPlusButton(GuiGraphics g, int x, int y, boolean hovered) {
        g.fill(x + 1, y + 1, x + 17, y + 17, VanillaTheme.SECTION_BG);
        g.renderOutline(x, y, 18, 18, hovered ? VanillaTheme.ACCENT : VanillaTheme.TEXT_DIM);
        int c = hovered ? VanillaTheme.TEXT : VanillaTheme.TEXT_DIM;
        g.fill(x + 8, y + 4, x + 10, y + 14, c);
        g.fill(x + 4, y + 8, x + 14, y + 10, c);
    }

    /** Draw the item/tag/fluid/chemical icon of one slot's content (no slot background). */
    void drawSlotContent(GuiGraphics g, int sx, int sy, SlotContent content) {
        if (content.isEmpty()) {
            return;
        }
        String count = countLabel(content);
        if (content.kind().isChemical()) {
            VanillaTheme.drawChemical(g, sx + 1, sy + 1, content);
            if (count != null) {
                // same bottom-right spot renderItemDecorations uses for stack counts
                g.drawString(this.font, count, sx + 18 - this.font.width(count), sy + 10,
                        0xFFFFFF, true);
            }
        } else {
            ItemStack icon = VanillaTheme.iconStackFor(content);
            g.renderItem(icon, sx + 1, sy + 1);
            if (count != null) {
                g.renderItemDecorations(this.font, icon, sx + 1, sy + 1, count);
            }
        }
        if (content.kind().isTag()) {
            g.drawString(this.font, "#", sx + 1, sy + 1, VanillaTheme.WARN, true);
        }
        drawChanceBadge(g, sx, sy, content);
    }

    /** Draw the output chance as a small "%NN" badge in the slot's top-left corner. */
    private void drawChanceBadge(GuiGraphics g, int sx, int sy, SlotContent content) {
        if (content.isEmpty() || content.chance() >= 1.0f) {
            return;
        }
        String label = chancePercent(content.chance());
        float scale = 0.5f;
        int w = (int) Math.ceil(this.font.width(label) * scale);
        // dark plate for legibility over the item icon
        g.fill(sx + 1, sy + 1, sx + 2 + w, sy + 6, 0xC0000000);
        g.pose().pushPose();
        g.pose().translate(sx + 1.5f, sy + 1.5f, 200f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(this.font, label, 0, 0, 0xFFFFE066, false);
        g.pose().popPose();
    }

    /** "50%", "12.5%" — trims a trailing ".0". */
    private static String chancePercent(float chance) {
        float pct = chance * 100f;
        String num = pct == Math.floor(pct) ? Integer.toString((int) pct) : Float.toString(pct);
        return num + "%";
    }

    private boolean slotHovered(int mouseX, int mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18
                && mouseX >= canvasBoxX && mouseX < canvasBoxX + canvasBoxW
                && mouseY >= canvasBoxY && mouseY < canvasBoxY + canvasBoxH;
    }

    private String countLabel(SlotContent content) {
        return switch (content.kind()) {
            case ITEM -> content.count() > 1 ? Integer.toString(content.count()) : null;
            case FLUID, FLUID_TAG, CHEMICAL, CHEMICAL_TAG -> content.amount() >= 1000
                    ? (content.amount() / 1000) + "B" : content.amount() + "m";
            default -> null;
        };
    }

    private List<Component> slotTooltip(SlotDefinition slotDef, SlotContent content) {
        List<Component> tip = new ArrayList<>();
        Component role = Component.translatable("kjsgen.slot_role." + slotDef.role().name().toLowerCase());
        tip.add(Component.literal(slotDef.key() + " · ").append(role));
        tip.add(Component.literal(content.describe()).withStyle(s -> s.withColor(VanillaTheme.TEXT_DIM)));
        tip.add(Component.translatable("kjsgen.ui.slot_hint").withStyle(s -> s.withColor(VanillaTheme.TEXT_DIM)));
        return tip;
    }

    private void drawDecoration(GuiGraphics g, LayoutDecoration decoration, int x, int y) {
        switch (decoration.type()) {
            case ARROW -> {
                int cy = y + 8;
                g.fill(x, cy - 1, x + 18, cy + 1, VanillaTheme.TEXT_DIM);
                for (int i = 0; i < 5; i++) {
                    g.fill(x + 18 + i, cy - 5 + i, x + 19 + i, cy + 5 - i, VanillaTheme.TEXT_DIM);
                }
            }
            case FLAME -> {
                for (int i = 0; i < 7; i++) {
                    g.fill(x + 7 - i / 2 - 1, y + 13 - i, x + 7 + i / 2 + 1, y + 14 - i, 0xFFFF8A3C);
                }
            }
            case PLUS -> g.drawString(this.font, "+", x, y, VanillaTheme.TEXT_DIM, true);
            case TEXT -> g.drawString(this.font, decoration.text(), x, y, VanillaTheme.TEXT_DIM, true);
        }
    }

    private void drawValidation(GuiGraphics g) {
        int y = canvasBoxY + canvasBoxH + 3;
        int shown = 0;
        for (RecipeValidator.Issue issue : issues) {
            if (shown >= 3) {
                break;
            }
            int color = issue.severity() == RecipeValidator.Severity.ERROR ? VanillaTheme.ERROR : VanillaTheme.WARN;
            Component msg = Component.translatable(issue.messageKey(), issue.args());
            g.fill(canvasBoxX, y + 1, canvasBoxX + 3, y + 7, color);
            String line = this.font.plainSubstrByWidth(msg.getString(), canvasBoxW - 6);
            g.drawString(this.font, line, canvasBoxX + 6, y, color, true);
            y += 10;
            shown++;
        }
        if (issues.isEmpty() && selectedRecipe().isPresent()) {
            g.fill(canvasBoxX, y + 1, canvasBoxX + 3, y + 7, VanillaTheme.OK_GREEN);
            g.drawString(this.font, "valid", canvasBoxX + 6, y, VanillaTheme.OK_GREEN, true);
        }
    }

    private void drawCenteredHint(GuiGraphics g, Component text) {
        g.drawCenteredString(this.font, text, canvasBoxX + canvasBoxW / 2,
                canvasBoxY + canvasBoxH / 2 - 4, VanillaTheme.TEXT_DIM);
    }

    // ---- params ----------------------------------------------------------

    private void drawParams(GuiGraphics g) {
        g.drawString(this.font, "Parameters", paramX, paramY - 10, VanillaTheme.TEXT_DIM, true);
        VanillaTheme.section(g, paramX, paramY, paramW, paramViewH);

        g.enableScissor(paramX + 1, paramY + 1, paramX + paramW - 1, paramY + paramViewH - 1);
        for (int i = 0; i < paramLabels.size(); i++) {
            AbstractWidget w = paramWidgets.get(i);
            if (!w.visible) {
                continue;
            }
            String label = this.font.plainSubstrByWidth(paramLabels.get(i).getString(), 54);
            g.drawString(this.font, label, paramX + 3, w.getY() + 3, VanillaTheme.TEXT_DIM, true);
        }
        g.disableScissor();
        drawScrollbar(g, paramX + paramW - 3, paramY, paramViewH, paramWidgets.size() * PARAM_ROW_H, paramScroll);
    }

    // ---- preview ---------------------------------------------------------

    private void drawPreview(GuiGraphics g) {
        g.drawString(this.font, Component.translatable("kjsgen.ui.preview"),
                previewX, previewY - 10, VanillaTheme.TEXT_DIM, true);
        VanillaTheme.section(g, previewX, previewY, previewW, previewH);

        float scale = 0.75f;
        int lineH = 8;
        g.enableScissor(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + previewH - 1);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        int tx = (int) ((previewX + 3) / scale);
        int startY = previewY + 3 - previewScroll;
        for (int i = 0; i < previewLines.size(); i++) {
            int ly = startY + i * lineH;
            if (ly + lineH < previewY || ly > previewY + previewH) {
                continue;
            }
            g.drawString(this.font, previewLines.get(i), tx, (int) (ly / scale),
                    VanillaTheme.OK_GREEN, true);
        }
        g.pose().popPose();
        g.disableScissor();
        drawScrollbar(g, previewX + previewW - 3, previewY, previewH, previewLines.size() * lineH, previewScroll);
    }

    // ---- scrollbar -------------------------------------------------------

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int scroll) {
        if (contentH <= viewH) {
            return;
        }
        int barH = Math.max(12, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int barY = y + (int) ((viewH - barH) * (scroll / (float) maxScroll));
        g.fill(x, y, x + 2, y + viewH, 0x40000000);
        g.fill(x, barY, x + 2, barY + barH, VanillaTheme.ACCENT);
    }

    // ------------------------------------------------------------------ carried item

    /**
     * Apply the carry/drag interaction to a single slot.
     *
     * <ul>
     *   <li><b>Shift + left click</b> — open the picker (edit/choose this slot's content).</li>
     *   <li><b>Left click</b> — with an item in hand, drop it into the slot (swapping with whatever
     *       was there); with an empty hand, pick the slot's item up, or open the picker if empty.</li>
     *   <li><b>Right click</b> — clear the slot.</li>
     *   <li><b>Middle click</b> — stamp a copy of the held item into the slot (hand keeps it); with an
     *       empty hand, clone the slot's item into the hand instead.</li>
     * </ul>
     *
     * @param current   the slot's present content
     * @param apply     writes a new content back into the slot
     * @param openPicker opens the slot/type editor for this slot
     */
    private void interactSlot(int button, SlotContent current,
                              java.util.function.Consumer<SlotContent> apply, Runnable openPicker) {
        slotClickedThisEvent = true; // a slot swallowed this click; don't drop the held item
        if (button == 2) { // middle: copy in either direction, never emptying anything
            if (!heldItem.isEmpty()) {
                apply.accept(heldItem);
                markDirty();
            } else if (!current.isEmpty()) {
                heldItem = current;
            }
            return;
        }
        if (button == 1) { // right: clear the slot
            if (!current.isEmpty()) {
                apply.accept(SlotContent.EMPTY);
                markDirty();
            }
            return;
        }
        // left button
        if (hasShiftDown()) { // shift+left opens the picker
            openPicker.run();
        } else if (!heldItem.isEmpty()) {
            apply.accept(heldItem);
            heldItem = current; // swap (EMPTY if the slot was empty)
            markDirty();
        } else if (current.isEmpty()) {
            openPicker.run();
        } else {
            heldItem = current;
            apply.accept(SlotContent.EMPTY);
            markDirty();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        slotClickedThisEvent = false;
        boolean wasHolding = !heldItem.isEmpty();
        boolean handled = doMouseClicked(mouseX, mouseY, button);
        // Clicking anywhere that isn't a slot drops the carried item.
        if (wasHolding && !slotClickedThisEvent) {
            heldItem = SlotContent.EMPTY;
        }
        return handled;
    }

    private boolean doMouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // recipe list rows
        if (inside(mouseX, mouseY, listX, listY, listW, listViewH)) {
            List<RecipeInstance> recipes = project.recipes();
            for (int i = 0; i < recipes.size(); i++) {
                int rowY = listY + i * ROW_H - listScroll;
                if (mouseY < rowY || mouseY >= rowY + ROW_H) {
                    continue;
                }
                RecipeInstance recipe = recipes.get(i);
                int delX = listX + listW - 16;
                int dupX = delX - 15;
                if (mouseX >= delX - 2) {
                    undoStack.push(new UndoAction.RecipeRemoved(recipe.uid(), recipe.toJson()));
                    project.remove(recipe);
                    ClientEditSession.removeRecipe(recipe);
                    if (recipe.uid().equals(selectedUid)) {
                        selectedUid = recipes.isEmpty() ? null : recipes.get(0).uid();
                    }
                    rebuildWidgets();
                } else if (mouseX >= dupX - 2) {
                    RecipeInstance copy = recipe.copy();
                    project.add(copy);
                    selectedUid = copy.uid();
                    ClientEditSession.pushRecipe(copy);
                    undoStack.push(new UndoAction.RecipeAdded(copy.uid(), copy.toJson()));
                    rebuildWidgets();
                } else {
                    selectedUid = recipe.uid();
                    paramScroll = 0;
                    rebuildWidgets();
                }
                return true;
            }
            return true;
        }
        // canvas slots
        RecipeInstance recipe = selectedRecipe().orElse(null);
        RecipeTypeDefinition type = recipe == null ? null
                : RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        if (recipe != null && type != null && inside(mouseX, mouseY, canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH)) {
            if (type.editorKind().equals("sequence")) {
                return sequencePanel.mouseClicked(this, recipe, type, mouseX, mouseY, button);
            }
            if (type.editorKind().equals("grid")) {
                return clickGridCanvas(mouseX, mouseY, button, recipe);
            }
            for (SlotDefinition slotDef : type.slots()) {
                if (slotDef.list()) {
                    if (clickListSlot(mouseX, mouseY, button, recipe, slotDef)) {
                        return true;
                    }
                    continue;
                }
                int sx = canvasOriginX + slotDef.x();
                int sy = canvasOriginY + slotDef.y();
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    interactSlot(button, recipe.slot(slotDef.key()),
                            content -> recipe.setSlot(slotDef.key(), content),
                            () -> editSlotDef(recipe, slotDef));
                    return true;
                }
            }
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
        int delta = (int) (-scrollY * ROW_H);
        if (inside(mouseX, mouseY, listX, listY, listW, listViewH)) {
            int max = Math.max(0, project.recipes().size() * ROW_H - listViewH);
            listScroll = Math.max(0, Math.min(listScroll + delta, max));
            return true;
        }
        if (inside(mouseX, mouseY, paramX, paramY, paramW, paramViewH)) {
            paramScroll += (int) (-scrollY * PARAM_ROW_H);
            layoutParamWidgets();
            return true;
        }
        if (inside(mouseX, mouseY, previewX, previewY, previewW, previewH)) {
            int max = Math.max(0, previewLines.size() * 8 - previewH);
            previewScroll = Math.max(0, Math.min(previewScroll + (int) (-scrollY * 12), max));
            return true;
        }
        if (inside(mouseX, mouseY, canvasBoxX, canvasBoxY, canvasBoxW, canvasBoxH)
                && selectedTypeHasEditorKind("sequence")) {
            sequencePanel.mouseScrolled(scrollY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selectedTypeHasEditorKind("sequence")
                && sequencePanel.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        RecipeInstance recipe = selectedRecipe().orElse(null);
        RecipeTypeDefinition type = recipe == null ? null
                : RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        if (recipe != null && type != null && type.editorKind().equals("sequence")) {
            sequencePanel.mouseReleased(recipe, type);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean selectedTypeHasEditorKind(String kind) {
        RecipeInstance recipe = selectedRecipe().orElse(null);
        if (recipe == null) {
            return false;
        }
        return RecipeTypeRegistry.get(recipe.typeId())
                .map(t -> t.editorKind().equals(kind)).orElse(false);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Handle a click on a list slot's entries or its '+' button. */
    private boolean clickListSlot(double mouseX, double mouseY, int button,
                                  RecipeInstance recipe, SlotDefinition def) {
        int shown = listShownCount(recipe, def);
        List<SlotContent> entries = recipe.listSlots(def.key());
        // entries + the trailing '+' cell all behave as carry/drag slots (the '+' cell appends)
        for (int i = 0; i <= shown; i++) {
            int sx = listEntryX(def, i);
            int sy = listEntryY(def, i);
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                int index = i;
                SlotContent current = i < entries.size() ? entries.get(i) : SlotContent.EMPTY;
                interactSlot(button, current,
                        content -> recipe.setListSlot(def.key(), index, content),
                        () -> openListEntryEditor(recipe, def, index));
                return true;
            }
        }
        return false;
    }

    private void openListEntryEditor(RecipeInstance recipe, SlotDefinition def, int index) {
        if (this.minecraft == null) {
            return;
        }
        List<SlotContent> entries = recipe.listSlots(def.key());
        SlotContent initial = index < entries.size() ? entries.get(index) : SlotContent.EMPTY;
        String title = def.key() + "[" + index + "]";
        this.minecraft.setScreen(new VanillaSlotEditScreen(this, def, title, initial,
                content -> recipe.setListSlot(def.key(), index, content)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Z / Ctrl+Y (or Ctrl+Shift+Z) undo/redo this client's own edits. Handled ahead of
        // widget dispatch: the stock EditBox has no undo of its own, so intercepting here is safe
        // even while a text field is focused (that field's edits ARE the recipe edits being undone).
        if (hasControlDown()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && !hasShiftDown()) {
                applyUndo();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y
                    || (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && hasShiftDown())) {
                applyRedo();
                return true;
            }
        }
        // ESC while carrying an item drops it back to the cursor pool instead of closing the editor.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !heldItem.isEmpty()
                && getFocused() == null) {
            heldItem = SlotContent.EMPTY;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ helpers

    static String typeName(RecipeTypeDefinition type) {
        String path = type.id().contains(":") ? type.id().substring(type.id().indexOf(':') + 1) : type.id();
        String fallback = Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        return Component.translatableWithFallback(type.translationKey(), fallback).getString();
    }

    static Component paramLabel(String key) {
        String fallback = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        return Component.translatableWithFallback("kjsgen.param." + key, fallback);
    }
}
