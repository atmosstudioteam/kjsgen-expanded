package com.zizazr.kjsgen.ui.vanilla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.ContentKind;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Recently used" and "favorite" picks for the slot editor's search panel,
 * kept separately per {@link ContentKind} and persisted to
 * {@code <game dir>/kjsgen/picks.json} so they survive restarts.
 * <p>
 * Values are the same raw ids stored in {@code SlotContent} (registry id for
 * items/fluids/chemicals, tag id without the leading {@code #} for tags).
 */
final class SlotPicks {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int RECENT_MAX = 18;
    private static final int FAV_MAX = 128;

    private static final Map<ContentKind, List<String>> RECENT = new EnumMap<>(ContentKind.class);
    private static final Map<ContentKind, List<String>> FAVORITES = new EnumMap<>(ContentKind.class);
    private static boolean loaded;

    private SlotPicks() {
    }

    static List<String> recent(ContentKind kind) {
        ensureLoaded();
        return RECENT.getOrDefault(kind, List.of());
    }

    static List<String> favorites(ContentKind kind) {
        ensureLoaded();
        return FAVORITES.getOrDefault(kind, List.of());
    }

    /** Push an id to the front of the recents list (deduped, capped). */
    static void addRecent(ContentKind kind, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        ensureLoaded();
        List<String> list = RECENT.computeIfAbsent(kind, k -> new ArrayList<>());
        list.remove(id);
        list.add(0, id);
        while (list.size() > RECENT_MAX) {
            list.remove(list.size() - 1);
        }
        save();
    }

    static boolean isFavorite(ContentKind kind, String id) {
        ensureLoaded();
        return FAVORITES.getOrDefault(kind, List.of()).contains(id);
    }

    /** Add {@code id} to favorites, or remove it if already present. */
    static void toggleFavorite(ContentKind kind, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        ensureLoaded();
        List<String> list = FAVORITES.computeIfAbsent(kind, k -> new ArrayList<>());
        if (!list.remove(id) && list.size() < FAV_MAX) {
            list.add(0, id);
        }
        save();
    }

    // ------------------------------------------------------------------ storage

    private static Path file() {
        return Platform.getGameFolder().resolve("kjsgen").resolve("picks.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            readSection(root, "recent", RECENT);
            readSection(root, "favorites", FAVORITES);
        } catch (Exception e) {
            KjsGen.LOGGER.error("kjsgen: failed to read slot picks", e);
        }
    }

    private static void readSection(JsonObject root, String key, Map<ContentKind, List<String>> target) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return;
        }
        JsonObject section = root.getAsJsonObject(key);
        for (String kindName : section.keySet()) {
            ContentKind kind;
            try {
                kind = ContentKind.valueOf(kindName);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            List<String> list = new ArrayList<>();
            for (var el : section.getAsJsonArray(kindName)) {
                list.add(el.getAsString());
            }
            target.put(kind, list);
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.add("recent", writeSection(RECENT));
            root.add("favorites", writeSection(FAVORITES));
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KjsGen.LOGGER.error("kjsgen: failed to persist slot picks", e);
        }
    }

    private static JsonObject writeSection(Map<ContentKind, List<String>> source) {
        JsonObject section = new JsonObject();
        for (var entry : source.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            JsonArray array = new JsonArray();
            entry.getValue().forEach(array::add);
            section.add(entry.getKey().name(), array);
        }
        return section;
    }
}
