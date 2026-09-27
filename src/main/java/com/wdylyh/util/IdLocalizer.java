package com.wdylyh.util;

import net.minecraft.text.Text;

/**
 * Turns raw registry ids from the filter lists into the localized display
 * names the player sees in-game. The game's own lang files (e.g. the zh_cn
 * translation) provide the names, so "minecraft:stony_shore" becomes "石岸"
 * on a Chinese client without shipping a translation table of our own.
 * Ids with no translation fall back to the raw id.
 */
public final class IdLocalizer {

    private IdLocalizer() {
    }

    public static String name(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }

        String namespace = "minecraft";
        String path = id;
        int colon = id.indexOf(':');

        if (colon >= 0) {
            namespace = id.substring(0, colon);
            path = id.substring(colon + 1);
        }

        // effect:xxx is our own prefix for the status-effect fog types; the
        // translation key lives in the effect namespace.
        if (namespace.equals("effect")) {
            return resolve("effect.minecraft." + path, id);
        }

        // The raw id does not say which registry it belongs to, so try the
        // common translation key layouts and use the first one that exists.
        String[] candidates = {
                "biome." + namespace + "." + path,
                "entity." + namespace + "." + path,
                "block." + namespace + "." + path,
                "item." + namespace + "." + path,
                "fluid." + namespace + "." + path
        };

        for (String key : candidates) {
            String result = resolve(key, null);
            if (result != null) {
                return result;
            }
        }

        return id;
    }

    /**
     * Returns the translated text for the key, or the fallback when the game
     * has no translation for it (Minecraft returns the key itself then).
     */
    private static String resolve(String key, String fallback) {
        String text = Text.translatable(key).getString();
        return text.equals(key) ? fallback : text;
    }
}