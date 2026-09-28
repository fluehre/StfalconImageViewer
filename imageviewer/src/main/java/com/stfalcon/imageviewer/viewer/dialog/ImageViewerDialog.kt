/*
 * Copyright 2018 stfalcon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stfalcon.imageviewer.viewer.dialog

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.stfalcon.imageviewer.R
import com.stfalcon.imageviewer.listeners.OnDismissListener
import com.stfalcon.imageviewer.listeners.OnImageChangeListener
import com.stfalcon.imageviewer.loader.ImageLoader
import com.stfalcon.imageviewer.loader.OverlayLoader
import com.stfalcon.imageviewer.viewer.builder.BuilderData
import com.stfalcon.imageviewer.viewer.view.ImageViewerView
import kotlin.math.max

class ImageViewerDialog<T> : DialogFragment() {

    lateinit var viewerView: ImageViewerView<T>

    private lateinit var dialog: AlertDialog
    private var animateOpen = true
    private lateinit var builderData: BuilderData<T>

    /*
    * The viewer only intercepted back via AlertDialog.setOnKeyListener { onDialogKeyEvent(...) },
    * which never fires under the predictive-back system.
    * As a result the dialog dismissed directly and viewerView.close()
    * was never called, so the source cell’s visibility was never restored — it stayed INVISIBLE,
    * exposing the cell’s gray background (@color/gray_40_static).*/
    private val onBackInvokedCallback: OnBackInvokedCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedCallback { handleBackPressed() }
        } else {
            null
        }

    private val dialogStyle: Int
        get() = if (builderData.shouldStatusBarHide)
            R.style.ImageViewerDialog_NoStatusBar
        else
            R.style.ImageViewerDialog_Default

    companion object {
        private const val builderDataKey = "BuilderDataKey"
        fun <T> newInstance(builderData: BuilderData<T>): ImageViewerDialog<T> {
            val args = Bundle()
            args.putSerializable(builderDataKey, builderData)
            val f = ImageViewerDialog<T>()
            f.arguments = args
            return f
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        builderData = arguments?.getSerializable(builderDataKey) as BuilderData<T>
        setupViewerView()
        dialog = AlertDialog
            .Builder(requireContext(), dialogStyle)
            .setView(viewerView)
            .setOnKeyListener { _, keyCode, event -> onDialogKeyEvent(keyCode, event) }
            .create()
            .apply {
                setOnShowListener {
                    viewerView.open(builderData.transitionView, animateOpen)
                    registerOnBackInvokedCallback()
                }
                setOnDismissListener {
                    unregisterOnBackInvokedCallback()
                    ((targetFragment ?: activity) as? OnDismissListener)?.onDismiss()
                }
            }
        return dialog
    }

    override fun onResume() {
        super.onResume()
        setupViewerView(onResume = true)
    }

    fun show(fragmentManager: FragmentManager, animate: Boolean) {
        animateOpen = animate
        show(fragmentManager, "")
    }

    fun close() {
        viewerView.close()
    }

    override fun dismiss() {
        dialog.dismiss()
    }

    fun updateImages(images: List<T>) {
        viewerView.updateImages(images)
    }

    fun getCurrentPosition(): Int =
        viewerView.currentPosition

    fun setCurrentPosition(position: Int): Int {
        viewerView.currentPosition = position
        return viewerView.currentPosition
    }

    fun updateTransitionImage(imageView: ImageView?) {
        viewerView.updateTransitionImage(imageView)
    }

    private fun onDialogKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            !event.isCanceled
        ) {
            handleBackPressed()
            return true
        }
        return false
    }

    private fun handleBackPressed() {
        if (viewerView.isScaled) {
            viewerView.resetScale()
        } else {
            viewerView.close()
        }
    }

    private fun registerOnBackInvokedCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = onBackInvokedCallback ?: return
            dialog.onBackInvokedDispatcher
                .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        }
    }

    private fun unregisterOnBackInvokedCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = onBackInvokedCallback ?: return
            dialog.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }

    private fun setupViewerView(onResume: Boolean = false) {
        if (!::viewerView.isInitialized) {
            viewerView = ImageViewerView(requireContext())
        }
        viewerView.apply {
            isZoomingAllowed = builderData.isZoomingAllowed
            isSwipeToDismissAllowed = builderData.isSwipeToDismissAllowed

            containerPadding = builderData.containerPaddingPixels
            imagesMargin = builderData.imageMarginPixels
            if (overlayView == null) {
                overlayView = ((targetFragment ?: activity) as? OverlayLoader<T>)?.loadOverlayFor(max(viewerView.currentPosition, builderData.startPosition),
                    this@ImageViewerDialog)
            } else {
                ((targetFragment ?: activity) as? OverlayLoader<T>)?.notifyExistingOverlay(overlayView,
                    this@ImageViewerDialog)
            }
            imageFullFocusEnabled = builderData.imageFullFocusEnabled

            if (!onResume) {
                setBackgroundColor(builderData.backgroundColor)
            }
            val imageLoader = (targetFragment ?: activity) as? ImageLoader<T>
            if (imageLoader != null) {
                val images = if (onResume) viewerView.images else builderData.images
                val position = if (onResume) getCurrentPosition() else builderData.startPosition
                setImages(images, position, imageLoader)
            }

            onPageChange = { position -> ((targetFragment ?: activity) as? OnImageChangeListener)?.onImageChange(position) }
            onDismiss = {
                dialog.dismiss()
                ((targetFragment ?: activity) as? OnDismissListener)?.onDismiss()
            }
        }
    }
}
