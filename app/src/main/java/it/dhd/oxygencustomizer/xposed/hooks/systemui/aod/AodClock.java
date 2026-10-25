package it.dhd.oxygencustomizer.xposed.hooks.systemui.aod;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static it.dhd.oxygencustomizer.utils.Constants.AOD_CLOCK_LAYOUT;
import static it.dhd.oxygencustomizer.utils.Constants.LOCKSCREEN_CLOCK_LAYOUT;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_COLOR_CODE_ACCENT1;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_COLOR_CODE_ACCENT2;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_COLOR_CODE_ACCENT3;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_COLOR_CODE_TEXT1;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_COLOR_CODE_TEXT2;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_CUSTOM_COLOR_SWITCH;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_CUSTOM_DEVICE_VALUE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_CUSTOM_IMAGE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_CUSTOM_USER_IMAGE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_CUSTOM_USER_VALUE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_DATE_FORMAT;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_LINE_HEIGHT;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_STYLE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_SWITCH;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.AodClock.AOD_CLOCK_TEXT_SCALING;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;
import static it.dhd.oxygencustomizer.xposed.hooks.systemui.OpUtils.getPrimaryColor;
import static it.dhd.oxygencustomizer.xposed.utils.ViewHelper.findViewWithTag;
import static it.dhd.oxygencustomizer.xposed.utils.ViewHelper.loadLottieAnimationView;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.General.GLOBAL_FONT_STYLE;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.ResourceManager;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.utils.GlobalFontHelper;
import it.dhd.oxygencustomizer.xposed.utils.CircleFramedDrawable;
import it.dhd.oxygencustomizer.xposed.utils.DrawableConverter;
import it.dhd.oxygencustomizer.xposed.utils.SystemUtils;
import it.dhd.oxygencustomizer.xposed.utils.TimeUtils;
import it.dhd.oxygencustomizer.xposed.utils.ViewHelper;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;
import it.dhd.oxygencustomizer.xposed.views.lockscreenwidgets.ProgressImageView;

public class AodClock extends XposedMods {

