# ADR-005. LLM-провайдеры в отдельных модулях

- **Статус:** принято
- **Дата:** 2026-10-31
- **Контекст:** ADR-001 (п. 1), ADR-002, [adapter-modules-plan.md](../adapter-modules-plan.md),
  [provider-plan.md](../provider-plan.md)

## Контекст

По ADR-001 все адаптеры жили в одном модуле `harness-infrastructure`. Для одного провайдера
это работало, но Claude API подключается через Anthropic Java SDK (provider-plan, шаг 1), а
SDK приносит свой стек: Jackson 2, Kotlin stdlib, OkHttp. Ollama же работает через Spring AI
на Jackson 3 и WebClient (Netty). В одном модуле:

- инфраструктура видит при компиляции оба стека, и классы Jackson 2 легко импортировать по
  ошибке вместо `tools.jackson`;
- граница «этот код про Ollama, этот про Claude» держится только на дисциплине пакетов;
- настройки, native hints и тестовые фикстуры провайдера перемешаны с общими (CLI, память,
  MCP, инструменты).

## Решения

### 1. Каждый провайдер — подмодуль `harness-adapter`

```text
harness-bootstrap ──► harness-infrastructure ──────────► harness-application
        │                                                       ▲
        ├──────────► harness-adapter:ollama ────────────────────┤
        └──────────► harness-adapter:anthropic ─────────────────┘
```

| Модуль | Пакет | Зависимости |
|---|---|---|
| `harness-adapter:ollama` | `dev.localllm.harness.adapter.ollama` (+ `config`, `dto`, `mapper`, `toolcall`) | ядро, Spring AI Ollama starter, MapStruct, Netty DNS resolver для macOS |
| `harness-adapter:anthropic` | `dev.localllm.harness.adapter.anthropic` | ядро, `anthropic-java` |

Каталог `harness-adapter/` — родительский Gradle-проект без кода. Его `build.gradle.kts`
применяет к каждому подмодулю `java-library`, единственную проектную зависимость
`api(project(":harness-application"))` и имя jar `harness-adapter-<провайдер>`. Новый провайдер —
ещё один подмодуль и одна строка в `harness-bootstrap/build.gradle.kts`.

Остальные адаптеры (CLI, память, MCP, инструменты, консоль, файлы) остаются в
`harness-infrastructure`: несовместимых зависимостей у них нет, и отдельные модули им ничего не
дают.

### 2. Адаптер знает только ядро

Модуль адаптера не зависит от `harness-infrastructure` и от других адаптеров, инфраструктура и
ядро не зависят от адаптеров. Gradle не даёт скомпилировать нарушение, а ArchUnit
(`HexagonalArchitectureTest`) проверяет то же по пакетам: `..adapter..` не зависит от
`..infrastructure..` и `..bootstrap..`, срезы `adapter.(*)` не зависят друг от друга,
`..application..` и `..infrastructure..` не зависят от `..adapter..`.

Поэтому всё, что нужно и адаптеру, и инфраструктуре, живёт в ядре. Это касается SPI
маршрутизации провайдеров из provider-plan: он объявляется в `harness-application/port/out`.

### 3. Настройки и native hints — в модуле провайдера

`OllamaProperties` привязан к тому же префиксу `harness`, что и `HarnessProperties`, и
забрал у него `ollama-base-url`, `tool-call-modes`, `default-tool-call-mode` и
`context.num-ctx`. Ключи `application.yml` и переменные `HARNESS_*` не изменились. Spring Boot
допускает два `@ConfigurationProperties` на одном префиксе; валидация и AOT-обработка в native
проверены. `harness.model` осталась в `HarnessProperties` как стартовая модель CLI.

Reachability metadata классов провайдера регистрирует его собственный `RuntimeHintsRegistrar`
(`OllamaConfig.OllamaNativeHints`), а не `NativeHintsConfig` инфраструктуры.

Бины находятся без изменений: `scanBasePackages = "dev.localllm.harness"` покрывает пакеты
`adapter`.

### 4. Тесты

Тесты адаптера живут в его модуле. Общие для модулей фикстуры (`OllamaTestSupport`,
`OllamaTestProperties`) лежат в test fixtures модуля, пакет `adapter.ollama.fixture`.
`AgentLiveIT` остаётся в `harness-infrastructure`: ему нужны тестовые классы инфраструктуры,
поэтому модуль Ollama подключён туда только как `testImplementation`. Порог покрытия модуля
Ollama — 50 %, оба модуля входят в общий порог 60 % в `harness-bootstrap`.

## Рассмотренные альтернативы

| Вариант | Почему нет |
|---|---|
| Оставить провайдеров в `harness-infrastructure` | Jackson 2, Kotlin и OkHttp на classpath всей инфраструктуры; граница провайдеров держится только на пакетах |
| Модуль на каждый адаптер (CLI, память, MCP, инструменты) | Изолировать им нечего; больше build-скриптов и связей без выигрыша |
| Плоские модули `harness-adapter-ollama`, `harness-adapter-anthropic` без родителя | Общие правила адаптеров пришлось бы повторять в каждом build-скрипте |
| SPI маршрутизации в инфраструктуре | Модули адаптеров его не видят: им пришлось бы зависеть от инфраструктуры |
| Отдельный префикс `harness.ollama` для `OllamaProperties` | Ломает `config.yml` пользователей; перестройка ключей — отдельный шаг provider-plan |

## Последствия

- В runtime classpath `harness-infrastructure` и `harness-adapter:ollama` нет Anthropic SDK,
  Jackson 2 (`jackson-databind`), Kotlin stdlib и OkHttp; остаётся только
  `jackson-annotations`, общий с Jackson 3. Classpath приложения не изменился.
- Инфраструктура больше не получает транзитивно то, что приносил Ollama starter:
  `HttpTools` подключает `spring-boot-starter-restclient` явно.
- Тестов модуля Ollama не видно из `harness-infrastructure`, кроме фикстур; при переносе
  тестов между модулями фикстуры выносятся в `testFixtures`.
- Live-тесты запускаются через `./gradlew test -PincludeTags=live`: в `check` с этим фильтром
  обычные тесты исключены, и пороги JaCoCo модулей не выполняются.
- Код Claude-адаптера пишется в `harness-adapter:anthropic` (provider-plan, шаги 7–8); ADR о
  провайдерах в runtime — ADR-006.
