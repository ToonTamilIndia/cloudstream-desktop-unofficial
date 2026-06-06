# CloudStream Desktop (Unofficial Client)

> *"This entire repository is purely vibecoded. Not one line of code here was actually written by a human lol. It's basically a duct-tape edition that somehow works, and I don't have the tiniest idea how this is working."*

Welcome to the **CloudStream Desktop** project.

This is a native Compose for Desktop application designed to run CloudStream Android plugins natively on a desktop JVM environment, without requiring an Android emulator.

## ⚠️ Disclaimer & No Guarantees

**This is an independent, unofficial experiment.** 
This project is **not** endorsed by, associated with, or maintained by the original CloudStream developers. It was built as a proof-of-concept to validate the execution of Android-specific plugins within a desktop JVM environment.

**No Guarantees Provided:** 
There is no guarantee of ongoing maintenance, bug fixes, or future updates for this repository. The codebase is provided "as is," without warranty of any kind. Users are encouraged to fork, modify, and improve the codebase independently.

## 📜 DMCA Notice

**This repository acts purely as a blank-slate media shell.** 
The application does not ship with any plugins, media files, or pre-configured content sources. Everything must be explicitly installed and configured by the user at their own discretion. The developers of this application do not host, distribute, or control any content, and hold no responsibility or liability for how users choose to utilize this software.

## 🛠 Architecture Overview

The repository is structured with strict modularity to ensure maintainability and separation of concerns.

```text
cloudstream-windows-workspace/
├── android-reference/           # Git Submodule pointing directly to the official CloudStream Android repository
├── android-stubs/               # "The Stubs": Fake Android APIs (Context, Log, Uri) mocked for the JVM
├── common/                      # Shared data models and logging interfaces
├── library/                     # Wrapper module connecting android-reference for the desktop JVM
├── player-abstraction/          # Pure Kotlin definitions for Video Player IPC and JNA bridges
├── plugin-runtime/              # Isolated ClassLoaders specifically for booting .cs3 Android plugins
├── plugin-sandbox/              # Testing environment for validating plugins against the Android stubs
├── desktop-app/                 # The main Kotlin/Compose Multiplatform desktop module
│   ├── build.gradle.kts         # Gradle build script for the desktop app
│   └── src/main/kotlin/com/lagradost/cloudstream3/desktop/
│       ├── Main.kt              # Clean 70-line application bootstrapper
│       ├── init/                # Bootstrap initialization logic (Network, Security, Proxy, Plugins)
│       ├── data/                # Data storage, app settings, and repos
│       ├── logic/               # MVVM ViewModels handling business logic without touching UI
│       ├── network/             # Network configuration enforcing DNS-over-HTTPS (DoH) privacy
│       ├── player/              # Compose Embedded MPV (JNA) and external player abstractions
│       ├── repo/                # Repository Manager for handling third-party extensions
│       ├── storage/             # DesktopDataStore for cross-platform JSON configuration saving
│       └── ui/                  # Jetpack Compose for Desktop UI Components
│           ├── components/      # Reusable, stateless UI widgets (ExtensionCard, ProgressIndicators)
│           ├── navigation/      # Stack-based screen router
│           ├── screens/         # Top-level feature views (Home, Details, extensions/* tabs)
│           └── theme/           # Unified appearance tokens and DesktopTheme configuration
├── build.gradle.kts             # Root Gradle build script
└── settings.gradle.kts          # Root settings
```

### Component Breakdown

#### 1. Core Library Isolation (`android-reference` & `:library`)
The architecture relies on a Git Submodule (`android-reference`) pointing directly to the official CloudStream Android repository. This ensures that the upstream scraping logic remains completely unmodified. The `:library` module acts as a wrapper, exposing the upstream parsing engine to the desktop JVM while keeping the desktop UI strictly decoupled from the core logic.

#### 2. The Android Stubs (`:android-stubs`)
CloudStream plugins are compiled against the Android SDK. Running them natively on a desktop JVM would normally result in `ClassNotFoundException` errors. The `:android-stubs` module provides mock JVM implementations of core Android classes (e.g., `Context`, `Log`, `Uri`), allowing the Dalvik bytecode to execute seamlessly on Windows.

#### 3. The Plugin Runtime Sandbox (`:plugin-runtime`)
To safely execute third-party Dalvik bytecode, the `plugin-runtime` converts `.cs3` Dalvik bytecode to JVM bytecode via `dex2jar` at runtime. A `PluginSecurityVerifier` then performs static ASM bytecode analysis before the plugin is loaded — if it detects calls to dangerous APIs (`java.lang.Runtime`, `java.io.File`, `ProcessBuilder`), the plugin is rejected outright and never executed.

#### 4. Embedded Hardware-Accelerated Video Playback (`:player-abstraction`)
Video playback is handled via an Embedded MPV Engine. The `:player-abstraction` module uses JNA (Java Native Access) to dynamically link into `libmpv-2.dll`, rendering hardware-accelerated video directly into a Compose `SwingPanel`.

## 🚀 Setup & Installation

### For End Users
Simply download the latest `.msi` installer from the **Releases** tab and double-click it. Everything is pre-bundled (including the hardware-accelerated video player). There is absolutely zero configuration required.

> 🛡️ **Security:** The official `.msi` release has been scanned and verified. View the [VirusTotal Scan Results](https://www.virustotal.com/gui/file/fdd8a2ad621569d1fc6bf2e2af6bdc8b1a28a941f1259b0f8b5bbf7a44f25238/detection).

---

### For Developers (Building from Source)

**1. Prerequisites:**
- **JDK 21 or higher** (The codebase targets Java 21)
- **Git** (Required for submodule cloning)

**2. Cloning the Repository:**
To clone the repository properly (including the Android submodule):
```bash
git clone --recursive https://github.com/YourUsername/cloudstream-windows.git
cd cloudstream-windows
```
> [!WARNING]  
> **DO NOT DOWNLOAD THIS REPOSITORY AS A ZIP FILE.** GitHub ZIP downloads do not include Git Submodules. The `android-reference` directory will be empty, causing immediate build failures. You **must** use `git clone --recursive`.

**3. The Video Engine Configuration:**
Since GitHub blocks files larger than 100MB, the `libmpv-2.dll` is not checked into this repository. You must provide it yourself.
- Download the Windows MPV binaries (ensure it includes `libmpv-2.dll`).
- Place the core `libmpv-2.dll` directly inside the `desktop-app/appResources/windows/mpv/` directory of the project workspace.

**4. Building for Local Testing:**
To compile and launch the application locally for testing and development, run the following Gradle task in your terminal:
```bash
./gradlew desktop-app:run
```
Alternatively, for Windows users, you can double-click **`launch.bat`** to run the application in Development Mode.

**5. Packaging the MSI Installer:**
To build the final standalone Windows `.msi` installer (which bundles the JRE and dependencies natively without requiring users to have Java installed), run:
```bash
./gradlew desktop-app:packageMsi
```
The compiled installer will be generated at `desktop-app/build/compose/binaries/main/msi/`.
> [!NOTE]  
> The Gradle script includes an automated `stripPlaywrightDriver` task. When you build the MSI, it will automatically unpack the `com.microsoft.playwright:driver-bundle` dependency, strip out the macOS and Linux Node.js binaries, and repackage it. This safely strips out redundant OS binaries without breaking Cloudflare bypassing on Windows, helping to keep the large bundled JVM + MPV installer as optimized as possible.

## 🙏 Acknowledgements
Significant acknowledgement is given to the original CloudStream developers and contributors. This project utilizes their core scraping engine and extension architecture as a foundation.
