package ru.capital.idle.core.game

/**
 * Постепенное раскрытие игры. tutorialStep хранится в состоянии:
 * 0..5 — активные шаги гида, DONE — гид пройден (или игрок после перерождения).
 */
object Onboarding {
    const val DONE = 99

    data class Step(val title: String, val text: String)

    val steps = listOf(
        Step("Заработайте первые деньги",
            "Тапните по карте несколько раз. Каждый тап — подработка в свободную минуту."),
        Step("Устройтесь на работу",
            "Появился раздел «Работа». Выберите вакансию курьера — деньги начнут капать сами, каждый игровой день."),
        Step("Настройте свой день",
            "Это распорядок. Сон влияет на бодрость, работа приносит зарплату, учёба пригодится дальше. Попробуйте подвигать ползунки."),
        Step("Получите образование",
            "Открылся раздел «Развитие». Пройдите среднее образование — без него не возьмут на работу получше и не открыть своё дело. Учёба идёт в часы учёбы из распорядка."),
        Step("Откройте своё первое дело",
            "Диплом есть! Появился раздел «Отрасли». Накопите денег и откройте лоток. Внимание: бизнес требует часов управления — выделите их нижним ползунком."),
        Step("Следите за рынком",
            "Это фаза рынка. В Бум доходы бизнеса выше, в Кризис ниже, зато всё дешевле. Дальше открывайте уровни, учитесь и копите. Удачи!"),
    )

    /** Выполнено ли условие текущего шага. */
    fun stepDone(state: GameState): Boolean = when (state.tutorialStep) {
        0 -> state.money >= 10.0
        1 -> state.jobId.isNotEmpty()
        2 -> state.money >= 50.0
        3 -> "school" in state.eduDone
        4 -> state.enterprises.any { it.isNotEmpty() }
        5 -> state.totalEarned >= 1_000.0
        else -> false
    }

    /** Строка прогресса под текстом шага (пустая, если не нужна). */
    fun progressText(state: GameState, currency: Currency): String = when (state.tutorialStep) {
        0 -> "${GameMath.formatMoney(state.money, currency)} / ${GameMath.formatMoney(10.0, currency)}"
        2 -> "накопите ${GameMath.formatMoney(50.0, currency)} на обучение · сейчас ${GameMath.formatMoney(state.money, currency)}"
        3 -> if (state.studyingId.isNotEmpty()) "идёт учёба..." else ""
        4 -> {
            val lotCost = GameMath.openEnterpriseCost(state, 0)   // цена открыть первое предприятие в торговле
            "${GameMath.formatMoney(state.money, currency)} / ${GameMath.formatMoney(lotCost, currency)}"
        }
        else -> ""
    }

    val guideActive: (GameState) -> Boolean = { it.tutorialStep < steps.size }

    // ---------- видимость блоков главной ----------
    fun showJobs(s: GameState) = s.tutorialStep >= 1
    fun showSchedule(s: GameState) = s.tutorialStep >= 2
    fun showIndustries(s: GameState) = s.tutorialStep >= 4
    fun showMarket(s: GameState) = s.tutorialStep >= 4

    // ---------- навигация: 5 групп, замки, анонсы ----------
    private fun veteran(s: GameState) =
        s.bullion > 0 || s.pIncome + s.pNegotiator + s.pStart + s.pStudy > 0

    data class NavGroup(val id: String, val icon: String, val title: String)
    data class Announce(val icon: String, val title: String, val text: String)

    val navGroups = listOf(
        NavGroup("main", "\uD83D\uDCBC", "Капитал"),
        NavGroup("dev", "\uD83C\uDF93", "Развитие"),
        NavGroup("inv", "\uD83D\uDCC8", "Инвест"),
        NavGroup("world", "\uD83C\uDF0D", "Мир"),
        NavGroup("prof", "\uD83D\uDC64", "Профиль"),
    )

