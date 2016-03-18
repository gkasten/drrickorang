/*
 * Copyright (C) 2016 The Android Open Source Project
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

package org.drrickorang.loopback;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsPicker extends LinearLayout implements SeekBar.OnSeekBarChangeListener,
        CatchEventsEditText.EditTextEventListener {

    protected TextView mTitleTextView;
    protected CatchEventsEditText mValueEditText;
    protected SeekBar mValueSeekBar;
    protected SettingChangeListener mSettingsChangeListener;

    protected int mMinimumValue;
    protected int mMaximumValue;

    public interface SettingChangeListener {
        public void settingChanged(int value);
    }

    public SettingsPicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.settings_picker, this);

        mTitleTextView = (TextView) findViewById(R.id.settings_title);
        mValueEditText = (CatchEventsEditText) findViewById(R.id.settings_valueText);
        mValueSeekBar = (SeekBar) findViewById(R.id.settings_seekbar);

        mValueEditText.setEditTextEvenListener(this);
        mValueSeekBar.setOnSeekBarChangeListener(this);
    }

    public void setMinMaxDefault(int min, int max, int def) {
        mMinimumValue = min;
        mMaximumValue = max;
        mValueSeekBar.setMax(max - min);
        setValue(def);
    }

    public void setTitle(String title) {
        mTitleTextView.setText(title);
    }

    public void setValue(int value) {
        mValueSeekBar.setProgress(value - mMinimumValue);
        mValueEditText.setText(Integer.toString(value));
    }

    public void setSettingsChangeListener(SettingChangeListener settingsChangeListener) {
        mSettingsChangeListener = settingsChangeListener;
    }

    protected void textChanged(int value) {
        mValueSeekBar.setProgress(value - mMinimumValue);
        if (mSettingsChangeListener != null) {
            mSettingsChangeListener.settingChanged(value);
        }
    }

    protected void sliderChanged(int value, boolean userInteractionFinished) {
        mValueEditText.setText(Integer.toString(value));
        if (userInteractionFinished && mSettingsChangeListener != null) {
            mSettingsChangeListener.settingChanged(value);
        }
    }

    @Override
    public void textEdited(EditText v) {
        if (!v.getText().toString().isEmpty()) {
            int value;
            try {
                value = Integer.parseInt(v.getText().toString());
            } catch (NumberFormatException e) {
                value = mMinimumValue;
                v.setText(Integer.toString(value));
            }
            if (value < mMinimumValue) {
                value = mMinimumValue;
                v.setText(Integer.toString(value));
            } else if (value > mMaximumValue) {
                value = mMaximumValue;
                v.setText(Integer.toString(value));
            }
            textChanged(value);
        } else {
            sliderChanged(mMinimumValue + mValueSeekBar.getProgress(), false);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            sliderChanged(mMinimumValue + progress, false);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        sliderChanged(mMinimumValue + seekBar.getProgress(), true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        mValueEditText.setEnabled(enabled);
        mValueSeekBar.setEnabled(enabled);
    }
}
