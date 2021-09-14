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

public class ColorConstants {

    // Theme general
    public static final int WHITE = 0xffffffff;
    public static final int BLACK = 0xff000000;

    // SystemUI
    public static final int LIGHT_MODE_COLOR_SINGLE_TONE          = WHITE;
    public static final int LIGHT_MODE_COLOR_DUAL_TONE_BACKGROUND = 0x4dffffff;
    public static final int LIGHT_MODE_COLOR_DUAL_TONE_FILL       = WHITE;

    public static final int DARK_MODE_COLOR_SINGLE_TONE          = 0x99000000;
    public static final int DARK_MODE_COLOR_DUAL_TONE_BACKGROUND = 0x3d000000;
    public static final int DARK_MODE_COLOR_DUAL_TONE_FILL       = 0x7a000000;

    // Alpha values

    // General
    public static final int FULLY_OPAQUE_ALPHA       = 255;

    // General (Night theme)
    public static final int TEXT_PRIMARY_ALPHA_NIGHT   = FULLY_OPAQUE_ALPHA;
    public static final int TEXT_SECONDARY_ALPHA_NIGHT = 179;
    public static final int ICON_NORMAL_ALPHA_NIGHT    = TEXT_SECONDARY_ALPHA_NIGHT;

    // General (Day theme)
    public static final int TEXT_PRIMARY_ALPHA_DAY   = 222;
    public static final int TEXT_SECONDARY_ALPHA_DAY = 138;
    public static final int ICON_NORMAL_ALPHA_DAY    = TEXT_SECONDARY_ALPHA_DAY;

    // SystemUI
    public static final int LIGHT_MODE_ALPHA_SINGLE_TONE          = FULLY_OPAQUE_ALPHA;
    public static final int LIGHT_MODE_ALPHA_DUAL_TONE_BACKGROUND = 77;
    public static final int LIGHT_MODE_ALPHA_DUAL_TONE_FILL       = FULLY_OPAQUE_ALPHA;

    public static final int DARK_MODE_ALPHA_SINGLE_TONE          = 153;
    public static final int DARK_MODE_ALPHA_DUAL_TONE_BACKGROUND = 61;
    public static final int DARK_MODE_ALPHA_DUAL_TONE_FILL       = 122;
}

