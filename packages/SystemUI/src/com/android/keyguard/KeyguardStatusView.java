/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.graphics.Typeface;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.ViewClippingUtil;
import com.android.keyguard.clocks.CustomAnalogClock;
import com.android.keyguard.clocks.CustomTextClock;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.omni.CurrentWeatherView;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import com.google.android.collect.Sets;

import java.lang.Math;
import java.util.Locale;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener, View.OnLayoutChangeListener {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

	private static final String FONT_FAMILY = "sans-serif-light";

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;
    private final float mSmallClockScale;

    private TextView mLogoutView;
    private CustomAnalogClock mCustomClockView;
    private CustomAnalogClock mDuClockView;
    private CustomAnalogClock mCustomNumClockView;
    private LinearLayout mTextClock;
    private TextClock mClockView;
    private TextView mTextClockV0;
    private TextView mTextClockV1;
    private TextView mTextClockV2;
    private View mClockSeparator;
    private TextView mOwnerInfo;
    private KeyguardSliceView mKeyguardSlice;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;


    private ArraySet<View> mVisibleInDoze;
    private boolean mPulsing;
    private boolean mWasPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private float mWidgetPadding;
    private int mLastLayoutHeight;
    private CurrentWeatherView mWeatherView;
    private boolean mShowWeather;

    private boolean mShowClock;
    private boolean mShowInfo;
    private int mClockSelection;
    private boolean mIsCenterAligned;
    private boolean mIsLeftAligned;
    private int mDateSelection;

    // Date styles paddings
    private int mDateVerPadding;
    private int mDateHorPadding;

    private boolean mWasLatestViewSmall;

    private boolean mForcedMediaDoze;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
            updateOwnerInfo();
            updateSettings();
            refreshLockFont();
            refreshLockDateFont();
            refreshclocksize();
            refreshdatesize();
            updateClockColor();
            updateClockDateColor();
            updateOwnerInfoColor();
            refreshOwnerInfoSize();
            refreshOwnerInfoFont();
            updateDateStyles();
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                updateSettings();
                refreshLockFont();
		refreshLockDateFont();
		refreshclocksize();
		refreshdatesize();
                updateClockColor();
                updateClockDateColor();
                updateOwnerInfoColor();
                refreshOwnerInfoSize();
                refreshOwnerInfoFont();
	    }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refreshFormat();
            updateOwnerInfo();
            updateLogoutView();
            refreshLockFont();
	    refreshLockDateFont();
	    refreshclocksize();
	    refreshdatesize();
            updateClockColor();
            updateClockDateColor();
            updateOwnerInfoColor();
            refreshOwnerInfoSize();
            refreshOwnerInfoFont();
	    updateSettings();
	}

        @Override
        public void onLogoutEnabledChanged() {
            updateLogoutView();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIActivityManager = ActivityManager.getService();
        mLockPatternUtils = new LockPatternUtils(getContext());
        mHandler = new Handler(Looper.myLooper());
        mSmallClockScale = getResources().getDimension(R.dimen.widget_small_font_size)
                / getResources().getDimension(R.dimen.widget_big_font_size);

        onDensityOrFontScaleChanged();
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, "Schedule setEnableMarquee: " + (enabled ? "Enable" : "Disable"));
        if (enabled) {
            if (mPendingMarqueeStart == null) {
                mPendingMarqueeStart = () -> {
                    setEnableMarqueeImpl(true);
                    mPendingMarqueeStart = null;
                };
                mHandler.postDelayed(mPendingMarqueeStart, MARQUEE_DELAY_MS);
            }
        } else {
            if (mPendingMarqueeStart != null) {
                mHandler.removeCallbacks(mPendingMarqueeStart);
                mPendingMarqueeStart = null;
            }
            setEnableMarqueeImpl(false);
        }
    }

    private void setEnableMarqueeImpl(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLogoutView = findViewById(R.id.logout);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.clock_view);
        mClockView.setShowCurrentUserTime(true);
        mCustomClockView = findViewById(R.id.custom_clock_view);
        mDuClockView = findViewById(R.id.du_clock_view);
        mCustomNumClockView = findViewById(R.id.custom_num_clock_view);
        mTextClock = findViewById(R.id.custom_textclock_view);
        mTextClockV0 = findViewById(R.id.custom_textclock_view0);
        mTextClockV1 = findViewById(R.id.custom_textclock_view1);
        mTextClockV2 = findViewById(R.id.custom_textclock_view2);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);
        mClockSeparator = findViewById(R.id.clock_separator);

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);

        mVisibleInDoze = Sets.newArraySet();
        if (mWeatherView != null) {
            mVisibleInDoze.add(mWeatherView);
        }
        if (mClockView != null) {
            mVisibleInDoze.add(mClockView);
        }
        if (mCustomClockView != null) {
            mVisibleInDoze.add(mCustomClockView);
        }
        if (mDuClockView != null) {
            mVisibleInDoze.add(mDuClockView);
        }
        if (mCustomNumClockView != null) {
            mVisibleInDoze.add(mCustomNumClockView);
        }
	if (mTextClock != null) {
	    mVisibleInDoze.add(mTextClock);
	}
	if (mKeyguardSlice != null) {
            mVisibleInDoze.add(mKeyguardSlice);
        }

        mTextColor = mClockView.getCurrentTextColor();

        int clockStroke = getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke);
        mClockView.getPaint().setStrokeWidth(clockStroke);
        mClockView.addOnLayoutChangeListener(this);
        mClockSeparator.addOnLayoutChangeListener(this);
        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        updateSettings();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
	updateSettings();
        refreshLockFont();
	refreshLockDateFont();
	refreshclocksize();
	refreshdatesize();
        updateClockColor();
        updateClockDateColor();
        updateOwnerInfoColor();
        refreshOwnerInfoSize();
        refreshOwnerInfoFont();

        // Disable elegant text height because our fancy colon makes the ymin value huge for no
        // reason.
        mClockView.setElegantTextHeight(false);

    }

    /**
     * Moves clock and separator, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        boolean smallClock = mKeyguardSlice.hasHeader() || mPulsing;
        prepareSmallView(smallClock);
        float clockScale = smallClock ? mSmallClockScale : 1;
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mClockView.getLayoutParams();
        int height = mClockView.getHeight();
        layoutParams.bottomMargin = (int) -(height - (clockScale * height));
        mClockView.setLayoutParams(layoutParams);

        // Custom analog clock
        RelativeLayout.LayoutParams customlayoutParams =
                (RelativeLayout.LayoutParams) mCustomClockView.getLayoutParams();
        customlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mCustomClockView.setLayoutParams(customlayoutParams);

        //Custom Text clock
        RelativeLayout.LayoutParams textlayoutParams =
                (RelativeLayout.LayoutParams) mTextClock.getLayoutParams();
        textlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mTextClock.setLayoutParams(textlayoutParams);

        // Du analog clock
        RelativeLayout.LayoutParams dulayoutParams =
                (RelativeLayout.LayoutParams) mDuClockView.getLayoutParams();
        dulayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mDuClockView.setLayoutParams(dulayoutParams);

        // Custom analog clock
        RelativeLayout.LayoutParams customnumlayoutParams =
                (RelativeLayout.LayoutParams) mCustomNumClockView.getLayoutParams();
        customnumlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mCustomNumClockView.setLayoutParams(customnumlayoutParams);

        /* TODO: Switch case for diff clock variants */
        mTextClockV0.setTextAppearance(getContext(), R.style.customtextclock_big_thin);
        mTextClockV1.setTextAppearance(getContext(), R.style.customtextclock_big_thin);
        mTextClockV2.setTextAppearance(getContext(), R.style.customtextclock_big_thin);
        layoutParams = (RelativeLayout.LayoutParams) mClockSeparator.getLayoutParams();
        layoutParams.topMargin = smallClock ? (int) mWidgetPadding : 0;
        layoutParams.bottomMargin = layoutParams.topMargin;
        mClockSeparator.setLayoutParams(layoutParams);
    }

    /**
     * Animate clock and its separator when necessary.
     */
    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int heightOffset = mPulsing || mWasPulsing ? 0 : getHeight() - mLastLayoutHeight;
        boolean hasHeader = mKeyguardSlice.hasHeader();
        boolean smallClock = hasHeader || mPulsing;
        prepareSmallView(smallClock);
        long duration = KeyguardSliceView.DEFAULT_ANIM_DURATION;
        long delay = smallClock || mWasPulsing ? 0 : duration / 4;
        mWasPulsing = false;

        boolean shouldAnimate = mKeyguardSlice.getLayoutTransition() != null
                && mKeyguardSlice.getLayoutTransition().isRunning();
        if (view == mClockView) {
            float clockScale = smallClock ? mSmallClockScale : 1;
            Paint.Style style = smallClock ? Paint.Style.FILL_AND_STROKE : Paint.Style.FILL;
            mClockView.animate().cancel();
            if (shouldAnimate) {
                mClockView.setY(oldTop + heightOffset);
                mClockView.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(new ClipChildrenAnimationListener())
                        .setStartDelay(delay)
                        .y(top)
                        .scaleX(clockScale)
                        .scaleY(clockScale)
                        .withEndAction(() -> {
                            mClockView.getPaint().setStyle(style);
                            mClockView.invalidate();
                        })
                        .start();
            } else {
                mClockView.setY(top);
                mClockView.setScaleX(clockScale);
                mClockView.setScaleY(clockScale);
                mClockView.getPaint().setStyle(style);
                mClockView.invalidate();
            }
        } else if (view == mClockSeparator) {
            boolean hasSeparator = hasHeader && !mPulsing;
            float alpha = hasSeparator ? 1 : 0;
            mClockSeparator.animate().cancel();
            if (shouldAnimate) {
                boolean isAwake = mDarkAmount != 0;
                mClockSeparator.setY(oldTop + heightOffset);
                mClockSeparator.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(isAwake ? null : new KeepAwakeAnimationListener(getContext()))
                        .setStartDelay(delay)
                        .y(top)
                        .alpha(alpha)
                        .start();
            } else {
                mClockSeparator.setY(top);
                mClockSeparator.setAlpha(alpha);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mClockView.setPivotX(mClockView.getWidth() / 2);
        mClockView.setPivotY(0);
        mLastLayoutHeight = getHeight();
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        mWidgetPadding = getResources().getDimension(R.dimen.widget_vertical_padding);
        if (mClockView != null) {
            mClockView.getPaint().setStrokeWidth(
                    getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke));
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        }
        if (mWeatherView != null) {
            mWeatherView.onDensityOrFontScaleChanged();
        }
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
        updateOwnerInfo();
        updateSettings();
        refreshLockFont();
        refreshLockDateFont();
        refreshclocksize();
        refreshdatesize();
        updateClockColor();
        updateClockDateColor();
        updateOwnerInfoColor();
        refreshOwnerInfoSize();
        refreshOwnerInfoFont();
        updateDateStyles();
    }

    private void refreshTime() {
        mClockView.refresh();

        if (mClockSelection == 0 || mWasLatestViewSmall) {
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 1) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>:mm"));
        } else if (mClockSelection == 5) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        } else if (mClockSelection == 6) {
            mClockView.setFormat12Hour(Html.fromHtml("hh<br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
        } else if (mClockSelection == 7) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color='#454545'>hh</font><br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color='#454545'>kk</font><br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
        } else if (mClockSelection == 9) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>h</strong>:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>kk</strong>:mm</font>"));
        } else if (mClockSelection == 10) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>h</strong></font>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>kk</strong></font>:mm"));
        } else if (mClockSelection == 11) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">:mm</font>"));
        } else {
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        }
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 29);
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 29);
    }

    private int getLockClockSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 78);
    }

    private int getLockDateSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKDATE_FONT_SIZE, 14);
    }

    private int getOwnerInfoFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_OWNERINFO_FONTS, 0);
    }

    private int getOwnerInfoSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKOWNER_FONT_SIZE, 21);
    }

    private void updateClockColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CLOCK_COLOR, 0xFFFFFFFF);

        if (mClockView != null) {
            mClockView.setTextColor(color);
        }
    }

    private void updateClockDateColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CLOCK_DATE_COLOR, 0xFFFFFFFF);

        if (mKeyguardSlice != null) {
            mKeyguardSlice.setTextColor(color);
       	}
    }

    private void updateOwnerInfoColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_OWNER_INFO_COLOR, 0xFFFFFFFF);

        if (mOwnerInfo != null) {
            mOwnerInfo.setTextColor(color);
        }
    }

    private void refreshFormat() {
        Patterns.update(mContext);
        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    public int getLogoutButtonHeight() {
        if (mLogoutView == null) {
            return 0;
        }
        return mLogoutView.getVisibility() == VISIBLE ? mLogoutView.getHeight() : 0;
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    private void updateLogoutView() {
        if (mLogoutView == null) {
            return;
        }
        mLogoutView.setVisibility(shouldShowLogout() ? VISIBLE : GONE);
        // Logout button will stay in language of user 0 if we don't set that manually.
        mLogoutView.setText(mContext.getResources().getString(
                com.android.internal.R.string.global_action_logout));
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String info = mLockPatternUtils.getDeviceOwnerInfo();
        if (info == null) {

            final ContentResolver resolver = mContext.getContentResolver();
            boolean mClockSelection = Settings.System.getIntForUser(resolver,
                    Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT) == 15;

            // If text style clock, align the textView to start else keep it center.
            if (mClockSelection) {
                mOwnerInfo.setPaddingRelative((int) mContext.getResources()
                    .getDimension(R.dimen.custom_clock_left_padding) + 8, 0, 0, 0);
                mOwnerInfo.setGravity(Gravity.START);
            } else {
                mOwnerInfo.setPaddingRelative(0, 0, 0, 0);
                mOwnerInfo.setGravity(Gravity.CENTER);
            }

            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        mOwnerInfo.setText(info);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onLocaleListChanged() {
        refreshFormat();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 29;

        if (lockClockFont == 0) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 2) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 3) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 5) {
                mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 6) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockClockFont == 7) {
                mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 8) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 9) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 10) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 11) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 12) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockClockFont == 14) {
                mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
                mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockClockFont == 16) {
                mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
                mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (lockClockFont == 18) {
                mClockView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (lockClockFont == 19) {
                mClockView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (lockClockFont == 20) {
                mClockView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (lockClockFont == 21) {
                mClockView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (lockClockFont == 22) {
                mClockView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (lockClockFont == 23) {
                mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (lockClockFont == 24) {
                mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 25) {
            mClockView.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 26) {
            mClockView.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 27) {
            mClockView.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 28) {
            mClockView.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 29) {
            mClockView.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 30) {
            mClockView.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
	}
	if (lockClockFont == 31) {
            mClockView.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 32) {
            mClockView.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 33) {
            mClockView.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 34) {
            mClockView.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 35) {
            mClockView.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
	}
    }

    private void refreshLockDateFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockDateFont = isPrimary ? getLockDateFont() : 29;
        if (lockDateFont == 0) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockDateFont == 1) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockDateFont == 2) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockDateFont == 3) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 4) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockDateFont == 5) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockDateFont == 6) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockDateFont == 7) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockDateFont == 8) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockDateFont == 9) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockDateFont == 10) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockDateFont == 11) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 12) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockDateFont == 13) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockDateFont == 14) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockDateFont == 15) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockDateFont == 16) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockDateFont == 17) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (lockDateFont == 18) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (lockDateFont == 19) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (lockDateFont == 20) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (lockDateFont == 21) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (lockDateFont == 22) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (lockDateFont == 23) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (lockDateFont == 24) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 25) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 26) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 27) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 28) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 29) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 30) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
    	}
        if (lockDateFont == 31) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
        }
	if (lockDateFont == 32) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
        }
	if (lockDateFont == 33) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 34) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
        }
	if (lockDateFont == 35) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
        }
	updateSettings();
    }

    public void refreshclocksize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockSize = isPrimary ? getLockClockSize() : 78;

        if (lockClockSize == 50) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_50));
        } else if (lockClockSize == 51) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51));
        } else if (lockClockSize == 52) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52));
        } else if (lockClockSize == 53) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53));
        } else if (lockClockSize == 54) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54));
        } else if (lockClockSize == 55) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55));
        } else if (lockClockSize == 56) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56));
        } else if (lockClockSize == 57) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57));
        } else if (lockClockSize == 58) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58));
        } else if (lockClockSize == 59) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59));
        } else if (lockClockSize == 60) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60));
        } else if (lockClockSize == 61) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61));
        } else if (lockClockSize == 62) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62));
        } else if (lockClockSize == 63) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63));
        } else if (lockClockSize == 64) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64));
        } else if (lockClockSize == 65) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
        } else if (lockClockSize == 66) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
        } else if (lockClockSize == 66) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
        } else if (lockClockSize == 68) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
        } else if (lockClockSize == 69) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
        } else if (lockClockSize == 70) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
        } else if (lockClockSize == 71) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
        } else if (lockClockSize == 72) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
        } else if (lockClockSize == 73) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
        } else if (lockClockSize == 74) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
        } else if (lockClockSize == 75) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
        } else if (lockClockSize == 76) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
        } else if (lockClockSize == 77) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
        } else if (lockClockSize == 78) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78));
        } else if (lockClockSize == 79) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
        } else if (lockClockSize == 80) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
        } else if (lockClockSize == 81) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
        } else if (lockClockSize == 82) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
        } else if (lockClockSize == 83) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
        } else if (lockClockSize == 84) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
        }  else if (lockClockSize == 85) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
        } else if (lockClockSize == 86) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
        } else if (lockClockSize == 87) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
         } else if (lockClockSize == 88) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
        } else if (lockClockSize == 89) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
         } else if (lockClockSize == 90) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
        } else if (lockClockSize == 91) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
        } else if (lockClockSize == 92) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
        }  else if (lockClockSize == 93) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
        } else if (lockClockSize == 94) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
        } else if (lockClockSize == 95) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
         } else if (lockClockSize == 96) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
        } else if (lockClockSize == 97) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
         } else if (lockClockSize == 98) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
        } else if (lockClockSize == 99) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
         } else if (lockClockSize == 100) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
        } else if (lockClockSize == 101) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
        } else if (lockClockSize == 102) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_102));
        }  else if (lockClockSize == 103) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_103));
        } else if (lockClockSize == 104) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_104));
        } else if (lockClockSize == 105) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_105));
         } else if (lockClockSize == 106) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_106));
        } else if (lockClockSize == 107) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_107));
         } else if (lockClockSize == 108) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_108));
         }
	updateSettings();
    }

    public void refreshdatesize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockDateSize = isPrimary ? getLockDateSize() : 14;

        if (lockDateSize == 0) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1));
        } else if (lockDateSize == 1) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1));
        } else if (lockDateSize == 2) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_2));
        } else if (lockDateSize == 3) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_3));
        } else if (lockDateSize == 4) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_4));
        } else if (lockDateSize == 5) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_5));
        } else if (lockDateSize == 6) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_6));
        } else if (lockDateSize == 7) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_7));
        } else if (lockDateSize == 8) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_8));
        } else if (lockDateSize == 9) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9));
        } else if (lockDateSize == 10) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (lockDateSize == 11) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (lockDateSize == 12) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (lockDateSize == 13) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (lockDateSize == 14) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
        }  else if (lockDateSize == 15) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (lockDateSize == 16) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (lockDateSize == 17) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (lockDateSize == 18) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (lockDateSize == 19) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (lockDateSize == 20) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (lockDateSize == 21) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (lockDateSize == 22) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (lockDateSize == 23) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (lockDateSize == 24) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (lockDateSize == 25) {
        mKeyguardSlice.setViewsTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
	}
    }

    private void updateVisibilities() {
        mClockView.setVisibility(View.GONE);
        mCustomClockView.setVisibility(View.GONE);
        mDuClockView.setVisibility(View.GONE);
        mCustomNumClockView.setVisibility(View.GONE);
        mTextClock.setVisibility(View.GONE);
        mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
        final int mClockVisibility = mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE;
        switch (mClockSelection) {
            case 2: // custom analog
                mCustomClockView.setVisibility(mClockVisibility);
                break;
            case 3: // DU analog
                mSpideyClockView.setVisibility(mClockVisibility);
                break;
            case 8: // custom analog with numbers
                mCustomNumClockView.setVisibility(mClockVisibility);
                break;
            case 12: // custom text clock
                mTextClock.setVisibility(mClockVisibility);
                break;
            case 0: // default digital
            case 1: // digital (bold)
            case 4: // sammy
            case 5: // sammy (bold)
            case 6: // sammy accent
            case 7: // sammy accent
            case 9: // digital (accent full)
			case 10: // digital (accent hour)
			case 11: // digital (accent minutes)
            default:
                mClockView.setVisibility(mClockVisibility);
                break;
	    }
    }

    private void updateDateStyles() {
        switch (mDateSelection) {
            case 0: // default
            default:
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackgroundResource(0);
                mKeyguardSlice.setViewsTypeface(Typeface.DEFAULT);
                mDateVerPadding = 0;
                mDateHorPadding = 0;
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 1: // semi-transparent box
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                mKeyguardSlice.setViewsTypeface(Typeface.DEFAULT_BOLD);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 2: // semi-transparent box (round)
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_border));
                mKeyguardSlice.setViewsTypeface(Typeface.DEFAULT_BOLD);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 3: // Q-Now Playing background
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.ambient_indication_pill_background));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 4: // accent box
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 5: // accent box but just the day
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 6: // accent box transparent
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 7: // accent box transparent but just the day
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 8: // gradient box
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 9: // Dark Accent border
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 10: // Dark Gradient border
                mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
        }
    }

    public void refreshOwnerInfoSize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int ownerInfoSize = isPrimary ? getOwnerInfoSize() : 21;

        if (ownerInfoSize == 0) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1));
        } else if (ownerInfoSize == 1) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1));
        } else if (ownerInfoSize == 2) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_2));
        } else if (ownerInfoSize == 3) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_3));
        } else if (ownerInfoSize == 4) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_4));
        } else if (ownerInfoSize == 5) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_5));
        } else if (ownerInfoSize == 6) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_6));
        } else if (ownerInfoSize == 7) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_7));
        } else if (ownerInfoSize == 8) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_8));
        } else if (ownerInfoSize == 9) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9));
        } else if (ownerInfoSize == 10) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (ownerInfoSize == 11) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (ownerInfoSize == 12) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (ownerInfoSize == 13) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (ownerInfoSize == 14) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
        }  else if (ownerInfoSize == 15) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (ownerInfoSize == 16) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (ownerInfoSize == 17) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (ownerInfoSize == 18) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (ownerInfoSize == 19) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (ownerInfoSize == 20) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (ownerInfoSize == 21) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (ownerInfoSize == 22) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (ownerInfoSize == 23) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (ownerInfoSize == 24) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (ownerInfoSize == 25) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
        }
    }

    private void refreshOwnerInfoFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int ownerinfoFont = isPrimary ? getOwnerInfoFont() : 0;

        if (ownerinfoFont == 0) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (ownerinfoFont == 1) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (ownerinfoFont == 2) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (ownerinfoFont == 3) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (ownerinfoFont == 4) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (ownerinfoFont == 5) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (ownerinfoFont == 6) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (ownerinfoFont == 7) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (ownerinfoFont == 8) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (ownerinfoFont == 9) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (ownerinfoFont == 10) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (ownerinfoFont == 11) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (ownerinfoFont == 12) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (ownerinfoFont == 13) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (ownerinfoFont == 14) {
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (ownerinfoFont == 15) {
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (ownerinfoFont == 16) {
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (ownerinfoFont == 17) {
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (ownerinfoFont == 18) {
                mOwnerInfo.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (ownerinfoFont == 19) {
                mOwnerInfo.setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (ownerinfoFont == 20) {
                mOwnerInfo.setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (ownerinfoFont == 21) {
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (ownerinfoFont == 22) {
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (ownerinfoFont == 23) {
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (ownerinfoFont == 24) {
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (ownerinfoFont == 25) {
            mOwnerInfo.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 26) {
            mOwnerInfo.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 27) {
            mOwnerInfo.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 28) {
            mOwnerInfo.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 29) {
            mOwnerInfo.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 30) {
            mOwnerInfo.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 31) {
            mOwnerInfo.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 32) {
            mOwnerInfo.setTypeface(Typeface.create("mexcellen-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 33) {
            mOwnerInfo.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 34) {
            mOwnerInfo.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
        }
        if (ownerinfoFont == 35) {
            mOwnerInfo.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
        }
    }

    private void prepareSmallView(boolean small) {
        if (mWasLatestViewSmall == small) return;
        mWasLatestViewSmall = small;
        if (small) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                    mKeyguardSlice.getLayoutParams();
            params.addRule(RelativeLayout.BELOW, R.id.clock_view);
            mClockView.setSingleLine(true);
            mClockView.setGravity(Gravity.CENTER);
            mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                    View.GONE) : View.VISIBLE);
            mCustomClockView.setVisibility(View.GONE);
	    mTextClock.setVisibility(View.GONE);
            mKeyguardSlice.setViewBackgroundResource(0);
        } else {
            setStyle();
            refreshTime();
        }
    }

    public void updateAll() {
        updateSettings();
        mKeyguardSlice.updateSettings();
        mKeyguardSlice.refresh();
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        updateDark();
    }

    private void updateDark() {
        boolean dark = mDarkAmount == 1;
        if (mLogoutView != null) {
            mLogoutView.setAlpha(dark ? 0 : 1);
        }

        if (mOwnerInfo != null) {
            boolean hasText = !TextUtils.isEmpty(mOwnerInfo.getText());
            mOwnerInfo.setVisibility(hasText ? VISIBLE : GONE);
            layoutOwnerInfo();
        }

        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        updateDozeVisibleViews();
        updateVisibilities();
    }

    private void layoutOwnerInfo() {
        if (mOwnerInfo != null && mOwnerInfo.getVisibility() != GONE) {
            // Animate owner info during wake-up transition
            mOwnerInfo.setAlpha(1f - mDarkAmount);

            float ratio = mDarkAmount;
            // Calculate how much of it we should crop in order to have a smooth transition
            int collapsed = mOwnerInfo.getTop() - mOwnerInfo.getPaddingTop();
            int expanded = mOwnerInfo.getBottom() + mOwnerInfo.getPaddingBottom();
            int toRemove = (int) ((expanded - collapsed) * ratio);
            setBottom(getMeasuredHeight() - toRemove);
        }
    }

    public void setPulsing(boolean pulsing, boolean animate) {
        if (mPulsing == pulsing) {
            return;
        }
        if (mPulsing) {
            mWasPulsing = true;
        }
        mPulsing = pulsing;
        // Animation can look really weird when the slice has a header, let's hide the views
        // immediately instead of fading them away.
        if (mKeyguardSlice.hasHeader()) {
            animate = false;
        }
        mKeyguardSlice.setPulsing(pulsing, animate);
        if (mWeatherView != null) {
            mWeatherView.setVisibility((mShowWeather && !mPulsing) ? View.VISIBLE : View.GONE);
        }
        updateDozeVisibleViews();
    }

    public void setCleanLayout(int reason) {
        mForcedMediaDoze =
                reason == DozeLog.PULSE_REASON_FORCED_MEDIA_NOTIFICATION;
        updateDozeVisibleViews();
    }

    private void updateDozeVisibleViews() {
        for (View child : mVisibleInDoze) {
            if (!mForcedMediaDoze) {
                child.setAlpha(mDarkAmount == 1 && mPulsing ? 0.8f : 1);
            } else {
                child.setAlpha(mDarkAmount == 1 ? 0 : 1);
            }
        }
    }

    private boolean shouldShowLogout() {
        return KeyguardUpdateMonitor.getInstance(mContext).isLogoutEnabled()
                && KeyguardUpdateMonitor.getCurrentUser() != UserHandle.USER_SYSTEM;
    }

    private void onLogoutClicked(View view) {
        int currentUserId = KeyguardUpdateMonitor.getCurrentUser();
        try {
            mIActivityManager.switchUser(UserHandle.USER_SYSTEM);
            mIActivityManager.stopUser(currentUserId, true /*force*/, null);
        } catch (RemoteException re) {
            Log.e(TAG, "Failed to logout user", re);
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        mShowWeather = Settings.System.getIntForUser(resolver,
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        if (mWeatherView != null) {
            if (mShowWeather) {
                mWeatherView.setVisibility(View.VISIBLE);
                mWeatherView.enableUpdates();
            }
            if (!mShowWeather) {
                mWeatherView.setVisibility(View.GONE);
                mWeatherView.disableUpdates();
            }
        }

	RelativeLayout.LayoutParams textClockParams = new RelativeLayout.LayoutParams(
			RelativeLayout.LayoutParams.WRAP_CONTENT,
			RelativeLayout.LayoutParams.WRAP_CONTENT);

	int leftPadding = (int) getResources().getDimension(R.dimen.custom_clock_left_padding);
        int topPadding = (int) getResources().getDimension(R.dimen.custom_clock_top_margin);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mWeatherView.getLayoutParams();

        mShowClock = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK, 1, UserHandle.USER_CURRENT) == 1;
        mShowInfo = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_INFO, 1, UserHandle.USER_CURRENT) == 1;
        mClockSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);
        mIsCenterAligned = Settings.System.getIntForUser(resolver,
                Settings.System.CENTER_TEXT_CLOCK, 0, UserHandle.USER_CURRENT) == 1;
        mIsLeftAligned = Settings.System.getIntForUser(resolver,
                Settings.System.LEFT_ALIGN_VIEW, 0, UserHandle.USER_CURRENT) == 1;
        mDateSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_DATE_SELECTION, 0, UserHandle.USER_CURRENT);

        if (mTextClock != null && mIsCenterAligned) {
	    mTextClock.setGravity(Gravity.CENTER);
	    textClockParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
	    mTextClock.setLayoutParams(textClockParams);
	    mTextClock.setPaddingRelative(0, topPadding, 0, 0);
	} else {
	    mTextClock.setGravity(Gravity.START);
	    textClockParams.removeRule(RelativeLayout.CENTER_HORIZONTAL);
	    mTextClock.setLayoutParams(textClockParams);
	    mTextClock.setPaddingRelative(leftPadding, topPadding, 0, 0);
	}

        if (mWeatherView != null && mKeyguardSlice != null && mIsLeftAligned) {
            lp.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
	    lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            mWeatherView.setLayoutParams(lp);
	    mWeatherView.setPaddingRelative(leftPadding, 0, 0, 0);
	} else {
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
     	    lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
	    mWeatherView.setLayoutParams(lp);
	    mWeatherView.setPaddingRelative(16, 0, 16, 0);
        }
        setStyle();
    }

    private void setStyle() {
    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
        mKeyguardSlice.getLayoutParams();
        switch (mClockSelection) {
            case 2: // custom analog
                params.addRule(RelativeLayout.BELOW, R.id.custom_clock_view);
                break;
            case 4: // sammy
            case 5: // sammy (bold)
            case 6: // sammy accent
            case 7: // sammy accent
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                mClockView.setLineSpacing(0, 0.8f);
                break;
            case 3: // DU analog
                params.addRule(RelativeLayout.BELOW, R.id.du_clock_view);
                break;
            case 8: // custom analog with numbers
                params.addRule(RelativeLayout.BELOW, R.id.custom_num_clock_view);
                break;
            case 12: // custom text clock
                params.addRule(RelativeLayout.BELOW, R.id.custom_textclock_view);
                break;
            case 0: // default digital
            case 1: // digital (bold)
            case 9: // digital (accent full)
			case 10: // digital (accent hour)
			case 11: // digital (accent minutes)
            default:
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
        }
        mKeyguardSlice.setLayoutParams(params);

        updateVisibilities();
        updateDozeVisibleViews();
    }

    private class ClipChildrenAnimationListener extends AnimatorListenerAdapter implements
            ViewClippingUtil.ClippingParameters {

        ClipChildrenAnimationListener() {
            ViewClippingUtil.setClippingDeactivated(mClockView, true /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            ViewClippingUtil.setClippingDeactivated(mClockView, false /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public boolean shouldFinish(View view) {
            return view == getParent();
        }
    }
}

