package it.dhd.oxygencustomizer.xposed.hooks.systemui.lockscreen;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.General.CHARGING_ANIM_DURATION;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.General.GLOBAL_FONT_STYLE;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
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
 * - changes how long the animation stays on screen. OplusChargeAnimImpl
 * schedules mDelayCancelAnimRunnable after 14 s; only that exact post is
 * re-timed or dropped ("always"). Unplugging, touching the screen and screen
 * off still dismiss the animation natively.
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
    // Preference values: 0 = stock (14 s), -1 = always, otherwise milliseconds.
    private static final long DURATION_STOCK = 0;
    private static final long DURATION_ALWAYS = -1;

    private Context appContext;
    private String mFontStyle = FONT_STYLE_SYSTEM;
    private String mCachedTypefaceStyle;
    private Typeface mCachedTypeface;
    private boolean mFontAppliedLogged = false;
    private long mDuration = DURATION_STOCK;
    private volatile Runnable mCancelAnimRunnable;
    private static final long DECIMAL_RETRY_DELAY = 3000;
    private View mDecimalRetryView;
    private View mDecimalLoggedView;
    // Decimal continuation state (one plug-in session); % values, e.g. 88.47.
    private float mDecimalSpeed;
    private long mDecimalSpeedCurrent;
    private float mFirstShownValue;
    private long mFirstShownTime;
    private long mFirstShownCurrent;
    private float mEstimatedLevel;
    private long mEstimatedTime;
    private boolean mContinuing;
    // Seconds per real 1 % step, the integer level it applies to, and when
    // that level started (mAnchorReal: started by an observed real step).
    private float mSecondsPerPercent;
    private long mSecondsPerPercentCurrent;
    private int mAnchorLevel;
    private long mAnchorTime;
    private boolean mAnchorReal;
    private int mDecimalHundredths;
    // Fuel gauge: µAh needed for 1 %, counter at the anchor, recent samples.
    private float mChargePerPercent;
    private long mAnchorCounter;
    private final java.util.ArrayDeque<long[]> mCounterSamples = new java.util.ArrayDeque<>();
    private static final float DECIMAL_STEP_SLOWDOWN = 1.15f;
    private static final float DECIMAL_STEP_SLOWDOWN_UNCALIBRATED = 1.4f;
    private boolean mStepCalibrated;
    private View mContinueView;
    private boolean mDurationLogged = false;

    public ChargingAnimation(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mFontStyle = Xprefs.getString(GLOBAL_FONT_STYLE, FONT_STYLE_SYSTEM);
        try {
            mDuration = Long.parseLong(Xprefs.getString(CHARGING_ANIM_DURATION, "0"));
        } catch (NumberFormatException ignored) {
            mDuration = DURATION_STOCK;
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

        hookLevelFont();
        hookDuration();
        hookDecimalLevelRebind();
        hookDecimalContinuation();
    }

    /**
     * The kernel (oplus_chg_v2, /proc/ui_soc_decimal) only provides the
     * decimal level for ~17 s after fast charging starts
     * (calculate_decimal_time limit, then ui_soc_decimal_deinit -> "0, 0").
     * Its value is itself an estimate integrated from charge current.
     * Continue it the same way: use the speed measured from the kernel
     * values, scaled by the live charge current, anchored to the real integer
     * level (never past level + 0.99, re-synced when it changes).
     */
    private void hookDecimalContinuation() {
        // Hooks on the data path (the helper's private getDecimalData() and
        // OplusBatteryManager.getDecimalData()) are bypassed once ART compiles
        // and inlines the callers, so measure from the values the level view
        // actually displays and drive the view directly.
        for (String className : new String[]{
                "com.oplus.charge.view.ChargeLevelAndLogoView",
                "com.oplus.charge.view.FrameChargeLevelAndLogoView"}) {
            ReflectedClass.ofIfPossible(className)
                    .after("updateBatteryLevelView")
                    .run(param -> {
                        if (mContinuing || param.args.length != 3) return;
                        recordDisplayedDecimal((int) param.args[0], (int) param.args[1],
                                (int) param.args[2]);
                    });
        }
        ReflectedClass.ofIfPossible("com.oplus.charge.BatteryLevelToDecimalHelper")
                .after("stopCalculateBatteryRate")
                .run(param -> {
                    Object callback = getObjectField(param.thisObject, "mCallback");
                    if (callback instanceof View levelView) startDecimalContinuation(levelView);
                });
        // Calibration belongs to one plug-in session.
        try {
            mContext.registerReceiver(new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, android.content.Intent intent) {
                    resetDecimalEstimate();
                }
            }, new android.content.IntentFilter(android.content.Intent.ACTION_POWER_DISCONNECTED));
        } catch (Throwable t) {
            log("Unable to register power receiver: " + t.getMessage());
        }
    }

    /** Kernel-driven value shown by the stock code, e.g. 88, 4, 7 = 88.47 %. */
    private void recordDisplayedDecimal(int integer, int tenths, int hundredths) {
        float value = integer + tenths / 10f + hundredths / 100f;
        if (value <= 0) return;
        long now = android.os.SystemClock.elapsedRealtime();
        // Start sampling the fuel gauge during the kernel window, so the
        // charge rate is already known when the continuation takes over.
        try {
            recordChargeCounter(now, readChargeCounter(
                    (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE)));
        } catch (Throwable ignored) {
        }
        if (mFirstShownTime == 0 || value < mFirstShownValue) {
            mFirstShownValue = value;
            mFirstShownTime = now;
            mFirstShownCurrent = readChargeCurrent();
        }
        mEstimatedLevel = value;
        mEstimatedTime = now;
        // Average speed over the kernel window (needs a few seconds of data).
        if (now - mFirstShownTime >= 3000 && value > mFirstShownValue) {
            mDecimalSpeed = (value - mFirstShownValue) * 1000f / (now - mFirstShownTime);
            mDecimalSpeedCurrent = mFirstShownCurrent;
        }
    }

    private void startDecimalContinuation(View levelView) {
        if (mContinuing || mDecimalSpeed <= 0 || mEstimatedLevel <= 0) return;
        if (!isLevelViewShowing(levelView)) return;
        // Pace for one percent: the kernel-window speed until a real 1 % step
        // has been observed. Keep the current position inside this percent.
        if (mSecondsPerPercent <= 0) {
            mSecondsPerPercent = 1f / mDecimalSpeed;
            mSecondsPerPercentCurrent = mDecimalSpeedCurrent;
        }
        if (mChargePerPercent <= 0) {
            try {
                BatteryManager bm = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
                long counter = readChargeCounter(bm);
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (counter > 0 && level > 0) mChargePerPercent = (float) counter / level;
            } catch (Throwable ignored) {
            }
        }
        if (mAnchorLevel != (int) mEstimatedLevel) {
            // Continue from the last kernel value within this percent.
            mAnchorLevel = (int) mEstimatedLevel;
            mAnchorReal = false;
            mDecimalHundredths = Math.max(1, Math.min(98,
                    Math.round((mEstimatedLevel - mAnchorLevel) * 100)));
            mAnchorTime = android.os.SystemClock.elapsedRealtime()
                    - (long) (mDecimalHundredths / 100f * mSecondsPerPercent * 1000);
        }
        mContinuing = true;
        mContinueView = levelView;
        levelView.removeCallbacks(mDecimalRetry);
        XposedBridge.log("[OxygenCustomizer-Charging] kernel decimal ended, continuing at "
                + mDecimalSpeed + " %/s from " + mEstimatedLevel);
        levelView.post(mContinueTick);
    }

    private final Runnable mContinueTick = new Runnable() {
        @Override
        public void run() {
            View levelView = mContinueView;
            if (!mContinuing || levelView == null || !levelView.isAttachedToWindow()
                    || !levelView.isShown()) {
                stopDecimalContinuation();
                return;
            }
            try {
                BatteryManager bm = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (level <= 0 || level >= 100) {
                    stopDecimalContinuation();
                    return;
                }
                long now = android.os.SystemClock.elapsedRealtime();
                long counter = readChargeCounter(bm);
                recordChargeCounter(now, counter);
                if (level != mAnchorLevel) {
                    // A real 1 % step (same source as the status bar):
                    // measure what it actually took and restart at .01.
                    // Trust the latest measurement more (70 / 30).
                    if (mAnchorReal && level == mAnchorLevel + 1) {
                        float measuredSeconds = (now - mAnchorTime) / 1000f;
                        mSecondsPerPercent = mSecondsPerPercent * 0.3f + measuredSeconds * 0.7f;
                        mSecondsPerPercentCurrent = readChargeCurrent();
                        if (counter > 0 && mAnchorCounter > 0 && counter > mAnchorCounter) {
                            float measuredCharge = counter - mAnchorCounter;
                            mChargePerPercent = mChargePerPercent <= 0 ? measuredCharge
                                    : mChargePerPercent * 0.3f + measuredCharge * 0.7f;
                        }
                        mStepCalibrated = true;
                    }
                    mAnchorReal = mAnchorLevel > 0;
                    mAnchorLevel = level;
                    mAnchorTime = now;
                    mAnchorCounter = counter;
                    if (mChargePerPercent <= 0 && counter > 0) {
                        // Until a full 1 % has been measured: counter / level.
                        mChargePerPercent = (float) counter / level;
                    }
                    mDecimalHundredths = 1;
                } else if (mDecimalHundredths < 99) {
                    // One 0.01 step per tick so no digit is skipped; at .99
                    // wait for the real level instead of passing the status bar.
                    mDecimalHundredths++;
                }
                mEstimatedLevel = level + mDecimalHundredths / 100f;
                mEstimatedTime = now;
                int integer = level;
                int decimals = mDecimalHundredths;
                // Keep the stock integer path from overwriting the decimal.
                Object helper = getObjectField(levelView, "levelToDecimalHelper");
                if (helper != null) setIntField(helper, "mCurrentStatus", 1);
                callMethod(levelView, "updatePowerFormat", 1);
                callMethod(levelView, "updateBatteryLevelView", integer, decimals / 10, decimals % 10);
            } catch (Throwable t) {
                XposedBridge.log("[OxygenCustomizer-Charging] decimal continuation failed: " + t);
                stopDecimalContinuation();
                return;
            }
            levelView.postDelayed(this, nextStepDelay());
        }
    };

    /**
     * Time for one 0.01 step = (seconds per real 1 %) / 100, slowed by
     * DECIMAL_STEP_SLOWDOWN so the decimals reach .9x around the real step
     * instead of waiting at .99.
     * Seconds per 1 % comes from the fuel gauge: charge needed for 1 %
     * (measured on real steps) divided by the charge actually flowing in
     * (CHARGE_COUNTER over the last 10-60 s). This follows the slower
     * constant-voltage phase at high levels by itself. Without enough gauge
     * data it falls back to the step timing scaled by the charge current.
     */
    private long nextStepDelay() {
        float secondsPerPercent = -1;
        float chargeRate = chargeCounterRate();
        if (chargeRate > 0 && mChargePerPercent > 0) {
            secondsPerPercent = mChargePerPercent / chargeRate;
        }
        if (secondsPerPercent <= 0) {
            secondsPerPercent = mSecondsPerPercent;
            long current = readChargeCurrent();
            if (mSecondsPerPercentCurrent > 0 && current > 0) {
                // Current drops late in fast charging: slow down with it.
                secondsPerPercent *= Math.max(0.5f, Math.min(2f,
                        (float) mSecondsPerPercentCurrent / current));
            }
        }
        // Until one real 1 % has been measured the estimate is rough: be
        // more conservative so the first percent does not stall at .99.
        float slowdown = mStepCalibrated ? DECIMAL_STEP_SLOWDOWN : DECIMAL_STEP_SLOWDOWN_UNCALIBRATED;
        // Ease out after .80 (up to 4x at .99): the distance to the real step
        // is never known exactly, so spread any surplus over the last digits
        // instead of stopping at .99.
        if (mDecimalHundredths > 80) {
            float x = (mDecimalHundredths - 80) / 19f;
            slowdown *= 1f + 3f * x * x;
        }
        long delay = (long) (secondsPerPercent * 10f * slowdown);
        return Math.max(200, Math.min(10000, delay));
    }

    private long readChargeCounter(BatteryManager bm) {
        try {
            int value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            return value > 0 && value != Integer.MIN_VALUE ? value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private void recordChargeCounter(long now, long counter) {
        if (counter <= 0) return;
        mCounterSamples.addLast(new long[]{now, counter});
        while (mCounterSamples.size() > 1 && now - mCounterSamples.peekFirst()[0] > 60000) {
            mCounterSamples.removeFirst();
        }
    }

    /** Charge flowing in, in µAh per second, or -1 without enough data. */
    private float chargeCounterRate() {
        long[] newest = mCounterSamples.peekLast();
        if (newest == null) return -1;
        // Use the most recent window that spans >= 10 s and >= 3 mAh, so the
        // 1 mAh gauge resolution does not dominate.
        java.util.Iterator<long[]> it = mCounterSamples.descendingIterator();
        while (it.hasNext()) {
            long[] sample = it.next();
            long dt = newest[0] - sample[0];
            long dq = newest[1] - sample[1];
            if (dt >= 10000 && dq >= 3000) return dq * 1000f / dt;
        }
        return -1;
    }

    private void stopDecimalContinuation() {
        mContinuing = false;
        View levelView = mContinueView;
        mContinueView = null;
        if (levelView != null) levelView.removeCallbacks(mContinueTick);
    }

    private long readChargeCurrent() {
        try {
            BatteryManager bm = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
            return Math.abs((long) bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void resetDecimalEstimate() {
        stopDecimalContinuation();
        mDecimalSpeed = 0;
        mDecimalSpeedCurrent = 0;
        mFirstShownValue = 0;
        mFirstShownTime = 0;
        mFirstShownCurrent = 0;
        mEstimatedLevel = 0;
        mEstimatedTime = 0;
        mSecondsPerPercent = 0;
        mSecondsPerPercentCurrent = 0;
        mAnchorLevel = 0;
        mAnchorTime = 0;
        mAnchorReal = false;
        mDecimalHundredths = 0;
        mChargePerPercent = 0;
        mAnchorCounter = 0;
        mCounterSamples.clear();
        mStepCalibrated = false;
    }

    /**
     * SuperVOOC shows a live decimal level driven by the singleton
     * BatteryLevelToDecimalHelper, which pushes values to one mCallback view.
     * A newly created level view only calls setCallback(this) in
     * updateChargeAnim() when the helper loop is idle (mEstimatedBatteryRate
     * <= 0). If the previous animation view was removed without its fade-out
     * (which stops the loop), the loop keeps feeding the detached old view and
     * the visible number freezes. Re-point the running loop at the new view.
     */
    private void hookDecimalLevelRebind() {
        for (String className : new String[]{
                "com.oplus.charge.view.ChargeLevelAndLogoView",
                "com.oplus.charge.view.FrameChargeLevelAndLogoView"}) {
            ReflectedClass.ofIfPossible(className)
                    .after("updateChargeAnim")
                    .run(param -> {
                        // A new view may not be attached yet; check after layout.
                        View levelView = (View) param.thisObject;
                        levelView.postDelayed(() -> ensureDecimalLoop(levelView), 300);
                    });
            // Each animation cycle fades the level out (which stops the loop)
            // and back in, but the stock fade-in never restarts the loop.
            ReflectedClass.ofIfPossible(className)
                    .after("startLevelAndLogoFadeinAnimation")
                    .run(param -> {
                        View levelView = (View) param.thisObject;
                        levelView.postDelayed(() -> ensureDecimalLoop(levelView), 300);
                    });
        }
        // The loop stops itself when the kernel has no decimal estimate
        // (start/end 0) and is otherwise only restarted on a new plug-in.
        // While the animation is still on screen, retry periodically.
        ReflectedClass.ofIfPossible("com.oplus.charge.BatteryLevelToDecimalHelper")
                .after("stopCalculateBatteryRate")
                .run(param -> {
                    Object callback = getObjectField(param.thisObject, "mCallback");
                    if (callback instanceof View levelView) scheduleDecimalRetry(levelView);
                });
    }

    /**
     * Keep the decimal loop running and pointed at the visible level view.
     * Values always come from OplusBatteryManager.getDecimalData(); when the
     * kernel provides none the stock code falls back to the integer level.
     */
    private void ensureDecimalLoop(View levelView) {
        try {
            // While continuing past the kernel window the module drives the
            // view; the stock loop would only read "0, 0" and reset it.
            if (mContinuing || !isLevelViewShowing(levelView)) return;
            Object helper = getObjectField(levelView, "levelToDecimalHelper");
            if (helper == null) return;
            Handler handler = (Handler) getObjectField(helper, "mHandler");
            Runnable loop = (Runnable) getObjectField(helper, "mCalculateBatteryRateRunnable");
            if (handler == null || loop == null) return;

            boolean callbackChanged = getObjectField(helper, "mCallback") != levelView;
            if (callbackChanged) callMethod(helper, "setCallback", levelView);
            // Force the next animator tick to push its value to this view.
            setIntField(helper, "mLastIntegerPart", -1);
            setIntField(helper, "mLastDecimalPart", -1);

            // mStopCalculate starts out false, so also check the 1 s loop is
            // queued or its smoothing animator is running.
            Object animator = getObjectField(helper, "mSmoothedBatteryRateAnimator");
            boolean running = !getBooleanField(helper, "mStopCalculate")
                    && (handler.hasCallbacks(loop)
                    || (animator instanceof ValueAnimator va && va.isRunning()));
            if (!running) {
                // Same start sequence as the stock updateChargeAnim().
                setBooleanField(helper, "mStopCalculate", false);
                setIntField(helper, "mEstimateDuration", 1000);
                handler.removeCallbacks(loop);
                handler.post(loop);
            }
            if ((callbackChanged || !running) && mDecimalLoggedView != levelView) {
                mDecimalLoggedView = levelView;
                XposedBridge.log("[OxygenCustomizer-Charging] decimal level loop "
                        + (running ? "rebound" : "started") + " for "
                        + levelView.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            log("Unable to keep decimal level loop: " + t.getMessage());
        }
    }

    private void scheduleDecimalRetry(View levelView) {
        // Retry only while the level is on screen (it may be fading out
        // between cycles); stop once the animation is hidden or removed.
        if (mContinuing || levelView == null || !levelView.isAttachedToWindow()
                || !levelView.isShown()) return;
        try {
            Object level = getObjectField(levelView, "level");
            if (level instanceof Integer l && l >= 100) return;
        } catch (Throwable ignored) {
        }
        levelView.removeCallbacks(mDecimalRetry);
        mDecimalRetryView = levelView;
        levelView.postDelayed(mDecimalRetry, DECIMAL_RETRY_DELAY);
    }

    private final Runnable mDecimalRetry = () -> {
        View levelView = mDecimalRetryView;
        mDecimalRetryView = null;
        if (levelView == null || !levelView.isAttachedToWindow() || !levelView.isShown()) return;
        if (isFadingOut(levelView)) {
            // Between cycles: wait for the fade-in instead of fighting it.
            scheduleDecimalRetry(levelView);
        } else {
            ensureDecimalLoop(levelView);
        }
    };

    private boolean isLevelViewShowing(View levelView) {
        return levelView != null && levelView.isAttachedToWindow() && levelView.isShown()
                && !isFadingOut(levelView);
    }

    private boolean isFadingOut(View levelView) {
        try {
            return (boolean) callMethod(levelView, "getFadeoutAnimating");
        } catch (Throwable ignored) {
            return false;
        }
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

    private void hookDuration() {
        ReflectedClass OplusChargeAnimImpl = ReflectedClass.ofIfPossible(
                "com.oplus.charge.viewmodel.OplusChargeAnimImpl");
        OplusChargeAnimImpl
                .afterConstruction()
                .run(param -> captureCancelRunnable(param.thisObject));
        OplusChargeAnimImpl
                .before("updateChargeAnimState")
                .run(param -> captureCancelRunnable(param.thisObject));

        // Identity check only, so other posts pay a single field compare.
        ReflectedClass.of(Handler.class)
                .before("postDelayed")
                .run(param -> {
                    Runnable cancel = mCancelAnimRunnable;
                    if (cancel == null || mDuration == DURATION_STOCK
                            || param.args.length < 2 || param.args[0] != cancel) {
                        return;
                    }
                    int delayIndex = param.args.length - 1;
                    if (!(param.args[delayIndex] instanceof Long)) return;
                    if (mDuration == DURATION_ALWAYS) {
                        param.setResult(true);
                    } else {
                        param.args[delayIndex] = mDuration;
                    }
                    if (!mDurationLogged) {
                        mDurationLogged = true;
                        XposedBridge.log("[OxygenCustomizer-Charging] charging anim duration set to "
                                + (mDuration == DURATION_ALWAYS ? "always" : mDuration + " ms"));
                    }
                });
    }

    private void captureCancelRunnable(Object impl) {
        if (mCancelAnimRunnable != null) return;
        try {
            Object runnable = getObjectField(impl, "mDelayCancelAnimRunnable");
            if (runnable instanceof Runnable r) mCancelAnimRunnable = r;
        } catch (Throwable ignored) {
        }
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
