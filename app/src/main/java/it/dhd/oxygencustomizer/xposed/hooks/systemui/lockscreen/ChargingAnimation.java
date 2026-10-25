package it.dhd.oxygencustomizer.xposed.hooks.systemui.lockscreen;

import static it.dhd.oxygencustomizer.utils.Constants.Preferences.General.GLOBAL_FONT_STYLE;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.utils.ViewHelper;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;

/**
 * OOS charging animation tweaks:
 * - applies the global font to the level text ("91 %"). The stock views
 * re-apply their variable OPlus Sans font through font variation settings,
 * so the selected Typeface is put back after each of those calls.
 */
public class ChargingAnimation extends XposedMods {

    private static final String listenPackage = Constants.Packages.SYSTEM_UI;
    private static final String FONT_STYLE_SYSTEM = "system";
    private static final String FONT_STYLE_CLOCKOPIA = "clockopia";
    private static final String FONT_STYLE_ORBITRON = "orbitron";
    private static final String[] LEVEL_VIEW_METHODS = {
            "onFinishInflate",
            "onConfigurationChanged",
            "setLevelVariationSettings",
            "setOplusFontVariationSettings",
            "setTypeFace",
            "updateBatteryLevelView",
            "updateChargeAnim",
            "startLevelAndLogoFadeinAnimation"
    };
    private Context appContext;
    private String mFontStyle = FONT_STYLE_SYSTEM;
    private String mCachedTypefaceStyle;
    private Typeface mCachedTypeface;
    private boolean mFontAppliedLogged = false;

    public ChargingAnimation(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mFontStyle = Xprefs.getString(GLOBAL_FONT_STYLE, FONT_STYLE_SYSTEM);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            appContext = mContext.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY
            );
        } catch (Throwable ignored) {
        }

        hookLevelFont();
    }

    private void hookLevelFont() {
        ReflectedClass ChargeLevelAndLogoView = ReflectedClass.ofIfPossible(
                "com.oplus.charge.view.ChargeLevelAndLogoView");
        ReflectedClass FrameChargeLevelAndLogoView = ReflectedClass.ofIfPossible(
                "com.oplus.charge.view.FrameChargeLevelAndLogoView");
        ReflectedClass GradientColorTextView = ReflectedClass.ofIfPossible(
                "com.oplus.charge.view.GradientColorTextView");

        for (String method : LEVEL_VIEW_METHODS) {
            ChargeLevelAndLogoView
                    .after(method)
                    .run(param -> applyFont((View) param.thisObject));
            FrameChargeLevelAndLogoView
                    .after(method)
                    .run(param -> applyFont((View) param.thisObject));
        }
        // The big level number; only changes the Typeface when it differs.
        GradientColorTextView
                .before("onDraw")
                .run(param -> {
                    Typeface typeface = loadTypeface();
                    TextView textView = (TextView) param.thisObject;
                    if (typeface != null && textView.getTypeface() != typeface) {
                        textView.setTypeface(typeface);
                    }
                });
    }

    private void applyFont(View view) {
        Typeface typeface = loadTypeface();
        if (view == null || typeface == null) return;
        ViewHelper.applyFontToView(view, typeface);
        if (!mFontAppliedLogged) {
            mFontAppliedLogged = true;
            XposedBridge.log("[OxygenCustomizer-Charging] charging level font applied: "
                    + view.getClass().getSimpleName() + ", font=" + mFontStyle);
        }
    }

    private Typeface loadTypeface() {
        String style = mFontStyle;
        if (appContext == null || style == null || FONT_STYLE_SYSTEM.equals(style)) return null;
        if (style.equals(mCachedTypefaceStyle)) return mCachedTypeface;

        String assetName;
        if (FONT_STYLE_CLOCKOPIA.equals(style)) {
            assetName = "Fonts/clockopia.ttf";
        } else if (FONT_STYLE_ORBITRON.equals(style)) {
            assetName = "Fonts/orbitron_regular.ttf";
        } else {
            return null;
        }

        mCachedTypefaceStyle = style;
        try {
            mCachedTypeface = Typeface.createFromAsset(appContext.getAssets(), assetName);
        } catch (Throwable t) {
            log("Unable to load bundled charging font " + assetName + ": " + t.getMessage());
            mCachedTypeface = null;
        }
        return mCachedTypeface;
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
