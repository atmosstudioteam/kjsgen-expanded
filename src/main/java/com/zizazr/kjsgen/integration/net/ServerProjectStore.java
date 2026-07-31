package com.zizazr.kjsgen.integration.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RemovalRule;
import com.zizazr.kjsgen.integration.kubejs.KubeJsExporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative store of the shared recipe projects. There is one instance of this
 * state per running server (integrated or dedicated), so it is modelled as static maps.
 *
 * <p>Projects are loaded lazily from {@link ProjectManager} (which resolves to the server's
 * game directory) and kept in memory. Every mutating op is gated to operators / the
 * singleplayer host, applied to the authoritative {@link RecipeProject}, persisted, and the
 * resulting delta is broadcast to the <em>other</em> players currently viewing that project
 * so their editors update live.
 *
 * <p>All methods here run on the server main thread (callers use {@code enqueueWork}).
 */
public final class ServerProjectStore {
    private static final Gson GSON = new Gson();

    /** project key -> authoritative project. */
    private static final Map<String, RecipeProject> loaded = new HashMap<>();
    /** project key -> players viewing it. */
    private static final Map<String, Set<UUID>> viewers = new HashMap<>();
    /** player -> project key they currently view. */
    private static final Map<UUID, String> viewing = new HashMap<>();
    /** project key -> per-viewer colour index (assigned on join, freed on leave). */
    private static final Map<String, Map<UUID, Integer>> colorIndex = new HashMap<>();

    private ServerProjectStore() {
    }

    // ------------------------------------------------------------------ dispatch

    public static void handle(ServerPlayer player, String op, String json) {
        try {
            JsonObject body = json.isEmpty() ? new JsonObject()
                    : JsonParser.parseString(json).getAsJsonObject();
            switch (op) {
                case KjsGenNet.OP_LIST -> {
                    if (requireOp(player)) sendList(player);
                }
                case KjsGenNet.OP_OPEN -> {
                    if (requireOp(player)) open(player, str(body, "project"));
                }
                case KjsGenNet.OP_CLOSE -> unview(player.getUUID(), player.serverLevel().getServer());
                case KjsGenNet.OP_CURSOR -> relayCursor(player, body);
                case KjsGenNet.OP_SCREEN -> relayScreen(player, body);
                case KjsGenNet.OP_CREATE -> {
                    if (requireOp(player)) create(player, str(body, "project"));
                }
                case KjsGenNet.OP_DELETE -> {
                    if (requireOp(player)) delete(player, str(body, "project"));
                }
                case KjsGenNet.OP_UPSERT_RECIPE -> {
                    if (requireOp(player)) upsertRecipe(player, str(body, "project"),
                            body.getAsJsonObject("recipe"));
                }
                case KjsGenNet.OP_REMOVE_RECIPE -> {
                    if (requireOp(player)) removeRecipe(player, str(body, "project"), str(body, "uid"));
                }
                case KjsGenNet.OP_UPSERT_REMOVAL -> {
                    if (requireOp(player)) upsertRemoval(player, str(body, "project"),
                            body.getAsJsonObject("removal"));
                }
                case KjsGenNet.OP_REMOVE_REMOVAL -> {
                    if (requireOp(player)) removeRemoval(player, str(body, "project"), str(body, "uid"));
                }
                case KjsGenNet.OP_META -> {
                    if (requireOp(player)) updateMeta(player, str(body, "project"),
                            body.getAsJsonObject("meta"));
                }
                case KjsGenNet.OP_EXPORT -> {
                    if (requireOp(player)) export(player, str(body, "project"));
                }
                default -> KjsGen.LOGGER.warn("kjsgen: unknown C2S op '{}' from {}", op, player.getGameProfile().getName());
            }
        } catch (Exception e) {
            KjsGen.LOGGER.error("kjsgen: failed to handle op '{}' from {}", op, player.getGameProfile().getName(), e);
        }
    }

    // ------------------------------------------------------------------ ops

    private static void open(ServerPlayer player, String name) {
        MinecraftServer server = player.serverLevel().getServer();
        unview(player.getUUID(), server);
        String key = key(name);
        RecipeProject project = getOrLoad(name);
        viewers.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(player.getUUID());
        viewing.put(player.getUUID(), key);
        assignColor(key, player.getUUID());
        KjsGenNet.toPlayer(player, KjsGenNet.OP_SNAPSHOT, GSON.toJson(project.toJson()));
        broadcastPresence(server, key);
    }

    private static void create(ServerPlayer player, String name) {
        MinecraftServer server = player.serverLevel().getServer();
        unview(player.getUUID(), server);
        String key = key(name);
        RecipeProject project = new RecipeProject(name);
        loaded.put(key, project);
        persist(project);
        viewers.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(player.getUUID());
        viewing.put(player.getUUID(), key);
        assignColor(key, player.getUUID());
        KjsGenNet.toPlayer(player, KjsGenNet.OP_SNAPSHOT, GSON.toJson(project.toJson()));
        broadcastPresence(server, key);
    }

