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
Every 30 minutes: "Time to drink water" plus a ZenQuotes quote (water-only text if offline).
- Bell icon in the top bar turns it on/off. Android 13+ asks for notification permission on first launch.
- Quiet hours 22:00-07:00: edit `QUIET_START_HOUR` / `QUIET_END_HOUR` in `reminder/WaterReminder.kt`
  (set them equal for 24/7).
- Delivery uses WorkManager, so Android may delay a reminder by a few minutes (Doze, battery saver).
  For tighter timing: Settings -> Apps -> Themis -> Battery -> Unrestricted.
