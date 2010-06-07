/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.codepunks.keyflinger;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.view.MotionEvent;
import android.util.AttributeSet;
import android.util.Log;
import java.util.List;


public class LatinKeyboardView extends KeyboardView
{
    static final String TAG = "KeyFlinger";
    static final int KEYCODE_OPTIONS = -100;

    private LatinKeyboardView mThis;
    private KeyFlinger mKeyFlinger;
    private KeyFlingDetector mFlingDetector;
    private int travelX = 10;
    private int travelY = 10;

    public LatinKeyboardView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initKeyFlinging();
        mThis = this;
        setPreviewEnabled(false);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        initKeyFlinging();
        mThis = this;
        setPreviewEnabled(false);
    }

    public void setKeyFlinger(KeyFlinger kf)
    {
        mKeyFlinger = kf;
    }

    protected void initKeyFlinging()
    {
        mFlingDetector = new KeyFlingDetector(getContext(),
            new KeyFlingDetector.FlingListener()
            {
                @Override
                public boolean onFling(KeyFlingDetector.FlingEvent[] evs,
                                       int idx, int pid)
                {
                    Log.d(TAG, String.format("onFling idx=%d pid=%d", idx, pid));
                    KeyFlingDetector.FlingEvent e = evs[pid];
                    final float absX = Math.abs(e.velocityX);
                    final float absY = Math.abs(e.velocityY);
                    float deltaX = e.up.getX(idx) - e.down.getX(idx);
                    float deltaY = e.up.getY(idx) - e.down.getY(idx);
                    if ((absY < absX) && (deltaX > travelX))
                    {
                        mKeyFlinger.flingRight();
                        return true;
                    }
                    else if ((absY < absX) & (deltaX < -travelX))
                    {
                        mKeyFlinger.flingLeft();
                        return true;
                    }
                    else if ((absX < absY) && (deltaY < -travelY))
                    {
                        mKeyFlinger.flingUp();
                        return true;
                    }
                    else if ((absX < absY / 2) && (deltaY > travelY))
                    {
                        mKeyFlinger.flingDown();
                        return true;
                    }
                    Log.d(TAG, "Passing in onFling");
                    return false;
                }
            });
    }

    @Override protected boolean onLongPress(Key key)
    {
        Log.d(TAG, "LKV::onLongPress");
        if (key.codes[0] == Keyboard.KEYCODE_CANCEL)
        {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        }
        else
        {
            return super.onLongPress(key);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e)
    {
        dumpEvent(e);
        if (mFlingDetector.onTouchEvent(e))
        {
            mKeyFlinger.ignoreNextKey();
        }
        return super.onTouchEvent(e);
    }

    private void dumpEvent(MotionEvent event)
    {
        String names[] = { "DOWN" , "UP" , "MOVE" , "CANCEL" , "OUTSIDE" ,
                           "POINTER_DOWN" , "POINTER_UP" , "7?" , "8?" , "9?" };
        StringBuilder sb = new StringBuilder();
        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        sb.append("event ACTION_" ).append(names[actionCode]);
        if ((actionCode == MotionEvent.ACTION_POINTER_DOWN) ||
            (actionCode == MotionEvent.ACTION_POINTER_UP))
        {
            sb.append("(pid " ).append(
                action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
            sb.append(")" );
        }
        sb.append("[" );
        for (int i = 0; i < event.getPointerCount(); i++)
        {
            sb.append("#" ).append(i);
            sb.append("(pid " ).append(event.getPointerId(i));
            sb.append(")=" ).append((int) event.getX(i));
            sb.append("," ).append((int) event.getY(i));
            if (i + 1 < event.getPointerCount())
                sb.append(";" );
        }
        sb.append("]" );
        Log.d(TAG, sb.toString());
    }
}
