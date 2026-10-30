# Next steps (written 2026-10-30, early morning)

Read this first when the owner says "continue". The owner is a beginner: explain simply, don't ask small questions, move fast, make decisions.

## How the owner wants Pulse to work
Like a normal fitness app (WHOOP / QWatch Pro): open it, wait about 30 seconds, and see everything from the band: sleep, heart rate, steps, workouts, recovery. No PC, no developer mode, no cables once installed.

## State right now
- Phase 0 is done. The G69 (name `G69_9307`) speaks the Colmi-style 16-byte protocol on service `6e40fff0-...` (see PROTOCOL_NOTES.md). Battery, heart-rate settings, set-clock and the heart-rate log all work. **Heart-rate log needs request "way B"** (local midnight treated as UTC). Steps replied "no data" (owner barely walked, recheck). Sleep command: not found yet.
- App: dark design, 4 tabs (Home, Health, Coach, Profile), starts empty, sample data is an opt-in preview. Bluetooth lab under Profile. **Auto-sync on open, local database, Add device / Remove device are written and compile** (GitHub built `Pulse.apk` successfully), but **have never been run on the phone**. Expect bugs.
- GitHub builds `Pulse.apk` on every push (`.github/workflows/build-apk.yml`) and attaches it to the "Pulse (latest)" release. The repo is private, so the download link only works for people with repo access.
- The band was worn overnight with heart-rate logging on (every 5 minutes).

## Update, 2026-10-30 evening
Real data now shows on the phone: auto-sync works (Add device, tap the band), heart rate, steps, battery. Sleep, recovery and strain are estimated from the heart-rate log and steps (`data/Analysis.kt`, used by `RealHealthRepository`). Recovery unlocks after 5 nights (night 1 of 5 found). Tabs are now Home, Sleep, Health, Coach, Profile; Home has the WHOOP-style three rings. The repo is public. Calories from the band (about 1,600) are in unknown units, unverified.
**Next:** (1) background sync so data is waiting each morning; (2) fixed signing key so APK updates install over the old one (GitHub secret needed, owner must add it; do NOT commit a keystore to the public repo); (3) notifications; (4) tune sleep detection against what the owner remembers; (5) email in git history is public, offer the noreply rewrite.

## Update, 2026-10-30 night
Built (compiles on CI, NOT yet run on the phone): logo everywhere (traced vector, app icon, intro), editable profile (height/weight/age/sex, units persisted), calories now an estimate (resting burn + Keytel active burn) instead of the band's raw number (unit unknown), "How Pulse measures this" card, Workouts tab (Running, Jogging, Walking, Biking use phone GPS via `WorkoutService`; Basketball, Swimming, Lifting; lifting adds muscular load to strain), Energy meter, background sync every 30 min, notifications. Profile moved to the avatar on each screen (not in the bottom bar).
**Untested, check first:** live heart rate during workouts (commands `69 01 01` start, `69 01 03` continue, `1E 33` keep-alive, `6A 01 00 00` stop; replies `69 01 00 <bpm>`), whether the band's heart-rate log gets finer than 5 minutes if the interval is shortened (the docs parse exactly 288 slots per day), GPS tracking in a pocket on the Xiaomi (battery restrictions), foreground service types. Age is a placeholder (25) until the owner edits it. Swimming: the band's water rating is unknown.

## Update, 2026-10-30 (privacy and sharing)
History was rewritten and force-pushed: all commits now use the GitHub noreply address and the band's MAC is blanked. **The owner must delete their Android Studio project folder and clone again** (their local history no longer matches). Welcome screen (name, height, weight, age, sex; no account) is built. Signing: the owner got `pulse-signing-secrets.txt` and must add 4 GitHub secrets (PULSE_KEYSTORE_BASE64, PULSE_KEYSTORE_PASSWORD, PULSE_KEY_ALIAS, PULSE_KEY_PASSWORD); until then CI builds a debug-signed APK. After adding them, trigger a run and check the release APK is signed. The first signed APK needs one uninstall of any older install. Keep using the noreply email for commits. iOS needs a separate app; not planned.

## (done) Owner wants the repo public (cloud session can't do it)
The owner asked for `vihaanc10/pulsefitnessband` to be public. The cloud session is blocked from changing repository settings (HTTP 403), so the owner must do it: GitHub > repo > Settings > Danger Zone > Change repository visibility. **Before they do:** every commit carries the owner's real email (`(private email removed)`) and `PROTOCOL_NOTES.md` has the band's MAC address. Offer to (a) blank the MAC and (b) rewrite history with GitHub's noreply email so the email isn't public. No passwords or keys were found in the repo.

## Tomorrow, in order
1. Install the newest build on the phone. Easiest: download `Pulse.apk` from the Releases page on the phone (no PC, no developer mode). Then Profile > Add device > tap the band. Check that auto-sync works and Home shows last night's heart rate. Fix whatever breaks (ask for screenshots of the Log tab in the Bluetooth lab).
2. Read steps (check "no data" again after walking) and find out whether the band stores sleep. Look for the sleep command (probably the second service `de5bf728-...` or a command in the public Colmi docs; `github.com` pages are reachable from the cloud session, `notes.tahnok.ca` and `gadgetbridge.org` are blocked).
3. Build sleep, recovery and strain (PRD section 5). If the band has no sleep command, estimate sleep from the heart-rate log plus steps (long low steady stretch with no movement). Resting HR = lowest 30-minute average during sleep. Show confidence labels.
4. Make it a proper app: background sync (foreground service or WorkManager) so data is there when the owner wakes up; notifications for morning recovery.
5. Install-file sharing: the owner wants a simple APK link for a friend. Decide with the owner: add the friend as a GitHub collaborator, or make the repo public, or send `Pulse.apk` by WhatsApp/Drive. Don't change repo visibility without the owner saying so.

## New bands the owner wants
- **Add device / Remove device**: done in the app (Profile). The owner wants it simple: find the Bluetooth, connect, then tell me what it has. No developer mode (the Bluetooth lab already needs none; developer mode was only for installing from the PC).
- **HK50 or HK51 band** (owner is unsure of the exact model; getting one, a friend too; figure it out when it arrives): when it arrives, use Add device, open the Bluetooth lab, connect, send me the Services list (or the exported CSV). Pulse already says "speaks a different Bluetooth language" for bands without service `6e40fff0-...`.
- **Xiaomi Mi Band 6 and 7**: owner wants these supported too. Be honest that these use a different, authenticated protocol (from memory: a per-device secret key is needed, which Gadgetbridge users get from the Xiaomi / Zepp account; unverified). Research when we get to it. Don't promise it is easy.
- Longer term: an adapter layer per band family (PRD section 6, P2).

## Rules to keep
- Only the owner's own band and own captures. Never copy or decompile QWatch Pro or any vendor app.
- Wellness only, no medical claims. Original name and design if shared. Nothing leaves the phone without opt-in.
- Ask before adding libraries or changing the plan. No third-party libraries so far (Compose only, built-in SQLite).
