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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.inputmethodservice.ExtractEditText;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ExtractView extends ExtractEditText
{
    private int trans;

    // private class ExtractDraw extends ColorDrawable
    // {
    //     public ExtractDraw()
    //     {
    //         super();
    //         setAlpha(0);
    //     }
        
    //     @Override public int getOpacity()
    //     {
    //         Log.d("KeyFlinger", "ExtractDraw::getOpacity");
    //         return android.graphics.PixelFormat.TRANSPARENT;
    //     }

    //     @Override public void draw(Canvas canvas)
    //     {
    //         Log.d("KeyFlinger", "ExtractDraw::onDraw");
    //         //super.onDraw(canvas);
    //         // canvas.drawColor(Color.argb(0, 0, 0, 0));
    //         // Paint paint = new Paint();
    //         // paint.setAntiAlias(true);
    //         // paint.setARGB(0, 70, 70, 70);
    //         // Rect drawRect = new Rect();
    //         // drawRect.set(0,0, getMeasuredWidth(), getMeasuredHeight());
    //         // canvas.drawRect(drawRect, paint);
    //     }
    //     @Override public Region getTransparentRegion()
    //     {
    //         return null;
    //     }
    // }
    
    public ExtractView(Context context)
    {
        super(context);
        Resources r = context.getResources();
        setId(android.R.id.inputExtractEditText);
        // ColorDrawable cd = new ColorDrawable(0);
        // setBackgroundDrawable(cd);
        // setDrawingCacheBackgroundColor(0);
        setBackgroundColor(r.getColor(R.color.candidate_background));
        //setWillNotDraw(true);
        //trans = r.getColor(android.R.color.transparent);
        //setBackgroundColor(trans);
        //setAlpha(0);
        //setBackgroundDrawable(new ExtractDraw());
        //setBackgroundResource(trans);
        //setDrawingCacheBackgroundColor(0);
    }

    // @Override public boolean isOpaque()
    // {
    //     Log.d("KeyFlinger", "isOpaque");
    //     return false;
    // }

    // @Override public int getSolidColor()
    // {
    //     Log.d("KeyFlinger", "getSolidColor");
    //     return 0;
    // }

    // @Override protected boolean onSetAlpha(int alpha)
    // {
    //     Log.d("KeyFlinger", "onSetAlpha");
    //     return true;
    // }

    // @Override protected void dispatchDraw(Canvas canvas)
    // {
    //     Log.d("KeyFlinger", "dispatchDraw");
    // }

    // @Override protected void onDraw(Canvas canvas)
    // {
    //     Log.d("KeyFlinger", "onDraw");
    //     //super.onDraw(canvas);
    //     //canvas.drawColor(trans);
    //     // Paint paint = new Paint();
    //     // paint.setAntiAlias(true);
    //     // paint.setARGB(0, 70, 70, 70);
    //     // Rect drawRect = new Rect();
    //     // drawRect.set(0,0, getMeasuredWidth(), getMeasuredHeight());
    //     // canvas.drawRect(drawRect, paint);
    // }

    // @Override public void draw(Canvas canvas)
    // {
    //     Log.d("KeyFlinger", "draw");
    //     //canvas.drawColor(Color.argb(0, 255, 255, 255));
    //     // canvas.drawColor(Color.argb(0, 0, 0, 0));
    //     canvas.drawColor(Color.argb(0, 255, 0, 0));
    // }
}
