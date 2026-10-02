package com.wdylyh.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wdylyh.client.gui.IconGridPicker;
import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.util.IdLocalizer;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.KeyCodes;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The /render client command: toggle the render filters, edit the per-type
 * filter lists, rebind the hotkeys, switch the filter modes / generic
 * settings and adjust the numeric limits straight from the chat, without
 * opening the config GUI.
 *
 * <pre>
 * /render option &lt;id&gt; [true/false]
 * /render list &lt;id&gt; [add/remove/set &lt;value...&gt;]
 * /render keybind &lt;id&gt; [key]
 * /render mode [list/others] &lt;id&gt; &lt;mode&gt;
 * </pre>
 */
public final class RenderCommand {

    /** 24 option ids -> the render toggles (20 disable switches + replace + coords + count/distance limit master switches). */
    private static final Map<String, ConfigBoolean> OPTIONS = new LinkedHashMap<>();

    /** 22 list categories (10 filter lists + 11 replace rule lists + the condition entries). */
    private static final Map<String, List_Category> LISTS = new LinkedHashMap<>();

    /** 27 keybind ids (22 hotkeyed option toggles + 5 dedicated hotkeys). */
    private static final Map<String, IHotkey> KEYS = new LinkedHashMap<>();

    /** 9 categories that have a filter mode (the 9 filter lists). */
    private static final Map<String, ConfigOptionValues<BaseOptionListConfigValue>> MODES = new LinkedHashMap<>();

    /** 7 "mode others" configs. */
    private static final Map<String, Other> OTHERS = new LinkedHashMap<>();

    /** Special fog ids without a registry (camera submersion + status effects). */
    private static final Set<String> FOG_SPECIAL = Set.of(
            "water", "lava", "powder_snow", "atmospheric",
            FilterEngine.FOG_EFFECT_BLINDNESS, FilterEngine.FOG_EFFECT_DARKNESS, FilterEngine.FOG_EFFECT_WITHER,
            FilterEngine.FOG_EFFECT_NIGHT_VISION, FilterEngine.FOG_EFFECT_PUMPKIN);

    /** Fixed HUD element ids understood by the HudElementFilter mixin (mirrors IconGridPicker.buildHud). */
    private static final Set<String> HUD_IDS = FilterEngine.HUD_ELEMENT_IDS;

