# Gallery Tidy

On-device Android app that suggests blurry, badly exposed and near-duplicate photos, then lets you review and delete them. Targets Android 17 / One UI 9.0 (compileSdk/targetSdk 37, minSdk 30).

## Get an APK
**Option A, no Android Studio:** push this folder to a GitHub repo (branch `main`). The `Build APK` workflow runs automatically (Actions tab -> latest run -> Artifacts -> `GalleryTidy-apk`). Unzip it and install the `.apk`.

**Option B, Android Studio:** Open this folder, let it sync (install the API 37 SDK if prompted), then Build -> Build Bundle(s)/APK(s) -> Build APK(s). Or just press Run with your phone connected.

The APK is debug-signed, which is fine for installing on your own phone. On the phone, allow "Install unknown apps" for the app you open the APK from.

Local command line: `./gradlew :app:assembleDebug` (APK lands in `app/build/outputs/apk/debug/`).

Versions were pinned without a local build. If CI or sync fails, the log will show which version to change.

## What changed from the original scaffold
- **Was never wired up:** the old worker only inserted rows. Nothing ran blur analysis or hashing. Now sync -> analyse -> suggest is one pipeline.
- **Sync by diff, not by timestamp:** compares MediaStore `DATE_MODIFIED` with the cache. Catches edited photos, photos with no `DATE_TAKEN`, and drops rows deleted elsewhere.
- **No OpenCV:** pure-Kotlin analysis. No unofficial dependency, no native libs.
- **Safe decoding:** `ImageDecoder` at reduced size (EXIF-aware, HEIC OK) instead of decoding full-size bitmaps.
- **Fewer blur false positives:** score is the mean of the 3 sharpest of 16 tiles, so portraits with soft backgrounds aren't flagged. Dark and overexposed shots get their own categories.
- **Live sensitivity sliders:** flags are derived from stored metrics, so moving a slider re-sorts instantly with no rescan.
- **Real duplicates:** dHash plus time-window neighbour comparison, keeps the best shot per group (resolution, then sharpness, then size).
- **Delete flow:** `createDeleteRequest` in batches of 200, Room updated only after the system dialog is confirmed, running "freed" total. minSdk raised to 30 (the original's 29 would crash on that API).
- **Permissions:** runtime request, partial-access (Android 14+) banner, settings fallback. Video permission removed (images only).
- **Background:** 12-hour constrained WorkManager job plus a ContentObserver while the app is open. The original's 15-minute loop was a battery drain.
- **Bug fix:** the original used `GalleryTidyApp` as both Application class and composable.

## Known limits
- Duplicates are only matched within 10 minutes of each other (bursts and retakes). Copies saved days apart aren't caught.
- Blur/exposure are heuristics: tune with the sliders, and use "Keep" to hide false positives for good.
