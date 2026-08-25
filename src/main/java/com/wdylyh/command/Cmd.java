package com.wdylyh.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import com.wdylyh.util.Names;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The /reignrender client command: add/remove entries in the per-type filter
 * lists and switch the filter modes from the chat, without opening the config
 * GUI. Categories mirror the Filter tab: entity, block, fluid, blockEntity,
 * particle, armor, fog (registry id lists) and nameTag, player (name lists).
 */
public final class Cmd {

    private static final Map<String, Cat> CATS = new LinkedHashMap<>();
    private static final Map<String, ConfigStringList> RCATS = new LinkedHashMap<>();

    static {
        register("entity", Cfg.F.ENTITY_MODE, Cfg.F.FILTERED_ENTITIES, null);
        register("block", Cfg.F.BLOCK_MODE, Cfg.F.FILTERED_BLOCKS, null);
        register("fluid", Cfg.F.FLUID_MODE, Cfg.F.FILTERED_FLUIDS, null);
        register("blockEntity", Cfg.F.BLOCK_ENTITY_MODE, Cfg.F.FILTERED_BLOCK_ENTITIES, null);
        register("particle", Cfg.F.PARTICLE_MODE, Cfg.F.FILTERED_PARTICLES, null);
        register("armor", Cfg.F.ARMOR_MODE, Cfg.F.FILTERED_ARMOR, null);
        register("fog", Cfg.F.FOG_MODE, Cfg.F.FILTERED_FOGS, null);
        register("nameTag", Cfg.F.NAME_TAG_MODE, null, Cfg.F.FILTERED_NAME_TAGS);
        register("player", Cfg.F.PLAYER_MODE, null, Cfg.F.FILTERED_PLAYERS);

        registerReplace("particle", Cfg.F.REPLACE_PARTICLES);
        registerReplace("block", Cfg.F.REPLACE_BLOCKS);
        registerReplace("entity", Cfg.F.REPLACE_ENTITIES);
        registerReplace("fog", Cfg.F.REPLACE_FOGS);
        registerReplace("armor", Cfg.F.REPLACE_ARMOR);
        registerReplace("nameTag", Cfg.F.REPLACE_NAME_TAGS);
        registerReplace("player", Cfg.F.REPLACE_PLAYER_NAMES);
        registerReplace("fluid", Cfg.F.REPLACE_FLUIDS);
        registerReplace("blockEntity", Cfg.F.REPLACE_BLOCK_ENTITIES);
        registerReplace("fallingBlock", Cfg.F.REPLACE_FALLING_BLOCKS);
    }

    private static void register(String id, ConfigOptionValues<BaseOptionListConfigValue> mode,
                                 ConfigStringList list, ConfigString names) {
        CATS.put(id, new Cat(id, mode, list, names));
    }

    private static void registerReplace(String id, ConfigStringList list) {
        RCATS.put(id, list);
    }

