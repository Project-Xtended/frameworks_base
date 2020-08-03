package com.android.systemui.statusbar.policy;

import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

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
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.android.internal.util.xtended.XtendedUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.Dependency;
import com.android.systemui.R;

/*
*
* Seeing how an Integer object in java requires at least 16 Bytes, it seemed awfully wasteful
* to only use it for a single boolean. 32-bits is plenty of room for what we need it to do.
*
*/
public class NetworkTrafficSB extends TextView implements StatusIconDisplayable {

    public static final String SLOT = "networktraffic";

    private static final int INTERVAL = 1500; //ms
    private static final int BOTH = 0;
    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int DYNAMIC = 3;
    private static final int KB = 1024;
    private static final int MB = KB * KB;
    private static final int GB = MB * KB;
    private static final String symbol = "B/s";

    private int mNetworkTrafficFontStyle = FONT_NORMAL;
    public static final int FONT_NORMAL = 0;
    public static final int FONT_ITALIC = 1;
    public static final int FONT_BOLD = 2;
    public static final int FONT_BOLD_ITALIC = 3;
    public static final int FONT_LIGHT = 4;
    public static final int FONT_LIGHT_ITALIC = 5;
    public static final int FONT_THIN = 6;
    public static final int FONT_THIN_ITALIC = 7;
    public static final int FONT_CONDENSED = 8;
    public static final int FONT_CONDENSED_ITALIC = 9;
    public static final int FONT_CONDENSED_LIGHT = 10;
    public static final int FONT_CONDENSED_LIGHT_ITALIC = 11;
    public static final int FONT_CONDENSED_BOLD = 12;
    public static final int FONT_CONDENSED_BOLD_ITALIC = 13;
    public static final int FONT_MEDIUM = 14;
    public static final int FONT_MEDIUM_ITALIC = 15;
    public static final int FONT_BLACK = 16;
    public static final int FONT_BLACK_ITALIC = 17;
    public static final int FONT_DANCINGSCRIPT = 18;
    public static final int FONT_DANCINGSCRIPT_BOLD = 19;
    public static final int FONT_COMINGSOON = 20;
    public static final int FONT_NOTOSERIF = 21;
    public static final int FONT_NOTOSERIF_ITALIC = 22;
    public static final int FONT_NOTOSERIF_BOLD = 23;
    public static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;
    public static final int GOBOLD_LIGHT = 25;
    public static final int ROADRAGE = 26;
    public static final int SNOWSTORM = 27;
    public static final int GOOGLESANS = 28;
    public static final int NEONEON = 29;
    public static final int THEMEABLE = 30;
    public static final int SAMSUNG = 31;
    public static final int MEXCELLENT = 32;
    public static final int BURNSTOWN = 33;
    public static final int DUMBLEDOR = 34;
    public static final int PHANTOMBOLD = 35;

    private static DecimalFormat decimalFormat = new DecimalFormat("##0.#");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private boolean mIsEnabled;
    private boolean mAttached;
    private boolean mTrafficInHeaderView;
    private long totalRxBytes;
    private long totalTxBytes;
    private long lastUpdateTime;
    private int txtSize;
    private int txtImgPadding;
    private int mTrafficType;
    private boolean mHideArrow;
    private int mTrafficLayout;
    private int mAutoHideThreshold;
    private int mNetTrafSize;
    private int mTintColor;
    private int mVisibleState = -1;
    private boolean mTrafficVisible = false;
    private boolean mSystemIconVisible = true;
    private boolean mScreenOn = true;
    private boolean iBytes;
    private boolean oBytes;


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

            iBytes = (rxData <= (mAutoHideThreshold * 1024));
            oBytes = (txData <= (mAutoHideThreshold * 1024));

