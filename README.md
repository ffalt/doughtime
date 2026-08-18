<div style="text-align:center"><a href="https://github.com/Safouene1/support-palestine-banner/blob/master/Markdown-pages/Support.md"><img src="https://raw.githubusercontent.com/Safouene1/support-palestine-banner/master/banner-support.svg" alt="Support Palestine" style="width: 100%;"></a></div>

---

<p align="center">
    <a href="https://ffalt.github.io" target="_blank">
        <img height="200" width="200" src="./logo.svg" alt="DoughTime logo">
    </a>
</p>

<h1 align="center">DoughTime - Sourdough Timer</h1>
<p align="center">A simple, reliable timer app for sourdough bakers. Track multiple fermentation steps with background notifications and persistent progress tracking.</p>

<p align="center">
  <a href="https://github.com/ffalt/doughtime/releases" target="_blank"><img src="https://img.shields.io/github/release/ffalt/doughtime.svg" alt="Latest release"></a>
  <a href="https://opensource.org/license/gpl-3-0" target="_blank"><img src="https://img.shields.io/badge/license-GPL%203.0-blue.svg" alt="License: GPL 3.0"></a>
  <img src="https://github.com/ffalt/doughtime/workflows/test/badge.svg" alt="CI test badge">
</p>

## ✨ Features

DoughTime focuses on simplicity and reliability for sourdough fermentation timing. Create custom timer recipes with multiple steps, and let the app handle the timing while you focus on the bread.

| Category                  | What you get                                                                             |
|---------------------------|------------------------------------------------------------------------------------------|
| 📋 **Custom Timers**      | Create multi-step timer recipes with custom names and durations                          |
| ⏱️ **Step Management**    | Organize fermentation into logical steps (bulk fermentation, shaping, final proof, etc.) |
| 🔔 **Notifications**      | Persistent background notifications keep you informed of timer progress                  |
| 🔄 **Background Service** | Timers continue running even when the app is closed or screen is off                     |
| 🎯 **Exact Alarms**       | Precise alarm scheduling ensures you never miss a step transition                        |
| ⚡ **Quick Access**        | Fast, intuitive interface for starting and managing timers during baking                 |
| 💾 **Persistent Storage** | All timer recipes and history stored locally on your device (no cloud required)          |
| 🔒 **Privacy First**      | F-Droid compatible; no proprietary SDKs or tracking                                      |

## 📲 Installation

No Play Store account needed - grab the APK directly:

<a href="https://github.com/ffalt/doughtime/releases" target="_blank"><img height="80" src="./badge-github.png" alt="Get it on Github"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22:%22io.github.ffalt.doughtime%22,%22url%22:%22https://github.com/ffalt/doughtime%22,%22author%22:%22ffalt%22,%22name%22:%22DoughTime%22,%22preferredApkIndex%22:%200%7D"  target="_blank"><img height="80" src="./badge-obtainium.png" alt="Get it on Obtainium"></a>

**Obtainium** lets you track and auto-update the app straight from this GitHub repository, so you'll always have the latest version without any app store.

### Quick-start after install

1. Open **DoughTime** from your app launcher
2. Tap **+** to create a new timer recipe
3. Add steps with names and durations (e.g., "Bulk Fermentation: 4 hours")
4. Save your recipe and tap **Start** when ready to bake
5. Get alarms as each step completes

## 🤝 Contribution & translation

All contributions are warmly welcome - no contribution is too small!

- 🐛 **Bug reports & ideas** - open an [issue](https://github.com/ffalt/doughtime/issues)
- 🔧 **Code** - send a pull request with small, focused changes

## ⚖️ License

This project is licensed under the **GPL 3.0** license. See [`LICENSE`](./LICENSE) for details.

## 🔨 Building from source

**Requirements:** Java JDK 17+, Android SDK (API level 37), Gradle wrapper (already included)

```bash
# Clone the repository
git clone https://github.com/ffalt/doughtime.git
cd doughtime

# Build a debug APK
./gradlew assembleDebug

# Run Checkstyle (must pass before committing)
./gradlew check

# (Optional) Install directly to a connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> The project follows the standard Android Gradle layout - all app code lives under `app/src/main/`. Architecture: MVVM with Room database, Foreground Service for background timer management, and Material Design UI.

### Build variants

- **Debug** (`assembleDebug`): Full app with debug symbols, signed with debug key, app ID includes `.debug` suffix
- **Release** (`assembleRelease`): Requires environment variables: `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`

## 📚 Architecture Overview

- **data/** - Room entities (Timer, TimerStep, TimerWithSteps) and TimerRepository
- **ui/** - MVVM UI layer (TimerViewModel, adapters, activities)
- **service/** - TimerService (foreground service managing active timers and alarms)
- **MainActivity** - Entry point, binds to TimerService

## 🙏 Acknowledgments

Built with:
- [Android Architecture Components](https://developer.android.com/topic/libraries/architecture) for MVVM and Room
- [Material Design 3](https://m3.material.io/) for the UI
