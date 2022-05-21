package com.simplemobiletools.keyboard.services

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.InputType.TYPE_CLASS_DATETIME
import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_PHONE
import android.text.InputType.TYPE_MASK_CLASS
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.EditorInfo.IME_ACTION_NONE
import android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
import android.view.inputmethod.EditorInfo.IME_MASK_ACTION
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.simplemobiletools.keyboard.R
import com.simplemobiletools.keyboard.clipboard.ClipboardManager
import com.simplemobiletools.keyboard.databinding.KeyboardLayoutBinding
import com.simplemobiletools.keyboard.extensions.config
import com.simplemobiletools.keyboard.helpers.*
import com.simplemobiletools.keyboard.views.MyKeyboardView


// based on https://www.androidauthority.com/lets-build-custom-keyboard-android-832362/
class SimpleKeyboardIME : InputMethodService(),
    MyKeyboardView.OnKeyboardActionListener, ClipboardManager.OnClipboardClipSelectionListener {
    private var SHIFT_PERM_TOGGLE_SPEED = 500   // how quickly do we have to double-tap shift to enable permanent caps lock
    private val KEYBOARD_LETTERS = 0
    private val KEYBOARD_SYMBOLS = 1
    private val KEYBOARD_SYMBOLS_SHIFT = 2

    private var keyboard: MyKeyboard? = null
    private var lastShiftPressTS = 0L
    private var keyboardMode = KEYBOARD_LETTERS
    private var inputTypeClass = InputType.TYPE_CLASS_TEXT
    private var enterKeyType = IME_ACTION_NONE
    private var switchToLetters = false

    private var keyboardLayoutBinding: KeyboardLayoutBinding? = null

    private var searchBoxEditorInfo: EditorInfo? = null
    private var searchBoxInputConnection: InputConnection? = null

    private var clipboardManager: ClipboardManager? = null

    override fun onInitializeInterface() {
        super.onInitializeInterface()
        keyboard = MyKeyboard(this, getKeyboardLayoutXML(), enterKeyType)
    }

    override fun onCreateInputView(): View {
        keyboardLayoutBinding = KeyboardLayoutBinding.inflate(layoutInflater)
        keyboardLayoutBinding!!.apply {
            keyboardView.setKeyboard(keyboard!!)
            keyboardView.onKeyboardActionListener = this@SimpleKeyboardIME

            searchCancelBtn.setOnClickListener {
                if (searchBox.isFocused) searchBox.clearFocus()
            }

            searchBox.setOnFocusChangeListener { view, focused ->
                if (focused) {
                    searchBoxEditorInfo = EditorInfo()
                    searchBoxInputConnection = searchBox.onCreateInputConnection(searchBoxEditorInfo)
                    searchBoxEditorInfo!!.imeOptions = EditorInfo.IME_ACTION_SEARCH
                    startInput(searchBoxEditorInfo, false)
                } else {
                    searchBoxEditorInfo = null
                    searchBoxInputConnection = null
                    startInput(currentInputEditorInfo, false)
                }
            }

            searchBox.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    Toast.makeText(this@SimpleKeyboardIME, "Search Clicked!", Toast.LENGTH_SHORT).show()
                    true
                } else false
            }

            clipboardManager = ClipboardManager(this, this@SimpleKeyboardIME)

            return this.root
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (isSearchBoxFocused()) {
            startInput(attribute, restarting)
        }
    }

    private fun startInput(attribute: EditorInfo?, restarting: Boolean) {
        inputTypeClass = attribute!!.inputType and TYPE_MASK_CLASS
        enterKeyType = attribute.imeOptions and (IME_MASK_ACTION or IME_FLAG_NO_ENTER_ACTION)

        val keyboardXml = when (inputTypeClass) {
            TYPE_CLASS_NUMBER, TYPE_CLASS_DATETIME, TYPE_CLASS_PHONE -> {
                keyboardMode = KEYBOARD_SYMBOLS
                R.xml.keys_symbols
            }
            else -> {
                keyboardMode = KEYBOARD_LETTERS
                getKeyboardLayoutXML()
            }
        }

        keyboard = MyKeyboard(this, keyboardXml, enterKeyType)
        keyboardLayoutBinding?.keyboardView!!.setKeyboard(keyboard!!)
        updateShiftKeyState()
    }

    private fun isSearchBoxFocused(): Boolean = keyboardLayoutBinding?.searchBox?.isFocused ?: false

    private fun getInputConnection() = searchBoxInputConnection ?: currentInputConnection

    private fun getEditorInfo() = searchBoxEditorInfo ?: currentInputEditorInfo

    private fun updateShiftKeyState() {
        if (keyboardMode == KEYBOARD_LETTERS) {
            val editorInfo = getEditorInfo()
            if (editorInfo != null && editorInfo.inputType != InputType.TYPE_NULL && keyboard?.mShiftState != SHIFT_ON_PERMANENT) {
                if (getInputConnection().getCursorCapsMode(editorInfo.inputType) != 0) {
                    keyboard?.setShifted(SHIFT_ON_ONE_CHAR)
                    keyboardLayoutBinding?.keyboardView!!.invalidateAllKeys()
                }
            }
        }
    }

    /**
     * ClipboardManager callbacks
     */
    override fun onClipboardClipSelected(clipText: String) {
        getInputConnection()?.commitText(clipText, 0)
    }


    override fun onPress(primaryCode: Int) {
        if (primaryCode != 0) {
            keyboardLayoutBinding?.keyboardView!!.vibrateIfNeeded()
        }
    }

    override fun onKey(code: Int) {
        val inputConnection = getInputConnection()
        if (keyboard == null || inputConnection == null) {
            return
        }

        if (code != MyKeyboard.KEYCODE_SHIFT) {
            lastShiftPressTS = 0
        }

        when (code) {
            MyKeyboard.KEYCODE_DELETE -> {
                if (keyboard!!.mShiftState == SHIFT_ON_ONE_CHAR) {
                    keyboard!!.mShiftState = SHIFT_OFF
                }

                val selectedText = inputConnection.getSelectedText(0)
                if (TextUtils.isEmpty(selectedText)) {
                    inputConnection.deleteSurroundingText(1, 0)
                } else {
                    inputConnection.commitText("", 1)
                }
                keyboardLayoutBinding?.keyboardView!!.invalidateAllKeys()
            }
            MyKeyboard.KEYCODE_SHIFT -> {
                if (keyboardMode == KEYBOARD_LETTERS) {
                    when {
                        keyboard!!.mShiftState == SHIFT_ON_PERMANENT -> keyboard!!.mShiftState = SHIFT_OFF
                        System.currentTimeMillis() - lastShiftPressTS < SHIFT_PERM_TOGGLE_SPEED -> keyboard!!.mShiftState = SHIFT_ON_PERMANENT
                        keyboard!!.mShiftState == SHIFT_ON_ONE_CHAR -> keyboard!!.mShiftState = SHIFT_OFF
                        keyboard!!.mShiftState == SHIFT_OFF -> keyboard!!.mShiftState = SHIFT_ON_ONE_CHAR
                    }

                    lastShiftPressTS = System.currentTimeMillis()
                } else {
                    val keyboardXml = if (keyboardMode == KEYBOARD_SYMBOLS) {
                        keyboardMode = KEYBOARD_SYMBOLS_SHIFT
                        R.xml.keys_symbols_shift
                    } else {
                        keyboardMode = KEYBOARD_SYMBOLS
                        R.xml.keys_symbols
                    }
                    keyboard = MyKeyboard(this, keyboardXml, enterKeyType)
                    keyboardLayoutBinding?.keyboardView!!.setKeyboard(keyboard!!)
                }
                keyboardLayoutBinding?.keyboardView!!.invalidateAllKeys()
            }
            MyKeyboard.KEYCODE_ENTER -> {
                val imeOptionsActionId = getImeOptionsActionId()
                if (imeOptionsActionId != IME_ACTION_NONE) {
                    inputConnection.performEditorAction(imeOptionsActionId)
                } else {
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            MyKeyboard.KEYCODE_MODE_CHANGE -> {
                val keyboardXml = if (keyboardMode == KEYBOARD_LETTERS) {
                    keyboardMode = KEYBOARD_SYMBOLS
                    R.xml.keys_symbols
                } else {
                    keyboardMode = KEYBOARD_LETTERS
                    getKeyboardLayoutXML()
                }
                keyboard = MyKeyboard(this, keyboardXml, enterKeyType)
                keyboardLayoutBinding?.keyboardView!!.setKeyboard(keyboard!!)
            }
            else -> {
                var codeChar = code.toChar()
                if (Character.isLetter(codeChar) && keyboard!!.mShiftState > SHIFT_OFF) {
                    codeChar = Character.toUpperCase(codeChar)
                }

                // If the keyboard is set to symbols and the user presses space, we usually should switch back to the letters keyboard.
                // However, avoid doing that in cases when the EditText for example requires numbers as the input.
                // We can detect that by the text not changing on pressing Space.
                if (keyboardMode != KEYBOARD_LETTERS && code == MyKeyboard.KEYCODE_SPACE) {
                    val originalText = inputConnection.getExtractedText(ExtractedTextRequest(), 0)?.text ?: return
                    inputConnection.commitText(codeChar.toString(), 1)
                    val newText = inputConnection.getExtractedText(ExtractedTextRequest(), 0).text
                    switchToLetters = originalText != newText
                } else {
                    inputConnection.commitText(codeChar.toString(), 1)
                }

                if (keyboard!!.mShiftState == SHIFT_ON_ONE_CHAR && keyboardMode == KEYBOARD_LETTERS) {
                    keyboard!!.mShiftState = SHIFT_OFF
                    keyboardLayoutBinding?.keyboardView!!.invalidateAllKeys()
                }
            }
        }

        if (code != MyKeyboard.KEYCODE_SHIFT) {
            updateShiftKeyState()
        }
    }

    override fun onActionUp() {
        if (switchToLetters) {
            keyboardMode = KEYBOARD_LETTERS
            keyboard = MyKeyboard(this, getKeyboardLayoutXML(), enterKeyType)

            val editorInfo = getEditorInfo()
            if (editorInfo != null && editorInfo.inputType != InputType.TYPE_NULL && keyboard?.mShiftState != SHIFT_ON_PERMANENT) {
                if (getInputConnection().getCursorCapsMode(editorInfo.inputType) != 0) {
                    keyboard?.setShifted(SHIFT_ON_ONE_CHAR)
                }
            }

            keyboardLayoutBinding?.keyboardView!!.setKeyboard(keyboard!!)
            switchToLetters = false
        }
    }

    override fun moveCursorLeft() {
        moveCursor(false)
    }

    override fun moveCursorRight() {
        moveCursor(true)
    }

    override fun onText(text: String) {
        getInputConnection()?.commitText(text, 0)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (newSelStart == newSelEnd) {
            clipboardManager?.closeClipboardManager()
        }
    }

    private fun moveCursor(moveRight: Boolean) {
        val extractedText = getInputConnection()?.getExtractedText(ExtractedTextRequest(), 0) ?: return
        var newCursorPosition = extractedText.selectionStart
        newCursorPosition = if (moveRight) {
            newCursorPosition + 1
        } else {
            newCursorPosition - 1
        }

        getInputConnection()?.setSelection(newCursorPosition, newCursorPosition)
    }

    private fun getImeOptionsActionId(): Int {
        return if (getEditorInfo().imeOptions and IME_FLAG_NO_ENTER_ACTION != 0) {
            IME_ACTION_NONE
        } else {
            getEditorInfo().imeOptions and IME_MASK_ACTION
        }
    }

    private fun getKeyboardLayoutXML(): Int {
        return when (baseContext.config.keyboardLanguage) {
            LANGUAGE_FRENCH -> R.xml.keys_letters_french
            LANGUAGE_RUSSIAN -> R.xml.keys_letters_russian
            LANGUAGE_ENGLISH_QWERTZ -> R.xml.keys_letters_english_qwertz
            LANGUAGE_SPANISH -> R.xml.keys_letters_spanish_qwerty
            LANGUAGE_GERMAN -> R.xml.keys_letters_german
            else -> R.xml.keys_letters_english_qwerty
        }
    }
}
