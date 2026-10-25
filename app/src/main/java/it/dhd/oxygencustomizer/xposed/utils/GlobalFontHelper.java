package it.dhd.oxygencustomizer.xposed.utils;

import android.content.Context;
import android.graphics.Typeface;

import de.robv.android.xposed.XposedBridge;

/**
 * Loads the global font (Constants.Preferences.General.GLOBAL_FONT_STYLE) from
 * the module assets once per style and shares that single instance with every
 * mod in the process. Callers run from draw/text hooks, so reusing one object
 * also lets setTypeface() see "no change" and skip extra layout passes.
 */
public final class GlobalFontHelper {

    public static final String STYLE_SYSTEM = "system";
    public static final String STYLE_CLOCKOPIA = "clockopia";
    public static final String STYLE_ORBITRON = "orbitron";

    private static String sCachedStyle;
    private static Typeface sCachedTypeface;

    private GlobalFontHelper() {
    }

    /**
     * @param moduleContext a context of the module package (for its assets)
     * @return the Typeface for the style, or null for the system font, an
     * unknown style or when the asset cannot be loaded
     */
    public static synchronized Typeface get(Context moduleContext, String style) {
        if (moduleContext == null || style == null || STYLE_SYSTEM.equals(style)) return null;
        if (style.equals(sCachedStyle)) return sCachedTypeface;

        String assetName;
        if (STYLE_CLOCKOPIA.equals(style)) {
            assetName = "Fonts/clockopia.ttf";
        } else if (STYLE_ORBITRON.equals(style)) {
            assetName = "Fonts/orbitron_regular.ttf";
        } else {
            return null;
        }

        sCachedStyle = style;
        try {
            sCachedTypeface = Typeface.createFromAsset(moduleContext.getAssets(), assetName);
        } catch (Throwable t) {
            XposedBridge.log("[OxygenCustomizer] Unable to load global font " + assetName + ": " + t);
            sCachedTypeface = null;
        }
        return sCachedTypeface;
    }
}
