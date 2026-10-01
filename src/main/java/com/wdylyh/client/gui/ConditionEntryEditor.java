package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Condition entry editor: one line of the unified condition system
 * ({@link RenderConfig.Conditions#CONDITION_ENTRIES}). Used by
 * {@link ConditionListScreen} when adding a new entry (empty fields) and when
 * editing an existing one (pre-filled from the current entry line).
 *
 * <p>An entry combines optional conditions (region box, distance limit, count
 * limit, attached ids) with the actions applied to the objects that satisfy
 * them: hide ("禁止渲染"), keep ("白名单"), face ("面修改") and one or more
 * replace rules ("替换渲染", "src=dst"). Confirming serializes the fields back
 * into the {@code key=value} line format
 * ({@code region=...;dist=...;count=...;ids=a,b;acts=hide,face,replace:s=d})
 * and appends it to (or replaces the edited index in) the condition list.
 *
 * <p>Invalid input is rejected with an inline error line while the typed draft
 * stays in the fields, so nothing invalid is silently dropped. The ids and the
 * replace rules can also be added by view selection: the id picker button
 * opens a category chooser followed by the {@link IconGridPicker} grid, the
 * rule picker button opens the {@link ReplaceCategoryPicker} two-step flow.
 * When editing an entry with the face action, every attached id that resolves
 * to a registry entry gets a Textures button jumping into its region texture
 * list ({@link FaceModTextureScreen}).
 */
public class ConditionEntryEditor extends GuiBase
{
    /** Index of the entry to edit, or null to append a new entry. */
    private final Integer index;
    private final Screen back;

    // Draft text kept across the failure-initGui rebuild and across the
    // picker detours, so a rejected or in-progress input stays in the fields.
    // The fields are only pre-filled from the existing entry on the first
    // init; a later rebuild keeps what the user typed.
    private String regionVal = "";
    private String distVal = "";
    private String countVal = "";
    private String idsVal = "";
    private String ruleVal = "";
    private boolean hideOn;
    private boolean keepOn;
    /** 默认开启的黑名单模式：区域内隐藏所列 id（与白名单单选互斥）。 */
    private boolean blacklistOn = true;
    private boolean faceOn;
    private boolean prefilled;

    /** Inline error line shown above the fields, or null. */
    private String err;

    private GuiTextFieldGeneric regionField;
    private GuiTextFieldGeneric distField;
    private GuiTextFieldGeneric countField;
    private GuiTextFieldGeneric idsField;
    private GuiTextFieldGeneric ruleField;

    public ConditionEntryEditor(Integer index, Screen back)
    {
        this.index = index;
        this.back = back;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(this.back);
        this.setTitle(StringUtils.translate("reignrender.gui.condition.edit.title"));

        if (!this.prefilled)
        {
            if (this.index != null)
            {
                List<String> list = RenderConfig.Conditions.CONDITION_ENTRIES.getStrings();

                if (this.index >= 0 && this.index < list.size())
                {
                    this.prefill(list.get(this.index));
                }
            }

            this.prefilled = true;
        }

        // Adaptive layout: the editor must fit every GUI scale. The column
        // starts left of center (label + field + side button), the row flow
        // is centered vertically, and the texture rows are capped to what
        // still fits on screen.
        int labelW = 80;
        int sideW = 70;
        int fieldW = Math.min(280, this.width - 20 - labelW - sideW - 8);
        int colW = labelW + 2 + fieldW + 4 + sideW;
        int lx = Math.max(10, (this.width - colW) / 2);
        int fx = lx + labelW + 2;
        int bx = fx + fieldW + 4;

        // The texture rows of an edited face entry, resolved up front so the
        // vertical centering accounts for them.
        List<RegionFaceEngineId> textureIds = new ArrayList<>();

        if (this.index != null && this.faceOn)
        {
            for (String id : this.splitIds(this.idsVal))
            {
                RegionFaceEngineId parsed = parseRegionId(id);

                if (parsed != null)
                {
                    textureIds.add(parsed);
                }
            }
        }

        int rowH = 22;                    // one field row
        int hintH = 12;
        int btnRowH = 26;                 // confirm / cancel row
        int texH = textureIds.isEmpty() ? 0 : 8 + textureIds.size() * 18;
        int totalH = (this.err != null ? 16 : 0) + 6 * rowH + hintH + btnRowH + texH;
        int y = Math.max(34, (this.height - totalH) / 2);

        if (this.err != null)
        {
            this.addLabel(lx, y, colW, 10, 0xFFE36B6B, this.err);
            y += 16;
        }

        // Region box
        this.addLabel(lx, y + 4, labelW, 10, 0xB0FFFFFF,
                StringUtils.translate("reignrender.gui.condition.label.region"));
        this.regionField = new GuiTextFieldGeneric(fx, y, fieldW, 16, this.textRenderer);
        this.regionField.setValueWrapper(this.regionVal);
        this.regionField.setPlaceholder(Text.translatable("reignrender.gui.condition.placeholder.region"));
        this.addTextField(this.regionField, f -> false);
        y += rowH;

        // Distance limit
        this.addLabel(lx, y + 4, labelW, 10, 0xB0FFFFFF,
                StringUtils.translate("reignrender.gui.condition.label.dist"));
        this.distField = new GuiTextFieldGeneric(fx, y, fieldW, 16, this.textRenderer);
        this.distField.setValueWrapper(this.distVal);
        this.distField.setPlaceholder(Text.translatable("reignrender.gui.condition.placeholder.dist"));
        this.addTextField(this.distField, f -> false);
        y += rowH;

        // Count limit
        this.addLabel(lx, y + 4, labelW, 10, 0xB0FFFFFF,
                StringUtils.translate("reignrender.gui.condition.label.count"));
        this.countField = new GuiTextFieldGeneric(fx, y, fieldW, 16, this.textRenderer);
        this.countField.setValueWrapper(this.countVal);
        this.countField.setPlaceholder(Text.translatable("reignrender.gui.condition.placeholder.count"));
        this.addTextField(this.countField, f -> false);
        y += rowH;

        // Attached ids + view picker
        this.addLabel(lx, y + 4, labelW, 10, 0xB0FFFFFF,
                StringUtils.translate("reignrender.gui.condition.label.ids"));
        this.idsField = new GuiTextFieldGeneric(fx, y, fieldW, 16, this.textRenderer);
        this.idsField.setValueWrapper(this.idsVal);
        this.idsField.setPlaceholder(Text.translatable("reignrender.gui.condition.placeholder.ids"));
        this.addTextField(this.idsField, f -> false);
        this.addButton(new ButtonGeneric(bx, y, sideW, 16,
                        StringUtils.translate("reignrender.gui.condition.ids.add")),
                       (b, m) ->
                       {
                           this.readFields();
                           this.err = null;
                           GuiBase.openGui(new IdKindPicker(this));
                       });
        y += rowH;

        // Actions
        this.addLabel(lx, y + 4, labelW, 10, 0xB0FFFFFF,
                StringUtils.translate("reignrender.gui.condition.label.acts"));
        int actW = (fieldW + 4 - 12) / 4;
        int ax = fx;
        ax += this.addActToggle(ax, y, actW, "hide", this.hideOn) + 4;
        ax += this.addActToggle(ax, y, actW, "blacklist", this.blacklistOn) + 4;
        ax += this.addActToggle(ax, y, actW, "keep", this.keepOn) + 4;
        this.addActToggle(ax, y, actW, "face", this.faceOn);
        y += rowH;

        // Replace rules + picker
        this.addLabel(lx, y + 4, labelW, 10, 0xB0FFFFFF,
                StringUtils.translate("reignrender.gui.condition.label.rules"));
        this.ruleField = new GuiTextFieldGeneric(fx, y, fieldW, 16, this.textRenderer);
        this.ruleField.setValueWrapper(this.ruleVal);
        this.ruleField.setPlaceholder(Text.translatable("reignrender.gui.condition.placeholder.rules"));
        this.addTextField(this.ruleField, f -> false);
        this.addButton(new ButtonGeneric(bx, y, sideW, 16,
                        StringUtils.translate("reignrender.gui.condition.pickRule")),
                       (b, m) ->
                       {
                           this.readFields();
                           this.err = null;
                           GuiBase.openGui(new ReplaceCategoryPicker(this));
                       });
        y += rowH;

        this.addLabel(fx, y + 1, fieldW + sideW, 10, 0x80FFFFFF,
                StringUtils.translate("reignrender.gui.condition.hint"));
        y += hintH;

        int doneW = Math.min(124, (colW - 8) / 2);
        this.addButton(new ButtonGeneric(fx, y, doneW, false,
                        this.index == null ? "reignrender.gui.condition.add" : "reignrender.gui.replace.finish"),
                       (b, m) -> this.confirm());
        // 4-arg (x, y, w, boolean, key) variant: the boolean is the alignment
        // (false = left-aligned at x). The (x, y, w, h, String) overload takes
        // the string literally instead of translating it, which rendered the
        // translation key as the button text.
        this.addButton(new ButtonGeneric(fx + doneW + 8, y, doneW, false, "reignrender.gui.replace.cancel"),
                       (b, m) -> GuiBase.openGui(this.back));
        y += btnRowH;

        // Editing an entry with the face action: one row per attached id that
        // resolves to a registry entry, jumping into its region texture list
        // (the pixel editor chain). Rows that would run past the bottom of
        // the screen are dropped.
        int ty = y + 4;

        for (RegionFaceEngineId parsed : textureIds)
        {
            if (ty + 16 > this.height - 4)
            {
                break;
            }

            String fid = parsed.id();
            this.addLabel(fx, ty + 3, fieldW, 10, 0xFFFFFFFF, fid);
            this.addButton(new ButtonGeneric(bx, ty, sideW, 16,
                            StringUtils.translate("reignrender.gui.face.textures")),
                           (b, m) -> GuiBase.openGui(new FaceModTextureScreen(kindOf(parsed), fid)));
            ty += 18;
        }
    }

    /**
     * Drag handling restricted to the focused text field.
     *
     * malilib's GuiBase forwards a drag to every registered text field in
     * registration order and short-circuits on the first one that claims it.
     * The vanilla drag-select state is set on press but never cleared for
     * fields — malilib forwards the release to widgets only, never to text
     * fields — so every previously clicked field stays "selecting" forever.
     * The next drag is then swallowed by such a stale, unfocused field,
     * which moves its own selection to the mouse position: text ends up
     * selected in the wrong box and drag-selection in the just-clicked
     * field dies. Forwarding only to the focused field fixes both.
     */
    @Override
    public boolean onMouseDragged(Click click, double dragXAmount, double dragYAmount)
    {
        for (GuiTextFieldGeneric f : java.util.Arrays.asList(
                this.regionField, this.distField, this.countField, this.idsField, this.ruleField))
        {
            if (f != null && f.isFocusedWrapper())
            {
                return f.mouseDragged(click, dragXAmount, dragYAmount);
            }
        }

        // No focused field: skip malilib's shared text-field loop entirely
        // (a stale field would swallow the drag there); this editor has no
        // button that needs drag events.
        return true;
    }

    /** Adds one "[x]/[ ] action" toggle button, returning its width. */
    private int addActToggle(int x, int y, int width, String act, boolean on)
    {
        ButtonGeneric btn = new ButtonGeneric(x, y, width, 16,
                (on ? "[x] " : "[ ] ") + StringUtils.translate("reignrender.gui.condition.act." + act));

        this.addButton(btn, (b, m) ->
        {
            this.readFields();

            // 黑名单与白名单单选绑定：同一时刻只能开启其中一个；hide（整区域
            // 禁止渲染）与白名单互斥（两者会为同一区域生成矛盾的坐标行）。
            if (act.equals("hide"))
            {
                this.hideOn = !this.hideOn;

                if (this.hideOn && this.keepOn)
                {
                    // hide 与白名单互斥；被顶掉的白名单交还给黑名单，保证
                    // 黑名单/白名单任意时刻恰好开启一个。
                    this.keepOn = false;
                    this.blacklistOn = true;
                }
            }
            else if (act.equals("blacklist"))
            {
                this.blacklistOn = true;
                this.keepOn = false;
            }
            else if (act.equals("keep"))
            {
                this.keepOn = true;
                this.blacklistOn = false;
                this.hideOn = false;
            }
            else
            {
                this.faceOn = !this.faceOn;
            }

            this.err = null;
            this.prefilled = true;
            GuiBase.openGui(this);
        });

        return btn.getWidth();
    }

    /** Copies the current field texts into the draft values. */
    private void readFields()
    {
        this.regionVal = this.regionField != null ? this.regionField.getValueWrapper().trim() : this.regionVal;
        this.distVal = this.distField != null ? this.distField.getValueWrapper().trim() : this.distVal;
        this.countVal = this.countField != null ? this.countField.getValueWrapper().trim() : this.countVal;
        this.idsVal = this.idsField != null ? this.idsField.getValueWrapper().trim() : this.idsVal;
        this.ruleVal = this.ruleField != null ? this.ruleField.getValueWrapper().trim() : this.ruleVal;
    }

    /** Pre-fills the drafts from one existing condition line. */
    private void prefill(String line)
    {
        for (String part : line.split(";", -1))
        {
            part = part.trim();

            int eq = part.indexOf('=');

            if (eq <= 0 || eq >= part.length() - 1)
            {
                continue;
            }

            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = part.substring(eq + 1).trim();

            switch (key)
            {
                case "region", "box" -> this.regionVal = val;
                case "dist", "distance" -> this.distVal = val;
                case "count" -> this.countVal = val;
                case "ids", "id" -> this.idsVal = val;
                case "acts", "act", "action", "actions" ->
                {
                    for (String act : val.split(","))
                    {
                        act = act.trim();

                        if (act.equalsIgnoreCase("hide"))
                        {
                            this.hideOn = true;
                        }
                        else if (act.equalsIgnoreCase("blacklist"))
                        {
                            this.blacklistOn = true;
                        }
                        else if (act.equalsIgnoreCase("keep") || act.equalsIgnoreCase("whitelist"))
                        {
                            this.keepOn = true;
                        }
                        else if (act.equalsIgnoreCase("face") || act.equalsIgnoreCase("facemod"))
                        {
                            this.faceOn = true;
                        }
                        else if (act.length() > 8 && act.regionMatches(true, 0, "replace:", 0, 8))
                        {
                            String rule = act.substring(8);

                            if (!rule.isEmpty())
                            {
                                this.ruleVal = this.ruleVal.isEmpty() ? rule : this.ruleVal + ";" + rule;
                            }
                        }
                    }
                }
                default ->
                {
                }
            }
        }

        // Legacy entries written before the blacklist toggle only carry
        // "acts=hide": hand the default mode back to the blacklist so the
        // blacklist/whitelist radio pair always shows exactly one active.
        if (this.hideOn && !this.blacklistOn && !this.keepOn)
        {
            this.blacklistOn = true;
        }
    }

    /**
     * Appends a "source=target" rule picked in view selection mode and reopens
     * this editor, so the rule shows up in the existing field next to any
     * manually typed ones. Called at the end of the {@link ReplaceCategoryPicker}
     * two-step picker flow; the re-init keeps the draft, only the rule field
     * gains one more entry.
     */
    public void appendRule(String rule)
    {
        this.ruleVal = this.ruleVal.isEmpty() ? rule : this.ruleVal + ";" + rule;
        this.err = null;
        this.prefilled = true;
        GuiBase.openGui(this);
    }

    /**
     * Appends one id picked in the {@link IdKindPicker} grid and reopens this
     * editor. The ids are comma separated in the config line.
     */
    public void appendId(String id)
    {
        this.idsVal = this.idsVal.isEmpty() ? id : this.idsVal + "," + id;
        this.err = null;
        this.prefilled = true;
        GuiBase.openGui(this);
    }

    /**
     * Registry existence check for a category-prefixed replace rule id
     * ("blocks:stone=glass"). A missing namespace falls back to "minecraft:",
     * mirroring the engine's id handling. Text categories (name tags / player
     * names) and the fog category's state-dependent biome ids always pass.
     */
    private static boolean ruleIdExists(String cat, String id)
    {
        switch (cat)
        {
            case "blocks", "blockentities", "fallingblocks" ->
            {
                return contains(Registries.BLOCK, id);
            }
            case "entities" ->
            {
                return contains(Registries.ENTITY_TYPE, id);
            }
            case "particles" ->
            {
                return contains(Registries.PARTICLE_TYPE, id);
            }
            case "fluids" ->
            {
                return contains(Registries.FLUID, id);
            }
            case "itementities", "helditems", "armor" ->
            {
                return contains(Registries.ITEM, id);
            }
        }

        // "fog" (biome existence is world-state dependent, format-only check
        // happens through the Identifier parse inside contains), "nametags"
        // and "playernames" (plain text rules) are not registry-backed here.
        return true;
    }

    private static boolean contains(net.minecraft.registry.Registry<?> r, String id)
    {
        net.minecraft.util.Identifier i = net.minecraft.util.Identifier.tryParse(id);

        if (i == null)
        {
            i = net.minecraft.util.Identifier.tryParse("minecraft:" + id);
        }

        return i != null && r.containsId(i);
    }

    private void confirm()
    {
        this.readFields();

        // ---- conditions ----
        boolean hasRegion = !this.regionVal.isEmpty();

        if (hasRegion && CoordinateFilter.parseBox(this.regionVal) == null)
        {
            this.err = StringUtils.translate("reignrender.gui.condition.err.badRegion");
            this.initGui();
            return;
        }

        int dist = -1;
        int count = -1;

        if (!this.distVal.isEmpty())
        {
            dist = parseNonNegative(this.distVal);

            if (dist < 0)
            {
                this.err = StringUtils.translate("reignrender.gui.condition.err.badNumber",
                        StringUtils.translate("reignrender.gui.condition.label.dist"));
                this.initGui();
                return;
            }
        }

        if (!this.countVal.isEmpty())
        {
            count = parseNonNegative(this.countVal);

            if (count < 0)
            {
                this.err = StringUtils.translate("reignrender.gui.condition.err.badNumber",
                        StringUtils.translate("reignrender.gui.condition.label.count"));
                this.initGui();
                return;
            }
        }

        List<String> ids = this.splitIds(this.idsVal);

        // ---- actions ----
        List<String> rules = new ArrayList<>();

        for (String p : this.ruleVal.split(";"))
        {
            String rule = p.trim();

            if (rule.isEmpty())
            {
                continue;
            }

            // Optional category prefix ("blocks:stone=glass"): strip it only
            // when the segment before the first colon names a valid replace
            // category. A view-picked rule like "minecraft:anvil=minecraft:gold_block"
            // carries its registry namespace in the same spot, which is not a
            // prefix — fall through to the plain eq check instead of rejecting.
            String probe = rule;
            int colon = probe.indexOf(':');
            if (colon > 0)
            {
                String maybe = probe.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                if (com.wdylyh.config.ReplacementEngine.COORD_REPLACE_CATEGORIES.contains(maybe))
                {
                    probe = probe.substring(colon + 1);
                }
            }

            int eq = probe.indexOf('=');

            if (eq <= 0 || eq >= probe.length() - 1)
            {
                this.err = Text.translatable("reignrender.gui.condition.err.badRule", rule).getString();
                this.initGui();
                return;
            }

            // An explicit category prefix pins the rule to one registry-backed
            // category, so a mistyped source/target id would become a silent
            // dead rule (the engine stores it but it can never match). Validate
            // the ids against that registry here. Un-prefixed rules live in the
            // shared bucket where unregistered ids are intentional (text rules
            // for name tags / player names), so they are not checked.
            if (colon > 0)
            {
                String maybe2 = rule.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                if (com.wdylyh.config.ReplacementEngine.COORD_REPLACE_CATEGORIES.contains(maybe2))
                {
                    String src = probe.substring(0, eq).trim();
                    String dst = probe.substring(eq + 1).trim();

                    if (!ruleIdExists(maybe2, src) || !ruleIdExists(maybe2, dst))
                    {
                        this.err = Text.translatable("reignrender.gui.condition.err.badRule", rule).getString();
                        this.initGui();
                        return;
                    }
                }
            }

            rules.add(rule);
        }

        // hide/keep/face/rules are region-bound actions. The blacklist toggle
        // defaults to on but silently detaches when the entry has no region
        // (the engine drops hide without a box the same way), so a pure
        // distance/count entry stays saveable.
        boolean regionActs = this.hideOn || this.keepOn || this.faceOn || !rules.isEmpty();
        boolean hasAct = regionActs || (this.blacklistOn && hasRegion);

        if (!hasAct && dist < 0 && count < 0)
        {
            this.err = StringUtils.translate("reignrender.gui.condition.err.noAct");
            this.initGui();
            return;
        }

        // Region actions require a region, mirroring the engine's parser that
        // silently drops them otherwise.
        if (regionActs && !hasRegion)
        {
            this.err = StringUtils.translate("reignrender.gui.condition.err.needRegion");
            this.initGui();
            return;
        }

        // keep without ids would hide the whole region, and dist/count without
        // ids cannot match anything: rejected instead of written dead.
        if (this.keepOn && ids.isEmpty())
        {
            this.err = StringUtils.translate("reignrender.gui.condition.err.keepNeedsIds");
            this.initGui();
            return;
        }

        if ((dist >= 0 || count >= 0) && ids.isEmpty())
        {
            this.err = StringUtils.translate("reignrender.gui.condition.err.limitNeedsIds");
            this.initGui();
            return;
        }

        // ---- serialize ----
        StringBuilder line = new StringBuilder();

        if (hasRegion)
        {
            line.append("region=").append(this.regionVal).append(';');
        }

        if (dist >= 0)
        {
            line.append("dist=").append(dist).append(';');
        }

        if (count >= 0)
        {
            line.append("count=").append(count).append(';');
        }

        if (!ids.isEmpty())
        {
            line.append("ids=").append(String.join(",", ids)).append(';');
        }

        List<String> acts = new ArrayList<>();

        if (this.hideOn)
        {
            acts.add("hide");
        }

        if (this.blacklistOn && hasRegion)
        {
            acts.add("blacklist");
        }

        if (this.keepOn)
        {
            acts.add("keep");
        }

        if (this.faceOn)
        {
            acts.add("face");
        }

        for (String rule : rules)
        {
            acts.add("replace:" + rule);
        }

        line.append("acts=").append(String.join(",", acts));

        List<String> cur = new ArrayList<>(RenderConfig.Conditions.CONDITION_ENTRIES.getStrings());

        if (this.index == null)
        {
            cur.add(line.toString());
        }
        else if (this.index >= 0 && this.index < cur.size())
        {
            cur.set(this.index, line.toString());
        }
        else
        {
            return;
        }

        // Writing through the config object fires the change callback, which
        // invalidates the engines and regenerates the region resources when
        // needed.
        RenderConfig.Conditions.CONDITION_ENTRIES.setStrings(cur);
        GuiBase.openGui(new ConditionListScreen());
    }

    private static int parseNonNegative(String val)
    {
        try
        {
            int v = Integer.parseInt(val.trim());
            return v >= 0 ? v : -1;
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    private List<String> splitIds(String raw)
    {
        List<String> ids = new ArrayList<>();

        for (String p : raw.split(","))
        {
            String id = p.trim();

            if (!id.isEmpty())
            {
                ids.add(id);
            }
        }

        return ids;
    }

    /**
     * The category resolution of one attached id (same registries as the
     * engine's id normalization).
     */
    static RegionFaceEngineId parseRegionId(String id)
    {
        Identifier iid = Identifier.tryParse(id.contains(":") ? id.trim().toLowerCase(Locale.ROOT)
                : "minecraft:" + id.trim().toLowerCase(Locale.ROOT));

        if (iid == null)
        {
            return null;
        }

        boolean block = Registries.BLOCK.containsId(iid);
        boolean entity = Registries.ENTITY_TYPE.containsId(iid);
        boolean particle = Registries.PARTICLE_TYPE.containsId(iid);
        boolean item = Registries.ITEM.containsId(iid);

        if (!block && !entity && !particle && !item)
        {
            return null;
        }

        return new RegionFaceEngineId(iid.toString(), block, entity, particle, item);
    }

    /** The region face kind of one attached id (first matching category). */
    static FaceModListScreen.FaceKind kindOf(RegionFaceEngineId id)
    {
        if (id.block())
        {
            return FaceModListScreen.FaceKind.R_BLOCKS;
        }

        if (id.entity())
        {
            return FaceModListScreen.FaceKind.R_ENTITIES;
        }

        if (id.particle())
        {
            return FaceModListScreen.FaceKind.R_PARTICLES;
        }

        return FaceModListScreen.FaceKind.R_ITEMS;
    }

    /** The parsed category flags of one attached id. */
    record RegionFaceEngineId(String id, boolean block, boolean entity, boolean particle, boolean item)
    {
    }

    /**
     * Category chooser for the id view picker: one button per registry
     * category the condition ids can name. The chosen one opens the
     * {@link IconGridPicker} grid in single-select mode; the picked id is
     * appended to the editor's ids field.
     */
    private static class IdKindPicker extends GuiBase
    {
        private static final int PER_COL = 3;

        private final ConditionEntryEditor edit;

        IdKindPicker(ConditionEntryEditor edit)
        {
            this.edit = edit;
        }

        @Override
        public void initGui()
        {
            super.initGui();
            this.setParent(this.edit);
            this.setTitle(StringUtils.translate("reignrender.gui.condition.ids.pick"));

            IconGridPicker.FKind[] kinds = {
                    IconGridPicker.FKind.ENTITIES,
                    IconGridPicker.FKind.BLOCKS,
                    IconGridPicker.FKind.BLOCK_ENTITIES,
                    IconGridPicker.FKind.PARTICLES,
                    IconGridPicker.FKind.ITEM_ENTITIES
            };
            String[] keys = {
                    "entities", "blocks", "blockEntities", "particles", "items"
            };
            int cx = this.width / 2 - 212;

            for (int i = 0; i < kinds.length; i++)
            {
                int r = i % PER_COL;
                int c = i / PER_COL;
                IconGridPicker.FKind kind = kinds[i];
                String key = "reignrender.gui.condition.ids." + keys[i];

                this.addButton(new ButtonGeneric(cx + c * 142, 56 + r * 24, 134, false, key),
                               (b, m) -> GuiBase.openGui(new IconGridPicker(kind, this.edit::appendId,
                                       "reignrender.gui.condition.ids.pick",
                                       "reignrender.gui.condition.ids.confirm", this.edit)));
            }
        }
    }
}
