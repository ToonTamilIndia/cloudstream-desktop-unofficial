import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // CloudStream Library (KMP, JVM target)
    // Contains: MainAPI, extractors, metaproviders, WebViewResolver (JVM actual), etc.
    implementation(project(":library"))

    // ASM Bytecode Scanner
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    // Android Stubs
    implementation(project(":android-stubs"))

    implementation(project(":plugin-runtime"))
    implementation(project(":player-abstraction"))
    implementation(project(":common"))

    // HTTP
    implementation(libs.nicehttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation(kotlin("reflect")) // Required for Jackson to deserialize plugin Kotlin data classes
    implementation("org.json:json:20240303") // Required for plugins using org.json (natively included on Android)

    // NewPipe Extractor — required by plugins that depend on it (e.g. YouTube extractors)
    implementation(libs.newpipeextractor)

    // Coroutines (swing provides Dispatchers.Main on desktop JVM)
    val coroutinesVersion = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")

    // Playwright (headless Chromium)
    // Desktop counterpart of Android's WebView system.
    implementation("com.microsoft.playwright:playwright:1.60.0")

    // BouncyCastle & Conscrypt
    // Android's built-in AES-GCM crypto is not available on desktop JVM.
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    // JNA for MPV
    implementation("net.java.dev.jna:jna:5.14.0")

    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    // Bundle Skiko native runtimes for ALL supported platforms into the fat JAR.
    // A fat JAR built on Linux only contains the Linux Skiko native by default,
    // which crashes on Windows ("Cannot find skiko-windows-x64.dll"). Including
    // the Windows runtime lets the same self-contained JAR run on both OSes.
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.18")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.8.18")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.18")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.8.18")
    implementation(compose.material3)  // material3 already includes core icons
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // Embedded browser used by the in-window JW Player backend.
    val javafxVersion = "21.0.2"
    val javafxPlatform = when {
        System.getProperty("os.name").lowercase().contains("win") -> "win"
        System.getProperty("os.name").lowercase().contains("mac") &&
            System.getProperty("os.arch").lowercase().contains("aarch64") -> "mac-aarch64"
        System.getProperty("os.name").lowercase().contains("mac") -> "mac"
        System.getProperty("os.arch").lowercase().contains("aarch64") -> "linux-aarch64"
        else -> "linux"
    }
    // JavaFX is only used for the embedded WebView (JW Playright backend); it is NOT
    // required to open the Compose/Skiko UI. Ship the Linux JavaFX runtime for the
    // per-OS native distributions (Deb/Rpm/AppImage), while the fat JAR relies on
    // Skiko natives (above) to render the desktop UI on every platform.
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")

    // Image loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
}

// Strip Playwright driver-bundle to current-platform-only before packaging.
// The driver-bundle JAR ships Node.js for ALL platforms (Win/Mac/Linux).
// We strip out entries for OTHER platforms to reduce the JAR from ~206MB down to ~50MB.
val stripPlaywrightDriver by tasks.registering {
    description = "Strips non-native platform binaries from the Playwright driver-bundle JAR."
    group = "build"

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val platformsToStrip = mutableListOf<String>()
        when {
            osName.contains("win") -> {
                platformsToStrip.addAll(listOf("driver/mac", "driver/mac-arm64", "driver/linux", "driver/linux-arm64"))
            }
            osName.contains("mac") -> {
                platformsToStrip.addAll(listOf("driver/linux", "driver/linux-arm64", "driver/win32", "driver/win32-x64"))
            }
            else -> { // Linux
                platformsToStrip.addAll(listOf("driver/mac", "driver/mac-arm64", "driver/win32", "driver/win32-x64"))
            }
        }

        val driverJar = configurations.runtimeClasspath.get()
            .resolvedConfiguration.resolvedArtifacts
            .find { it.name == "driver-bundle" }?.file ?: return@doLast

        val strippedJar = layout.buildDirectory.get().asFile.resolve("playwright-driver-stripped.jar")
        if (strippedJar.exists() && strippedJar.lastModified() > driverJar.lastModified()) {
            println("Playwright driver already stripped, skipping.")
            return@doLast
        }

        println("Stripping non-native entries from Playwright driver-bundle (${driverJar.length() / 1024 / 1024}MB)...")

        ZipFile(driverJar).use { input: ZipFile ->
            ZipOutputStream(strippedJar.outputStream().buffered()).use { output: ZipOutputStream ->
                input.entries().asSequence()
                    .filter { entry: ZipEntry ->
                        platformsToStrip.none { entry.name.startsWith(it) }
                    }
                    .forEach { entry: ZipEntry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            input.getInputStream(entry).copyTo(output)
                        }
                        output.closeEntry()
                    }
            }
        }

        println("Stripped driver size: ${strippedJar.length() / 1024 / 1024}MB (was ${driverJar.length() / 1024 / 1024}MB)")

        // Replace the original JAR in the Gradle cache with the stripped version
        // so the packager picks up the smaller file
        driverJar.delete()
        strippedJar.copyTo(driverJar, overwrite = true)
    }
}

// Build a single self-contained (fat) JAR with every runtime dependency merged inside.
// This is the file you wrap with Launch4j (or run via `java -jar`) to produce a Windows
// .exe without relying on the Gradle/jlink distribution layout.
val fatJar by tasks.registering(Jar::class) {
    description = "Builds a standalone runnable JAR with all dependencies merged in."
    group = "build"
    archiveBaseName.set("CloudStream-Desktop")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.lagradost.cloudstream3.desktop.MainKt"
        attributes["Implementation-Title"] = "CloudStream Desktop"
        attributes["Implementation-Version"] = (project.findProperty("APP_VERSION") ?: "0.0.0")
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/versions/*/module-info.class", "module-info.class")
    }
    dependsOn(stripPlaywrightDriver)
}

// Compose Desktop application configuration
compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream3.desktop.MainKt"
        jvmArgs += listOf(
            "-Djava.security.manager=allow",
            "-Dcloudstream.version=${project.findProperty("APP_VERSION")}",
        )

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
            )
            packageName = "CloudStream-Desktop"
            packageVersion = project.findProperty("APP_VERSION")?.toString()?.substringBefore('-') ?: "0.0.0"
            description = "CloudStream Desktop Client"
            vendor = "CloudStream"
            includeAllModules = true  // Required — jlink cannot detect dynamically-loaded modules (JNA, Playwright, Conscrypt)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))

            windows {
                iconFile.set(project.file("src/main/resources/logo_installer.ico"))
                menuGroup = "CloudStream Desktop"
                upgradeUuid = "d7e9b04f-723a-4467-84df-fcf470c1ae02"
                shortcut = true       // Creates a Desktop shortcut during install
                perUserInstall = true // Installs per-user, avoids needing admin rights
            }

            linux {
                iconFile.set(project.file("src/main/resources/logo_ui.png"))
                packageName = "cloudstream-desktop"
            }

            macOS {
                iconFile.set(project.file("src/main/resources/logo_ui.png"))
            }
        }
    }

    // Hook the strip task to run before any packaging task
    afterEvaluate {
        listOf("packageMsi", "packageReleaseMsi", "packageDeb", "packageReleaseDeb", "packageRpm", "packageReleaseRpm", "packageAppImage", "packageReleaseAppImage", "createDistributable", "createReleaseDistributable")
            .mapNotNull { tasks.findByName(it) }
            .forEach { it.dependsOn(stripPlaywrightDriver) }
    }
}
