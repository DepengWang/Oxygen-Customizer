package it.dhd.oxygencustomizer.xposed.hooks.incallui;

import static it.dhd.oxygencustomizer.utils.Constants.Preferences.General.INCALL_DIALPAD_HEIGHT;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;

/**
 * Shrinks the OPlus in-call DTMF dialpad (InCallUI oplus_dialpad_fragment).
 * Every key row is an item_dialpad_button FrameLayout holding a fixed-size
 * TalkBackConstraintLayout circle; the row height and its vertical margins are
 * scaled, and the circle is scaled from its top edge so it fits the shorter row.
 */
public class InCallDialpad extends XposedMods {

    private static final String listenPackage = Constants.Packages.INCALLUI;

    private float mScale = 1f;
    private WeakReference<ViewGroup> mDialpad = new WeakReference<>(null);
    // Original {height, topMargin, bottomMargin} of each key row.
    private final Map<View, int[]> mOriginalRows = new WeakHashMap<>();

    public InCallDialpad(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        int percent;
        try {
            percent = Integer.parseInt(Xprefs.getString(INCALL_DIALPAD_HEIGHT, "100"));
        } catch (NumberFormatException ignored) {
            percent = 100;
        }
        mScale = Math.max(50, Math.min(100, percent)) / 100f;

        if (Key.length > 0 && Key[0].equals(INCALL_DIALPAD_HEIGHT)) {
            ViewGroup dialpad = mDialpad.get();
            if (dialpad != null) dialpad.post(() -> scaleKeys(dialpad));
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // onLayout is called by the framework, so ART can't inline it away.
        ReflectedClass.ofIfPossible("com.android.incallui.mvvm.widget.DialpadRecyclerView")
                .after("onLayout")
                .run(param -> {
                    ViewGroup dialpad = (ViewGroup) param.thisObject;
                    mDialpad = new WeakReference<>(dialpad);
                    scaleKeys(dialpad);
                });
    }

    private void scaleKeys(ViewGroup dialpad) {
        boolean changed = false;
        for (int i = 0; i < dialpad.getChildCount(); i++) {
            if (!(dialpad.getChildAt(i) instanceof ViewGroup row) || row.getChildCount() == 0) continue;
            if (!(row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams lp)) continue;
            View key = row.getChildAt(0);
            ViewGroup.LayoutParams keyLp = key.getLayoutParams();
            if (keyLp == null || keyLp.height <= 0) continue;

            int[] original = mOriginalRows.get(row);
            if (original == null) {
                original = new int[]{lp.height, lp.topMargin, lp.bottomMargin};
                mOriginalRows.put(row, original);
            }

            boolean stock = mScale >= 1f;
            int height = stock ? original[0] : Math.round(keyLp.height * mScale);
            int top = stock ? original[1] : Math.round(original[1] * mScale);
            int bottom = stock ? original[2] : Math.round(original[2] * mScale);
            if (lp.height == height && lp.topMargin == top && lp.bottomMargin == bottom
                    && key.getScaleY() == mScale) continue;

            lp.height = height;
            lp.topMargin = top;
            lp.bottomMargin = bottom;
            key.setPivotX((keyLp.width > 0 ? keyLp.width : key.getWidth()) / 2f);
            key.setPivotY(0);
            key.setScaleX(mScale);
            key.setScaleY(mScale);
            row.requestLayout();
            changed = true;
        }
        // Called from onLayout: apply the new row sizes on the next pass.
        if (changed) dialpad.post(dialpad::requestLayout);
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
