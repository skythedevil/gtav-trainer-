# GTA V PS5 Menu Installer for Android

**Install a GTA V Story Mode mod menu on a jailbroken PS5 straight from your phone. No PC needed.**

[**⬇ Download the APK**](https://github.com/skythedevil/gtav-trainer-/releases/latest/download/app-debug.apk) · [How to install](#how-to-use-it) · [Troubleshooting](#something-went-wrong) · [Credits](#credits)

> **Credit where it's due.** The menu itself, the game profiles and the installer logic were created by **[GronedWaffel](https://github.com/GronedWaffel)** in the project
> **[GronedWaffel/gta-v-ps5-menu](https://github.com/GronedWaffel/gta-v-ps5-menu)**. This repository is an **unofficial Android port of that project's Windows installer**. It bundles GronedWaffel's menu files unchanged. Please star and support the original project.

*Keywords for search: GTA V PS5 trainer, PS5 mod menu, GTA 5 PS5 jailbreak menu, PS5Debug-NG, Android APK, PS5 13.60.*

---

## What is this?

GronedWaffel's menu runs **inside GTA V on your PS5**. To get it there, the original project offers a Windows app that copies it into the running game over your Wi-Fi.

This app does the same copy step **from an Android phone**. After it finishes, the menu lives in the game and you can close the app. The menu and all of its options are exactly the same as the original.

## What you need

- A **jailbroken PS5** (firmware 11.00 to 13.60, same list as the original project) with **PS5Debug-NG 1.3.2** running. This app does not jailbreak anything.
- **GTA V** on PS5, one of these versions: **PPSA04263 (01.000.000)** or **PPSA04264 (01.010.002)**.
- An **Android phone** (Android 8 or newer) on the **same Wi-Fi** as the PS5.

## How to use it

1. **Download the APK** with the link above, open the file, and allow "install from this source" if Android asks.
2. On the PS5: jailbreak, load **PS5Debug-NG 1.3.2**, start **GTA V**, and enter **Story Mode**. Wait until your character is in control and the game isn't paused.
3. Find your PS5's IP address: *Settings → Network → View Connection Status.*
4. Open the app, type the IP address (leave the port as **744**).
5. Tap **Check connection**. This only reads from the game and changes nothing. You should see a green check mark and the game version.
6. Tap **Install in-game menu**.
7. In GTA, press **L1 + D-pad Right** to open the menu.

You'll need to run the install again each time you restart GTA.

### Controls

| Button | What it does |
|---|---|
| L1 + D-pad Right | Open / close the menu |
| D-pad Up / Down | Move through lists (hold to repeat) |
| Cross | Select / toggle |
| Circle | Back / cancel |

### What's in the menu

Player (god mode, infinite ammo, super run/jump, never wanted), weapons, a vehicle spawner and vehicle tweaks, character and clothing changer, world options (teleport to waypoint, weather, time), and story options. The full, up-to-date list is in the [original README](https://github.com/GronedWaffel/gta-v-ps5-menu#options).

Some options (money, skills, trophy requests) can affect your autosaves. The menu asks for confirmation first.

## Something went wrong?

| What the app says | What to do |
|---|---|
| *Could not reach …* | Check the IP address, that the PS5 and phone share the same Wi-Fi, and that PS5Debug-NG is running. |
| *Load PS5Debug-NG 1.3.2 first* | You have a different debugger version loaded. Use 1.3.2. |
| *GTA V is not running* | Start GTA V on the PS5 and wait for it to finish loading. |
| *Story Mode only* / *Enter Story Mode…* | Load into Story Mode and wait for your character to appear. GTA Online isn't supported. |
| *This GTA V version isn't supported* | Your game version isn't one of the two listed above. Nothing was changed. |
| *PS5 firmware … is not in this menu's supported list* | Your firmware is outside 11.00 to 13.60. |
| *PLAYER_ID is already hooked…* | Restart GTA V, then install again. |

Still stuck? [Open an issue](https://github.com/skythedevil/gtav-trainer-/issues) and include the exact message from the app, your PS5 firmware and your GTA version.

## Safety and fair use

- For **single-player (Story Mode) use on your own console.** The app and menu refuse to run in GTA Online. Don't use mods online.
- The app checks the game's fingerprints, refuses unknown versions, and verifies every memory write. A bug or a new game update can still crash the game. Use at your own risk.
- This project is **unofficial**. It isn't affiliated with or endorsed by GronedWaffel, Rockstar Games, Sony, or the PS5Debug-NG authors. It contains no game files and does not help you pirate anything.

## Build it yourself

You don't need a computer set up for Android. Fork this repository, and **GitHub Actions** builds the APK automatically on every change (see `.github/workflows/build-apk.yml`). The workflow runs a self-test against a pretend PS5 and then publishes `app-debug.apk` under *Releases*.

To run the self-test locally: `sh tools/selftest/run.sh` (needs Java).

Project layout and history are in [PROJECT_NOTES.md](PROJECT_NOTES.md).

## Credits

- **[GronedWaffel](https://github.com/GronedWaffel): [gta-v-ps5-menu](https://github.com/GronedWaffel/gta-v-ps5-menu).** The in-game menu (`gta-bridge.bin`), the game profiles, the native-function tables and the installer protocol that this app follows. Bundled unchanged; the source is in `menu-source/`.
- **Pharaoh2k / OSR and PS5Debug-NG contributors**: [PS5Debug-NG](https://github.com/Pharaoh2k/ps5debug-NG), the debugger this app talks to. Not included here.
- **John Törnblom and contributors:** [PS5 payload SDK](https://github.com/ps5-payload-dev/sdk), and the etaHEN / libNineS projects that the menu's source builds on.
- All other references and data sources the original menu is built on are listed in [CREDITS.md](CREDITS.md) and [CREDITS-upstream.md](CREDITS-upstream.md).

The Android installer code in `app/` and `tools/` was written with **Claude (Anthropic)** as a port of the original installer.

## Licence

**GPL-3.0-or-later**, same as the original project. See [LICENSE](LICENSE). Because the menu binary is included, its source is provided in `menu-source/`.
