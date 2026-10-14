package app.sports.core

/** Нейтральный 16-недельный план для тестов: вторник и четверг – силовая и интервалы, суббота – ровный бег. */
object TestPlan {
    const val ID = "test-16w"

    private fun strength(w: Int): WorkoutDef = WorkoutDef(
        "strength", "Силовая", "{n} круга", "Зал", SessionType.STRENGTH, 10, "", emptyList(),
        listOf(
            BlockDef(
                name = "Круг", repeat = if (w % 4 == 0) 2 else 3, adjustable = true,
                steps = listOf(
                    StepDef("Приседания", reps = 12, tempo = 3.0, key = "squat", video = "приседания техника"),
                    StepDef("Планка", sec = 30, key = "plank", video = "планка техника"),
                ),
                restBetween = StepDef("Отдых между кругами", kind = Kind.REST, sec = 60, key = "rest.round"),
            ),
        ),
    )

    private fun intervals(w: Int, tue: Boolean): WorkoutDef = WorkoutDef(
        "treadmill", "Дорожка · Интервалы", "{n} интервалов", "Дорожка", SessionType.TREADMILL, 10, "", emptyList(),
        listOf(
            BlockDef("Разминка", steps = listOf(StepDef("Разминка", kind = Kind.REST, sec = 300, key = "tm.warmup"))),
            BlockDef(
                name = "Интервал", repeat = if (tue) 4 else 5, adjustable = true, groupFormat = "{name} {r}/{n}",
                steps = listOf(
                    StepDef("Бег", sec = 120, key = "tm.hill"),
                    StepDef("Восстановление", kind = Kind.REST, sec = 90, key = "tm.recover"),
                ),
            ),
        ),
    )

    private fun steady(w: Int): WorkoutDef = WorkoutDef(
        "treadmill", "Дорожка · Ровный бег", "{work}", "Дорожка", SessionType.TREADMILL, 10, "", emptyList(),
        listOf(BlockDef("Бег", steps = listOf(StepDef("Ровный бег", sec = (20 + w) * 60, key = "tm.steady")))),
    )

    fun create(): PlanDef {
        val weeks = (1..16).map { w ->
            WeekDef(
                (w + 3) / 4, w % 4 == 0,
                mapOf(2 to listOf(strength(w), intervals(w, true)), 4 to listOf(strength(w), intervals(w, false)), 6 to listOf(steady(w))),
            )
        }
        return PlanDef(ID, "Тестовый план", "Для проверки логики.", listOf(Phase(1, "Основа", "1–4", ""), Phase(2, "Рабочие", "5–8", ""), Phase(3, "Объём", "9–12", ""), Phase(4, "Пик", "13–16", "")), emptyList(), weeks)
    }
}
