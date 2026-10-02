package com.example.screentranslator

import android.content.Context
import android.graphics.*
import android.view.View

/** Kept as a transparent compatibility layer. The new primary UI is the side panel. */
class OverlayView(context: Context) : View(context) {
    fun setBlocks(@Suppress("UNUSED_PARAMETER") blocks: List<Block>) = invalidate()
    fun clear() = invalidate()
}

data class Block(val rect: Rect, val original: String, val translation: String)
