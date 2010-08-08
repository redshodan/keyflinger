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

package org.codepunks.keyflinger;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class KeyFlingerSettings extends PreferenceActivity
{
    static final String TAG = "KeyFlinger";

	@Override protected void onCreate(Bundle savedInstanceState)
    {
		super.onCreate(savedInstanceState);
        
        try
        {
            addPreferencesFromResource(R.xml.preferences);
        }
        catch (ClassCastException e)
        {
            Log.d(TAG, "Failed to load preferences, setting to defaults");
            SharedPreferences sp =
                PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            SharedPreferences.Editor editor = sp.edit();
            editor.clear();
            editor.commit();

            editor = sp.edit();
            editor.putBoolean("longpress", true);
            editor.putInt("touchSlop", 10);
            editor.putInt("doubleTapSlop", 100);
            editor.putInt("minFlingVelocity", 5);
            editor.commit();

            addPreferencesFromResource(R.xml.preferences);
        }
	}
}
