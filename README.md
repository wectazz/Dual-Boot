# Dual Boot

Root-приложение для управления и переключения систем через **Android Dynamic System Updates (DSU)** с интерфейсом в стиле **HyperOS 4 Glass** на базе **Miuix Compose**.

## Возможности

- **Переключение ОС**: выбор активного слота (Primary OS / Secondary DSU) и перезагрузка в один тап (`IDynamicSystemService.setEnable` + `reboot`).
- **Потоковая установка прошивок**: установка `.img`, `.xz`, `.gz`/`.gzip` и `.zip` (DSU-пакетов) напрямую через `SharedMemory` / ashmem без ручной подготовки через ADB.
- **Управление DSU-слотом**: просмотр установленных систем, удаление DSU (`Discard DSU`), контроль занятого места в `/data/gsi/dsu` и настройка размера `userdata` (в ГБ).
- **Диагностика окружения**: проверка root-доступа (`uid=0`), подключения к `IDynamicSystemService` и поддержки динамических разделов (`ro.boot.dynamic_partitions`).
- **Miuix Glass UI**: `GlassTopAppBar` с `progressiveBlur`, `GlassNavigationBar`, `GlassDialog`, `PressFeedbackType.Sink` и переключение темы (`System` / `Dark` / `Light`).

## Требования

- Android 10+ (`minSdk = 29`)
- Поддержка динамических разделов (`ro.boot.dynamic_partitions = true`)
- **Root-доступ** (Magisk / KernelSU / APatch) — требуется для доступа к `MANAGE_DYNAMIC_SYSTEM` через `libsu` `RootService`
- Разблокированный загрузчик

## Сборка (WSL)

Проект использует `compileSdk = 37.2` (требуется для `miuix-glass:0.9.5-rc01`) и собирается в WSL:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease --no-daemon
```

Готовый APK: `app/build/outputs/apk/release/app-release.apk`.

## Благодарности и лицензия

- Движок DSU и привилегированный root-сервис основаны на [VegaBobo/DSU-Sideloader](https://github.com/VegaBobo/DSU-Sideloader) (Apache-2.0).
- Интерфейс построен на [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (Apache-2.0).
