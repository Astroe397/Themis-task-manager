# Themis Task Manager & Health Hub

*Liberating data. Replacing paywalls. People's app.*

A private, offline-first habit, weight, strength, activity tracking, and mindfulness app. It features a desktop app (Python/Eel) and an Android app (Jetpack Compose/Room) that sync directly with each other over your home Wi-Fi—with no account, no cloud, and no subscription.

Your data lives in a local SQLite database on the desktop and a Room database on the mobile device.

---

## Key Features

### Habits & Tasks
- **Daily Tasks:** Tasks that repeat every day. Includes a GitHub-style 26-week consistency heatmap, current streak counter, perfect days tracker, and an "average day" completion chart.
- **One-off Tasks:** Standalone items that do not affect heatmaps, streaks, or daily averages.

### Weight Tracking
- **6-Month Progress Chart:** Visual line chart of bodyweight over time.
- **Key Metrics:** Tracks latest weight, 6-month change, lowest, and highest records.
- **Flexible Units:** Supports `kg` or `lb` (stored internally as kg).

### Muscle Strength & Workout Logging *(Android)*
- **Strength Ranks:** Ranks muscle groups based on estimated 1-Rep Max (1RM) using Epley's formula over a 30-day rolling window.
- **Tier Ladder:** Tracks progress through rank tiers (from Beginner up to Olympian levels).
- **Bodyweight Calculations:** Pull-ups, dips, and other bodyweight exercises automatically incorporate logged bodyweight.
- **Rank Sharing:** Export and share visual cards displaying your muscle strength tier standings.

### Outdoor Activity Tracking *(Android)*
- **GPS Route Recording:** Track runs and walks with real-time vector map rendering (via MapLibre).
- **Location Filtering:** Includes Kalman location filtering and Douglas-Peucker path simplification to optimize route accuracy and storage.
- **Live Metrics:** Shows live distance, duration, pace, and step count.
- **Background Tracking:** Handles location tracking seamlessly in background services.

### 10-Minute ADHD Reset & Meditation *(Android)*
- **Breathing Patterns:** Guided visual and tactile box breathing (4-4-4-4) and calm breathing (4-7-8).
- **Ambient Soundscapes:** Seamless ambient audio loops (White Noise, Rain, Brown Noise, Soft Pad) powered by ExoPlayer.
- **Haptic Guidance:** Optional pulse vibrations for breathing state transitions.

### Reminders & Motivation
- **Daily Quotes:** ZenQuotes integration (gracefully hidden when offline).
- **Water Reminders *(Android)*:** Optional interval notification reminders every 30 minutes with customizable quiet hours (default: 22:00 to 07:00).

### Seamless LAN Sync
- **Automatic Peer-to-Peer Sync:** Automatic device discovery over local Wi-Fi without server setup.

---

## Repository Layout

```text
main.py                 Desktop backend (Eel + SQLite) and LAN sync server
web/                    Desktop frontend (HTML, CSS, JavaScript)
themis-mobile/          Android application (Kotlin, Jetpack Compose, Room, Hilt, MapLibre)
```

---

## Getting Started

### 1. Desktop App

**Requirements:** Python 3.9+, SQLite 3.24+, and Google Chrome or Microsoft Edge.

```bash
pip install eel
python main.py
```

* On first launch, the local database (`progress.db`) is automatically initialized or upgraded.
* Automatic backups are created before database upgrades (`progress.db.v<old-version>.bak`).

#### Sync Status Indicator
| Indicator Dot | Meaning |
|---|---|
| **Green** | Connected and synced |
| **Amber** | Searching for mobile client |
| **Red** | Offline / Port binding error |

---

### 2. Android App

**Requirements:** Android Studio (Koala or newer), JDK 17, Android 8.0+ (API 26+), targeting API 34.

1. Open `themis-mobile` in Android Studio and let Gradle synchronize dependencies.
2. Ensure your phone and PC are connected to the **same Wi-Fi network**.
3. Launch the desktop app first, then launch the mobile app.

---

## Architecture & Peer-to-Peer Sync

The desktop acts as the sync server while the mobile application operates as the client worker thread.

```text
+-------------------+                    +-------------------+
|   Android App     |                    |    Desktop App    |
|   (Room DB)       |                    |    (SQLite DB)    |
+---------+---------+                    +---------+---------+
          |                                        |
          | --- UDP Broadcast (Port 54321) ------> | Discovery Ping
          | <-- UDP Response (Sync Port) --------- |
          |                                        |
          | --- HTTP GET/POST (Port 54322) -------> | Pull & Merge
          | <-- HTTP Push ------------------------ | Push local edits
```

### Sync Details
- **Discovery Protocol:** Mobile client broadcasts `THEMIS_DISCOVERY_PING` over UDP port **54321**.
- **Transport:** Standard HTTP REST endpoints running over TCP port **54322** (`/api/v1/sync/status`, `/api/v1/sync/pull`, `/api/v1/sync/push`).
- **Conflict Resolution:** **Last-Write-Wins (LWW)** based on UTC ISO-8601 timestamps (`updated_at`).
- **Soft Deletions:** Deleting tasks, strength logs, or weight entries sets an `is_deleted` flag so the deletion propagates reliably across devices.
- **UUID Identity:** Items use UUID keys (`uid`) to avoid primary key collisions across multi-device syncs.

---

## Troubleshooting & Firewall Settings

- **Desktop Stays Amber:** Ensure Python is allowed through host firewall settings for UDP port **54321** and TCP port **54322**. Ensure router **AP Isolation** or **Client Isolation** is turned off.
- **Emulator Syncing:** Emulators behind NAT cannot receive UDP broadcasts; the mobile app automatically falls back to target `10.0.2.2:54322`.
- **Water Reminders / Background Services:** Ensure battery optimization settings for the mobile app are set to *Unrestricted* if Android suspends background notifications or activity recording.

---

## Security Note

The local sync endpoint operates **without network authentication** under the assumption that it runs inside a trusted, isolated home network. Avoid running the sync engine on public or shared Wi-Fi networks.

---

## Design System

Color palettes and typography follow unified design tokens across both client applications:
- **Background:** `#E8EDF0`
- **Surface:** `#F7F9FA`
- **Ink / Text:** `#1C2830`
- **Primary Accent:** `#1F6F62` (Teal)