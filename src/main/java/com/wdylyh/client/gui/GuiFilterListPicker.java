package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
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
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
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
 * the {@link FilterRules} caches.
 */
public class GuiFilterListPicker extends GuiBase
{
    public static final int CELL_SIZE = 26; // 24 px cell + 2 px gap
    public static final int LIST_X = 20;
    public static final int LIST_TOP = 60;
    public static final int LIST_BOTTOM_MARGIN = 34;

    private final FilterKind kind;
    private final ConfigStringList config;
    private final List<FilterEntry> entries = new ArrayList<>();
    private Set<String> selectedIds = Collections.emptySet();
    private String searchText = "";
    private int scrollOffset;

    public GuiFilterListPicker(FilterKind kind)
    {
        this.kind = kind;
        this.config = kind.getConfig();
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.setParent(new GuiConfigs());
        this.setTitle(StringUtils.translate(this.kind.getTitleKey()));
        this.scrollOffset = 0;

        // Search box for live filtering of the candidate list
        GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(LIST_X, 26, 180, 16, this.textRenderer);
        searchField.setPlaceholder(Text.translatable("reignrender.gui.filter.search"));
        this.addTextField(searchField, this::onSearchTextChanged);

        // Done button (returns to the config GUI)
        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       new DoneButtonListener());

        // The candidate registry ids only need to be collected once per GUI instance
        if (this.entries.isEmpty())
        {
            this.buildEntries();
        }

