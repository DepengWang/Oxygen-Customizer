package it.dhd.oxygencustomizer.xposed.hooks.systemui.lockscreen;

import static it.dhd.oxygencustomizer.utils.Constants.Preferences.General.GLOBAL_FONT_STYLE;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.ControllersProvider;
import it.dhd.oxygencustomizer.xposed.utils.GlobalFontHelper;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;

/**
 * Applies the global font to notification rows while the lockscreen
 * (StatusBarState.KEYGUARD) is showing, and restores the original typefaces
 * when leaving it, so the pulled-down shade keeps the system font.
 * CJK glyphs that the bundled fonts lack fall back to the system font.
 */
public class LockscreenNotificationFont extends XposedMods {

    private static final String listenPackage = Constants.Packages.SYSTEM_UI;
    private static final String FONT_STYLE_SYSTEM = "system";
    // com.android.systemui.statusbar.StatusBarState.KEYGUARD
    private static final int STATUS_BAR_STATE_KEYGUARD = 1;

    private Context appContext;
    private String mFontStyle = FONT_STYLE_SYSTEM;
    private boolean mOnKeyguard = false;
    private boolean mAppliedLogged = false;
    // Original typeface of every TextView we changed, for restoring.
    private final Map<TextView, Typeface> mOriginalTypefaces = new WeakHashMap<>();
    private final Map<View, Boolean> mRows = new WeakHashMap<>();

    public LockscreenNotificationFont(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mFontStyle = Xprefs.getString(GLOBAL_FONT_STYLE, FONT_STYLE_SYSTEM);

        if (Key.length > 0 && Key[0].equals(GLOBAL_FONT_STYLE)) {
            new Handler(Looper.getMainLooper()).post(this::refreshAllRows);
        }
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

        ControllersProvider.registerStatusBarStateChangedCallback(state -> {
            boolean onKeyguard = state == STATUS_BAR_STATE_KEYGUARD;
            if (onKeyguard == mOnKeyguard) return;
            mOnKeyguard = onKeyguard;
            refreshAllRows();
        });

        ReflectedClass ExpandableNotificationRow = ReflectedClass.ofIfPossible(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
        // Rows inflate and swap content views (contracted/expanded) after
        // attach, so re-check on layout; unchanged views are skipped cheaply.
        ExpandableNotificationRow
                .after("onLayout")
                .run(param -> {
                    View row = (View) param.thisObject;
                    mRows.put(row, Boolean.TRUE);
                    if (mOnKeyguard) applyFontToRow(row);
                });
        ExpandableNotificationRow
                .after("onAttachedToWindow")
                .run(param -> mRows.put((View) param.thisObject, Boolean.TRUE));
    }

    private void refreshAllRows() {
        Typeface typeface = mOnKeyguard ? loadTypeface() : null;
        if (typeface == null) {
            restoreAll();
            return;
        }
        for (View row : new ArrayList<>(mRows.keySet())) {
            if (row != null) applyFontToRow(row);
        }
    }

    private void applyFontToRow(View row) {
        Typeface typeface = loadTypeface();
        if (typeface == null) return;
        applyFontRecursively(row, typeface);
        if (!mAppliedLogged) {
            mAppliedLogged = true;
            XposedBridge.log("[OxygenCustomizer-LockNotif] lockscreen notification font applied, font="
                    + mFontStyle);
        }
    }

    private void applyFontRecursively(View view, Typeface typeface) {
        if (view instanceof TextView textView) {
            Typeface current = textView.getTypeface();
            if (current != typeface) {
                if (!mOriginalTypefaces.containsKey(textView)) {
                    mOriginalTypefaces.put(textView, current);
                }
                textView.setTypeface(typeface);
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                applyFontRecursively(group.getChildAt(i), typeface);
            }
        }
    }

    private void restoreAll() {
        for (Map.Entry<TextView, Typeface> entry : new ArrayList<>(mOriginalTypefaces.entrySet())) {
            TextView textView = entry.getKey();
            if (textView != null) textView.setTypeface(entry.getValue());
        }
        mOriginalTypefaces.clear();
    }

    private Typeface loadTypeface() {
        return GlobalFontHelper.get(appContext, mFontStyle);
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
