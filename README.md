# Locked

A calm little Android app that blocks distracting apps and websites when you tap an NFC puck.

Tap a paired puck to lock a set of apps. Tap it again to unlock. That's the whole idea.

I built it as a gift for my fiancée. It's a personal project, installed over ADB on two phones, and
it's never been on the Play Store.

> **Friction, not a jail.** Locked is meant to make distraction a little harder, not impossible.
> Uninstalling, safe mode, a factory reset and force-stopping the app are all known ways out, and
> that's fine.

## What it does

- **Pucks.** Write an NFC tag from inside the app, give it a name, and choose the mode it locks.
  One puck can be shared between two phones.
- **Modes.** A mode is a set of apps and websites, with optional notification hiding.
- **Blocking.** Opening a blocked app sends you Home and shows a quiet overlay. Blocked websites in
  Chrome are backed out of.
- **Passes.** Each lock comes with a few 5-minute passes (0 to 5, default 3) that open one blocked
  app or website without ending the lock.
- **Schedules and timed locks.** Lock automatically at set times, or for a set length of time.
- **Notification hiding.** Notifications from locked apps are dismissed while the lock is on.
- **Strict mode.** While locked, Locked's own App info, accessibility and uninstall screens are
  closed, so the escape hatches are harder to reach.
- **Stats.** A session log, step-ins, and a last-7-days chart.
- **Home-screen widget** showing whether you're locked and for how long.
- **Safety first.** Phone, contacts, messages, maps, the clock, Settings, the keyboard and
  emergency info are never blocked, in any mode.
- **Only stricter while locked.** While a lock is on, settings can only get tighter. You can add
  apps, but you can't remove them, delete modes, or move pucks to end the lock early.

## Private by design

- No `INTERNET` permission, no analytics, no crash reporting. Everything stays on the phone.
- No backups (`allowBackup="false"`).
- The accessibility service reads the foreground app and the browser address bar only to decide
  whether to block. Nothing is stored beyond a local step-in history that can be cleared from Stats.

## How it works

| Piece | What it does |
| --- | --- |
| `BrickRepository` | The single source of truth. Combines lock state, schedules, modes and passes into one immutable `Policy` exposed as a `StateFlow`. |
| `activePolicy()` | A pure Kotlin function (`domain/`) that works out "am I locked right now?" from stored state plus the clock. It doesn't depend on alarms, so a missed alarm can't leave you stuck or unprotected. It has unit tests. |
| `BlockerAccessibilityService` | Reads the foreground package from the in-memory `Policy` (never Room) and blocks with a `TYPE_ACCESSIBILITY_OVERLAY` window. |
| `NotificationBlockerService` | Cancels notifications from blocked apps. |
| `TapActivity` | A transparent activity that verifies the tag (UID and a hash of its secret) and toggles the lock. |
| `HealthCheckWorker` | WorkManager check that reminds you to re-enable the blocker if it has been switched off. |

Enforcers only read the `Policy`. Every state change goes through the repository.

## Stack

Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, DataStore, Hilt, WorkManager.

Min SDK 36 (Android 16), tested on Samsung One UI 8.5. There's deliberately no compatibility code
for older Android versions.

## Building

Requires Android Studio and a device running Android 16.

```powershell
.\gradlew assembleDebug          # debug build
.\gradlew testDebugUnitTest      # unit tests
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Debug builds include an ADB broadcast receiver that force-unlocks, so testing never locks the
developer out. Release builds don't have it.

Release builds are signed with a key kept outside the repo. See [docs/HANDOFF.md](docs/HANDOFF.md)
for building, installing on another phone, setting it up, and updating it later.

## Project layout

```
com.max.locked
├── data/       Room entities and DAOs, repositories, Policy, settings, NeverBlockList
├── domain/     Pure rules: activePolicy, passes, schedules, strictness, stats
├── service/    Accessibility and notification services, block overlay, setup checks
├── nfc/        Tag format, writing, TapActivity
├── schedule/   Health check worker
├── widget/     Home-screen widget
├── di/         Hilt module
└── ui/         One package per screen, plus theme and shared components
```

## Notes

- Room schema changes always ship with a migration and the exported schema. There's no destructive
  fallback, so an update never wipes modes, pucks or schedules.
- Debug and release builds are signed with different keys, so switching between them means
  uninstalling first, which erases that phone's setup.
- This is a personal project, not a product. It isn't affiliated with any commercial app-blocking
  service.
