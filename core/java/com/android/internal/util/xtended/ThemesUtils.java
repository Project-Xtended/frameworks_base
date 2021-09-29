/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import com.android.internal.util.xtended.clock.ClockFace;

public class ThemesUtils {

    public static final String TAG = "ThemesUtils";

    private Context mContext;
    private UiModeManager mUiModeManager;
    private IOverlayManager mOverlayManager;
    private PackageManager pm;

    public ThemesUtils(Context context) {
        mContext = context;
        mUiModeManager = context.getSystemService(UiModeManager.class);
        mOverlayManager = IOverlayManager.Stub
                .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
        pm = context.getPackageManager();
    }

    // QS Tile Styles
    public static final String[] QS_TILE_THEMES = {
        "com.android.systemui.qstile.default", // 0
        "com.android.systemui.qstile.circletrim", // 1
        "com.android.systemui.qstile.dualtonecircletrim", // 2
        "com.android.systemui.qstile.squircletrim", // 3
        "com.android.systemui.qstile.wavey", // 4
        "com.android.systemui.qstile.pokesign", // 5
        "com.android.systemui.qstile.ninja", // 6
        "com.android.systemui.qstile.dottedcircle", // 7
        "com.android.systemui.qstile.attemptmountain", // 8
        "com.android.systemui.qstile.squaremedo", // 9
        "com.android.systemui.qstile.inkdrop", // 10
        "com.android.systemui.qstile.cookie", // 11
        "com.android.systemui.qstile.circleoutline", // 12
        "com.bootleggers.qstile.cosmos", // 13
        "com.bootleggers.qstile.divided", // 14
        "com.bootleggers.qstile.neonlike", // 15
        "com.bootleggers.qstile.oos", // 16
        "com.bootleggers.qstile.triangles", // 17
        "com.xtended.qstile.circledecor", // 18
        "com.xtended.qstile.hexdecor", // 19
        "com.xtended.qstile.xtnddecor", // 20
        "com.xtended.qstile.royalcrown", // 21
    };

    // QS Tile Size
    public static final String[] QS_TILE_SIZE_STYLE = {
        "com.android.qstilesize.default", // 0
        "com.android.qstilesize.xsmall", // 1
        "com.android.qstilesize.small", // 2
        "com.android.qstilesize.large", // 3
        "com.android.qstilesize.xlarge", // 4
    };

     // Switch themes
    public static final String[] SWITCH_THEMES = {
        "com.android.system.switch.stock", // 0
        "com.android.system.switch.oneplus", // 1
	"com.android.system.switch.narrow", // 2
        "com.android.system.switch.contained", // 3
        "com.android.system.switch.telegram", // 4
        "com.android.system.switch.md2", // 5
        "com.android.system.switch.retro", // 6
        "com.android.system.switch.oos", // 7
        "com.android.system.switch.fluid", // 8
        "com.android.system.switch.android_s", // 9
    };

    public static final String[] NAVBAR_STYLES = {
            "com.android.theme.navbar.android",
            "com.android.theme.navbar.asus",
            "com.android.theme.navbar.moto",
            "com.android.theme.navbar.nexus",
            "com.android.theme.navbar.old",
            "com.android.theme.navbar.oneplus",
            "com.android.theme.navbar.oneui",
            "com.android.theme.navbar.sammy",
            "com.android.theme.navbar.tecno",
    };

    // Statusbar Signal icons
    public static final String[] SIGNAL_BAR = {
        "org.blissroms.systemui.signalbar_a",
        "org.blissroms.systemui.signalbar_b",
        "org.blissroms.systemui.signalbar_c",
        "org.blissroms.systemui.signalbar_d",
        "org.blissroms.systemui.signalbar_e",
        "org.blissroms.systemui.signalbar_f",
        "org.blissroms.systemui.signalbar_g",
        "org.blissroms.systemui.signalbar_h",
    };

    // Statusbar Wifi icons
    public static final String[] WIFI_BAR = {
        "org.blissroms.systemui.wifibar_a",
        "org.blissroms.systemui.wifibar_b",
        "org.blissroms.systemui.wifibar_c",
        "org.blissroms.systemui.wifibar_d",
        "org.blissroms.systemui.wifibar_e",
        "org.blissroms.systemui.wifibar_f",
        "org.blissroms.systemui.wifibar_g",
        "org.blissroms.systemui.wifibar_h",
    };

    public static final String[] STATUSBAR_HEIGHT = {
            "com.gnonymous.gvisualmod.sbh_m", // 1
            "com.gnonymous.gvisualmod.sbh_l", // 2
            "com.gnonymous.gvisualmod.sbh_xl", // 3
    };

    public static final String[] UI_RADIUS = {
            "com.gnonymous.gvisualmod.urm_r", // 1
            "com.gnonymous.gvisualmod.urm_m", // 2
            "com.gnonymous.gvisualmod.urm_l", // 3
    };

    public static final String NAVBAR_COLOR_PURP = "com.gnonymous.gvisualmod.pgm_purp";

    public static final String NAVBAR_COLOR_ORCD = "com.gnonymous.gvisualmod.pgm_orcd";

    public static final String NAVBAR_COLOR_OPRD = "com.gnonymous.gvisualmod.pgm_oprd";

    public static final String NAVBAR_COLOR_BLUE = "com.gnonymous.gvisualmod.pgm_blue";

    public static final String NAVBAR_COLOR_ROSE = "com.gnonymous.gvisualmod.pgm_rose";

