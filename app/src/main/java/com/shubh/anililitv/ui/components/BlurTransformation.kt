package com.shubh.anililitv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation

class BlurTransformation(
    private val context: Context,
    private val radius: Float = 25f,
    private val sampling: Float = 4f
) : Transformation {
    init {
        require(radius in 0f..25f) { "radius must be in [0, 25]." }
        require(sampling > 0) { "sampling must be > 0." }
    }

    override val cacheKey: String = "BlurTransformation(radius=$radius,sampling=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val scaledWidth = (input.width / sampling).toInt().coerceAtLeast(1)
        val scaledHeight = (input.height / sampling).toInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(scaledWidth, scaledHeight, input.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.scale(1f / sampling, 1f / sampling)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(input, 0f, 0f, paint)

        var rs: RenderScript? = null
        try {
            rs = RenderScript.create(context)
            val inputAlloc = Allocation.createFromBitmap(rs, output)
            val outputAlloc = Allocation.createTyped(rs, inputAlloc.type)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(radius)
            blur.setInput(inputAlloc)
            blur.forEach(outputAlloc)
            outputAlloc.copyTo(output)
            inputAlloc.destroy()
            outputAlloc.destroy()
            blur.destroy()
        } finally {
            rs?.destroy()
        }

        return output
    }
}
