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
| Bluetooth name | `G69_9307` |
| Address (MAC) | `XX:XX:XX:XX:XX:XX` |
| Advertised service UUIDs | _(not recorded yet; check the scan screen)_ |
| Firmware / hardware version | _(not read yet; Device Information 180A has READ characteristics 2A26 firmware, 2A27 hardware, 2A25 serial)_ |
| QWatch Pro version used for captures | _(none yet; app was uninstalled)_ |
| Phone used | Xiaomi 23117RA68G, Android 16 |
| Date | 2026-10-29 (phone clock about 03:07-03:12) |

## 2. Services and characteristics

Copy these from the Pulse "Services" tab (or from the `service` / `characteristic` rows at the top of the CSV).

Source for this whole table: Pulse Phase 0 "Log" tab screenshots, 6 services found. No CSV export yet.

| Service UUID | Characteristic UUID | Properties | Looks like | Source |
| --- | --- | --- | --- | --- |
| `00001801` Generic Attribute | `2A05` | INDICATE | standard | log screenshots |
| `00001800` Generic Access | `2A00`, `2A01`, `2A04`, `2AA6` | READ | standard (device name, appearance, ...) | log screenshots |
| `6e40fff0-b5a3-f393-e0a9-e50e24dcca9e` | `6e400002-...` | WRITE, WRITE_NO_RESPONSE | probable command channel (phone to band) | log screenshots |
| same | `6e400003-...` | NOTIFY | probable reply channel (band to phone) | log screenshots |
| `de5bf728-d711-4e47-af26-65e3012a5dc7` | `de5bf72a-...` | WRITE, WRITE_NO_RESPONSE | second command channel, purpose unknown | log screenshots |
| same | `de5bf729-...` | NOTIFY | second reply channel, purpose unknown | log screenshots |
| `0000180a` Device Information | `2A23`, `2A25`, `2A26`, `2A27` | READ | standard (system ID, serial, firmware, hardware) | log screenshots |
| `0000fee7` | `fea1` | READ, NOTIFY | unknown | log screenshots |
| same | `fea2` | READ, WRITE, INDICATE | unknown | log screenshots |
| same | `fec9` | READ | unknown | log screenshots |

Does it match the Colmi-style UART service (`6e40fff0-b5a3-f393-e0a9-e50e24dcca9e`)? **The band has a service with that exact UUID**, but the UUID was written from memory and still has to be checked against the Colmi docs (PRD section 8, step 1). The second service (`de5bf728-...`) may also match a Colmi "big data" service from memory; also unchecked.

## 3. Actions: what the band sends

One row per thing I did. "Bytes written" = phone to band. "Bytes received" = band to phone (notifications or read results). Write bytes as hex, e.g. `69 01 00`.

| # | Action I did (in QWatch Pro or Pulse) | Characteristic | Bytes written | Bytes received | What I think it means | Confirmed? | Source |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Connected with Pulse, tapped "Subscribe to all notifications" | `2A05`, `6e400003`, `de5bf729`, `fea1`, `fea2` | `01 00` to the notification switch (0x2902) of each NOTIFY one, `02 00` for the INDICATE ones (`2A05`, `fea2`) | nothing | all five subscriptions succeeded (status=0) | yes | log screenshots |
| 2 | Waited / pressed band button / walked (exact trigger unknown) | `6e400003` | nothing | one notification: `73 01 00 00 00 00 00 00 00 00 00 00 00 00 00 74` (16 bytes) | Last byte `74` = sum of the first 15 bytes (`73+01`), so this looks like a 16-byte packet with a checksum. Meaning of `73 01` unknown. | guess | log screenshots |

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
| Does the band need a pairing / handshake before it answers? | partly answered: it accepts a plain connection and lets us subscribe with no handshake. Not yet known whether commands need one. | row 1 above |
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