            if (shouldHide(rxData, txData, timeDelta)) {
                setText("");
                mTrafficVisible = false;
            } else {
                CharSequence output;
                if (mTrafficType == UP){
                    output = formatOutput(timeDelta, txData, symbol);
                } else if (mTrafficType == DOWN){
                    output = formatOutput(timeDelta, rxData, symbol);
                } else if (mTrafficType == BOTH) {
                   output = formatOutput(timeDelta, txData, symbol) + "\n" + formatOutput(timeDelta, rxData, symbol);
                 } else if (mTrafficType == DYNAMIC) {
                    if (txData > rxData) {
                        output = formatOutput(timeDelta, txData, symbol);
                        if (!oBytes) {
                            oBytes = false;
                            iBytes = true;
                        } else {
                            oBytes = true;
                            iBytes = true;
                        }
                    } else {
                        output = formatOutput(timeDelta, rxData, symbol);
                        if (!iBytes) {
                            iBytes = false;
                            oBytes = true;
                        } else {
                            iBytes = true;
                            oBytes = true;
                        }
                    }
                } else {
                    output = formatOutput(timeDelta, rxData + txData, symbol);
                    if (txData > rxData) {
                        if (!oBytes) {
                            oBytes = false;
                            iBytes = true;
                        } else {
                            oBytes = true;
                            iBytes = true;
                        }
                    } else {
                        if (!iBytes) {
                            iBytes = false;
                            oBytes = true;
                        } else {
                            iBytes = true;
                            oBytes = true;
                        }
                    }
                }
                // Update view if there's anything new to show
                if (output != getText()) {
                   setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                   if (mTrafficLayout == 1) {
                       setMaxLines(2);
                       setLineSpacing(0.75f, 0.75f);
                   }
                   setText(output);
                }
                mTrafficVisible = true;
            }
            updateVisibility();
	    updateTextSize();
	    updateNetworkTrafficFontStyle();
            updateTrafficDrawable();

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, INTERVAL);
        }

        private CharSequence formatOutput(long timeDelta, long data, String symbol) {
            long speed = (long)(data / (timeDelta / 1000F));
            if (mTrafficLayout == 0 || mTrafficType == BOTH) {
                if (speed < KB) {
                    return decimalFormat.format(speed) + symbol;
                } else if (speed < MB) {
                    return decimalFormat.format(speed / (float)KB) + "K" + symbol;
                } else if (speed < GB) {
                    return decimalFormat.format(speed / (float)MB) + "M" + symbol;
                }
                return decimalFormat.format(speed / (float)GB) + "G" + symbol;
            } else {
                return formatDecimal(speed);
            }
        }

        private CharSequence formatDecimal(long speed) {
            DecimalFormat mDecimalFormat;
            String mUnit;
            String formatSpeed;
            SpannableString spanUnitString;
            SpannableString spanSpeedString;

            if (speed >= GB) {
                mUnit = "G";
                mDecimalFormat = new DecimalFormat("0.00");
                formatSpeed =  mDecimalFormat.format(speed / (float)GB);
            } else if (speed >= 100 * MB) {
                mDecimalFormat = new DecimalFormat("000");
                mUnit = "M";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= 10 * MB) {
                mDecimalFormat = new DecimalFormat("00.0");
                mUnit = "M";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= MB) {
                mDecimalFormat = new DecimalFormat("0.00");
                mUnit = "M";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= 100 * KB) {
                mDecimalFormat = new DecimalFormat("000");
                mUnit = "K";
                formatSpeed =  mDecimalFormat.format(speed / (float)KB);
            } else if (speed >= 10 * KB) {
                mDecimalFormat = new DecimalFormat("00.0");
                mUnit = "K";
                formatSpeed =  mDecimalFormat.format(speed / (float)KB);
            } else {
                mDecimalFormat = new DecimalFormat("0.00");
                mUnit = "K";
                formatSpeed = mDecimalFormat.format(speed / (float)KB);
            }

            spanSpeedString = new SpannableString(formatSpeed);
            spanSpeedString.setSpan(new RelativeSizeSpan(0.75f), 0, (formatSpeed).length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            spanUnitString = new SpannableString(mUnit + symbol);
            spanUnitString.setSpan(new RelativeSizeSpan(0.70f), 0, (mUnit + symbol).length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return TextUtils.concat(spanSpeedString, "\n", spanUnitString);
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KB;
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KB;
           if (mTrafficType == UP) {
            return !getConnectAvailable() || speedTxKB < mAutoHideThreshold;
           } else if (mTrafficType == DOWN) {
            return !getConnectAvailable() || speedRxKB < mAutoHideThreshold;
           } else {
            return !getConnectAvailable() ||
                    (speedRxKB < mAutoHideThreshold &&
                    speedTxKB < mAutoHideThreshold);
           }
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_STATE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_TYPE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_HIDEARROW), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_FONT_STYLE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_FONT_SIZE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_LAYOUT), false,
                    this, UserHandle.USER_ALL);
        }

        /*
         *  @hide
         */
        @Override
        public void onChange(boolean selfChange) {
            setMode();
            updateSettings();
            updateNetworkTrafficFontStyle();
        }
    }

    /*
     *  @hide
     */
    public NetworkTrafficSB(Context context) {
        this(context, null);
    }

    /*
     *  @hide
     */
    public NetworkTrafficSB(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     *  @hide
     */
    public NetworkTrafficSB(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        txtSize = (mTrafficType == BOTH)
                    ? resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size)
                    : mNetTrafSize;
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding);
        mTintColor = resources.getColor(android.R.color.white);
        Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        setMode();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) && mScreenOn) {
                updateSettings();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
                updateSettings();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                clearHandlerCallbacks();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager != null) ? connManager.getActiveNetworkInfo() : null;
        return network != null;
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        mTrafficInHeaderView = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, 0,
                UserHandle.USER_CURRENT) == 1;
        updateVisibility();
        updateTextSize();
        if (mIsEnabled) {
            if (mAttached) {
                totalRxBytes = TrafficStats.getTotalRxBytes();
                lastUpdateTime = SystemClock.elapsedRealtime();
                mTrafficHandler.sendEmptyMessage(1);
            }
            updateTrafficDrawable();
            return;
        } else {
            clearHandlerCallbacks();
        }
    }

    private void setMode() {
        ContentResolver resolver = mContext.getContentResolver();
        mIsEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0,
                UserHandle.USER_CURRENT) == 1;
        mTrafficType = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_TYPE, 0,
                UserHandle.USER_CURRENT);
        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 0,
                UserHandle.USER_CURRENT);
        mHideArrow = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_HIDEARROW, 0,
	        UserHandle.USER_CURRENT) == 1;
        mTrafficInHeaderView = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, 0,
                UserHandle.USER_CURRENT) == 1;
        mNetTrafSize = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_FONT_SIZE, 21,
                UserHandle.USER_CURRENT);
        mTrafficLayout = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_LAYOUT, 0,
                UserHandle.USER_CURRENT);
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    private void updateTrafficDrawable() {
        int intTrafficDrawable;
        if (mIsEnabled && !mHideArrow) {
            if (mTrafficType == UP) {
                if (oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                }
            } else if (mTrafficType == DOWN) {
                if (iBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                }
            } else if (mTrafficType == DYNAMIC || mTrafficType == BOTH) {
                if (iBytes && !oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                } else if (!iBytes && oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                }
            } else {
                if (!iBytes && !oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
                } else if (!oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                } else if (!iBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                }
            }
        } else {
            intTrafficDrawable = 0;
        }
        if (intTrafficDrawable != 0 && !mHideArrow) {
            Drawable d = getContext().getDrawable(intTrafficDrawable);
            d.setColorFilter(mTintColor, Mode.MULTIPLY);
            setCompoundDrawablePadding(txtImgPadding);
            setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
        } else {
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
        setTextColor(mTintColor);
    }

    private void updateTextSize() {
        if (mTrafficLayout == 0 || mTrafficType == BOTH) {
            if (mTrafficType == BOTH) {
                txtSize = getResources().getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
            } else {
                txtSize = mNetTrafSize;
            }
            setLineSpacing(1f, 1f);
        } else {
            txtSize = getResources().getDimensionPixelSize(R.dimen.net_traffic_single_text_size_x);
            setMaxLines(2);
            setLineSpacing(0.75f, 0.75f);
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)txtSize);
    }

    public void onDensityOrFontScaleChanged() {
        final Resources resources = getResources();
        txtSize = (mTrafficType == BOTH)
		    ? resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size)
		    : mNetTrafSize;
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding);
        setCompoundDrawablePadding(txtImgPadding);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)txtSize);
        setGravity(Gravity.RIGHT);
	updateTextSize();
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public String getSlot() {
        return SLOT;
    }

    @Override
    public boolean isIconVisible() {
        return mIsEnabled;
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState) {
            return;
        }
        mVisibleState = state;

        switch (state) {
            case STATE_ICON:
                mSystemIconVisible = true;
                break;
            case STATE_DOT:
            case STATE_HIDDEN:
            default:
                mSystemIconVisible = false;
                break;
        }
        updateVisibility();
    }

    private void updateVisibility() {
        if (mIsEnabled && mTrafficVisible && mSystemIconVisible && !mTrafficInHeaderView) {
			setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    public void setStaticDrawableColor(int color) {
        mTintColor = color;
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public void setDecorColor(int color) {
    }

    private void updateNetworkTrafficFontStyle() {
        mNetworkTrafficFontStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_FONT_STYLE, FONT_NORMAL,
		UserHandle.USER_CURRENT);
        getNetworkTrafficFontStyle(mNetworkTrafficFontStyle);
        updateVisibility();
    }

    public void getNetworkTrafficFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case GOBOLD_LIGHT:
                setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                break;
            case ROADRAGE:
                setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                break;
            case SNOWSTORM:
                setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case GOOGLESANS:
                setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                break;
            case NEONEON:
                setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case THEMEABLE:
                setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
                break;
            case SAMSUNG:
                setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case MEXCELLENT:
                setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case BURNSTOWN:
                setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case DUMBLEDOR:
                setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case PHANTOMBOLD:
                setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
        }
    }
}
