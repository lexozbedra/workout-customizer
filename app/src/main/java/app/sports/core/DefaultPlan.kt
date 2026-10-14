package app.sports.core

/** Небольшой пробный план: на его основе строится пример в шаблоне для нейросети. */
object DefaultPlan {
    /* ---------- маленький тестовый план ---------- */

    fun sample(): PlanDef {
        fun home(w: Int) = WorkoutDef(
            "home", "Дом · Круговая", "{n} круга по три упражнения", "Дом", SessionType.STRENGTH, 10, "Коврик и бутылка воды рядом",
            if (w == 2) listOf(OutlineBlock("Вторая неделя", listOf("Те же упражнения, постарайся сделать на пару повторений больше."))) else emptyList(),
            listOf(
                BlockDef(
                    name = "Круг", repeat = 3, adjustable = true,
                    steps = listOf(
                        StepDef("Приседания", reps = 15, tempo = 3.0, key = "squat-home", video = "приседания без веса техника", videoTitle = "Приседания"),
                        StepDef("Отжимания с колен", reps = 8, repsText = "8–10", tempo = 3.0, key = "pushup-knees", video = "отжимания с колен техника", videoTitle = "Отжимания с колен"),
                        StepDef("Боковая планка", sec = 25, sides = listOf("левая сторона", "правая сторона"), key = "sideplank-home"),
                    ),
                    restBetween = StepDef("Отдых", kind = Kind.REST, sec = 45, key = "rest.round", label = "Отдых между кругами"),
                ),
            ),
        )
        val walk = WorkoutDef(
            "walk", "Улица · Ходьба", "{work} быстрым шагом", "Улица", SessionType.TREADMILL, 0, "", emptyList(),
            listOf(
                BlockDef("Разминка", steps = listOf(StepDef("Спокойный шаг", "расслабленные плечи", Kind.REST, 180, key = "walk.warmup"))),
                BlockDef("Основная часть", steps = listOf(StepDef("Быстрый шаг", "дыхание ровное, можно говорить", Kind.WORK, 900, key = "walk.main"))),
            ),
        )
        val week = { w: Int -> WeekDef(1, false, mapOf(1 to listOf(home(w)), 3 to listOf(home(w)), 5 to listOf(walk))) }
        return PlanDef(
            "test-home-2w", "Тест: дом, 2 недели", "Короткий пробный план из дома и прогулки, чтобы проверить импорт и переключение планов.",
            listOf(Phase(1, "Пробный", "1–2", "Ничего тяжёлого: просто проверка приложения.")),
            listOf("Понедельник и среда – круговая дома, пятница – быстрая прогулка."),
            listOf(week(1), week(2)),
        )
    }
}
