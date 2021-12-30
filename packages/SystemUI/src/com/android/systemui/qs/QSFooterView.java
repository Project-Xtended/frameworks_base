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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;

/**
 * Footer of expanded Quick Settings, tiles page indicator, (optionally) build number and
 * {@link FooterActionsView}
 */
public class QSFooterView extends FrameLayout {
    private PageIndicator mPageIndicator;
    private View mEditButton;

    @Nullable
    protected TouchAnimator mFooterAnimator;

    private boolean mQsDisabled;
    private boolean mExpanded;
    private float mExpansionAmount;


    @Nullable
    private OnClickListener mExpandClickListener;

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
        mPageIndicator = findViewById(R.id.footer_page_indicator);
        mEditButton = findViewById(android.R.id.edit);

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
        if (isShow) {
            v.setText("#Xtended");
            v.setVisibility(View.VISIBLE);
        } else {
            v.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    private void updateResources() {
        updateFooterAnimator();
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.qs_footers_margin_bottom);
        setLayoutParams(lp);
    }

    private void updateFooterAnimator() {
        mFooterAnimator = createFooterAnimator();
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mPageIndicator, "alpha", 0, 1)
                .addFloat(mEditButton, "alpha", 0, 1)
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

    void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();
    }

    /** */
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
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

    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    void disable(int state2) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        updateEverything();
    }

    void updateEverything() {
        post(() -> {
            updateVisibilities();
            updateClickabilities();
            setClickable(false);
        });
    }

    private void updateClickabilities() {
    }

    private void updateVisibilities() {
    }
}
