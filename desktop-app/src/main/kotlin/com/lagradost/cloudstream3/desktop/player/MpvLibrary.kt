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
        private interface PosixCLibrary : Library {
            fun setlocale(category: Int, locale: String): String?
        }

        private fun forceNumericCLocale() {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) return
            try {
                val libc = Native.load("c", PosixCLibrary::class.java) as PosixCLibrary
                val result = libc.setlocale(1, "C") // POSIX LC_NUMERIC
                AppLogger.i("Set LC_NUMERIC for libmpv: ${result ?: "failed"}")
            } catch (t: Throwable) {
                AppLogger.w("Could not set LC_NUMERIC=C before loading libmpv: ${t.message}")
            }
        }

        fun installHint(): String = when {
            System.getProperty("os.name").lowercase().contains("win") ->
                "Install a libmpv build and place libmpv-2.dll next to the application, or set CLOUDSTREAM_LIBMPV."
            System.getProperty("os.name").lowercase().contains("mac") ->
                "Install libmpv (for example with Homebrew) or set CLOUDSTREAM_LIBMPV to libmpv.dylib."
            else -> "Install the libmpv runtime package (not only the mpv executable), or set CLOUDSTREAM_LIBMPV."
        }

        val INSTANCE: MpvLibrary by lazy {
            forceNumericCLocale()
            val os = System.getProperty("os.name").lowercase()
            val targets = linkedSetOf<String>()
            System.getenv("CLOUDSTREAM_LIBMPV")?.takeIf { it.isNotBlank() }?.let(targets::add)
            if (os.contains("win")) {
                targets += listOf("libmpv-2.dll", "libmpv-2", "mpv-2", "mpv-1", "libmpv")
            } else if (os.contains("mac")) {
                targets += listOf(
                    "/opt/homebrew/lib/libmpv.dylib", "/usr/local/lib/libmpv.dylib",
                    "libmpv.dylib", "mpv", "libmpv",
                )
            } else {
                targets += listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so", "mpv", "libmpv")
            }
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
            if (loaded == null) {
                // Final fallback: try loading any libmpv*.so* from system paths
                try {
                    val osName = System.getProperty("os.name").lowercase()
                    if (osName.contains("linux")) {
                        val process = Runtime.getRuntime().exec(arrayOf("ldconfig", "-p"))
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val match = Regex("""\s+(libmpv\S*)\s+.*=>\s+(/\S+)""").find(line!!)
                            if (match != null) {
                                val path = match.groupValues[2]
                                try {
                                    loaded = Native.load(path, MpvLibrary::class.java) as MpvLibrary
                                    AppLogger.i("Successfully loaded mpv from system: $path")
                                    break
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            loaded ?: throw RuntimeException("Failed to load native MPV library. ${installHint()}")
        }
    }
}
