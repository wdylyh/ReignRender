package com.wdylyh.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdylyh.ShadowBlocks;
import fi.dy.masa.malilib.util.JsonUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Management of the generated face-mod resource pack ("面修改").
 *
 * Every texture edited in the pixel editor is written into the resource pack
 * under {@code <game dir>/resourcepacks/ReignRender_FaceMod}:
 * <pre>
 *   pack.mcmeta
 *   assets/&lt;namespace&gt;/&lt;texture path&gt;   e.g. assets/minecraft/textures/entity/creeper/creeper.png
 * </pre>
 * The pack is enabled through the game's own {@link ResourcePackManager} and
 * the resources are reloaded, so every renderer of the covered texture paths
 * picks the edited image with no per-render hooks at all. The master switch
 * ({@link RenderConfig.General#ENABLE_FACE_MOD}) enables/disables the pack;
 * while disabled the game simply falls back to the original textures.
 */
public class    FaceModPacks {

    public static final Logger LOGGER = LoggerFactory.getLogger(FaceModPacks.class);

    /** Directory name (and resource pack profile id) of the generated pack. */
    public static final String PACK_NAME = "ReignRender_FaceMod";

    /**
     * Profile id the game assigns to folder packs ("file/" + file name, as
     * listed in options.txt). Some versions use the bare folder name, so the
     * manager is probed for both.
     */
    private static final String PACK_ID = "file/" + PACK_NAME;

    private FaceModPacks() {}

    private static Path packDir() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PACK_NAME);
    }

    /**
     * Creates the pack directory and pack.mcmeta when missing. pack_format is
     * paired with a wide supported_formats range so the pack stays compatible
     * across game versions.
     */
    private static void ensurePack() {
        Path dir = packDir();
        Path meta = dir.resolve("pack.mcmeta");

        if (Files.exists(meta)) {
            ensureShadowAtlas();
            ensureShadowResources();
            return;
        }

        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 94);
        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", 15);
        range.addProperty("max_inclusive", 999);
        pack.add("supported_formats", range);
        pack.addProperty("description", "ReignRender Face Mod");

        JsonObject root = new JsonObject();
        root.add("pack", pack);

        try {
            Files.createDirectories(dir);
            JsonUtils.writeJsonToFile(root, meta.toFile());
        } catch (IOException e) {
            LOGGER.warn("[ReignRender] failed to create the face-mod resource pack metadata", e);
        }

        ensureShadowAtlas();
        ensureShadowResources();
    }

    /**
     * Generates the resources of the falling-block shadow blocks into the
     * pack (idempotent): blockstate jsons under
     * {@code assets/reignrender/blockstates/falling_<name>.json} (a copy of
     * the vanilla blockstate with model refs remapped to the shadow models),
     * model jsons under {@code assets/reignrender/models/block/falling/}
     * (vanilla model with texture refs remapped to {@code reignrender:falling/...})
     * and a default copy of every referenced vanilla texture under
     * {@code assets/reignrender/textures/falling/}, so a freshly enabled pack
     * renders falling blocks exactly like the originals.
     */
    private static void ensureShadowResources() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        ResourceManager rm = mc.getResourceManager();

        for (Map.Entry<Block, Block> e : ShadowBlocks.SHADOWS.entrySet()) {
            String vn = Registries.BLOCK.getId(e.getKey()).getPath();
            Path bsFile = packDir().resolve("assets/reignrender/blockstates/falling_" + vn + ".json");

            if (Files.isRegularFile(bsFile)) {
                continue;
            }

            JsonObject vbs = readJson(rm, Identifier.of("minecraft", "blockstates/" + vn + ".json"));

            if (vbs == null) {
                LOGGER.warn("[ReignRender] no vanilla blockstate for the falling shadow {}", vn);
                continue;
            }

            List<String> models = new ArrayList<>();
            remapBlockstate(vbs, models);

            try {
                Files.createDirectories(bsFile.getParent());
                JsonUtils.writeJsonToFile(vbs, bsFile.toFile());
            } catch (IOException ex) {
                LOGGER.warn("[ReignRender] failed to write the shadow blockstate {}", vn, ex);
                continue;
            }

            for (String model : models) {
                ensureShadowModel(rm, model);
            }
        }
    }

    /** Remaps every "model" ref inside the blockstate to a shadow model and collects the model names. */
    private static void remapBlockstate(JsonElement el, List<String> models) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();

            if (obj.has("model") && obj.get("model").isJsonPrimitive()) {
                String ref = obj.get("model").getAsString();
                String remapped = remapToShadowModel(ref, models);

                if (remapped != null) {
                    obj.addProperty("model", remapped);
                }
            } else {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    remapBlockstate(entry.getValue(), models);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                remapBlockstate(item, models);
            }
        }
    }

    /** "minecraft:block/sand" or "block/sand" -> "reignrender:block/falling/sand"; returns null for other refs. */
    private static String remapToShadowModel(String ref, List<String> models) {
        String r = ref;

        if (r.startsWith("minecraft:")) {
            r = r.substring("minecraft:".length());
        }

        if (!r.startsWith("block/")) {
            return null;
        }

        String base = r.substring("block/".length());
        models.add(base);

        return "reignrender:block/falling/" + base;
    }

    /**
     * Copies the vanilla model json into the pack with its texture refs
     * remapped to {@code reignrender:falling/...}, and copies every referenced
     * vanilla block texture next to it as the editable default.
     */
    private static void ensureShadowModel(ResourceManager rm, String base) {
        Path mf = packDir().resolve("assets/reignrender/models/block/falling/" + base + ".json");

        if (Files.isRegularFile(mf)) {
            return;
        }

        List<String> textures = new ArrayList<>();
        JsonObject model = readJson(rm, Identifier.of("minecraft", "models/block/" + base + ".json"));

        if (model == null || !remapModelTextures(model, textures)) {
            // Fall back to a plain cube so the shadow still renders.
            model = new JsonObject();
            model.addProperty("parent", "minecraft:block/cube_all");
            JsonObject tex = new JsonObject();
            tex.addProperty("all", "reignrender:falling/" + base);
            tex.addProperty("particle", "reignrender:falling/" + base);
            model.add("textures", tex);
            textures.add(base);
        }

        try {
            Files.createDirectories(mf.getParent());
            JsonUtils.writeJsonToFile(model, mf.toFile());
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the shadow model {}", base, ex);
            return;
        }

        for (String tex : textures) {
            ensureShadowTexture(rm, tex);
        }
    }

    /**
     * Rewrites the texture refs of the model to the shadow textures
     * ({@code reignrender:falling/...}) and collects the referenced texture
     * names. Returns false when the model references no block textures at all
     * (the caller then uses a plain cube fallback).
     */
    private static boolean remapModelTextures(JsonObject model, List<String> textures) {
        if (!model.has("textures") || !model.get("textures").isJsonObject()) {
            return false;
        }

        JsonObject tex = model.getAsJsonObject("textures");
        boolean any = false;

        for (Map.Entry<String, JsonElement> entry : tex.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }

            String ref = entry.getValue().getAsString();

            if (ref.startsWith("#")) {
                continue; // reference to a texture variable, resolved by the parent
            }

            String r = ref;

            if (r.startsWith("minecraft:")) {
                r = r.substring("minecraft:".length());
            }

            if (r.startsWith("block/")) {
                String sub = r.substring("block/".length());
                tex.addProperty(entry.getKey(), "reignrender:falling/" + sub);
                textures.add(sub);
                any = true;
            } else {
                tex.addProperty(entry.getKey(), ref); // keep non-block refs (e.g. item textures)
            }
        }

        return any;
    }

    /** Copies the original vanilla block texture into the pack as the editable shadow texture. */
    private static void ensureShadowTexture(ResourceManager rm, String sub) {
        Path tf = packDir().resolve("assets/reignrender/textures/falling/" + sub + ".png");

        if (Files.isRegularFile(tf)) {
            return;
        }

        try {
            Resource res = rm.getResourceOrThrow(Identifier.of("minecraft", "textures/block/" + sub + ".png"));
            NativeImage img = NativeImage.read(res.getInputStream());

            try {
                Files.createDirectories(tf.getParent());
                img.writeTo(tf);
            } finally {
                img.close();
            }
        } catch (Exception ex) {
            LOGGER.warn("[ReignRender] failed to copy the default shadow texture {}", sub, ex);
        }
    }

    /** Reads a json resource from the given manager, or null when missing/invalid. */
    private static JsonObject readJson(ResourceManager rm, Identifier id) {
        try (Reader reader = new InputStreamReader(rm.getResourceOrThrow(id).getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Held / dropped item shadow models
    // ------------------------------------------------------------------

    /** The two shadow kinds and their texture folders in the pack. */
    public static final String[] SHADOW_KINDS = {"held", "dropped"};

    /**
     * Generates the held/dropped item shadow resources for the current face
     * mod id lists: one atlas json entry adding {@code textures/shadow/**}
     * to the blocks atlas, one shadow model per listed item per kind under
     * {@code assets/reignrender/models/item/shadow/<kind>/<item>.json} (the
     * vanilla item model with texture refs remapped to the shadow folder) and
     * a default copy of every referenced vanilla texture under
     * {@code assets/reignrender/textures/shadow/<kind>/}. Idempotent.
     */
    public static void ensureShadowItemResources() {
        MinecraftClient cli = MinecraftClient.getInstance();

        if (cli == null) {
            return;
        }

        ResourceManager rm = cli.getResourceManager();
        ensureShadowAtlas();

        // One-time re-sync of the non-edited shadow copies: packs created by
        // older versions may carry copies that baked in a block edit (broken
        // readOriginal source order).
        boolean resyncAll;
        Path stampFile = packDir().resolve("assets/reignrender/textures/shadow/" + COPY_STAMP);

        try {
            resyncAll = !Files.isRegularFile(stampFile)
                    || !COPY_STAMP_ID.equals(Files.readString(stampFile).trim());
        } catch (IOException e) {
            resyncAll = true;
        }

        // Every item whose model chain references BLOCK textures gets shadow
        // copies for held / dropped rendering even when it is not listed:
        // without them the item falls back to the vanilla model, whose refs
        // point straight at the vanilla block textures — and a block-category
        // edit overrides exactly those, so the edit would leak into the held /
        // dropped look. Listed / edited ids are (re)processed additionally so
        // newly added ids get their model and edited copies stay intact.
        for (Item item : Registries.ITEM) {
            String sid = Registries.ITEM.getId(item).toString();
            boolean heldListed = isShadowId("held", sid);
            boolean droppedListed = isShadowId("dropped", sid);
            boolean done = SHADOW_DONE.contains(sid);

            if (done && !heldListed && !droppedListed && !resyncAll) {
                continue;
            }

            ShadowResolution r = resolveItemModel(rm, sid);

            SHADOW_DONE.add(sid);

            if (r == null) {
                continue;
            }

            // Item-only items (model chain references no block textures) need
            // no isolation shadow: their item textures are never touched by
            // the block category. They only get a shadow when listed / edited
            // (their edits then live on the item texture copies).
            for (String kind : SHADOW_KINDS) {
                boolean listed = kind.equals("held") ? heldListed : droppedListed;

                if (!listed && !r.blockDerived) {
                    continue;
                }

                ensureShadowItemModel(rm, kind, sid, r, resyncAll);
            }
        }

        // Mark the pack's copies as produced by the current copy logic.
        try {
            Files.createDirectories(stampFile.getParent());
            Files.writeString(stampFile, COPY_STAMP_ID);
        } catch (IOException e) {
            LOGGER.warn("[ReignRender] failed to write the shadow copy stamp", e);
        }
    }

    /** True when the id is in the shadow id list of the kind or still carries edits. */
    private static boolean isShadowId(String kind, String sid) {
        boolean listed = kind.equals("held")
                ? RenderConfig.Face.FACE_HELD_ITEMS.getStrings().contains(sid)
                : RenderConfig.Face.FACE_ITEM_ENTITIES.getStrings().contains(sid);

        return listed || FaceModIndex.overriddenPaths(
                kind.equals("held") ? "HELD_ITEMS" : "ITEM_ENTITIES", sid).size() > 0;
    }

    /** Item ids whose shadow resources were already resolved in this session. */
    private static final Set<String> SHADOW_DONE = new HashSet<>();

    /**
     * Copy-generation stamp: marks that every non-edited shadow copy in
     * the pack was made by the current copy logic. When missing (packs
     * from older versions may carry copies that baked in a block edit
     * because of the broken readOriginal order), every non-edited copy is
     * re-synced once from the vanilla source.
     */
    private static final String COPY_STAMP = "gen.stamp";
    private static final String COPY_STAMP_ID = "readOriginal-v2";

    /** Item ids (per kind) whose shadow model baked successfully; drives the wrap. */
    private static final Map<String, Set<String>> SHADOW_READY = new HashMap<>();

    /** The resolved shadow model of one item: merged json + vanilla sources. */
    private static class ShadowResolution {
        final JsonObject model;
        final List<Identifier> sources;
        final boolean blockDerived;

        ShadowResolution(JsonObject model, List<Identifier> sources, boolean blockDerived) {
            this.model = model;
            this.sources = sources;
            this.blockDerived = blockDerived;
        }
    }

    /**
     * Resolves the vanilla item model of an id and remaps EVERY texture ref
     * along the parent chain to {@code reignrender:shadow/<kind>/...} copies,
     * so no ref keeps pointing at a vanilla path. Returns null when the item
     * has no resolvable model and no item/block texture either.
     */
    private static ShadowResolution resolveItemModel(ResourceManager rm, String sid) {
        String norm = normalizeId(sid);

        if (norm == null) {
            return null;
        }

        int c = norm.indexOf(':');
        String ns = norm.substring(0, c);
        String shortName = norm.substring(c + 1);
        JsonObject model = readJson(rm, Identifier.of(ns, "models/item/" + shortName + ".json"));

        if (model == null) {
            // 1.21.4+ item definitions: assets/<ns>/items/<id>.json points at
            // any model (e.g. the anvil item references minecraft:block/anvil
            // directly and there is no models/item/anvil.json).
            JsonObject def = readJson(rm, Identifier.of(ns, "items/" + shortName + ".json"));

            if (def != null && def.has("model") && def.get("model").isJsonObject()) {
                JsonObject m = def.getAsJsonObject("model");

                if (m.has("type") && m.get("type").isJsonPrimitive()
                        && m.get("type").getAsString().equals("minecraft:model")
                        && m.has("model") && m.get("model").isJsonPrimitive()) {
                    String ref = m.get("model").getAsString();
                    String rns = ns;

                    if (ref.startsWith("minecraft:")) {
                        rns = "minecraft";
                        ref = ref.substring("minecraft:".length());
                    }

                    model = readJson(rm, Identifier.of(rns, "models/" + ref + ".json"));
                }
            }
        }

        List<Identifier> sources = new ArrayList<>();
        boolean blockDerived = false;
        boolean remapped = false;

        if (model != null) {
            remapped = remapShadowTextures(model, ns, sources);
            // hasBlockRef must run on the PRE-remap refs: after remapShadowTextures
            // the paths read "shadow/..." and no longer start with "block/". The
            // sources list holds the vanilla texture ids just collected, so it
            // still sees the original "textures/block/..." paths.
            blockDerived |= anyBlockSource(sources);

            if (model.has("parent") && model.get("parent").isJsonPrimitive()) {
                String parentRef = model.get("parent").getAsString();

                for (int guard = 0; parentRef != null && guard < 16; guard++) {
                    String p = parentRef;
                    String pns = ns;

                    if (p.startsWith("minecraft:")) {
                        pns = "minecraft";
                        p = p.substring("minecraft:".length());
                    }

                    JsonObject pj = readJson(rm, Identifier.of(pns, "models/" + p + ".json"));

                    if (pj == null) {
                        break;
                    }

                    // Vanilla resolves textures child-first: a key already
                    // remapped on a closer model must not be overwritten.
                    if (pj.has("textures") && pj.get("textures").isJsonObject()) {
                        JsonObject tex = model.has("textures") && model.get("textures").isJsonObject()
                                ? model.getAsJsonObject("textures") : new JsonObject();

                        if (!model.has("textures")) {
                            model.add("textures", tex);
                        }

                        for (Map.Entry<String, JsonElement> entry : pj.getAsJsonObject("textures").entrySet()) {
                            if (!entry.getValue().isJsonPrimitive() || tex.has(entry.getKey())) {
                                continue;
                            }

                            String ref = entry.getValue().getAsString();

                            if (!ref.startsWith("#")) {
                                String plain = refNsPath(ref);
                                String srcNs = refNsOf(ref, pns);

                                tex.addProperty(entry.getKey(), "reignrender:shadow/" + plain);
                                sources.add(Identifier.of(srcNs, "textures/" + plain + ".png"));
                                remapped = true;
                                blockDerived |= plain.startsWith("block/");
                            }
                        }
                    }

                    blockDerived |= hasBlockRef(pj);
                    parentRef = pj.has("parent") && pj.get("parent").isJsonPrimitive()
                            ? pj.get("parent").getAsString() : null;
                }
            }
        }

        if (model == null || !remapped) {
            // Flat item fallback: point layer0 at the copy of the vanilla
            // item texture — item-only items must use the ITEM texture, the
            // block texture is only the second candidate for block items
            // whose model could not be resolved. The sprite ref must use the
            // same sub path as the copied file, otherwise the atlas misses.
            String sub = null;
            Identifier src = null;

            for (String cand : new String[] {"item/" + shortName, "block/" + shortName}) {
                Identifier t = Identifier.of(ns, "textures/" + cand + ".png");

                if (rm.getResource(t).isPresent()) {
                    src = t;
                    sub = cand;
                    break;
                }
            }

            if (src == null) {
                return null;
            }

            model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject tex = new JsonObject();
            tex.addProperty("layer0", "reignrender:shadow/" + sub);
            model.add("textures", tex);
            sources.clear();
            sources.add(src);
            blockDerived = sub.startsWith("block/");
        }

        return new ShadowResolution(model, sources, blockDerived);
    }

    /** True when any collected vanilla source texture is a block texture. */
    private static boolean anyBlockSource(List<Identifier> sources) {
        for (Identifier t : sources) {
            if (t.getPath().startsWith("textures/block/")) {
                return true;
            }
        }

        return false;
    }

    /** The namespace-free path of a texture ref ("minecraft:block/x" -> "block/x"). */
    private static String refNsPath(String ref) {
        int c = ref.indexOf(':');

        return c < 0 ? ref : ref.substring(c + 1);
    }

    private static String refNsOf(String ref, String modelNs) {
        return ref.startsWith("minecraft:") ? "minecraft" : modelNs;
    }

    /** True when any texture ref of the model is a block texture (namespace tolerated). */
    private static boolean hasBlockRef(JsonObject model) {
        if (!model.has("textures") || !model.get("textures").isJsonObject()) {
            return false;
        }

        for (JsonElement e : model.getAsJsonObject("textures").asMap().values()) {
            if (e.isJsonPrimitive() && refNsPath(e.getAsString()).startsWith("block/")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remaps the model's own texture refs to the shadow folder and collects
     * the vanilla source textures. Returns true when at least one ref was
     * remapped. Variables ({@code #key}) and unknown paths stay untouched.
     */
    private static boolean remapShadowTextures(JsonObject model, String modelNs, List<Identifier> sources) {
        if (!model.has("textures") || !model.get("textures").isJsonObject()) {
            return false;
        }

        JsonObject tex = model.getAsJsonObject("textures");
        boolean any = false;

        for (Map.Entry<String, JsonElement> entry : tex.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }

            String ref = entry.getValue().getAsString();

            if (ref.startsWith("#")) {
                continue;
            }

            String plain = refNsPath(ref);

            tex.addProperty(entry.getKey(), "reignrender:shadow/" + plain);
            sources.add(Identifier.of(refNsOf(ref, modelNs), "textures/" + plain + ".png"));
            any = true;
        }

        return any;
    }

    /**
     * Writes the shadow model + texture copies of one item for one kind. The
     * copies hold the ORIGINAL textures (read from the lowest-priority
     * resource, never the face-mod pack's own override); only a copy directly
     * edited through the held / dropped GUI (recorded in the index) is kept.
     * The model json is written only after EVERY copy succeeded — a model
     * whose sprite file is missing would render the item blank.
     */
    private static void ensureShadowItemModel(ResourceManager rm, String kind, String sid,
                                              ShadowResolution r, boolean resyncAll) {
        String norm = normalizeId(sid);

        if (norm == null) {
            return;
        }

        int c = norm.indexOf(':');
        String shortName = norm.substring(c + 1);
        String cat = kind.equals("held") ? "HELD_ITEMS" : "ITEM_ENTITIES";
        Path mf = packDir().resolve("assets/reignrender/models/item/shadow/" + kind + "/" + shortName + ".json");
        List<String> editedPaths = FaceModIndex.overriddenPaths(cat, norm);

        // The copies must exist before the model json is written: a missing
        // sprite turns the item blank, so a failed copy cancels the whole
        // shadow (the item then keeps rendering vanilla).
        for (Identifier vanillaTex : r.sources) {
            String rest = vanillaTex.getPath().substring("textures/".length());
            Path target = packDir().resolve("assets/reignrender/textures/shadow/" + kind + "/" + rest);
            String indexPath = "reignrender:textures/shadow/" + kind + "/" + rest;

            if (Files.isRegularFile(target) && editedPaths.contains(indexPath)) {
                // A directly edited copy is user content and stays.
                continue;
            }

            // All other copies hold the untouched vanilla look: re-sync them
            // when missing, when the copy stamp is outdated (copies from older
            // versions may have baked in a block edit) or when listed (a copy
            // created while a block edit was active must not keep it baked in).
            if (!copyVanillaTexture(rm, vanillaTex, target)) {
                return;
            }
        }

        // Always rewrite the model json: earlier runs may have produced
        // broken refs (e.g. "reignrender:shadow/minecraft:block/..." from
        // namespaced texture keys) that a file-exists skip would never fix.
        // The texture refs must carry the kind segment: the copies live under
        // textures/shadow/<kind>/... (the atlas directory source "shadow" with
        // prefix "shadow/" builds sprite ids shadow/<kind>/<path>), and the
        // resolution refs are kind-less. The resolved model is shared between
        // the held and dropped kinds, so each kind writes its own deep copy.
        JsonObject out = r.model.deepCopy();

        if (out.has("textures") && out.get("textures").isJsonObject()) {
            JsonObject tex = out.getAsJsonObject("textures");

            for (Map.Entry<String, JsonElement> entry : tex.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    String v = entry.getValue().getAsString();

                    if (v.startsWith("reignrender:shadow/")) {
                        tex.addProperty(entry.getKey(),
                                "reignrender:shadow/" + kind + "/" + v.substring("reignrender:shadow/".length()));
                    }
                }
            }
        }

        try {
            Files.createDirectories(mf.getParent());
            JsonUtils.writeJsonToFile(out, mf.toFile());
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the shadow item model {}/{}", kind, shortName, ex);
            return;
        }

        SHADOW_READY.computeIfAbsent(kind, k -> new HashSet<>()).add(norm);
    }

    /**
     * Called whenever a face-mod id list changes (a removed id from the list
     * screen as well as a config reset): the edited textures of every id that
     * is no longer listed are deleted from the pack, so the object reverts to
     * its original look instead of silently keeping the edit.
     */
    public static void onFaceListChanged() {
        List<String> paths = new ArrayList<>();

        for (com.wdylyh.client.gui.FaceModListScreen.FaceKind kind
                : com.wdylyh.client.gui.FaceModListScreen.FaceKind.values()) {
            if (kind.getCfg() == null) {
                continue;
            }

            List<String> listed = kind.getCfg().getStrings();

            for (String id : FaceModIndex.allIds(kind.name())) {
                if (!listed.contains(id)) {
                    paths.addAll(FaceModIndex.removeId(kind.name(), id));
                }
            }
        }

        if (!paths.isEmpty()) {
            removeTextures(paths);
        }
    }

    /**
     * Adds textures/shadow/** and textures/falling/** to the blocks atlas via
     * merged atlas sources. The prefix must repeat the folder name: a
     * directory source builds its sprite ids as prefix + path relative to the
     * source folder, and the models reference
     * {@code reignrender:shadow/...} / {@code reignrender:falling/...}.
     * The file is always rewritten so a stale earlier version (empty prefix,
     * missing falling source) is repaired.
     */
    private static void ensureShadowAtlas() {
        JsonArray sources = new JsonArray();

        for (String dir : new String[] {"shadow", "falling"}) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "directory");
            source.addProperty("source", dir);
            source.addProperty("prefix", dir + "/");
            sources.add(source);
        }

        JsonObject root = new JsonObject();
        root.add("sources", sources);

        Path f = packDir().resolve("assets/minecraft/atlases/blocks.json");

        try {
            Files.createDirectories(f.getParent());
            JsonUtils.writeJsonToFile(root, f.toFile());
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the shadow atlas source", ex);
        }
    }

    /**
     * Copies a vanilla png resource (read from the lowest-priority resource,
     * i.e. the original — never the face-mod pack's own override) to the
     * target path, always overwriting. Returns false when no source exists.
     */
    private static boolean copyVanillaTexture(ResourceManager rm, Identifier vanillaTex, Path target) {
        try {
            NativeImage img = readOriginal(rm, vanillaTex);

            if (img == null) {
                LOGGER.warn("[ReignRender] no original resource found for the shadow texture {}", vanillaTex);
                return false;
            }

            try {
                Files.createDirectories(target.getParent());
                img.writeTo(target);
            } finally {
                img.close();
            }

            return true;
        } catch (Exception ex) {
            LOGGER.warn("[ReignRender] failed to copy the default shadow texture {}", vanillaTex, ex);
            return false;
        }
    }

    /**
     * Reads the ORIGINAL image of a texture, never the face-mod pack's own
     * override. The shadow copies must hold the untouched look: when they
     * picked up an already edited block texture (the face-mod pack overrides
     * {@code minecraft:textures/block/...}), every held / dropped / item
     * shadow generated afterwards would carry that block edit forever.
     * The pack order of getAllResources is not relied on: any resource coming
     * from the face-mod pack itself is skipped explicitly, and the remaining
     * lowest-priority entry (scanning from the end) is the vanilla original.
     */
    private static NativeImage readOriginal(ResourceManager rm, Identifier id) {
        List<Resource> all;

        try {
            all = rm.getAllResources(id);
        } catch (Exception ex) {
            return null;
        }

        for (int i = all.size() - 1; i >= 0; i--) {
            Resource res = all.get(i);

            // The face-mod pack's own override (an edited block texture) must
            // never seed a shadow copy — skip it regardless of its position.
            String pid = res.getPackId();

            if (PACK_NAME.equals(pid) || PACK_ID.equals(pid)) {
                continue;
            }

            try {
                return NativeImage.read(res.getInputStream());
            } catch (Exception ignored) {
                // try the next copy
            }
        }

        return null;
    }

    /** "name" -> "minecraft:name"; returns null for malformed ids. */
    private static String normalizeId(String sid) {
        if (sid == null || sid.isEmpty()) {
            return null;
        }

        String s = sid.toLowerCase(Locale.ROOT);

        return s.contains(":") ? s : "minecraft:" + s;
    }

    /**
     * The shadow model identifiers of every item whose shadow resources exist
     * (listed / edited ids plus the block-derived isolation shadows), per
     * kind. Built by {@link #ensureShadowItemResources} on each reload.
     */
    public static List<Identifier> shadowModelIds() {
        List<Identifier> ids = new ArrayList<>();

        for (Map.Entry<String, Set<String>> e : SHADOW_READY.entrySet()) {
            for (String norm : e.getValue()) {
                ids.add(Identifier.of("reignrender", "item/shadow/" + e.getKey() + "/"
                        + norm.substring(norm.indexOf(':') + 1)));
            }
        }

        return ids;
    }

    /** {@code reignrender:item/shadow/<kind>/<item>} for a listed item id, or null. */
    public static Identifier shadowModelId(String sid, String kind) {
        String norm = normalizeId(sid);

        if (norm == null) {
            return null;
        }

        return Identifier.of("reignrender", "item/shadow/" + kind + "/" + norm.substring(norm.indexOf(':') + 1));
    }

    /**
     * Converts a fully namespaced texture path
     * ({@code "minecraft:textures/entity/creeper/creeper.png"}) into the file
     * inside the pack ({@code assets/minecraft/...}), or null when the path
     * is not a valid identifier.
     */
    private static Path textureFile(String texturePath) {
        Identifier id = Identifier.tryParse(texturePath);

        if (id == null) {
            return null;
        }

        return packDir().resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
    }

    /**
     * Reads the edited texture from the pack, or null when this texture has
     * not been modified (then the editor starts from the original resource).
     */
    public static NativeImage readModified(String texturePath) {
        Path file = textureFile(texturePath);

        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }

        try {
            return NativeImage.read(Files.newInputStream(file));
        } catch (Exception e) {
            LOGGER.warn("[ReignRender] failed to read the modified texture {}", texturePath, e);
            return null;
        }
    }

    /**
     * Writes the edited texture into the pack, enables the pack and reloads
     * the resources, so the edit applies immediately.
     */
    public static void saveTexture(String texturePath, NativeImage image) {
        Path file = textureFile(texturePath);

        if (file == null) {
            return;
        }

        try {
            ensurePack();
            Files.createDirectories(file.getParent());
            image.writeTo(file);
        } catch (IOException e) {
            LOGGER.warn("[ReignRender] failed to save the modified texture {}", texturePath, e);
            return;
        }

        // Every edit must (re)create the held / dropped item shadow resources
        // BEFORE the reload: a block-category edit is exactly the case that
        // leaks into the held / dropped look, so the isolation shadows have to
        // exist on disk before the reload starts — during the reload the atlas
        // is stitched from the pack files, and the model-loading plugin runs
        // too late in the pipeline to feed freshly written sprites into it.
        ensureShadowItemResources();

        enablePackAndReload();
    }

    /**
     * Deletes the given edited textures from the pack and reloads the
     * resources (only when the pack is enabled and something was removed).
     */
    public static void removeTextures(Collection<String> texturePaths) {
        boolean removed = false;

        for (String texturePath : texturePaths) {
            Path file = textureFile(texturePath);

            if (file == null) {
                continue;
            }

            try {
                if (Files.deleteIfExists(file)) {
                    removed = true;
                }
            } catch (IOException e) {
                LOGGER.warn("[ReignRender] failed to delete the modified texture {}", texturePath, e);
            }
        }

        if (removed && isEnabled()) {
            reloadResources();
        }
    }

    /**
     * The face-mod master switch: enables the pack (and reloads) when turned
     * on, disables it (and reloads) when turned off. Turning it on without an
     * existing pack (nothing edited yet) does nothing.
     */
    public static void applyEnabled(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        if (!enabled) {
            if (isEnabled()) {
                disablePack();
                reloadResources();
            }

            return;
        }

        // The shadow blocks' resources live in the pack, so turning the
        // master switch on always creates it (even without edited textures).
        // The held / dropped item shadows must be on disk before the reload
        // for the same reason as in saveTexture: the atlas is stitched from
        // the pack files during the reload.
        ensurePack();
        ensureShadowItemResources();
        enablePackAndReload();
    }

    /**
     * True when the face-mod resource pack is currently enabled; the falling
     * block shadow hook only swaps in the shadow states while this holds
     * (otherwise the shadow resources would not be loaded and the falling
     * blocks would render as missing-texture cubes).
     */
    public static boolean isPackActive() {
        return isEnabled();
    }

    /**
     * The profile id the given manager uses for the pack ("file/"-prefixed
     * or the bare folder name), or null when it was not discovered.
     */
    private static String packId(ResourcePackManager manager) {
        if (manager.hasProfile(PACK_ID)) {
            return PACK_ID;
        }

        return manager.hasProfile(PACK_NAME) ? PACK_NAME : null;
    }

    /** True when the pack is currently among the enabled resource packs. */
    private static boolean isEnabled() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return false;
        }

        String id = packId(mc.getResourcePackManager());

        return id != null && mc.getResourcePackManager().getEnabledIds().contains(id);
    }

    /**
     * Scans the resource pack folders, enables the pack and persists the
     * enabled list to options.txt, then reloads the resources so the edits
     * apply. No-op reload when the pack was already enabled and nothing
     * changed on disk; saving an edit always reloads.
     */
    private static void enablePackAndReload() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        ResourcePackManager manager = mc.getResourcePackManager();
        manager.scanPacks();

        String id = packId(manager);

        if (id == null) {
            LOGGER.warn("[ReignRender] the face-mod resource pack was not discovered by the game");
            return;
        }

        if (!manager.getEnabledIds().contains(id)) {
            manager.enable(id);
            // Persist the enabled state so the pack stays on across restarts
            mc.options.refreshResourcePacks(manager);
        }

        reloadResources();
    }

    /** Removes the pack from the enabled list and persists the change. */
    private static void disablePack() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        ResourcePackManager manager = mc.getResourcePackManager();
        String id = packId(manager);

        if (id != null && manager.disable(id)) {
            mc.options.refreshResourcePacks(manager);
        }
    }

    /** Reloads the game resources (asynchronous, like the F3+T action). */
    private static void reloadResources() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.reloadResources().exceptionally(e -> {
            LOGGER.warn("[ReignRender] face-mod resource reload failed", e);
            return null;
        });
    }
}
