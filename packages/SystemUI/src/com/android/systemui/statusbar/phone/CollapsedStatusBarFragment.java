/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import static com.android.systemui.statusbar.phone.StatusBar.reinflateSignalCluster;

import android.annotation.Nullable;
import android.app.Fragment;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageSwitcher;
import android.widget.LinearLayout;
import android.util.Slog;

import com.android.internal.utils.du.UserContentObserver;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.omni.NetworkTraffic;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

import android.widget.ImageView;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;


/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";
    private PhoneStatusBarView mStatusBar;
    private KeyguardMonitor mKeyguardMonitor;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private View mNotificationIconAreaInner;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private SignalClusterView mSignalClusterView;

    // Statusbar Weather Image
    private View mWeatherImageView;
    private View mWeatherTextView;
    private int mShowWeather;
    private View mBatteryBar;

    // Custom Carrier
    private View mCustomCarrierLabel;
    private int mShowCarrierLabel;
    private LinearLayout mCenterClockLayout;
    private View mClock;
    private View mCenterClock;
    private View mLeftClock;
    private int mClockStyle;
    private boolean mShowNetworkTraffic;
    private NetworkTraffic mNetworkTraffic;
    private final Handler mHandler = new Handler();
    private int mTickerEnabled;
    private TickerObserver mTickerObserver;
    private View mTickerViewFromStub;

    private ImageView mXtendedLogo;
    private int mLogoStyle;
    private boolean mShowLogo;
    private int mLogoColor;
	    
    private class XtendedSettingsObserver extends ContentObserver {
        XtendedSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_CARRIER),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_STYLE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SECONDS),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_STYLE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_FORMAT),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_POSITION),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NETWORK_TRAFFIC_ENABLE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NETWORK_TRAFFIC_STATE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.STATUS_BAR_BATTERY_STYLE),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SHOW_BATTERY_PERCENT),
		    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP),
	            false, this, UserHandle.USER_ALL);
	    getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO),
                    false, this, UserHandle.USER_ALL);
	    getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_STYLE),
		    false, this, UserHandle.USER_ALL);
	    getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_COLOR),
		    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if ((uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO_STYLE))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO_COLOR)))){
                 updateLogoSettings(true);
	    }
            updateSettings(true);
            mNetworkTraffic.updateSettings();
        }

        protected void update() {
            updateSettings(true);
        }
    }

    private XtendedSettingsObserver mXtendedSettingsObserver = new XtendedSettingsObserver(mHandler);
    private ContentResolver mContentResolver;

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mStatusBarComponent.recomputeDisableFlags(true /* animate */);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContext().getContentResolver();
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarComponent = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
        mXtendedSettingsObserver = new XtendedSettingsObserver(mHandler);
        mTickerObserver = new TickerObserver(new Handler());
    }

    class TickerObserver extends UserContentObserver {

        TickerObserver(Handler handler) {
            super(handler);
        }

        protected void unobserve() {
            super.unobserve();
            getContext().getContentResolver().unregisterContentObserver(this);
        }

        protected void observe() {
            super.observe();
            getContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_SHOW_TICKER), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        protected void update() {
            mTickerEnabled = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.STATUS_BAR_SHOW_TICKER, 1,
                    UserHandle.USER_CURRENT);
            initTickerView();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.go(savedInstanceState.getInt(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons));
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mBatteryBar = mStatusBar.findViewById(R.id.battery_bar);
        mSignalClusterView = mStatusBar.findViewById(R.id.signal_cluster);
        mCenterClockLayout = (LinearLayout) mStatusBar.findViewById(R.id.center_clock_layout);
        mClock = mStatusBar.findViewById(R.id.clock);
        mLeftClock = mStatusBar.findViewById(R.id.left_clock);
        mCenterClock = mStatusBar.findViewById(R.id.center_clock);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mSignalClusterView);
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);
        mNetworkTraffic = (NetworkTraffic) mStatusBar.findViewById(R.id.networkTraffic);
	mWeatherTextView = mStatusBar.findViewById(R.id.weather_temp);
        mWeatherImageView = mStatusBar.findViewById(R.id.weather_image);
	mXtendedLogo = mStatusBar.findViewById(R.id.status_bar_logo);
        mXtendedSettingsObserver.observe();
        updateSettings(false);
        mNetworkTraffic.updateSettings();
	updateLogoSettings(false);
        // Default to showing until we know otherwise.
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();

        mTickerObserver.observe();
        mTickerObserver.update();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_PANEL_STATE, mStatusBar.getState());
    }

    @Override
    public void onResume() {
        super.onResume();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).addCallbacks(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).removeCallbacks(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mSignalClusterView);
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
        mTickerObserver.unobserve();
    }

    public void initNotificationIconArea(NotificationIconAreaController
            notificationIconAreaController) {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                notificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);
        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
            } else {
                showSystemIconArea(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
			 hideCarrierName(animate);
            } else {
                showNotificationIconArea(animate);
			 showCarrierName(animate);
            }
        }
    }

    protected int adjustDisableFlags(int state) {
        if (!mStatusBarComponent.isLaunchTransitionFadingAway()
                && !mKeyguardMonitor.isKeyguardFadingAway()
                && shouldHideNotificationIcons()) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
        }
        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }
        return state;
    }

    private boolean shouldHideNotificationIcons() {
        if (!mStatusBar.isClosed() && mStatusBarComponent.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        if (mStatusBarComponent.hideStatusBarIconsForBouncer()) {
            return true;
        }
        return false;
    }

    public void hideSystemIconArea(boolean animate) {
	animateHide(mBatteryBar, animate, false);
	if (((Clock)mCenterClock).isEnabled()) {
        animateHide(mCenterClockLayout, animate, true);
	}
	animateHide(mSystemIconArea, animate, true);
    }

    public void showSystemIconArea(boolean animate) {
	animateShow(mBatteryBar, animate);
	if (((Clock)mCenterClock).isEnabled()) {
        animateShow(mCenterClockLayout, animate);
	}
	animateShow(mSystemIconArea, animate);
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate, true);
        if (((Clock)mCenterClock).isEnabled()) {
        animateHide(mCenterClockLayout, animate,false);
        }

        if (((Clock)mLeftClock).isEnabled()) {
            animateHide(mLeftClock, animate, true);
        }

        if (mShowNetworkTraffic) {
            animateHide(mNetworkTraffic, animate, true);
	}

        if (mShowLogo) {
            animateHide(mXtendedLogo, animate, true);
        }
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);

	if (mShowLogo) {
	    animateShow(mXtendedLogo, animate);
	}

	if (((Clock)mCenterClock).isEnabled()) {
            animateShow(mCenterClockLayout, animate);
	}

        if (((Clock)mLeftClock).isEnabled()) {
            animateShow(mLeftClock, animate);
        }
    }

    public void hideCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            animateHide(mCustomCarrierLabel, animate, true);
        }
    }

    public void showCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            setCarrierLabel(animate);
        }
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate, final boolean invisible)  {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(invisible ? View.INVISIBLE : View.GONE);;
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(invisible ? View.INVISIBLE : View.GONE));
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(50)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardMonitor.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardMonitor.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardMonitor.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

	public void updateSettings(boolean animate) {

 	     mShowCarrierLabel = Settings.System.getIntForUser(
                 getContext().getContentResolver(), Settings.System.STATUS_BAR_SHOW_CARRIER, 1,
                 UserHandle.USER_CURRENT);	
	     setCarrierLabel(animate);

             mShowWeather = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP, 0,
                UserHandle.USER_CURRENT);

             if (mStatusBar == null) return;
 
             if (getContext() == null) {
                 return;
	     }
			((Clock)mClock).updateSettings();
            ((Clock)mCenterClock).updateSettings();
            ((Clock)mLeftClock).updateSettings();
			mStatusBarComponent.updateQsbhClock();	
            mStatusBarComponent.updateBatterySettings();
            mNetworkTraffic.updateSettings();
      }

    public void updateLogoSettings(boolean animate) {
        Drawable logo = null;

        if (mStatusBar == null) return;

        if (getContext() == null) {
            return;
        }

        mShowLogo = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_LOGO, 0,
                UserHandle.USER_CURRENT) == 1;
        mLogoColor = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_LOGO_COLOR, 0xff009688,
                UserHandle.USER_CURRENT);
        mLogoStyle = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);

        switch(mLogoStyle) {
                // Xtnd Old
            case 1:
                logo = getContext().getDrawable(R.drawable.ic_xtnd_logo);
                break;
                // XTND Short
            case 2:
                logo = getContext().getDrawable(R.drawable.ic_xtnd_short);
                break;
                // GZR Skull
            case 3:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_gzr_skull_logo);
                break;
                // GZR Circle
            case 4:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_gzr_circle_logo);
                break;
                // Batman
            case 5:
                logo = getContext().getDrawable(R.drawable.ic_batman_logo);
                break;
                // Deadpool
            case 6:
                logo = getContext().getDrawable(R.drawable.ic_deadpool_logo);
                break;
                // Superman
            case 7:
                logo = getContext().getDrawable(R.drawable.ic_superman_logo);
                break;
                // Ironman
            case 8:
                logo = getContext().getDrawable(R.drawable.ic_ironman_logo);
                break;
                // Spiderman
            case 9:
                logo = getContext().getDrawable(R.drawable.ic_spiderman_logo);
                break;
                // Decepticons
            case 10:
                logo = getContext().getDrawable(R.drawable.ic_decpeticons_logo);
                break;
                // Minions
            case 11:
                logo = getContext().getDrawable(R.drawable.ic_minions_logo);
                break;
            case 12:
                logo = getContext().getDrawable(R.drawable.ic_android_logo);
                break;
                // Shit
            case 13:
                logo = getContext().getDrawable(R.drawable.ic_apple_logo);
                break;
                // Shitty Logo
            case 14:
                logo = getContext().getDrawable(R.drawable.ic_ios_logo);
                break;
                // Others
            case 15:
                logo = getContext().getDrawable(R.drawable.ic_blackberry);
                break;
            case 16:
                logo = getContext().getDrawable(R.drawable.ic_cake);
                break;
            case 17:
                logo = getContext().getDrawable(R.drawable.ic_blogger);
                break;
            case 18:
                logo = getContext().getDrawable(R.drawable.ic_biohazard);
                break;
            case 19:
                logo = getContext().getDrawable(R.drawable.ic_linux);
                break;
            case 20:
                logo = getContext().getDrawable(R.drawable.ic_yin_yang);
                break;
            case 21:
                logo = getContext().getDrawable(R.drawable.ic_windows);
                break;
            case 22:
                logo = getContext().getDrawable(R.drawable.ic_robot);
                break;
            case 23:
                logo = getContext().getDrawable(R.drawable.ic_ninja);
                break;
            case 24:
                logo = getContext().getDrawable(R.drawable.ic_heart);
                break;
            case 25:
                logo = getContext().getDrawable(R.drawable.ic_ghost);
                break;
            case 26:
                logo = getContext().getDrawable(R.drawable.ic_google);
                break;
            case 27:
                logo = getContext().getDrawable(R.drawable.ic_human_male);
                break;
            case 28:
                logo = getContext().getDrawable(R.drawable.ic_human_female);
                break;
            case 29:
                logo = getContext().getDrawable(R.drawable.ic_human_male_female);
                break;
            case 30:
                logo = getContext().getDrawable(R.drawable.ic_gender_male);
                break;
            case 31:
                logo = getContext().getDrawable(R.drawable.ic_gender_female);
                break;
            case 32:
                logo = getContext().getDrawable(R.drawable.ic_gender_male_female);
                break;
            case 33:
                logo = getContext().getDrawable(R.drawable.ic_guitar_electric);
                break;
                // Default (Xtended Main)
            case 0:
            default:
                logo = getContext().getDrawable(R.drawable.status_bar_logo);
                break;
        }

        if (mXtendedLogo != null) {
            if (logo == null) {
                // Something wrong. Do not show anything
                mXtendedLogo.setImageDrawable(logo);
                return;
            }

            mXtendedLogo.setImageDrawable(logo);
            mXtendedLogo.setColorFilter(mLogoColor, PorterDuff.Mode.MULTIPLY);
        }

        if (mNotificationIconAreaInner != null) {
            if (mShowLogo) {
                if (mNotificationIconAreaInner.getVisibility() == View.VISIBLE) {
                    animateShow(mXtendedLogo, animate);
                }
            } else {
                animateHide(mXtendedLogo, animate, false);
            }
        }
    }

    private void setCarrierLabel(boolean animate) {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            animateShow(mCustomCarrierLabel, animate);
        } else {
            animateHide(mCustomCarrierLabel, animate, false);
        }
    }

    private void updateClockStyle(boolean animate) {
        if (mClockStyle == 0 || mClockStyle == 1) {
            animateHide(mLeftClock, animate, false);
        } else {
            animateShow(mLeftClock, animate);
	}
    }

    private void initTickerView() {
        if (mTickerEnabled != 0) {
            View tickerStub = mStatusBar.findViewById(R.id.ticker_stub);
            if (mTickerViewFromStub == null && tickerStub != null) {
                mTickerViewFromStub = ((ViewStub) tickerStub).inflate();
            }
            TickerView tickerView = (TickerView) mStatusBar.findViewById(R.id.tickerText);
            ImageSwitcher tickerIcon = (ImageSwitcher) mStatusBar.findViewById(R.id.tickerIcon);
            mStatusBarComponent.createTicker(
                    mTickerEnabled, getContext(), mStatusBar, tickerView, tickerIcon, mTickerViewFromStub);
        } else {
            mStatusBarComponent.disableTicker();
        }
    }
}

