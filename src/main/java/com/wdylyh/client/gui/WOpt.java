package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.wdylyh.config.Cfg;
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
import net.minecraft.text.Text;

/**
 * Config option row for the filter lists.
 *
 * Only the STRING_LIST rows are customized. Every list follows the input mode
 * selected in {@link Cfg.G.FILTER_INPUT_MODE}:
 *
 * <ul>
 *   <li>the icon picker lists (every render category filter plus the HUD
 *       elements list) open the picker in view mode and an inline text field
 *       in manual mode;</li>
 *   <li>the replacement lists open the full screen replacement editor in view
 *       mode and an inline "source=target" text field in manual mode.</li>
 * </ul>
 *
 * The name based STRING rows get a placeholder advertising the semicolon
 * separator.
 */
public class WOpt extends WidgetConfigOption
{
    public WOpt(int x, int y, int width, int height, int lw, int cw,
            ConfigOptionWrapper w, int idx, IKeybindConfigGui host,
            WidgetListConfigOptionsBase<?, ?> parent)
    {
        super(x, y, width, height, lw, cw, w, idx, host, parent);
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
        if (config == Cfg.G.REVEAL_AFFECTED_TYPES)
        {
            ATypeBtn btn = new ATypeBtn(x, y, cw, 20,
                    (IConfigStringList) config, this.host, this.host.getDialogHandler());
            this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
            return;
        }

        // The replacement lists have their own editor. In view selection mode
        // the button opens the full screen replacement list (add via a two-step
        // picker flow, delete per row); in manual mode the rules are typed into
        // the inline "source=target" field.
        RplScreen.RKind rk = RplScreen.RKind.of((IConfigStringList) config);

        if (rk != null)
        {
            if (Cfg.G.FILTER_INPUT_MODE.getOptionValue() == Cfg.G.INPUT_MODE_MANUAL)
            {
                addManualField(x, y, cw, rk.getCfg());
            }
            else
            {
                RplBtn btn = new RplBtn(x, y, cw, 20,
                        rk.getCfg(), this.host, this.host.getDialogHandler());
                this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
            }

            return;
        }

        // The icon picker only knows the FKind lists (the filtered lists of
        // every render category plus the HUD elements list). Any other string
        // list without an FKind (or one that has no picker match) is edited as
        // an inline text field regardless of the input mode.
        boolean man = needMan((IConfigStringList) config);
        boolean manIn = Cfg.G.FILTER_INPUT_MODE.getOptionValue() == Cfg.G.INPUT_MODE_MANUAL;

        if (man || manIn)
        {
            addManualField(x, y, cw, (IConfigStringList) config);
        }
        else
        {
            FPickBtn btn = new FPickBtn(x, y, cw, 20,
                    (IConfigStringList) config, this.host, this.host.getDialogHandler());
            this.addConfigButtonEntry(x + cw + 2, y, (IConfigResettable) config, btn);
        }
    }

    /**
     * Inline text field for a string list. The entries are joined into a
     * single semicolon separated string, edited in place and split back on
     * apply (enter, scrolling away or switching tabs).
     */
    private void addManualField(int x, int y, int cw, IConfigStringList cfg)
    {
        String init = String.join(";", cfg.getStrings());

        GuiTextFieldGeneric field = this.createTextField(x, y + 1, cw - 4, 17);
        field.setMaxLengthWrapper(this.maxTextfieldTextLength);
        field.setValueWrapper(init);
        field.setPlaceholder(Text.translatable("reignrender.gui.filter.placeholder.semicolon"));

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
                List<String> ids = new ArrayList<>();

                for (String p : this.textField.textField().getValueWrapper().split(";"))
                {
                    String id = p.trim();

                    if (!id.isEmpty())
                    {
                        ids.add(id);
                    }
                }

                if (!ids.equals(cfg.getStrings()))
                {
                    cfg.setStrings(ids);
                }
            }

            // Normalize the field (spacing may have been entered around the
            // separators) and remember it as the applied baseline.
            this.lastAppliedValue = String.join(";", cfg.getStrings());
            this.textField.textField().setValueWrapper(this.lastAppliedValue);
            return;
        }

        super.applyNewValueToConfig();
    }

    // A list needs the manual text input iff the icon picker has no FKind
    // for it. Checking membership against the enum instead of listing every
    // list keeps this correct for any future string list.
    private static boolean needMan(IConfigStringList config)
    {
        for (GPicker.FKind kind : GPicker.FKind.values())
        {
            if (config == kind.getConfig())
            {
                return false;
            }
        }

        return true;
    }

    /** Keeps the reset button enabled state in sync with the typed text. */
    private static final class FieldLis implements ITextFieldListener<GuiTextFieldGeneric>
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
            WOpt.this.lastAppliedValue = String.join(";", this.cfg.getStrings());
            this.field.setValueWrapper(WOpt.this.lastAppliedValue);
        }
    }
}