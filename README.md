# Video As Screensaver

Play any video as your Android screensaver (Daydream). Made because I couldn't find this on the Play Store. Also I was bored.

## Features
- Pick any MP4 (or other video) from your phone via SAF
- Fill or Fit scaling
- Optional sound with volume slider
- Loop toggle
- Preview button (tries to launch SystemUI Somnambulator directly)
- Direct link to system Daydream settings — because some phones bury it 3 menus deep

## Requirements
- Android 8.0+ (API 26)

## Building locally
```bash
gradle wrapper --gradle-version 8.4
chmod +x gradlew
./gradlew assembleDebug
```
APK lands at `app/build/outputs/apk/debug/app-debug.apk`

## CI
Push to `main` or `master` — GitHub Actions generates the wrapper, builds both debug and release APKs, and uploads them as artifacts.

## Device setup
1. Install APK
2. Open app → pick a video → SAVE
3. Hit "Open System Daydream Settings" (or dig through Settings → Display → Screen Saver yourself)
4. Select **Video Screensaver**
5. Plug in charger / dock — screensaver fires

---
[Donate](https://malikhw.github.io/donate) · [Source](https://github.com/MalikHw/vidasscreensaver)
