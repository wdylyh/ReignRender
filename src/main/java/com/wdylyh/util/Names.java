package com.wdylyh.util;

import net.minecraft.text.Text;

/**
 * Turns raw registry ids from the filter lists into the localized display
 * names the player sees in-game. The game's own lang files (e.g. the zh_cn
 * translation) provide the names, so "minecraft:stony_shore" becomes "石岸"
 * on a Chinese client without shipping a translation table of our own.
 * Ids with no translation fall back to the raw id.
 */
public final class Names {

    private Names() {
    }

    public static String name(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }

        String ns = "minecraft";
        String p = id;
        int colon = id.indexOf(':');

        if (colon >= 0) {
            ns = id.substring(0, colon);
            p = id.substring(colon + 1);
        }

        // effect:xxx is our own prefix for the status-effect fog types; the
        // translation key lives in the effect namespace.
        if (ns.equals("effect")) {
            return res("effect.minecraft." + p, id);
        }

        // The raw id does not say which registry it belongs to, so try the
        // common translation key layouts and use the first one that exists.
        String[] cs = {
                "biome." + ns + "." + p,
                "entity." + ns + "." + p,
                "block." + ns + "." + p,
                "item." + ns + "." + p,
                "fluid." + ns + "." + p
        };

        for (String k : cs) {
            String r = res(k, null);
            if (r != null) {
                return r;
            }
        }

        return id;
    }

    /**
     * Returns the translated text for the key, or the fallback when the game
     * has no translation for it (Minecraft returns the key itself then).
     */
    private static String res(String k, String fb) {
        String s = Text.translatable(k).getString();
        return s.equals(k) ? fb : s;
    }
}