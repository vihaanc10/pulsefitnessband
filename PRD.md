# PRD v2: Pulse for the LIGE Band (Android)

Oct 30, 2026

## 1. Overview

Pulse is now an Android-first app that replaces QWatch Pro for the LIGE no-screen band, adding recovery, strain, sleep and trend analysis. The biggest change from v1: the app talks to the band directly over Bluetooth LE and stores everything on the phone, so no hardware needs building.

**Key finding.** Listings and a QWatch Pro manual describe the band taking heart rate about every 5 minutes and blood oxygen about hourly. That is enough for resting heart rate, sleep and trends, but not for true HRV, the main input WHOOP uses for Recovery. Pulse therefore uses a different Recovery recipe (section 5) and treats the first task as discovering what the band can really send (section 8).

**Biggest risk.** The band's Bluetooth protocol is not public. Related cheap devices (the Colmi ring family that uses the QRing app) have been reverse engineered by hobbyists, so this is feasible, but we must confirm it on the actual band before committing.

## 2. What the LIGE band can do

The band records heart rate, blood oxygen, sleep, steps and calories, but sellers' specs conflict and only the Phase 0 spike can confirm what it sends. Everything below comes from seller listings, a manual and app-store pages, not from testing the unit.

| Capability | What sources say | Confidence | Use in Pulse |
| --- | --- | --- | --- |
| Heart rate | Optical sensor; auto reading every 5 minutes per a QWatch Pro manual | Medium; interval may be adjustable | Resting HR, daily curve, Strain |
| Blood oxygen (SpO2) | Hourly auto reading, same manual | Medium | Overnight dips, trend only |
| Sleep | Deep and light sleep recorded, per the QWatch Pro App Store listing | Medium; no REM reported | Sleep duration, efficiency, consistency |
| Steps, distance, calories | Accelerometer (STK8321 G-sensor per a seller page) | High | Activity, workouts |
| Blood pressure | Advertised in listings, but the manual says the function is not active for that model | Low | Do not use or display |
| GPS | None on the band; "GPS route" in listings is likely the phone's GPS | Medium | Phone GPS for outdoor workouts |
| Vibration motor | Alarm, sedentary reminder, find-device | High | Smart alarm, alerts |
| Battery and build | About 120-180 mAh, up to roughly 20 days claimed, Bluetooth 5.0-5.2, 1 ATM or IP68 (listings disagree), single button, Realtek chip | Medium | Battery indicator, charge reminders |

**QWatch Pro app.** Android package `com.qcwireless.qcwatch`, by Shenzhen Qingcheng Wireless, about 1.8M installs. A similar app, QiFitPro, appears to be a fork of it.

**Still unknown (to answer in Phase 0):** exact Bluetooth services and commands; whether heart-rate logging can be set to 1-minute intervals; whether real-time heart rate streaming works; whether any beat-to-beat or HRV data exists; how many days the band stores offline; whether sleep includes stages beyond deep and light; whether the firmware requires a pairing handshake.

## 3. Goals, non-goals and success metrics

The goal is a personal-first app better than QWatch Pro for daily use, then a product others with the same cheap bands can use. Targets are proposed.

**Goals**

- Pull all data from the band over Bluetooth without QWatch Pro and store it locally.
- Show daily Recovery, Strain and Sleep scores plus long-term trends QWatch Pro lacks.
- Give a clear daily recommendation with the reasoning visible.
- Work for other cheap bands in the same family later, through a device adapter layer.

**Non-goals (v1)**

- No new hardware.
- No blood pressure, ECG or any medical claim.
- No iOS, no social features, no cloud account required.

| Metric | Target (proposed) |
| --- | --- |
| Band data syncs without QWatch Pro | Works on the owner's own band by end of Phase 1 |
| Days with at least 20 hours of heart-rate coverage | 80% of days |
| Morning check-in (app opened before 10:00) | 5 of 7 days |
| "Recovery feels right" self-rating | 70% of days |
| Sync success rate | 95% of attempts |

