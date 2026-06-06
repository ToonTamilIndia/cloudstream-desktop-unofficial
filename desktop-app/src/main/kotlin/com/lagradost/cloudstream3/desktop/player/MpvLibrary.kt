package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.logging.AppLogger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface MpvLibrary : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): String?
    fun mpv_command_string(ctx: Pointer, args: String): Int
    fun mpv_terminate_destroy(handle: Pointer)

    companion object {
        val INSTANCE: MpvLibrary by lazy {
            val targets = listOf("libmpv-2", "mpv-2", "mpv-1", "mpv", "libmpv", "libmpv.so.1", "libmpv.so.2", "mpv-3.dll")
            var loaded: MpvLibrary? = null
            for (target in targets) {
                try {
                    loaded = Native.load(target, MpvLibrary::class.java) as MpvLibrary
                    AppLogger.i("Successfully loaded native mpv library: $target")
                    break
                } catch (e: UnsatisfiedLinkError) {
                    // Try next
                } catch (e: IllegalArgumentException) {
                    // Try next
                }
            }
            loaded ?: throw RuntimeException("Failed to load native MPV library. Please ensure mpv is installed and in your system PATH.")
        }
    }
}