    public static final String OC_AOD_CLOCK_TAG = "oxygencustomizer_aod_clock";
    private static final String listenPackage = Constants.Packages.SYSTEM_UI;
    private static final String FONT_STYLE_SYSTEM = "system";
    Class<?> LottieAn = null;
    private ViewGroup mRootLayout = null;
    private Context appContext;
    private boolean mAodClockEnabled = false;
    private int accent1, accent2, accent3, text1, text2;
    private boolean mCustomColor, mCustomImage, mCustomUserImage;
    private String mFontStyle = FONT_STYLE_SYSTEM;
    private View mStockAodView;
    private boolean mNativeAodTextHookLogged;
    private boolean mApplyingNativeTypeface;
    private boolean mApplyingAodFont;
    // Hot-path caches for the global draw/text hooks (see applyFontToAodView,
    // isNativeAodClockRoot, getNativeAodClockTextId).
    private static final long FONT_REAPPLY_INTERVAL_MS = 1000;
    private final Map<View, Long> mFontAppliedAt = new WeakHashMap<>();
    private Typeface mThrottleTypeface;
    private int mKeyguardStyleClockId;
    private final Map<android.content.res.Resources, android.util.SparseArray<String>> mClockTextIdNames =
            new WeakHashMap<>();
    private boolean mNativeAodDiagnosticLogged;
    private boolean mNativeThemeHookLogged;
    private static final String NATIVE_CLOCK_TIME_VIEW_CLASS =
            "com.oplus.keyguard.clock.base.ui.view.ClockTimeView";
    private static final String NATIVE_THEME_CLOCK_HELPER_CLASS =
            "com.oplus.keyguard.clock.base.util.ThemeClockHelper";
    private static final String[] NATIVE_CLOCK_TIME_IDS = {
            "clock_time_hour_first_txt",
            "clock_time_hour_second_txt",
            "clock_time_symbol_text",
            "clock_time_minutes_txt"
    };
    // ThemeClockHelper must be hooked in the ClassLoader SystemUI actually
    // uses for the clock plugin, which can differ from createPackageContext().
    private final Set<ClassLoader> mHookedNativeClockLoaders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Class<?>> mHookedThemeClockHelpers = new HashSet<>();
    private final Map<View, String> mNativeClockRenderedStyle = new WeakHashMap<>();
    // The OOS 16 AOD screen (aod_off_layout) is drawn by the VariUIEngine
    // "egview" widgets, which paint text through AttributeTextView.mTextPaint
    // rather than a TextView.
    private static final String AOD_ENGINE_TEXT_VIEW_CLASS = "com.oplus.egview.attribute.AttributeTextView";
    private static final String AOD_ENGINE_AOD_TEXT_VIEW_CLASS = "com.oplus.egview.widget.AodTextView";
    private static final String AOD_ENGINE_TIME_VIEW_CLASS = "com.oplus.egview.widget.TimeView";
    private final Set<ClassLoader> mHookedAodEngineLoaders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final String AOD_ENGINE_PARSONS_CLOCK_CLASS = "com.oplus.egview.widget.insight.OpParsonsClock";
    private boolean mAodEnginePaintLogged;
    private boolean mAodParsonsClockLogged;
    private View mAodApkView;
    private String mCustomUserName;
    private String mCustomDeviceName;
    private float mClockScale;
    private String mCustomDateFormat;
    private int mLineHeight;
    private int mAodClockStyle = 0;
    private int mBatteryStatus = 1;
    private int mBatteryPercentage = 1;
    private UserManager mUserManager;
    private AudioManager mAudioManager;
    private TextView mBatteryStatusView;
    private TextView mBatteryLevelView;
    private TextView mVolumeLevelView;
    private ProgressBar mBatteryProgress;
    private ProgressBar mVolumeProgress;
    private ProgressImageView mVolumeLevelArcProgress;
    private final BroadcastReceiver mVolumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            initSoundManager();
        }
    };
    private ProgressImageView mRamUsageArcProgress;
    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 1);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                mBatteryPercentage = (level * 100) / scale;
                initBatteryStatus();
            }
        }
    };
    public AodClock(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mAodClockEnabled = Xprefs.getBoolean(AOD_CLOCK_SWITCH, false);
        mAodClockStyle = Xprefs.getInt(AOD_CLOCK_STYLE, 0);
        accent1 = Xprefs.getInt(AOD_CLOCK_COLOR_CODE_ACCENT1, getPrimaryColor(mContext));
        accent2 = Xprefs.getInt(AOD_CLOCK_COLOR_CODE_ACCENT2, getPrimaryColor(mContext));
        accent3 = Xprefs.getInt(AOD_CLOCK_COLOR_CODE_ACCENT3, getPrimaryColor(mContext));
        text1 = Xprefs.getInt(AOD_CLOCK_COLOR_CODE_TEXT1, Color.WHITE);
        text2 = Xprefs.getInt(AOD_CLOCK_COLOR_CODE_TEXT2, Color.WHITE);
        mFontStyle = Xprefs.getString(GLOBAL_FONT_STYLE, FONT_STYLE_SYSTEM);
        mCustomColor = Xprefs.getBoolean(AOD_CLOCK_CUSTOM_COLOR_SWITCH, false);
        mCustomImage = Xprefs.getBoolean(AOD_CLOCK_CUSTOM_IMAGE, false);
        mCustomDeviceName = Xprefs.getString(AOD_CLOCK_CUSTOM_DEVICE_VALUE, "");
        mCustomUserName = Xprefs.getString(AOD_CLOCK_CUSTOM_USER_VALUE, "");
        mCustomUserImage = Xprefs.getBoolean(AOD_CLOCK_CUSTOM_USER_IMAGE, false);
        mClockScale = Xprefs.getSliderFloat(AOD_CLOCK_TEXT_SCALING, 1.0f);
        mLineHeight = Xprefs.getSliderInt(AOD_CLOCK_LINE_HEIGHT, 0);
        mCustomDateFormat = Xprefs.getString(AOD_CLOCK_DATE_FORMAT, "");

        if (Key.length > 0 && Key[0].equals(GLOBAL_FONT_STYLE) && mStockAodView != null) {
            mStockAodView.post(this::applyFontToAodView);
        }

    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(listenPackage)) return;

        Log.i("OxygenCustomizer-AOD", "AodClock hooks initializing for " + lpparam.packageName);

        initResources(mContext);

        LottieAn = ReflectedClass.of("com.airbnb.lottie.LottieAnimationView").getClazz();

        ReflectedClass AodClockLayout = ReflectedClass.of(
                "com.oplus.systemui.aod.aodclock.off.AodClockLayout", /* OOS 15, 14 */
                "com.oplusos.systemui.aod.aodclock.off.AodClockLayout" /* OOS 13 */
        );


        AodClockLayout
                .after("initForAodApk")
                        .run(param -> {
                            FrameLayout mAodViewFromApk = (FrameLayout) getObjectField(param.thisObject, "mAodViewFromApk");
                            mStockAodView = mAodViewFromApk;
                            mAodApkView = mAodViewFromApk;
                            ensureAodEngineHooks(mAodViewFromApk);
                            applyFontToAodView(mAodViewFromApk);
                            if (!mAodClockEnabled) return;
                            if (!canShowAodClock()) return;
                            for (int i = 0; i < mAodViewFromApk.getChildCount(); i++) {
                                log(" mAodViewFromApk " + mAodViewFromApk.getChildAt(i).getClass().getCanonicalName());
                                if (mAodViewFromApk.getChildAt(i) instanceof ViewGroup v) {
                                    for (int j = 0; j < v.getChildCount(); j++) {
                                        mRootLayout = v;
                                        if (v.getChildAt(j) instanceof ViewGroup v2) {
                                            for (int k = 0; k < v2.getChildCount(); k++) {
                                                v.getChildAt(k).setVisibility(View.GONE);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            try {
                                updateClockView();
                            } catch (Throwable t) {
                                log(t);
                            }
                        });


        AodClockLayout
                .after("performTimeUpdate")
                        .run(param -> {
                            ensureAodEngineHooks(mAodApkView);
                            applyFontToAodView();
                            if (!mAodClockEnabled) return;
                            if (!canShowAodClock()) return;
                            try {
                                updateClockView();
                            } catch (Throwable t) {
                                log(t);
                            }
                        });

        // OOS 16 renders the stock AOD clock in a separate SystemUI view tree
        // (keyguard_style_clock), outside AodClockLayout.mAodViewFromApk.
        // Capture that tree when it is attached so the global font also covers
        // the native time, date/weather text and any native battery text.
        ReflectedClass SystemUiView = ReflectedClass.ofIfPossible("android.view.View");
        SystemUiView
                .after("onAttachedToWindow")
                .run(param -> {
                    try {
                        View view = (View) param.thisObject;
                        if (!isNativeAodClockRoot(view)) return;

                        mStockAodView = view;
                        applyFontToAodView(view);
                        applyFontToNativeAodClockIds(view);
                        log("Applied global font to native AOD clock tree: " + view.getClass().getName());
                    } catch (Throwable t) {
                        log("Unable to apply global font to native AOD clock tree: " + t);
                    }
                });

        // Some OOS 16 clock implementations reset the TextView typeface during
        // their own layout pass. Re-apply only to TextViews belonging to the
        // native AOD tree after attachment and layout.
        ReflectedClass SystemUiTextView = ReflectedClass.of(TextView.class);
        SystemUiTextView
                .after("onAttachedToWindow")
                .run(param -> applyFontToNativeAodTextView((TextView) param.thisObject));
        SystemUiTextView
                .after("setText")
                .run(param -> {
                    TextView textView = (TextView) param.thisObject;
                    if (!isDescendantOfNativeAodClock(textView)) return;
                    applyFontToNativeAodTextView(textView);
                    if (!mNativeAodTextHookLogged) {
                        mNativeAodTextHookLogged = true;
                        Log.i("OxygenCustomizer-AOD", "Native AOD TextView update hit, font=" + mFontStyle);
                    }
                });
        SystemUiTextView
                .after("setTypeface")
                .run(param -> applyFontToNativeAodTextView((TextView) param.thisObject));

        // Per-frame hooks (TextView.onDraw/onLayout, ViewGroup/FrameLayout
        // dispatchDraw, WrapGridLayout, CustomOplusKeyguardStyleClock draw) were
        // removed to cut SystemUI CPU: fonts are re-applied on the events where
        // the stock code resets them (setText / setTypeface / attach / time
        // updates), and the AOD engine is handled in ensureAodEngineHooks().
        ReflectedClass NativeOplusKeyguardStyleClockBase = ReflectedClass.ofIfPossible(
                "com.oplus.keyguard.OplusKeyguardStyleClock",
                lpparam.classLoader
        );
        NativeOplusKeyguardStyleClockBase
                .after("onAttachedToWindow")
                .run(param -> {
                    View view = (View) param.thisObject;
                    applyFontToAodView(view);
                    applyFontToNativeAodAncestors(view);
                    view.post(() -> {
                        applyFontToAodView(view);
                        applyFontToNativeAodClockIds(view);
                        applyFontToNativeAodAncestors(view);
                        view.invalidate();
                    });
                });
        // Clock plugin classes (KeyguardClockBase / PersonalityClocks) are not
        // loaded through createPackageContext(CONTEXT_INCLUDE_CODE): that made
        // a second copy of their classes, and hooks on those copies never ran.
        // The classes SystemUI really uses are hooked in their actual
        // ClassLoader (ensureNativeClockHooks / ensureAodEngineHooks).
    }





    private boolean isNativeAodClockTextId(View view) {
        return getNativeAodClockTextId(view) != null;
    }

    private String getNativeAodClockTextId(View view) {
        if (view == null || view.getId() == View.NO_ID) return null;
        // These checks run on every draw/setText of every TextView: resolve
        // each view id to its entry name once and cache it ("" = not ours).
        int id = view.getId();
        // Keyed per Resources: plugin (KeyguardClockBase) ids can collide
        // with SystemUI's 0x7f ids.
        android.content.res.Resources res = view.getResources();
        android.util.SparseArray<String> names = mClockTextIdNames.get(res);
        if (names == null) {
            names = new android.util.SparseArray<>();
            mClockTextIdNames.put(res, names);
        }
        String cached = names.get(id);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String result = "";
        try {
            String name = view.getResources().getResourceEntryName(id);
            switch (name) {
                case "clock_time_hour_first_txt", "clock_time_hour_second_txt",
                        "clock_time_symbol_text", "clock_time_minutes_txt", "week_text",
                        "date_text", "extra_message_content" -> result = name;
                default -> {
                }
            }
        } catch (Throwable ignored) {
        }
        names.put(id, result);
        return result.isEmpty() ? null : result;
    }

    private boolean isNativeAodClockRoot(View view) {
        if (view == null || view.getId() == View.NO_ID) return false;
        // Compare ints instead of looking up the entry name for every view on
        // every frame; the id is resolved once from SystemUI's resources.
        if (mKeyguardStyleClockId == 0) {
            try {
                mKeyguardStyleClockId = mContext.getResources().getIdentifier(
                        "keyguard_style_clock", "id", mContext.getPackageName());
            } catch (Throwable ignored) {
            }
            if (mKeyguardStyleClockId == 0) mKeyguardStyleClockId = -1;
        }
        // Plugin views can reuse the same 0x7f id, so confirm the name only
        // when the int matches (rare); all other views exit on the int test.
        if (mKeyguardStyleClockId > 0 && view.getId() != mKeyguardStyleClockId) return false;
        try {
            return "keyguard_style_clock".equals(view.getResources().getResourceEntryName(view.getId()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyFontToNativeAodAncestors(View view) {
        applyFontToNativeAodAncestors(view, false);
    }

    private void applyFontToNativeAodAncestors(View view, boolean onDraw) {
        View current = view;
        for (int i = 0; i < 8 && current != null; i++) {
            if (isNativeAodClockRoot(current)) {
                // No invalidate(): this also runs from onDraw/dispatchDraw, so
                // it would request another frame every frame (endless redraw
                // on the lockscreen). setTypeface() invalidates when it changes.
                if (onDraw) {
                    applyFontToAodViewOnDraw(current);
                } else {
                    applyFontToAodView(current);
                }
                applyFontToNativeAodClockIds(current);
                return;
            }
            if (!(current.getParent() instanceof View parent)) return;
            current = parent;
        }
    }

    private void applyFontToNativeAodTextView(TextView textView) {
        if (textView == null || !isDescendantOfNativeAodClock(textView)) return;
        if (mApplyingNativeTypeface) return;
        Typeface typeface = loadClockTypeface(mFontStyle);
        if (typeface != null && !typeface.equals(textView.getTypeface())) {
            mApplyingNativeTypeface = true;
            try {
                textView.setTypeface(typeface);
                textView.getPaint().setTypeface(typeface);
            } finally {
                mApplyingNativeTypeface = false;
            }
        }
    }

    private void applyFontToNativeAodClockIds(View root) {
        if (root == null) return;
        Typeface typeface = loadClockTypeface(mFontStyle);
        for (String name : NATIVE_CLOCK_TIME_IDS) {
            try {
                int id = root.getResources().getIdentifier(
                        name, "id", "com.oplus.keyguard.clock.base");
                View child = id == 0 ? null : root.findViewById(id);
                if (!(child instanceof TextView textView)) continue;

                View clockTimeView = findNativeClockTimeView(textView);
                if (clockTimeView != null) {
                    ensureNativeClockHooks(clockTimeView);
                    refreshNativeClockTimeView(clockTimeView);
                }
                if (typeface != null && !typeface.equals(textView.getTypeface())) {
                    textView.setTypeface(typeface);
                }
                if (!mNativeAodDiagnosticLogged) {
                    mNativeAodDiagnosticLogged = true;
                    XposedBridge.log("[OxygenCustomizer-AOD] native text id hit: "
                            + name + ", class=" + textView.getClass().getName()
                            + ", clockTimeView=" + (clockTimeView != null));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Hook the egview text widgets in the ClassLoader that loaded the live AOD
     * views. TimeView re-applies its variable OPlus Sans font in
     * setTextWidget(), including in the middle of drawVerticalTime(), so the
     * selected Typeface is put back on mTextPaint after that call and right
     * before each draw. Only the Paint changes: no layout or invalidate.
     */
    private void ensureAodEngineHooks(View root) {
        Class<?> textViewClass = findAodEngineTextViewClass(root, 0);
        if (textViewClass == null) return;
        ClassLoader loader = textViewClass.getClassLoader();
        if (loader == null || !mHookedAodEngineLoaders.add(loader)) return;
        XposedBridge.log("[OxygenCustomizer-AOD] hooking egview text widgets in " + loader);

        // The OOS 16 "Insight" AOD clock draws time and date with plain
        // TextViews but re-applies red_font_text_45lt.ttf in these methods.
        ReflectedClass parsonsClock = ReflectedClass.ofIfPossible(AOD_ENGINE_PARSONS_CLOCK_CLASS, loader);
        for (String method : new String[]{"applyLayoutParams", "onTimeChanged",
                "updateResource", "onAttachedToWindow"}) {
            parsonsClock
                    .after(method)
                    .run(param -> {
                        applyFontToAodView((View) param.thisObject);
                        if (!mAodParsonsClockLogged && loadClockTypeface(mFontStyle) != null) {
                            mAodParsonsClockLogged = true;
                            XposedBridge.log("[OxygenCustomizer-AOD] OpParsonsClock font applied after "
                                    + method + ", font=" + mFontStyle);
                        }
                    });
        }

        ReflectedClass timeView = ReflectedClass.ofIfPossible(AOD_ENGINE_TIME_VIEW_CLASS, loader);
        ReflectedClass aodTextView = ReflectedClass.ofIfPossible(AOD_ENGINE_AOD_TEXT_VIEW_CLASS, loader);
        ReflectedClass attributeTextView = ReflectedClass.ofIfPossible(AOD_ENGINE_TEXT_VIEW_CLASS, loader);

        timeView
                .after("setTextWidget")
                .run(param -> applyFontToAodEnginePaint(param.thisObject));
        timeView
                .before("onDraw")
                .run(param -> applyFontToAodEnginePaint(param.thisObject));
        aodTextView
                .before("onDraw")
                .run(param -> applyFontToAodEnginePaint(param.thisObject));
        attributeTextView
                .before("onDraw")
                .run(param -> applyFontToAodEnginePaint(param.thisObject));
        attributeTextView
                .after(Pattern.compile("setTypeface.*"))
                .run(param -> applyFontToAodEnginePaint(param.thisObject));
    }

    private Class<?> findAodEngineTextViewClass(View view, int depth) {
        if (view == null || depth > 24) return null;
        for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass()) {
            if (AOD_ENGINE_TEXT_VIEW_CLASS.equals(type.getName())) return type;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                Class<?> found = findAodEngineTextViewClass(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void applyFontToAodEnginePaint(Object view) {
        Typeface typeface = loadClockTypeface(mFontStyle);
        if (typeface == null) return;
        try {
            Object paint = getObjectField(view, "mTextPaint");
            if (paint instanceof Paint textPaint && textPaint.getTypeface() != typeface) {
                textPaint.setTypeface(typeface);
                if (!mAodEnginePaintLogged) {
                    mAodEnginePaintLogged = true;
                    XposedBridge.log("[OxygenCustomizer-AOD] egview paint font applied: "
                            + view.getClass().getName() + ", font=" + mFontStyle);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private View findNativeClockTimeView(View view) {
        View current = view;
        for (int i = 0; i < 6 && current != null; i++) {
            if (NATIVE_CLOCK_TIME_VIEW_CLASS.equals(current.getClass().getName())) return current;
            if (!(current.getParent() instanceof View parent)) return null;
            current = parent;
        }
        return null;
    }

    /**
     * Hook ThemeClockHelper in the ClassLoader that actually loaded the live
     * ClockTimeView. A class resolved through createPackageContext() may be a
     * separate copy, so hooking only that copy never affects the visible clock.
     */
    private void ensureNativeClockHooks(View clockTimeView) {
        ClassLoader loader = clockTimeView.getClass().getClassLoader();
        if (loader == null || !mHookedNativeClockLoaders.add(loader)) return;
        hookNativeThemeClockHelper(ReflectedClass.ofIfPossible(NATIVE_THEME_CLOCK_HELPER_CLASS, loader));
    }

    /**
     * OOS 16 ClockTimeView.render() draws the digits as compound image
     * drawables while ThemeClockHelper.isClockImg() is true, so the Typeface is
     * ignored. Reporting false makes render() take its native text path, which
     * clears the drawables and restores the text layout height itself.
     */
    private void hookNativeThemeClockHelper(ReflectedClass helper) {
        Class<?> clazz = helper.getClazz();
        if (clazz == null || !mHookedThemeClockHelpers.add(clazz)) return;
        XposedBridge.log("[OxygenCustomizer-AOD] hooking ThemeClockHelper in " + clazz.getClassLoader());

        helper
                .after("isClockImg")
                .run(param -> {
                    if (loadClockTypeface(mFontStyle) == null) return;
                    param.setResult(false);
                    if (!mNativeThemeHookLogged) {
                        mNativeThemeHookLogged = true;
                        XposedBridge.log("[OxygenCustomizer-AOD] ThemeClockHelper.isClockImg intercepted, font="
                                + mFontStyle);
                    }
                });
        // Fallbacks for callers that use the image helpers without isClockImg().
        helper
                .before("setTextImg")
                .run(param -> {
                    if (loadClockTypeface(mFontStyle) == null) return;
                    if (param.args == null || param.args.length < 2
                            || !(param.args[0] instanceof TextView textView)
                            || !(param.args[1] instanceof String text)) {
                        return;
                    }
                    textView.setCompoundDrawables(null, null, null, null);
                    textView.setText(text);
                    applyFontToNativeAodTextView(textView);
                    param.setResult(null);
                });
        helper
                .before("setTextSymbol")
                .run(param -> {
                    if (loadClockTypeface(mFontStyle) == null) return;
                    if (param.args == null || param.args.length < 1
                            || !(param.args[0] instanceof TextView textView)) {
                        return;
                    }
                    textView.setCompoundDrawables(null, null, null, null);
                    textView.setText(":");
                    applyFontToNativeAodTextView(textView);
                    param.setResult(null);
                });
    }

    /**
     * render() skips work while the time is unchanged. Re-render once per
     * font change so the new image/text path is visible immediately instead
     * of at the next minute tick. Runs posted, never inside a draw pass.
     */
    private void refreshNativeClockTimeView(View clockTimeView) {
        String style = mFontStyle == null ? FONT_STYLE_SYSTEM : mFontStyle;
        String renderedStyle = mNativeClockRenderedStyle.get(clockTimeView);
        if (style.equals(renderedStyle)) return;
        mNativeClockRenderedStyle.put(clockTimeView, style);
        // Native image digits are already correct for the system font.
        if (renderedStyle == null && FONT_STYLE_SYSTEM.equals(style)) return;

        clockTimeView.post(() -> {
            Object state = null;
            try {
                state = getObjectField(clockTimeView, "renderedViewState");
                if (state == null) {
                    mNativeClockRenderedStyle.remove(clockTimeView);
                    return;
                }
                Method render = null;
                for (Method method : clockTimeView.getClass().getDeclaredMethods()) {
                    if ("render".equals(method.getName()) && !method.isBridge()
                            && method.getParameterCount() == 2
                            && method.getParameterTypes()[0].isInstance(state)) {
                        render = method;
                        break;
                    }
                }
                if (render == null) return;
                setObjectField(clockTimeView, "currentTimeTriple", null);
                setObjectField(clockTimeView, "renderedViewState", null);
                render.setAccessible(true);
                render.invoke(clockTimeView, state, null);
                applyFontToNativeClockTimeView(clockTimeView);
                XposedBridge.log("[OxygenCustomizer-AOD] ClockTimeView re-rendered, font=" + style);
            } catch (Throwable t) {
                XposedBridge.log("[OxygenCustomizer-AOD] ClockTimeView re-render failed: " + t);
                try {
                    if (state != null && getObjectField(clockTimeView, "renderedViewState") == null) {
                        setObjectField(clockTimeView, "renderedViewState", state);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void applyFontToNativeClockTimeView(View view) {
        if (view == null) return;
        applyFontToAodView(view);
        String[] childFields = {
                "timeHourFirstTextView",
                "timeHourSecondTextView",
                "timeMinuteTextView",
                "timeSymbolTextView",
                "timeAmPmTextView"
        };
        for (String fieldName : childFields) {
            try {
                Object child = getObjectField(view, fieldName);
                if (child instanceof TextView textView) {
                    Typeface typeface = loadClockTypeface(mFontStyle);
                    if (typeface != null) textView.setTypeface(typeface);
                }
            } catch (Throwable ignored) {
            }
        }
    }



    private boolean isDescendantOfNativeAodClock(View view) {
        View current = view;
        for (int i = 0; i < 16 && current != null; i++) {
            if (isNativeAodClockRoot(current)) return true;
            if (!(current.getParent() instanceof View parent)) return false;
            current = parent;
        }
        return false;
    }

    private boolean canShowAodClock() {
        if (Build.VERSION.SDK_INT < 35) return true;

        int fullScreenAod = Settings.Secure.getInt(mContext.getContentResolver(), "full_screen_aod", 0);
        return fullScreenAod == 0;
    }

    private void updateClockView() {

        if (mRootLayout == null) return;

        log(" updateClockView " + mRootLayout.getChildCount());

        View clockView = getClockView();

        boolean isClockAdded = mRootLayout.findViewWithTag(OC_AOD_CLOCK_TAG) != null;

        // Remove existing clock view
        if (isClockAdded) {
            mRootLayout.removeView(mRootLayout.findViewWithTag(OC_AOD_CLOCK_TAG));
        }

        if (clockView != null) {
            clockView.setTag(OC_AOD_CLOCK_TAG);

            if (clockView.getParent() != null) {
                ((ViewGroup) clockView.getParent()).removeView(clockView);
            }

            mRootLayout.addView(clockView, 0);
            modifyClockView(clockView);
            initSoundManager();
            initBatteryStatus();

        }
    }

    private Typeface loadClockTypeface(String style) {
        // Do not read Xprefs here: RemotePreferences does an IPC query to the
        // module app on every get, and this runs from draw/text hooks.
        // updatePrefs() keeps mFontStyle current whenever the setting changes.
        return GlobalFontHelper.get(appContext, style);
    }

    private void applyFontToAodView() {
        applyFontToAodView(mStockAodView);
        applyFontToAodView(mRootLayout);
    }

    /**
     * For per-frame hooks (onDraw / dispatchDraw / onLayout) only: walking the
     * whole tree with reflection every frame is costly, so do it at most once
     * per second per view. Event hooks (OpParsonsClock callbacks, setText,
     * attach, time updates) must call applyFontToAodView() directly, because
     * the stock code re-sets its own font right before them.
     */
    private void applyFontToAodViewOnDraw(View view) {
        if (view == null) return;
        Typeface typeface = loadClockTypeface(mFontStyle);
        if (typeface == null) return;
        if (typeface != mThrottleTypeface) {
            mFontAppliedAt.clear();
            mThrottleTypeface = typeface;
        }
        long now = android.os.SystemClock.uptimeMillis();
        Long last = mFontAppliedAt.get(view);
        if (last != null && now - last < FONT_REAPPLY_INTERVAL_MS) return;
        mFontAppliedAt.put(view, now);
        applyFontToAodView(view);
    }

    private void applyFontToAodView(View view) {
        if (view == null || mApplyingAodFont) return;
        Typeface typeface = loadClockTypeface(mFontStyle);
        if (typeface == null) return;
        // setTypeface()/setText() hooks call back into this method; without
        // this guard the recursion runs until SystemUI ANRs.
        mApplyingAodFont = true;
        try {
            ViewHelper.applyFontToView(view, typeface);
        } finally {
            mApplyingAodFont = false;
        }
    }

    private void modifyClockView(View clockView) {
        int systemAccent = getPrimaryColor(mContext);

        Typeface typeface = loadClockTypeface(mFontStyle);

        ViewHelper.setMargins(clockView, mContext, 0, 0, 0, 0);
        log(" customColor: " + mCustomColor);

        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent1", mCustomColor ? accent1 : systemAccent);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent2", mCustomColor ? accent2 : systemAccent);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent3", mCustomColor ? accent3 : systemAccent);
        if (mCustomColor) {
            ViewHelper.findViewWithTagAndChangeColor(clockView, "text1", text1);
            ViewHelper.findViewWithTagAndChangeColor(clockView, "text2", text2);
        }
        if (mVolumeLevelArcProgress != null) {
            mVolumeLevelArcProgress.setColors(mCustomColor ? accent1 : systemAccent, text1);
        }
        if (mRamUsageArcProgress != null) {
            mRamUsageArcProgress.setColors(mCustomColor ? accent1 : systemAccent, text1);
        }

        if (typeface != null) {
            // Some AOD clock templates render through a private Paint instead
            // of TextView's normal paint. Apply both TextView and Paint fields.
            ViewHelper.applyFontToView(clockView, typeface);
        }

        ViewHelper.applyTextMarginRecursively((ViewGroup) clockView, mLineHeight);

        if (mClockScale != 1.0f) {
            ViewHelper.applyTextScalingRecursively((ViewGroup) clockView, mClockScale);
        }
        clockView.setVisibility(View.VISIBLE);

        TextClock textClock = (TextClock) findViewWithTag(clockView, "textClockDate");
        if (!TextUtils.isEmpty(mCustomDateFormat) && textClock != null) {
            try {
                textClock.setFormat12Hour(mCustomDateFormat);
                textClock.setFormat24Hour(mCustomDateFormat);
            } catch (Throwable t) {
                log("Error setting date format: " + t.getMessage());
            }
        }

        TextView deviceName = (TextView) findViewWithTag(clockView, "device_name");
        if (deviceName != null) {
            deviceName.setText(mCustomDeviceName.isEmpty() ? Build.MODEL : mCustomDeviceName);
        }

        TextView username = (TextView) findViewWithTag(clockView, "username");
        if (username != null) {
            username.setText(mCustomUserName.isEmpty() ? getUserName() : mCustomUserName);
        }

        ImageView profilePicture = (ImageView) findViewWithTag(clockView, "profile_picture");
        if (mCustomUserImage && profilePicture != null) {
            profilePicture.post(() -> profilePicture.setImageDrawable(getCustomUserImage(profilePicture)));
        }

        View customImage = findViewWithTag(clockView, "custom_image");
        if (mCustomImage && customImage != null) {
            boolean isRound = mAodClockStyle == 35;
            setCustomImage(customImage, getCustomImage(customImage, isRound));
        }

        mBatteryStatusView = null;
        mBatteryLevelView = null;
        mBatteryProgress = null;
        mVolumeLevelView = null;
        mVolumeProgress = null;
        mVolumeLevelArcProgress = null;
        mRamUsageArcProgress = null;

        switch (mAodClockStyle) {
            case 2 -> {
                TextClock tickIndicator = (TextClock) findViewWithTag(clockView, "tickIndicator");
                tickIndicator.setTextColor(Color.TRANSPARENT);
                TextView hourView = (TextView) findViewWithTag(clockView, "hours");
                TimeUtils.setCurrentTimeTextClockRed(tickIndicator, hourView, mCustomColor ? accent1 : getPrimaryColor(mContext));
            }
            case 5 -> {
                mBatteryStatusView = (TextView) findViewWithTag(clockView, "battery_status");
                mBatteryLevelView = (TextView) findViewWithTag(clockView, "battery_percentage");
                mVolumeLevelView = (TextView) findViewWithTag(clockView, "volume_level");
                mBatteryProgress = (ProgressBar) findViewWithTag(clockView, "battery_progressbar");
                mVolumeProgress = (ProgressBar) findViewWithTag(clockView, "volume_progressbar");
            }
            case 7 -> {
                ImageView imageView = (ImageView) findViewWithTag(clockView, "user_profile_image");
                imageView.post(() ->
                        imageView.setImageDrawable(mCustomUserImage ? getCustomUserImage(imageView) : getUserImage()));
            }
            case 19 -> {
                mBatteryLevelView = (TextView) findViewWithTag(clockView, "battery_percentage");
                mBatteryProgress = (ProgressBar) findViewWithTag(clockView, "battery_progressbar");
                LinearLayout volumeProgress = (LinearLayout) findViewWithTag(clockView, "volume_progress");
                if (mVolumeLevelArcProgress == null) {
                    mVolumeLevelArcProgress = new ProgressImageView(mContext);
                    mVolumeLevelArcProgress.setProgressType(ProgressImageView.ProgressType.VOLUME);
                    mVolumeLevelArcProgress.setColors(mCustomColor ? accent1 : getPrimaryColor(mContext), text1);
                }
                volumeProgress.addView(mVolumeLevelArcProgress);
                volumeProgress.setBackground(null);
                LinearLayout ramProgress = (LinearLayout) findViewWithTag(clockView, "ram_usage_info");
                if (mRamUsageArcProgress == null) {
                    mRamUsageArcProgress = new ProgressImageView(mContext);
                    mRamUsageArcProgress.setProgressType(ProgressImageView.ProgressType.MEMORY);
                    mRamUsageArcProgress.setColors(mCustomColor ? accent1 : getPrimaryColor(mContext), text1);
                }
                ramProgress.addView(mRamUsageArcProgress);
                ramProgress.setBackground(null);

                mBatteryProgress.setProgressTintList(ColorStateList.valueOf(mCustomColor ? accent1 : getPrimaryColor(mContext)));
            }
            case 25 -> {
                ImageView imageView = (ImageView) findViewWithTag(clockView, "custom_image");
                if (mCustomImage) {
                    imageView.setImageDrawable(getCustomImage(imageView, false));
                }
            }
            case 27 -> {
                TextView hourView = (TextView) findViewWithTag(clockView, "textHour");
                TextView minuteView = (TextView) findViewWithTag(clockView, "textMinute");
                TextClock tickIndicator = (TextClock) findViewWithTag(clockView, "tickIndicator");

                TimeUtils.setCurrentTimeTextClock(mContext, tickIndicator, hourView, minuteView);
            }
        }

        // TimeClock/TextClock templates update their text after inflation.
        // Re-apply after that initialization so the selected typeface also
        // reaches custom AOD renderers that cache their Paint.
        if (typeface != null) {
            clockView.post(() -> {
                ViewHelper.applyFontToView(clockView, typeface);
                clockView.invalidate();
            });
        }
    }

    @SuppressLint("DiscouragedApi")
    private View getClockView() {
        if (appContext == null) return null;
        LayoutInflater inflater = LayoutInflater.from(appContext);

        int resId = appContext
                .getResources()
                .getIdentifier(
                        AOD_CLOCK_LAYOUT + mAodClockStyle,
                        "layout",
                        BuildConfig.APPLICATION_ID
                );
        if (resId == 0) {
            resId = appContext
                    .getResources()
                    .getIdentifier(
                            LOCKSCREEN_CLOCK_LAYOUT + mAodClockStyle,
                            "layout",
                            BuildConfig.APPLICATION_ID
                    );
        }

        View v = inflater.inflate(resId, null);

        loadLottieAnimationView(
                appContext,
                LottieAn,
                v,
                mAodClockStyle
        );

        return v;

    }

    private void initBatteryStatus() {
        if (mBatteryStatusView != null) {
            if (mBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
                mBatteryStatusView.setText(ResourceManager.modRes.getString(R.string.battery_charging));
            } else if (mBatteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING ||
                    mBatteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                mBatteryStatusView.setText(ResourceManager.modRes.getString(R.string.battery_discharging));
            } else if (mBatteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
                mBatteryStatusView.setText(ResourceManager.modRes.getString(R.string.battery_full));
            } else if (mBatteryStatus == BatteryManager.BATTERY_STATUS_UNKNOWN) {
                mBatteryStatusView.setText(ResourceManager.modRes.getString(R.string.battery_level_percentage));
            }
        }

        if (mBatteryProgress != null) {
            mBatteryProgress.setProgress(mBatteryPercentage);
            if (mAodClockStyle == 19) {
                mBatteryProgress.setProgressTintList(ColorStateList.valueOf(mCustomColor ? accent1 : getPrimaryColor(mContext)));
            }
        }
        if (mBatteryLevelView != null) {
            mBatteryLevelView.setText(appContext.getResources().getString(R.string.percentage_text, mBatteryPercentage));
        }
    }

    private void initSoundManager() {
        if (!mAodClockEnabled || (Build.VERSION.SDK_INT >= 35 && !canShowAodClock())) return;
        int volLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volPercent = (int) (((float) volLevel / maxVolLevel) * 100);

        if (mVolumeProgress != null) {
            mVolumeProgress.setProgress(volPercent);
        }
        if (mVolumeLevelView != null) {
            mVolumeLevelView.setText(appContext.getResources().getString(R.string.percentage_text, volPercent));
        }
    }

    @SuppressLint("MissingPermission")
    private String getUserName() {
        if (mUserManager == null) {
            return "User";
        }

        String username = mUserManager.getUserName();
        return !username.isEmpty() ?
                mUserManager.getUserName() :
                appContext.getResources().getString(R.string.default_user_name);
    }

    @SuppressWarnings("all")
    private Drawable getUserImage() {
        if (mUserManager == null) {
            return appContext.getResources().getDrawable(R.drawable.default_avatar);
        }

        try {
            Method getUserIconMethod = mUserManager.getClass().getMethod("getUserIcon", int.class);
            int userId = (int) UserHandle.class.getDeclaredMethod("myUserId").invoke(null);
            Bitmap bitmapUserIcon = (Bitmap) getUserIconMethod.invoke(mUserManager, userId);
            return new BitmapDrawable(mContext.getResources(), bitmapUserIcon);
        } catch (Throwable throwable) {
            log(throwable);
            return appContext.getResources().getDrawable(R.drawable.default_avatar);
        }
    }

    private Drawable getImageFromFile(String fileName, @DrawableRes int defaultImage) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(new File(Environment.getExternalStorageDirectory() + "/.oxygen_customizer/" + fileName));

            Drawable drawable = ImageDecoder.decodeDrawable(source);

            if (drawable instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                ((AnimatedImageDrawable) drawable).start();
            }

            return drawable;
        } catch (Throwable t) {
            log(t);
            return ResourcesCompat.getDrawable(appContext.getResources(), defaultImage, appContext.getTheme());
        }
    }

    private Drawable getCustomUserImage(View view) {
        Drawable customUserImage = getImageFromFile("aod_user_image.png", R.drawable.default_avatar);
        return new CircleFramedDrawable(DrawableConverter.drawableToBitmap(customUserImage), view.getWidth());
    }

    private Drawable getCustomImage(View view, boolean isRound) {
        Drawable customUserImage = getImageFromFile("aod_custom_image.png", R.drawable.relax);
        CircleFramedDrawable circled = new CircleFramedDrawable(DrawableConverter.drawableToBitmap(customUserImage), view.getWidth());
        RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(mContext.getResources(), DrawableConverter.drawableToBitmap(customUserImage));
        roundedDrawable.setCornerRadius(32f);
        return isRound ? circled : roundedDrawable;
    }

    private void setCustomImage(View view, Drawable image) {
        if (view instanceof ImageView iv) {
            iv.post(() -> iv.setImageDrawable(image));
        } else {
            view.post(() -> view.setBackground(image));
        }
    }

    private void initResources(Context context) {
        try {
            appContext = context.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY
            );
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        mUserManager = SystemUtils.UserManager();
        mAudioManager = SystemUtils.AudioManager();

        try {
            context.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception ignored) {
        }
        try {
            context.registerReceiver(mVolumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