## 4. Benchmark: what to borrow

WHOOP-style apps win on one daily decision, Fitbit-style apps on breadth and habits, and open-source tools on data ownership. Pulse borrows the best of each within what this band can measure. WHOOP, Fitbit and Oura rows are from general knowledge; the others are from public pages.

| App or tool | Strength | Borrow | Skip or adapt |
| --- | --- | --- | --- |
| WHOOP | Daily Recovery, Strain and Sleep coach; journal that links habits to recovery | Three-score home screen, strain target by recovery, journal impact analysis | Needs beat-to-beat HRV; adapt Recovery to resting-HR basis |
| Fitbit | Broad dashboard, goals, sleep score, long history, friendly onboarding | Sleep score, weekly summary, streaks, goal setting | Skip social challenges and premium paywalls in v1 |
| Oura | Readiness and sleep staging; strong trend views | Readiness-style explanation text, body-temperature trend (if band has it) | Skip temperature features unless the band exposes data |
| Gadgetbridge (open source) | Local-first, no account, supports many cheap bands | Local-only data and device-adapter structure; check whether the band is already supported | Its UI is utilitarian |
| Colmi R02 open-source clients | Reverse-engineered protocol for the same QC-style apps: real-time heart rate and SpO2, heart-rate logs, set logging interval, sleep, steps | Packet formats as a starting point for the band | Rings, not bands; commands may differ |
| HRV4Training | Validated HRV from the phone camera, readiness insights | Idea of a 60-second morning camera HRV check | iPhone app; build our own Android version |

## 5. Scores redesigned for this band

Without HRV, Recovery is built mainly on overnight resting heart rate and sleep, with an optional morning HRV check added if the band or phone can supply it. Weights are our own starting guesses and must be tuned on the owner's own data.

| Score | Inputs | Notes |
| --- | --- | --- |
| Recovery (0-100%) | Overnight resting HR vs 30-day baseline (about 40%), sleep performance (30%), overnight SpO2 dips (10%), yesterday's Strain (10%), morning "how do I feel" slider (10%); HRV replaces part of the resting-HR weight when available | Green 67-100, yellow 34-66, red 0-33; confidence label shown when data is sparse |
| Strain (0-21) | Heart-rate zone minutes weighted by intensity, from the band's heart-rate log plus real-time streaming during workouts | With 5-minute readings, daily Strain is approximate; workout mode (phone connected, live heart rate) gives accurate session Strain |
| Sleep (0-100%) | Time asleep vs need, efficiency, consistency of bed and wake times, deep share | Need = baseline + extra for yesterday's Strain + sleep debt |

**Resting heart rate.** Defined as the lowest sustained average (for example 30 minutes) during detected sleep, not a single lowest sample, so one noisy reading does not skew the baseline.

**Heart-rate zones** use age-based max heart rate at first, then refine from the highest values seen in workouts.

**Optional morning HRV check (P1).** A 60-second phone-camera or band-based reading, labelled "spot check", used only if its quality is good. If Phase 0 finds the band exposes beat-to-beat intervals, this becomes automatic overnight HRV.

**Cold start.** Scores show "calibrating" for the first 5 nights, then use personal baselines.

**Transparency rule.** Every score has a "Why this number?" view listing inputs and how each differs from baseline.

## 6. Feature requirements

P0 is the first usable version for the owner's own band; P1 and P2 follow once data quality is proven.

