# Themis Mobile (Android)

Kotlin + Jetpack Compose + Room + Retrofit/OkHttp + WorkManager. Syncs peer-to-peer over the LAN with the
desktop app (`main.py` v3): UDP discovery on 54321, HTTP sync on 54322, last-write-wins on `updated_at`.

## Open & run
1. Android Studio (Koala or newer) -> Open this folder. Let Gradle sync (it downloads Gradle 8.9 from the wrapper properties).
2. Run on a **physical phone on the same Wi-Fi** as the desktop (best test), or an emulator.
3. Start the desktop: `python main.py`. The phone's top-right dot goes amber (searching) then green; the desktop header dot goes green.

## Testing notes
- Emulators sit behind NAT and never see UDP broadcasts. The app falls back to `10.0.2.2:54322` (the emulator's alias
  for the host PC) automatically, so sync still works there.
- Allow Python through the desktop firewall for UDP 54321 and TCP 54322.
- Some routers enable "client/AP isolation", which blocks phone <-> PC traffic. Turn it off for testing.
- Phone and desktop clocks should be roughly correct: last-write-wins compares their timestamps.
- First sync pushes everything (since = 1970); later syncs only send changes.

## One-off tasks
Tasks screen -> **One-off** chip. Tick once and it's done for good; they never affect the heatmap, streak or averages.
They sync with the desktop's "One-off tasks" section. Existing phone data is kept (Room migration v1 -> v2).

## Water reminders (Android only)
Every 30 minutes (:00 and :30): "Time to drink water" plus a ZenQuotes quote (water-only text if offline).
- Runs on AlarmManager, so it works with the app closed; BootReceiver re-arms it after a reboot/update.
- **No reminders 23:00-09:00** (`QUIET_START_HOUR` / `QUIET_END_HOUR` in `reminder/WaterReminder.kt`).
- Bell in the top bar toggles it. Android 13+ asks for notification permission on first launch.
- Exact timing needs "Alarms & reminders" (Settings -> Apps -> Themis); otherwise an inexact alarm is used.

## Activity tracking
Activity tab: live map, distance, time, pace, steps, history. See the root README for the architecture.
Test on a real phone outdoors; walk a loop, lock the screen, and check the route survives. To test process
death: start a workout, then `adb shell am kill com.themis.mobile` and reopen the app.

## Sharing a workout
Finish an activity -> summary screen -> share icon -> 9:16 preview -> "Share to Instagram Story" or "Save / Share".
Test the Instagram path on a phone with Instagram installed, and the fallback on one without it (or an emulator).
