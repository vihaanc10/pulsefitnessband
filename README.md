# Pulse

## The app (design phase)

Four tabs: **Home** (recovery, sleep, strain, heart rate), **Health** (7-day / 30-day trends), **Coach** and **Profile**. It is dark, minimal and drawn with Jetpack Compose only (no extra libraries; charts and icons are hand-drawn).

- **It starts empty.** Until the band has delivered real data, every screen says "Waiting for your band". Nothing on screen is invented.
- **Sample data** is a clearly marked preview: Profile > Data > "Show sample data" (or the button on the empty screens). Numbers come from `data/Repositories.kt` (`SampleHealthRepository`).
- All screens read from one interface, `HealthRepository` (`data/HealthModels.kt`). Real band data will plug in there later.
- **Bluetooth lab** (the Phase 0 tools below) opens from Profile > Fitness band.
- The band reports deep and light sleep, so there is no REM in the app. HRV and respiratory rate show "--" until we know the band can supply them.

# Phase 0 (band discovery spike)

A small throwaway Android app for finding out what the LIGE band sends over Bluetooth LE. It is **not** the real Pulse app yet (see `PRD.md`, section 8).

What it does:

1. **Scan screen**: lists nearby Bluetooth LE devices with name, address, signal strength and advertised service UUIDs.
2. **Device screen**: connects to the device you tap, lists every service and characteristic, lets you Read, Write (hex) and Subscribe, and writes **every message in both directions** to a CSV file you can share.

## Build and run (Android Studio)

1. Install [Android Studio](https://developer.android.com/studio) (free) and let it finish downloading its extras.
2. **File > Open** and choose this folder. Wait for "Gradle sync" to finish (the first time takes several minutes).
3. On your phone: **Settings > About phone**, tap **Build number** 7 times, then **Settings > Developer options > USB debugging** on.
4. Plug the phone into the computer with a USB cable and accept the "Allow USB debugging?" question on the phone.
5. Pick your phone in the device dropdown at the top of Android Studio and press the green **Run** button.

## Using it

1. **Close QWatch Pro completely** (long-press its icon > App info > Force stop). A band usually talks to only one app at a time.
2. Open Pulse Phase 0, tap **Scan for devices**, allow "Nearby devices" when asked.
3. Find your band (name, or the strongest signal when it is next to the phone) and tap it.
4. On the device screen, the **Services** tab shows everything the band offers. Try **Subscribe to all notifications**, then move or tap the band.
5. The **Log** tab shows each message. **Export CSV** opens the share menu.

Each CSV row: `time_iso, epoch_ms, direction (TX phone->band, RX band->phone, INFO app note), kind, characteristic, hex, note`.
Files live in the app's private storage and are also listed under "Saved logs" on the scan screen.

## Recording what QWatch Pro does

This app only sees its own conversation with the band. To see what QWatch Pro sends, use the Bluetooth HCI snoop log (PRD section 8, step 3) and note the results in `PROTOCOL_NOTES.md`.
