# Protocol notes: LIGE band

Where we record what the band sends for each action. **Nothing here is confirmed until it has a capture file next to it.**

## Ground rules

- Only my own band, my own phone, my own Bluetooth captures.
- Never copy, decompile or redistribute QWatch Pro (its code, firmware or keys).
- Public open-source projects (e.g. the Colmi docs) may be read as hints, but every claim below must be checked against a capture from this band.
- Save every capture in `captures/` and write its file name in the "Source" column.

## 1. The band

| Item | Value |
| --- | --- |
| Bluetooth name | _(fill in from the Pulse scan screen)_ |
| Address (MAC) | _(fill in; this stays the same between scans)_ |
| Advertised service UUIDs | _(fill in, or "none advertised")_ |
| Firmware / hardware version | _(Device Information service 180A, if present)_ |
| QWatch Pro version used for captures | _(fill in)_ |
| Phone used | _(model + Android version)_ |
| Date | _(YYYY-MM-DD)_ |

## 2. Services and characteristics

Copy these from the Pulse "Services" tab (or from the `service` / `characteristic` rows at the top of the CSV).

| Service UUID | Characteristic UUID | Properties | Looks like | Source |
| --- | --- | --- | --- | --- |
| | | | | |

Does it match the Colmi-style UART service (`6e40fff0-b5a3-f393-e0a9-e50e24dcca9e`)? **Unknown.** _(Check against the Colmi docs, then record yes/no here.)_

## 3. Actions: what the band sends

One row per thing I did. "Bytes written" = phone to band. "Bytes received" = band to phone (notifications or read results). Write bytes as hex, e.g. `69 01 00`.

| # | Action I did (in QWatch Pro or Pulse) | Characteristic | Bytes written | Bytes received | What I think it means | Confirmed? | Source |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | _(example)_ Open app, sync | | | | | no | |

Actions to capture (PRD section 8, steps 3 and 4):

- [ ] Connect / pair for the first time
- [ ] Read battery level
- [ ] Sync heart-rate log
- [ ] Sync SpO2 log
- [ ] Sync sleep
- [ ] Sync steps / calories / distance
- [ ] Take a manual heart-rate reading (real-time)
- [ ] Change the heart-rate logging interval (try 1 minute)
- [ ] Read the current heart-rate interval
- [ ] Find device / vibrate
- [ ] Set an alarm
- [ ] Anything else the app offers

## 4. Open questions (PRD section 2)

| Question | Answer | Evidence |
| --- | --- | --- |
| Does the band need a pairing / handshake before it answers? | unknown | |
| Smallest heart-rate logging interval? | unknown | |
| Does real-time heart-rate streaming work? | unknown | |
| Any beat-to-beat (RR) data, i.e. could we get HRV? | unknown | |
| How many days of data does the band store offline? | unknown | |
| Does sleep include stages beyond deep and light? | unknown | |
| What does the SpO2 data look like? | unknown | |
| Which alarm commands exist? | unknown | |

## 5. Packet format guesses

_(Only after several captures. For each: byte positions, what each byte means, and which captures support it. Mark anything not yet seen in at least two captures as "guess".)_

## 6. Phase 0 exit check (PRD section 8)

Go if a prototype can read heart rate, sleep and steps **without QWatch Pro**.

- [ ] Heart rate read without QWatch Pro
- [ ] Sleep read without QWatch Pro
- [ ] Steps read without QWatch Pro