| Priority | Feature | Requirement |
| --- | --- | --- |
| P0 | Band pairing and sync | Scan, pair, read heart-rate log, SpO2 log, sleep, steps and battery from the band; background sync via Android foreground service; clear reconnect handling |
| P0 | Local database | All data stored on the phone; export to CSV; no account needed |
| P0 | Heart-rate settings | Set logging interval as short as the band allows (target 1 minute); show current interval |
| P0 | Home dashboard | Recovery, Strain, Sleep, today's recommendation, battery level |
| P0 | Recovery, Strain, Sleep scores | As defined in section 5, with "Why this number?" |
| P0 | Sleep view | Sleep and wake times, deep and light, efficiency, consistency, debt |
| P1 | Workout mode | Live heart rate and zones while the phone is near the band; auto Strain for the session; phone GPS route for runs and rides |
| P1 | Morning check-in | Feel slider and optional 60-second HRV spot check |
| P1 | Journal | Yes/no daily behaviors (caffeine, alcohol, late meal, stress) correlated with next-day Recovery |
| P1 | Notifications | Morning Recovery, bedtime reminder, low battery, sync problems |
| P1 | Smart alarm | Band vibrates in a light-sleep window before target time (if the band supports alarm commands) |
| P1 | Weekly and monthly reports | Trends, best and worst days, shareable image |
| P2 | Health Connect export | Write steps, heart rate and sleep to Android Health Connect |
| P2 | AI coach | Chat over the owner's own data; runs only with explicit opt-in |
| P2 | Other bands | Adapter layer for similar QC-style bands and rings |
| P2 | Optional cloud backup | Encrypted backup and multi-phone sync |

**Android requirements.** Bluetooth LE with the nearby-devices permission (Android 12+), foreground service for sync, battery-optimization guidance because many phones kill background apps, and notification permission. Target Android 10 and above.

## 7. Architecture

Pulse is local-first: the Android app pairs with the band, stores every sample on the phone and computes scores there, so it works with no account and no internet.

Diagram (described in text): the LIGE band connects over Bluetooth LE to the Android app (Kotlin). Inside the app are three components: a BLE sync service (pairs, pulls logs, sets the heart-rate interval), a local database (all raw and daily data, stays on the phone) and a score engine (Recovery, Strain, Sleep, personal baselines). Two optional exports hang off the app: Health Connect export (P2) and encrypted cloud backup (P2).

A device-adapter layer sits inside the sync service, so supporting another band later means writing one new adapter that outputs the same heart-rate, sleep, step and SpO2 records.

**Suggested stack (to confirm).** Kotlin with Jetpack Compose for screens; a local SQLite database through Room; WorkManager and a foreground service for background sync; Nordic's open-source Android BLE library for connection handling; charts via a Compose chart library; unit-tested score engine written as pure Kotlin so it can be tuned against recorded sample data without a phone.

| Data type | Fields | Retention (proposed) |
| --- | --- | --- |
| Heart-rate sample | time, bpm, source (log or live) | Raw 180 days, then daily aggregates |
| SpO2 sample | time, percent | Raw 180 days |
| Sleep session | start, end, deep and light minutes, wake count | Life of install |
| Steps and activity | time bucket, steps, calories, distance | Life of install |
| Workout | start, end, avg and max heart rate, Strain, optional route | Life of install |
| Daily score | date, Recovery, Strain, Sleep, inputs, model version | Life of install |
| Journal entry | date, behaviors, feel slider | Life of install |

## 8. Phase 0: band discovery spike

Before building anything else, spend about a week finding out exactly what the band sends. This decides how good Recovery can be. All steps use the owner's own device and own phone.

1. **Identify the band.** Install the free nRF Connect app, scan, and note the band's Bluetooth name, address and the services it advertises. Compare against the Colmi ring service ID in the open-source client docs (github.com/patmorli/colmi-r09-smart-ring): a match means those packet formats are a strong starting point.
2. **Check existing support.** Look in Gadgetbridge's supported-device list for the band or its Bluetooth name; if supported, its parsing code may be reusable.
3. **Record real traffic.** On the phone, turn on Developer options, Bluetooth HCI snoop log, and use QWatch Pro to sync, measure heart rate, change settings and start a sleep night. Export the log and open it in Wireshark. The BTLeTool project (github.com/nullstring1/btletool) shows how to parse such logs on Android.
4. **Map the commands.** For each action in QWatch Pro, record the bytes written to the band and the notifications returned. Build a table: feature, command, response format.
5. **Answer the unknowns** (section 2): heart-rate interval limits, real-time heart rate, beat-to-beat data, storage days, sleep stages, SpO2 format, alarm commands.
6. **Prototype.** A throwaway Android or Python script that connects without QWatch Pro and prints one night of data.

