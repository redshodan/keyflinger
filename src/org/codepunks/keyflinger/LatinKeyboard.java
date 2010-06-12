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
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.Keyboard.Row;
import android.view.inputmethod.EditorInfo;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import java.util.List;

public class LatinKeyboard extends Keyboard
{
    static private String TAG = "KeyFlinger";
    static final public int KEY_INDEX_UP = 0;
    static final public int KEY_INDEX_DOWN = 1;
    static final public int KEY_INDEX_LEFT = 2;
    static final public int KEY_INDEX_RIGHT = 3;
    static final public int KEY_INDEX_MAX = 4;
    static final public int KEYCODE_CTL = -10;
    static final public int KEYCODE_ESC = -11;
    private Key mEnterKey;

    public LatinKeyboard(Context context, int xmlLayoutResId)
    {
        super(context, xmlLayoutResId);
    }

    public LatinKeyboard(Context context, int layoutTemplateResId, 
                         CharSequence characters, int columns,
                         int horizontalPadding)
    {
        super(context, layoutTemplateResId, characters, columns,
              horizontalPadding);
    }

    @Override protected Key createKeyFromXml(Resources res, Row parent, int x,
                                             int y, XmlResourceParser parser)
    {
        Key key = new LatinKey(res, parent, x, y, parser);
        if (key.codes[0] == 10)
        {
            mEnterKey = key;
        }
        return key;
    }
    
    /**
     * This looks at the ime options given by the current editor, to set the
     * appropriate label on the keyboard's enter key (if it has one).
     */
    void setImeOptions(Resources res, int options)
    {
        if (mEnterKey == null)
        {
            return;
        }
        
        switch (options & (EditorInfo.IME_MASK_ACTION |
                           EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        {
        case EditorInfo.IME_ACTION_GO:
            mEnterKey.iconPreview = null;
            mEnterKey.icon = null;
            mEnterKey.label = res.getText(R.string.label_go_key);
            break;
        case EditorInfo.IME_ACTION_NEXT:
            mEnterKey.iconPreview = null;
            mEnterKey.icon = null;
            mEnterKey.label = res.getText(R.string.label_next_key);
            break;
        case EditorInfo.IME_ACTION_SEARCH:
            mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_search);
            mEnterKey.label = null;
            break;
        case EditorInfo.IME_ACTION_SEND:
            mEnterKey.iconPreview = null;
            mEnterKey.icon = null;
            mEnterKey.label = res.getText(R.string.label_send_key);
            break;
        default:
            mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_return);
            mEnterKey.label = null;
            break;
        }
    }

    static class LatinKey extends Keyboard.Key
    {
        public static final int MAX = 4;
        public static final String XMLNS =
            "http://codepunks.org/schemas/android/res/keyflinger";
        public int[] mDCodes;
        public String[] mDLabels;
        public int[][] mDOffsets;
        
        public LatinKey(Resources res, Keyboard.Row parent, int x, int y,
                        XmlResourceParser parser)
        {
            super(res, parent, x, y, parser);

            try
            {
            mDCodes = new int[MAX];
            mDLabels = new String[MAX];
            mDOffsets = new int[MAX][2];
            AttributeSet attrs = Xml.asAttributeSet(parser);
            for (int i = 0; i < MAX; ++i)
            {
                String name;
                if (i == KEY_INDEX_UP)
                    name = "Up";
                else if (i == KEY_INDEX_DOWN)
                    name = "Down";
                else if (i == KEY_INDEX_RIGHT)
                    name = "Right";
                else if (i == KEY_INDEX_LEFT)
                    name = "Left";
                else
                    name = "";
                mDCodes[i] = attrs.getAttributeIntValue(
                    XMLNS, String.format("key%sCode", name), -1000);
                mDLabels[i] = attrs.getAttributeValue(
                    XMLNS, String.format("key%sLabel", name));
                mDOffsets[i][0] = attrs.getAttributeIntValue(
                    XMLNS, String.format("key%sXOff", name), 0);
                mDOffsets[i][1] = attrs.getAttributeIntValue(
                    XMLNS, String.format("key%sYOff", name), 0);
                // if (mDCodes[i] > -1000)
                // {
                //     Log.d(TAG, String.format("found code: %d", mDCodes[i]));
                // }
                // if (mDLabels[i] != null)
                // {
                //     Log.d(TAG, String.format("found label: %s", mDLabels[i]));
                // }
            }
            }
            catch (Exception e)
            {
                Log.d(TAG, "Exception: " + e.toString());
            }
        }
        
        /**
         * Overriding this method so that we can reduce the target area for the
         * key that closes the keyboard. 
         */
        @Override public boolean isInside(int x, int y)
        {
            return super.isInside(x, codes[0] == KEYCODE_CANCEL ? y - 10 : y);
        }
    }
}
