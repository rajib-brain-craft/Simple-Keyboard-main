package com.simplemobiletools.keyboard.clipboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isPiePlus
import com.simplemobiletools.keyboard.R
import com.simplemobiletools.keyboard.activities.ManageClipboardItemsActivity
import com.simplemobiletools.keyboard.activities.SettingsActivity
import com.simplemobiletools.keyboard.adapters.ClipsKeyboardAdapter
import com.simplemobiletools.keyboard.databinding.KeyboardLayoutBinding
import com.simplemobiletools.keyboard.extensions.clipsDB
import com.simplemobiletools.keyboard.extensions.config
import com.simplemobiletools.keyboard.extensions.getCurrentClip
import com.simplemobiletools.keyboard.extensions.getStrokeColor
import com.simplemobiletools.keyboard.interfaces.RefreshClipsListener
import com.simplemobiletools.keyboard.models.Clip
import com.simplemobiletools.keyboard.models.ClipsSectionLabel
import com.simplemobiletools.keyboard.models.ListItem
import com.simplemobiletools.keyboard.utils.Utils
import com.simplemobiletools.keyboard.views.MyKeyboardView

class ClipboardManager(
    private val keyboardLayoutBinding: KeyboardLayoutBinding,
    private val onClipboardClipSelectionListener: OnClipboardClipSelectionListener
) : MyKeyboardView.OnNewKeyboardSetListener, MyKeyboardView.OnVisibilityChangedListener {

    init {
        keyboardLayoutBinding.apply {
            settingsCog.setOnLongClickListener { getContext().toast(R.string.settings); true; }
            settingsCog.setOnClickListener {
                vibrateIfNeeded()
                Intent(getContext(), SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    getContext().startActivity(this)
                }
            }

            clipboardPinnedItems.setOnLongClickListener { getContext().toast(R.string.clipboard); true; }
            clipboardPinnedItems.setOnClickListener {
                vibrateIfNeeded()
                openClipboardManager()
            }

            clipboardClearBtn.setOnLongClickListener { getContext().toast(R.string.clear_clipboard_data); true; }
            clipboardClearBtn.setOnClickListener {
                vibrateIfNeeded()
                clearClipboardContent()
                toggleClipboardVisibility(false)
            }

            val clipboardManager = (getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            clipboardManager.addPrimaryClipChangedListener {
                val clipboardContent = clipboardManager.primaryClip?.getItemAt(0)?.text?.trim()
                if (clipboardContent?.isNotEmpty() == true) {
                    handleClipboard()
                }
                setupStoredClips()
            }

            clipboardManagerCloseBtn.setOnClickListener {
                vibrateIfNeeded()
                closeClipboardManager()
            }

            clipboardManagerManageBtn.setOnLongClickListener { getContext().toast(R.string.manage_clipboard_items); true; }
            clipboardManagerManageBtn.setOnClickListener {
                Intent(getContext(), ManageClipboardItemsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    getContext().startActivity(this)
                }
            }

            keyboardView.onNewKeyboardSetListener = this@ClipboardManager
            keyboardView.onVisibilityChangedListener = this@ClipboardManager
        }
    }

    fun closeClipboardManager() {
        keyboardLayoutBinding.clipboardManagerHolder.beGone()
    }

    fun openClipboardManager() {
        keyboardLayoutBinding.clipboardManagerHolder.beVisible()
        setupStoredClips()
    }

    /**
     * @see com.simplemobiletools.keyboard.views.MyKeyboardView listeners
     * */
    override fun onNewKeyboardSet() {
        closeClipboardManager()
    }

    override fun onVisibilityChanged(visibility: Int) {
        closeClipboardManager()
        if (visibility == View.VISIBLE) {
            keyboardLayoutBinding.apply {
                val mTextColor = keyboardView.mTextColor
                val mBackgroundColor = keyboardView.mBackgroundColor
                val strokeColor = getContext().getStrokeColor()

                val toolbarColor = if (getContext().config.isUsingSystemTheme) {
                    getContext().resources.getColor(R.color.you_keyboard_toolbar_color, getContext().theme)
                } else {
                    mBackgroundColor.darkenColor()
                }

                val rippleBg = getContext().resources.getDrawable(R.drawable.clipboard_background, getContext().theme) as RippleDrawable
                val layerDrawable = rippleBg.findDrawableByLayerId(R.id.clipboard_background_holder) as LayerDrawable
                layerDrawable.findDrawableByLayerId(R.id.clipboard_background_stroke).applyColorFilter(strokeColor)
                layerDrawable.findDrawableByLayerId(R.id.clipboard_background_shape).applyColorFilter(mBackgroundColor)

                val wasDarkened = mBackgroundColor != mBackgroundColor.darkenColor()
                topKeyboardDivider.beGoneIf(wasDarkened)
                topKeyboardDivider.background = ColorDrawable(strokeColor)

                toolbarHolder.background = ColorDrawable(toolbarColor)
                clipboardValue.apply {
                    background = rippleBg
                    setTextColor(mTextColor)
                    setLinkTextColor(mTextColor)
                }

                settingsCog.applyColorFilter(mTextColor)
                clipboardPinnedItems.applyColorFilter(mTextColor)
                clipboardClearBtn.applyColorFilter(mTextColor)

                topClipboardDivider.beGoneIf(wasDarkened)
                topClipboardDivider.background = ColorDrawable(strokeColor)
                clipboardManagerHolder.background = ColorDrawable(toolbarColor)

                clipboardManagerCloseBtn.applyColorFilter(mTextColor)
                clipboardManagerManageBtn.applyColorFilter(mTextColor)

                clipboardManagerLabel.setTextColor(mTextColor)
                clipboardContentPlaceholder1.setTextColor(mTextColor)
                clipboardContentPlaceholder2.setTextColor(mTextColor)

                setupStoredClips()
            }
        }

    }


    private fun getContext() = keyboardLayoutBinding.root.context

    private fun vibrateIfNeeded() {
        Utils.vibrateIfNeeded(keyboardLayoutBinding.keyboardView)
    }

    private fun handleClipboard() {
        keyboardLayoutBinding.apply {
            if (/*mToolbarHolder != null && mPopupParent.id != R.id.popUpKeyboardView*/true) {
                val clipboardContent = getContext().getCurrentClip()
                if (clipboardContent?.isNotEmpty() == true) {
                    clipboardValue.apply {
                        text = clipboardContent
                        removeUnderlines()
                        setOnClickListener {
                            onClipboardClipSelectionListener.onClipboardClipSelected(clipboardContent.toString())
                            vibrateIfNeeded()
                        }
                    }
                    toggleClipboardVisibility(true)
                } else {
                    hideClipboardViews()
                }
            } else {
                hideClipboardViews()
            }
        }
    }

    private fun hideClipboardViews() {
        keyboardLayoutBinding.apply {
            clipboardValueHolder.beGone()
            clipboardValueHolder.alpha = 0f
            clipboardClearBtn.beGone()
            clipboardClearBtn.alpha = 0f
        }
    }

    private fun clearClipboardContent() {
        val clipboardManager = (getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        if (isPiePlus()) {
            clipboardManager.clearPrimaryClip()
        } else {
            val clip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(clip)
        }
    }

    private fun toggleClipboardVisibility(show: Boolean) {
        keyboardLayoutBinding.apply {
            if ((show && clipboardValueHolder.alpha == 0f) || (!show && clipboardValueHolder.alpha == 1f)) {
                val newAlpha = if (show) 1f else 0f
                val animations = ArrayList<ObjectAnimator>()
                val clipboardValueAnimation = ObjectAnimator.ofFloat(clipboardValueHolder, "alpha", newAlpha)
                animations.add(clipboardValueAnimation)

                val clipboardClearAnimation = ObjectAnimator.ofFloat(clipboardClearBtn, "alpha", newAlpha)
                animations.add(clipboardClearAnimation)

                val animSet = AnimatorSet()
                animSet.playTogether(*animations.toTypedArray())
                animSet.duration = 150
                animSet.interpolator = AccelerateInterpolator()
                animSet.doOnStart {
                    if (show) {
                        clipboardValueHolder.beVisible()
                        clipboardClearBtn.beVisible()
                    }
                }
                animSet.doOnEnd {
                    if (!show) {
                        clipboardValueHolder.beGone()
                        clipboardClearBtn.beGone()
                    }
                }
                animSet.start()
            }
        }
    }

    private fun setupStoredClips() {
        ensureBackgroundThread {
            val clips = ArrayList<ListItem>()
            val clipboardContent = getContext().getCurrentClip()

            val pinnedClips = getContext().clipsDB.getClips()
            val isCurrentClipPinnedToo = pinnedClips.any { clipboardContent?.isNotEmpty() == true && it.value.trim() == clipboardContent }

            if (!isCurrentClipPinnedToo && clipboardContent?.isNotEmpty() == true) {
                val section = ClipsSectionLabel(getContext().getString(R.string.clipboard_current), true)
                clips.add(section)

                val clip = Clip(-1, clipboardContent)
                clips.add(clip)
            }

            if (!isCurrentClipPinnedToo && clipboardContent?.isNotEmpty() == true) {
                val section = ClipsSectionLabel(getContext().getString(R.string.clipboard_pinned), false)
                clips.add(section)
            }

            clips.addAll(pinnedClips)
            Handler(Looper.getMainLooper()).post {
                setupClipsAdapter(clips)
            }
        }
    }

    private fun setupClipsAdapter(clips: ArrayList<ListItem>) {
        keyboardLayoutBinding.apply {
            clipboardContentPlaceholder1.beVisibleIf(clips.isEmpty())
            clipboardContentPlaceholder2.beVisibleIf(clips.isEmpty())
            clipboardClipsList.beVisibleIf(clips.isNotEmpty())

            val refreshClipsListener = object : RefreshClipsListener {
                override fun refreshClips() {
                    setupStoredClips()
                }
            }

            val adapter = ClipsKeyboardAdapter(getContext(), clips, refreshClipsListener) { clip ->
                onClipboardClipSelectionListener.onClipboardClipSelected(clip.value)
                vibrateIfNeeded()
            }

            clipboardClipsList.adapter = adapter
        }
    }

    interface OnClipboardClipSelectionListener {
        fun onClipboardClipSelected(clipText: String)
    }
}
