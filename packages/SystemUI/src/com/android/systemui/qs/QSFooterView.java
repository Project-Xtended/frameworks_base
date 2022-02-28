/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.Utils;
import com.android.settingslib.drawable.UserIconDrawable;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.statusbar.phone.MultiUserSwitch;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.tuner.TunerService;

/** */
public class QSFooterView extends FrameLayout implements TunerService.Tunable {

    public static final String QS_FOOTER_SHOW_SETTINGS =
            "system:" + Settings.System.QS_FOOTER_SHOW_SETTINGS;
    public static final String QS_FOOTER_SHOW_SERVICES =
            "system:" + Settings.System.QS_FOOTER_SHOW_SERVICES;
    public static final String QS_FOOTER_SHOW_EDIT =
            "system:" + Settings.System.QS_FOOTER_SHOW_EDIT;
    public static final String QS_FOOTER_SHOW_USER =
            "system:" + Settings.System.QS_FOOTER_SHOW_USER;

    private SettingsButton mSettingsButton;
    protected View mSettingsContainer;
    private PageIndicator mPageIndicator;
    private View mRunningServicesButton;

    private boolean mQsDisabled;

    private boolean mExpanded;

    private boolean mListening;

    protected MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;

    protected TouchAnimator mFooterAnimator;
    private float mExpansionAmount;

    protected View mEdit;
    private TouchAnimator mSettingsCogAnimator;

    private View mActionsContainer;
    private View mTunerIcon;
    private int mTunerIconTranslation;

    private OnClickListener mExpandClickListener;

    private boolean mMultiUserEnabled;

    private boolean mShowSettingsIcon;
    private boolean mShowServicesIcon;
    private boolean mShowEditIcon;
    private boolean mShowUserIcon;

    private final ContentObserver mSettingsObserver = new ContentObserver(
            new Handler(mContext.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            setBuildText();
        }
    };

    public QSFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEdit = requireViewById(android.R.id.edit);

        mPageIndicator = findViewById(R.id.footer_page_indicator);

