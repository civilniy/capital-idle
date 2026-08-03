# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Капитал** (`ru.capital.idle`) — a native Android money-clicker / idle-tycoon game. Kotlin + Jetpack Compose (Material 3) + Room. Single-module Gradle project (`:app`). UI strings, comments, and game content are in Russian.

- AGP 8.6.1, Kotlin 2.0.20, Compose BOM 2024.09.02, Room 2.6.1 (KSP), coroutines 1.8.1
- compileSdk/targetSdk 35, minSdk 26, JDK 17, Gradle 8.9

## Build & test

The Gradle wrapper is committed and works. From the repo root:

```sh
./gradlew :app:compileDebugKotlin   # fast compile check, no packaging — use this first
./gradlew test                      # run JVM unit tests
./gradlew assembleDebug             # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest --tests "ru.capital.idle.SomeTest.someCase"  # single test
```

If `./gradlew` fails with "permission denied", run `chmod +x ./gradlew`.

Locally, `local.properties` (git-ignored) must point `sdk.dir` at the Android SDK. On GitHub Actions runners the Android SDK is preinstalled and `local.properties` is not needed.

> ℹ️ **JVM unit tests live in `app/src/test/java/ru/capital/idle/`** and cover the pure logic in `core/game/` only (no Android, no Compose, no Room). They are **characterization tests**: they pin the current balance numbers as they are. If you deliberately retune a constant in `GameConfig`/`Economy`/`GameMath`, the matching test will go red — update the expected number in the same commit, and never the other way round.
> Files: `GameTimeTest` (игровые часы, сон, распорядок), `GameMathIncomeTest` (зарплата, предприятия, множители, пассив), `GameMathOfflineTest` (оффлайн-сейф, престиж), `GameMathFormatTest` (форматирование), `EconomyLaddersTest` (лестницы отраслей, цены, ворота доступа), `LifestyleOwnershipTest` (имущество: множества купленных предметов, миграция со старого индекса уровня), `CollectiblesTest` (коллекция: цена от игрового дня, покупка, продажа, прибыль), `CollectiblesPersistenceTest` (коллекция через мапперы `toEntity`/`toState`). Общие помощники — в `GameTestFixtures`.

### Скриншот-тесты вёрстки (Roborazzi)

Ловят механические дефекты разметки — перенос подписи на вторую строку, обрезанное число,
наезжающие элементы — без телефона и эмулятора: Compose рендерится на JVM через Robolectric.

```sh
./gradlew :app:verifyRoborazziDebug   # сверить вёрстку с эталонами (то же делает CI)
./gradlew :app:compareRoborazziDebug  # то же, но с картинками расхождений вместо падения
./gradlew :app:recordRoborazziDebug   # перезаписать эталоны локально
```

Обычный `./gradlew test` скриншот-тесты **пропускает** — они подключаются только задачами
Roborazzi. Иначе прогон без флагов молча перезаписал бы эталоны.

- Тесты живут в `app/src/test/java/ru/capital/idle/screenshot/`, эталоны — в
  `app/src/test/screenshots/` и коммитятся в репозиторий.
- Конфигурация экрана и масштаба шрифта — в `ScreenshotBase.kt` (`Screenshots.DEVICE`,
  `Screenshots.LARGE_FONT`). Устройство: 393×873dp, как OnePlus CPH2653, локаль `ru-rRU` —
  чтобы снимки на сервере совпадали с тем, что видно на телефоне.
- Снимать нужно **чистые** `@Composable` — принимающие данные параметрами, без `GameViewModel`.
  Если нужный кусок экрана завязан на ViewModel, вынеси его отдельной `internal @Composable`
  функцией (так сделано с `ProfileTabsRow`, `MoneyCellsRow`, `CollectionSummary`) — без рефакторинга
  самого экрана.
- Крайние значения важнее типичных: самые большие суммы (в рублях они длиннее всего),
  самые длинные названия, отрицательные значения, масштаб шрифта 1.5.

