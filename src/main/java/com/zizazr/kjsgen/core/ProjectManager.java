package com.zizazr.kjsgen.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.KjsGen;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Saves and loads {@link RecipeProject}s as JSON files under
 * {@code <game dir>/kjsgen/projects/}. Also tracks the currently open project.
 */
public final class ProjectManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static RecipeProject current;

    private ProjectManager() {
    }

    public static Path projectsDir() {
        return Platform.getGameFolder().resolve("kjsgen").resolve("projects");
    }

    /** The project currently being edited; lazily creates a default one. */
    public static RecipeProject current() {
        if (current == null) {
            current = load("default").orElseGet(() -> new RecipeProject("default"));
        }
        return current;
    }

    public static void setCurrent(RecipeProject project) {
        current = project;
    }

    /** File-name-safe version of a project name. */
    public static String sanitizeName(String name) {
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
        return cleaned.isEmpty() ? "unnamed" : cleaned;
    }

    public static Path fileFor(String projectName) {
        return projectsDir().resolve(sanitizeName(projectName) + ".json");
    }

    public static void save(RecipeProject project) throws IOException {
        Path file = fileFor(project.name());
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(project.toJson()), StandardCharsets.UTF_8);
    }

    public static Optional<RecipeProject> load(String projectName) {
        Path file = fileFor(projectName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            return Optional.of(RecipeProject.fromJson(json));
        } catch (Exception e) {
            KjsGen.LOGGER.error("Failed to load kjsgen project {}", projectName, e);
            return Optional.empty();
        }
    }

    /** Names of all saved projects. */
    public static List<String> listProjects() {
        Path dir = projectsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = new ArrayList<>(files
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.substring(0, fileName.length() - ".json".length());
                    })
                    .sorted(Comparator.naturalOrder())
                    .toList());
            return names;
        } catch (IOException e) {
            KjsGen.LOGGER.error("Failed to list kjsgen projects", e);
            return List.of();
        }
    }

    public static boolean delete(String projectName) {
        try {
            return Files.deleteIfExists(fileFor(projectName));
        } catch (IOException e) {
            KjsGen.LOGGER.error("Failed to delete kjsgen project {}", projectName, e);
            return false;
        }
    }
}
