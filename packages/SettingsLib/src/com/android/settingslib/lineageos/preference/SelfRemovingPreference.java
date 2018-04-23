package com.android.settingslib.lineageos.preference;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.preference.R;
import android.util.AttributeSet;

/**
 * A Preference which can automatically remove itself from the hierarchy
 * based on constraints set in XML.
 */
public class SelfRemovingPreference extends Preference {

    private final ConstraintsHelper mConstraints;

    public SelfRemovingPreference(Context context, AttributeSet attrs,
                                  int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        mConstraints = new ConstraintsHelper(context, attrs, this);
    }

    public SelfRemovingPreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public SelfRemovingPreference(Context context, AttributeSet attrs) {
        this(context, attrs, ConstraintsHelper.getAttr(
                context, R.attr.preferenceStyle, android.R.attr.preferenceStyle));
    }

    public SelfRemovingPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mConstraints.onAttached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mConstraints.onBindViewHolder(holder);
    }

    public void setAvailable(boolean available) {
        mConstraints.setAvailable(available);
    }

    public boolean isAvailable() {
        return mConstraints.isAvailable();
    }
}
