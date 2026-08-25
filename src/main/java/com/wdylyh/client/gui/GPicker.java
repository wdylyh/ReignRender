package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.util.Names;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.block.Block;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

/**
 * Custom picker GUI for a single blacklist/whitelist filter list.
 *
 * Shows every registry entry of the given kind as a scrollable, searchable grid
 * of icon cells (litematica style). Clicking a cell toggles the corresponding
 * registry id in the {@link ConfigStringList} backing that filter, and updates
 * the {@link FR} caches.
 */
public class GPicker extends GuiBase
{
    public static final int CS = 26; // 24 px cell + 2 px gap
    public static final int LX = 20;
    public static final int LT = 60;
    public static final int LBM = 34;

    private final FKind k;
    private final ConfigStringList cfg;

    /** Non-null in single-select (replace) mode; null in the multi-select mode. */
    private final PickCb cb;
    private final String titleKey;
    private final String confirmKey;
    private final Screen back;

    private final List<FEntry> ents = new ArrayList<>();
    private Set<String> sel = Collections.emptySet();
    private String q = "";
    private int so;

    public GPicker(FKind k)
    {
        this(k, null, null, null, null);
    }

    /**
     * Single-select mode for the replacement lists: clicking a cell picks it
     * (the selection is not written to the config), and the confirm button
     * hands the picked id to {@code cb}, which opens the next screen (the
     * target picker or the replace list itself). {@code back} is the screen
     * to return to on escape.
     */
    public GPicker(FKind k, PickCb cb, String titleKey, String confirmKey, Screen back)
    {
        this.k = k;
        this.cfg = k.getConfig();
        this.cb = cb;
        this.titleKey = titleKey;
        this.confirmKey = confirmKey;
        this.back = back;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        boolean single = this.cb != null;

        this.setParent(single ? this.back : new GConfigs());
        this.setTitle(StringUtils.translate(single ? this.titleKey : this.k.getKey()));
        this.so = 0;

        // Search box for live filtering of the candidate list
        GuiTextFieldGeneric sf = new GuiTextFieldGeneric(LX, 26, 180, 16, this.textRenderer);
        sf.setPlaceholder(Text.translatable("reignrender.gui.filter.search"));
        this.addTextField(sf, this::onSearch);

        if (single)
        {
            // Single-select mode: the confirm button hands the picked id to the
            // callback, which opens the next screen (target picker / text input).
            this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, this.confirmKey),
                           (b, m) -> this.confirm());
        }
        else
        {
            // Multi-select mode: the done button returns to the config GUI
            this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                           new Done());
        }

        // The candidate registry ids only need to be collected once per GUI instance
        if (this.ents.isEmpty())
        {
            this.build();
        }

        // In single-select mode the selection starts empty and only ever holds
        // the one id the player last clicked; the config is written by the
        // callback chain in RplScreen, never here.
        this.sel = single ? Collections.emptySet() : new HashSet<>(this.cfg.getStrings());
        this.rebuild();
    }

    /**
     * Hands the currently selected id to the callback (single-select mode
     * only). A no-op while nothing is picked.
     */
    private void confirm()
    {
        if (this.cb != null && this.sel.size() == 1)
        {
            this.cb.onPicked(this.sel.iterator().next());
        }
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double ha, double va)
    {
        if (GuiBase.isMouseOver((int) mx, (int) my, LX, LT, this.width - LX * 2, this.listH()))
        {
            this.so = Math.max(0, this.so - (int) va * CS);
            this.rebuild();
            return true;
        }

        return super.onMouseScrolled(mx, my, ha, va);
    }

    private int listH()
    {
        return Math.max(0, this.height - LT - LBM);
    }

    private boolean onSearch(GuiTextFieldGeneric tf)
    {
        this.q = tf.getText();
        this.so = 0;
        this.rebuild();
        return true;
    }

    private void rebuild()
    {
        this.clearChildren();

        String s = this.q.toLowerCase(Locale.ROOT);
        List<FEntry> ms = new ArrayList<>(Math.min(this.ents.size(), 512));

        for (FEntry e : this.ents)
        {
            if (e.matches(s))
            {
                ms.add(e);
            }
        }

        int cols = Math.max(1, (this.width - LX * 2) / CS);
        int rows = (ms.size() + cols - 1) / cols;
        int vrows = Math.max(1, this.listH() / CS + 1);
        this.so = Math.min(this.so, Math.max(0, rows - vrows) * CS);

        int sr = this.so / CS;
        int er = Math.min(rows, sr + vrows + 1);

        for (int r = sr; r < er; ++r)
        {
            for (int c = 0; c < cols; ++c)
            {
                int i = r * cols + c;

                if (i >= ms.size())
                {
                    break;
                }

                this.addWidget(new Cell(ms.get(i),
                        LX + c * CS,
                        LT + r * CS - this.so));
            }
        }
    }

    private void toggle(String id)
    {
        // Copy to a new list so setStrings() detects the change and fires the callback
        List<String> cur = new ArrayList<>(this.cfg.getStrings());

        // remove() returns true if the id was present, replacing the separate
        // contains() + remove() passes with a single O(n) lookup
        if (!cur.remove(id))
        {
            cur.add(id);
        }

        this.cfg.setStrings(cur);
        this.sel = new HashSet<>(cur);
        FR.invalidateCaches();
    }

    private void build()
    {
        switch (this.k)
        {
            case ENTITIES -> this.buildEnt();
            case BLOCKS -> this.buildBlk();
            case FLUIDS -> this.buildFluid();
            case BLOCK_ENTITIES -> this.buildBE();
            case PARTICLES -> this.buildPart();
            case ARMOR -> this.buildArmor();
            case FOGS -> this.buildFog();
        case HUD -> this.buildHud();
        case FALLING_BLOCKS -> this.buildFalling();
        case ITEM_ENTITIES -> this.buildItems();
        }

        this.ents.sort(Comparator.comparing(FEntry::id));
    }

    private void buildEnt()
    {
        List<Identifier> ids = new ArrayList<>(Registries.ENTITY_TYPE.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            EntityType<?> t = Registries.ENTITY_TYPE.get(id);

            if (t == null)
            {
                continue;
            }

            Item egg = SpawnEggItem.forEntity(t);
            ItemStack ic = null;
            String nm;

            if (egg != null && egg != Items.AIR)
            {
                ic = new ItemStack(egg);
                nm = ic.getName().getString();
            }
            else
            {
                nm = t.getName().getString();
            }

            this.ents.add(new FEntry(id.toString(), nm, ic));
        }
    }

    private void buildBlk()
    {
        List<Identifier> ids = new ArrayList<>(Registries.BLOCK.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Block b = Registries.BLOCK.get(id);

            if (b == null)
            {
                continue;
            }

            // Blocks backed by a managed block entity type (copper chests, signs,
            // shulker boxes, ...) are handled by the block entity filter list,
            // so they are not offered in the block picker
            if (FR.isBlockManagedByBlockEntityFilter(b))
            {
                continue;
            }

            Item it = b.asItem();
            ItemStack ic = null;
            String nm;

            if (it != null && it != Items.AIR)
            {
                ic = new ItemStack(it);
                nm = ic.getName().getString();
            }
            else
            {
                nm = b.getName().getString();
            }

            this.ents.add(new FEntry(id.toString(), nm, ic));
        }
    }

    private void buildFluid()
    {
        List<Identifier> ids = new ArrayList<>(Registries.FLUID.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Fluid fl = Registries.FLUID.get(id);

            if (fl == null)
            {
                continue;
            }

            Item bk = fl.getBucketItem();
            ItemStack ic = null;
            String nm;

            if (bk != null && bk != Items.AIR)
            {
                ic = new ItemStack(bk);
                nm = ic.getName().getString();
            }
            else
            {
                nm = fl.getDefaultState().getBlockState().getBlock().getName().getString();
            }

            this.ents.add(new FEntry(id.toString(), nm, ic));
        }
    }

    private void buildBE()
    {
        List<Identifier> bIds = new ArrayList<>(Registries.BLOCK.getIds());
        Collections.sort(bIds);

        List<Identifier> ids = new ArrayList<>(Registries.BLOCK_ENTITY_TYPE.getIds());
        Collections.sort(ids);

        // Deduplicates by block instance: legacy alias ids (e.g. "minecraft:sign"
        // and "minecraft:oak_sign") resolve to the same block, so they only add
        // one cell. This also guarantees the same block never appears under two
        // different block entity types.
        Set<Block> added = new HashSet<>();

        for (Identifier id : ids)
        {
            // Only offer vanilla block entities, not modded ones
            if (!Cfg.F.BLOCK_ENTITY_TYPE_IDS.contains(id.toString()))
            {
                continue;
            }

            BlockEntityType<?> t = Registries.BLOCK_ENTITY_TYPE.get(id);

            if (t == null)
            {
                continue;
            }

            boolean any = false;

            // Expand every vanilla variant block backed by this block entity type
            // (e.g. each shulker box color, sign wood, banner color), so the player
            // can see and pick each variant. Every variant block has its own
            // registry id, so each one can be filtered independently.
            for (Identifier bId : bIds)
            {
                Block b = Registries.BLOCK.get(bId);

                if (b == null || added.contains(b) || !t.supports(b.getDefaultState()))
                {
                    continue;
                }

                Item it = b.asItem();

                if (it == null || it == Items.AIR)
                {
                    continue;
                }

                added.add(b);
                ItemStack ic = new ItemStack(it);
                this.ents.add(new FEntry(bId.toString(), ic.getName().getString(), ic));
                any = true;
            }

            // Fall back to a single generic entry when no item-backed variant exists
            if (!any)
            {
                this.ents.add(new FEntry(id.toString(), id.toString(), null));
            }
        }
    }

    private void buildPart()
    {
        List<Identifier> ids = new ArrayList<>(Registries.PARTICLE_TYPE.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            this.ents.add(new FEntry(id.toString(), id.toString(), null));
        }
    }

    private void buildArmor()
    {
        List<Identifier> ids = new ArrayList<>(Registries.ITEM.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Item it = Registries.ITEM.get(id);

            if (it == null)
            {
                continue;
            }

            EquippableComponent eq = it.getComponents().get(DataComponentTypes.EQUIPPABLE);

            // Only offer pieces that are rendered as armor on a biped (head,
            // chest, legs, feet). Horse armor and the like are not handled by
            // ArmorFeatureRenderer.
            if (eq == null || !eq.slot().isArmorSlot())
            {
                continue;
            }

            // Carpets are technically equippable on the head, but they are not
            // armor and clutters the picker, so they are not offered here.
            if (it instanceof BlockItem bi && bi.getBlock() instanceof CarpetBlock)
            {
                continue;
            }

            ItemStack ic = new ItemStack(it);
            this.ents.add(new FEntry(id.toString(), ic.getName().getString(), ic));
        }
    }

    private void buildFog()
    {
        // Camera submersion based fog types; no registry for them.
        this.ents.add(new FEntry("water", Names.name("water"), new ItemStack(Items.WATER_BUCKET)));
        this.ents.add(new FEntry("lava", Names.name("lava"), new ItemStack(Items.LAVA_BUCKET)));
        this.ents.add(new FEntry("powder_snow", Names.name("powder_snow"), new ItemStack(Items.POWDER_SNOW_BUCKET)));
        this.ents.add(new FEntry("atmospheric", "atmospheric", null));

        // Status effect based fog types; also no registry id for the fog itself.
        // The icons hint at the effect: milk cures blindness, sculk is tied to
        // the darkness effect, the wither rose drops from the wither, and the
        // golden carrot brews the night vision potion.
        this.ents.add(new FEntry(FR.FOG_EFFECT_BLINDNESS,
                Text.translatable("effect.minecraft.blindness").getString(),
                new ItemStack(Items.MILK_BUCKET)));
        this.ents.add(new FEntry(FR.FOG_EFFECT_DARKNESS,
                Text.translatable("effect.minecraft.darkness").getString(),
                new ItemStack(Items.SCULK)));
        this.ents.add(new FEntry(FR.FOG_EFFECT_WITHER,
                Text.translatable("effect.minecraft.wither").getString(),
                new ItemStack(Items.WITHER_ROSE)));
        this.ents.add(new FEntry(FR.FOG_EFFECT_NIGHT_VISION,
                Text.translatable("effect.minecraft.night_vision").getString(),
                new ItemStack(Items.GOLDEN_CARROT)));

        // Biome based fog: entries are the biome registry ids, matched against
        // the biome the camera is currently in. Requires a loaded world.
        ClientWorld w = MinecraftClient.getInstance().world;

        if (w == null)
        {
            return;
        }

        Registry<Biome> reg = w.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        List<Identifier> ids = new ArrayList<>(reg.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            this.ents.add(new FEntry(id.toString(), Names.name(id.toString()), null));
        }
    }

    /**
     * HUD elements which can be hidden by the "Disable HUD Elements" toggle.
     * One fixed set, mirroring the ids understood by the HudM mixin ({@link
     * com.wdylyh.mixin.HudM}). The icons hint at each element's purpose.
     */
    private void buildFalling()
    {
        List<Identifier> ids = new ArrayList<>(Registries.BLOCK.getIds());
        Collections.sort(ids);

        // Only blocks that can actually become a falling block entity (sand,
        // gravel, concrete powders, anvils, dragon egg, ...) are offered here,
        // mirroring the pool the "disable falling blocks" toggle affects.
        for (Identifier id : ids)
        {
            Block b = Registries.BLOCK.get(id);

            if (b == null || !(b instanceof FallingBlock))
            {
                continue;
            }

            Item it = b.asItem();
            ItemStack ic = null;
            String nm;

            if (it != null && it != Items.AIR)
            {
                ic = new ItemStack(it);
                nm = ic.getName().getString();
            }
            else
            {
                nm = b.getName().getString();
            }

            this.ents.add(new FEntry(id.toString(), nm, ic));
        }
    }

    private void buildHud()
    {
        this.ents.add(new FEntry("bossbar", this.hudName("bossbar"), new ItemStack(Items.DRAGON_HEAD)));
        this.ents.add(new FEntry("subtitles", this.hudName("subtitles"), new ItemStack(Items.MUSIC_DISC_CAT)));
        this.ents.add(new FEntry("chat", this.hudName("chat"), new ItemStack(Items.OAK_SIGN)));
        this.ents.add(new FEntry("statusEffects", this.hudName("statusEffects"), new ItemStack(Items.POTION)));
        this.ents.add(new FEntry("crosshair", this.hudName("crosshair"), new ItemStack(Items.BOW)));
        this.ents.add(new FEntry("hotbar", this.hudName("hotbar"), new ItemStack(Items.CHEST)));
        this.ents.add(new FEntry("overlayMessage", this.hudName("overlayMessage"), new ItemStack(Items.PAPER)));
        this.ents.add(new FEntry("title", this.hudName("title"), new ItemStack(Items.NAME_TAG)));
        this.ents.add(new FEntry("scoreboard", this.hudName("scoreboard"), new ItemStack(Items.COMMAND_BLOCK)));
        this.ents.add(new FEntry("playerList", this.hudName("playerList"), new ItemStack(Items.PLAYER_HEAD)));
        this.ents.add(new FEntry("demoTimer", this.hudName("demoTimer"), new ItemStack(Items.CLOCK)));
        this.ents.add(new FEntry("heldItemTooltip", this.hudName("heldItemTooltip"), new ItemStack(Items.BOOK)));
        this.ents.add(new FEntry("portal", this.hudName("portal"), new ItemStack(Items.OBSIDIAN)));
        this.ents.add(new FEntry("nausea", this.hudName("nausea"), new ItemStack(Items.SLIME_BALL)));
        this.ents.add(new FEntry("vignette", this.hudName("vignette"), new ItemStack(Items.SPYGLASS)));
    }

    /**
     * Item candidate list for the item entity replacement flows. Every vanilla
     * (and modded) item is offered, since any item can be dropped as an item
     * entity; the item's own icon doubles as the picker cell icon.
     */
    private void buildItems()
    {
        List<Identifier> ids = new ArrayList<>(Registries.ITEM.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Item it = Registries.ITEM.get(id);

            if (it == null || it == Items.AIR)
            {
                continue;
            }

            ItemStack ic = new ItemStack(it);
            this.ents.add(new FEntry(id.toString(), ic.getName().getString(), ic));
        }
    }

    private String hudName(String id)
    {
        return StringUtils.translate("reignrender.gui.hud." + id);
    }

    public enum FKind
    {
        ENTITIES(Cfg.F.FILTERED_ENTITIES, "reignrender.gui.title.filter.entities"),
        BLOCKS(Cfg.F.FILTERED_BLOCKS, "reignrender.gui.title.filter.blocks"),
        FLUIDS(Cfg.F.FILTERED_FLUIDS, "reignrender.gui.title.filter.fluids"),
        BLOCK_ENTITIES(Cfg.F.FILTERED_BLOCK_ENTITIES, "reignrender.gui.title.filter.blockEntities"),
        PARTICLES(Cfg.F.FILTERED_PARTICLES, "reignrender.gui.title.filter.particles"),
        ARMOR(Cfg.F.FILTERED_ARMOR, "reignrender.gui.title.filter.armor"),
        FOGS(Cfg.F.FILTERED_FOGS, "reignrender.gui.title.filter.fogs"),
        HUD(Cfg.F.HIDDEN_HUD_ELEMENTS, "reignrender.gui.title.filter.hud"),
        FALLING_BLOCKS(Cfg.F.REPLACE_FALLING_BLOCKS, "reignrender.gui.title.filter.fallingBlocks"),
        ITEM_ENTITIES(Cfg.F.REPLACE_ITEM_ENTITIES, "reignrender.gui.title.filter.items");

        private final ConfigStringList cfg;
        private final String key;

        FKind(ConfigStringList cfg, String key)
        {
            this.cfg = cfg;
            this.key = key;
        }

        public ConfigStringList getConfig()
        {
            return this.cfg;
        }

        public String getKey()
        {
            return this.key;
        }
    }

    private record FEntry(String id, String nm, ItemStack ic, String lower)
    {
        FEntry(String id, String nm, ItemStack ic)
        {
            this(id, nm, ic, nm.toLowerCase(Locale.ROOT));
        }

        boolean matches(String s)
        {
            return s.isEmpty() ||
                   this.id.contains(s) ||
                   this.lower.contains(s);
        }
    }

    private class Cell extends WidgetBase
    {
        private final FEntry e;

        public Cell(FEntry e, int x, int y)
        {
            super(x, y, CS, CS);
            this.e = e;
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            boolean hov = this.isMouseOver(mx, my);
            boolean chk = GPicker.this.sel.contains(this.e.id());

            // Cell background so the grid reads as solid cells
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF202020);

            if (chk)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2, 0x3300C800, 0xFF00C800);
            }
            else if (hov)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2, 0x26FFFFFF, 0xFF909090);
            }

            // Item icon (when available)
            if (this.e.ic() != null)
            {
                ctx.drawItem(this.e.ic(), this.x + 5, this.y + 5);
            }
        }

        @Override
        protected boolean onMouseClickedImpl(Click c, boolean dc)
        {
            if (c.getKeycode() == 0)
            {
                if (GPicker.this.cb != null)
                {
                    // Single-select mode: picking never touches the config, it
                    // only marks the id so the confirm button can hand it over.
                    GPicker.this.sel = Set.of(this.e.id());
                    GPicker.this.rebuild();
                }
                else
                {
                    GPicker.this.toggle(this.e.id());
                }

                return true;
            }

            return false;
        }

        @Override
        public void postRenderHovered(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.postRenderHovered(ctx, mx, my, selected);

            if (this.isMouseOver(mx, my))
            {
                RenderUtils.drawHoverText(ctx, mx, my, List.of(
                        this.e.nm(),
                        GuiBase.TXT_GRAY + this.e.id(),
                        StringUtils.translate(GPicker.this.cb != null
                                ? "reignrender.gui.replace.pick"
                                : "reignrender.gui.filter.select")));
            }
        }
    }

    private static class Done implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase b, int m)
        {
            GuiBase.openGui(new GConfigs());
        }
    }

    /**
     * Single-select mode callback: receives the one id the player picked and
     * confirmed. The implementation is responsible for opening the next screen.
     */
    @FunctionalInterface
    public interface PickCb
    {
        void onPicked(String id);
    }
}