    /** Открыта ли группа/подраздел. */
    fun unlocked(s: GameState, id: String): Boolean = veteran(s) || when (id) {
        "main" -> true
        "dev" -> s.tutorialStep >= 3
        "inv" -> "acc" in s.eduDone
        "world" -> s.totalEarned >= 1_000_000.0 || s.milestonesClaimed > 0
        "prof" -> s.tutorialStep >= 2
        // подразделы
        "net" -> s.totalEarned >= 5_000.0
        "pres" -> s.totalEarned >= 1_000_000_000.0
        else -> true
    }

    /** Текст плашки при тапе по замку: заголовок и условие. */
    fun lockText(id: String): Pair<String, String> = when (id) {
        "dev" -> "Развитие" to "Накопите $ 50 на первое обучение — раздел откроется."
        "inv" -> "Инвестиции" to "Пройдите курс «Бухгалтерия» — свободные деньги начнут работать."
        "world" -> "Мир" to "Заработайте первый миллион долларов — узнаете своё место среди богатейших."
        "prof" -> "Профиль" to "Устройтесь на работу — появится ваш профиль и имущество."
        "net" -> "Окружение" to "Заработайте $ 5 000 — полезные знакомства найдут вас сами."
        "pres" -> "Престиж" to "Заработайте первый миллиард — откроется перерождение."
        else -> "" to ""
    }

    /** Анонсы открытий разделов. */
    val announces = mapOf(
        "dev" to Announce("\uD83C\uDF93", "Открылся раздел «Развитие»", "Дипломы открывают работу получше и новые бизнесы"),
        "inv" to Announce("\uD83D\uDCC8", "Открылся раздел «Инвестиции»", "Свободные деньги могут работать сами"),
        "world" to Announce("\uD83C\uDF0D", "Открылся раздел «Мир»", "Цели, рейтинг богатейших и не только"),
        "prof" to Announce("\uD83D\uDC64", "Открылся раздел «Профиль»", "Имущество, статус и хроника вашей жизни"),
        "net" to Announce("\uD83E\uDD1D", "В «Развитии» открылось Окружение", "Наставник и связи ждут"),
        "pres" to Announce("\u267B\uFE0F", "В «Мире» открылся Престиж", "Пора подумать о новой жизни?"),
    )
    val announceIds: Set<String> get() = announces.keys

    // ---------- одноразовые подсказки новых разделов ----------
    data class Hint(val id: String, val title: String, val text: String)

    val hints = mapOf(
        "inv" to Hint("inv", "Открылись инвестиции",
            "Сверху — тихая гавань: депозит и облигации дают гарантированный процент каждый день. Ниже — фонды: индексный фонд и золотой резерв. Их цена медленно колеблется, а держать их выгодно: они платят дивиденды каждый день просто за то, что они у вас есть."),
        "net" to Hint("net", "Окружение решает",
            "Наставник ускоряет учёбу, бизнес-клуб и фонд приносят репутацию. После первого миллиарда элиты начнут давить на ваш доход — репутация смягчает это давление до -70%."),
        "goals" to Hint("goals", "Ваш путь к вершине",
            "За каждую веху богатства начисляются слитки — валюта престижа. Главная цель: стать богаче №1 в мире. И это только начало."),
        "rank" to Hint("rank", "Вы вошли в большую игру",
            "Это топ-1000 богатейших людей планеты по версии Forbes. В рейтинге учитывается весь ваш капитал, а не только наличные: деньги во вкладах, акциях, бизнесах и имуществе не пропадают, а считаются как состояние. Рейтинг обновляется раз в игровые сутки, поэтому место не скачет каждую секунду."),
        "pres" to Hint("pres", "Игра — это круги, а не один забег",
            "Один заход конечен: рано или поздно вы упрётесь в стену. Тогда перерождение даёт слитки, а вечные усиления (особенно множитель дохода) разгоняют следующий круг примерно втрое. Так от забега к забегу вы забираетесь всё выше. Дипломы, репутация и окружение сохраняются."),
        "prof" to Hint("prof", "Ваша жизнь — на виду",
            "Здесь покупается имущество: жильё делает сон эффективнее, транспорт дарит лишние часы в дне, техника ускоряет учёбу. Статус растёт сам — от Работяги до Легенды. А хроника записывает каждый ваш шаг."),
    )
}
