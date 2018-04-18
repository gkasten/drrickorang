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
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/**
 *  Provides a callback for both soft-keyboard dismissed or confirm submission button
 */
public class CatchEventsEditText extends EditText implements TextView.OnEditorActionListener {

    public interface EditTextEventListener {
        public void textEdited(EditText v);
    }

    private EditTextEventListener mEditListener;

    public CatchEventsEditText(Context context) {
        super(context);
        setOnEditorActionListener(this);
    }

    public CatchEventsEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnEditorActionListener(this);
    }

    public CatchEventsEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnEditorActionListener(this);
    }

    public void setEditTextEvenListener(EditTextEventListener listener) {
        mEditListener = listener;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                || (actionId == EditorInfo.IME_ACTION_DONE)) {
            mEditListener.textEdited(this);
        }
        // Necessary to return false even when event handled for soft-keyboard to be dismissed
        // Differs from on click listener chains where first listener to handle returns true
        return false;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            mEditListener.textEdited(this);
        }
        return super.onKeyPreIme(keyCode, event);
    }

}
