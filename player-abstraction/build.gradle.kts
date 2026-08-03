plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xopt-in=com.lagradost.cloudstream3.Prerelease",
            "-Xopt-in=com.lagradost.cloudstream3.InternalAPI",
        )
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation(libs.nicehttp)

    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    // We may need to depend on the library to pass SubtitleData and ExtractorLink
    implementation(project(":library"))
    implementation(project(":common"))
}
