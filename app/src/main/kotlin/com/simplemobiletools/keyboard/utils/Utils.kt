package com.simplemobiletools.keyboard.utils

import android.view.View
import com.simplemobiletools.commons.extensions.performHapticFeedback
import com.simplemobiletools.keyboard.extensions.config

class Utils {
    companion object {
        fun vibrateIfNeeded(view: View) {
            if (view.context.config.vibrateOnKeypress) {
                view.performHapticFeedback()
            }
        }
    }
}
