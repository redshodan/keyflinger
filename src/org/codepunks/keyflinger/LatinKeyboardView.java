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
import java.util.Arrays;


public class LatinKeyboardView extends KeyboardView
{
    static final String TAG = "KeyFlinger";
    static final int KEYCODE_OPTIONS = -100;

    private LatinKeyboardView mThis;
    private KeyFlinger mKeyFlinger;
    private KeyFlingDetector mFlingDetector;
    private int travelX = 10;
    private int travelY = 10;

    // Copied from android.inputmethodservice.Keyboard
    private static final int NOT_A_KEY = -1;
    private LatinKeyboard mKeyboard;
    private LatinKeyboard.LatinKey[] mKeys;
    private int mProximityThreshold;
    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];
    private int mDownKey = NOT_A_KEY;

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
                    int code = NOT_A_KEY;
                    if ((absY < absX) && (deltaX > travelX))
                    {
                        mKeyFlinger.flingRight(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_RIGHT];
                    }
                    else if ((absY < absX) & (deltaX < -travelX))
                    {
                        mKeyFlinger.flingLeft(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_LEFT];
                    }
                    else if ((absX < absY) && (deltaY < -travelY))
                    {
                        mKeyFlinger.flingUp(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_UP];
                    }
                    else if ((absX < absY / 2) && (deltaY > travelY))
                    {
                        mKeyFlinger.flingDown(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_DOWN];
                    }
                    if (code != NOT_A_KEY)
                    {
                        detectAndSendKey(mDownKey, code);
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
        //dumpEvent(e);
        if (mFlingDetector.onTouchEvent(e))
        {
            mKeyFlinger.ignoreNextKey();
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN)
        {
            int touchX = (int) e.getX() - getPaddingLeft();
            int touchY = (int) e.getY() + getPaddingTop();
            mDownKey = getKeyIndices(touchX, touchY, null);
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

    // Copied from android.inputmethodservice.Keyboard
    @Override public void setKeyboard(Keyboard keyboard)
    {
        mKeyboard = (LatinKeyboard)keyboard;
        List<Key> keys = mKeyboard.getKeys();
        mKeys = keys.toArray(new LatinKeyboard.LatinKey[keys.size()]);

        int length = mKeys.length;
        int dimensionSum = 0;
        for (int i = 0; i < length; i++)
        {
            Key key = mKeys[i];
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mProximityThreshold = (int) (dimensionSum * 1.4f / length);
        mProximityThreshold *= mProximityThreshold; // Square it
        super.setKeyboard(keyboard);
    }
    
    protected int getKeyIndices(int x, int y, int[] allKeys) {
        final Key[] keys = mKeys;
        int closestKey = NOT_A_KEY;
        int primaryIndex = NOT_A_KEY;
        int closestKeyDist = mProximityThreshold + 1;
        java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);
            if (((isProximityCorrectionEnabled()
                  && (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold) 
                 || isInside)
                && key.codes[0] > 32) {
                // Find insertion point
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }
                
                if (allKeys == null) continue;
                
                for (int j = 0; j < mDistances.length; j++) {
                    if (mDistances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(mDistances, j, mDistances, j + nCodes,
                                         mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                         allKeys.length - j - nCodes);
                        for (int c = 0; c < nCodes; c++) {
                            allKeys[j + c] = key.codes[c];
                            mDistances[j + c] = dist;
                        }
                        break;
                    }
                }
            }
            
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }
        }
        if (primaryIndex == NOT_A_KEY) {
            primaryIndex = closestKey;
        }
        return primaryIndex;
    }

    protected void detectAndSendKey(int index, int code /*, int x, int y*/) {
        if (index != NOT_A_KEY && index < mKeys.length) {
            final Key key = mKeys[index];
            if (key.text != null) {
                getOnKeyboardActionListener().onText(key.text);
                getOnKeyboardActionListener().onRelease(NOT_A_KEY);
            } else {
                //TextEntryState.keyPressedAt(key, x, y);
                // int[] codes = new int[MAX_NEARBY_KEYS];
                // Arrays.fill(codes, NOT_A_KEY);
                // getKeyIndices(x, y, codes);
                // Multi-tap
                // if (mInMultiTap) {
                //     if (mTapCount != -1) {
                //         getOnKeyboardActionListener().onKey(
                //             Keyboard.KEYCODE_DELETE, KEY_DELETE);
                //     } else {
                //         mTapCount = 0;
                //     }
                //     code = key.codes[mTapCount];
                // }
                getOnKeyboardActionListener().onKey(code, null /*codes*/);
                getOnKeyboardActionListener().onRelease(code);
            }
        }
    }
}
