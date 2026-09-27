package com.wdylyh.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wdylyh.ModReference;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

/**
 * Index of the face-mod edits: which object id of which face-mod category has
 * which texture paths modified.
 *
 * Layout: {@code config/reignrender/face_mod/index.json}
 * <pre>
 *   {
 *     "PARTICLES": { "minecraft:cloud": [ "minecraft:textures/particle/generic_0.png", ... ] },
 *     ...
 *   }
 * </pre>
 * The texture data itself lives in the generated ReignRender_FaceMod resource
 * pack ({@link FaceModPacks}); this index only drives the GUI markers, the
 * per-id delete flows and the editor's "already modified" state.
 *
 * The category key is the {@link com.wdylyh.client.gui.FaceModListScreen.FaceKind}
 * enum name, and object ids are stored lowercase.
 */
public class FaceModIndex {

    private static final Map<String, Map<String, Set<String>>> INDEX = new HashMap<>();

    private static volatile boolean loaded = false;

    private FaceModIndex() {}

    private static void checkLoaded() {
        if (loaded) {
            return;
        }

        synchronized (FaceModIndex.class) {
            if (loaded) {
                return;
            }

            File index = root().resolve("index.json").toFile();

            if (index.exists() && index.isFile()) {
                JsonElement el = JsonUtils.parseJsonFile(index);

                if (el != null && el.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> catEntry : el.getAsJsonObject().entrySet()) {
                        if (!catEntry.getValue().isJsonObject()) {
                            continue;
                        }

                        Map<String, Set<String>> ids = new HashMap<>();

                        for (Map.Entry<String, JsonElement> idEntry : catEntry.getValue().getAsJsonObject().entrySet()) {
                            if (!idEntry.getValue().isJsonArray()) {
                                continue;
                            }

                            Set<String> paths = new LinkedHashSet<>();

                            for (JsonElement path : idEntry.getValue().getAsJsonArray()) {
                                if (path.isJsonPrimitive()) {
                                    paths.add(path.getAsString());
                                }
                            }

                            if (!paths.isEmpty()) {
                                ids.put(idEntry.getKey().toLowerCase(Locale.ROOT), paths);
                            }
                        }

                        if (!ids.isEmpty()) {
                            INDEX.put(catEntry.getKey(), ids);
                        }
                    }
                }
            }

            loaded = true;
        }
    }

    private static Path root() {
        return FileUtils.getConfigDirectory().toAbsolutePath()
                .resolve(ModReference.MOD_ID)
                .resolve("face_mod");
    }

    /** True when (category, id) has at least one modified texture. */
    public static boolean hasOverrides(String category, String objectId) {
        checkLoaded();

        if (objectId == null) {
            return false;
        }

        Map<String, Set<String>> ids = INDEX.get(category);

        return ids != null && ids.containsKey(objectId.toLowerCase(Locale.ROOT))
                && !ids.get(objectId.toLowerCase(Locale.ROOT)).isEmpty();
    }

    /** All modified texture paths of (category, id), sorted. */
    public static List<String> overriddenPaths(String category, String objectId) {
        checkLoaded();

        Map<String, Set<String>> ids = INDEX.get(category);

        if (ids == null || objectId == null) {
            return Collections.emptyList();
        }

        Set<String> paths = ids.get(objectId.toLowerCase(Locale.ROOT));

        if (paths == null || paths.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>(paths);
        Collections.sort(out);

        return out;
    }

    /** Records one modified texture and persists the index. */
    public static void addOverride(String category, String objectId, String texturePath) {
        checkLoaded();

        INDEX.computeIfAbsent(category, c -> new HashMap<>())
                .computeIfAbsent(objectId.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>())
                .add(texturePath);

        save();
    }

    /** Drops one modified texture from the index; true when it was listed. */
    public static boolean removeOverride(String category, String objectId, String texturePath) {
        checkLoaded();

        Map<String, Set<String>> ids = INDEX.get(category);

        if (ids == null) {
            return false;
        }

        Set<String> paths = ids.get(objectId.toLowerCase(Locale.ROOT));

        if (paths == null || !paths.remove(texturePath)) {
            return false;
        }

        if (paths.isEmpty()) {
            ids.remove(objectId.toLowerCase(Locale.ROOT));
        }

        save();

        return true;
    }

    /** All ids of a category that currently carry at least one edit. */
    public static List<String> allIds(String category) {
        checkLoaded();

        Map<String, Set<String>> ids = INDEX.get(category);

        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(ids.keySet());
    }

    /** Drops every modified texture of (category, id); returns the dropped paths. */
    public static List<String> removeId(String category, String objectId) {
        checkLoaded();

        Map<String, Set<String>> ids = INDEX.get(category);

        if (ids == null) {
            return Collections.emptyList();
        }

        Set<String> paths = ids.remove(objectId.toLowerCase(Locale.ROOT));

        if (paths == null) {
            return Collections.emptyList();
        }

        save();

        return new ArrayList<>(paths);
    }

    private static void save() {
        JsonObject root = new JsonObject();

        for (Map.Entry<String, Map<String, Set<String>>> catEntry : INDEX.entrySet()) {
            JsonObject ids = new JsonObject();

            for (Map.Entry<String, Set<String>> idEntry : catEntry.getValue().entrySet()) {
                if (idEntry.getValue().isEmpty()) {
                    continue;
                }

                com.google.gson.JsonArray paths = new com.google.gson.JsonArray();

                for (String path : idEntry.getValue()) {
                    paths.add(path);
                }

                ids.add(idEntry.getKey(), paths);
            }

            if (ids.size() > 0) {
                root.add(catEntry.getKey(), ids);
            }
        }

        try {
            Files.createDirectories(root());

            if (root.size() == 0) {
                Files.deleteIfExists(root().resolve("index.json"));
            } else {
                JsonUtils.writeJsonToFile(root, root().resolve("index.json").toFile());
            }
        } catch (IOException e) {
            FaceModPacks.LOGGER.warn("[ReignRender] failed to save the face-mod index", e);
        }
    }
}
