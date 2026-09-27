package com.wdylyh.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wdylyh.RegionFaceBlocks;
import fi.dy.masa.malilib.util.JsonUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Management of the generated region face-mod resource pack ("区域面修改").
 *
 * The pack lives at {@code <game dir>/resourcepacks/ReignRender_RegionFace} and
 * carries ONLY {@code reignrender:} resources under the {@code rface/} folders
 * (no vanilla path overrides), so enabling it changes nothing outside the
 * matched regions — the region render hooks swap the vanilla textures for the
 * {@code reignrender:rface/...} ones exactly where a region entry matches:
 * <pre>
 *   assets/reignrender/blockstates/rface_&lt;name&gt;.json        block shadows
 *   assets/reignrender/models/block/rface/&lt;name&gt;.json       block shadow models
 *   assets/reignrender/models/item/rface/&lt;name&gt;.json        item shadow models
 *   assets/reignrender/textures/rface/block|item|entity|particle/...   edited textures
 * </pre>
 * Blocks and items get remapped shadow models generated from the vanilla ones;
 * entities and particles are pure texture swaps driven by the editor's saved
 * files. The pack is enabled through the game's own {@link ResourcePackManager}
 * while {@link RenderConfig.Hotkeys#TOGGLE_REGION_FACE} is on (and the global
 * face mod is off, see {@link RegionFaceEngine#active()}).
 */
public class RegionFacePacks {

    public static final Logger LOGGER = LoggerFactory.getLogger(RegionFacePacks.class);

    /** Directory name (and resource pack profile id) of the generated pack. */
    public static final String PACK_NAME = "ReignRender_RegionFace";

    private static final String PACK_ID = "file/" + PACK_NAME;

    private RegionFacePacks() {}

    private static Path packDir() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PACK_NAME);
    }

    // ==================== 激活 (Activation) ====================

    /**
     * True when the region face-mod is fully active: the config gate
     * ({@link RegionFaceEngine#active()}: region switch on, global face mod
     * off) holds AND the region pack is actually enabled (so the rface
     * resources are loaded — otherwise a swap would render missing textures).
     */
    public static boolean active() {
        return RegionFaceEngine.active() && isPackActive();
    }

    /**
     * Enables the pack (and reloads) when the region switch turns on, disables
     * it when the switch turns off. Turning it on first generates the shadow
     * resources of the currently listed ids.
     */
    public static void applyEnabled(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        if (!enabled || !RegionFaceEngine.active()) {
            if (isEnabled()) {
                disablePack();
                reloadResources();
            }

            return;
        }

        // No texture edits at all: the pack has nothing to contribute, so do
        // not generate resources or reload (only a pack that is still enabled
        // from an earlier edit session gets disabled once, which is necessary
        // to clear its stale content).
        if (!RegionFaceIndex.hasAnyOverrides()) {
            if (isEnabled()) {
                disablePack();
                reloadResources();
            }

            return;
        }

        ensureResources();
        ensurePack();
        enablePackAndReload();
    }

    /**
     * True when the pack is currently among the enabled resource packs; the
     * render hooks require this so a swap never references unloaded resources.
     */
    public static boolean isPackActive() {
        return isEnabled();
    }

    private static String packId(ResourcePackManager manager) {
        if (manager.hasProfile(PACK_ID)) {
            return PACK_ID;
        }

        return manager.hasProfile(PACK_NAME) ? PACK_NAME : null;
    }

    private static boolean isEnabled() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return false;
        }

        String id = packId(mc.getResourcePackManager());

        return id != null && mc.getResourcePackManager().getEnabledIds().contains(id);
    }

    private static void enablePackAndReload() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        ResourcePackManager manager = mc.getResourcePackManager();
        manager.scanPacks();

        String id = packId(manager);

        if (id == null) {
            LOGGER.warn("[ReignRender] the region face-mod resource pack was not discovered by the game");
            return;
        }

        if (!manager.getEnabledIds().contains(id)) {
            manager.enable(id);
            mc.options.refreshResourcePacks(manager);
        }

        reloadResources();
    }

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
            LOGGER.warn("[ReignRender] region face-mod resource reload failed", e);
            return null;
        });
    }

    /**
     * Regenerates the shadow resources of the currently listed region ids and
     * applies the pack state. Called when the region entries or the master
     * toggles change; a newly listed id only renders with its region textures
     * after this ran (it writes the model/blockstate jsons and then reloads).
     *
     * <p>The reload only happens when it is actually necessary: without any
     * texture edits nothing is generated nor reloaded, and while the pack is
     * already enabled a reload is skipped unless new resources were written.</p>
     */
    public static void onRegionConfigChanged() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return;
        }

        RegionFaceEngine.invalidateCaches();

        if (!RegionFaceEngine.active()) {
            applyEnabled(false);
            return;
        }

        // No texture edits at all: at most a stale enabled pack gets cleared.
        if (!RegionFaceIndex.hasAnyOverrides()) {
            if (isEnabled()) {
                disablePack();
                reloadResources();
            }

            return;
        }

        boolean changed = ensureResources();
        ensurePack();

        // The pack is already enabled and nothing new was written: the loaded
        // resources already contain everything, so no reload is needed.
        if (!changed && isEnabled()) {
            return;
        }

        enablePackAndReload();
    }

    // ==================== 资源生成 (Resource generation) ====================

    /**
     * Creates the pack directory, pack.mcmeta and the atlas source jsons when
     * missing. The atlas sources add the rface block/item textures to the
     * blocks atlas and the rface particle textures to the particles atlas
     * (atlas sources merge across packs). The prefix must repeat the folder
     * name: a directory source builds sprite ids as prefix + path relative to
     * the source folder.
     */
    private static void ensurePack() {
        Path dir = packDir();
        Path meta = dir.resolve("pack.mcmeta");

        if (Files.exists(meta)) {
            return;
        }

        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 94);
        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", 15);
        range.addProperty("max_inclusive", 999);
        pack.add("supported_formats", range);
        pack.addProperty("description", "ReignRender Region Face Mod");

        JsonObject root = new JsonObject();
        root.add("pack", pack);

        try {
            Files.createDirectories(dir);
            JsonUtils.writeJsonToFile(root, meta.toFile());
        } catch (IOException e) {
            LOGGER.warn("[ReignRender] failed to create the region face-mod resource pack metadata", e);
        }

        writeAtlas("blocks", new String[] {"rface/block", "rface/item"});
        writeAtlas("particles", new String[] {"rface/particle"});
    }

    private static void writeAtlas(String atlas, String[] dirs) {
        JsonArray sources = new JsonArray();

        for (String dir : dirs) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "directory");
            source.addProperty("source", dir);
            source.addProperty("prefix", dir + "/");
            sources.add(source);
        }

        JsonObject root = new JsonObject();
        root.add("sources", sources);

        Path f = packDir().resolve("assets/minecraft/atlases/" + atlas + ".json");

        try {
            Files.createDirectories(f.getParent());
            JsonUtils.writeJsonToFile(root, f.toFile());
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the region atlas source {}", atlas, ex);
        }
    }

    /**
     * Generates the block and item shadow resources for the ids of the current
     * region entries. Entities and particles need no generated resources (they
     * are pure texture swaps). Idempotent.
     *
     * <p>Only ids that actually have edited textures are generated for — a
     * listed id without edits renders nothing region-modified anyway (the
     * render hooks check the index), so generating its resources would only
     * produce new pack files and trigger needless reloads. Returns true when
     * any new resource file was written (i.e. a reload could be needed).</p>
     */
    public static boolean ensureResources() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return false;
        }

        boolean changed = false;

        for (RegionFaceEngine.Id id : RegionFaceEngine.listedIds()) {
            if (id.block() && RegionFaceIndex.hasOverrides(RegionFaceIndex.BLOCKS, id.id())) {
                changed |= ensureRegionBlockResources(mc.getResourceManager(), id.id());
            }

            if (id.item() && RegionFaceIndex.hasOverrides(RegionFaceIndex.ITEMS, id.id())) {
                changed |= ensureRegionItemModel(mc.getResourceManager(), id.id());
            }
        }

        return changed;
    }

    /**
     * Generates one block shadow: the vanilla blockstate json with model refs
     * remapped to {@code reignrender:block/rface/...}, those models with
     * texture refs remapped to {@code reignrender:rface/block/...} and a
     * default copy of every referenced vanilla block texture.
     */
    private static boolean ensureRegionBlockResources(ResourceManager rm, String blockId) {
        String vn = normalizeId(blockId);
        String ns = vn.substring(0, vn.indexOf(':'));
        String shortName = vn.substring(vn.indexOf(':') + 1);
        // Non-minecraft blocks keep their namespace in the flat file name so
        // the layout stays inside reignrender:.
        String name = ns.equals("minecraft") ? shortName : ns + "_" + shortName;

        Block vanilla = Registries.BLOCK.get(Identifier.of(vn));

        if (vanilla == null || vanilla == net.minecraft.block.Blocks.AIR) {
            return false;
        }

        Path bsFile = packDir().resolve("assets/reignrender/blockstates/rface_" + name + ".json");

        if (Files.isRegularFile(bsFile)) {
            return false;
        }

        JsonObject vbs = readJson(rm, Identifier.of(ns, "blockstates/" + shortName + ".json"));

        if (vbs == null) {
            LOGGER.warn("[ReignRender] no vanilla blockstate for the region shadow {}", name);
            return false;
        }

        List<String> models = new ArrayList<>();
        remapBlockstate(vbs, models);

        try {
            Files.createDirectories(bsFile.getParent());
            JsonUtils.writeJsonToFile(vbs, bsFile.toFile());
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the region shadow blockstate {}", name, ex);
            return false;
        }

        boolean changed = true;

        for (String model : models) {
            changed |= ensureRegionBlockModel(rm, model);
        }

        return changed;
    }

    /** Remaps every "model" ref inside the blockstate to a region shadow model and collects the model names. */
    private static void remapBlockstate(JsonElement el, List<String> models) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();

            if (obj.has("model") && obj.get("model").isJsonPrimitive()) {
                String ref = obj.get("model").getAsString();
                String remapped = remapToRegionModel(ref, models);

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

    /** "minecraft:block/sand" or "block/sand" -> "reignrender:block/rface/sand"; returns null for other refs. */
    private static String remapToRegionModel(String ref, List<String> models) {
        String r = ref;

        if (r.startsWith("minecraft:")) {
            r = r.substring("minecraft:".length());
        }

        if (!r.startsWith("block/")) {
            return null;
        }

        String base = r.substring("block/".length());
        models.add(base);

        return "reignrender:block/rface/" + base;
    }

    /**
     * Copies the vanilla model json into the pack with its texture refs
     * remapped to {@code reignrender:rface/block/...} and copies every
     * referenced vanilla block texture as the editable default.
     */
    private static boolean ensureRegionBlockModel(ResourceManager rm, String base) {
        Path mf = packDir().resolve("assets/reignrender/models/block/rface/" + base + ".json");

        if (Files.isRegularFile(mf)) {
            return false;
        }

        List<String> textures = new ArrayList<>();
        JsonObject model = readJson(rm, Identifier.of("minecraft", "models/block/" + base + ".json"));

        if (model == null || !remapModelTextures(model, textures)) {
            // Fall back to a plain cube so the shadow still renders.
            model = new JsonObject();
            model.addProperty("parent", "minecraft:block/cube_all");
            JsonObject tex = new JsonObject();
            tex.addProperty("all", "reignrender:rface/block/" + base);
            tex.addProperty("particle", "reignrender:rface/block/" + base);
            model.add("textures", tex);
            textures.add(base);
        }

        try {
            Files.createDirectories(mf.getParent());
            JsonUtils.writeJsonToFile(model, mf.toFile());
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the region shadow model {}", base, ex);
            return false;
        }

        boolean changed = false;

        for (String tex : textures) {
            changed |= copyVanillaTexture(rm, Identifier.of("minecraft", "textures/block/" + tex + ".png"),
                    packDir().resolve("assets/reignrender/textures/rface/block/" + tex + ".png"));
        }

        return changed;
    }

    /**
     * Rewrites the texture refs of a block model to the region textures
     * ({@code reignrender:rface/block/...}). Returns false when the model
     * references no block textures at all.
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
                tex.addProperty(entry.getKey(), "reignrender:rface/block/" + sub);
                textures.add(sub);
                any = true;
            } else {
                tex.addProperty(entry.getKey(), ref); // keep non-block refs (e.g. item textures)
            }
        }

        return any;
    }

    /**
     * Generates one region item shadow model: the vanilla item model (or the
     * model its 1.21.4+ item definition references, or the first parent in the
     * chain that defines textures, or a flat item/generated fallback) with
     * texture refs remapped to {@code reignrender:rface/item/...} and a
     * default copy of every referenced vanilla texture.
     */
    private static boolean ensureRegionItemModel(ResourceManager rm, String itemId) {
        String norm = normalizeId(itemId);
        String ns = norm.substring(0, norm.indexOf(':'));
        String shortName = norm.substring(norm.indexOf(':') + 1);
        // The model id keeps the namespace in the path for non-minecraft items.
        String modelName = norm.startsWith("minecraft:") ? shortName : norm.replace(':', '_');
        Path mf = packDir().resolve("assets/reignrender/models/item/rface/" + modelName + ".json");

        List<Identifier> textures = new ArrayList<>();
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

        // Remap the model's own texture refs, then merge in the remapped
        // texture keys of EVERY model along the parent chain. Keeping the
        // original parent preserves the 3D block geometry, but deeper parent
        // models may define extra texture keys that still point at the vanilla
        // block textures — the block category edits override exactly those, so
        // every key must get its own region copy under reignrender:rface/item/.
        boolean inline = false;

        if (model != null && remapItemModelTextures(model, ns, textures)) {
            inline = true;
        }

        if (model != null && model.has("parent") && model.get("parent").isJsonPrimitive()) {
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

                        String remapped = remapTexRef(entry.getValue().getAsString(), pns, textures);

                        if (remapped != null) {
                            tex.addProperty(entry.getKey(), remapped);
                            inline = true;
                        }
                    }
                }

                parentRef = pj.has("parent") && pj.get("parent").isJsonPrimitive()
                        ? pj.get("parent").getAsString() : null;
            }
        }

        if (!inline) {
            // Flat item fallback: point layer0 at the copy of the vanilla item
            // texture, or the block texture when the item has none of its own.
            // The sprite ref must use the same sub path as the copied file.
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
                src = Identifier.of(ns, "textures/item/" + shortName + ".png");
                sub = "item/" + shortName;
            }

            model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject tex = new JsonObject();
            tex.addProperty("layer0", "reignrender:rface/item/" + sub);
            model.add("textures", tex);
            textures.add(src);
        }

        // Always recompute and rewrite the model json: an older generated model
        // may still hold stale vanilla texture refs, and skipping the rewrite
        // whenever the file exists would repair them never. The reload is only
        // forced when the content actually changed or a texture copy is new.
        boolean changed = false;

        try {
            Files.createDirectories(mf.getParent());
            JsonObject old = readLocalJson(mf);

            if (old == null || !old.equals(model)) {
                JsonUtils.writeJsonToFile(model, mf.toFile());
                changed = true;
            }
        } catch (IOException ex) {
            LOGGER.warn("[ReignRender] failed to write the region item model {}", modelName, ex);
            return false;
        }

        for (Identifier vanillaTex : textures) {
            changed |= copyVanillaTexture(rm, vanillaTex, packDir()
                    .resolve("assets/reignrender/textures/rface/item/" + vanillaTex.getPath()
                            .substring("textures/".length())));
        }

        return changed;
    }

    /** Reads a local json file of the pack dir; null when missing or malformed. */
    private static JsonObject readLocalJson(Path f) {
        if (!Files.isRegularFile(f)) {
            return null;
        }

        try {
            JsonElement el = JsonUtils.parseJsonFile(f.toFile());

            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Remaps a single texture reference to {@code reignrender:rface/item/...}
     * and collects the vanilla source texture to copy. Returns null for
     * variable refs ({@code #key}) and refs outside the item/block/entity
     * texture folders.
     */
    private static String remapTexRef(String ref, String modelNs, List<Identifier> textures) {
        if (ref.startsWith("#")) {
            return null;
        }

        String r = ref;
        String refNs = modelNs;

        if (r.startsWith("minecraft:")) {
            refNs = "minecraft";
            r = r.substring("minecraft:".length());
        }

        if (r.startsWith("item/") || r.startsWith("block/") || r.startsWith("entity/")) {
            textures.add(Identifier.of(refNs, "textures/" + r + ".png"));
            return "reignrender:rface/item/" + r;
        }

        return null;
    }

    /**
     * Rewrites the model texture refs ({@code item/...}, {@code block/...},
     * {@code entity/...}) to {@code reignrender:rface/item/...} and collects
     * the vanilla source textures to copy. Returns false when no texture could
     * be remapped.
     */
    private static boolean remapItemModelTextures(JsonObject model, String modelNs, List<Identifier> textures) {
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

            String r = ref;
            String refNs = modelNs;

            if (r.startsWith("minecraft:")) {
                refNs = "minecraft";
                r = r.substring("minecraft:".length());
            }

            if (r.startsWith("item/") || r.startsWith("block/") || r.startsWith("entity/")) {
                Identifier vanillaTex = Identifier.of(refNs, "textures/" + r + ".png");
                tex.addProperty(entry.getKey(), "reignrender:rface/item/" + r);
                textures.add(vanillaTex);
                any = true;
            } else {
                tex.addProperty(entry.getKey(), ref);
            }
        }

        return any;
    }

    /** "name" -> "minecraft:name"; returns null for malformed ids. */
    private static String normalizeId(String sid) {
        if (sid == null || sid.isEmpty()) {
            return null;
        }

        String s = sid.toLowerCase(Locale.ROOT);

        return s.contains(":") ? s : "minecraft:" + s;
    }

    /** The region item shadow model identifiers needed by the current region entries. */
    public static List<Identifier> regionItemModelIds() {
        List<Identifier> ids = new ArrayList<>();

        for (RegionFaceEngine.Id id : RegionFaceEngine.listedIds()) {
            if (!id.item()) {
                continue;
            }

            Identifier mid = regionItemModelId(id.id());

            if (mid != null) {
                ids.add(mid);
            }
        }

        return ids;
    }

    /**
     * The region item shadow model id of one item
     * ({@code reignrender:item/rface/<name>}), or null for malformed ids.
     */
    public static Identifier regionItemModelId(String itemId) {
        String norm = normalizeId(itemId);

        if (norm == null) {
            return null;
        }

        String modelName = norm.startsWith("minecraft:")
                ? norm.substring(norm.indexOf(':') + 1) : norm.replace(':', '_');

        return Identifier.of("reignrender", "item/rface/" + modelName);
    }

    // ==================== 编辑器存取 (Editor io) ====================

    /** Converts a fully namespaced texture path into the file inside the pack, or null. */
    private static Path textureFile(String texturePath) {
        Identifier id = Identifier.tryParse(texturePath);

        if (id == null) {
            return null;
        }

        return packDir().resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
    }

    /** Reads the edited texture from the region pack, or null when not modified. */
    public static NativeImage readModified(String texturePath) {
        Path file = textureFile(texturePath);

        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }

        try {
            return NativeImage.read(Files.newInputStream(file));
        } catch (Exception e) {
            LOGGER.warn("[ReignRender] failed to read the region-modified texture {}", texturePath, e);
            return null;
        }
    }

    /**
     * Writes the edited texture into the region pack, ensures the pack state
     * and reloads the resources, so the region edit applies immediately.
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
            LOGGER.warn("[ReignRender] failed to save the region-modified texture {}", texturePath, e);
            return;
        }

        ensureResources();
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
                LOGGER.warn("[ReignRender] failed to delete the region-modified texture {}", texturePath, e);
            }
        }

        if (removed && isEnabled()) {
            reloadResources();
        }
    }

    // ==================== 粒子贴图 (Particle sprites) ====================

    /**
     * Resolves the region sprite of an edited particle texture path
     * ({@code "reignrender:textures/rface/particle/generic_0"}) from the
     * particles atlas, or null when the sprite is not stitched (yet).
     */
    public static Sprite particleSprite(String savePath) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            return null;
        }

        Identifier id = Identifier.tryParse(savePath);

        if (id == null) {
            return null;
        }

        // "reignrender:textures/rface/particle/generic_0" -> sprite
        // "reignrender:rface/particle/generic_0" (the atlas prefix repeats the
        // folder name, so the texture path maps 1:1 after "textures/").
        String p = id.getPath();

        if (!p.startsWith("textures/")) {
            return null;
        }

        Identifier spriteId = Identifier.of(id.getNamespace(), p.substring("textures/".length(), p.length() - ".png".length()));
        AtlasManager atlas = mc.getAtlasManager();
        Sprite sprite = atlas.getSprite(new SpriteIdentifier(net.minecraft.util.Atlases.PARTICLES, spriteId));

        return sprite != null ? sprite : atlas
                .getSprite(new SpriteIdentifier(net.minecraft.util.Atlases.BLOCKS, spriteId));
    }

    /** Reads a json resource from the given manager, or null when missing/invalid. */
    private static JsonObject readJson(ResourceManager rm, Identifier id) {
        try (Reader reader = new InputStreamReader(rm.getResourceOrThrow(id).getInputStream(), StandardCharsets.UTF_8)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Copies a vanilla png resource to the given target path (idempotent). */
    private static boolean copyVanillaTexture(ResourceManager rm, Identifier vanillaTex, Path target) {
        if (Files.isRegularFile(target)) {
            return false;
        }

        try {
            // Read the ORIGINAL (lowest-priority resource): the face-mod pack
            // overrides vanilla paths, and a copy taken from that override
            // would bake the global block edit into the region copy.
            java.util.List<Resource> all = rm.getAllResources(vanillaTex);
            NativeImage img = NativeImage.read(all.get(all.size() - 1).getInputStream());

            try {
                Files.createDirectories(target.getParent());
                img.writeTo(target);
            } finally {
                img.close();
            }

            return true;
        } catch (Exception ex) {
            LOGGER.warn("[ReignRender] failed to copy the default region texture {}", vanillaTex, ex);
            return false;
        }
    }
}
