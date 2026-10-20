# Themis Task Manager

*Liberating data. Replacing paywalls. People's app.*

A private, offline-first habit and weight tracker with a desktop app and an Android app that sync directly with each other over your home Wi-Fi. No account, no cloud, no subscription. Your data lives in a local SQLite database on the desktop and in a Room database on the phone.

## Features

- **Daily tasks** that repeat every day, with a GitHub-style 26-week consistency heatmap, current streak, perfect days and an "average day" completion chart.
- **One-off tasks**: do it once, tick it, and it's done for good. They never affect the heatmap, streak or averages.
- **Weight tracker**: 6-month line chart, latest / change / lowest / highest figures, kg or lb (stored as kg).
- **Daily quote** from ZenQuotes (hidden when offline).
- **Automatic LAN sync** between desktop and Android, with no setup: the phone finds the desktop on its own.
- **Water reminders** *(Android only)*: a notification every 30 minutes with a quote.

## Repository layout

```
main.py                 Desktop backend (Eel + SQLite) and the sync server
web/                    Desktop frontend (index.html, style.css, app.js)
themis-mobile/          Android app (Kotlin, Jetpack Compose, Room)
```

## Desktop app

**Requirements:** Python 3.9+, SQLite 3.24+ (bundled with modern Python), and Chrome or Edge (Eel opens its window with one of them).

```bash
pip install eel
python main.py
```

On first launch the database (`progress.db`) is created or upgraded in place. Before any upgrade a backup is saved next to it as `progress.db.v<old-version>.bak`.

The header shows a sync indicator:

| Dot | Meaning |
|---|---|
| Green | Connected, last sync under a minute ago |
| Amber | Searching for a phone |
| Red | Offline / sync services failed to start (for example a port is already in use) |

## Android app

**Requirements:** Android Studio (Koala or newer), JDK 17. The app supports Android 8.0+ (API 26) and targets API 34.

1. Open the `themis-mobile` folder in Android Studio and let Gradle sync.
2. Run it on a phone connected to the **same Wi-Fi** as the desktop.
3. Start the desktop app. Within a few seconds both sync dots turn green.

The app syncs when opened, every 30 seconds while visible, and every 15 minutes in the background on Wi-Fi.

### Water reminders

Every 30 minutes the phone shows "Time to drink water" with a ZenQuotes quote (water-only text if offline). The bell icon in the top bar turns them on or off, and Android 13+ asks for notification permission on first launch.

- **Quiet hours** are 22:00 to 07:00. Change `QUIET_START_HOUR` / `QUIET_END_HOUR` in `reminder/WaterReminder.kt` (set them equal for 24/7).
- Reminders run through WorkManager, so Android may delay one by a few minutes under Doze or battery saver. For tighter timing set the app's battery usage to *Unrestricted*.

## How sync works

The desktop runs two background threads; the phone is the client.

| Step | Detail |
|---|---|
| Discovery | Phone broadcasts `THEMIS_DISCOVERY_PING` to UDP **54321**. The desktop replies with its name and sync port. |
| Transport | HTTP on TCP **54322**: `GET /api/v1/sync/status`, `POST /api/v1/sync/pull`, `POST /api/v1/sync/push`. |
| Flow | Phone pulls everything changed since its last sync, merges it, then pushes its own changes. |
| Conflicts | **Last-write-wins** on UTC ISO-8601 `updated_at` timestamps. A record only overwrites another if it is strictly newer. |

Design notes:

- **Soft deletes.** Deleting a task, un-ticking a completion or removing a weight sets `is_deleted` rather than removing the row, so the deletion can travel to the other device.
- **Task identity.** Tasks carry a UUID (`uid`), because auto-increment IDs on two devices would collide. Completions reference tasks by `task_uid` when syncing.
- **One-off tasks** are ordinary task records with `is_one_off` and `is_done` flags, so they sync like any other edit.
- Both devices should have roughly correct clocks, since conflicts are decided by timestamps.

### Database versions

| Version | Adds |
|---|---|
| 1 | Tasks and completions |
| 2 | Weights and settings |
| 3 | Sync columns (`updated_at`, `is_deleted`, `uid`), `device_meta` |
| 4 | One-off tasks (`is_one_off`, `is_done`) |

The Android Room database is at version 2 (v1 to v2 adds one-off tasks) and migrates existing data.

## Troubleshooting

- **Desktop stays amber.** Allow Python through the firewall for UDP 54321 and TCP 54322, and turn off any router "AP isolation" or "client isolation" setting.
- **Emulator.** Emulators sit behind NAT and never receive UDP broadcasts, so the app falls back to `10.0.2.2:54322` automatically.
- **Red dot at startup.** Another program is probably using port 54321 or 54322.
- **No reminders.** Check the bell is on, notification permission is granted, it's outside quiet hours, and battery saver isn't restricting the app.

## Security note

The sync server has **no authentication**: any device on the same network can read or write your data while the desktop app is running. That is fine on a trusted home network; avoid running it on shared or public Wi-Fi. A pairing token is the natural next step.

## Design

Colours and typography follow one set of tokens across both apps: background `#E8EDF0`, surface `#F7F9FA`, ink `#1C2830`, accent teal `#1F6F62`, serif headings with a system sans body.