**Эталоны записываются только на ubuntu.** Рендер текста на macOS и Linux различается,
поэтому единственный источник эталонов — workflow `Record screenshots`
(Actions → Record screenshots → Run workflow, выбрать ветку): он пишет картинки на ubuntu
и коммитит их в ту же ветку. Локальный `recordRoborazziDebug` на Mac даст картинки,
которые CI не примет. Допуск сравнения — 0.1% пикселей: гасит шум сглаживания,
но перенос строки задевает сотни пикселей и всё равно ловится.

**Когда дизайн меняется намеренно** и шаг «Screenshot tests» краснеет: посмотреть артефакт
`screenshot-diff` из упавшего запуска (в нём картинки «ожидалось / получилось»), убедиться,
что расхождение — то самое задуманное изменение, и запустить `Record screenshots` на своей ветке.

**Чего в этих картинках нет:** `Modifier.blur` (единственное место — размытие фона в `GameScreen`)
на JVM не рендерится, размытые слои выходят чёткими. Остальной glass-стиль — полупрозрачные
заливки и радиальные градиенты — снимается как есть.

## Architecture

Three layers under `app/src/main/java/ru/capital/idle/`:

### `core/game/` — pure game logic (no Android deps)
The heart of the app. **`GameState`** is a single immutable data class holding *all* progress (money, prestige upgrades, per-industry enterprises, education, investments, stock market, reputation, lifestyle, chronicle, stats). **`GameMath`** holds all economic formulas as pure functions over `GameState` (income/day, salary, offline gain, business multipliers, number formatting). `GameConfig` holds balance constants.

Content/feature modules, each a self-contained `object`/`enum` of game data + rules:
- `Economy.kt` — game clock (`GameTime`: 1 game hour = 1 real second), `Sleep`, `Jobs`, `Industries` (enterprise ladders), `Education`, `Network`, `MarketPhase`, `Pressure`, `Manager`, `Enterprise`, `BusinessConfig`
- `Investments.kt` — `Asset` (passive deposits), `Exchange`/`Stock`/`StockEvent` (stock market)
- `Prestige.kt` — rebirth for gold bullion + permanent upgrades
- `Lifestyle.kt` — homes/cars/tech, upkeep, `Chronicle`, `Museum`
- `Collectibles.kt` — коллекция: искусство и редкие объекты. Покупаются в любом порядке, держатся все сразу, дорожают линейно от номера игрового дня с потолком `MAX_GROWTH_MULT`. Содержания не требуют и на доход не влияют; дают очки статуса и входят в `netWorth` по текущей цене. Цена — чистая функция дня, без случайности. Там же `CollectibleSet` — наборы предметов, собранные по смыслу: полнота вычисляется из тех же `state.collectibles` (своих полей в `GameState` нет), за полный набор даются очки статуса. Предмет может входить в несколько наборов
- `Auctions.kt` — торги за коллекционные предметы. Пять уникальных предметов (`Auctions.auctionOnly`) убраны из каталога и достаются только с торгов; `AuctionTier` задаёт ворота по репутации и социальному статусу. Вся случайность лота разыгрывается **один раз при старте** и лежит в `Auction`, поэтому `Auctions.advance(state, nowGameH)` — чистая функция от времени: сто мелких шагов дают ровно то же, что один большой. Оффлайн идёт через `skipOffline`, который сдвигает метки самого лота (общие игровые часы, пока игра закрыта, стоят). Молчание зала — отдельный флаг `rivalReplies`, а не «особое» значение времени: после сдвига метки уходят в минус и сентинел вроде `-1` был бы неотличим от честного времени
- `Milestones.kt`, `Onboarding.kt`, `RankModel.kt`/`RankingData.kt`, `CardTier.kt`, `Currency.kt`, `EnterpriseNames.kt`

When adding a feature or economy tuning, prefer extending these modules and `GameMath` rather than putting logic in the UI/ViewModel.