    private static final SuggestionProvider<FabricClientCommandSource> CAT_SUG =
            (ctx, builder) -> {
                for (String id : CATS.keySet()) {
                    builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> RCAT_SUG =
            (ctx, builder) -> {
                for (String id : RCATS.keySet()) {
                    builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> MODE_SUG =
            (ctx, builder) -> {
                for (BaseOptionListConfigValue mode : List.of(
                        Cfg.F.MODE_OFF, Cfg.F.MODE_BLACKLIST, Cfg.F.MODE_WHITELIST)) {
                    builder.suggest(mode.getName());
                }
                return builder.buildFuture();
            };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(Cmd::onReg);
    }

    private static void onReg(CommandDispatcher<FabricClientCommandSource> dispatcher,
                              CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("reignrender")
                .then(ClientCommandManager.literal("help").executes(Cmd::help))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                .suggests(CAT_SUG)
                                .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                        .executes(Cmd::add))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                .suggests(CAT_SUG)
                                .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                        .executes(Cmd::remove))))
                .then(ClientCommandManager.literal("mode")
                        .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                .suggests(CAT_SUG)
                                .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                        .suggests(MODE_SUG)
                                        .executes(Cmd::mode))))
                .then(ClientCommandManager.literal("list")
                        .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                .suggests(CAT_SUG)
                                .executes(Cmd::list)))
                .then(ClientCommandManager.literal("replace")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                        .suggests(RCAT_SUG)
                                        .then(ClientCommandManager.argument("rule", StringArgumentType.greedyString())
                                                .executes(Cmd::rAdd))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("category", StringArgumentType.word())
                                        .suggests(RCAT_SUG)
                                        .then(ClientCommandManager.argument("rule", StringArgumentType.greedyString())
                                                .executes(Cmd::rRemove)))))
                .executes(Cmd::help));
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource s = ctx.getSource();
        s.sendFeedback(Text.translatable("reignrender.command.help.title"));
        s.sendFeedback(Text.translatable("reignrender.command.help.add"));
        s.sendFeedback(Text.translatable("reignrender.command.help.remove"));
        s.sendFeedback(Text.translatable("reignrender.command.help.mode"));
        s.sendFeedback(Text.translatable("reignrender.command.help.list"));
        s.sendFeedback(Text.translatable("reignrender.command.help.replaceAdd"));
        s.sendFeedback(Text.translatable("reignrender.command.help.replaceRemove"));
        s.sendFeedback(Text.translatable("reignrender.command.help.categories", String.join(", ", CATS.keySet())));
        s.sendFeedback(Text.translatable("reignrender.command.help.replaceCategories", String.join(", ", RCATS.keySet())));
        return 1;
    }

    private static int add(CommandContext<FabricClientCommandSource> ctx) {
        Cat c = CATS.get(StringArgumentType.getString(ctx, "category"));

        if (c == null) {
            badCat(ctx);
            return 0;
        }

        String id = StringArgumentType.getString(ctx, "id");

        if (c.hasNames()) {
            List<String> ns = split(c.names.getStringValue());

            if (ns.contains(id)) {
                dup(ctx, c, id);
                return 0;
            }

            ns.add(id);
            c.names.setStringValue(String.join(";", ns));
        } else {
            List<String> l = new ArrayList<>(c.list.getStrings());

            if (l.contains(id)) {
                dup(ctx, c, id);
                return 0;
            }

            l.add(id);
            c.list.setStrings(l);
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.added", Names.name(id), c.id));
        return 1;
    }

    private static int remove(CommandContext<FabricClientCommandSource> ctx) {
        Cat c = CATS.get(StringArgumentType.getString(ctx, "category"));

        if (c == null) {
            badCat(ctx);
            return 0;
        }

        String id = StringArgumentType.getString(ctx, "id");

        if (c.hasNames()) {
            List<String> ns = split(c.names.getStringValue());

            if (!ns.remove(id)) {
                missing(ctx, c, id);
                return 0;
            }

            c.names.setStringValue(String.join(";", ns));
        } else {
            List<String> l = new ArrayList<>(c.list.getStrings());

            if (!l.remove(id)) {
                missing(ctx, c, id);
                return 0;
            }

            c.list.setStrings(l);
        }

        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.removed", Names.name(id), c.id));
        return 1;
    }

    private static int mode(CommandContext<FabricClientCommandSource> ctx) {
        Cat c = CATS.get(StringArgumentType.getString(ctx, "category"));

        if (c == null) {
            badCat(ctx);
            return 0;
        }

        String mn = StringArgumentType.getString(ctx, "mode");
        BaseOptionListConfigValue m = null;

        for (BaseOptionListConfigValue v : c.mode.getAllValues()) {
            if (v.getName().equalsIgnoreCase(mn)) {
                m = v;
                break;
            }
        }

        if (m == null) {
            ctx.getSource().sendError(Text.translatable("reignrender.command.invalidMode", mn));
            return 0;
        }

        c.mode.setOptionValue(m);
        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.modeSet", c.id, m.getName()));
        return 1;
    }

    private static int rAdd(CommandContext<FabricClientCommandSource> ctx) {
        ConfigStringList l = RCATS.get(StringArgumentType.getString(ctx, "category"));

        if (l == null) {
            badRCat(ctx);
            return 0;
        }

        String rule = StringArgumentType.getString(ctx, "rule");

        if (!validRule(rule)) {
            ctx.getSource().sendError(Text.translatable("reignrender.command.invalidReplaceRule", rule));
            return 0;
        }

        List<String> es = new ArrayList<>(l.getStrings());

        if (es.contains(rule)) {
            ctx.getSource().sendError(Text.translatable("reignrender.command.alreadyPresent", rule, StringArgumentType.getString(ctx, "category")));
            return 0;
        }

        es.add(rule);
        l.setStrings(es);
        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.replaceAdded", rule, StringArgumentType.getString(ctx, "category")));
        return 1;
    }

    private static int rRemove(CommandContext<FabricClientCommandSource> ctx) {
        ConfigStringList l = RCATS.get(StringArgumentType.getString(ctx, "category"));

        if (l == null) {
            badRCat(ctx);
            return 0;
        }

        String rule = StringArgumentType.getString(ctx, "rule");
        List<String> es = new ArrayList<>(l.getStrings());

        if (!es.remove(rule)) {
            ctx.getSource().sendError(Text.translatable("reignrender.command.notPresent", rule, StringArgumentType.getString(ctx, "category")));
            return 0;
        }

        l.setStrings(es);
        flush();
        ctx.getSource().sendFeedback(Text.translatable("reignrender.command.replaceRemoved", rule, StringArgumentType.getString(ctx, "category")));
        return 1;
    }

    /**
     * A replacement rule is "source=target", e.g. "minecraft:zombie=minecraft:skeleton".
     * Both sides must be non-empty and an '=' must separate them, mirroring the
     * parsing in {@link Rpl}.
     */
    private static boolean validRule(String rule) {
        if (rule == null) {
            return false;
        }
        int eq = rule.indexOf('=');
        if (eq <= 0 || eq >= rule.length() - 1) {
            return false;
        }
        // Must survive the trim in Rpl.parse too, otherwise the
        // command reports success but the rule is silently skipped.
        return !rule.substring(0, eq).trim().isEmpty()
                && !rule.substring(eq + 1).trim().isEmpty();
    }

    private static void badRCat(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendError(Text.translatable("reignrender.command.invalidReplaceCategory",
                StringArgumentType.getString(ctx, "category")));
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        Cat c = CATS.get(StringArgumentType.getString(ctx, "category"));

        if (c == null) {
            badCat(ctx);
            return 0;
        }

        String ct = c.hasNames()
                ? c.names.getStringValue()
                : c.list.getStrings().stream()
                        .map(Names::name)
                        .collect(java.util.stream.Collectors.joining(", "));

        if (ct.isEmpty()) {
            ctx.getSource().sendFeedback(Text.translatable("reignrender.command.listEmpty", c.id));
        } else {
            ctx.getSource().sendFeedback(Text.translatable("reignrender.command.listContent", c.id, ct));
        }
        return 1;
    }

    /**
     * Splits a semicolon separated name list, trimmed and lowercased, so add
     * and remove behave like the config GUI (case-insensitive matching).
     */
    private static List<String> split(String raw) {
        List<String> ns = new ArrayList<>();

        if (raw == null) {
            return ns;
        }

        for (String part : raw.split(";")) {
            String n = part.trim().toLowerCase(Locale.ROOT);

            if (!n.isEmpty()) {
                ns.add(n);
            }
        }

        return ns;
    }

    /**
     * Invalidates the filter caches (so the change applies immediately) and
     * persists the config file. The value change callbacks registered in
     * {@link com.wdylyh.config.Cb} fire on the set calls above and
     * handle chunk rebuilds for the block/fluid categories.
     */
    private static void flush() {
        FR.invalidateCaches();
        Rpl.invalidateCaches();
        ((ConfigManager) ConfigManager.getInstance()).saveAllConfigs();
    }

    private static void badCat(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendError(Text.translatable("reignrender.command.invalidCategory",
                StringArgumentType.getString(ctx, "category")));
    }

    private static void dup(CommandContext<FabricClientCommandSource> ctx, Cat c, String id) {
        ctx.getSource().sendError(Text.translatable("reignrender.command.alreadyPresent", Names.name(id), c.id));
    }

    private static void missing(CommandContext<FabricClientCommandSource> ctx, Cat c, String id) {
        ctx.getSource().sendError(Text.translatable("reignrender.command.notPresent", Names.name(id), c.id));
    }

    private record Cat(String id, ConfigOptionValues<BaseOptionListConfigValue> mode,
                       ConfigStringList list, ConfigString names) {
        boolean hasNames() {
            return names != null;
        }
    }
}