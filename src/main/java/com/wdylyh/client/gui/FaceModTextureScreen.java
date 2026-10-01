package com.wdylyh.client.gui;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdylyh.config.FaceModIndex;
import com.wdylyh.config.FaceModPacks;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Texture list screen for one listed face-mod id: enumerates every texture
 * the resource packs provide under the category's folder (plus the already
 * modified ones from the face-mod pack), each with an edit and a reset
 * button. Editing opens the pixel editor seeded with the current look of that
 * texture (the edited pack png when one exists, the original resource
 * otherwise).
 */
public class FaceModTextureScreen extends GuiBase
{
    private final FaceModListScreen.FaceKind kind;
    private final String id;

    private final List<String> paths = new ArrayList<>();
    private final List<String> matched = new ArrayList<>();
    private final List<String> modified = new ArrayList<>();
    private String query = "";
    private boolean showAll;
    private int scrollOffset;

    public FaceModTextureScreen(FaceModListScreen.FaceKind kind, String id)
    {
        this.kind = kind;
        this.id = id;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        // The region categories come from the condition entry list screen
        // instead of a per-category id list screen.
        this.setParent(this.kind.isRegion()
                ? new ConditionListScreen() : new FaceModListScreen(this.kind));
        this.setTitle(StringUtils.translate("reignrender.gui.face.texlist.title",
                StringUtils.translate(this.kind.getTitleKey()), this.id));
        this.scrollOffset = 0;

        // Search box for live filtering of the texture path list
        GuiTextFieldGeneric sf = new GuiTextFieldGeneric(20, 26, 180, 16, this.textRenderer);
        sf.setPlaceholder(Text.translatable("reignrender.gui.filter.search"));
        this.addTextField(sf, this::onSearch);

        this.rebuild();
    }

    private boolean onSearch(GuiTextFieldGeneric tf)
    {
        this.query = tf.getText();
        this.scrollOffset = 0;
        this.rebuild();
        return true;
    }

