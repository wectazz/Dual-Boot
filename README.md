# Dual Boot

A root Android application for managing and switching operating systems via **Android Dynamic System Updates (DSU)**, featuring a **HyperOS 4 Glass** UI powered by **Miuix Compose**.

## Features

- **OS Switching**: Select the active slot (Primary OS / Secondary DSU) and reboot in one tap (`IDynamicSystemService.setEnable` + `reboot`).
- **Streaming Firmware Installation**: Install `.img`, `.xz`, `.gz`/`.gzip`, and `.zip` (DSU package) files directly via `SharedMemory` / ashmem without manual ADB preparation.
- **DSU Slot Management**: Inspect installed systems, discard the DSU slot (`Discard DSU`), monitor space used in `/data/gsi/dsu`, and configure `userdata` allocation size (in GB).
- **Environment Diagnostics**: Verify root access (`uid=0`), `IDynamicSystemService` binding status, and dynamic partition support (`ro.boot.dynamic_partitions`).
- **Miuix Glass UI**: `GlassTopAppBar` with `progressiveBlur`, `GlassNavigationBar`, `GlassDialog`, `PressFeedbackType.Sink`, and theme switching (`System` / `Dark` / `Light`).

## Requirements

- Android 10+ (`minSdk = 29`)
- Dynamic partitions support (`ro.boot.dynamic_partitions = true`)
- **Root access** (Magisk / KernelSU / APatch) — required for `MANAGE_DYNAMIC_SYSTEM` via `libsu` `RootService`
- Unlocked bootloader

## Building (WSL)

The project targets `compileSdk = 37.2` (required by `miuix-glass:0.9.5-rc01`) and builds in WSL:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease --no-daemon
```

Output APK: `app/build/outputs/apk/release/app-release.apk`.

## Credits & License

- DSU engine and privileged root service are based on [VegaBobo/DSU-Sideloader](https://github.com/VegaBobo/DSU-Sideloader) (Apache-2.0).
- UI built with [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (Apache-2.0).
