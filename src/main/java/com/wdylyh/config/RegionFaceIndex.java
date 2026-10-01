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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wdylyh.ModReference;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

/**
 * Index of the region face-mod edits: which object id of which region face-mod
 * category has which texture paths modified.
 *
 * Layout: {@code config/reignrender/region_face/index.json}
 * <pre>
 *   {
 *     "BLOCKS": { "minecraft:stone": [ "reignrender:textures/rface/block/stone.png", ... ] },
 *     ...
 *   }
 * </pre>
 * The texture data itself lives in the generated ReignRender_RegionFace resource
 * pack ({@link RegionFacePacks}); this index only drives the GUI markers, the
 * per-id delete flows, the editor's "already modified" state and the render
 * hooks' decision whether a given vanilla texture has a region edit (so objects
 * with no edited texture are never touched).
 *
 * The category key is one of "BLOCKS", "ENTITIES", "PARTICLES", "ITEMS" (the
 * region face categories), and object ids are stored lowercase.
 */
public class RegionFaceIndex {

    public static final String BLOCKS = "BLOCKS";
    public static final String ENTITIES = "ENTITIES";
    public static final String PARTICLES = "PARTICLES";
    public static final String ITEMS = "ITEMS";

    private static final Map<String, Map<String, Set<String>>> INDEX = new HashMap<>();

    private static volatile boolean loaded = false;

    private RegionFaceIndex() {}

    private static void checkLoaded() {
        if (loaded) {
            return;
        }

        synchronized (RegionFaceIndex.class) {
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
                .resolve("region_face");
    }

    /** True when any category has at least one modified texture at all. */
    public static boolean hasAnyOverrides() {
        checkLoaded();

        for (Map<String, Set<String>> ids : INDEX.values()) {
            for (Set<String> paths : ids.values()) {
                if (!paths.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
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

    /**
     * True when this exact texture path is modified for (category, id). The
     * render hooks use this to decide whether the object's vanilla texture has
     * a region edit at all.
     */
    public static boolean hasOverride(String category, String objectId, String texturePath) {
        checkLoaded();

        if (objectId == null || texturePath == null) {
            return false;
        }

        Map<String, Set<String>> ids = INDEX.get(category);

        return ids != null && ids.get(objectId.toLowerCase(Locale.ROOT)) != null
                && ids.get(objectId.toLowerCase(Locale.ROOT)).contains(texturePath);
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

    /** All object ids of the category that carry at least one modified texture, sorted. */
    public static List<String> idsWithOverrides(String category) {
        checkLoaded();

        Map<String, Set<String>> ids = INDEX.get(category);

        if (ids == null) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();

        for (Map.Entry<String, Set<String>> e : ids.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(e.getKey());
            }
        }

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

                JsonArray paths = new JsonArray();

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
            RegionFacePacks.LOGGER.warn("[ReignRender] failed to save the region face-mod index", e);
        }
    }
}