    public static final String HEADER_LARGE = "com.android.theme.header.large";

    public static final String HEADER_XLARGE = "com.android.theme.header.xlarge";

    // Brightness Slider Styles, DU-Way
    public static final String[] BRIGHTNESS_SLIDER_DANIEL = {
            "com.android.systemui.brightness.slider.daniel",
    };
    public static final String[] BRIGHTNESS_SLIDER_MEMEMINII = {
            "com.android.systemui.brightness.slider.mememini",
    };
    public static final String[] BRIGHTNESS_SLIDER_MEMEROUND = {
            "com.android.systemui.brightness.slider.memeround",
    };
    public static final String[] BRIGHTNESS_SLIDER_MEMEROUNDSTROKE = {
            "com.android.systemui.brightness.slider.memeroundstroke",
    };
    public static final String[] BRIGHTNESS_SLIDER_MEMESTROKE = {
            "com.android.systemui.brightness.slider.memestroke",
    };

    // System-Wide Slider Styles, DU-Way
    public static final String[] SYSTEM_SLIDER_DANIEL = {
            "com.android.system.slider.daniel",
    };
    public static final String[] SYSTEM_SLIDER_MEMEMINII = {
            "com.android.system.slider.mememini",
    };
    public static final String[] SYSTEM_SLIDER_MEMEROUND = {
            "com.android.system.slider.memeround",
    };
    public static final String[] SYSTEM_SLIDER_MEMEROUNDSTROKE = {
            "com.android.system.slider.memeroundstroke",
    };
    public static final String[] SYSTEM_SLIDER_MEMESTROKE = {
            "com.android.system.slider.memestroke",
    };

    public static final String[] PANEL_BG_STYLE = {
        "com.jrinfected.panel.batik", // 1
        "com.jrinfected.panel.kece", // 2
        "com.jrinfected.panel.outline", // 3
        "com.jrinfected.panel.xaccent", // 4
        "com.jrinfected.panel.xgradient", // 5
        "com.jrinfected.panel.xrevgrad", // 6
        "com.jrinfected.panel.kecegrad", // 7
        "com.jrinfected.panel.kecerevgrad", // 8
    };

    public static final String[] NOTIF_CAT_STYLE = {
        "com.xtended.notif.accentnotifcat", // 1
        "com.xtended.notif.gradnotifcat", // 2
        "com.xtended.notif.revgradnotifcat", // 3
    };

    // Switches qs tile style to user selected.
    public static void updateNewTileStyle(IOverlayManager om, int userId, int qsTileStyle) {
        if (qsTileStyle == 0) {
            stockNewTileStyle(om, userId);
        } else {
            try {
                om.setEnabled(QS_TILE_THEMES[qsTileStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs tile style", e);
            }
        }
    }

    // Switches qs tile style back to stock.
    public static void stockNewTileStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_TILE_THEMES.length; i++) {
            String qstiletheme = QS_TILE_THEMES[i];
            try {
                om.setEnabled(qstiletheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Switches qs tile size to user selected.
    public static void updateNewQsTileStyle(IOverlayManager om, int userId, int newQsSizeStyle) {
        if (newQsSizeStyle == 0) {
            stockNewQsTileSize(om, userId);
        } else {
            try {
                om.setEnabled(QS_TILE_SIZE_STYLE[newQsSizeStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs tile size", e);
            }
        }
    }

    // Switches qs tile size back to stock.
    public static void stockNewQsTileSize(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_TILE_SIZE_STYLE.length; i++) {
            String qsNewTilesize = QS_TILE_SIZE_STYLE[i];
            try {
                om.setEnabled(qsNewTilesize,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateSwitchStyle(IOverlayManager om, int userId, int switchStyle) {
        if (switchStyle == 0) {
            stockSwitchStyle(om, userId);
        } else {
            try {
                om.setEnabled(SWITCH_THEMES[switchStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change switch theme", e);
            }
        }
    }

    public static void stockSwitchStyle(IOverlayManager om, int userId) {
        for (int i = 1; i < SWITCH_THEMES.length; i++) {
            String switchtheme = SWITCH_THEMES[i];
            try {
                om.setEnabled(switchtheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public List<ClockFace> getClocks() {
        ProviderInfo providerInfo = mContext.getPackageManager().resolveContentProvider("com.android.keyguard.clock",
                        PackageManager.MATCH_SYSTEM_ONLY);

        Uri optionsUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(providerInfo.authority)
                .appendPath("list_options")
                .build();

        ContentResolver resolver = mContext.getContentResolver();

        List<ClockFace> clocks = new ArrayList<>();
        try (Cursor c = resolver.query(optionsUri, null, null, null, null)) {
            while(c.moveToNext()) {
                String id = c.getString(c.getColumnIndex("id"));
                String title = c.getString(c.getColumnIndex("title"));
                String previewUri = c.getString(c.getColumnIndex("preview"));
                Uri preview = Uri.parse(previewUri);
                String thumbnailUri = c.getString(c.getColumnIndex("thumbnail"));
                Uri thumbnail = Uri.parse(thumbnailUri);

                ClockFace.Builder builder = new ClockFace.Builder();
                builder.setId(id).setTitle(title).setPreview(preview).setThumbnail(thumbnail);
                clocks.add(builder.build());
            }
        } catch (Exception e) {
            clocks = null;
        } finally {
            // Do Nothing
        }
        return clocks;
    }

}
