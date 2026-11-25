# Cache Twister for Android

Cache Twister shows available cache measurements and opens the Android App info page for a selected app. It does not clear any other app's cache. On Samsung Galaxy: **App info → Storage → Clear cache**. Do not choose **Clear data**. Usage Access is optional and allows measurement, not deletion. Some packages or cache sizes may not be visible.

Selected apps are saved on this device across restarts until you change the selection. Apps stay selected even if Android reports 0 B, so the same list is ready next time. A measured nonzero cache stays pending. With Usage Access unavailable, the app does not assume that returning from Settings cleared anything.

Both home-screen widgets open the queue only. Inside the app, an explicit, single-use confirmation can delete files only in Cache Twister's own cache, code cache, and app-specific external cache. Other apps' measured decreases are shown without claiming that Cache Twister cleared them. There is no root, Shizuku, Accessibility, or network permission in the app manifest.

## Preview and verification

[Download the 1.1 preview APK](downloads/Cache-Twister-1.1-preview.apk) · [SHA-256 checksum](downloads/SHA256SUMS)

The debug preview has application ID `com.aistudio.cachetwister.vnxkrq.preview`, separate from the original app ID, and requires Android 8 or newer. It is test-signed rather than a production update. Do not uninstall a working copy to resolve a signing mismatch.

Build with JDK 21, Android SDK Platform 36 and the checked-in Gradle wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The Android tooling normally generates a local debug key. A build environment can point `CACHE_TWISTER_DEBUG_KEYSTORE` at a disposable Android debug key instead. Do not commit signing keys.

### Physical S25 acceptance (pending)

1. Record Android and One UI versions. Keep the original app installed if present. Pin both widgets and verify each only opens the queue.
2. With a disposable app containing saved settings and known cache, open its App info with Usage Access denied, then return without clearing it. It must stay eligible in the queue and be labeled unmeasured. Repeat with Access granted; no decrease should be claimed.
3. Open App info again and use **Storage → Clear cache**. Confirm the saved settings survived. Check measured decrease without attributing it to Cache Twister. Repeat with Usage Access revoked and check graceful loss of measurement.
4. In Cache Twister, cancel an own-cache cleanup and leave all apps deselected; neither should delete anything. With Twister selected, review and confirm. Check that only its cache is affected and its preferences survive.
