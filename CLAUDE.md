# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Капитал** (`ru.capital.idle`) — a native Android money-clicker / idle-tycoon game. Kotlin + Jetpack Compose (Material 3) + Room. Single-module Gradle project (`:app`). UI strings, comments, and game content are in Russian.

- AGP 8.6.1, Kotlin 2.0.20, Compose BOM 2024.09.02, Room 2.6.1 (KSP), coroutines 1.8.1
- compileSdk/targetSdk 35, minSdk 26, JDK 17

## Build & test

**The Gradle wrapper jar is intentionally absent.** `./gradlew` is a stub script that only prints instructions — it does *not* build. To build from the terminal you must first generate the wrapper (requires a system Gradle 8.9):

```sh
gradle wrapper           # one-time: creates gradle/wrapper/gradle-wrapper.jar
./gradlew assembleDebug  # build debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew test           # run JVM unit tests
./gradlew :app:testDebugUnitTest --tests "ru.capital.idle.SomeTest.someCase"  # single test
```

The normal workflow is Android Studio (Hedgehog+): open the folder, let it sync (it fetches the wrapper and deps), Run or Build > Build APK. `local.properties` (git-ignored) must point `sdk.dir` at the Android SDK. Install/run on a device over adb, e.g. `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

> ℹ️ **There are currently no tests.** `app/src/test/` is empty — the former `GameMathTest.kt` described an obsolete economy model (`GameConfig.generators`, `GameMath.costFor/bulkCost/maxBuyable`) and was removed. Write any new tests against the current `GameMath`/`Economy` API.

## Architecture

Three layers under `app/src/main/java/ru/capital/idle/`:

### `core/game/` — pure game logic (no Android deps)
The heart of the app. **`GameState`** is a single immutable data class holding *all* progress (money, prestige upgrades, per-industry enterprises, education, investments, stock market, reputation, lifestyle, chronicle, stats). **`GameMath`** holds all economic formulas as pure functions over `GameState` (income/day, salary, offline gain, business multipliers, number formatting). `GameConfig` holds balance constants.

Content/feature modules, each a self-contained `object`/`enum` of game data + rules:
- `Economy.kt` — game clock (`GameTime`: 1 game hour = 1 real second), `Sleep`, `Jobs`, `Industries` (enterprise ladders), `Education`, `Network`, `MarketPhase`, `Pressure`, `Manager`, `Enterprise`, `BusinessConfig`
- `Investments.kt` — `Asset` (passive deposits), `Exchange`/`Stock`/`StockEvent` (stock market)
- `Prestige.kt` — rebirth for gold bullion + permanent upgrades
- `Lifestyle.kt` — homes/cars/tech, upkeep, `Chronicle`, `Museum`
- `Milestones.kt`, `Onboarding.kt`, `RankModel.kt`/`RankingData.kt`, `CardTier.kt`, `Currency.kt`, `EnterpriseNames.kt`

When adding a feature or economy tuning, prefer extending these modules and `GameMath` rather than putting logic in the UI/ViewModel.

### `data/` — persistence
**Dual-store, JSON-first.** The source of truth on load is `save.json` in internal storage (`GameRepository`), *not* Room. Room is a secondary cache. This is deliberate: `AppDatabase` uses `fallbackToDestructiveMigration()`, so schema bumps wipe the DB — the JSON file survives them. `SaveFile.kt` serializes each field explicitly and fills missing fields from a default `GameEntity`, so old save files load in new versions.

Consequence: **any new `GameState` field must be threaded through all four**: `GameState` → `GameEntity` (+ `toEntity`/`toState` mappers) → `SaveFile.toJson`/`fromJson` → and bump `AppDatabase` version. Skipping `SaveFile` means the field silently won't persist. Collections are stored as CSV/raw strings on the entity.

### `ui/` — Compose + ViewModel
**`GameViewModel`** (`AndroidViewModel`) is the single owner of runtime state, exposed as `StateFlow<GameState>`. It runs:
- the **game loop** (`startLoop`): a coroutine ticking every 100ms, computing `dtReal` from wall-clock (capped at 1s), calling `tick(dtReal)` which advances income, market, study, etc. via `_state.update`
- **autosave** every 30s (`startAutosave`), plus `persistSoon()` after discrete actions
- **offline progress** (`applyOfflineProgress`): on foreground, credits capped/decayed earnings for time away
- lifecycle wiring lives in `MainActivity`: `ON_STOP` → `onAppBackground()` (stop loop, stamp `lastSeenMillis`, persist); `ON_START`/resume → `onAppForeground()`

`MainActivity` holds tab navigation (string tab ids: `main`, `dev`, `inv`, `world`, profile) and the onboarding gate (`WelcomeScreen` until onboarded). Screens (`GameScreen`, `InvestScreen`, `WorldScreen`, `DevScreen`, `ProfileScreen`, `PrestigeScreen`, etc.) are `@Composable`s taking `vm`. Theme/visual system is in `ui/theme/` (`Color`, `Theme`, `Type`, `Glass` for the glassmorphism styling).

## Правила работы

- **Всегда выдавай файлы целиком, не диффы.** При изменении файла возвращай его полное содержимое от первой до последней строки, а не фрагмент/патч.
- **UI — строго в glass-стиле.** Новые экраны и компоненты держат общий glassmorphism-вид; используй `ui/theme/Glass.kt` и существующую тему, не вводи чужеродных стилей.
- **Тестовое устройство — OnePlus CPH2653, подключается по adb.** Установка/запуск на реальном девайсе идёт через `adb` (например `adb install -r app/build/outputs/apk/debug/app-debug.apk`); при нескольких устройствах адресуй его через `adb -s`.

## Conventions

- All new game logic should stay pure and Android-free inside `core/game/` so it can be reasoned about (and, ideally, tested) in isolation from Compose/Room.
- Money is stored in USD as the base unit; `Currency` converts for display only.
- Game time is compressed: 1 game hour = 1 real second (24 game hours = 24 real seconds/day) — see `GameTime`.
