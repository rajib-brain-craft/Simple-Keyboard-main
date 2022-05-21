package com.simplemobiletools.keyboard.views

import android.content.Context
import android.view.View

class TestView(context: Context): View(context) {
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
    }
}
