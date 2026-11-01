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

## Installing

Locked isn't on the Play Store. Download `app-release.apk` from the Releases page and install it
with `adb install -r app-release.apk`, or open the file on the phone.

You need an NFC phone running **Android 16 or newer**. Older versions aren't supported.

Android treats accessibility services in apps installed outside the Play Store as "restricted", so
the first time you'll see the switch greyed out:

1. Open **Settings › Apps › Locked**.
2. Tap **⋮** (top right) › **Allow restricted settings**.
3. Go back and turn on Locked under **Settings › Accessibility › Installed apps**.

Play Protect may also say the app is unrecognised. That's expected for a hobby app that isn't in
the Play Store. The setup checklist inside Locked walks through the rest (accessibility,
notification access, notification reminders).

### Checking your download

Releases list the APK's SHA-256. Compare it with your copy:

```powershell
Get-FileHash .\app-release.apk -Algorithm SHA256
```

Every release is signed with the same key, so updates install over each other. The signing
certificate's SHA-256 is:

```
73554e0abdd7d9729f2887dc54bc88cb60a7498e3176e84aec2aafc31952f3d1
```

You can check it with `apksigner verify --print-certs app-release.apk`. If it doesn't match, don't
install it.

Don't want to trust a binary? The source is here: build it yourself (see Building below).

## If you need to get out of a lock

Locked is friction, not a jail, and you can always get out.

- **Tap the puck.** Tapping the puck that started the lock ends it.
- **Timed locks and schedules** end on their own. Locking is worked out from the clock each time,
  so nothing can leave you stuck if the phone was off or restarted.
- **Emergencies.** Phone, contacts, messages, maps, the clock, Settings, the keyboard and emergency
  info are never blocked, in any mode.
- **Lost the puck?** Restart the phone in safe mode (hold the power button, then press and hold
  *Power off*, then tap *Safe mode*). Third-party apps don't run there, so you can uninstall Locked
  from Settings › Apps. Uninstalling erases your modes, pucks and history.
- **Strict mode** makes Locked's App info page and uninstall prompt harder to reach while a lock is
  on. Safe mode and a factory reset still work.

Release builds have no ADB unlock, so keep a puck somewhere you can reach.

## Privacy

Locked works entirely on your phone.

- It has no internet permission, so it can't send anything anywhere. It has no analytics and no
  crash reporting.
- What it stores: your modes, paired pucks (a hash of each tag's secret, not the secret), schedules,
  your settings, and a history of lock sessions and which blocked apps you opened. All of it is in
  the app's private storage and is excluded from Android backups.
- The history can be cleared at any time from Stats. Uninstalling the app removes everything.
- The accessibility service sees which app or browser page is in front so it can decide whether to
  block it. That information is not recorded beyond the step-in history above.
- Notification access is used only to dismiss notifications from locked apps. They are never read
  out, stored or re-posted.

## Known limits

Locked is a self-control tool, not a security product.

- **Pucks aren't secret-proof.** A puck holds a random key in plain text on the tag. Anyone with
  physical access can read it with any NFC app and copy it onto another tag. Locked also checks the
  tag's ID, which blocks casual copies but not specially made cards. Tags are never permanently
  locked, so you can re-pair them.
- **Other apps on the phone.** The tap screen is reachable by other installed apps, which is how
  Android delivers NFC tags. Without the tag's ID and key, an app can't make it do anything.
  Only install apps you trust.
- **Escape hatches are real.** Uninstalling, safe mode, a factory reset, and force-stopping the app
  all end the blocking. Force stop turns the accessibility service off until you switch it back on;
  the lock itself survives.
- **Websites** are only blocked in Chrome, by reading its address bar.
- **Samsung phones** put background apps to sleep. If blocking stops working, set Locked to
  *Unrestricted* under Settings › Apps › Locked › Battery.

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