**Exit criteria.** Go if the prototype reads heart rate, sleep and steps without QWatch Pro. If the band needs a vendor handshake we cannot replicate, check whether QWatch Pro can export to Health Connect and read from there, or switch to a better-documented band such as a Colmi-family device.

**Deliverable.** A one-page protocol notes doc and a recorded sample dataset for developing the score engine offline.

## 9. Privacy, legal and safety

Keeping all data on the phone makes privacy simple and is a selling point. Nothing here is legal advice.

- **Reverse engineering your own device** to read your own data is generally accepted for interoperability, but laws differ by country. Do your own captures, write your own code, and never copy or redistribute QWatch Pro's app code, firmware or keys.
- **If the app is ever released,** use an original name and design (not QWatch Pro, WHOOP or LIGE branding), and say the app is not affiliated with those companies.
- **Wellness only.** Show a disclaimer: optical heart-rate and SpO2 readings from cheap bands are for personal trends, not diagnosis. Do not show blood pressure or any alerts that imply a medical condition.
- **Data protection.** If other people use it, the Philippine Data Privacy Act treats health data as sensitive, so get clear consent, offer export and deletion, and keep data local by default.
- **Security.** Encrypt the local database, request only needed permissions (Bluetooth, notifications, optional location for workout routes), and never send data off the phone without explicit opt-in.

## 10. Roadmap, risks and open questions

Each phase ends in a gate; durations assume one developer working part-time and are estimates.

1. **Phase 0, Discovery (1-2 weeks):** the spike in section 8. *Gate: the prototype reads heart rate, sleep and steps without QWatch Pro.*
2. **Phase 1, Personal MVP (6-8 weeks):** pairing, background sync, local database, home dashboard, Recovery, Strain and Sleep. *Gate: the owner prefers it to QWatch Pro for daily use for two weeks.*
3. **Phase 2, Training features (4-6 weeks):** workout mode, journal, morning check-in, notifications, reports. *Gate: Recovery "feels right" on 70% of days.*
4. **Phase 3, Share it (open-ended):** Health Connect export, device adapters for similar bands, beta testers, decision on a cloud option.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Band uses an encrypted or handshake-locked protocol | Cannot read data directly | Phase 0 check; fall back to a better-documented band (e.g. Colmi family, about USD 25) |
| Heart rate only every 5 minutes, no HRV | Weaker Recovery and Strain | Try 1-minute interval; workout live mode; morning HRV spot check; confidence labels |
| Android kills background sync | Gaps in data | Foreground service; battery-optimization onboarding; band stores logs, so catch-up sync fills gaps |
| Cheap optical sensor is noisy | Wrong scores | Outlier filtering; trend-based scoring; show data quality |
| Firmware updates change the protocol | App breaks | Version detection; adapter tests; keep recorded sample data |
| Time and scope | Never ships | Phase 0 and 1 are personal-use only; skip cloud and accounts |

**Open questions**

- What is the band's exact Bluetooth name and the QWatch Pro version in use?
- Is the developer coding Kotlin directly, or should the plan assume AI-assisted building? (Answer: AI-assisted, owner is a beginner.)
- Personal use only, or release later?
- Which phone model (matters for background Bluetooth behavior)?
- Which matters more first: Recovery accuracy or a polished dashboard?

**Sources.** QWatch Pro manual (manualslib.com/manual/2803918, page 74); LIGE listing (gogadgetssupplies.com); LIGE manual (manuals.plus/ae/1005008373764082); Tokopedia listing (toco.id); QWatch Pro on AppGoblin (appgoblin.info/apps/com.qcwireless.qcwatch); Colmi R02 client (pypi.org/project/colmi-r02-client); Gadgetbridge 0.87.0 release notes; HRV4Training App Store page. Gathered 2026-10-30 from search snippets, not tested on the actual unit.
