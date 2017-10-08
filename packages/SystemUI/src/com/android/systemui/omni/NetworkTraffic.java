package com.android.systemui.omni;

import java.text.DecimalFormat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;
/*
*
* Seeing how an Integer object in java requires at least 16 Bytes, it seemed awfully wasteful
* to only use it for a single boolean. 32-bits is plenty of room for what we need it to do.
*
*/
public class NetworkTraffic extends TextView implements DarkReceiver {

    private static final int INTERVAL = 1500; //ms
    private static final int KB = 1024;
    private static final int MB = KB * KB;
    private static final int GB = MB * KB;
    private static final String symbol = "B/s";

    private static DecimalFormat decimalFormat = new DecimalFormat("##0.#");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private boolean mIsEnabled;
    private boolean mAttached;
    private long totalRxBytes;
    private long totalTxBytes;
    private long lastUpdateTime;
    private int txtSize;
    private int txtImgPadding;
    private int mAutoHideThreshold;
    private int mTintColor;
    private boolean mEnabled;

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (timeDelta < INTERVAL * .95) {
                if (msg.what != 1) {
                    // we just updated the view, nothing further to do
                    return;
                }
                if (timeDelta < 1) {
                    // Can't div by 0 so make sure the value displayed is minimal
                    timeDelta = Long.MAX_VALUE;
                }
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long rxData = newTotalRxBytes - totalRxBytes;
            long txData = newTotalTxBytes - totalTxBytes;

            if (shouldHide(rxData, txData, timeDelta)) {
                setVisibility(View.GONE);
            } else {
                // Get information for uplink ready so the line return can be added
                String output = formatOutput(timeDelta, txData, symbol);
                // Ensure text size is where it needs to be
                output += "\n";
                // Add information for downlink if it's called for
                output += formatOutput(timeDelta, rxData, symbol);

                // Update view if there's anything new to show
                if (! output.contentEquals(getText())) {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)txtSize);
                    setText(output);
                }
                setVisibility(View.VISIBLE);
            }

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, INTERVAL);
        }

        private String formatOutput(long timeDelta, long data, String symbol) {
            long speed = (long)(data / (timeDelta / 1000F));
            if (speed < KB) {
                return decimalFormat.format(speed) + symbol;
            } else if (speed < MB) {
                return decimalFormat.format(speed / (float)KB) + 'k' + symbol;
            } else if (speed < GB) {
                return decimalFormat.format(speed / (float)MB) + 'M' + symbol;
            }
            return decimalFormat.format(speed / (float)GB) + 'G' + symbol;
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KILOBYTE;
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KILOBYTE;
            return mAutoHide &&
                   (isSet(mState, MASK_DOWN) && speedRxKB <= mAutoHideThreshold ||
                    isSet(mState, MASK_UP) && speedTxKB <= mAutoHideThreshold ||
                    isSet(mState, MASK_UP + MASK_DOWN) &&
                       speedRxKB <= mAutoHideThreshold &&
                       speedTxKB <= mAutoHideThreshold);
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    /*
     *  @hide
     */
    public NetworkTraffic(Context context) {
        this(context, null);
    }

    /*
     *  @hide
     */
    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     *  @hide
     */
    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        txtSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size);
        txtSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding);
        mTintColor = resources.getColor(android.R.color.white);
        Handler mHandler = new Handler();
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateTraffic();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager != null) ? connManager.getActiveNetworkInfo() : null;
        return network != null;
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mAutoHide = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE, 0,
                UserHandle.USER_CURRENT) == 1;

        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 10,
                UserHandle.USER_CURRENT);

        mState = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE,
                1, UserHandle.USER_CURRENT);

        mEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_ENABLE,
                0, UserHandle.USER_CURRENT) != 0;

        if (isSet(mState, MASK_UNIT)) {
            KB = KILOBYTE;
        } else {
            KB = KILOBIT;
        }
        MB = KB * KB;
        GB = MB * KB;

        if (mEnabled) {
            Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
            if (!mAttached) {
                mAttached = true;
                IntentFilter filter = new IntentFilter();
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
            }
            setVisibility(View.VISIBLE);
            // kick it
            updateTraffic();
        } else {
            Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
            clearHandlerCallbacks();
            setVisibility(View.GONE);
            if (mAttached) {
                mContext.unregisterReceiver(mIntentReceiver);
                mAttached = false;
            }
        }
    }

    public void updateTraffic() {
        if (mEnabled && getConnectAvailable()) {
            totalRxBytes = TrafficStats.getTotalRxBytes();
            lastUpdateTime = SystemClock.elapsedRealtime();
            mTrafficHandler.sendEmptyMessage(1);
            //updateTrafficDrawable();
        }
    }

    private void setMode() {
        ContentResolver resolver = mContext.getContentResolver();
        mIsEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 1,
                UserHandle.USER_CURRENT) == 1;
        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 1,
                UserHandle.USER_CURRENT);
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    private void updateTrafficDrawable() {
        /*int intTrafficDrawable;

        if (isSet(mState, MASK_UP + MASK_DOWN)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
        } else {
            intTrafficDrawable = 0;
        }
        if (intTrafficDrawable != 0) {
            Drawable d = getContext().getDrawable(intTrafficDrawable);
            d.setColorFilter(mTintColor, Mode.SRC_ATOP);
            setCompoundDrawablePadding(txtImgPadding);
            setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
        } else {
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }*/
    }

    public void onDensityOrFontScaleChanged() {
        final Resources resources = getResources();
        txtSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size);
        txtSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding);

        int textSize;
        if (isSet(mState, MASK_UP + MASK_DOWN)) {
            textSize = txtSizeMulti;
        } else {
            textSize = txtSizeSingle;
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)textSize);
        //setCompoundDrawablePadding(txtImgPadding);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }
}
