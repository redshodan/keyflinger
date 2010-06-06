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
    static final int KEYCODE_OPTIONS = -100;

    private LatinKeyboardView mThis;
    private KeyFlinger mKeyFlinger;
    private KeyFlingDetector mFlingDetector;

    // Copied from KeyboardView cause everything is bloody private.
    private static final int NOT_A_KEY = -1;
    private Keyboard mKeyboard;
    private Key[] mKeys;
    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];
    private int mProximityThreshold;

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
            new KeyFlingDetector.SimpleOnGestureListener()
            {
                @Override
                public boolean onFling(MotionEvent me1, MotionEvent me2,
                                       float velocityX, float velocityY)
                {
                    final float absX = Math.abs(velocityX);
                    final float absY = Math.abs(velocityY);
                    float deltaX = me2.getX() - me1.getX();
                    float deltaY = me2.getY() - me1.getY();
                    int travelX = 10;
                    int travelY = 10;
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
                    Log.d("KeyFlinger", "Passing in onFling");
                    return false;
                }
            });
    }

    @Override protected boolean onLongPress(Key key)
    {
        Log.d("KeyFlinger", "LKV::onLongPress");
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

    @Override public boolean onTouchEvent(MotionEvent me)
    {
        //dumpEvent(me);
        if (mFlingDetector.onTouchEvent(me))
        {
            mKeyFlinger.ignoreNextKey();
        }
        return super.onTouchEvent(me);
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
        Log.d("KeyFlinger", sb.toString());
    }
}
