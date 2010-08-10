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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.preference.PreferenceManager;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class KeyFlinger extends InputMethodService 
    implements KeyboardView.OnKeyboardActionListener
{
    static final boolean DEBUG = true;
    static final String TAG = "KeyFlinger";

    public final static int KEY_ESCAPE = 27;
    public final static String DEF_KEYBOARD_NAME = "kb_qwerty";
    
    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;

    private LatinKeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private LatinKeyboard mKeyboardSymbols;
    private LatinKeyboard mKeyboardPhone;
    private LatinKeyboard mKeyboardPhoneSymbols;
    private LatinKeyboard[] mKeyboards;
    private Map<String,LatinKeyboard> mKeyboardMap;
    private int[] mKeyboardResList =
    {
       R.xml.kb_qwerty,
       R.xml.kb_st_outside,
       R.xml.kb_st_center,
       R.xml.kb_multitouch
    };
    private Map<String,Integer> mKeyboardResMap;
    private LatinKeyboard mCurKeyboard;
    private EditorInfo mInputAttribute;
    
    private String mWordSeparators;

    private boolean mIgnoreNextKey = false;
    private boolean mIsControlSet = false;

    // Public config info
    public String mKeyboardName = DEF_KEYBOARD_NAME;
    public boolean mLongPressEnabled = true;
    public int mTouchSlop = 10;
    public int mDoubleTapSlop = 100;
    public int mMinFlingVelocity = 5;

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate()
    {
		Log.d(TAG, "onCreate");
        super.onCreate();

        Resources res = getResources();
        mWordSeparators = res.getString(R.string.word_separators);

        mKeyboardResMap = new HashMap<String,Integer>();
        for (int i = 0; i < mKeyboardResList.length; ++i)
        {
            int r = mKeyboardResList[i];
            mKeyboardResMap.put(res.getResourceEntryName(r), r);
        }

        mKeyboards = new LatinKeyboard[mKeyboardResList.length];
        mKeyboardMap = new HashMap<String,LatinKeyboard>();
        for (int i = 0; i < mKeyboardResList.length; ++i)
        {
            int r = mKeyboardResList[i];
            mKeyboards[i] = new LatinKeyboard(this, r);
            mKeyboardMap.put(res.getResourceEntryName(r), mKeyboards[i]);
        }
        mKeyboardSymbols = new LatinKeyboard(this, R.xml.kb_symbols);
        mKeyboardPhone = new LatinKeyboard(this, R.xml.kb_phone);
        mKeyboardPhoneSymbols = new LatinKeyboard(this, R.xml.kb_phone_symbols);
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface()
    {
        if (mKeyboards[0] != null)
        {
            // Configuration changes can happen after the keyboard gets
            // recreated, so we need to be able to re-build the keyboards if the
            // available space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth)
            {
                return;
            }
            mLastDisplayWidth = displayWidth;
        }
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView()
    {
        mInputView =
            (LatinKeyboardView) getLayoutInflater().inflate(R.layout.input,
                                                            null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyFlinger(this);
        setConfigedKeyboard();
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView()
    {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    // @Override public boolean onEvaluateFullscreenMode()
    // {
    //     return true;
    // }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting)
    {
        onStartInputFlinger(attribute, restarting, true);
    }
    
    public void onStartInputFlinger(EditorInfo attribute, boolean restarting,
                                    boolean reset_kb)
    {
		Log.d(TAG, "onStartInput");
        super.onStartInput(attribute, restarting);

        mInputAttribute = attribute;
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();
        
        if (!restarting)
        {
            // Clear shift states.
            mMetaState = 0;
        }
        
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        if (reset_kb)
        {
            setConfigedKeyboard();
        }

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS)
        {
        case EditorInfo.TYPE_CLASS_NUMBER:
        case EditorInfo.TYPE_CLASS_DATETIME:
            // Numbers and dates default to the symbols keyboard, with
            // no extra features.
            break;
                
        case EditorInfo.TYPE_CLASS_PHONE:
            // Phones will also default to the symbols keyboard, though
            // often you will want to have a dedicated phone keyboard.
            break;
                
        case EditorInfo.TYPE_CLASS_TEXT:
            // This is general text editing.  We will default to the
            // normal alphabetic keyboard, and assume that we should
            // be doing predictive text (showing candidates as the
            // user types).
            mPredictionOn = true;
                
            // We now look for a few special variations of text that will
            // modify our behavior.
            int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;
            if ((variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD) ||
                (variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
            {
                // Do not display predictions / what the user is typing
                // when they are entering a password.
                mPredictionOn = false;
            }
                
            if ((variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) || 
                (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) ||
                (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER))
            {
                // Our predictions are not useful for e-mail addresses
                // or URIs.
                mPredictionOn = false;
            }
                
            if ((attribute.inputType &
                 EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0)
            {
                // If this is an auto-complete text view, then our predictions
                // will not be shown and instead we will allow the editor
                // to supply their own.  We only show the editor's
                // candidates when in fullscreen mode, otherwise relying
                // own it displaying its own UI.
                mPredictionOn = false;
                mCompletionOn = isFullscreenMode();
            }
                
            // We also want to look at the current state of the editor
            // to decide whether our alphabetic keyboard should start out
            // shifted.
            updateShiftKeyState(attribute);
            break;
                
        default:
            // For all unknown input types, default to the alphabetic
            // keyboard with no special features.
            // setConfigedKeyboard();
            Log.d(TAG, "Starting type DEFAULT");
            updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        getCurKeyboard().setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput()
    {
        Log.d(TAG, "onFinishInput");

        super.onFinishInput();
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        setConfigedKeyboard();
        if (mInputView != null)
        {
            mInputView.closing();
        }
    }
    
    @Override public void onStartInputView(EditorInfo attribute,
                                           boolean restarting)
    {
        Log.d(TAG, "onStartInputView");
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
        loadPrefs();
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                            int newSelStart, int newSelEnd,
                                            int candidatesStart,
                                            int candidatesEnd)
    {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if ((mComposing.length() > 0) &&
            ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd)))
        {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null)
            {
                ic.finishComposingText();
            }
        }
    }
    
    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions)
    {
        if (mCompletionOn)
        {
            mCompletions = completions;
            if (completions == null)
            {
                setSuggestions(null, false, false);
                return;
            }
            
            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++)
            {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }
    
    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event)
    {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                                                      keyCode, event);
        int c =
            event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null)
        {
            return false;
        }
        
        // boolean dead = false;
      
        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0)
        {
            // dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }
        
        if (mComposing.length() > 0)
        {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);
            
            if (composed != 0)
            {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }
        
        onKey(c, null);
        
        return true;
    }
    
    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.d(TAG, String.format("onKeyDown: %d : %s", keyCode,
                                 event.toString()));
        switch (keyCode)
        {
        case KeyEvent.KEYCODE_BACK:
            // The InputMethodService already takes care of the back
            // key for us, to dismiss the input method if it is shown.
            // However, our keyboard could be showing a pop-up window
            // that back should dismiss, so we first allow it to do that.
            if ((event.getRepeatCount() == 0) && (mInputView != null))
            {
                if (mInputView.handleBack())
                {
                    return true;
                }
            }
            break;
                
        case KeyEvent.KEYCODE_DEL:
            // Special handling of the delete key: if we currently are
            // composing text for the user, we want to modify that instead
            // of let the application to the delete itself.
            if (mComposing.length() > 0)
            {
                onKey(Keyboard.KEYCODE_DELETE, null);
                return true;
            }
            break;
                
        case KeyEvent.KEYCODE_ENTER:
            // Let the underlying text editor always handle these.
            return false;
                
        default:
            // For all other keys, if we want to do transformations on
            // text being entered with a hard keyboard, we need to process
            // it and do the appropriate action.

            if (PROCESS_HARD_KEYS)
            {
                if ((keyCode == KeyEvent.KEYCODE_SPACE) &&
                    ((event.getMetaState() & KeyEvent.META_ALT_ON) != 0))
                {
                    // A silly example: in our input method, Alt+Space
                    // is a shortcut for 'android' in lower case.
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null)
                    {
                        // First, tell the editor that it is no longer in the
                        // shift state, since we are consuming this.
                        ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                        keyDownUp(KeyEvent.KEYCODE_A);
                        keyDownUp(KeyEvent.KEYCODE_N);
                        keyDownUp(KeyEvent.KEYCODE_D);
                        keyDownUp(KeyEvent.KEYCODE_R);
                        keyDownUp(KeyEvent.KEYCODE_O);
                        keyDownUp(KeyEvent.KEYCODE_I);
                        keyDownUp(KeyEvent.KEYCODE_D);
                        // And we consume this event.
                        return true;
                    }
                }
                if (mPredictionOn && translateKeyDown(keyCode, event))
                {
                    return true;
                }
            }
        }
        
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS)
        {
            if (mPredictionOn)
            {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                                                            keyCode, event);
            }
        }
        
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection)
    {
        if (mComposing.length() > 0)
        {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr)
    {
        if ((attr != null) && (mInputView != null) &&
            (getCurKeyboard().isShiftable()))
        {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if ((ei != null) && (ei.inputType != EditorInfo.TYPE_NULL))
            {
                caps = getCurrentInputConnection().getCursorCapsMode(
                    attr.inputType);
            }
            mInputView.setShifted(mCapsLock || (caps != 0));
        }
    }
    
    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code)
    {
        if (Character.isLetter(code))
        {
            return true;
        }
        else
        {
            return false;
        }
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode)
    {
        getCurrentInputConnection().sendKeyEvent(
            new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
            new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode)
    {
        switch (keyCode)
        {
        case '\n':
            keyDownUp(KeyEvent.KEYCODE_ENTER);
            break;
        default:
            if (keyCode >= '0' && keyCode <= '9')
            {
                keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
            }
            else
            {
                getCurrentInputConnection().commitText(
                    String.valueOf((char)keyCode), 1);
            }
            break;
        }
    }

    public void ignoreNextKey()
    {
        Log.d(TAG, "ignoreNextKey()");
        mIgnoreNextKey = true;
    }
    
    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes)
    {
        Log.d(TAG, String.format("onKey: %d : %s", primaryCode,
                                 String.valueOf((char) primaryCode)));
        // if (primaryCode == -10)
        // {
        //     primaryCode = keyCodes[0];
        // }
        if (keyCodes != null)
        {
            for (int i = 0; i < keyCodes.length; ++i)
            {
                if (keyCodes[i] == -1)
                {
                    break;
                }
                Log.d(TAG, String.format("keyCode[%d]=%d", i, keyCodes[i]));
            }
        }

        if (mIgnoreNextKey)
        {
            Log.d(TAG, "mIgnoreNextKey set, ignoring");
            mIgnoreNextKey = false;
            return;
        }
        
        if (isWordSeparator(primaryCode))
        {
            // Handle separator
            if (mComposing.length() > 0)
            {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
        else if (primaryCode == Keyboard.KEYCODE_DELETE)
        {
            handleBackspace();
        }
        else if (primaryCode == Keyboard.KEYCODE_SHIFT)
        {
            handleShift();
        }
        else if (primaryCode == Keyboard.KEYCODE_ALT)
        {
            if (mCurKeyboard == mKeyboardSymbols)
            {
                setConfigedKeyboard();
            }
            else
            {
                mCurKeyboard = mKeyboardSymbols;
                mInputView.setKeyboard(mCurKeyboard);
                onStartInputFlinger(mInputAttribute, true, false);
            }
        }
        else if (primaryCode == Keyboard.KEYCODE_CANCEL)
        {
            handleClose();
            return;
        }
        else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS)
        {
            // Show a menu or somethin'
        }
        else if (primaryCode == LatinKeyboard.KEYCODE_CTL)
        {
            mIsControlSet = !mIsControlSet;
        }
        else if (primaryCode == LatinKeyboard.KEYCODE_ESC)
        {
            handleCharacter(KEY_ESCAPE, keyCodes);
        }
        else if ((primaryCode == Keyboard.KEYCODE_MODE_CHANGE) &&
                 (mInputView != null))
        {
            Log.d(TAG, "Keyboard mode change");
            if (mCurKeyboard == mKeyboardPhone)
            {
                mCurKeyboard = mKeyboardPhoneSymbols;
            }
            else if (mCurKeyboard == mKeyboardPhoneSymbols)
            {
                mCurKeyboard = mKeyboardPhone;
            }
            else
            {
                int i = 0;
                LatinKeyboard current = getCurKeyboard();
                for (; i < mKeyboards.length; ++i)
                {
                    if (current == mKeyboards[i])
                    {
                        ++i;
                        break;
                    }
                }
                if (i >= mKeyboards.length)
                {
                    i = 0;
                }
                mCurKeyboard = mKeyboards[i];
            }
            mInputView.setKeyboard(mCurKeyboard);
            onStartInputFlinger(mInputAttribute, true, false);
            // if (current.isShiftable())
            // {
            //     current.setShifted(false);
            // }
        }
        else
        {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    public void onText(CharSequence text)
    {
        Log.d(TAG, String.format("onText: %s", text.toString()));
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
        {
            return;
        }
        ic.beginBatchEdit();
        if (mComposing.length() > 0)
        {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates()
    {
        if (!mCompletionOn)
        {
            if (mComposing.length() > 0)
            {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            }
            else
            {
                setSuggestions(null, false, false);
            }
        }
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid)
    {
        setCandidatesViewShown(false);
        // if (suggestions != null && suggestions.size() > 0)
        // {
        //     setCandidatesViewShown(true);
        // }
        // else if (isExtractViewShown())
        // {
        //     setCandidatesViewShown(true);
        // }
        // if (mCandidateView != null)
        // {
        //     mCandidateView.setSuggestions(suggestions, completions,
        //                                   typedWordValid);
        // }
    }
    
    private void handleBackspace()
    {
        final int length = mComposing.length();
        if (length > 1)
        {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        }
        else if (length > 0)
        {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        }
        else
        {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift()
    {
        if (mInputView == null)
        {
            return;
        }
        
        if (getCurKeyboard().isShiftable())
        {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes)
    {
        if (isInputViewShown())
        {
            if (mInputView.isShifted())
            {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn)
        {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        }
        else
        {
            if (mIsControlSet)
            {
                // Support CTRL-a through CTRL-z
                if (primaryCode >= 0x61 && primaryCode <= 0x7A)
                    primaryCode -= 0x60;
                // Support CTRL-A through CTRL-_
                else if (primaryCode >= 0x41 && primaryCode <= 0x5F)
                    primaryCode -= 0x40;
                else if (primaryCode == 0x20)
                    primaryCode = 0x00;
                else if (primaryCode == 0x3F)
                    primaryCode = 0x7F;
            }
            getCurrentInputConnection().commitText(
                String.valueOf((char) primaryCode), 1);
        }
        mIsControlSet = false;
    }

    private void handleClose()
    {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock()
    {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now)
        {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        }
        else
        {
            mLastShiftTime = now;
        }
    }
    
    private String getWordSeparators()
    {
        return mWordSeparators;
    }
    
    public boolean isWordSeparator(int code)
    {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate()
    {
        pickSuggestionManually(0);
    }
    
    public void pickSuggestionManually(int index)
    {
        if (mCompletionOn && (mCompletions != null) && (index >= 0) &&
            (index < mCompletions.length))
        {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null)
            {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
        else if (mComposing.length() > 0)
        {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }
    
    public void swipeRight()
    {
    }
    
    public void swipeLeft()
    {
    }

    public void swipeDown()
    {
    }

    public void swipeUp()
    {
    }
    
    public void onPress(int primaryCode)
    {
    }
    
    public void onRelease(int primaryCode)
    {
    }

    public void flingRight(LatinKeyboard.LatinKey key)
    {
		Log.d(TAG ,
              "flingRight: " + key.mDLabels[LatinKeyboard.KEY_INDEX_RIGHT]);
    }
    
    public void flingLeft(LatinKeyboard.LatinKey key)
    {
		Log.d(TAG,
              "flingLeft: " + key.mDLabels[LatinKeyboard.KEY_INDEX_LEFT]);
    }

    public void flingDown(LatinKeyboard.LatinKey key)
    {
		Log.d(TAG,
              "flingDown: " + key.mDLabels[LatinKeyboard.KEY_INDEX_DOWN]);
    }

    public void flingUp(LatinKeyboard.LatinKey key)
    {
		Log.d(TAG,
              "flingUp: " + key.mDLabels[LatinKeyboard.KEY_INDEX_UP]);
    }

    protected void setConfigedKeyboard()
    {
        switch (mInputAttribute.inputType & EditorInfo.TYPE_MASK_CLASS)
        {
        case EditorInfo.TYPE_CLASS_NUMBER:
        case EditorInfo.TYPE_CLASS_DATETIME:
            // Numbers and dates default to the symbols keyboard, with
            // no extra features.
            Log.d(TAG, "Setting type NUMBER/DATETIME");
            break;
                
        case EditorInfo.TYPE_CLASS_PHONE:
            // Phones will also default to the symbols keyboard, though
            // often you will want to have a dedicated phone keyboard.
            Log.d(TAG, "Setting type PHONE");
            if ((mCurKeyboard != mKeyboardPhone) &&
                (mCurKeyboard != mKeyboardPhoneSymbols))
            {
                mCurKeyboard = mKeyboardPhone;
            }
            break;
            
        case EditorInfo.TYPE_CLASS_TEXT:
        default:
            Log.d(TAG, "Setting type TEXT");
            mCurKeyboard = (LatinKeyboard)mKeyboardMap.get(mKeyboardName);
            break;
        }
               
        if (mInputView != null)
        {
            mInputView.setKeyboard(mCurKeyboard);
        }
    }

    public LatinKeyboard getCurKeyboard()
    {
        if (mInputView != null)
        {
            mCurKeyboard = (LatinKeyboard)mInputView.getKeyboard();
        }
        return mCurKeyboard;
    }
    
    protected void loadPrefs()
    {
        SharedPreferences sp =
            PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        try
        {
            mKeyboardName = sp.getString("keyboard", DEF_KEYBOARD_NAME);
            mLongPressEnabled = sp.getBoolean("longpress", true);
            mTouchSlop = Integer.parseInt(sp.getString("touchSlop", "10"));
            mDoubleTapSlop =
                Integer.parseInt(sp.getString("doubleTapSlop", "100"));
            mMinFlingVelocity =
                Integer.parseInt(sp.getString("minFlingVelocity", "5"));
        }
        catch (ClassCastException e)
        {
            Log.d(TAG, "loadPrefs: failed");
            defaultPrefs();
        }
        int ilp = 1;
        if (!mLongPressEnabled)
        {
            ilp = 0;
        }
        Log.d(TAG, String.format("config: ts=%d dts=%d mfs=%d lp=%d", mTouchSlop,
                                 mDoubleTapSlop, mMinFlingVelocity, ilp));
        if (mInputView != null)
        {
            mInputView.setParams(mTouchSlop, mDoubleTapSlop, mMinFlingVelocity,
                                 mLongPressEnabled);
        }
    }

    protected void defaultPrefs()
    {
        Log.d(TAG, "Setting default prefs");
        mLongPressEnabled = true;
        mTouchSlop = 10;
        mDoubleTapSlop = 100;
        mMinFlingVelocity = 5;
    }
}
