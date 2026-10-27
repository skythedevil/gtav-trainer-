# Credits

## Original project
This app is an **unofficial Android port of the installer** from
**[GronedWaffel/gta-v-ps5-menu](https://github.com/GronedWaffel/gta-v-ps5-menu)** by **GronedWaffel**.

Everything below is taken from or based on that project and remains the work of its authors:

| What | Where in this repo | Origin |
|---|---|---|
| In-game menu program | `app/src/main/assets/menu/**/gta-bridge.bin` | GronedWaffel, unchanged |
| Game profiles (title IDs, fingerprints, addresses) | `app/src/main/assets/menu/**/profile.json`, `profiles.json` | GronedWaffel, unchanged |
| Game function address tables | `app/src/main/assets/menu/**/native-addresses.json` | GronedWaffel, unchanged |
| Menu source code | `menu-source/` | GronedWaffel, unchanged |
| Install procedure (what is checked, written and verified, in what order) | `app/src/main/java/.../core/Installer.java` | Re-implemented in Java from GronedWaffel's JavaScript installer (`src/*.mjs`) |
| PS5Debug wire protocol use | `app/src/main/java/.../core/Ps5Debug.java` | Re-implemented from the same installer |

The upstream project's own full credit list (PS5 payload SDK, etaHEN/libNineS, PS5Debug-NG, native databases, hash translation tables, vehicle data and more) is kept verbatim in [CREDITS-upstream.md](CREDITS-upstream.md).

## This Android port
- Android app, Java port of the installer, self-test with a pretend PS5, and GitHub build setup: **Sky ([@skythedevil](https://github.com/skythedevil))**, written with **Claude (Anthropic)**.

## Not included
- **PS5Debug-NG** by Pharaoh2k / OSR and contributors is required on the console but is **not bundled**: https://github.com/Pharaoh2k/ps5debug-NG
- No Rockstar or Sony files are included.

## Licence
GPL-3.0-or-later. See [LICENSE](LICENSE). Referenced projects keep their own authorship and licences.
