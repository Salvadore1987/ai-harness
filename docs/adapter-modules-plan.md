# План: адаптеры LLM-провайдеров в отдельных Gradle-модулях

Рефакторинг перед [provider-plan.md](provider-plan.md) (шаги 2–11 выполняются после него).
Архитектура — [ADR-001](adr/ADR-001-architecture.md), сборка — [build.md](build.md). Общие
правила и Definition of Done — как в [implementation-plan.md](implementation-plan.md).
Выполненные шаги отмечаются ✅.

## Цель

Каждый LLM-провайдер — отдельный Gradle-модуль со своими зависимостями: Spring AI Ollama не
попадает в модуль Anthropic, Anthropic SDK (Jackson 2, Kotlin, OkHttp) — в модуль Ollama и в
`harness-infrastructure`. Остальные адаптеры (CLI, память, MCP, инструменты, консоль, файлы)
остаются в `harness-infrastructure`. Поведение и ключи конфигурации не меняются — чистый
рефакторинг.

```
harness-bootstrap ──► harness-infrastructure ──────► harness-application
        │                                                  ▲
        ├──────────► harness-adapter-ollama ───────────────┤
        └──────────► harness-adapter-anthropic ────────────┘
```

## Решения

| Вопрос | Решение | Почему |
|---|---|---|
| Что выносится | Только LLM-провайдеры: `harness-adapter-ollama`, `harness-adapter-anthropic` | Изолирует самые тяжёлые и несовместимые зависимости; остальное не выигрывает от модулей |
| Зависимости модуля адаптера | Только `api(project(":harness-application"))`; от `harness-infrastructure` и друг от друга — нет | Адаптер реализует порты ядра и больше ничего не знает; цикл с инфраструктурой невозможен |
| Пакеты | `dev.localllm.harness.adapter.ollama` (+ `mapper`, `toolcall`, `dto`, `config`), `dev.localllm.harness.adapter.anthropic` | Пакет совпадает с модулем; ArchUnit проверяет границы по пакетам |
| Настройки Ollama | `OllamaProperties` в модуле Ollama, привязан к тому же префиксу `harness`: `ollama-base-url`, `tool-call-modes`, `default-tool-call-mode`, `context.num-ctx`; эти поля уходят из `HarnessProperties` | Ключи и переменные `HARNESS_*` не меняются; перестройка ключей — шаг 4 provider-plan |
| `harness.model` | Остаётся в `HarnessProperties` | Это стартовая модель CLI; в provider-plan станет моделью по умолчанию провайдера |
| Native hints | У каждого модуля свой `RuntimeHintsRegistrar` (`OllamaNativeHints`) | `NativeHintsConfig` инфраструктуры не должен ссылаться на классы адаптеров |
| Сканирование бинов | Без изменений: `scanBasePackages = "dev.localllm.harness"` покрывает новые пакеты | Модуль подключается одной строкой в `harness-bootstrap/build.gradle.kts` |
| SPI для маршрутизации провайдеров | В `harness-application/port/out` (шаги 2–3 provider-plan) | Модули адаптеров видят только ядро; SPI в инфраструктуре был бы им недоступен |

## Вне рамок

Вынос CLI, памяти, MCP, инструментов и консоли в модули; смена ключей конфигурации; любые
изменения поведения; код Anthropic-адаптера (provider-plan, шаги 7–8).

## Шаги

1. **Выделить настройки Ollama внутри `harness-infrastructure`.** `OllamaProperties` (с
   `ToolCallModeType`) и `OllamaNativeHints` в пакете `out/ollama/config`; Ollama-адаптеры,
   `ToolCallModeResolver`, `OllamaPsClient`, `OllamaErrorTranslator` переходят на них;
   `ollamaBaseUrl`, `toolCallModes`, `defaultToolCallMode`, `context.numCtx` удаляются из
   `HarnessProperties`; `NativeHintsConfig` больше не ссылается на `OllamaRunningModels`.
   Тестовые фикстуры: `TestProperties` разделяется на общую часть и `OllamaTestProperties`.
   Сборка зелёная, модулей ещё нет — шаг проверяет, что зависимости разрезаются чисто.
2. **Создать `harness-adapter-ollama` и перенести код.** Модуль в `settings.gradle.kts`,
   `build.gradle.kts` (`java-library`, `java-test-fixtures`, `api(":harness-application")`,
   Spring AI Ollama starter, MapStruct + processor, configuration processor, WireMock, JaCoCo
   ≥ 50 %). `git mv` пакета `out/ollama` с переименованием в `adapter.ollama`, `package-info`
   с `@NullMarked`. Из `harness-infrastructure` уходят `spring-ai-starter-model-ollama` и
   Netty DNS resolver (нужен WebClient Ollama); если инфраструктуре не хватит классов Spring AI
   (`chat.messages`, `chat.memory`, `tool`), добавить явную зависимость `spring-ai-model`.
   Тесты `out/ollama` переезжают в модуль; `AgentLiveIT` (Ollama + инструменты + MCP) — в
   `harness-bootstrap`; импорты в `HarnessApplicationIT` обновляются. `harness-bootstrap`
   подключает модуль.
3. **Создать каркас `harness-adapter-anthropic`.** Модуль с `package-info` и build-скриптом;
   зависимость `anthropic-java` переносится сюда из `harness-infrastructure`. Проверить через
   `dependencies`, что SDK (Jackson 2, Kotlin, OkHttp) не попадает в classpath
   инфраструктуры и Ollama-модуля.
4. **Закрепить границы и проверить.** ArchUnit (`HexagonalArchitectureTest`): `application` не
   зависит от `..adapter..`; `infrastructure` не зависит от `..adapter..`; адаптеры не
   зависят от `..infrastructure..`, `..bootstrap..` и друг от друга (slices). Общий порог
   JaCoCo в `harness-bootstrap` учитывает новые модули. Прогон: `./gradlew clean check`,
   `-PincludeTags=live` (локальная Ollama), `-PincludeTags=native` (сборка образа и smoke),
   ручной сценарий REPL: чат, агентный ход с инструментом, `/list`, `/ps`, `/resume`.
5. **Документация.** ADR-005 «LLM-провайдеры в отдельных модулях»; пометка «Обновление» в
   таблице модулей ADR-001; граф модулей и таблица в `docs/build.md`; раздел архитектуры в
   `docs/java-ai-harness.md`; правила в `implementation-plan.md`. В `provider-plan.md`:
   ссылка на этот план как на предварительный этап, SPI — в `port/out` ядра, адаптер Claude —
   в `harness-adapter-anthropic`, ADR о провайдерах становится ADR-006.

## Риски

- **Два `@ConfigurationProperties` на префиксе `harness`.** Spring Boot это допускает, но
  нужно проверить валидацию и AOT-обработку в native. Если что-то сломается, `OllamaProperties`
  временно привязывается к `harness.ollama` с переносом ключей в `application.yml`; переменные
  `HARNESS_*` при этом не меняются.
- **Транзитивные классы Spring AI.** После удаления Ollama starter инфраструктура может
  потерять `spring-ai-client-chat` или `spring-ai-model`. Компиляция покажет это сразу,
  native smoke — то, что пропущено в рантайме.
