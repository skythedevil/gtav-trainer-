# PROJECT NOTES — GTA V PS5 Menu Installer (Android)

> Read this file first at the start of every new chat. Update it after EVERY change.
> The person who owns this project is not a programmer: explain things simply, make the technical decisions, and fix bugs for them.

## What this project is
An Android app (APK) that does what the Windows installer of
https://github.com/GronedWaffel/gta-v-ps5-menu does: it installs an in-game cheat menu into GTA V (Story Mode only) on a **jailbroken PS5** by talking to PS5Debug-NG over Wi-Fi.

Important: the cheats themselves live in a small program (`gta-bridge.bin`) that runs INSIDE the game after install. The phone app only installs it. After install, the phone can be closed. All menu functions are therefore identical to the original.

## Current status (v1.0.1)
| Area | State |
|---|---|
| Android app screen (IP, port, Check, Install, log) | Written, compiles against Android API 33 stubs |
| Installer logic (port of the original JS installer) | Written, passes 25 checks against a pretend PS5 (`tools/selftest`) |
| Bundled menu files for both game builds (PPSA04263 01.000.000, PPSA04264 01.010.002) | Rebuilt by CI from the local C source; both profile-specific binaries are packaged into the APK |
| Automatic APK build on GitHub (`.github/workflows/build-apk.yml`) | Updated to compile both native bridges with Zig, then run installer self-tests and build the APK; feature-branch build pending |
| Real PS5 test | Install confirmed working on the owner's real PS5 (2026-10-27) |
| Gradle build / APK file | Built by GitHub Actions. Direct link: https://github.com/skythedevil/gtav-trainer-/releases/latest/download/app-debug.apk |

## How the installer works (same order as upstream)
1. Connect to PS5Debug-NG (port 744). Must say PS5Debug-NG v1.3.2 and a supported firmware (default target 13.60).
2. Find exactly one `eboot.bin` process (GTA). Match title/content ID and four-to-five byte-fingerprints of the game's code against the bundled profiles. Unknown build = refuse, nothing written.
3. Check Story Mode (online flag = 0) and that the player character is loaded and healthy.
4. Find the game's main script, find the `PLAYER_ID` function slot, ask the console for 128 KB of executable memory in the game (separate connection), copy the menu image there in 4 KB pieces (each write: check memory is as expected, write, read back), save recovery info, point `PLAYER_ID` at the menu, set the "enabled" flag.
5. Wait one second and check the menu's frame counter moves (proof it runs).
Re-running install after it is already installed re-attaches (uses saved recovery info) instead of installing twice. Restart GTA before switching to another menu build.

## Files
- `app/src/main/java/.../core/` — logic with no Android parts: `Installer` (steps above), `Ps5Debug` (network), `MiniJson`, `Bytes`, `InstallException`.
- `app/src/main/java/.../MainActivity.java` — the single screen.
- `app/src/main/assets/menu/` — upstream menu binaries + profiles (GPL-3.0). Do not edit by hand.
- `menu-source/` — upstream menu source (C), including the speedometer feature.
- `scripts/build-gta-bridge.mjs` and `scripts/build-gta-vehicles.mjs` — reproducible native bridge build scripts used by CI.
- `tools/selftest/` — pretend PS5 + tests. Run: `sh tools/selftest/run.sh`.
- `.github/workflows/build-apk.yml` — builds `app-debug.apk` on GitHub, runs the self-test first.

## Decisions made (and why)
- Written in Java (not Kotlin) so the logic can be compiled and tested outside Android Studio.
- minSdk 26 (Android 8+), targetSdk 34 — avoids newer Android restrictions on local-network access for now.
- Debug-signed APK: fine for installing on your own phone; no Play Store.
- Safety kept identical to upstream: refuse unknown game builds, Story Mode only, verified writes.
- Not included from upstream: the experimental ELF payload, vehicle spawner data on the phone side (all in-game menu code is inside `gta-bridge.bin`).

## Known limits / risks
- Needs a jailbroken PS5 (firmware 13.60) with PS5Debug-NG 1.3.2 already running. The app does not jailbreak.
- Story Mode only. Not for GTA Online.
- Some menu actions (money, skills, trophies) can affect autosaves.
- Phone and PS5 must be on the same Wi-Fi. If a future Android version blocks local-network access, the app may need an extra permission.

## Speedometer feature (feature branch)
- Garage menu: `Speedometer` toggles the HUD on/off; `Speedometer style` switches Digital/Gauge; `Speed unit` switches KM/H/MPH.
- Speed is read from the vehicle entity each game frame and rendered only while driving in Story Mode. The speedometer is hidden while the trainer menu is open.
- CI is configured to rebuild both supported game-profile binaries before packaging. Do not install the feature APK until the feature-branch workflow succeeds and the new HUD is tested on the console.

## Change log
- 2026-10-27 v1.0.1: Credits added: new README (credit to GronedWaffel, install guide, troubleshooting), CREDITS.md, in-app credit line.
- 2026-10-27: Project pushed to github.com/skythedevil/gtav-trainer-; first GitHub build succeeded; workflow now also publishes the APK as a Release (stable download link).
- 2026-10-26 v1.0.0: First version. Core installer ported from upstream commit on `main`, UI, CI workflow, self-test (25 passing).

## Next steps / ideas
- First real-console test: use "Check connection" first, then "Install".
- Possible: show detected game version/firmware in a status line; remember last result.
