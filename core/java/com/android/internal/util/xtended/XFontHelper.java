/*
* Copyright (C) 2017-2021 The Project-Xtended
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.internal.util.xtended;

import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

public class XFontHelper {

    public static void setFontType(TextView view, int font) {
	    switch (font) {
	        case 0:
		default:
        	    view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            	break;
	        case 1:
	            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
	            break;
	        case 2:
	            view.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
	            break;
	        case 3:
	            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
	            break;
	        case 4:
	            view.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
	            break;
	        case 5:
	            view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
	            break;
	        case 6:
	            view.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
	            break;
	        case 7:
	            view.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
	            break;
	        case 8:
	            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
	            break;
	        case 9:
	            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
	            break;
	        case 10:
	            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
	            break;
	        case 11:
	            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
	            break;
	        case 12:
	            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
	            break;
	        case 13:
	            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
	            break;
	        case 14:
	            view.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
	            break;
	        case 15:
	            view.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
	            break;
	        case 16:
	            view.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
	            break;
	        case 17:
	            view.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
	            break;
	        case 18:
	            view.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
	            break;
	        case 19:
	            view.setTypeface(Typeface.create("cursive", Typeface.BOLD));
	            break;
	        case 20:
	            view.setTypeface(Typeface.create("casual", Typeface.NORMAL));
	            break;
	        case 21:
	            view.setTypeface(Typeface.create("serif", Typeface.NORMAL));
	            break;
	        case 22:
	            view.setTypeface(Typeface.create("serif", Typeface.ITALIC));
	            break;
	        case 23:
	            view.setTypeface(Typeface.create("serif", Typeface.BOLD));
	            break;
	        case 24:
	            view.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
	            break;
	        case 25:
	            view.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
	            break;
	        case 26:
	            view.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
	            break;
	        case 27:
	            view.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
	            break;
	        case 28:
                    view.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
	            break;
	        case 29:
	            view.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
	            break;
	        case 30:
	            view.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
	            break;
                case 31:
	            view.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
	            break;
	        case 32:
	            view.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
	            break;
	        case 33:
	            view.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
	            break;
	        case 34:
	            view.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
	            break;
	        case 35:
	            view.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
	            break;
	}
    }
}

