package com.zizazr.kjsgen.integration.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RemovalRule;
import com.zizazr.kjsgen.ui.vanilla.CollabScreens;
import com.zizazr.kjsgen.ui.vanilla.VanillaEditorScreen;
import com.zizazr.kjsgen.ui.vanilla.VanillaProjectsScreen;
import com.zizazr.kjsgen.ui.vanilla.VanillaRemovalsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side facade the vanilla editor talks to instead of touching {@link ProjectManager}
 * directly. Two modes:
 *
 * <ul>
 *   <li><b>remote</b> — the connected server has kjsgen (a {@code snapshot} or any other S2C op
 *       has arrived). The held project is a mirror of the server's authoritative copy; every
 *       mutation is sent upstream and the server broadcasts it to the other operators. No local
 *       files are written.</li>
 *   <li><b>local</b> — a server without kjsgen (or before the first reply). Behaves exactly like
 *       the old single-player flow: mutations stay in-memory and the Save/Export buttons write to
 *       the local game directory.</li>
 * </ul>
 *
 * All inbound handling runs on the client main thread (the payload handler uses
 * {@code enqueueWork}), so it is safe to touch the open {@link net.minecraft.client.gui.screens.Screen}.
 */
public final class ClientEditSession {
    private static final Gson GSON = new Gson();

    private static RecipeProject project;
    private static boolean remote;
    private static List<String> projectNames = new ArrayList<>();
    /** Last screen key we told the server we're on, so we only send OP_SCREEN on a change. */
    private static String lastScreenKey;
    /** The recipe the local editor currently has selected (published by the editor each tick). */
    private static String localRecipeUid;
    /** Last recipe uid we reported, so OP_SCREEN also re-fires when the selected recipe changes. */
    private static String lastReportedUid;

    private ClientEditSession() {
    }

    // ------------------------------------------------------------------ state

    /** The current working project (mirror in remote mode); lazily the local current project. */
    public static RecipeProject project() {
        if (project == null) {
            project = ProjectManager.current();
        }
        return project;
    }

    /** Local-mode project switch (also updates {@link ProjectManager}). */
    public static void setLocalProject(RecipeProject p) {
        project = p;
        ProjectManager.setCurrent(p);
    }

    public static boolean isRemote() {
        return remote;
    }

    public static List<String> projectNames() {
        return projectNames;
    }

    /** Reset on (dis)connect so a fresh server starts from local mode again. */
    public static void reset() {
        remote = false;
        project = null;
        projectNames = new ArrayList<>();
        ClientPresence.clear();
    }

    // ------------------------------------------------------------------ outbound

    /** Ask the server (if it has kjsgen) for a snapshot of {@code name} and register as a viewer. */
    public static void requestOpen(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("project", name);
        KjsGenNet.toServer(KjsGenNet.OP_OPEN, GSON.toJson(body));
    }

    public static void closeProject() {
        KjsGenNet.toServer(KjsGenNet.OP_CLOSE, "{}");
        ClientPresence.clear();
        lastScreenKey = null;
        localRecipeUid = null;
        lastReportedUid = null;
    }

    /** Publish which recipe the local editor has selected (so the others can locate/highlight it). */
    public static void setLocalRecipe(String uid) {
        localRecipeUid = uid;
    }

    /**
     * Report the kjsgen screen + selected recipe we're currently on (called each client tick). Only
     * sends on a change, so switching screen or recipe updates the others' presence — the tab they
     * see us on and whether our cursor lines up with theirs.
     */
    public static void tickScreenReport() {
        if (!remote) {
            lastScreenKey = null;
            lastReportedUid = null;
            return;
        }
        String key = CollabScreens.labelKey(Minecraft.getInstance().screen);
        if (key == null) {
            lastScreenKey = null; // left the editor context entirely
            return;
        }
        if (!key.equals(lastScreenKey) || !java.util.Objects.equals(localRecipeUid, lastReportedUid)) {
            lastScreenKey = key;
            lastReportedUid = localRecipeUid;
            JsonObject body = new JsonObject();
            body.addProperty("screen", key);
            if (localRecipeUid != null) {
                body.addProperty("uid", localRecipeUid);
            }
            KjsGenNet.toServer(KjsGenNet.OP_SCREEN, GSON.toJson(body));
        }
    }

    /** Refresh the list of server projects (remote), or read local files (local mode). */
    public static void requestList() {
        if (remote) {
            KjsGenNet.toServer(KjsGenNet.OP_LIST, "{}");
        } else {
            projectNames = ProjectManager.listProjects();
        }
    }