    private static void delete(ServerPlayer player, String name) {
        String key = key(name);
        loaded.remove(key);
        viewers.remove(key);
        colorIndex.remove(key);
        ProjectManager.delete(name);
        sendList(player);
    }

    /** Relay a viewer's live cursor to the other viewers of the same project. */
    private static void relayCursor(ServerPlayer sender, JsonObject body) {
        String key = viewing.get(sender.getUUID());
        if (key == null) {
            return; // not currently viewing anything
        }
        JsonObject out = new JsonObject();
        out.addProperty("uuid", sender.getUUID().toString());
        out.addProperty("project", key);
        if (body.has("state")) {
            out.addProperty("state", body.get("state").getAsString());
        }
        if (body.has("x") && body.has("y")) {
            out.addProperty("x", body.get("x").getAsInt());
            out.addProperty("y", body.get("y").getAsInt());
        }
        if (body.has("held")) {
            out.add("held", body.get("held"));
        }
        broadcast(sender, key, KjsGenNet.OP_CURSOR, GSON.toJson(out));
    }

    /** Relay which screen a viewer is on to the other viewers of the same project. */
    private static void relayScreen(ServerPlayer sender, JsonObject body) {
        String key = viewing.get(sender.getUUID());
        if (key == null) {
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("uuid", sender.getUUID().toString());
        out.addProperty("project", key);
        out.addProperty("screen", body.has("screen") ? body.get("screen").getAsString() : "");
        if (body.has("uid")) {
            out.addProperty("uid", body.get("uid").getAsString());
        }
        broadcast(sender, key, KjsGenNet.OP_SCREEN, GSON.toJson(out));
    }

    private static void upsertRecipe(ServerPlayer player, String name, JsonObject recipeJson) {
        if (recipeJson == null) return;
        RecipeProject project = getOrLoad(name);
        RecipeInstance recipe = RecipeInstance.fromJson(recipeJson);
        project.remove(recipe); // remove existing same-uid, then add (upsert by uid)
        project.add(recipe);
        persist(project);
        broadcast(player, key(name), KjsGenNet.OP_UPSERT_RECIPE, wrap(name, "recipe", recipeJson));
    }

    private static void removeRecipe(ServerPlayer player, String name, String uid) {
        RecipeProject project = getOrLoad(name);
        project.recipeByUid(uid).ifPresent(project::remove);
        persist(project);
        JsonObject body = new JsonObject();
        body.addProperty("project", name);
        body.addProperty("uid", uid);
        broadcast(player, key(name), KjsGenNet.OP_REMOVE_RECIPE, GSON.toJson(body));
    }

    private static void upsertRemoval(ServerPlayer player, String name, JsonObject removalJson) {
        if (removalJson == null) return;
        RecipeProject project = getOrLoad(name);
        project.replaceRemoval(RemovalRule.fromJson(removalJson));
        persist(project);
        broadcast(player, key(name), KjsGenNet.OP_UPSERT_REMOVAL, wrap(name, "removal", removalJson));
    }

    private static void removeRemoval(ServerPlayer player, String name, String uid) {
        RecipeProject project = getOrLoad(name);
        project.removeRemoval(uid);
        persist(project);
        JsonObject body = new JsonObject();
        body.addProperty("project", name);
        body.addProperty("uid", uid);
        broadcast(player, key(name), KjsGenNet.OP_REMOVE_REMOVAL, GSON.toJson(body));
    }

    private static void updateMeta(ServerPlayer player, String name, JsonObject meta) {
        if (meta == null) return;
        RecipeProject project = getOrLoad(name);
        applyMeta(project, meta);
        persist(project);
        broadcast(player, key(name), KjsGenNet.OP_META, wrap(name, "meta", meta));
    }

    private static void export(ServerPlayer player, String name) {
        RecipeProject project = getOrLoad(name);
        JsonObject result = new JsonObject();
        StringBuilder text = new StringBuilder();
        JsonArray warnings = new JsonArray();
        try {
            KubeJsExporter.ExportResult r = KubeJsExporter.exportProject(project);
            text.append("kjsgen.ui.export_done");
            JsonArray files = new JsonArray();
            for (KubeJsExporter.FileResult f : r.files()) {
                files.add(f.fileName() + ".js(" + f.recipeCount() + ")");
            }
            result.add("files", files);
            r.warnings().forEach(warnings::add);
            if (project.reloadOnExport()) {
                MinecraftServer server = player.serverLevel().getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
            }
        } catch (IOException e) {
            KjsGen.LOGGER.error("kjsgen: server-side export failed", e);
            result.addProperty("error", e.getMessage() == null ? "export failed" : e.getMessage());
        }
        result.addProperty("text", text.toString());
        result.add("warnings", warnings);
        KjsGenNet.toPlayer(player, KjsGenNet.OP_EXPORT_RESULT, GSON.toJson(result));
    }

    private static void sendList(ServerPlayer player) {
        JsonObject body = new JsonObject();
        JsonArray names = new JsonArray();
        ProjectManager.listProjects().forEach(names::add);
        body.add("names", names);
        KjsGenNet.toPlayer(player, KjsGenNet.OP_LIST, GSON.toJson(body));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Architectury {@code PlayerEvent.PLAYER_QUIT} listener, registered from common setup. */
    public static void onLogout(ServerPlayer player) {
        unview(player.getUUID(), player.getServer());
    }

    private static void unview(UUID player, MinecraftServer server) {
        String key = viewing.remove(player);
        if (key == null) {
            return;
        }
        Set<UUID> set = viewers.get(key);
        if (set != null) {
            set.remove(player);
            if (set.isEmpty()) {
                viewers.remove(key);
            }
        }
        freeColor(key, player);
        if (server != null) {
            broadcastPresence(server, key);
        }
    }

    // ------------------------------------------------------------------ presence

    /** Assign the lowest free colour index for {@code player} in this project (stable while viewing). */
    private static int assignColor(String key, UUID player) {
        Map<UUID, Integer> map = colorIndex.computeIfAbsent(key, k -> new HashMap<>());
        Integer existing = map.get(player);
        if (existing != null) {
            return existing;
        }
        int idx = 0;
        while (map.containsValue(idx)) {
            idx++;
        }
        map.put(player, idx);
        return idx;
    }

    private static void freeColor(String key, UUID player) {
        Map<UUID, Integer> map = colorIndex.get(key);
        if (map != null) {
            map.remove(player);
            if (map.isEmpty()) {
                colorIndex.remove(key);
            }
        }
    }

    /** Send the full viewer list (name + colour index) to every viewer of {@code key}. */
    private static void broadcastPresence(MinecraftServer server, String key) {
        Set<UUID> set = viewers.get(key);
        JsonObject body = new JsonObject();
        body.addProperty("project", key);
        JsonArray arr = new JsonArray();
        if (set != null) {
            Map<UUID, Integer> colors = colorIndex.getOrDefault(key, Map.of());
            for (UUID id : set) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) {
                    continue;
                }
                JsonObject v = new JsonObject();
                v.addProperty("uuid", id.toString());
                v.addProperty("name", p.getGameProfile().getName());
                v.addProperty("color", colors.getOrDefault(id, 0));
                arr.add(v);
            }
        }
        body.add("viewers", arr);
        String json = GSON.toJson(body);
        if (set != null) {
            for (UUID id : set) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    KjsGenNet.toPlayer(p, KjsGenNet.OP_PRESENCE, json);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean requireOp(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        boolean allowed = player.hasPermissions(2) || server.isSingleplayerOwner(player.getGameProfile());
        if (!allowed) {
            JsonObject body = new JsonObject();
            body.addProperty("reason", "kjsgen.error.no_permission");
            KjsGenNet.toPlayer(player, KjsGenNet.OP_DENIED, GSON.toJson(body));
        }
        return allowed;
    }

    private static RecipeProject getOrLoad(String name) {
        String key = key(name);
        RecipeProject project = loaded.get(key);
        if (project == null) {
            project = ProjectManager.load(name).orElseGet(() -> new RecipeProject(name));
            loaded.put(key, project);
        }
        return project;
    }

    private static void persist(RecipeProject project) {
        try {
            ProjectManager.save(project);
        } catch (IOException e) {
            KjsGen.LOGGER.error("kjsgen: failed to persist project {}", project.name(), e);
        }
    }

    /** Send a delta to every viewer of {@code key} except {@code origin} (who made the edit). */
    private static void broadcast(ServerPlayer origin, String key, String op, String json) {
        Set<UUID> set = viewers.get(key);
        if (set == null || set.isEmpty()) {
            return;
        }
        MinecraftServer server = origin.serverLevel().getServer();
        for (UUID id : set) {
            if (id.equals(origin.getUUID())) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(id);
            if (target != null) {
                KjsGenNet.toPlayer(target, op, json);
            }
        }
    }

    private static String wrap(String project, String field, JsonObject value) {
        JsonObject body = new JsonObject();
        body.addProperty("project", project);
        body.add(field, value);
        return GSON.toJson(body);
    }

    static void applyMeta(RecipeProject project, JsonObject meta) {
        if (meta.has("name")) project.setName(meta.get("name").getAsString());
        if (meta.has("defaultTargetFile")) project.setDefaultTargetFile(meta.get("defaultTargetFile").getAsString());
        if (meta.has("exportComments")) project.setExportComments(meta.get("exportComments").getAsBoolean());
        if (meta.has("reloadOnExport")) project.setReloadOnExport(meta.get("reloadOnExport").getAsBoolean());
    }

    private static String str(JsonObject body, String key) {
        return body.has(key) ? body.get(key).getAsString() : "";
    }

    private static String key(String name) {
        return ProjectManager.sanitizeName(name);
    }
}