### `data/` — persistence
**Dual-store, JSON-first.** The source of truth on load is `save.json` in internal storage (`GameRepository`), *not* Room. Room is a secondary cache. This is deliberate: `AppDatabase` uses `fallbackToDestructiveMigration()`, so schema bumps wipe the DB — the JSON file survives them. `SaveFile.kt` serializes each field explicitly and fills missing fields from a default `GameEntity`, so old save files load in new versions.

Consequence: **any new `GameState` field must be threaded through all four**: `GameState` → `GameEntity` (+ `toEntity`/`toState` mappers) → `SaveFile.toJson`/`fromJson` → and bump `AppDatabase` version. Skipping `SaveFile` means the field silently won't persist. Collections are stored as CSV/raw strings on the entity.

Когда поле **заменяет** старое, а не добавляется: в `fromJson` дефолтом для нового ключа должна быть «пустышка» (например, `""`), а не значение из `DEFAULT`. Иначе отсутствие ключа в старом файле неотличимо от осознанно записанного дефолта, и мигрировать со старого поля будет уже нечем. Так сделано для имущества: `ownedHomesCsv`/`ownedCarsCsv`/`ownedTechsCsv` читаются с дефолтом `""`, и пустое значение достраивается из legacy-индекса через `Lifestyle.ladderSet(N)` → `{0..N}`. Legacy-ключи `ownedHome`/`ownedCar`/`ownedTech` продолжают писаться, чтобы файл читала и предыдущая версия игры.

### `ui/` — Compose + ViewModel
**`GameViewModel`** (`AndroidViewModel`) is the single owner of runtime state, exposed as `StateFlow<GameState>`. It runs:
- the **game loop** (`startLoop`): a coroutine ticking every 100ms, computing `dtReal` from wall-clock (capped at 1s), calling `tick(dtReal)` which advances income, market, study, etc. via `_state.update`
- **autosave** every 30s (`startAutosave`), plus `persistSoon()` after discrete actions
- **offline progress** (`applyOfflineProgress`): on foreground, credits capped/decayed earnings for time away
- lifecycle wiring lives in `MainActivity`: `ON_STOP` → `onAppBackground()` (stop loop, stamp `lastSeenMillis`, persist); `ON_START`/resume → `onAppForeground()`

`MainActivity` holds tab navigation (string tab ids: `main`, `dev`, `inv`, `world`, profile) and the onboarding gate (`WelcomeScreen` until onboarded). Screens (`GameScreen`, `InvestScreen`, `WorldScreen`, `DevScreen`, `ProfileScreen`, `PrestigeScreen`, etc.) are `@Composable`s taking `vm`. Theme/visual system is in `ui/theme/` (`Color`, `Theme`, `Type`, `Glass` for the glassmorphism styling).

## Правила работы

- **Формат вывода зависит от среды.** В чате (claude.ai) при изменении файла возвращай его полное содержимое от первой до последней строки, а не патч. В Claude Code и в GitHub Actions работай обычными точечными правками — переписывать целиком файлы вроде `GameScreen.kt` (1400+ строк) не нужно и вредно.
- **UI — строго в glass-стиле.** Новые экраны и компоненты держат общий glassmorphism-вид; используй `ui/theme/Glass.kt` и существующую тему, не вводи чужеродных стилей.
- **Держись границ задачи.** Не рефактори файлы, не относящиеся к задаче. Не меняй балансовые константы в `GameConfig`, `Economy` и `GameMath`, если задача явно не про баланс — эти числа выверены, и молчаливая правка ломает прогрессию.
- **Проверяй себя сборкой.** Прежде чем считать работу законченной, прогони `./gradlew :app:compileDebugKotlin` и `./gradlew test`. Если не сходится — не выдавай результат за готовый, опиши, что осталось.

### Только локально (Mac)