    public static void createProject(String name) {
        if (remote) {
            JsonObject body = new JsonObject();
            body.addProperty("project", name);
            KjsGenNet.toServer(KjsGenNet.OP_CREATE, GSON.toJson(body));
        } else {
            setLocalProject(new RecipeProject(name));
        }
    }

    public static void deleteProject(String name) {
        if (remote) {
            JsonObject body = new JsonObject();
            body.addProperty("project", name);
            KjsGenNet.toServer(KjsGenNet.OP_DELETE, GSON.toJson(body));
        } else {
            ProjectManager.delete(name);
            projectNames = ProjectManager.listProjects();
        }
    }

    /** Upsert one recipe (whole-recipe, last-write-wins). No-op in local mode. */
    public static void pushRecipe(RecipeInstance recipe) {
        if (!remote) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("project", project().name());
        body.add("recipe", recipe.toJson());
        KjsGenNet.toServer(KjsGenNet.OP_UPSERT_RECIPE, GSON.toJson(body));
    }

    public static void removeRecipe(RecipeInstance recipe) {
        if (!remote) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("project", project().name());
        body.addProperty("uid", recipe.uid());
        KjsGenNet.toServer(KjsGenNet.OP_REMOVE_RECIPE, GSON.toJson(body));
    }

    /** Upsert one removal rule (whole-rule, last-write-wins). No-op in local mode. */
    public static void pushRemoval(RemovalRule rule) {
        if (!remote) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("project", project().name());
        body.add("removal", rule.toJson());
        KjsGenNet.toServer(KjsGenNet.OP_UPSERT_REMOVAL, GSON.toJson(body));
    }

    public static void removeRemoval(String uid) {
        if (!remote) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("project", project().name());
        body.addProperty("uid", uid);
        KjsGenNet.toServer(KjsGenNet.OP_REMOVE_REMOVAL, GSON.toJson(body));
    }

    /** Push the project's meta fields (name + export options). No-op in local mode. */
    public static void pushMeta() {
        if (!remote) {
            return;
        }
        RecipeProject p = project();
        JsonObject meta = new JsonObject();
        meta.addProperty("name", p.name());
        meta.addProperty("defaultTargetFile", p.defaultTargetFile());
        meta.addProperty("exportComments", p.exportComments());
        meta.addProperty("reloadOnExport", p.reloadOnExport());
        JsonObject body = new JsonObject();
        body.addProperty("project", p.name());
        body.add("meta", meta);
        KjsGenNet.toServer(KjsGenNet.OP_META, GSON.toJson(body));
    }

    /** Send our live cursor position (panel-relative) to the other operators. No-op in local mode. */
    public static void sendCursor(int x, int y, String state, com.zizazr.kjsgen.core.SlotContent held) {
        if (!remote) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("x", x);
        body.addProperty("y", y);
        body.addProperty("state", state);
        if (held != null && !held.isEmpty()) {
            body.add("held", held.toJson());
        }
        KjsGenNet.toServer(KjsGenNet.OP_CURSOR, GSON.toJson(body));
    }

    /** Tell the others to hide our cursor (e.g. we opened a sub-dialog) without leaving the project. */
    public static void hideCursor() {
        if (!remote) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("state", "off");
        KjsGenNet.toServer(KjsGenNet.OP_CURSOR, GSON.toJson(body));
    }

    /** Request a server-side export. Returns true when handled remotely (caller skips local export). */
    public static boolean requestExport() {
        if (!remote) {
            return false;
        }
        JsonObject body = new JsonObject();
        body.addProperty("project", project().name());
        KjsGenNet.toServer(KjsGenNet.OP_EXPORT, GSON.toJson(body));
        return true;
    }

    // ------------------------------------------------------------------ inbound