        this.selectedIds = new HashSet<>(this.config.getStrings());
        this.rebuildRows();
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (GuiBase.isMouseOver((int) mouseX, (int) mouseY, LIST_X, LIST_TOP, this.width - LIST_X * 2, this.getListHeight()))
        {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) verticalAmount * CELL_SIZE);
            this.rebuildRows();
            return true;
        }

        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int getListHeight()
    {
        return Math.max(0, this.height - LIST_TOP - LIST_BOTTOM_MARGIN);
    }

    private boolean onSearchTextChanged(GuiTextFieldGeneric textField)
    {
        this.searchText = textField.getText();
        this.scrollOffset = 0;
        this.rebuildRows();
        return true;
    }

    private void rebuildRows()
    {
        this.clearChildren();

        String search = this.searchText.toLowerCase(Locale.ROOT);
        List<FilterEntry> matches = new ArrayList<>();

        for (FilterEntry entry : this.entries)
        {
            if (entry.matchesSearch(search))
            {
                matches.add(entry);
            }
        }

        int cols = Math.max(1, (this.width - LIST_X * 2) / CELL_SIZE);
        int totalRows = (matches.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, this.getListHeight() / CELL_SIZE + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, totalRows - visibleRows) * CELL_SIZE);

        int startRow = this.scrollOffset / CELL_SIZE;
        int endRow = Math.min(totalRows, startRow + visibleRows + 1);

        for (int row = startRow; row < endRow; ++row)
        {
            for (int col = 0; col < cols; ++col)
            {
                int index = row * cols + col;

                if (index >= matches.size())
                {
                    break;
                }

                this.addWidget(new CellWidget(matches.get(index),
                        LIST_X + col * CELL_SIZE,
                        LIST_TOP + row * CELL_SIZE - this.scrollOffset));
            }
        }
    }

    private void toggleEntry(String id)
    {
        // Copy to a new list so setStrings() detects the change and fires the callback
        List<String> current = new ArrayList<>(this.config.getStrings());

        // remove() returns true if the id was present, replacing the separate
        // contains() + remove() passes with a single O(n) lookup
        if (!current.remove(id))
        {
            current.add(id);
        }

        this.config.setStrings(current);
        this.selectedIds = new HashSet<>(current);
        FilterRules.invalidateCaches();
    }

    private void buildEntries()
    {
        switch (this.kind)
        {
            case ENTITIES -> this.buildEntityEntries();
            case BLOCKS -> this.buildBlockEntries();
            case FLUIDS -> this.buildFluidEntries();
            case BLOCK_ENTITIES -> this.buildBlockEntityEntries();
            case PARTICLES -> this.buildParticleEntries();
            case ARMOR -> this.buildArmorEntries();
            case FOGS -> this.buildFogEntries();
        }

        this.entries.sort(Comparator.comparing(FilterEntry::id));
    }

    private void buildEntityEntries()
    {
        List<Identifier> ids = new ArrayList<>(Registries.ENTITY_TYPE.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            EntityType<?> type = Registries.ENTITY_TYPE.get(id);

            if (type == null)
            {
                continue;
            }

            Item egg = SpawnEggItem.forEntity(type);
            ItemStack icon = null;
            String displayName;

            if (egg != null && egg != Items.AIR)
            {
                icon = new ItemStack(egg);
                displayName = icon.getName().getString();
            }
            else
            {
                displayName = type.getName().getString();
            }

            this.entries.add(new FilterEntry(id.toString(), displayName, icon));
        }
    }

    private void buildBlockEntries()
    {
        List<Identifier> ids = new ArrayList<>(Registries.BLOCK.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Block block = Registries.BLOCK.get(id);

            if (block == null)
            {
                continue;
            }

            // Blocks backed by a managed block entity type (copper chests, signs,
            // shulker boxes, ...) are handled by the block entity filter list,
            // so they are not offered in the block picker
            if (FilterRules.isBlockManagedByBlockEntityFilter(block))
            {
                continue;
            }

            Item item = block.asItem();
            ItemStack icon = null;
            String displayName;

            if (item != null && item != Items.AIR)
            {
                icon = new ItemStack(item);
                displayName = icon.getName().getString();
            }
            else
            {
                displayName = block.getName().getString();
            }

            this.entries.add(new FilterEntry(id.toString(), displayName, icon));
        }
    }

    private void buildFluidEntries()
    {
        List<Identifier> ids = new ArrayList<>(Registries.FLUID.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Fluid fluid = Registries.FLUID.get(id);

            if (fluid == null)
            {
                continue;
            }

            Item bucket = fluid.getBucketItem();
            ItemStack icon = null;
            String displayName;

            if (bucket != null && bucket != Items.AIR)
            {
                icon = new ItemStack(bucket);
                displayName = icon.getName().getString();
            }
            else
            {
                displayName = fluid.getDefaultState().getBlockState().getBlock().getName().getString();
            }

            this.entries.add(new FilterEntry(id.toString(), displayName, icon));
        }
    }

    private void buildBlockEntityEntries()
    {
        List<Identifier> blockIds = new ArrayList<>(Registries.BLOCK.getIds());
        Collections.sort(blockIds);

        List<Identifier> ids = new ArrayList<>(Registries.BLOCK_ENTITY_TYPE.getIds());
        Collections.sort(ids);

        // Deduplicates by block instance: legacy alias ids (e.g. "minecraft:sign"
        // and "minecraft:oak_sign") resolve to the same block, so they only add
        // one cell. This also guarantees the same block never appears under two
        // different block entity types.
        Set<Block> addedBlocks = new HashSet<>();

        for (Identifier id : ids)
        {
            // Only offer vanilla block entities, not modded ones
            if (!Configs.Filter.BLOCK_ENTITY_TYPE_IDS.contains(id.toString()))
            {
                continue;
            }

            BlockEntityType<?> type = Registries.BLOCK_ENTITY_TYPE.get(id);

            if (type == null)
            {
                continue;
            }

            boolean anyAdded = false;

            // Expand every vanilla variant block backed by this block entity type
            // (e.g. each shulker box color, sign wood, banner color), so the player
            // can see and pick each variant. Every variant block has its own
            // registry id, so each one can be filtered independently.
            for (Identifier blockId : blockIds)
            {
                Block block = Registries.BLOCK.get(blockId);

                if (block == null || addedBlocks.contains(block) || !type.supports(block.getDefaultState()))
                {
                    continue;
                }

                Item item = block.asItem();

                if (item == null || item == Items.AIR)
                {
                    continue;
                }

                addedBlocks.add(block);
                ItemStack icon = new ItemStack(item);
                this.entries.add(new FilterEntry(blockId.toString(), icon.getName().getString(), icon));
                anyAdded = true;
            }

            // Fall back to a single generic entry when no item-backed variant exists
            if (!anyAdded)
            {
                this.entries.add(new FilterEntry(id.toString(), id.toString(), null));
            }
        }
    }

    private void buildParticleEntries()
    {
        List<Identifier> ids = new ArrayList<>(Registries.PARTICLE_TYPE.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            this.entries.add(new FilterEntry(id.toString(), id.toString(), null));
        }
    }

    private void buildArmorEntries()
    {
        List<Identifier> ids = new ArrayList<>(Registries.ITEM.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            Item item = Registries.ITEM.get(id);

            if (item == null)
            {
                continue;
            }

            EquippableComponent equippable = item.getComponents().get(DataComponentTypes.EQUIPPABLE);

            // Only offer pieces that are rendered as armor on a biped (head,
            // chest, legs, feet). Horse armor and the like are not handled by
            // ArmorFeatureRenderer.
            if (equippable == null || !equippable.slot().isArmorSlot())
            {
                continue;
            }

            // Carpets are technically equippable on the head, but they are not
            // armor and clutters the picker, so they are not offered here.
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof CarpetBlock)
            {
                continue;
            }

            ItemStack icon = new ItemStack(item);
            this.entries.add(new FilterEntry(id.toString(), icon.getName().getString(), icon));
        }
    }

    private void buildFogEntries()
    {
        // Camera submersion based fog types; no registry for them.
        this.entries.add(new FilterEntry("water", "water", new ItemStack(Items.WATER_BUCKET)));
        this.entries.add(new FilterEntry("lava", "lava", new ItemStack(Items.LAVA_BUCKET)));
        this.entries.add(new FilterEntry("powder_snow", "powder_snow", new ItemStack(Items.POWDER_SNOW_BUCKET)));
        this.entries.add(new FilterEntry("atmospheric", "atmospheric", null));

        // Biome based fog: entries are the biome registry ids, matched against
        // the biome the camera is currently in. Requires a loaded world.
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return;
        }

        Registry<Biome> biomeRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        List<Identifier> ids = new ArrayList<>(biomeRegistry.getIds());
        Collections.sort(ids);

        for (Identifier id : ids)
        {
            String translationKey = "biome." + id.getNamespace() + "." + id.getPath();
            String display = Text.translatable(translationKey).getString();

            if (display.equals(translationKey))
            {
                display = id.toString();
            }

            this.entries.add(new FilterEntry(id.toString(), display, null));
        }
    }

    public enum FilterKind
    {
        ENTITIES(Configs.Filter.FILTERED_ENTITIES, "reignrender.gui.title.filter.entities"),
        BLOCKS(Configs.Filter.FILTERED_BLOCKS, "reignrender.gui.title.filter.blocks"),
        FLUIDS(Configs.Filter.FILTERED_FLUIDS, "reignrender.gui.title.filter.fluids"),
        BLOCK_ENTITIES(Configs.Filter.FILTERED_BLOCK_ENTITIES, "reignrender.gui.title.filter.blockEntities"),
        PARTICLES(Configs.Filter.FILTERED_PARTICLES, "reignrender.gui.title.filter.particles"),
        ARMOR(Configs.Filter.FILTERED_ARMOR, "reignrender.gui.title.filter.armor"),
        FOGS(Configs.Filter.FILTERED_FOGS, "reignrender.gui.title.filter.fogs");

        private final ConfigStringList config;
        private final String titleKey;

        FilterKind(ConfigStringList config, String titleKey)
        {
            this.config = config;
            this.titleKey = titleKey;
        }

        public ConfigStringList getConfig()
        {
            return this.config;
        }

        public String getTitleKey()
        {
            return this.titleKey;
        }
    }

    private record FilterEntry(String id, String displayName, ItemStack icon, String displayNameLower)
    {
        FilterEntry(String id, String displayName, ItemStack icon)
        {
            this(id, displayName, icon, displayName.toLowerCase(Locale.ROOT));
        }

        boolean matchesSearch(String search)
        {
            return search.isEmpty() ||
                   this.id.contains(search) ||
                   this.displayNameLower.contains(search);
        }
    }

    private class CellWidget extends WidgetBase
    {
        private final FilterEntry entry;

        public CellWidget(FilterEntry entry, int x, int y)
        {
            super(x, y, CELL_SIZE, CELL_SIZE);
            this.entry = entry;
        }

        @Override
        public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
        {
            super.render(ctx, mouseX, mouseY, selected);

            boolean hovered = this.isMouseOver(mouseX, mouseY);
            boolean checked = GuiFilterListPicker.this.selectedIds.contains(this.entry.id());

            // Cell background so the grid reads as solid cells
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF202020);

            if (checked)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2, 0x3300C800, 0xFF00C800);
            }
            else if (hovered)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2, 0x26FFFFFF, 0xFF909090);
            }

            // Item icon (when available)
            if (this.entry.icon() != null)
            {
                ctx.drawItem(this.entry.icon(), this.x + 5, this.y + 5);
            }
        }

        @Override
        protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
        {
            if (click.getKeycode() == 0)
            {
                GuiFilterListPicker.this.toggleEntry(this.entry.id());
                return true;
            }

            return false;
        }

        @Override
        public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean selected)
        {
            super.postRenderHovered(ctx, mouseX, mouseY, selected);

            if (this.isMouseOver(mouseX, mouseY))
            {
                RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(
                        this.entry.displayName(),
                        GuiBase.TXT_GRAY + this.entry.id(),
                        StringUtils.translate("reignrender.gui.filter.select")));
            }
        }
    }

    private static class DoneButtonListener implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            GuiBase.openGui(new GuiConfigs());
        }
    }
}