- **Тестовое устройство — OnePlus CPH2653, подключается по adb.** Установка/запуск идёт через `adb` (например `adb install -r app/build/outputs/apk/debug/app-debug.apk`); при нескольких устройствах адресуй его через `adb -s`.
- В GitHub Actions это неприменимо: на раннере нет ни устройства, ни эмулятора. Там доступны только компиляция и JVM-тесты.

## Conventions

- All new game logic should stay pure and Android-free inside `core/game/` so it can be reasoned about (and, ideally, tested) in isolation from Compose/Room.
- Money is stored in USD as the base unit; `Currency` converts for display only.
- Game time is compressed: 1 game hour = 1 real second (24 game hours = 24 real seconds/day) — see `GameTime`.

### Правила отображения чисел

Это осознанные решения проекта, а не недосмотр. Не «исправляйте» их походя — если что-то
из-за них не влезает в макет, расширяйте макет, а не меняйте правило.

**0. Карта на главном экране — постоянных пропорций.** Пропорции задаёт константа проекта
`CARD_ASPECT_RATIO` в `ui/GameScreen.kt` (от размера карты в макете — `CARD_REFERENCE_WIDTH_DP`
× `CARD_REFERENCE_HEIGHT_DP`). **Менять её нельзя**, в том числе «подгонять» под внешние
стандарты вроде формата банковской карты: это не декоративное число, а размер, в котором
нарисован макет. Высота считается от ширины и не зависит ни от длины баланса, ни от надбавки
за тап, ни от системного шрифта — иначе разметка под картой прыгает. Содержимое подстраивается
под карту, а не наоборот: кегли внутри карты держатся в постоянном экранном размере
(`textScale` в `CardFace`), а сама карта размечается в опорном размере и целиком
масштабируется под фактическую ширину экрана (`fit` в `CardFace`). Последнее обязательно:
высота карты считается от ширины, поэтому на экране уже опорных 393dp карта ниже опорной —
без общего масштаба низ карты срезал бы строку с именем владельца, а подпись тира садилась бы
на фирменный знак. Правило проверяется тестами в `CardSizeScreenshotTest`.

**1. Полнота чисел.** Денежные суммы показываются целиком до миллиарда: самое длинное
число, отображаемое полностью — `999 999 999`. С миллиарда включается сокращение (`B`, `T`).
В игре про деньги важно видеть настоящую сумму, а не «1,0B». Сокращать числа ниже миллиарда
ради экономии места нельзя — вместо этого расширяйте макет. Любой элемент, где показываются
деньги, обязан вмещать `999 999 999` при обычном и увеличенном (1.5) системном шрифте.

Следствие: в одном списке рядом могут стоять `₽ 368 500`, `₽ 73 700 000` и `₽ 73,7B` —
это правильное поведение, а не разнобой.

**2. Положение символа валюты** зависит от смысла числа:

| Смысл | Запись | Пример |
|---|---|---|
| сумма, количество | символ ПЕРЕД числом | `$ 188 935 178`, `+$ 42 541 917` |
| скорость, поток за время | символ ПОСЛЕ числа | `+127 588 552 $/день`, `+10,2B ₽/день` |

Разный вид записи в одном списке — норма, если у чисел разный смысл. `formatMoney` ставит
символ впереди (сумма); для потоков символ добавляется вручную после числа через `formatAmount`.

**3. Разделитель — всегда запятая**, независимо от языка системы. Касается денег, процентов
и любых дробных чисел. Для этого есть `GameMath.decimal(value, digits)`: он форматирует через
`Locale.ROOT` и меняет точку на запятую. **Не используйте `String.format("%.1f", …)` напрямую** —
на английском телефоне выйдет точка, на русском запятая, и на одном экране получится разнобой.

То же касается всего, что молча зависит от локали: `String.format` с `%d` (в арабской локали
другие цифры), `Calendar.getInstance()` (в тайской — буддийский календарь, год отличается на 543),
`uppercase()` (в турецкой `i` превращается в `İ`). Везде задавайте `Locale.ROOT` явно.