        mRunningServicesButton = findViewById(R.id.running_services_button);
        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);

        mMultiUserSwitch = findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = mMultiUserSwitch.findViewById(R.id.multi_user_avatar);

        mActionsContainer = requireViewById(R.id.qs_footer_actions_container);
        mTunerIcon = requireViewById(R.id.tuner_icon);

        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        if (mSettingsButton.getBackground() instanceof RippleDrawable) {
            ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);
        }
        if (mRunningServicesButton.getBackground() instanceof RippleDrawable) {
            ((RippleDrawable) mRunningServicesButton.getBackground()).setForceSoftware(true);
        }
        updateResources();

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBuildText();
    }

    private void setBuildText() {
        TextView v = findViewById(R.id.build);
        if (v == null) return;
        boolean isShow = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.OMNI_FOOTER_TEXT_SHOW, 0,
                        UserHandle.USER_CURRENT) == 1;
        String text = Settings.System.getStringForUser(mContext.getContentResolver(),
                        Settings.System.X_FOOTER_TEXT_STRING,
                        UserHandle.USER_CURRENT);
        if (isShow) {
            if (text == null || text == "") {
                v.setText("Xtended");
                v.setVisibility(View.VISIBLE);
            } else {
                v.setText(text);
                v.setVisibility(View.VISIBLE);
            }
        } else {
            v.setVisibility(View.INVISIBLE);
        }
    }

    void updateAnimator(int width, int numTiles) {
        int size = mContext.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size)
                - mContext.getResources().getDimensionPixelSize(R.dimen.qs_tile_padding);
        int remaining = (width - numTiles * size) / (numTiles - 1);
        int defSpace = mContext.getResources().getDimensionPixelOffset(R.dimen.default_gear_space);

        mSettingsCogAnimator = new Builder()
                .addFloat(mSettingsButton, "translationX",
                        isLayoutRtl() ? (remaining - defSpace) : -(remaining - defSpace), 0)
                .addFloat(mSettingsButton, "rotation", -120, 0)
                .build();

        setExpansion(mExpansionAmount);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void updateResources() {
        updateFooterAnimator();
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.qs_footers_margin_bottom);
        setLayoutParams(lp);
        mTunerIconTranslation = mContext.getResources()
                .getDimensionPixelOffset(R.dimen.qs_footer_tuner_icon_translation);
        mTunerIcon.setTranslationX(isLayoutRtl() ? -mTunerIconTranslation : mTunerIconTranslation);
    }

    private void updateFooterAnimator() {
        mFooterAnimator = createFooterAnimator();
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mActionsContainer, "alpha", 0, 1)
                .addFloat(mPageIndicator, "alpha", 0, 1)
                .setStartDelay(0.9f);
        return builder.build();
    }

    /** */
    public void setKeyguardShowing() {
        setExpansion(mExpansionAmount);
    }

    public void setExpandClickListener(OnClickListener onClickListener) {
        mExpandClickListener = onClickListener;
    }

    void setExpanded(boolean expanded, boolean isTunerEnabled, boolean multiUserEnabled) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mMultiUserEnabled = multiUserEnabled;
        updateEverything(false, mMultiUserEnabled);
    }

    /** */
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        if (mSettingsCogAnimator != null) mSettingsCogAnimator.setPosition(headerExpansionFraction);

        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.OMNI_FOOTER_TEXT_SHOW), false,
                mSettingsObserver, UserHandle.USER_ALL);

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.X_FOOTER_TEXT_STRING), false,
                mSettingsObserver, UserHandle.USER_ALL);

        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, QS_FOOTER_SHOW_SETTINGS);
        tunerService.addTunable(this, QS_FOOTER_SHOW_SERVICES);
        tunerService.addTunable(this, QS_FOOTER_SHOW_EDIT);
        tunerService.addTunable(this, QS_FOOTER_SHOW_USER);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        Dependency.get(TunerService.class).removeTunable(this);
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    /** */
    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_EXPAND) {
            if (mExpandClickListener != null) {
                mExpandClickListener.onClick(null);
                return true;
            }
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case QS_FOOTER_SHOW_SETTINGS:
                mShowSettingsIcon =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case QS_FOOTER_SHOW_SERVICES:
                mShowServicesIcon =
                        TunerService.parseIntegerSwitch(newValue, false);
                break;
            case QS_FOOTER_SHOW_EDIT:
                mShowEditIcon =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case QS_FOOTER_SHOW_USER:
                mShowUserIcon =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            default:
                break;
        }
        updateEverything(false, mMultiUserEnabled);
    }

    void disable(int state2, boolean isTunerEnabled, boolean multiUserEnabled) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mMultiUserEnabled = multiUserEnabled;
        updateEverything(false, mMultiUserEnabled);
    }

    void updateEverything(boolean isTunerEnabled, boolean multiUserEnabled) {
        post(() -> {
            updateVisibilities(false, multiUserEnabled);
            updateClickabilities();
            setClickable(false);
        });
    }

    private void updateClickabilities() {
        mMultiUserSwitch.setClickable(mMultiUserSwitch.getVisibility() == View.VISIBLE);
        mEdit.setClickable(mEdit.getVisibility() == View.VISIBLE);
        mRunningServicesButton.setClickable(mRunningServicesButton.getVisibility() == View.VISIBLE);
        mSettingsButton.setClickable(mSettingsButton.getVisibility() == View.VISIBLE);
    }

    private void updateVisibilities(boolean isTunerEnabled, boolean multiUserEnabled) {
        mSettingsContainer.setVisibility(mQsDisabled || !mShowSettingsIcon ? View.GONE : View.VISIBLE);
        final boolean isDemo = UserManager.isDeviceInDemoMode(mContext);
        mMultiUserSwitch.setVisibility(mShowUserIcon &&
                showUserSwitcher(multiUserEnabled) ? View.VISIBLE : View.GONE);
        mRunningServicesButton.setVisibility(isDemo || !mExpanded || !mShowServicesIcon ?
                View.GONE : View.VISIBLE);
        mSettingsButton.setVisibility(isDemo || !mExpanded || !mShowSettingsIcon ?
                View.GONE : View.VISIBLE);
        mEdit.setVisibility(isDemo || !mExpanded || !mShowEditIcon ?
                View.GONE : View.VISIBLE);
    }

    private boolean showUserSwitcher(boolean multiUserEnabled) {
        return mExpanded && multiUserEnabled;
    }

    void onUserInfoChanged(Drawable picture, boolean isGuestUser) {
        if (picture != null && isGuestUser && !(picture instanceof UserIconDrawable)) {
            picture = picture.getConstantState().newDrawable(getResources()).mutate();
            picture.setColorFilter(
                    Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorForeground),
                    Mode.SRC_IN);
        }
        mMultiUserAvatar.setImageDrawable(picture);
    }
}