    static {
        registerOpt("particles", RenderConfig.Toggles.DISABLE_PARTICLES);
        registerOpt("text_name", RenderConfig.Toggles.DISABLE_NAME_TAGS);
        registerOpt("player", RenderConfig.Toggles.HIDE_SELF);
        registerOpt("other_players", RenderConfig.Toggles.HIDE_OTHER_PLAYERS);
        registerOpt("hud", RenderConfig.Toggles.DISABLE_HUD_ELEMENTS);
        registerOpt("entities", RenderConfig.Toggles.DISABLE_ENTITIES);
        registerOpt("blocks", RenderConfig.Toggles.DISABLE_BLOCKS);
        registerOpt("fluids", RenderConfig.Toggles.DISABLE_FLUIDS);
        registerOpt("blockEntities", RenderConfig.Toggles.DISABLE_BLOCK_ENTITIES);
        registerOpt("fallingBlocks", RenderConfig.Toggles.DISABLE_FALLING_BLOCKS);
        registerOpt("armor", RenderConfig.Toggles.DISABLE_ARMOR);
        registerOpt("heldItems", RenderConfig.Toggles.DISABLE_HELD_ITEMS);
        registerOpt("elytra", RenderConfig.Toggles.DISABLE_ELYTRA);
        registerOpt("fog", RenderConfig.Toggles.DISABLE_FOG);
        registerOpt("sky", RenderConfig.Toggles.DISABLE_SKY);
        registerOpt("weather", RenderConfig.Toggles.DISABLE_WEATHER);
        registerOpt("items", RenderConfig.Toggles.DISABLE_ITEM_ENTITIES);
        registerOpt("enchantment_glint", RenderConfig.Toggles.DISABLE_GLINT);
        registerOpt("clouds", RenderConfig.Toggles.DISABLE_CLOUDS);
        registerOpt("block_outline", RenderConfig.Toggles.DISABLE_BLOCK_OUTLINE);

        // The replacement master switch (also settable via "mode others
        // replace_enabled"), the coordinate filter master switch and the
        // per-id render count/distance limit master switches.
        registerOpt("replace", RenderConfig.General.REPLACE_ENABLED);
        registerOpt("coords", RenderConfig.Hotkeys.TOGGLE_COORD_FILTER);
        registerOpt("count_limits", RenderConfig.Hotkeys.TOGGLE_COUNT_LIMITS);
        registerOpt("distance_limits", RenderConfig.Hotkeys.TOGGLE_DISTANCE_LIMITS);

        registerList("entities", RenderConfig.Filters.FILTERED_ENTITIES, RenderCommand::normalize_Entity);
        registerList("blocks", RenderConfig.Filters.FILTERED_BLOCKS, RenderCommand::normalize_Block);
        registerList("fluids", RenderConfig.Filters.FILTERED_FLUIDS, RenderCommand::normalize_Fluid);
        registerList("blockEntities", RenderConfig.Filters.FILTERED_BLOCK_ENTITIES, RenderCommand::normalize_Block_Entity);
        registerList("particles", RenderConfig.Filters.FILTERED_PARTICLES, RenderCommand::normalize_Particle);
        registerList("armor", RenderConfig.Filters.FILTERED_ARMOR, RenderCommand::normalize_Armor);
        registerList("fog", RenderConfig.Filters.FILTERED_FOGS, RenderCommand::normalize_Fog);
        registerNames("text_name", RenderConfig.Filters.FILTERED_NAME_TAGS);
        registerNames("other_players", RenderConfig.Filters.FILTERED_PLAYERS);
        registerFixed("hud", RenderConfig.Filters.HIDDEN_HUD_ELEMENTS, HUD_IDS);

        // Universal replacement rule lists: "source=target" entries, validated
        // per category. The text based categories accept any non-empty pair.
        registerList("replace_particles", RenderConfig.Filters.REPLACE_PARTICLES, build_Replace_Validator(RenderCommand::normalize_Particle));
        registerList("replace_blocks", RenderConfig.Filters.REPLACE_BLOCKS, build_Replace_Validator(RenderCommand::normalize_Block));
        registerList("replace_entities", RenderConfig.Filters.REPLACE_ENTITIES, build_Replace_Validator(RenderCommand::normalize_Entity));
        registerList("replace_fogs", RenderConfig.Filters.REPLACE_FOGS, build_Replace_Validator(RenderCommand::normalize_Fog));
        registerList("replace_armor", RenderConfig.Filters.REPLACE_ARMOR, build_Replace_Validator(RenderCommand::normalize_Armor));
        registerList("replace_name_tags", RenderConfig.Filters.REPLACE_NAME_TAGS, replace_Text_Pair_Validator());
        registerList("replace_player_names", RenderConfig.Filters.REPLACE_PLAYER_NAMES, replace_Text_Pair_Validator());
        registerList("replace_fluids", RenderConfig.Filters.REPLACE_FLUIDS, build_Replace_Validator(RenderCommand::normalize_Fluid));
        registerList("replace_block_entities", RenderConfig.Filters.REPLACE_BLOCK_ENTITIES, build_Replace_Validator(RenderCommand::normalize_Block_Entity));
        registerList("replace_falling_blocks", RenderConfig.Filters.REPLACE_FALLING_BLOCKS, build_Replace_Validator(RenderCommand::normalize_Falling_Block));
        registerList("replace_item_entities", RenderConfig.Filters.REPLACE_ITEM_ENTITIES, build_Replace_Validator(RenderCommand::normalize_Item));
        registerList("replace_held_items", RenderConfig.Filters.REPLACE_HELD_ITEMS, build_Replace_Validator(RenderCommand::normalize_Item));
        registerList("replace_hud_elements", RenderConfig.Filters.REPLACE_HUD_ELEMENTS, replace_Text_Pair_Validator());

        // Condition system entries: a semicolon separated key=value list per
        // line ("region=x1,y1,z1~x2,y2,z2;dist=32;count=5;ids=a,b;acts=hide").
        registerList("conditions", RenderConfig.Conditions.CONDITION_ENTRIES, RenderCommand::normalize_Condition);

        // Keybinds: the hotkeyed option toggles plus the 5 dedicated hotkeys.
        // "replace" has no hotkey and "coords" already has the dedicated
        // "toggle_coord_filter" id, so both stay out of the keybind list.
        for (Map.Entry<String, ConfigBoolean> entry : OPTIONS.entrySet()) {
            if (entry.getValue() instanceof ConfigBooleanHotkeyed hotkeyed && !entry.getKey().equals("coords")) {
                KEYS.put(entry.getKey(), hotkeyed);
            }
        }

        registerKey("open_config_gui", RenderConfig.Hotkeys.OPEN_CONFIG_GUI);
        registerKey("reveal_hotkey", RenderConfig.Hotkeys.REVEAL_HOTKEY);
        registerKey("pick_entity", RenderConfig.Hotkeys.PICK_ENTITY_HOTKEY);
        registerKey("toggle_coord_filter", RenderConfig.Hotkeys.TOGGLE_COORD_FILTER);
        registerKey("pick_coord", RenderConfig.Hotkeys.PICK_COORD_HOTKEY);

        registerMode("entities", RenderConfig.Filters.ENTITY_MODE);
        registerMode("blocks", RenderConfig.Filters.BLOCK_MODE);
        registerMode("fluids", RenderConfig.Filters.FLUID_MODE);
        registerMode("blockEntities", RenderConfig.Filters.BLOCK_ENTITY_MODE);
        registerMode("particles", RenderConfig.Filters.PARTICLE_MODE);
        registerMode("armor", RenderConfig.Filters.ARMOR_MODE);
        registerMode("fog", RenderConfig.Filters.FOG_MODE);
        registerMode("text_name", RenderConfig.Filters.NAME_TAG_MODE);
        registerMode("other_players", RenderConfig.Filters.PLAYER_MODE);

        registerOther("keep_sign_text", RenderConfig.General.KEEP_SIGN_TEXT, Other_Kind.BOOL);

        registerOther("replace_enabled", RenderConfig.General.REPLACE_ENABLED, Other_Kind.BOOL);
        registerOther("reveal_hotkey_mode", RenderConfig.General.REVEAL_HOTKEY_MODE, Other_Kind.OPTION);
        registerOther("filter_input_mode", RenderConfig.General.FILTER_INPUT_MODE, Other_Kind.OPTION);
        registerOther("coord_pick_mode", RenderConfig.General.COORD_PICK_MODE, Other_Kind.OPTION);
        registerOther("reveal_affected_types", RenderConfig.General.REVEAL_AFFECTED_TYPES, Other_Kind.TYPES);
    }

