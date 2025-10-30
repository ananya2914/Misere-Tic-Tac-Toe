package com.example.bluetooth


import android.content.Context
import android.util.AttributeSet
import androidx.gridlayout.widget.GridLayout

class SquareGridLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : GridLayout(context, attrs, defStyle) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force height = width to keep the board square
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        val size = measuredWidth
        setMeasuredDimension(size, size)
    }
}