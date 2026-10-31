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
        ├──────────► harness-adapter:ollama ───────────────┤
        └──────────► harness-adapter:anthropic ────────────┘
```

## Решения

| Вопрос | Решение | Почему |
|---|---|---|
| Что выносится | Только LLM-провайдеры: `harness-adapter:ollama`, `harness-adapter:anthropic` | Изолирует самые тяжёлые и несовместимые зависимости; остальное не выигрывает от модулей |
| Раскладка модулей | Общий родитель `harness-adapter/` с подмодулями `ollama`, `anthropic`; jar — `harness-adapter-<провайдер>` | Родитель задаёт для всех адаптеров `java-library` и единственную зависимость `api(":harness-application")`; новый провайдер — ещё один подмодуль |
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

1. ✅ **Выделить настройки Ollama внутри `harness-infrastructure`.** `OllamaProperties` (с
   `ToolCallModeType`) и `OllamaNativeHints` в пакете `out/ollama/config`; Ollama-адаптеры,
   `ToolCallModeResolver`, `OllamaPsClient`, `OllamaErrorTranslator` переходят на них;
   `ollamaBaseUrl`, `toolCallModes`, `defaultToolCallMode`, `context.numCtx` удаляются из
   `HarnessProperties`; `NativeHintsConfig` больше не ссылается на `OllamaRunningModels`.
   Тестовые фикстуры: `TestProperties` разделяется на общую часть и `OllamaTestProperties`.
   Сборка зелёная, модулей ещё нет — шаг проверяет, что зависимости разрезаются чисто.
   *Итог:* `OllamaProperties` привязан к префиксу `harness` рядом с `HarnessProperties` и
   регистрируется в `OllamaConfig` вместе с `OllamaNativeHints`; ключи и `HARNESS_*` не
   изменились. Риск двух `@ConfigurationProperties` на одном префиксе не подтвердился:
   `clean check` (286 тестов), native smoke (8) и live против `qwen3.5:27b` (8) зелёные.
2. ✅ **Создать `harness-adapter-ollama` и перенести код.** Модуль в `settings.gradle.kts`,
   `build.gradle.kts` (`java-library`, `java-test-fixtures`, `api(":harness-application")`,
   Spring AI Ollama starter, MapStruct + processor, configuration processor, WireMock, JaCoCo
   ≥ 50 %). `git mv` пакета `out/ollama` с переименованием в `adapter.ollama`, `package-info`
   с `@NullMarked`. Из `harness-infrastructure` уходят `spring-ai-starter-model-ollama` и
   Netty DNS resolver (нужен WebClient Ollama); если инфраструктуре не хватит классов Spring AI
   (`chat.messages`, `chat.memory`, `tool`), добавить явную зависимость `spring-ai-model`.
   Тесты `out/ollama` переезжают в модуль; `AgentLiveIT` (Ollama + инструменты + MCP) — в
   `harness-bootstrap`; импорты в `HarnessApplicationIT` обновляются. `harness-bootstrap`
   подключает модуль.
   *Итог и отличия от плана:* `AgentLiveIT` остался в тестах `harness-infrastructure` (пакет
   `out/tools`): ему нужны тестовые классы инфраструктуры (`TestProperties`, `TestTerminals`,
   `McpTestSupport`), которые bootstrap не видит. Модуль Ollama подключён к нему только как
   `testImplementation`, а `OllamaTestSupport` и `OllamaTestProperties` вынесены в test
   fixtures модуля (`adapter.ollama.fixture`). `HttpTools` получал `spring-web` транзитивно
   через Ollama starter — инфраструктуре добавлен явный `spring-boot-starter-restclient`.
   Модуль сразу включён в общий порог JaCoCo. Тестов по-прежнему 286 (application 66,
   infrastructure 151, adapter-ollama 56, bootstrap 13); native smoke (8) и live (8) зелёные;
   в runtime classpath инфраструктуры больше нет Spring AI Ollama и Netty DNS.
3. ✅ **Создать каркас `harness-adapter-anthropic`.** Модуль с `package-info` и build-скриптом;
   зависимость `anthropic-java` переносится сюда из `harness-infrastructure`. Проверить через
   `dependencies`, что SDK (Jackson 2, Kotlin, OkHttp) не попадает в classpath
   инфраструктуры и Ollama-модуля.
   *Итог:* модуль (`java-library`, `api(":harness-application")`, `anthropic-java`) подключён
   к `harness-bootstrap` и к общему порогу JaCoCo. В runtime classpath инфраструктуры и
   Ollama-модуля нет `com.anthropic`, `jackson-databind` 2, `kotlin-stdlib` и `okhttp`
   (остаётся только `jackson-annotations` 2.21 — его использует и Jackson 3). Внешние
   зависимости runtime classpath `harness-bootstrap` совпадают с прежними, поэтому native и
   live не перезапускались; `clean check` зелёный (286 тестов).
   *Дополнение:* оба модуля перенесены в общий родитель `harness-adapter/` (Gradle-пути
   `:harness-adapter:ollama`, `:harness-adapter:anthropic`). Родительский
   `build.gradle.kts` применяет к подмодулям `java-library`, `api(":harness-application")` и
   имя jar `harness-adapter-<имя>`; в build-скриптах провайдеров остались только их
   собственные зависимости. `clean check` зелёный (286 тестов), оба jar в `harness.jar`.
4. **Закрепить границы и проверить.** ArchUnit (`HexagonalArchitectureTest`): `application` не
   зависит от `..adapter..`; `infrastructure` не зависит от `..adapter..`; адаптеры не
   зависят от `..infrastructure..`, `..bootstrap..` и друг от друга (slices). Общий порог
   JaCoCo в `harness-bootstrap` учитывает новые модули (Ollama — уже в шаге 2). Прогон:
   `./gradlew clean check`,
   `-PincludeTags=live` (локальная Ollama), `-PincludeTags=native` (сборка образа и smoke),
   ручной сценарий REPL: чат, агентный ход с инструментом, `/list`, `/ps`, `/resume`.
5. **Документация.** ADR-005 «LLM-провайдеры в отдельных модулях»; пометка «Обновление» в
   таблице модулей ADR-001; граф модулей и таблица в `docs/build.md`; раздел архитектуры в
   `docs/java-ai-harness.md`; правила в `implementation-plan.md`. В `provider-plan.md`:
   ссылка на этот план как на предварительный этап, SPI — в `port/out` ядра, адаптер Claude —
   в `harness-adapter:anthropic`, ADR о провайдерах становится ADR-006.

## Риски

- **Два `@ConfigurationProperties` на префиксе `harness`.** Spring Boot это допускает, но
  нужно проверить валидацию и AOT-обработку в native. Если что-то сломается, `OllamaProperties`
  временно привязывается к `harness.ollama` с переносом ключей в `application.yml`; переменные
  `HARNESS_*` при этом не меняются.
- **Транзитивные классы Spring AI.** После удаления Ollama starter инфраструктура может
  потерять `spring-ai-client-chat` или `spring-ai-model`. Компиляция покажет это сразу,
  native smoke — то, что пропущено в рантайме.
