package com.lagradost.cloudstream3.ui.settings

object Globals {
    var beneneCount = 0

    const val PHONE: Int = 0b001
    const val TV: Int = 0b010
    const val EMULATOR: Int = 0b100

    // Desktop is not a phone, TV, or emulator, so none of the flags match.
    private const val layoutId = 0

    fun isLayout(flags: Int): Boolean {
        return (layoutId and flags) != 0
    }

    fun isLandscape(): Boolean = false
}
