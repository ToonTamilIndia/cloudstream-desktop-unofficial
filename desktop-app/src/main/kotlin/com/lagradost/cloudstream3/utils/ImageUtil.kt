package com.lagradost.cloudstream3.utils

sealed class UiImage {
    data class Image(
        val url: String,
        val headers: Map<String, String>? = null
    ) : UiImage()

    data class Drawable(val resId: Int) : UiImage()
    data class Bitmap(val bitmap: Any) : UiImage()
}