    private static void registerOpt(String id, ConfigBoolean cfg) {
        OPTIONS.put(id, cfg);
    }

    private static void registerList(String id, ConfigStringList list, Function<String, String> norm) {
        LISTS.put(id, new List_Category(id, list, null, null, norm));
    }

    private static void registerNames(String id, ConfigString names) {
        LISTS.put(id, new List_Category(id, null, names, null, null));
    }

    private static void registerFixed(String id, ConfigStringList list, Set<String> fixed) {
        LISTS.put(id, new List_Category(id, list, null, fixed, null));
    }

    private static void registerKey(String id, IHotkey hotkey) {
        KEYS.put(id, hotkey);
    }

    private static void registerMode(String id, ConfigOptionValues<BaseOptionListConfigValue> cfg) {
        MODES.put(id, cfg);
    }

    private static void registerOther(String id, Object cfg, Other_Kind kind) {
        OTHERS.put(id, new Other(cfg, kind));
    }

    // ============================ suggestions ============================

    private static final SuggestionProvider<FabricClientCommandSource> OPTION_SUG =
            (ctx, builder) -> suggest(OPTIONS.keySet(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> LIST_SUGGEST =
            (ctx, builder) -> suggest(LISTS.keySet(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> KEYS_SUGGEST =
            (ctx, builder) -> suggest(KEYS.keySet(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> MODE_LIST_SUG =
            (ctx, builder) -> suggest(MODES.keySet(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> OTHERS_SUGGEST =
            (ctx, builder) -> suggest(OTHERS.keySet(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> BOOL_SUGGEST =
            (ctx, builder) -> {
                builder.suggest("true");
                builder.suggest("false");
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> MODE_VAL_SUGGEST =
            (ctx, builder) -> {
                builder.suggest("none");
                builder.suggest("white");
                builder.suggest("black");
                return builder.buildFuture();
            };

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            Set<String> keys, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (String id : keys) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }

    // ============================ registration ============================

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(RenderCommand::on_Register);
    }

    private static void on_Register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                              CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("render")
                .then(ClientCommandManager.literal("option")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .suggests(OPTION_SUG)
                                .executes(RenderCommand::option_Get)
                                .then(ClientCommandManager.argument("value", StringArgumentType.word())
                                        .suggests(BOOL_SUGGEST)
                                        .executes(RenderCommand::option_Set))))
                .then(ClientCommandManager.literal("list")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .suggests(LIST_SUGGEST)
                                .executes(RenderCommand::list_Show)
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(RenderCommand::list_Add)))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(RenderCommand::list_Remove)))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(RenderCommand::list_Set)))))
                .then(ClientCommandManager.literal("keybind")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                .suggests(KEYS_SUGGEST)
                                .executes(RenderCommand::keybind_Get)
                                .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                                        .executes(RenderCommand::keybind_Set))))
                .then(ClientCommandManager.literal("mode")
                        .then(ClientCommandManager.literal("list")
                                .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                        .suggests(MODE_LIST_SUG)
                                        .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                                .suggests(MODE_VAL_SUGGEST)
                                                .executes(RenderCommand::mode_SetList))))
                        .then(ClientCommandManager.literal("others")
                                .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                        .suggests(OTHERS_SUGGEST)
                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(RenderCommand::mode_Other)))))
                .executes(RenderCommand::help));
    }

    // ============================ /render option ============================

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        source.sendFeedback(Text.translatable("reignrender.command.help.title"));
        source.sendFeedback(Text.translatable("reignrender.command.help.option"));
        source.sendFeedback(Text.translatable("reignrender.command.help.list"));
        source.sendFeedback(Text.translatable("reignrender.command.help.keybind"));
        source.sendFeedback(Text.translatable("reignrender.command.help.mode"));
        return 1;
    }

    private static int option_Get(CommandContext<FabricClientCommandSource> ctx) {
        ConfigBoolean option = OPTIONS.get(StringArgumentType.getString(ctx, "id"));

        if (option == null) {
            send_Error(ctx, "reignrender.command.invalidOption", StringArgumentType.getString(ctx, "id"));
            return 0;
        }

        ctx.getSource().sendFeedback(Text.translatable(
                "reignrender.command.optionValue", StringArgumentType.getString(ctx, "id"), option.getBooleanValue()));
        return 1;
    }

    private static int option_Set(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ConfigBoolean option = OPTIONS.get(id);

        if (option == null) {
            send_Error(ctx, "reignrender.command.invalidOption", id);
            return 0;
        }

        Boolean value = parse_Bool(StringArgumentType.getString(ctx, "value"));

        if (value == null) {
            send_Error(ctx, "reignrender.command.invalidBoolean", StringArgumentType.getString(ctx, "value"));
            return 0;
        }

        option.setBooleanValue(value);
        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.optionSet", id, value));
        return 1;
    }

    // ============================ /render list ============================

    private static int list_Show(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        List_Category list = LISTS.get(id);

        if (list == null) {
            send_Error(ctx, "reignrender.command.noList", id);
            return 0;
        }

        ConfigOptionValues<BaseOptionListConfigValue> mode = MODES.get(id);

        if (mode != null) {
            ctx.getSource().sendFeedback(Text.translatable(
                    "reignrender.command.modeIs", id, mode.getOptionValue().getName()));
        }

        String content = list.content();

        if (content.isEmpty()) {
            ctx.getSource().sendFeedback(Text.translatable("reignrender.command.listEmpty", id));
        } else {
            ctx.getSource().sendFeedback(Text.translatable("reignrender.command.listContent", id, content));
        }

        return 1;
    }

    private static int list_Add(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        List_Category list = LISTS.get(id);

        if (list == null) {
            send_Error(ctx, "reignrender.command.noList", id);
            return 0;
        }

        String raw = StringArgumentType.getString(ctx, "value");
        String stored = list.stored(raw);

        if (stored == null) {
            send_Error(ctx, "reignrender.command.invalidId", raw, id);
            return 0;
        }

        if (list.hasNames()) {
            List<String> names = split_Names(list.names.getStringValue());

            if (names.contains(stored)) {
                send_Error(ctx, "reignrender.command.alreadyPresent", stored, id);
                return 0;
            }

            names.add(stored);
            list.names.setStringValue(String.join(";", names));
        } else {
            List<String> entries = new ArrayList<>(list.list.getStrings());

            if (entries.contains(stored)) {
                send_Error(ctx, "reignrender.command.alreadyPresent", stored, id);
                return 0;
            }

            entries.add(stored);
            list.list.setStrings(entries);
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.added", IdLocalizer.name(stored), id));
        return 1;
    }

    private static int list_Remove(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        List_Category list = LISTS.get(id);

        if (list == null) {
            send_Error(ctx, "reignrender.command.noList", id);
            return 0;
        }

        String raw = StringArgumentType.getString(ctx, "value");
        String stored = list.stored(raw);

        if (stored == null) {
            send_Error(ctx, "reignrender.command.notPresent", raw, id);
            return 0;
        }

        if (list.hasNames()) {
            List<String> names = split_Names(list.names.getStringValue());

            if (!names.remove(stored)) {
                send_Error(ctx, "reignrender.command.notPresent", stored, id);
                return 0;
            }

            list.names.setStringValue(String.join(";", names));
        } else {
            List<String> entries = new ArrayList<>(list.list.getStrings());

            if (!entries.remove(stored)) {
                send_Error(ctx, "reignrender.command.notPresent", stored, id);
                return 0;
            }

            list.list.setStrings(entries);
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.removed", IdLocalizer.name(stored), id));
        return 1;
    }

    private static int list_Set(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        List_Category list = LISTS.get(id);

        if (list == null) {
            send_Error(ctx, "reignrender.command.noList", id);
            return 0;
        }

        String raw = StringArgumentType.getString(ctx, "value").trim();

        if (raw.equalsIgnoreCase("empty")) {
            if (list.hasNames()) {
                list.names.setStringValue("");
            } else {
                list.list.setStrings(List.of());
            }

            flush();
            ctx.getSource().sendFeedback(Text.translatable("reignrender.command.listCleared", id));
            return 1;
        }

        String[] parts = raw.split("\\s+");
        List<String> stored = new ArrayList<>();

        for (String part : parts) {
            String value = list.stored(part);
            if (value == null) {
                send_Error(ctx, "reignrender.command.invalidId", part, id);
                return 0;
            }
            stored.add(value);
        }

        if (list.hasNames()) {
            list.names.setStringValue(String.join(";", stored));
        } else {
            list.list.setStrings(stored);
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable(
                "reignrender.command.listSet", id, String.join(", ", stored)));
        return 1;
    }

    // ============================ /render keybind ============================

    private static int keybind_Get(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        IHotkey hotkey = KEYS.get(id);

        if (hotkey == null) {
            send_Error(ctx, "reignrender.command.unknownId", id);
            return 0;
        }

        String displayString = hotkey.getKeybind().getKeysDisplayString();
        ctx.getSource().sendFeedback(Text.translatable(
                "reignrender.command.keybindValue", id,
                displayString.isEmpty() ? Text.translatable("reignrender.command.keyNone") : displayString));
        return 1;
    }

    private static int keybind_Set(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        IHotkey hotkey = KEYS.get(id);
        String key = StringArgumentType.getString(ctx, "key").trim();

        if (hotkey == null) {
            send_Error(ctx, "reignrender.command.unknownId", id);
            return 0;
        }

        if (key.equalsIgnoreCase("none")) {
            hotkey.getKeybind().clearKeys();
        } else {
            int code = KeyCodes.getKeyCodeFromName(key);

            if (code == KeyCodes.KEY_NONE) {
                send_Error(ctx, "reignrender.command.invalidKey", key);
                return 0;
            }

            hotkey.getKeybind().clearKeys();
            hotkey.getKeybind().addKey(code);
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable(
                "reignrender.command.keybindSet", id,
                key.equalsIgnoreCase("none") ? Text.translatable("reignrender.command.keyNone") : key));
        return 1;
    }

    // ============================ /render mode ============================

    private static int mode_SetList(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ConfigOptionValues<BaseOptionListConfigValue> mode = MODES.get(id);

        if (mode == null) {
            send_Error(ctx, "reignrender.command.noMode", id);
            return 0;
        }

        BaseOptionListConfigValue value = parse_Filter_Mode(StringArgumentType.getString(ctx, "mode"));

        if (value == null) {
            send_Error(ctx, "reignrender.command.invalidMode", StringArgumentType.getString(ctx, "mode"));
            return 0;
        }

        mode.setOptionValue(value);
        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.modeSet", id, value.getName()));
        return 1;
    }

    private static int mode_Other(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        Other other = OTHERS.get(id);

        if (other == null) {
            send_Error(ctx, "reignrender.command.unknownId", id);
            return 0;
        }

        String value = StringArgumentType.getString(ctx, "value").trim();

        switch (other.kind) {
            case BOOL -> {
                ConfigBoolean bool = (ConfigBoolean) other.cfg;
                Boolean parsed = parse_Bool(value);

                if (parsed == null) {
                    send_Error(ctx, "reignrender.command.invalidBoolean", value);
                    return 0;
                }

                bool.setBooleanValue(parsed);
            }
            case OPTION -> {
                @SuppressWarnings("unchecked")
                ConfigOptionValues<BaseOptionListConfigValue> optionValues = (ConfigOptionValues<BaseOptionListConfigValue>) other.cfg;
                BaseOptionListConfigValue selected = null;

                for (BaseOptionListConfigValue candidate : optionValues.getAllValues()) {
                    if (candidate.getName().equalsIgnoreCase(value)) {
                        selected = candidate;
                        break;
                    }
                }

                if (selected == null) {
                    send_Error(ctx, "reignrender.command.invalidMode", value);
                    return 0;
                }

                optionValues.setOptionValue(selected);
            }
            case OPTION_ALIAS -> {
                BaseOptionListConfigValue selected = parse_Filter_Mode(value);

                if (selected == null) {
                    send_Error(ctx, "reignrender.command.invalidMode", value);
                    return 0;
                }

                ((ConfigOptionValues<BaseOptionListConfigValue>) other.cfg).setOptionValue(selected);
            }
            case TYPES -> {
                ConfigStringList stringList = (ConfigStringList) other.cfg;

                if (value.equalsIgnoreCase("all")) {
                    stringList.setStrings(new ArrayList<>(FilterEngine.ALL_TYPES));
                } else if (value.equalsIgnoreCase("none")) {
                    stringList.setStrings(List.of());
                } else {
                    List<String> result = new ArrayList<>();

                    for (String part : value.split("\\s+")) {
                        if (!FilterEngine.ALL_TYPES.contains(part)) {
                            send_Error(ctx, "reignrender.command.invalidType", part);
                            return 0;
                        }
                        result.add(part);
                    }

                    stringList.setStrings(result);
                }
            }
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.modeSet", id, value));
        return 1;
    }

    // ============================ helpers ============================

    private enum Other_Kind { BOOL, OPTION, OPTION_ALIAS, TYPES }

    private record Other(Object cfg, Other_Kind kind) { }

    /**
     * A filter list category. Either a registry/fixed-id {@link ConfigStringList}
     * or a free-text {@link ConfigString} (name lists). {@code norm} turns a raw
     * command value into the form stored in the config (defaulting missing
     * namespaces to "minecraft:"), or returns null when the id is invalid.
     */
    private static final class List_Category {
        final String id;
        final ConfigStringList list;
        final ConfigString names;
        final Set<String> fixed;
        final Function<String, String> norm;

        List_Category(String id, ConfigStringList list, ConfigString names, Set<String> fixed,
             Function<String, String> norm) {
            this.id = id;
            this.list = list;
            this.names = names;
            this.fixed = fixed;
            this.norm = norm;
        }

        boolean hasNames() {
            return names != null;
        }

        String stored(String raw) {
            if (hasNames()) {
                return raw.toLowerCase(Locale.ROOT);
            }

            if (fixed != null) {
                return fixed.contains(raw) ? raw : null;
            }

            return norm != null ? norm.apply(raw) : null;
        }

        /** The list content for the display command. */
        String content() {
            if (hasNames()) {
                String value = names.getStringValue();
                return value == null ? "" : value;
            }

            List<String> entries = list.getStrings();

            if (fixed != null) {
                return String.join(", ", entries);
            }

            return entries.stream().map(IdLocalizer::name)
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }

    // -------------------- id normalization / validation --------------------

    private static String normalize_Entity(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.ENTITIES, norm) ? norm : null;
    }

    private static String normalize_Block(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.BLOCKS, norm) ? norm : null;
    }

    private static String normalize_Fluid(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.FLUIDS, norm) ? norm : null;
    }

    private static String normalize_Block_Entity(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.BLOCK_ENTITIES, norm) ? norm : null;
    }

    private static String normalize_Particle(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.PARTICLES, norm) ? norm : null;
    }

    private static String normalize_Armor(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.ARMOR, norm) ? norm : null;
    }

    private static String normalize_Fog(String raw) {
        return IconGridPicker.matches(IconGridPicker.FKind.FOGS, raw) ? raw : null;
    }

    /** Validator for the replace falling block lists: only falling blocks. */
    private static String normalize_Falling_Block(String raw) {
        String norm = default_Namespace(raw);
        return IconGridPicker.matches(IconGridPicker.FKind.FALLING_BLOCKS, norm) ? norm : null;
    }

    /**
     * Builds the validator for a "source=target" replacement rule: both sides
     * must pass the category's id validator, and the stored entry is the
     * normalized "source=target".
     */
    private static Function<String, String> build_Replace_Validator(Function<String, String> norm) {
        return raw -> {
            int eq = raw.indexOf('=');

            if (eq <= 0 || eq >= raw.length() - 1) {
                return null;
            }

            String source = norm.apply(raw.substring(0, eq).trim());
            String target = norm.apply(raw.substring(eq + 1).trim());

            return source != null && target != null ? source + "=" + target : null;
        };
    }

    /**
     * Validator for the free-text replacement pairs (name tags, player
     * names): any non-empty "source=target", with the source lowercased like
     * the name list matching in {@link ReplacementEngine} (the target keeps its case).
     */
    private static Function<String, String> replace_Text_Pair_Validator() {
        return raw -> {
            int eq = raw.indexOf('=');

            if (eq <= 0 || eq >= raw.length() - 1) {
                return null;
            }

            String source = raw.substring(0, eq).trim();
            String target = raw.substring(eq + 1).trim();

            if (source.isEmpty() || target.isEmpty()) {
                return null;
            }

            return source.toLowerCase(Locale.ROOT) + "=" + target;
        };
    }

    /**
     * Validator for the item replacement lists (dropped items): a registry
     * item id that is not air.
     */
    private static String normalize_Item(String raw) {
        Identifier id = Identifier.tryParse(default_Namespace(raw));

        if (id == null || !Registries.ITEM.containsId(id)) {
            return null;
        }

        Item item = Registries.ITEM.get(id);
        return item != Items.AIR ? id.toString() : null;
    }

    /**
     * Validator for a condition system entry: one line of semicolon separated
     * key=value fields (region/box, dist, count, ids, acts). The full
     * validation (region required for region actions, ids required for
     * dist/count, id normalization) happens in
     * {@link com.wdylyh.config.ConditionEngine#parse},
     * which drops invalid lines with a log message, so the command only
     * rejects empty text. The stored entry keeps the given text.
     */
    private static String normalize_Condition(String raw) {
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }

    /** Defaults a missing namespace to "minecraft:", like the config GUI stores ids. */
    private static String default_Namespace(String raw) {
        return raw.indexOf(':') >= 0 ? raw : "minecraft:" + raw;
    }

    // -------------------- misc --------------------

    private static Boolean parse_Bool(String text) {
        if (text.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }

        if (text.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }

        return null;
    }

    /**
     * Maps "none"/"off", "white"/"whitelist" and "black"/"blacklist" onto the
     * filter mode values, so both the spec words and the raw config names work.
     */
    private static BaseOptionListConfigValue parse_Filter_Mode(String text) {
        if (text.equalsIgnoreCase("none") || text.equalsIgnoreCase("off")) {
            return RenderConfig.Filters.MODE_OFF;
        }

        if (text.equalsIgnoreCase("white") || text.equalsIgnoreCase("whitelist")) {
            return RenderConfig.Filters.MODE_WHITELIST;
        }

        if (text.equalsIgnoreCase("black") || text.equalsIgnoreCase("blacklist")) {
            return RenderConfig.Filters.MODE_BLACKLIST;
        }

        return null;
    }

    /**
     * Splits a semicolon separated name list, trimmed and lowercased, so add,
     * remove and set behave like the config GUI (case-insensitive matching).
     */
    private static List<String> split_Names(String raw) {
        List<String> names = new ArrayList<>();

        if (raw == null) {
            return names;
        }

        for (String part : raw.split(";")) {
            String value = part.trim().toLowerCase(Locale.ROOT);

            if (!value.isEmpty()) {
                names.add(value);
            }
        }

        return names;
    }

    /**
     * Invalidates the filter caches and persists the config file. The value
     * change callbacks registered in {@link com.wdylyh.config.ConfigCallbacks} fire on the
     * set calls above and handle the mesh rebuilds for the block/fluid
     * categories (and the replace list caches).
     */
    private static void flush() {
        FilterEngine.invalidateCaches();
        ((ConfigManager) ConfigManager.getInstance()).saveAllConfigs();
    }

    private static void send_Error(CommandContext<FabricClientCommandSource> ctx, String key, Object... args) {
        ctx.getSource().sendError(Text.translatable(key, args));
    }
}