# Clean Your TV phone app

An Android phone app that applies the tested lists in [`examples/`](../examples) to a TV over
Wi-Fi ADB. It writes a restore record before the first change, disables with
`pm disable-user --user 0` one package per shell call, and can put everything back from the phone.
The reference launcher in [`launcher/`](../launcher) can be installed and made Home in the same run.

The app talks to `adbd` directly through [Kadb](https://github.com/flyfishxu/Kadb); no `adb` binary
and no computer are involved.

## Build

Requirements: JDK 17, Android SDK platform 36 and build-tools 36.0.0.

```sh
cd phone-app
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Bundled lists

The lists and `catalog.json` under `app/src/main/assets/` are generated from `examples/`. Re-run
this after changing a list, and commit the result:

```sh
python3 phone-app/tools/bundle_lists.py
```

## What it refuses to do

The app keeps the safety rules from [`PROMPT.md`](../PROMPT.md) and `tools/disable.sh`:

- Never uninstalls, never uses root, never flashes anything.
- Refuses Android core packages (`com.android.*`, `com.google.android.*`, the permission
  controller, package installer, SystemUI, Settings, providers) with no override.
- Keeps the setup wizard when it owns the saved Wi-Fi, keeps the current Home app when no other
  usable launcher is installed, keeps packages asking for `TV_INPUT_HARDWARE`, and keeps a CVTE
  board's stock launcher.
- Installs the launcher, sets Home, and confirms Home answers before disabling anything else.
- Writes the backup before the first change; the restore record is also saved as a zip with a
  `restore.sh` for a computer.

It does not set up Private DNS on a list's behalf beyond the explicit optional switch, and it never
edits a bundled list.

## Tests

```sh
cd phone-app
./gradlew testDebugUnitTest
```

The JVM tests cover the list parser, the core-package and Home guards, and the analysis pass
against a fake TV shell.
