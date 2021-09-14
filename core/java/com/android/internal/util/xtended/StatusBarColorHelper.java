/*
 * Copyright (C) 2018 DarkKat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.xtended;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.View;

import com.android.internal.util.xtended.ColorConstants;

public class StatusBarColorHelper {

    public static int getTextColor(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_TEXT_COLOR, ColorConstants.WHITE);
        return (ColorConstants.FULLY_OPAQUE_ALPHA << 24) | (color & 0x00ffffff);
    }

    public static int getIconColor(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_ICON_COLOR, ColorConstants.WHITE);
        return (ColorConstants.FULLY_OPAQUE_ALPHA << 24) | (color & 0x00ffffff);
    }

    public static int getTextColorDarkMode(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_TEXT_COLOR_DARK_MODE, ColorConstants.BLACK);
        return (ColorConstants.FULLY_OPAQUE_ALPHA << 24) | (color & 0x00ffffff);
    }

    public static int getIconColorDarkMode(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_ICON_COLOR_DARK_MODE, ColorConstants.BLACK);
        return (ColorConstants.FULLY_OPAQUE_ALPHA << 24) | (color & 0x00ffffff);
    }

    public static int getBatteryTextColor(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR, ColorConstants.WHITE);
        return (ColorConstants.FULLY_OPAQUE_ALPHA << 24) | (color & 0x00ffffff);
    }

    public static int getBatteryTextColorDarkMode(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR_DARK_MODE, ColorConstants.BLACK);
        return (ColorConstants.FULLY_OPAQUE_ALPHA << 24) | (color & 0x00ffffff);
    }

    private static int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, lightColor, darkColor);
    }

    // Following is based on / copy of methods from:
    // com.android.systemui.statusbar.policy.DarkIconDispatcher


    // DarkIconDispatcher: 'getTint(Rect tintArea, View view, int color)'
    public static int getTextSingleToneTint(Context context, Rect tintArea, View view,
            float darkIntensity) {
        final int colorLight = getTextColor(context);
        final int colorDark = (ColorConstants.DARK_MODE_ALPHA_SINGLE_TONE << 24)
                | (getTextColorDarkMode(context) & 0x00ffffff);
        final int tint = getColorForDarkIntensity(darkIntensity, colorLight, colorDark);
        int tintTouse = colorLight;
        if (isInArea(tintArea, view)) {
            tintTouse = tint;
        }
        return tintTouse;
    }

    // DarkIconDispatcher: 'getTint(Rect tintArea, View view, int color)'
    public static int getIconSingleToneTint(Context context, Rect tintArea, View view,
            float darkIntensity) {
        final int colorLight = getIconColor(context);
        final int colorDark = (ColorConstants.DARK_MODE_ALPHA_SINGLE_TONE << 24)
                | (getIconColorDarkMode(context) & 0x00ffffff);
        final int tint = getColorForDarkIntensity(darkIntensity, colorLight, colorDark);
        int tintTouse = colorLight;
        if (isInArea(tintArea, view)) {
            tintTouse = tint;
        }
        return tintTouse;
    }

    // DarkIconDispatcher: 'getTint(Rect tintArea, View view, int color)'
    public static int getIconDualToneBackgroundTint(Context context, Rect tintArea, View view,
            float darkIntensity) {
        final int colorLight = (ColorConstants.LIGHT_MODE_ALPHA_DUAL_TONE_BACKGROUND << 24)
                | (getIconColor(context) & 0x00ffffff);
        final int colorDark = (ColorConstants.DARK_MODE_ALPHA_DUAL_TONE_BACKGROUND << 24)
                | (getIconColorDarkMode(context) & 0x00ffffff);
        final int tint = getColorForDarkIntensity(darkIntensity, colorLight, colorDark);
        int tintTouse = colorLight;
        if (isInArea(tintArea, view)) {
            tintTouse = tint;
        }
        return tintTouse;
    }

    // DarkIconDispatcher: 'getTint(Rect tintArea, View view, int color)'
    public static int getIconDualToneFillTint(Context context, Rect tintArea, View view,
            float darkIntensity) {
        final int colorLight = (ColorConstants.LIGHT_MODE_ALPHA_DUAL_TONE_FILL << 24)
                | (getIconColor(context) & 0x00ffffff);
        final int colorDark = (ColorConstants.DARK_MODE_ALPHA_DUAL_TONE_FILL << 24)
                | (getIconColorDarkMode(context) & 0x00ffffff);
        final int tint = getColorForDarkIntensity(darkIntensity, colorLight, colorDark);
        int tintTouse = colorLight;
        if (isInArea(tintArea, view)) {
            tintTouse = tint;
        }
        return tintTouse;
    }

    // DarkIconDispatcher: 'getTint(Rect tintArea, View view, int color)'
    public static int getBatteryTextDualToneFillTint(Context context, Rect tintArea, View view,
            float darkIntensity) {
        final int colorLight = (ColorConstants.LIGHT_MODE_ALPHA_DUAL_TONE_FILL << 24)
                | (getBatteryTextColor(context) & 0x00ffffff);
        final int colorDark = (ColorConstants.DARK_MODE_ALPHA_DUAL_TONE_FILL << 24)
                | (getBatteryTextColorDarkMode(context) & 0x00ffffff);
        final int tint = getColorForDarkIntensity(darkIntensity, colorLight, colorDark);
        int tintTouse = colorLight;
        if (isInArea(tintArea, view)) {
            tintTouse = tint;
        }
        return tintTouse;
    }

    // DarkIconDispatcher: 'isInArea(Rect area, View view)'
    private static boolean isInArea(Rect area, View view) {
        if (area.isEmpty()) {
            return true;
        }
        Rect sTmpRect = new Rect();
        int[] sTmpInt2 = new int[2];

        sTmpRect.set(area);
        view.getLocationOnScreen(sTmpInt2);
        int left = sTmpInt2[0];

        int intersectStart = Math.max(left, area.left);
        int intersectEnd = Math.min(left + view.getWidth(), area.right);
        int intersectAmount = Math.max(0, intersectEnd - intersectStart);

        boolean coversFullStatusBar = area.top <= 0;
        boolean majorityOfWidth = 2 * intersectAmount > view.getWidth();
        return majorityOfWidth && coversFullStatusBar;
    }
}

