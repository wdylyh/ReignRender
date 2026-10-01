package com.wdylyh.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Category chooser used by the replacement rule picker: lists every
 * replacement category (same set as {@link ReplaceListScreen.ReplaceKind}) and hands the
 * chosen one to the two-step "pick source id, then pick target id" flow, so a
 * condition entry's replace rule can be added by view selection instead of typing.
 *
 * Icon backed categories use the {@link IconGridPicker} grid, the name based ones
 * (name tags, player names) fall back to {@link NamePickScreen}. The finished
 * "source=target" rule is appended to the open {@link ConditionEntryEditor} rule field.
 */
public class ReplaceCategoryPicker extends GuiBase
{
    private static final int PER_COL = 4;

    private final ConditionEntryEditor edit;

    public ReplaceCategoryPicker(ConditionEntryEditor edit)
    {
        this.edit = edit;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(this.edit);
        this.setTitle(StringUtils.translate("reignrender.gui.repCat.title"));

        // Skip the categories without a coordinate aware replace path (HUD
        // elements) BEFORE laying out the grid, so no empty slot is left
        // behind and the rows stay aligned.
        java.util.List<ReplaceListScreen.ReplaceKind> kinds = new java.util.ArrayList<>();

        for (ReplaceListScreen.ReplaceKind k : ReplaceListScreen.ReplaceKind.values())
        {
            if (k.catKey() != null)
            {
                kinds.add(k);
            }
        }

        // Adaptive column count: four 134px buttons (+8px gap) per row need
        // roughly 560px of button space; narrow windows or high GUI scales
        // fall back to fewer columns so the rightmost button stays on screen.
        int cols = Math.max(1, Math.min(PER_COL, (this.width - 20) / 142));
        int cx = this.width / 2 - (cols * 142 - 8) / 2;

        for (int i = 0; i < kinds.size(); i++)
        {
            int r = i % cols;
            int c = i / cols;
            ReplaceListScreen.ReplaceKind replaceKind = kinds.get(i);

            this.addButton(new ButtonGeneric(cx + c * 142, 56 + r * 24, 134, false,
                            "reignrender.gui.repCat." + ReplaceCategoryPicker.keyOf(replaceKind)),
                           (b, m) -> this.pick(replaceKind));
        }
    }

    /** Opens the source picker for the chosen category. */
    private void pick(ReplaceListScreen.ReplaceKind replaceKind)
    {
        if (replaceKind.isText())
        {
            GuiBase.openGui(new NamePickScreen(src -> this.pickTarget(replaceKind, src),
                    "reignrender.gui.replace.source.title",
                    "reignrender.gui.replace.confirm", this.edit));
        }
        else
        {
            GuiBase.openGui(new IconGridPicker(replaceKind.getKind(), src -> this.pickTarget(replaceKind, src),
                    "reignrender.gui.replace.source.title",
                    "reignrender.gui.replace.confirm", this.edit));
        }
    }

    /** Opens the target picker once the source id is known. */
    private void pickTarget(ReplaceListScreen.ReplaceKind replaceKind, String src)
    {
        if (replaceKind.isText())
        {
            GuiBase.openGui(new NamePickScreen(dst -> this.addRule(replaceKind, src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this.edit));
        }
        else
        {
            GuiBase.openGui(new IconGridPicker(replaceKind.getKind(), dst -> this.addRule(replaceKind, src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this.edit));
        }
    }

    /**
     * Appends the picked rule to the editor and returns to it. The rule is
     * pinned to the picked category ("blocks:stone=glass"), so a shared id
     * like "anvil" only affects that one render category.
     */
    private void addRule(ReplaceListScreen.ReplaceKind kind, String src, String dst)
    {
        String cat = kind.catKey();
        this.edit.appendRule((cat != null ? cat + ":" : "") + src + "=" + dst);
    }

    private static String keyOf(ReplaceListScreen.ReplaceKind replaceKind)
    {
        return switch (replaceKind)
        {
            case PARTICLES -> "particles";
            case BLOCKS -> "blocks";
            case ENTITIES -> "entities";
            case FOGS -> "fogs";
            case ARMOR -> "armor";
            case FLUIDS -> "fluids";
            case BLOCK_ENTITIES -> "blockEntities";
            case FALLING_BLOCKS -> "fallingBlocks";
            case ITEM_ENTITIES -> "itemEntities";
            case HELD_ITEMS -> "heldItems";
            case HUD_ELEMENTS -> "hudElements";
            case NAME_TAGS -> "nameTags";
            case PLAYER_NAMES -> "playerNames";
        };
    }
}