package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore

/**
 * Initializes security-related subsystems:
 * - Uncaught exception handler
 * - BouncyCastle security provider (Android AES-GCM compat)
 * - DataStore pre-initialization (prevents SecurityManager issues)
 */
fun initSecurity() {
    // Uncaught exception handler
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        DesktopErrorReporter.report("Unhandled exception in ${thread.name}", throwable)
    }

    // BouncyCastle security provider
    // Append (not insert-at-position-1) so the JDK's SunEC provider keeps ownership of
    // X25519/XDH. Registering BC first causes BC's BCXDHPublicKey to be returned for
    // XDH/X25519 and then fail a cast to java.security.interfaces.XECPublicKey.
    java.security.Security.insertProviderAt(
        org.bouncycastle.jce.provider.BouncyCastleProvider(),
        java.security.Security.getProviders().size + 1,
    )
    AppLogger.i("Registered BouncyCastle Security Provider")

    // Pre-initialize DataStore
    // Force initialization of DataStore BEFORE plugins are loaded.
    // This prevents plugins from triggering <clinit> which causes the SecurityManager to block File.mkdirs()
    DesktopDataStore.init()
}