    public static void handleServer(String op, String json) {
        remote = true; // any reply proves the server speaks kjsgen
        JsonObject body = json.isEmpty() ? new JsonObject() : JsonParser.parseString(json).getAsJsonObject();
        switch (op) {
            case KjsGenNet.OP_SNAPSHOT -> {
                project = RecipeProject.fromJson(body);
                ProjectManager.setCurrent(project);
                refreshEditor();
            }
            case KjsGenNet.OP_UPSERT_RECIPE -> {
                if (matches(body)) {
                    RecipeInstance recipe = RecipeInstance.fromJson(body.getAsJsonObject("recipe"));
                    project().remove(recipe);
                    project().add(recipe);
                    refreshEditor();
                }
            }
            case KjsGenNet.OP_REMOVE_RECIPE -> {
                if (matches(body)) {
                    project().recipeByUid(body.get("uid").getAsString()).ifPresent(project()::remove);
                    refreshEditor();
                }
            }
            case KjsGenNet.OP_UPSERT_REMOVAL -> {
                if (matches(body)) {
                    project().replaceRemoval(RemovalRule.fromJson(body.getAsJsonObject("removal")));
                    refreshEditor();
                }
            }
            case KjsGenNet.OP_REMOVE_REMOVAL -> {
                if (matches(body)) {
                    project().removeRemoval(body.get("uid").getAsString());
                    refreshEditor();
                }
            }
            case KjsGenNet.OP_META -> {
                if (matches(body)) {
                    ServerProjectStore.applyMeta(project(), body.getAsJsonObject("meta"));
                    refreshEditor();
                }
            }
            case KjsGenNet.OP_LIST -> {
                projectNames = new ArrayList<>();
                JsonArray names = body.getAsJsonArray("names");
                if (names != null) {
                    names.forEach(e -> projectNames.add(e.getAsString()));
                }
                if (Minecraft.getInstance().screen instanceof VanillaProjectsScreen ps) {
                    ps.setNames(projectNames);
                }
            }
            case KjsGenNet.OP_PRESENCE -> {
                if (matches(body)) {
                    applyPresence(body);
                }
            }
            case KjsGenNet.OP_CURSOR -> applyCursor(body);
            case KjsGenNet.OP_SCREEN -> {
                if (body.has("uuid")) {
                    UUID id = UUID.fromString(body.get("uuid").getAsString());
                    String screen = body.has("screen") ? body.get("screen").getAsString() : "";
                    ClientPresence.updateScreen(id, screen.isEmpty() ? null : screen);
                    ClientPresence.updateRecipe(id, body.has("uid") ? body.get("uid").getAsString() : null);
                }
            }
            case KjsGenNet.OP_EXPORT_RESULT -> showExportResult(body);
            case KjsGenNet.OP_DENIED -> message(Component.translatable(
                    body.has("reason") ? body.get("reason").getAsString() : "kjsgen.error.no_permission"));
            default -> {
            }
        }
    }

    private static void applyPresence(JsonObject body) {
        List<ClientPresence.Viewer> list = new ArrayList<>();
        JsonArray arr = body.getAsJsonArray("viewers");
        if (arr != null) {
            for (JsonElement e : arr) {
                JsonObject v = e.getAsJsonObject();
                list.add(new ClientPresence.Viewer(
                        UUID.fromString(v.get("uuid").getAsString()),
                        v.get("name").getAsString(),
                        v.has("color") ? v.get("color").getAsInt() : 0));
            }
        }
        ClientPresence.setViewers(list);
    }

    private static void applyCursor(JsonObject body) {
        if (!body.has("uuid")) {
            return;
        }
        UUID id = UUID.fromString(body.get("uuid").getAsString());
        String state = body.has("state") ? body.get("state").getAsString() : "default";
        if ("off".equals(state) || !body.has("x") || !body.has("y")) {
            ClientPresence.hideCursor(id);
            return;
        }
        com.zizazr.kjsgen.core.SlotContent held = body.has("held")
                ? com.zizazr.kjsgen.core.SlotContent.fromJson(body.getAsJsonObject("held"))
                : com.zizazr.kjsgen.core.SlotContent.EMPTY;
        ClientPresence.updateCursor(id, body.get("x").getAsInt(), body.get("y").getAsInt(), state, held);
    }

    private static boolean matches(JsonObject body) {
        if (!body.has("project")) {
            return true;
        }
        return ProjectManager.sanitizeName(body.get("project").getAsString())
                .equals(ProjectManager.sanitizeName(project().name()));
    }

    private static void refreshEditor() {
        if (Minecraft.getInstance().screen instanceof VanillaEditorScreen es) {
            es.refreshFromSession();
        } else if (Minecraft.getInstance().screen instanceof VanillaRemovalsScreen rs) {
            rs.refreshFromSession();
        }
    }

    private static void showExportResult(JsonObject body) {
        if (body.has("error")) {
            message(Component.translatable("kjsgen.error.export_failed")
                    .append(Component.literal(": " + body.get("error").getAsString())));
            return;
        }
        StringBuilder text = new StringBuilder(Component.translatable(
                body.has("text") ? body.get("text").getAsString() : "kjsgen.ui.export_done").getString());
        if (body.has("files")) {
            body.getAsJsonArray("files").forEach(f -> text.append(" ").append(f.getAsString()));
        }
        message(Component.literal(text.toString()));
        if (body.has("warnings")) {
            body.getAsJsonArray("warnings").forEach(w ->
                    message(Component.translatable(w.getAsString())));
        }
    }

    private static void message(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(text, false);
        }
    }
}