    private int listH()
    {
        return Math.max(0, this.height - ReplaceListScreen.LTOP - 12);
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double ha, double va)
    {
        if (GuiBase.isMouseOver((int) mx, (int) my, 20, ReplaceListScreen.LTOP, this.width - 40, this.listH()))
        {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) va * ReplaceListScreen.LH);
            this.rebuild();
            return true;
        }

        return super.onMouseScrolled(mx, my, ha, va);
    }

    /** Path fragments per HUD element id, matched against textures/gui/sprites paths. */
    private static final Map<String, List<String>> HUD_TEX = Map.ofEntries(
            // 1.21.11: boss bar sprites live in the "boss_bar" sprite group
            Map.entry("bossbar", List.of("boss_bar")),
            Map.entry("subtitles", List.of("subtitles")),
            Map.entry("chat", List.of("chat")),
            // status icons: hud/effect_background*.png plus the effect icons
            // themselves in textures/mob_effect/<effect>.png
            Map.entry("statusEffects", List.of("effect", "mob_effect")),
            Map.entry("crosshair", List.of("crosshair", "attack_indicator")),
            Map.entry("hotbar", List.of("hotbar")),
            Map.entry("overlayMessage", List.of("overlay_message")),
            Map.entry("title", List.of("title")),
            Map.entry("scoreboard", List.of("scoreboard")),
            Map.entry("playerList", List.of("player_list")),
            // demo screen background is at textures/gui/demo_background.png
            Map.entry("demoTimer", List.of("demo")),
            Map.entry("heldItemTooltip", List.of("held_item_tooltip")),
            Map.entry("fire", List.of()), // first-person fire overlay uses block-atlas sprites
            // vignette/nausea are full-screen textures/misc/ overlays
            Map.entry("nausea", List.of("nausea")),
            Map.entry("vignette", List.of("vignette")));

    /** Extra path fragments per block entity type id (beyond the plain name match). */
    private static final Map<String, List<String>> BE_TEX = Map.of(
            "sign", List.of("signs"),
            "banner", List.of("banners"),
            "shulker_box", List.of("shulker"),
            "chest", List.of("chest"),
            "ender_chest", List.of("chest"),
            "trapped_chest", List.of("chest"),
            "skull", List.of("player", "skeleton", "zombie", "creeper", "dragon", "piglin"));

    /** Block entity id suffix -> shared family folder: white_bed -> entity/bed/white.png. */
    private static final Map<String, String> BE_SUFFIX_DIR = Map.of(
            "_bed", "bed",
            "_banner", "banner",
            "_sign", "sign",
            "_hanging_sign", "hanging_sign",
            "_shulker_box", "shulker");

    /** Armor item id suffix -> equipment material ("iron_helmet" -> "iron"). */
    private static final List<String> ARMOR_SUFFIXES =
            List.of("_helmet", "_chestplate", "_leggings", "_boots");

    /** Extra environment fragments for the fixed sky id. */
    private static final List<String> SKY_TEX = List.of("sun", "moon");

    /**
     * True when the texture belongs to the listed id. Block-like and item-like
     * categories first try the exact resolution: the blockstate / item model
     * json determines which textures the id really uses, so e.g. minecraft:stone
     * only lists stone.png and not end_stone / mossy_stone. When no json can be
     * resolved (or for name-driven categories), the strict name match below is
     * the fallback.
     */
    static boolean matches(FaceModListScreen.FaceKind kind, String id, Identifier tex,
                           Set<String> resolved)
    {
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String shortId = (c < 0 ? id : id.substring(c + 1)).toLowerCase(Locale.ROOT);

        // Shadow categories (falling blocks, held items, dropped items) list
        // and edit the vanilla textures like the plain categories; only the
        // save target differs (shadowSavePath), so matching is the same.
        if (!tex.getNamespace().equals(ns))
        {
            return false;
        }

        String rel = tex.getPath().toLowerCase(Locale.ROOT);

        // Exact textures from the model json (blocks, fluids, falling blocks,
        // items, particles): these win over any name-based heuristic.
        if (resolved.contains(tex.toString()))
        {
            return true;
        }

        if (kind == FaceModListScreen.FaceKind.HUD_ELEMENTS)
        {
            return HUD_TEX.getOrDefault(shortId, List.of()).stream().anyMatch(k -> wordMatch(rel, k));
        }

        if (kind == FaceModListScreen.FaceKind.BLOCK_ENTITIES)
        {
            for (String k : BE_TEX.getOrDefault(shortId, List.of()))
            {
                if (wordMatch(rel, k))
                {
                    return true;
                }
            }

            // Color variants whose textures sit in a shared family folder and
            // carry only the color as file name: white_bed -> entity/bed/white.png,
            // purple_shulker_box -> entity/shulker/purple.png, oak_sign ->
            // entity/signs/oak.png. The folder must match the family and the
            // file name the color/wood prefix.
            for (Map.Entry<String, String> e : BE_SUFFIX_DIR.entrySet())
            {
                if (shortId.endsWith(e.getKey()) && rel.contains("/" + e.getValue()))
                {
                    String color = shortId.substring(0, shortId.length() - e.getKey().length());

                    if (fileNameOf(rel).startsWith(color))
                    {
                        return true;
                    }
                }
            }
        }
        else if (kind == FaceModListScreen.FaceKind.SKY)
        {
            for (String k : SKY_TEX)
            {
                if (wordMatch(rel, k))
                {
                    return true;
                }
            }
        }
        else if (kind == FaceModListScreen.FaceKind.PARTICLES
                || kind == FaceModListScreen.FaceKind.R_PARTICLES)
        {
            // Alias fragment table for particle ids whose descriptor json is
            // missing (or lists nothing): poof -> generic_*, cloud -> generic_*.
            for (String k : PARTICLE_TEX.getOrDefault(shortId, List.of()))
            {
                if (wordMatch(rel, k))
                {
                    return true;
                }
            }
        }

        if (kind == FaceModListScreen.FaceKind.ARMOR || kind == FaceModListScreen.FaceKind.ELYTRA)
        {
            // Fallback when the equipment json could not be resolved: the
            // equipment textures carry the material name ("iron_helmet" ->
            // humanoid/iron.png).
            return nameMatch(rel, armorMaterial(shortId));
        }

        return nameMatch(rel, shortId)
                || entityFolderMatch(kind, rel, shortId);
    }

    /** Strips the armor slot suffix: "iron_helmet" -> "iron", "elytra" -> "elytra". */
    private static String armorMaterial(String shortId)
    {
        for (String s : ARMOR_SUFFIXES)
        {
            if (shortId.endsWith(s))
            {
                return shortId.substring(0, shortId.length() - s.length());
            }
        }

        return shortId;
    }

    /** The file name part of a resource path, without the ".png" suffix. */
    private static String fileNameOf(String rel)
    {
        int slash = rel.lastIndexOf('/');
        String file = slash < 0 ? rel : rel.substring(slash + 1);

        return file.endsWith(".png") ? file.substring(0, file.length() - 4) : file;
    }

    /** Known texture name fragments for particle ids whose descriptor json is
     * missing or does not reference id-named files (poof is the "explode"
     * alias and uses the generic_* sheets like cloud). */
    private static final Map<String, List<String>> PARTICLE_TEX = Map.ofEntries(
            Map.entry("poof", List.of("generic")),
            Map.entry("cloud", List.of("generic")),
            Map.entry("sculk_soul", List.of("sculk_soul")));

    /**
     * Textures that live in a dedicated folder named after the id
     * (cat -> entity/cat/tabby.png, chest -> entity/chest/normal.png) are
     * matched by that folder segment, but only when the file name itself is
     * not another registered id of the same registry (cow must not list
     * cow/mooshroom.png - mooshroom has its own id).
     */
    private static boolean entityFolderMatch(FaceModListScreen.FaceKind kind, String rel,
                                             String shortId)
    {
        boolean entity = kind == FaceModListScreen.FaceKind.ENTITIES
                || kind == FaceModListScreen.FaceKind.R_ENTITIES;
        boolean be = kind == FaceModListScreen.FaceKind.BLOCK_ENTITIES;

        if (!entity && !be)
        {
            return false;
        }

        int slash = rel.lastIndexOf('/');
        String file = slash < 0 ? rel : rel.substring(slash + 1);

        if (file.endsWith(".png"))
        {
            file = file.substring(0, file.length() - 4);
        }

        boolean taken = entity
                ? net.minecraft.registry.Registries.ENTITY_TYPE.containsId(Identifier.ofVanilla(file))
                : net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE.containsId(Identifier.ofVanilla(file));

        if (taken)
        {
            return false;
        }

        String dir = slash < 0 ? "" : rel.substring(0, slash);

        for (String seg : dir.split("/"))
        {
            if (seg.equals(shortId))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Strict file-name match: the needle must be the leading segment of the
     * file name (stone.png, stone_bricks.png), never a later fragment
     * (end_stone.png, mossy_stone.png do not match "stone").
     */
    private static boolean nameMatch(String rel, String needle)
    {
        if (needle.isEmpty())
        {
            return false;
        }

        int slash = rel.lastIndexOf('/');
        String file = slash < 0 ? rel : rel.substring(slash + 1);

        if (!file.startsWith(needle))
        {
            return false;
        }

        return file.length() == needle.length()
                || file.charAt(needle.length()) == '_'
                || file.charAt(needle.length()) == '.';
    }

    /**
     * Word-boundary containment: the needle must start and end at a string
     * edge or at a '_', '/' separator ('.' also counts as an end boundary for
     * the ".png" suffix), so "stone" matches stone.png but not stonecutter.png
     * or redstone_ore.png.
     */
    private static boolean wordMatch(String hay, String needle)
    {
        if (needle.isEmpty())
        {
            return false;
        }

        int i = hay.indexOf(needle);

        while (i >= 0)
        {
            char l = i > 0 ? hay.charAt(i - 1) : '_';
            char r = i + needle.length() < hay.length() ? hay.charAt(i + needle.length()) : '_';

            if ((l == '_' || l == '/') && (r == '_' || r == '/' || r == '.'))
            {
                return true;
            }

            i = hay.indexOf(needle, i + 1);
        }

        return false;
    }

    /**
     * Reads the particle descriptor json (assets/&lt;ns&gt;/particles/&lt;id&gt;.json)
     * and returns its texture list as full paths, since particle texture file
     * names often do not contain the particle id (cloud -&gt; generic_*).
     * In 1.21.11 the descriptor references files relative to textures/particle/
     * ("minecraft:generic_0"); full "textures/..." forms are tolerated too.
     */
    private static Set<String> particleJsonTextures(String id)
    {
        Set<String> out = new HashSet<>();
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String shortId = (c < 0 ? id : id.substring(c + 1)).toLowerCase(Locale.ROOT);

        try
        {
            Resource res = MinecraftClient.getInstance().getResourceManager()
                    .getResourceOrThrow(Identifier.of(ns, "particles/" + shortId + ".json"));

            try (Reader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))
            {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();

                if (obj.has("textures") && obj.get("textures").isJsonArray())
                {
                    for (JsonElement e : obj.getAsJsonArray("textures"))
                    {
                        String v = e.getAsString();
                        int cc = v.indexOf(':');
                        String vns = cc < 0 ? ns : v.substring(0, cc);
                        String name = cc < 0 ? v : v.substring(cc + 1);

                        if (name.startsWith("textures/"))
                        {
                            out.add(vns + ":" + name + ".png");
                        }
                        else if (name.startsWith("particle/"))
                        {
                            out.add(vns + ":textures/" + name + ".png");
                        }
                        else
                        {
                            out.add(vns + ":textures/particle/" + name + ".png");
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // no descriptor json for this particle; plain name matching still applies
        }

        return out;
    }

    /** Small json reader over the resource manager (null when absent). */
    private static JsonObject readJson(Identifier id)
    {
        try
        {
            Resource res = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(id);

            try (Reader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))
            {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** Strips "minecraft:" / keeps "block/stone" style refs normalized. */
    private static String normalizeRef(String ref, String fallbackNs)
    {
        String v = ref;

        if (v.startsWith(fallbackNs + ":"))
        {
            v = v.substring(fallbackNs.length() + 1);
        }
        else if (v.contains(":"))
        {
            int i = v.indexOf(':');
            fallbackNs = v.substring(0, i);
            v = v.substring(i + 1);
        }

        return fallbackNs + ":" + v;
    }

    /** Turns a "block/stone" model texture value into a full texture path. */
    private static void addTextureRef(String value, Set<String> out)
    {
        if (value == null || value.isEmpty() || value.startsWith("#"))
        {
            return;
        }

        String n = normalizeRef(value, "minecraft");
        int i = n.indexOf(':');

        if (!n.substring(i + 1).startsWith("textures/"))
        {
            n = n.substring(0, i + 1) + "textures/" + n.substring(i + 1);
        }

        if (!n.endsWith(".png"))
        {
            n = n + ".png";
        }

        out.add(n);
    }

    /** Walks a model json: collects its texture refs and follows the parent chain. */
    private static void collectModelTextures(String modelRef, Set<String> out, Set<String> visited)
    {
        String n = normalizeRef(modelRef, "minecraft");

        if (!visited.add(n) || visited.size() > 64)
        {
            return;
        }

        int i = n.indexOf(':');
        JsonObject model = readJson(Identifier.of(n.substring(0, i), "models/" + n.substring(i + 1) + ".json"));

        if (model == null)
        {
            return;
        }

        if (model.has("textures") && model.get("textures").isJsonObject())
        {
            for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet())
            {
                if (e.getValue().isJsonPrimitive())
                {
                    addTextureRef(e.getValue().getAsString(), out);
                }
            }
        }

        if (model.has("parent") && model.get("parent").isJsonPrimitive())
        {
            collectModelTextures(model.get("parent").getAsString(), out, visited);
        }
    }

    /**
     * Collects every model referenced by a blockstate json (variants values
     * and multipart apply entries).
     */
    private static void collectBlockstateModels(JsonObject bs, Set<String> models)
    {
        if (bs.has("variants") && bs.get("variants").isJsonObject())
        {
            for (JsonElement v : bs.getAsJsonObject("variants").asMap().values())
            {
                if (v.isJsonObject())
                {
                    JsonObject o = v.getAsJsonObject();

                    if (o.has("model") && o.get("model").isJsonPrimitive())
                    {
                        models.add(o.get("model").getAsString());
                    }
                }
                else if (v.isJsonArray())
                {
                    for (JsonElement e : v.getAsJsonArray())
                    {
                        if (e.isJsonObject() && e.getAsJsonObject().has("model")
                                && e.getAsJsonObject().get("model").isJsonPrimitive())
                        {
                            models.add(e.getAsJsonObject().get("model").getAsString());
                        }
                    }
                }
            }
        }

        if (bs.has("multipart") && bs.get("multipart").isJsonArray())
        {
            for (JsonElement e : bs.getAsJsonArray("multipart"))
            {
                if (!e.isJsonObject())
                {
                    continue;
                }

                JsonObject o = e.getAsJsonObject();

                if (!o.has("apply"))
                {
                    continue;
                }

                JsonElement ap = o.get("apply");

                if (ap.isJsonObject() && ap.getAsJsonObject().has("model")
                        && ap.getAsJsonObject().get("model").isJsonPrimitive())
                {
                    models.add(ap.getAsJsonObject().get("model").getAsString());
                }
                else if (ap.isJsonArray())
                {
                    for (JsonElement a : ap.getAsJsonArray())
                    {
                        if (a.isJsonObject() && a.getAsJsonObject().has("model")
                                && a.getAsJsonObject().get("model").isJsonPrimitive())
                        {
                            models.add(a.getAsJsonObject().get("model").getAsString());
                        }
                    }
                }
            }
        }
    }

    /**
     * Exact texture set of a block-like id: blockstate json -> referenced
     * models -> all texture refs along the parent chains. Empty when the
     * blockstate cannot be read (then name matching is the fallback).
     */
    private static Set<String> blockModelTextures(String id)
    {
        Set<String> out = new HashSet<>();
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String shortId = (c < 0 ? id : id.substring(c + 1)).toLowerCase(Locale.ROOT);

        JsonObject bs = readJson(Identifier.of(ns, "blockstates/" + shortId + ".json"));

        if (bs == null)
        {
            return out;
        }

        Set<String> models = new HashSet<>();
        collectBlockstateModels(bs, models);

        Set<String> visited = new HashSet<>();

        for (String m : models)
        {
            collectModelTextures(m, out, visited);
        }

        return out;
    }

    /**
     * Exact texture set of an item-like id: models/item/<id>.json, or the
     * 1.21.4+ items/<id>.json definition pointing at any model (anvil ->
     * minecraft:block/anvil); every nested "model" string of a non-"model"
     * definition type (selector/condition) is followed too.
     */
    private static Set<String> itemModelTextures(String id)
    {
        Set<String> out = new HashSet<>();
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String shortId = (c < 0 ? id : id.substring(c + 1)).toLowerCase(Locale.ROOT);

        Set<String> refs = new HashSet<>();
        JsonObject model = readJson(Identifier.of(ns, "models/item/" + shortId + ".json"));

        if (model != null)
        {
            refs.add(normalizeRef("item/" + shortId, ns));
        }
        else
        {
            JsonObject def = readJson(Identifier.of(ns, "items/" + shortId + ".json"));

            if (def != null)
            {
                collectModelRefs(def, refs);
            }
        }

        Set<String> visited = new HashSet<>();

        for (String r : refs)
        {
            collectModelTextures(r, out, visited);
        }

        return out;
    }

    /** Finds every string under a "model" key inside a json tree. */
    private static void collectModelRefs(JsonElement el, Set<String> out)
    {
        if (el.isJsonObject())
        {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet())
            {
                if (e.getKey().equals("model") && e.getValue().isJsonPrimitive())
                {
                    out.add(normalizeRef(e.getValue().getAsString(), "minecraft"));
                }
                else
                {
                    collectModelRefs(e.getValue(), out);
                }
            }
        }
        else if (el.isJsonArray())
        {
            for (JsonElement e : el.getAsJsonArray())
            {
                collectModelRefs(e, out);
            }
        }
    }

    /**
     * Exact texture set of an armor/elytra id: the equipment asset json
     * (assets/&lt;ns&gt;/equipment/&lt;material&gt;.json) defines per-layer
     * texture refs ("minecraft:iron" under "humanoid"), which resolve to
     * textures/entity/equipment/&lt;layer&gt;/&lt;material&gt;.png. Empty when
     * the json is missing (then the material name fallback applies).
     */
    private static Set<String> armorModelTextures(String id)
    {
        Set<String> out = new HashSet<>();
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String material = armorMaterial((c < 0 ? id : id.substring(c + 1)).toLowerCase(Locale.ROOT));

        JsonObject eq = readJson(Identifier.of(ns, "equipment/" + material + ".json"));

        if (eq == null || !eq.has("layers") || !eq.get("layers").isJsonObject())
        {
            return out;
        }

        for (Map.Entry<String, JsonElement> layer : eq.getAsJsonObject("layers").entrySet())
        {
            if (!layer.getValue().isJsonArray())
            {
                continue;
            }

            for (JsonElement item : layer.getValue().getAsJsonArray())
            {
                if (!item.isJsonObject() || !item.getAsJsonObject().has("texture")
                        || !item.getAsJsonObject().get("texture").isJsonPrimitive())
                {
                    continue;
                }

                // "minecraft:iron" (or "iron") under layer "humanoid" ->
                // minecraft:textures/entity/equipment/humanoid/iron.png
                String v = item.getAsJsonObject().get("texture").getAsString();
                int vi = v.indexOf(':');
                String tns = vi < 0 ? ns : v.substring(0, vi);
                String tex = vi < 0 ? v : v.substring(vi + 1);

                out.add(tns + ":textures/entity/equipment/" + layer.getKey() + "/" + tex + ".png");
            }
        }

        return out;
    }

    /** Resolves the exact texture set for the category kinds that have a json source. */
    private Set<String> resolvedTextures()
    {
        FaceModListScreen.FaceKind k = this.kind;

        if (k == FaceModListScreen.FaceKind.BLOCKS
                || k == FaceModListScreen.FaceKind.FLUIDS
                || k == FaceModListScreen.FaceKind.FALLING_BLOCKS
                || k == FaceModListScreen.FaceKind.R_BLOCKS)
        {
            return blockModelTextures(this.id);
        }

        if (k == FaceModListScreen.FaceKind.HELD_ITEMS
                || k == FaceModListScreen.FaceKind.ITEM_ENTITIES
                || k == FaceModListScreen.FaceKind.R_ITEMS)
        {
            return itemModelTextures(this.id);
        }

        if (k == FaceModListScreen.FaceKind.ARMOR || k == FaceModListScreen.FaceKind.ELYTRA)
        {
            return armorModelTextures(this.id);
        }

        return Set.of();
    }

    /**
     * Collects the textures of this category's folder once: the full list plus
     * the subset plausibly belonging to the listed id. Textures already
     * modified for this id are always included in the display.
     */
    private void collect()
    {
        this.paths.clear();
        this.matched.clear();
        this.modified.clear();

        Set<String> json = this.kind == FaceModListScreen.FaceKind.PARTICLES
                        || this.kind == FaceModListScreen.FaceKind.R_PARTICLES
                ? particleJsonTextures(this.id) : Set.of();

        // Exact textures resolved from the blockstate / item model json for
        // the block- and item-like categories (empty for the name-matched ones).
        Set<String> resolved = this.kind == FaceModListScreen.FaceKind.PARTICLES
                        || this.kind == FaceModListScreen.FaceKind.R_PARTICLES
                ? Set.of() : resolvedTextures();

        // Shadow categories list the vanilla textures of the id (falling
        // blocks use the block textures, items the item and block textures);
        // an edit is then saved under the shadow path instead. The plain
        // categories enumerate their own resource folder. The region
        // categories enumerate the vanilla folders like the shadow ones.
        List<String> domains = new ArrayList<>();

        if (this.kind.isRegion())
        {
            domains.add(this.kind == FaceModListScreen.FaceKind.R_ENTITIES ? "textures/entity"
                    : this.kind == FaceModListScreen.FaceKind.R_PARTICLES ? "textures/particle"
                    : "textures/block");

            if (this.kind == FaceModListScreen.FaceKind.R_ITEMS)
            {
                domains.add(0, "textures/item"); // items first, then block items
            }
        }
        else if (this.kind.isShadow())
        {
            domains.add(this.kind == FaceModListScreen.FaceKind.FALLING_BLOCKS
                    ? "textures/block" : "textures/item");

            if (this.kind != FaceModListScreen.FaceKind.FALLING_BLOCKS)
            {
                domains.add("textures/block"); // block items (dropped/held blocks)
            }
        }
        else
        {
            domains.add(this.kind.getDomain());

            // Block entity textures are spread over both folders in 1.21.11:
            // bed/banner/sign/shulker(entity) still render from the entity
            // folder, but bell/conduit/hopper/crafter/shulker_box colors etc.
            // draw from the block atlas (textures/block).
            if (this.kind == FaceModListScreen.FaceKind.BLOCK_ENTITIES)
            {
                domains.add("textures/block");
            }

            // HUD elements draw from more folders than just gui/sprites: the
            // boss bar has its own sprite group (already inside the sprites
            // domain), the effect icons live in textures/mob_effect, and the
            // vignette/nausea overlays plus the demo background are plain
            // textures (textures/misc, textures/gui).
            if (this.kind == FaceModListScreen.FaceKind.HUD_ELEMENTS)
            {
                domains.add("textures/misc");
                domains.add("textures/mob_effect");
                domains.add("textures/gui");
            }
        }

        for (String domain : domains)
        {
            Map<Identifier, ?> res = MinecraftClient.getInstance().getResourceManager()
                    .findResources(domain, r -> r.getPath().endsWith(".png"));

            for (Identifier rid : res.keySet())
            {
                if (!this.paths.contains(rid.toString()))
                {
                    this.paths.add(rid.toString());
                }

                if (json.contains(rid.toString()) || matches(this.kind, this.id, rid, resolved))
                {
                    if (!this.matched.contains(rid.toString()))
                    {
                        this.matched.add(rid.toString());
                    }
                }
            }
        }

        Collections.sort(this.paths);
        Collections.sort(this.matched);

        FaceModPacks.LOGGER.info("[ReignRender] tex list {}: id='{}': {} paths enumerated, {} matched, {} modified",
                this.kind, this.id, this.paths.size(), this.matched.size(), this.modified.size());

        // Shadow categories store the edits under the shadow path in the
        // index; map them back to the vanilla paths the rows display. The
        // region categories keep their index in RegionFaceIndex.
        List<String> overrides = this.kind.isRegion()
                ? com.wdylyh.config.RegionFaceIndex.overriddenPaths(this.kind.indexKey(), this.id)
                : FaceModIndex.overriddenPaths(this.kind.indexKey(), this.id);

        for (String sp : overrides)
        {
            String vp = this.kind.vanillaOfShadow(sp);

            if (vp != null)
            {
                this.modified.add(vp);
            }
        }
    }

    private void rebuild()
    {
        // Keep the search text field alive: clearElements() also clears
        // textFields, which would wipe the box added in initGui
        this.clearChildren();
        this.clearButtons();

        // The sky has a single id (sun/moon/stars/...), so there is nothing
        // to filter by it: no toggle button and always the full folder list.
        boolean showToggle = this.kind != FaceModListScreen.FaceKind.SKY;

        if (showToggle)
        {
            this.addButton(new ButtonGeneric(206, 26, 86, false,
                    this.showAll ? "reignrender.gui.face.showmatched" : "reignrender.gui.face.showall"),
                    (b, m) ->
                    {
                        this.showAll = !this.showAll;
                        this.collect();
                        this.rebuild();
                    });
        }

        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       (b, m) -> GuiBase.openGui(FaceModTextureScreen.this.kind.isRegion()
                               ? new ConditionListScreen() : new FaceModListScreen(this.kind)));

        if (this.paths.isEmpty())
        {
            this.collect();
        }

        String s = this.query.toLowerCase(Locale.ROOT);
        // An active search always runs over the whole folder; without a query
        // the list shows only the id-matched textures (or all with the toggle).
        // The sky always shows the whole folder; a category where nothing
        // matched the id also falls back to the whole folder instead of an
        // empty list.
        List<String> base = (!showToggle || this.showAll || !s.isEmpty() || this.matched.isEmpty())
                ? this.paths : this.matched;
        List<String> ms = new ArrayList<>(this.modified.size() + base.size());

        // Modified textures first (marked), then the rest, both filtered
        for (String p : this.modified)
        {
            if (p.toLowerCase(Locale.ROOT).contains(s))
            {
                ms.add(p);
            }
        }

        for (String p : base)
        {
            if (!this.modified.contains(p) && p.toLowerCase(Locale.ROOT).contains(s))
            {
                ms.add(p);
            }
        }

        int vis = Math.max(1, this.listH() / ReplaceListScreen.LH + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, ms.size() - vis) * ReplaceListScreen.LH);

        if (ms.isEmpty())
        {
            this.addLabel(20, ReplaceListScreen.LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.face.texlist.empty"));
            return;
        }

        int top = this.scrollOffset / ReplaceListScreen.LH;

        for (int i = top; i < Math.min(ms.size(), top + vis + 1); i++)
        {
            this.addWidget(new Row(i, ms.get(i), this.modified.contains(ms.get(i)),
                    20, ReplaceListScreen.LTOP + i * ReplaceListScreen.LH - this.scrollOffset));
        }
    }

    private class Row extends WidgetBase
    {
        private final String path;
        private final boolean mod;

        Row(int index, String path, boolean mod, int x, int y)
        {
            super(x, y, FaceModTextureScreen.this.width - 40, ReplaceListScreen.LH);
            this.path = path;
            this.mod = mod;
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            boolean hov = this.isMouseOver(mx, my);
            int rbx = this.x + this.width - ReplaceListScreen.DWB;
            int ebx = rbx - ReplaceListScreen.DWB;
            boolean roh = hov && mx >= rbx;
            boolean eoh = hov && mx >= ebx && mx < rbx;

            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20202020);

            if (hov)
            {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x0FFFFFFF);
            }

            this.drawString(ctx, this.x + 4, this.y + 6, 0xFFFFFFFF, this.path);

            if (this.mod)
            {
                this.drawString(ctx, ebx - 10, this.y + 6, 0xFF00E000, "*");
            }

            RenderUtils.drawRect(ctx, ebx, this.y, ReplaceListScreen.DWB, this.height, eoh ? 0x2630FF30 : 0x20308030);
            this.drawCenteredString(ctx, ebx + ReplaceListScreen.DWB / 2, this.y + 6, 0xFFFFFFFF,
                    StringUtils.translate("reignrender.gui.face.edit"));
            RenderUtils.drawRect(ctx, rbx, this.y, ReplaceListScreen.DWB, this.height,
                    this.mod ? (roh ? 0x66FFB030 : 0x26808030) : 0x10606060);
            this.drawCenteredString(ctx, rbx + ReplaceListScreen.DWB / 2, this.y + 6, this.mod ? 0xFFFFFFFF : 0x50FFFFFF,
                    StringUtils.translate("reignrender.gui.face.reset"));
        }

        @Override
        protected boolean onMouseClickedImpl(Click c, boolean dc)
        {
            if (c.getKeycode() == 0)
            {
                int rbx = this.x + this.width - ReplaceListScreen.DWB;
                int ebx = rbx - ReplaceListScreen.DWB;

                if ((int) c.x() >= rbx)
                {
                    if (this.mod)
                    {
                        // Shadow categories stored the edit under the shadow
                        // path; index entry and pack file both use it.
                        String sp = FaceModTextureScreen.this.kind.shadowSavePath(this.path);

                        if (sp != null)
                        {
                            if (FaceModTextureScreen.this.kind.isRegion())
                            {
                                com.wdylyh.config.RegionFaceIndex.removeOverride(
                                        FaceModTextureScreen.this.kind.indexKey(),
                                        FaceModTextureScreen.this.id, sp);
                                com.wdylyh.config.RegionFacePacks.removeTextures(java.util.List.of(sp));
                            }
                            else
                            {
                                FaceModIndex.removeOverride(FaceModTextureScreen.this.kind.indexKey(),
                                        FaceModTextureScreen.this.id, sp);
                                FaceModPacks.removeTextures(java.util.List.of(sp));
                            }

                            FaceModTextureScreen.this.collect();
                            FaceModTextureScreen.this.rebuild();
                        }
                    }

                    return true;
                }

                if ((int) c.x() >= ebx)
                {
                    GuiBase.openGui(new FaceModEditorScreen(FaceModTextureScreen.this.kind,
                            FaceModTextureScreen.this.id, this.path));
                    return true;
                }
            }

            return false;
        }
    }
}
