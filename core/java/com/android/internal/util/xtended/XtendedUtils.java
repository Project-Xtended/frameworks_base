/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.internal.util.xtended;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.om.IOverlayManager;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.IWindowManager;
import android.provider.MediaStore;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Some custom utilities
 */
public class XtendedUtils {

    public static final String ANDROIDNS = "http://schemas.android.com/apk/res/android";
    public static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    public static final String PACKAGE_ANDROID = "android";
    public static final String FORMAT_NONE = "none";
    public static final String FORMAT_FLOAT = "float";
    public static final String ID = "id";
    public static final String DIMEN = "dimen";
    public static final String DIMEN_PIXEL = "dimen_pixel";
    public static final String FLOAT = "float";
    public static final String INT = "integer";
    public static final String DRAWABLE = "drawable";
    public static final String COLOR = "color";
    public static final String BOOL = "bool";
    public static final String STRING = "string";
    public static final String ANIM = "anim";
    public static final String INTENT_SCREENSHOT = "action_take_screenshot";
    public static final String INTENT_REGION_SCREENSHOT = "action_take_region_screenshot";

    private static final String TAG = XtendedUtils.class.getSimpleName();

    private static OverlayManager mOverlayService;

    private static IStatusBarService mStatusBarService = null;

    private static IStatusBarService getStatusBarService() {
        synchronized (XtendedUtils.class) {
            if (mStatusBarService == null) {
                mStatusBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
            }
            return mStatusBarService;
        }
    }

    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                return ignoreState || pi.applicationInfo.enabled;
            } catch (PackageManager.NameNotFoundException e) {
                // Do nothing
            }
        }
        return false;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        final IStatusBarService service = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                service.toggleCameraFlash();
            } catch (RemoteException e) {
                // do nothing.
            }
        }
    }

    // Method to detect whether an overlay is enabled or not
    public static boolean isThemeEnabled(String packageName) {
        mOverlayService = new OverlayManager();
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }

    // Check for Chinese language
    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }
}
