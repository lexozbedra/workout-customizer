# Workouts

**English** | [Русский](#тренировки)

A personal Android app for training plans. A plan is a JSON file you import, and you can keep several plans and switch between them. It works offline. All data (profile, progress, plans) lives as plain files in a folder you choose.

**Contents**

1. [Installation and getting started](#installation-and-getting-started)
2. [Features](#features)
3. [Rules and logic](#rules-and-logic)
4. [Background work and sound](#background-work-and-sound)
5. [Data and storage](#data-and-storage)
6. [Architecture](#architecture)
7. [Tests](#tests)

## Installation and getting started

1. **Download and install the APK** on your phone: [app-debug.apk](https://github.com/lexozbedra/workout-customizer/releases/latest/download/app-debug.apk).
2. **Open the app.** Allow notifications and choose a folder for your data: create a folder (for example "Workouts") and tap **Use this folder**.
3. **Add a plan.** You can import your own plan file (**Plans → Import from file**) or get a template with the **Plan template** button and have an AI assistant generate a plan for you. Once a plan is started, the app shows the workout calendar and statistics.
4. **Fill in your profile.** On the **Profile** tab enter your name, height, sex and date of birth. Enter your weight on the **Body** tab: it is used for BMI, body fat, goals and the weight forecast.

## Features

| Area | What is there |
|---|---|
| Today screen | Workouts for the selected day, a week strip with colored status dots, start, mark done, move, skip |
| Player | Work and rest timers, two distinct signals, pause, restart current step, back, next, finish early |
| Progress | Plan completion bar, total time, gym days, active days, approximate calories, a log of skipped and partial workouts |
| Plan | Weeks expand into workouts, read-only preview, exercise videos |
| Plans | A collection of plans from files: import, start, pause, resume, finish, delete. Only one plan is active, progress of every plan is kept. Statuses: not started / active / on pause / finished |
| Profile | Name, height, weight, sex, date of birth (age is computed), the data folder |
| Body | Weight history with a chart, BMI, body fat estimate, a goal (weight, BMI or body fat), a forecast of when the goal is reached, a monthly reminder to weigh in |
| Plan template | One button copies a prompt with the full file format and a working example, to give to an AI assistant that writes a plan file for you |
| Tuning | Time, reps, number of rounds and rest can be edited in the app, no code changes |
| Schedule | Plan start date, vacation with a reason (shifts the plan), moving a workout within the week, paging through weeks on the Today screen (arrows or swipe) |
| Floating timer | While a workout runs and the app is in the background, a small bar at the bottom of the screen shows "Work 0:45" / "Rest 1:10" with a pause button; tap it to open the full screen, × hides it without stopping the workout (needs the "Display over other apps" permission, can be turned off on the Plan tab) |

The gym strength session and the treadmill session are always two separate workouts with their own start: the floors differ, and there is time needed for the stairs and water in between.

## Rules and logic

### Workout statuses

| Status | How it is set |
|---|---|
| Done | The player reached the end, or "Mark done" was pressed |
| Partially done | "Finish early" in the player: a percentage (pre-filled from elapsed time, editable) and a reason: "Ran out of time" / "Tired" / "Other" |
| Skipped | The "Skip" button |
| Auto-skipped | The workout's day has passed with no mark and no move. It can still be marked done afterwards |
| Upcoming | Everything else |

A partial workout counts toward progress proportionally: 50% is half a workout. Entering 100% makes it a fully completed workout.

### Vacation

A reason is required: trip, illness or other ("other" requires a comment). Everything scheduled from the vacation start date is pushed forward by the vacation length. The "whole weeks" mode rounds the shift up to full weeks so workouts stay on Tuesday, Thursday and Saturday.

### Moving a workout

Only within one calendar week (Monday to Sunday). It must be done before 21:00 of whichever day comes first: the original or the new one. Tuesday to Monday: before 21:00 on Monday. Tuesday to Wednesday: before 21:00 on Tuesday. Vacation days are not valid targets.

### Tuning workouts

Edits are stored on top of the plan and never change its code. You can change the time of each step, the number of reps, an offset to the number of rounds (strength) or intervals (treadmill), and the rest between rounds and supersets. An edit applies to one workout or to all similar ones. For rep-based exercises the time is derived from the reps; if you set the time manually, it stays as set. Reset buttons: for one workout and for all edits at once. Edits are applied when a workout starts, a workout in progress does not change.

### Statistics

- **Progress**: sum of completion fractions ÷ number of workouts in the plan.
- **Time**: actual workout time; for workouts marked by hand, the planned duration is used.
- **Gym days**: days with a completed or partial strength workout. **Active days**: days with any such workout.
- **Calories**: average intensity (MET) per workout stage, weighted by time, × body weight × time. About 10 for hill running, 8.5 for steady running, 8.3 for jogging, 7 for incline walking, 5 for strength sets, 2.5 for rest between sets. Accuracy is roughly ±30%. Body weight comes from the Profile tab (65 kg if not set).

### Body metrics

- **BMI** = weight ÷ height². Labels: below 18.5 "below normal", up to 25 "normal", up to 30 "above normal", higher "obesity".
- **Body fat** is estimated with the Deurenberg formula: 1.2 × BMI + 0.23 × age − 10.8 × (1 for men, 0 for women) − 5.4. It needs height, sex and age (18 or older). The error can be several percentage points, so treat it as a rough guide, not a medical measurement.
- **Goal**: set a weight, a BMI or a body fat percentage. BMI and body fat goals are converted to a target weight. If several are set, the first of weight, BMI, body fat is used.
- **Forecast**: a straight trend line through the weight entries of the last 90 days (at least two entries at least a week apart). If the weight moves toward the goal, the app shows the date it would be reached at that pace. If it is flat or moves away, it says so instead of guessing.
- **Reminder**: a banner on the Today tab and a notification around 10:00 when the last weight entry is 30 days old or there are none (at most once a week until you record a weight). It can be turned off on the Body tab. The alarm is set again after a phone restart.

### Exercise videos

The "Learn more" button opens a video search on YouTube (in a browser or the YouTube app). This is the only place that uses the internet. The app does not need the INTERNET permission, since the link is opened by the system.

## Background work and sound

- A workout runs in a **foreground service** with a notification (pause, Next and Stop buttons in the shade). The timer is based on the system clock and does not depend on the screen; a wake lock keeps the CPU awake.
- **Signals**: two high beeps (1500 Hz) mean work starts, one long low beep (620 Hz) means rest, two low beeps mean the workout is over. The sounds are generated in code.
- A signal **never pauses other apps' music**: audio focus is requested in ducking mode (this can be turned off on the Plan tab), so music or video gets quieter for a couple of seconds and then returns.
- On phones with aggressive battery saving (Xiaomi, Huawei, Samsung) set the app to **Battery – Unrestricted** and allow autostart and background activity. Allow notifications on first launch.

## Data and storage

On first launch the app asks you to pick a folder with the system folder picker (once; access is remembered). Everything lives there as ordinary files, so it survives reinstalling the app and can be copied to another phone. Only the link to the folder is kept inside the app. If you pick a folder that already has data, the app uses it; if it is empty, the current data is moved there.

```
<your folder>/
├── profile.json        name, height, weight, sex, birth date, settings, which plan is active
├── progress.json       per-plan state: status, start date, results, moves, vacations, edits
└── plans/
    └── <your plan>.json
```

A workout id looks like `w{week}-{ISO weekday}-{workout id from the plan file}`, for example `w3-4-treadmill`. An edit key is `w3-4-strength/squat` for one workout, or just `squat` for all of them. Progress of an older version (kept in app storage) is not migrated.

### Plan files

A plan file is JSON (`format: "workout-plan"`). It describes phases, weeks, days (ISO weekday numbers 1–7), workouts, blocks (rounds, supersets, intervals) and steps (seconds, or reps with a tempo). A week that repeats an earlier one is written as `"copyOf": N`. The app validates a file on import and shows a readable message for the first problem found. Importing a file with an id that already exists updates the plan and keeps its progress.

Ready-made files are in the `plans/` folder of the repository: `test-home-2w.json` (a small example plan) and `TEMPLATE.txt` (the same text the **Plan template** button copies: instructions for an AI assistant, the format, and a working example). To use a plan: copy the file to the phone, then **Plans → Import from file**, then **Start**.

## Architecture

The code is split into pure logic (Kotlin without Android, testable on the JVM) and the Android-dependent part.

```
app/src/main/java/app/sports/
├── core/                    pure logic
│   ├── Model.kt             steps, sessions, edits (Tuning)
│   ├── PlanDef.kt           a plan as data and how it turns into workouts and steps
│   ├── PlanFormat.kt        plan file (JSON): reading, validation, writing, AI template
│   ├── Json.kt              small JSON parser and writer
│   ├── DefaultPlan.kt       a small sample plan (used in the AI template)
│   ├── Body.kt              BMI, body fat, goal, forecast, reminder rule
│   ├── Data.kt              profile, per-plan state, statuses, folder abstraction, Repo
│   ├── Schedule.kt          dates: vacation, moves, the 21:00 rule
│   ├── Results.kt           statuses, outcomes, reasons, auto-skip
│   └── Stats.kt             progress, time, days, calories
├── workout/
│   ├── WorkoutEngine.kt     workout clock: pause, seek, independent of the screen
│   ├── WorkoutService.kt    foreground service: timer, signals, notification
│   └── Signals.kt           signal generation and ducking of other apps' audio
├── Store.kt                 loads and saves the data folder, exposes the state to the UI
├── WeightReminder.kt        monthly weigh-in notification
├── SafFolder.kt             the user's folder through the system file access API
├── MainActivity.kt
└── ui/                      Jetpack Compose
    ├── Root.kt              tabs: Today / Progress / Plan / Plans / Profile, first-launch folder choice
    ├── PlansScreen.kt       plan collection
    ├── BodyScreen.kt        weight, BMI, body fat, goal, forecast
    ├── ProfileScreen.kt     profile and data folder
    ├── Screens.kt           today, workout details, plan
    ├── ProgressScreen.kt    progress and statistics
    ├── PlayerScreen.kt      player
    ├── EarlyFinish.kt       finishing early
    ├── Dialogs.kt           move, vacation, workout editor
    ├── Controller.kt        user actions
    └── Status.kt, Theme.kt, Glyphs.kt
```

**Stack**: Kotlin 1.9.24, Jetpack Compose (Material 3), Android Gradle Plugin 8.5.2, Gradle 8.7, minSdk 26, targetSdk 34, no third-party libraries beyond AndroidX and coroutines.

## Tests

Unit tests live in `app/src/test/java/app/sports/`. `ScheduleTest` (9 tests) covers step durations and continuity, plan dates, vacation shifts, move rules and the 21:00 deadline, the workout clock with pause and seek, workout edits, auto-skip, partial completion and statistics. `PlanDataTest` checks the JSON round trip, `copyOf`, validation messages, the AI template example, saving and loading the data folder, and plan status transitions. `BodyTest` covers BMI, body fat, goals, forecast, the reminder rule and saving them. To run: right-click the file, **Run**.

---

# Тренировки

[English](#workouts) | **Русский**

Личное Android-приложение для тренировочных планов. План – это JSON-файл, который импортируется в приложение; планов может быть несколько, между ними можно переключаться. Работает без интернета. Все данные (профиль, прогресс, планы) лежат обычными файлами в папке, которую выбираешь ты.

**Содержание**

1. [Установка и начало работы](#установка-и-начало-работы)
2. [Возможности](#возможности)
3. [Правила и логика](#правила-и-логика)
4. [Работа в фоне и звук](#работа-в-фоне-и-звук)
5. [Данные и хранение](#данные-и-хранение)
6. [Архитектура](#архитектура)
7. [Тесты](#тесты)

## Установка и начало работы

1. **Скачайте и установите APK** на телефон: [app-debug.apk](https://github.com/lexozbedra/workout-customizer/releases/latest/download/app-debug.apk).
2. **Откройте приложение.** Разрешите уведомления и выберите папку для данных: создайте папку (например, «Тренировки») и нажмите **Использовать эту папку**.
3. **Добавьте план.** Можно загрузить свой файл (**Планы → Импорт из файла**) или получить шаблон кнопкой **Шаблон для нейросети** и сгенерировать план с помощью нейросети. После запуска плана приложение покажет календарь тренировок и статистику.
4. **Заполните профиль.** На вкладке **Профиль** укажите имя, рост, пол и дату рождения. Вес указывается на вкладке **Тело**: по нему считаются ИМТ, процент жира, цели и прогноз веса.

## Возможности

| Область | Что есть |
|---|---|
| Экран «Сегодня» | Тренировки выбранного дня, полоса недели с цветными точками статуса, запуск, отметка, перенос, пропуск |
| Плеер | Таймеры работы и отдыха, два разных сигнала, пауза, «сначала» (текущий шаг), назад, дальше, досрочное завершение |
| Прогресс | Полоса выполнения плана, время, дни в зале, активные дни, примерные калории, журнал пропусков и неполных тренировок |
| План | Недели раскрываются списком тренировок, просмотр без запуска, видео по упражнению |
| Планы | Коллекция планов из файлов: импорт, начать, пауза, продолжить, завершить, удалить. Активен один план, прогресс каждого сохраняется. Статусы: не начат / активен / на паузе / завершён |
| Профиль | Имя, рост, вес, пол, дата рождения (возраст считается сам), папка с данными |
| Тело | История веса с графиком, ИМТ, оценка процента жира, цель (вес, ИМТ или процент жира), прогноз достижения цели, ежемесячное напоминание о взвешивании |
| Шаблон плана | Одна кнопка копирует подсказку с полным описанием формата файла и рабочим примером: её можно отдать нейросети, и она напишет файл плана |
| Настройка | Время, повторения, число кругов и отдых правятся в приложении, без изменения кода |
| Расписание | Старт плана, отпуск с причиной (сдвиг плана), перенос внутри недели, листание недель на экране «Сегодня» (стрелки или свайп) |
| Плашка с таймером | Пока идёт тренировка, а приложение свёрнуто, внизу экрана висит «Работа 0:45» / «Отдых 1:10» с кнопкой паузы; нажатие открывает полный экран, × убирает плашку без остановки тренировки (нужно разрешение «Поверх других приложений», отключается на вкладке «План») |

Силовая в зале и дорожка – всегда две отдельные тренировки со своим запуском: между ними нужно время на лестницу и воду.

## Правила и логика

### Статусы тренировки

| Статус | Как получается |
|---|---|
| Выполнена | Плеер дошёл до конца или нажата «Отметить» |
| Выполнена частично | «Завершить досрочно» в плеере: процент (подставляется по времени, правится вручную) и причина «Не успела» / «Устала» / «Другое» |
| Пропущена | Кнопка «Пропустить» |
| Пропущена автоматически | День тренировки прошёл, а отметки и переноса не было. Отметить её задним числом можно |
| Впереди | Всё остальное |

Частичная тренировка идёт в прогресс пропорционально проценту: 50% = полтренировки. Если указать 100%, тренировка считается выполненной.

### Отпуск

Нужно выбрать причину: поездка, болезнь или другое (для «другое» комментарий обязателен). Всё, что запланировано с даты начала отпуска, сдвигается вперёд на его длительность. Режим «целые недели» округляет сдвиг вверх до недель, и тренировки остаются на вторнике, четверге и субботе.

### Перенос тренировки

Только внутри одной календарной недели (понедельник – воскресенье). Успеть нужно до 21:00 того дня, который наступает раньше: исходного или нового. Вторник на понедельник – до 21:00 в понедельник, вторник на среду – до 21:00 во вторник. Дни отпуска как цель переноса недоступны.

### Настройка тренировок

Правки лежат поверх плана и не меняют его код. Можно менять время каждого шага, число повторений, добавку к числу кругов (силовая) или интервалов (дорожка), отдых между кругами и суперсетами. Правка применяется к одной тренировке или ко всем таким же. Для упражнений на повторения время считается из повторений; если задать время вручную, оно остаётся как задано. Кнопки сброса: для одной тренировки и для всех правок сразу. Правки действуют на тренировку при её запуске, идущая тренировка не меняется.

### Статистика

- **Прогресс**: сумма долей выполнения ÷ число тренировок плана.
- **Время**: фактическое время тренировки; для отмеченных вручную берётся плановая длительность.
- **Дни в зале**: дни с выполненной или частичной силовой. **Активные дни**: дни с любой такой тренировкой.
- **Калории**: средняя нагрузка (MET) по этапам тренировки, взвешенная по времени, × вес × время. Для бега в горку 10, ровного бега 8,5, трусцы 8,3, шага в гору 7, силовых подходов 5, отдыха между подходами 2,5. Точность порядка ±30%. Вес берётся из вкладки «Профиль» (если не указан, 65 кг).

### Показатели тела

- **ИМТ** = вес ÷ рост². Подписи: до 18,5 «ниже нормы», до 25 «норма», до 30 «выше нормы», дальше «ожирение».
- **Процент жира** оценивается по формуле Дюрена: 1,2 × ИМТ + 0,23 × возраст − 10,8 × (1 для мужчин, 0 для женщин) − 5,4. Нужны рост, пол и возраст (от 18 лет). Ошибка может быть в несколько пунктов, поэтому это ориентир, а не медицинское измерение.
- **Цель**: вес, ИМТ или процент жира. ИМТ и процент жира пересчитываются в целевой вес. Если задано несколько, берётся первое из списка: вес, ИМТ, процент жира.
- **Прогноз**: прямая линия тренда по записям веса за последние 90 дней (минимум две записи с разницей от недели). Если вес идёт к цели, показывается дата, когда она будет достигнута в таком темпе. Если вес стоит на месте или уходит от цели, приложение так и скажет, а не будет гадать.
- **Напоминание**: плашка на вкладке «Сегодня» и уведомление около 10:00, когда последней записи веса 30 дней или записей нет (не чаще раза в неделю, пока вес не записан). Выключается на вкладке «Тело». После перезагрузки телефона будильник ставится заново.

### Видео по упражнениям

Кнопка «Узнать больше» открывает поиск видео на YouTube (в браузере или приложении). Это единственное место, где используется интернет. Разрешение INTERNET приложению не нужно, ссылка открывается через систему.

## Работа в фоне и звук

- Тренировка идёт в **службе переднего плана** с уведомлением (кнопки паузы, «Дальше» и «Стоп» в шторке). Таймер основан на системных часах и не зависит от экрана, блокировка держит процессор включённым.
- **Сигналы**: два высоких писка (1500 Гц) – начало работы, один длинный низкий (620 Гц) – отдых, два низких – конец тренировки. Звук генерируется в коде.
- Сигнал **не ставит чужую музыку на паузу**: фокус запрашивается в режиме приглушения (его можно выключить на вкладке «План»), музыка или видео на пару секунд становятся тише и возвращаются.
- На телефонах с агрессивной экономией заряда (Xiaomi, Huawei, Samsung) поставь для приложения **Батарея – Без ограничений** и разреши автозапуск и фоновую работу. При первом запуске разреши уведомления.

## Данные и хранение

При первом запуске приложение просит выбрать папку в системном окне (один раз, доступ запоминается). Всё лежит там обычными файлами: переживает переустановку приложения и копируется на другой телефон. Внутри приложения хранится только ссылка на папку. Если выбрать папку, где уже есть данные, приложение возьмёт их; если она пустая, туда переедут текущие.

```
<твоя папка>/
├── profile.json        имя, рост, вес, пол, дата рождения, настройки, какой план активен
├── progress.json       состояние каждого плана: статус, старт, итоги, переносы, отпуска, правки
└── plans/
    └── <your plan>.json
```

Идентификатор тренировки: `w{неделя}-{день недели ISO}-{id тренировки из файла плана}`, например `w3-4-treadmill`. Ключ правки: `w3-4-strength/squat` для одной тренировки или просто `squat` для всех. Прогресс старой версии (он хранился внутри приложения) не переносится.

### Файлы планов

Файл плана – JSON (`format: "workout-plan"`). В нём описаны фазы, недели, дни (номера дней недели ISO 1–7), тренировки, блоки (круги, суперсеты, интервалы) и шаги (секунды либо повторения с темпом). Неделя, повторяющая одну из предыдущих, записывается как `"copyOf": N`. При импорте файл проверяется, и приложение показывает понятное сообщение о первой найденной проблеме. Если импортировать файл с уже существующим id, план обновится, а прогресс сохранится.

Готовые файлы лежат в папке `plans/` репозитория: `test-home-2w.json` (маленький пример плана) и `TEMPLATE.txt` (тот же текст, что копирует кнопка **Шаблон для нейросети**: инструкция для нейросети, формат и рабочий пример). Чтобы подключить план: скопируй файл на телефон, потом **Планы → Импорт из файла**, потом **Начать**.

## Архитектура

Код разделён на чистую логику (Kotlin без Android, проверяется на JVM) и часть, зависящую от Android.

```
app/src/main/java/app/sports/
├── core/                    чистая логика
│   ├── Model.kt             шаги, тренировки, правки (Tuning)
│   ├── PlanDef.kt           план как данные и превращение в тренировки и шаги
│   ├── PlanFormat.kt        файл плана (JSON): чтение, проверка, запись, шаблон для нейросети
│   ├── Json.kt              небольшой разбор и запись JSON
│   ├── DefaultPlan.kt       небольшой пример плана (для шаблона нейросети)
│   ├── Body.kt              ИМТ, процент жира, цель, прогноз, правило напоминания
│   ├── Data.kt              профиль, состояние плана, статусы, абстракция папки, Repo
│   ├── Schedule.kt          даты: отпуск, переносы, правило 21:00
│   ├── Results.kt           статусы, итоги, причины, автопропуск
│   └── Stats.kt             прогресс, время, дни, калории
├── workout/
│   ├── WorkoutEngine.kt     часы тренировки: пауза, перемотка, без привязки к экрану
│   ├── WorkoutService.kt    служба переднего плана: таймер, сигналы, уведомление
│   └── Signals.kt           генерация сигналов и приглушение чужой музыки
├── Store.kt                 читает и пишет папку с данными, отдаёт состояние интерфейсу
├── WeightReminder.kt        ежемесячное напоминание о весе
├── SafFolder.kt             папка пользователя через системный доступ к файлам
├── MainActivity.kt
└── ui/                      Jetpack Compose
    ├── Root.kt              вкладки «Сегодня» / «Прогресс» / «План» / «Планы» / «Профиль», выбор папки при первом запуске
    ├── PlansScreen.kt       коллекция планов
    ├── BodyScreen.kt        вес, ИМТ, процент жира, цель, прогноз
    ├── ProfileScreen.kt     профиль и папка с данными
    ├── Screens.kt           сегодня, содержимое тренировки, план
    ├── ProgressScreen.kt    прогресс и статистика
    ├── PlayerScreen.kt      плеер
    ├── EarlyFinish.kt       досрочное завершение
    ├── Dialogs.kt           перенос, отпуск, редактор тренировки
    ├── Controller.kt        действия пользователя
    └── Status.kt, Theme.kt, Glyphs.kt
```

**Стек**: Kotlin 1.9.24, Jetpack Compose (Material 3), Android Gradle Plugin 8.5.2, Gradle 8.7, minSdk 26, targetSdk 34, без сторонних библиотек сверх AndroidX и корутин.

## Тесты

Юнит-тесты лежат в `app/src/test/java/app/sports/`. `ScheduleTest` (9 штук) покрывает: длительности и непрерывность шагов, даты плана, сдвиг на отпуск, правила переноса и дедлайн 21:00, часы тренировки с паузой и перемоткой, правки тренировок, автопропуск, частичное выполнение и статистику. `PlanDataTest` проверяет круговой путь JSON, `copyOf`, сообщения проверки, пример из шаблона для нейросети, сохранение и загрузку папки с данными и переходы статусов планов. `BodyTest` проверяет ИМТ, процент жира, цели, прогноз, правило напоминания и их сохранение. Запуск: правый клик по файлу, **Run**.
