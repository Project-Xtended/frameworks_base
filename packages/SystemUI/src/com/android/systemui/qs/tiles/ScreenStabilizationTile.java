package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Switch;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Enable/Disable ScreenStabilization **/
public class ScreenStabilizationTile extends QSTile<QSTile.BooleanState> {
    
    private SettingsObserver mSettingsObserver;
    private boolean mListening;
    
    public ScreenStabilizationTile(Host host) {
        super(host);
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.register();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        mListening = listening;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.SCREEN_STABILIZATION_SETTINGS);
    }

    @Override
    protected void handleClick() {
        ContentResolver resolver = mContext.getContentResolver();
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.value);
        Settings.System.putInt(resolver, Settings.System.STABILIZATION_ENABLE, (Settings.System.getInt(resolver, Settings.System.STABILIZATION_ENABLE, 0) == 1) ? 0:1);
    }

    @Override
    protected void handleSecondaryClick() {
        handleClick();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_stabilization_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final Drawable mEnable = mContext.getDrawable(R.drawable.ic_screen_stabilization_enabled);
        final Drawable mDisable = mContext.getDrawable(R.drawable.ic_screen_stabilization_disabled);
        state.value = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.STABILIZATION_ENABLE, 0) == 1);
        state.label = mContext.getString(R.string.quick_settings_stabilization_label);
        state.icon = new DrawableIcon(state.value ? mEnable : mDisable);
        state.minimalAccessibilityClassName = state.expandedAccessibilityClassName
                = Switch.class.getName();
        state.contentDescription = state.label;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EXTENSIONS;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.quick_settings_stabilization_on);
        } else {
            return mContext.getString(R.string.quick_settings_stabilization_off);
        }
    }
    
    private class SettingsObserver extends ContentObserver {
	private ContentResolver mResolver;
        SettingsObserver(Handler handler) {
            super(handler);
	    mResolver = mContext.getContentResolver();
        }

        public void register() {
            mResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STABILIZATION_ENABLE),
                    false, this, UserHandle.USER_ALL);
            refreshState();
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    }
}
