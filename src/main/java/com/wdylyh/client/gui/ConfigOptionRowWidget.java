package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IConfigInfoProvider;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.text.Text;

/**
 * Config option row for the filter lists.
 *
 * Only the STRING_LIST rows are customized. Every list follows the input mode
 * selected in {@link RenderConfig.General.FILTER_INPUT_MODE}:
 *
 * <ul>
 *   <li>the icon picker lists (every render category filter plus the HUD
 *       elements list) open the picker in view mode and an inline text field
 *       in manual mode;</li>
 *   <li>the replacement lists open the full screen replacement editor in view
 *       mode and an inline "source=target" text field in manual mode;</li>
 *   <li>the condition entries open their own full screen list editor in both
 *       modes, since an entry contains semicolon separated key=value fields
 *       itself.</li>
 * </ul>
 *
 * The name based STRING rows get a placeholder advertising the semicolon
 * separator.
 */
public class ConfigOptionRowWidget extends WidgetConfigOption
{
    /** Inline field height; the error hint line is drawn right below it. */
    private static final int FIELD_HEIGHT = 17;

    /** Error hint line drawn below the field; {@code null} when none. */
    private String hint;

    public ConfigOptionRowWidget(int x, int y, int width, int height, int lw, int cw,
            ConfigOptionWrapper w, int idx, IKeybindConfigGui host,
            WidgetListConfigOptionsBase<?, ?> parent)
    {
        super(x, y, width, height, lw, cw, w, idx, host, parent);
    }

    @Override
    public void render(GuiContext guiContext, int mouseX, int mouseY, boolean selected)
    {
        super.render(guiContext, mouseX, mouseY, selected);

        // The rejected entries are reported as a small line below the field
        // instead of a toast, so they stay attached to the input being fixed.
        if (this.hint != null && this.textField != null)
        {
            GuiTextFieldGeneric f = this.textField.textField();
            this.drawStringWithShadow(guiContext,
                    f.getXWrapper(), f.getYWrapper() + FIELD_HEIGHT + 1, 0xFFE36B6B, this.hint);
        }
    }

    @Override
    protected void addConfigOption(int x, int y, int lw, int cw, IConfigBase config)
    {
        if (config.getType() == ConfigType.STRING)
        {
            super.addConfigOption(x, y, lw, cw, config);

            // Name based lists (name tags / players) use semicolon separated
            // input; advertise that in the field placeholder
            if (this.textField != null && this.textField.textField() != null)
            {
                this.textField.textField().setPlaceholder(Text.translatable("reignrender.gui.filter.placeholder.names"));
            }

            return;
        }

        if (config.getType() != ConfigType.STRING_LIST)
        {
            super.addConfigOption(x, y, lw, cw, config);
            return;
        }

        y += 1;

        String cname = config.getConfigGuiDisplayName();
        this.addLabel(x, y + 7, lw, 8, 0xFFFFFFFF, cname);

        IConfigInfoProvider prov = this.host.getHoverInfoProvider();
        String cmt = prov != null ? prov.getHoverInfo(config) : config.getComment();

        if (cmt != null)
        {
            this.addConfigComment(x, y + 5, lw, 12, cmt);
        }

        x += lw + 10;

        // The hotkey affected types list has its own multi-select picker,
        // independent of the filter list input mode.
        if (config == RenderConfig.General.REVEAL_AFFECTED_TYPES)
        {
            AffectedTypeButton btn = new AffectedTypeButton(x, y, cw, 20,
                    (IConfigStringList) config, this.host, this.host.getDialogHandler());
            this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
            return;
        }

        // The condition entries list has its own full screen editor. Unlike
        // the replacement rules ("src=dst") an entry contains semicolon
        // separated key=value fields itself ("region=...;dist=...;acts=..."),
        // so it cannot be represented unambiguously in the inline
        // semicolon-joined field: the editor is used in both input modes.
        if (config == RenderConfig.Conditions.CONDITION_ENTRIES)
        {
            ConditionListButton btn = new ConditionListButton(x, y, cw, 20,
                    RenderConfig.Conditions.CONDITION_ENTRIES, this.host, this.host.getDialogHandler());
            this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
            return;
        }

        // The replacement lists have their own editor. In view selection mode
        // the button opens the full screen replacement list (add via a two-step
        // picker flow, delete per row); in manual mode the rules are typed into
        // the inline "source=target" field.
        ReplaceListScreen.ReplaceKind replaceKind = ReplaceListScreen.ReplaceKind.of((IConfigStringList) config);

        if (replaceKind != null)
        {
            if (RenderConfig.General.FILTER_INPUT_MODE.getOptionValue() == RenderConfig.General.INPUT_MODE_MANUAL)
            {
                addManualField(x, y, cw, replaceKind.getCfg(),
                        "reignrender.gui.filter.placeholder.rule");
            }
            else
            {
                ReplaceListButton btn = new ReplaceListButton(x, y, cw, 20,
                        replaceKind.getCfg(), this.host, this.host.getDialogHandler());
                this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
            }

            return;
        }

        // The face-mod category lists open the full screen id list editor
        // regardless of the input mode; the add flow inside that screen itself
        // switches between the visualized picker and typed ids.
        FaceModListScreen.FaceKind faceKind = FaceModListScreen.FaceKind.of((IConfigStringList) config);

        if (faceKind != null)
        {
            FaceListButton btn = new FaceListButton(x, y, cw, 20,
                    faceKind.getCfg(), this.host, this.host.getDialogHandler());
            this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
            return;
        }

        // The icon picker only knows the FKind lists (the filtered lists of
        // every render category plus the HUD elements list). Any other string
        // list without an FKind (or one that has no picker match) is edited as
        // an inline text field regardless of the input mode.
        boolean man = needMan((IConfigStringList) config);
        boolean manIn = RenderConfig.General.FILTER_INPUT_MODE.getOptionValue() == RenderConfig.General.INPUT_MODE_MANUAL;

        if (man || manIn)
        {
            addManualField(x, y, cw, (IConfigStringList) config);
        }
        else
        {
            FilterPickButton btn = new FilterPickButton(x, y, cw, 20,
                    (IConfigStringList) config, this.host, this.host.getDialogHandler());
            this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
        }
    }

