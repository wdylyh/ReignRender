package com.wdylyh.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Category chooser used by the coordinate replacement rule picker: lists every
 * replacement category (same set as {@link ReplaceListScreen.ReplaceKind}) and hands the
 * chosen one to the two-step "pick source id, then pick target id" flow, so a
 * coordinate replacement rule can be added by view selection instead of typing.
 *
 * Icon backed categories use the {@link IconGridPicker} grid, the name based ones
 * (name tags, player names) fall back to {@link NamePickScreen}. The finished
 * "source=target" rule is appended to the open {@link CoordReplaceEditor} rule field.
 */
public class ReplaceCategoryPicker extends GuiBase
{
    private static final int PER_COL = 4;

    private final CoordReplaceEditor edit;

    public ReplaceCategoryPicker(CoordReplaceEditor edit)
    {
        this.edit = edit;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(this.edit);
        this.setTitle(StringUtils.translate("reignrender.gui.repCat.title"));

        ReplaceListScreen.ReplaceKind[] kinds = ReplaceListScreen.ReplaceKind.values();
        int cx = this.width / 2 - 212;

        for (int i = 0; i < kinds.length; i++)
        {
            int r = i % PER_COL;
            int c = i / PER_COL;
            ReplaceListScreen.ReplaceKind replaceKind = kinds[i];

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
            GuiBase.openGui(new NamePickScreen(dst -> this.addRule(src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this.edit));
        }
        else
        {
            GuiBase.openGui(new IconGridPicker(replaceKind.getKind(), dst -> this.addRule(src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this.edit));
        }
    }

    /** Appends the picked rule to the editor and returns to it. */
    private void addRule(String src, String dst)
    {
        this.edit.appendRule(src + "=" + dst);
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