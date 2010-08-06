/*
 * Copyright (C) 2010 James Newton
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
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.codepunks.keyflinger;

import org.codepunks.keyflinger.util.VelocityTracker;

import android.os.Handler;
import android.os.Message;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class KeyFlingDetector
{
    public class FlingEvent
    {
        public static final int ACTION_NONE = 0;
        public static final int ACTION_STARTED = 1;
        public static final int ACTION_FLING = 2;

        public int action;
        public MotionEvent down;
        public MotionEvent up;
        public float velocityX;
        public float velocityY;

        public void set(int action, MotionEvent down, MotionEvent up,
                        float velocityX, float velocityY)
        {
            this.action = action;
            this.down = down;
            this.up = up;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
        }

        public void clear()
        {
            this.action = ACTION_NONE;
            this.down = null;
            this.up = null;
            this.velocityX = 0;
            this.velocityY = 0;
        }

        public boolean empty()
        {
            if (down == null)
                return false;
            return true;
        }
    }

    public class FlingState
    {
        private MotionEvent mCurrentDownEvent;
        private MotionEvent mPreviousUpEvent;
        private float mLastMotionY;
        private float mLastMotionX;
        private boolean mStillDown;
        private boolean mInLongPress;
        private boolean mAlwaysInTapRegion;
        private boolean mAlwaysInBiggerTapRegion;
        // True when the user is still touching for the second tap (down, move,
        // and up events). Can only be true if there is a double tap listener
        // attached.
        private boolean mIsDoubleTapping;
    }
    
    public interface OnGestureListener
    {
        boolean onDown(MotionEvent e, int idx, int pid);
        void onShowPress(MotionEvent e, int idx, int pid);
        boolean onSingleTapUp(MotionEvent e, int idx, int pid);
        boolean onScroll(MotionEvent e1, MotionEvent e2, int idx, int pid,
                         float distanceX, float distanceY);
        void onLongPress(MotionEvent e, int idx, int pid);
        boolean onFling(FlingEvent[] e, int idx, int pid);
    }

    public interface OnDoubleTapListener
    {
        boolean onSingleTapConfirmed(MotionEvent e, int idx, int pid);
        boolean onDoubleTap(MotionEvent e, int idx, int pid);
        boolean onDoubleTapEvent(MotionEvent e, int idx, int pid);
    }

    public static class FlingListener
        implements OnGestureListener, OnDoubleTapListener
    {
        public boolean onSingleTapUp(MotionEvent e, int idx, int pid)
        {
            return false;
        }

        public void onLongPress(MotionEvent e, int idx, int pid)
        {
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, int idx, int pid,
                                float distanceX, float distanceY)
        {
            return false;
        }

        public boolean onFling(FlingEvent[] e, int idx, int pid)
        {
            return false;
        }

        public void onShowPress(MotionEvent e, int idx, int pid)
        {
        }

        public boolean onDown(MotionEvent e, int idx, int pid)
        {
            return false;
        }

        public boolean onDoubleTap(MotionEvent e, int idx, int pid)
        {
            return false;
        }

        public boolean onDoubleTapEvent(MotionEvent e, int idx, int pid)
        {
            return false;
        }

        public boolean onSingleTapConfirmed(MotionEvent e, int idx, int pid)
        {
            return false;
        }
    }

    private static final int LONGPRESS_TIMEOUT =
        ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    private static final int DOUBLE_TAP_TIMEOUT =
        ViewConfiguration.getDoubleTapTimeout();

    // constants for Message.what used by GestureHandler below
    private static final int SHOW_PRESS = 1;
    private static final int LONG_PRESS = 2;
    private static final int TAP = 3;

    private final Handler mHandler;
    private final OnGestureListener mListener;
    private OnDoubleTapListener mDoubleTapListener;

    private int mBiggerTouchSlopSquare = 20 * 20;
    private int mTouchSlopSquare;
    private int mDoubleTapSlopSquare;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private boolean mIsLongpressEnabled;
    private int mNumFlings = 2;

    private FlingState[] mFlings;
    private FlingEvent[] mFlingEvents;

    private VelocityTracker mVelocityTracker;

    private class GestureHandler extends Handler {
        FlingState mFling;
        int mIdx;
        int mPid;
        
        GestureHandler() {
            super();
        }

        GestureHandler(Handler handler) {
            super(handler.getLooper());
        }

        public void setFlingState(FlingState fling, int idx, int pid)
        {
            mFling = fling;
            mIdx = idx;
            mPid = pid;
        }
        
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case SHOW_PRESS:
                mListener.onShowPress(mFling.mCurrentDownEvent, mIdx, mPid);
                break;
                
            case LONG_PRESS:
                dispatchLongPress(mFling, mIdx, mPid);
                break;
                
            case TAP:
                // If the user's finger is still down, do not count it as a tap
                if (mDoubleTapListener != null && !mFling.mStillDown)
                {
                    mDoubleTapListener.onSingleTapConfirmed(
                        mFling.mCurrentDownEvent, mIdx, mPid);
                }
                break;

            default:
                throw new RuntimeException("Unknown message " + msg); //never
            }
        }
    }

    @Deprecated public KeyFlingDetector(OnGestureListener listener,
                                        Handler handler)
    {
        this(null, listener, handler);
    }

    @Deprecated public KeyFlingDetector(OnGestureListener listener)
    {
        this(null, listener, null);
    }

    public KeyFlingDetector(Context context, OnGestureListener listener)
    {
        this(context, listener, null);
    }

    public KeyFlingDetector(Context context, OnGestureListener listener,
                            Handler handler)
    {
        if (handler != null) {
            mHandler = new GestureHandler(handler);
        } else {
            mHandler = new GestureHandler();
        }
        mListener = listener;
        if (listener instanceof OnDoubleTapListener) {
            setOnDoubleTapListener((OnDoubleTapListener) listener);
        }
        init(context);
    }

    private void init(Context context)
    {
        if (mListener == null) {
            throw new NullPointerException("OnGestureListener must not be null");
        }
        if (context == null) {
            throw new NullPointerException("Context must not be null");
        }
        mIsLongpressEnabled = true;

        int touchSlop = 10;
        int doubleTapSlop = 10;
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        doubleTapSlop = configuration.getScaledDoubleTapSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMinimumFlingVelocity = 5;
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        mTouchSlopSquare = touchSlop * touchSlop;
        mTouchSlopSquare = 20;
        mDoubleTapSlopSquare = doubleTapSlop * doubleTapSlop;

        mFlings = new FlingState[mNumFlings];
        mFlingEvents = new FlingEvent[mNumFlings];
        for (int i = 0; i < mNumFlings; ++i)
        {
            mFlings[i] = new FlingState();
            mFlingEvents[i] = new FlingEvent();
        }
    }

    public void setParams(int touchSlop, int doubleTapSlop, int minFlingVelocity,
                          boolean longPressEnabled)
    {
        mTouchSlopSquare = touchSlop;
        mDoubleTapSlopSquare = doubleTapSlop;
        mMinimumFlingVelocity = minFlingVelocity;
        mIsLongpressEnabled = longPressEnabled;
    }
    
    public void setOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener)
    {
        mDoubleTapListener = onDoubleTapListener;
    }

    public void setIsLongpressEnabled(boolean isLongpressEnabled)
    {
        mIsLongpressEnabled = isLongpressEnabled;
    }

    public boolean isLongpressEnabled()
    {
        return mIsLongpressEnabled;
    }

    public boolean onTouchEvent(MotionEvent ev)
    {
        if (mVelocityTracker == null)
        {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        boolean handled = false;
        int action = ev.getAction();
        int code = action & MotionEvent.ACTION_MASK;
        int idx = action >> MotionEvent.ACTION_POINTER_ID_SHIFT;
        int pid = ev.getPointerId(idx);

        if ((action == MotionEvent.ACTION_DOWN) ||
            (action == MotionEvent.ACTION_UP) ||
            (code == MotionEvent.ACTION_POINTER_DOWN) ||
            (code == MotionEvent.ACTION_POINTER_UP))

        {
            handled = processEvent(mFlings[idx], ev, action, code, idx, pid);
        }
        else if (action == MotionEvent.ACTION_MOVE)
        {
            for (int i = 0; i < ev.getPointerCount(); ++i)
            {
                handled |= processEvent(mFlings[i], ev, action, code, i, pid);
            }
        }

        return handled;
    }

    public boolean processEvent(FlingState fling, MotionEvent ev, int action,
                                int code, int idx, int pid)
    {
        boolean handled = false;
        final float y = ev.getY(idx);
        final float x = ev.getX(idx);

        if (mHandler instanceof GestureHandler)
        {
            ((GestureHandler)mHandler).setFlingState(fling, pid, idx);
        }

        if (mFlingEvents[pid].action == FlingEvent.ACTION_NONE)
        {
            mFlingEvents[pid].action = FlingEvent.ACTION_STARTED;
        }
        
        if ((action == MotionEvent.ACTION_DOWN) ||
            (code == MotionEvent.ACTION_POINTER_DOWN))
        {
            if (mDoubleTapListener != null)
            {
                boolean hadTapMessage = mHandler.hasMessages(TAP);
                if (hadTapMessage)
                {
                    mHandler.removeMessages(TAP);
                }
                if ((fling.mCurrentDownEvent != null) &&
                    (fling.mPreviousUpEvent != null) &&
                    hadTapMessage &&
                    isConsideredDoubleTap(fling, ev, idx, pid))
                {
                    // This is a second tap
                    fling.mIsDoubleTapping = true;
                    // Give a callback with the first tap of the double-tap
                    handled |=
                        mDoubleTapListener.onDoubleTap(fling.mCurrentDownEvent,
                                                       idx, pid);
                    // Give a callback with down event of the double-tap
                    handled |= mDoubleTapListener.onDoubleTapEvent(ev, idx, pid);
                }
                else
                {
                    // This is a first tap
                    mHandler.sendEmptyMessageDelayed(TAP, DOUBLE_TAP_TIMEOUT);
                }
            }

            fling.mLastMotionX = x;
            fling.mLastMotionY = y;
            fling.mCurrentDownEvent = MotionEvent.obtain(ev);
            fling.mAlwaysInTapRegion = true;
            fling.mAlwaysInBiggerTapRegion = true;
            fling.mStillDown = true;
            fling.mInLongPress = false;

            if (mIsLongpressEnabled)
            {
                mHandler.removeMessages(LONG_PRESS);
                mHandler.sendEmptyMessageAtTime(
                    LONG_PRESS, fling.mCurrentDownEvent.getDownTime() +
                    TAP_TIMEOUT + LONGPRESS_TIMEOUT);
            }
            mHandler.sendEmptyMessageAtTime(
                SHOW_PRESS, fling.mCurrentDownEvent.getDownTime() + TAP_TIMEOUT);
            handled |= mListener.onDown(ev, idx, pid);
        }
        else if ((action == MotionEvent.ACTION_MOVE) && !fling.mInLongPress)
        {
            final float scrollX = fling.mLastMotionX - x;
            final float scrollY = fling.mLastMotionY - y;
            if (fling.mIsDoubleTapping)
            {
                // Give the move events of the double-tap
                handled |= mDoubleTapListener.onDoubleTapEvent(ev, idx, pid);
            }
            else if (fling.mAlwaysInTapRegion)
            {
                final int deltaX = (int) (x - fling.mCurrentDownEvent.getX());
                final int deltaY = (int) (y - fling.mCurrentDownEvent.getY());
                int distance = (deltaX * deltaX) + (deltaY * deltaY);
                if (distance > mTouchSlopSquare)
                {
                    handled = mListener.onScroll(fling.mCurrentDownEvent, ev,
                                                 idx, pid, scrollX, scrollY);
                    fling.mLastMotionX = x;
                    fling.mLastMotionY = y;
                    fling.mAlwaysInTapRegion = false;
                    mHandler.removeMessages(TAP);
                    mHandler.removeMessages(SHOW_PRESS);
                    mHandler.removeMessages(LONG_PRESS);
                }
                if (distance > mBiggerTouchSlopSquare)
                {
                    fling.mAlwaysInBiggerTapRegion = false;
                }
            }
            else if ((Math.abs(scrollX) >= 1) || (Math.abs(scrollY) >= 1))
            {
                handled = mListener.onScroll(fling.mCurrentDownEvent, ev, idx,
                                             pid, scrollX, scrollY);
                fling.mLastMotionX = x;
                fling.mLastMotionY = y;
            }
        }
        else if ((action == MotionEvent.ACTION_UP) ||
                 (code == MotionEvent.ACTION_POINTER_UP))
        {
            fling.mStillDown = false;
            MotionEvent currentUpEvent = MotionEvent.obtain(ev);
            if (fling.mIsDoubleTapping)
            {
                // Finally, give the up event of the double-tap
                handled |= mDoubleTapListener.onDoubleTapEvent(ev, idx, pid);
            }
            else if (fling.mInLongPress)
            {
                mHandler.removeMessages(TAP);
                fling.mInLongPress = false;
            }
            else if (fling.mAlwaysInTapRegion)
            {
                handled = mListener.onSingleTapUp(ev, idx, pid);
            }
            else
            {
                // A fling must travel the minimum tap distance
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000,
                                                       mMaximumFlingVelocity);
                final float velocityY = velocityTracker.getYVelocity(pid);
                final float velocityX = velocityTracker.getXVelocity(pid);

                if ((Math.abs(velocityY) > mMinimumFlingVelocity) ||
                    (Math.abs(velocityX) > mMinimumFlingVelocity))
                {
                    FlingEvent fe = mFlingEvents[pid];
                    fe.set(FlingEvent.ACTION_FLING, fling.mCurrentDownEvent,
                           currentUpEvent, velocityX, velocityY);
                    handled = mListener.onFling(mFlingEvents, idx, pid);
                    fe.clear();
                }
            }
            fling.mPreviousUpEvent = MotionEvent.obtain(ev);
            if (action == MotionEvent.ACTION_UP)
            {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                fling.mIsDoubleTapping = false;
                mHandler.removeMessages(SHOW_PRESS);
                mHandler.removeMessages(LONG_PRESS);
                mFlingEvents[0].clear();
                mFlingEvents[1].clear();
            }
        }
        else if (action == MotionEvent.ACTION_CANCEL)
        {
            mHandler.removeMessages(SHOW_PRESS);
            mHandler.removeMessages(LONG_PRESS);
            mHandler.removeMessages(TAP);
            mVelocityTracker.recycle();
            mVelocityTracker = null;
            fling.mIsDoubleTapping = false;
            fling.mStillDown = false;
            mFlingEvents[0].clear();
            mFlingEvents[1].clear();
            if (fling.mInLongPress)
            {
                fling.mInLongPress = false;
            }
        }
        
        return handled;
    }

    private boolean isConsideredDoubleTap(FlingState fling,
                                          MotionEvent secondDown,
                                          int idx, int pid)
    {
        if (!fling.mAlwaysInBiggerTapRegion)
        {
            return false;
        }

        if ((secondDown.getEventTime() -
             fling.mPreviousUpEvent.getEventTime()) > DOUBLE_TAP_TIMEOUT)
        {
            return false;
        }

        int deltaX = (int)(fling.mCurrentDownEvent.getX(idx) -
                           (int)secondDown.getX());
        int deltaY = (int)(fling.mCurrentDownEvent.getY(idx) -
                           (int)secondDown.getY());
        return (deltaX * deltaX + deltaY * deltaY < mDoubleTapSlopSquare);
    }

    private void dispatchLongPress(FlingState fling, int idx, int pid)
    {
        mHandler.removeMessages(TAP);
        fling.mInLongPress = true;
        mListener.onLongPress(fling.mCurrentDownEvent, idx, pid);
    }
}