    private void addManualField(int x, int y, int cw, IConfigStringList cfg)
    {
        addManualField(x, y, cw, cfg, "reignrender.gui.filter.placeholder.semicolon");
    }

    /**
     * Inline text field for a string list. The entries are joined into a
     * single semicolon separated string, edited in place and split back on
     * apply (enter, scrolling away or switching tabs).
     */
    private void addManualField(int x, int y, int cw, IConfigStringList cfg, String placeholderKey)
    {
        String init = String.join(";", cfg.getStrings());

        GuiTextFieldGeneric field = this.createTextField(x, y + 1, cw - 4, FIELD_HEIGHT);
        field.setMaxLengthWrapper(this.maxTextfieldTextLength);
        field.setValueWrapper(init);
        field.setPlaceholder(Text.translatable(placeholderKey));

        this.lastAppliedValue = init;

        ButtonGeneric reset = this.createResetButton(x + cw + 2, y, (IConfigResettable) cfg);

        // Register the field manually instead of addTextField(): the base helper
        // only accepts its own ConfigOptionChangeListenerTextField, which does
        // not fit a string list config.
        TextFieldWrapper<GuiTextFieldGeneric> wrapper = new TextFieldWrapper<>(field, new FieldLis(cfg, reset), TextFieldType.STRING);
        this.textField = wrapper;
        this.parent.addTextField(wrapper);

        ConfigOptionListenerResetConfig action = new ConfigOptionListenerResetConfig(
                (IConfigResettable) cfg, new FieldReset(cfg, field), reset, null);
        this.addButton(reset, action);
    }

    @Override
    public void applyNewValueToConfig()
    {
        if (this.wrapper.getConfig() instanceof IConfigStringList cfg)
        {
            if (this.textField != null && this.hasPendingModifications())
            {
                String raw = this.textField.textField().getValueWrapper();
                List<String> ids = new ArrayList<>();
                List<String> bad = new ArrayList<>();
                Function<String, String> check = manualCheck(cfg);

                for (String p : raw.split(";"))
                {
                    String part = p.trim();

                    if (part.isEmpty())
                    {
                        continue;
                    }

                    String id = check.apply(part);

                    if (id != null && !id.isEmpty())
                    {
                        ids.add(id);
                    }
                    else
                    {
                        bad.add(part);
                    }
                }

                if (!bad.isEmpty())
                {
                    // Never wipe the typed text on a failed parse: keep the
                    // draft so the offending entry can be corrected, and show a
                    // hint below the field listing what was rejected (and why)
                    // instead of silently deleting it. Marking the raw text as
                    // the applied baseline keeps later apply attempts (clicking
                    // away, scrolling) from re-parsing and re-notifying until
                    // the text changes.
                    this.lastAppliedValue = raw;
                    reject(cfg, bad);
                    return;
                }

                this.hint = null;

                if (!ids.equals(cfg.getStrings()))
                {
                    cfg.setStrings(ids);
                }
            }

            // Normalize the field (spacing may have been entered around the
            // separators) and remember it as the applied baseline.
            this.lastAppliedValue = String.join(";", cfg.getStrings());

            if (this.textField != null)
            {
                this.textField.textField().setValueWrapper(this.lastAppliedValue);
            }

            return;
        }

        super.applyNewValueToConfig();
    }

