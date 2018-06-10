/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.statusbar;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.R;

public class DuClockUtils {
    public static final String TAG = "DuClockUtils";

    private static final String[] getClocks(Context ctx) {
        final String list = ctx.getResources().getString(R.string.custom_clock_styles);
        return list.split(",");
    }

    // Switches the analog clock from one to another or back to stock
    public static void updateClocks(IOverlayManager om, int userId, int clockSetting, Context ctx) {
        // all clock already unloaded due to StatusBar observer unloadClocks call
        // set the custom analog clock overlay
        if (clockSetting > 4) {
            try {
                final String[] clocks = getClocks(ctx);
                om.setEnabled(clocks[clockSetting],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change analog clocks", e);
            }
        }
    }

    // Unload all the analog clocks
    public static void unloadClocks(IOverlayManager om, int userId, Context ctx) {
        // skip index 0
        final String[] clocks = getClocks(ctx);
        for (int i = 1; i < clocks.length; i++) {
            String clock = clocks[i];
            try {
                om.setEnabled(clock,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}