    /**
     * Keeps the rejected entries as a hint line below the field; the typed
     * draft itself stays untouched.
     */
    private void reject(IConfigStringList cfg, List<String> bad)
    {
        String key;

        if (ReplaceListScreen.ReplaceKind.of(cfg) != null)
        {
            key = "reignrender.message.manualInvalidRule";
        }
        else
        {
            key = "reignrender.message.manualInvalidId";
        }

        this.hint = Text.translatable(key, String.join(";", bad)).getString();
    }

    /**
     * Validator for the manual input field, mirroring the /render list command
     * and the view picker: an id typed by hand must be offered by the current
     * entry's view selector (the block list only accepts blocks, the falling
     * block list only falling blocks, ...). When any entry fails validation the
     * whole draft is kept in the field and reported, so nothing the user typed
     * is silently dropped. Lists without a picker range accept any non-empty
     * entry.
     */
    private static Function<String, String> manualCheck(IConfigStringList cfg)
    {
        // Replacement rules ("src=dst") take precedence: the falling block and
        // item entity replace lists are also picker kinds, but store a rule.
        ReplaceListScreen.ReplaceKind replaceKind = ReplaceListScreen.ReplaceKind.of(cfg);

        if (replaceKind != null)
        {
            if (replaceKind.isText())
            {
                return ConfigOptionRowWidget::anyPair;
            }

            IconGridPicker.FKind kind = replaceKind.getKind();
            return s -> rule(kind, s);
        }

        IconGridPicker.FKind fk = IconGridPicker.FKind.of(cfg);

        if (fk != null)
        {
            return s -> id(fk, s);
        }

        return s -> s.isEmpty() ? null : s;
    }

    /**
     * Filters a single id for a picker-backed list: it must be offered by the
     * picker kind's view selector. The fog and HUD candidates include ids that
     * are not registry entries ("water", "effect:blindness", "bossbar", ...)
     * and stay as-is; every other picker kind stores the fully namespaced id,
     * like the view picker and the command do.
     */
    private static String id(IconGridPicker.FKind kind, String raw)
    {
        String norm = (kind == IconGridPicker.FKind.HUD
                || (kind == IconGridPicker.FKind.FOGS && IconGridPicker.FOG_SPECIAL.contains(raw)))
                ? raw
                : (raw.indexOf(':') >= 0 ? raw : "minecraft:" + raw);

        return IconGridPicker.matches(kind, norm) ? norm : null;
    }

    /** Filters a "src=dst" replacement rule for a picker-backed category. */
    private static String rule(IconGridPicker.FKind kind, String raw)
    {
        int eq = raw.indexOf('=');

        if (eq <= 0 || eq >= raw.length() - 1)
        {
            return null;
        }

        String src = id(kind, raw.substring(0, eq).trim());
        String tgt = id(kind, raw.substring(eq + 1).trim());

        return src != null && tgt != null ? src + "=" + tgt : null;
    }

    /** Any non-empty "src=dst" for the text replacement lists (name tags). */
    private static String anyPair(String raw)
    {
        int eq = raw.indexOf('=');

        if (eq <= 0 || eq >= raw.length() - 1)
        {
            return null;
        }

        String src = raw.substring(0, eq).trim();
        String tgt = raw.substring(eq + 1).trim();

        return src.isEmpty() || tgt.isEmpty() ? null : src + "=" + tgt;
    }

    // A list needs the manual text input iff the icon picker has no FKind
    // for it. Checking membership against the enum instead of listing every
    // list keeps this correct for any future string list.
    private static boolean needMan(IConfigStringList config)
    {
        return IconGridPicker.FKind.of(config) == null;
    }

    /**
     * Keeps the reset button enabled state in sync with the typed text and
     * clears an outdated error hint as soon as the input is edited again.
     */
    private final class FieldLis implements ITextFieldListener<GuiTextFieldGeneric>
    {
        private final IConfigStringList cfg;
        private final ButtonGeneric reset;

        FieldLis(IConfigStringList cfg, ButtonGeneric reset)
        {
            this.cfg = cfg;
            this.reset = reset;
        }

        @Override
        public boolean onTextChange(GuiTextFieldGeneric field)
        {
            ConfigOptionRowWidget.this.hint = null;
            this.reset.setEnabled(!String.join(";", this.cfg.getStrings()).equals(field.getValueWrapper()));
            return false;
        }
    }

    /** Restores the field text after the config has been reset. */
    private final class FieldReset extends ConfigOptionListenerResetConfig.ConfigResetterBase
    {
        private final IConfigStringList cfg;
        private final GuiTextFieldGeneric field;

        FieldReset(IConfigStringList cfg, GuiTextFieldGeneric field)
        {
            this.cfg = cfg;
            this.field = field;
        }

        @Override
        public void resetConfigOption()
        {
            ConfigOptionRowWidget.this.hint = null;
            ConfigOptionRowWidget.this.lastAppliedValue = String.join(";", this.cfg.getStrings());
            this.field.setValueWrapper(ConfigOptionRowWidget.this.lastAppliedValue);
        }
    }
}