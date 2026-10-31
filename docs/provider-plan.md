# План: провайдеры LLM с выбором в runtime (Ollama + Anthropic)

Архитектурные решения — [ADR-001](adr/ADR-001-architecture.md), native image —
[ADR-002](adr/ADR-002-native-image.md), модули провайдеров —
[ADR-005](adr/ADR-005-llm-adapter-modules.md). Предварительный этап —
[adapter-modules-plan.md](adapter-modules-plan.md) ✅. Общие правила и Definition of Done — как в
[implementation-plan.md](implementation-plan.md). Выполненные задачи отмечаются ✅.

## Цель

Harness работает с несколькими провайдерами LLM: Ollama (локально) и Claude API (Anthropic).
Оба провайдера собираются в бинарник всегда, активный выбирается в консоли командой
`/provider`. Каждый провайдер видит только свои модели: при активной Ollama `/list` и `/model`
работают с моделями Ollama, при активном Anthropic — с моделями Claude. Смена провайдера
начинает новую сессию.

## Решения

| Вопрос | Решение | Почему |
|---|---|---|
| Выбор провайдера | Состояние runtime (`/provider`, `--provider`, `HARNESS_PROVIDER`), не `@ConditionalOnProperty` | Spring AOT вычисляет условия бинов при сборке native image (ADR-002): переключение свойством потребовало бы пересборки |
| Набор провайдеров | Перечисление `LlmProvider { OLLAMA, ANTHROPIC }` в `application/model` | Провайдеры фиксируются при сборке; enum даёт исчерпывающий `switch` |
| Передача провайдера | Провайдер идёт рядом с моделью в Command/Query и `ChatRequest`, как сейчас идёт модель; текущее значение хранит `ReplSession` | Порты остаются без скрытого глобального состояния; ядро тестируется на фейках |
| Маршрутизация | SPI провайдера — интерфейс в `harness-application/port/out`; его реализуют модули `harness-adapter:<провайдер>`, а маршрутизаторы в `infrastructure/out/routing/` реализуют порты `port/out` поверх списка SPI-бинов | Слой application не знает о конкретных провайдерах; модули адаптеров видят только ядро (ADR-005), поэтому SPI в инфраструктуре был бы им недоступен |
| Код провайдеров | Ollama — `harness-adapter:ollama`, Claude — `harness-adapter:anthropic`; настройки и native hints — в модуле провайдера | Стек SDK (Jackson 2, Kotlin, OkHttp) не попадает в инфраструктуру и модуль Ollama |
| Смена провайдера | Всегда новая сессия; модель — модель по умолчанию нового провайдера | Thinking-блоки Claude не переносимы, окна контекста несопоставимы (64K против 1M) |
| Клиент Claude | Официальный Anthropic Java SDK (`com.anthropic:anthropic-java`), не Spring AI | Нужны adaptive thinking, `effort`, `fallbacks`, типизированные ошибки; Spring AI отстаёт от API |
| Refusal-fallbacks | Не включаются; `stop_reason: "refusal"` возвращается пользователю как ошибка | Решение владельца проекта |
| `effort` | `high` по умолчанию, настраивается | Дефолт Opus 5.5 — `medium`; для агентного режима нужен `high` |
| Нет `ANTHROPIC_API_KEY` | Бин создаётся, провайдер помечается недоступным; `/provider anthropic` — понятная ошибка, активный провайдер не меняется | Работает в native без условных бинов |

## Вне рамок

Серверные инструменты Anthropic (web search, code execution), prompt caching, серверная
компакция, Batch API, Bedrock/Vertex/Foundry, fast mode, режим `text-json` для Claude (только
native tool calling), перенос истории между провайдерами.

## Задачи

Перед шагом 2 выполнен рефакторинг [adapter-modules-plan.md](adapter-modules-plan.md) ✅:
адаптеры провайдеров переехали в модули `harness-adapter:ollama` и
`harness-adapter:anthropic`, шаги ниже уточнены под эту раскладку.

1. ✅ **Spike SDK в native image.** `anthropic-java` 3.2.0 подключён в
   `gradle/libs.versions.toml` и `harness-infrastructure/build.gradle.kts`. BOM Spring Boot
   поднимает транзитивные Jackson 2 до 2.21.7 и Kotlin до 2.3.21; с Jackson 3 конфликтов нет.
   Временный раннер в native-образе против локального mock-сервера: `models().list()` и
   `retrieve()` (включая `max_input_tokens` и `capabilities`), 404 → `NotFoundException`,
   SSE-стрим с thinking + `signature_delta`, text и `tool_use` с `input_json_delta` через
   `MessageAccumulator`; запрос с adaptive thinking, `effort`, `eager_input_streaming`
   сериализуется верно. Дополнительные reachability hints не понадобились, старт native
   `--version` — 0.05 с, как до изменения. Раннер удалён, в коде только зависимость.
   *Обновление:* зависимость перенесена в `harness-adapter/anthropic/build.gradle.kts`
   (adapter-modules-plan, шаг 3).
2. ✅ **Ядро: провайдер как понятие.** В `harness-application`:
   - `model/LlmProvider`, поле `provider` в `ChatRequest`, `SessionInfo` и в Command/Query,
     где уже есть модель (`SendMessageCommand`, `RunAgentTurnCommand`, `SwitchModelCommand`,
     `ToggleThinkingCommand`, `CompactSessionCommand`, `ListModelsCriteria`,
     `ContextUsageCriteria`);
   - порты `ModelCatalogPort`, `ContextBudgetPort` принимают провайдера; новый
     `ProviderCatalogPort` (провайдеры, доступность, модель по умолчанию);
   - `SwitchProviderUseCase` + `SwitchProviderCommand` → `ProviderResult` (провайдер, модель
     по умолчанию), `ListProvidersQuery` + `ListProvidersCriteria` → `ProviderSummaryResult`;
   - `ProviderUnavailableException` (диапазон `500xxx`), маппинг в `TurnApplicationMapper`;
   - wiring в `BeanConfig`, unit-тесты сервисов на фейках портов (AAA).

   *Итог и отличия от плана:* `ProviderInfo(provider, available, defaultModel)` в `model`,
   `ProviderCatalogPort.listProviders()`; `ListProvidersCriteria(activeProvider)` отмечает
   активного провайдера в `ProviderSummaryResult.active`, список идёт в порядке `LlmProvider`.
   `SessionSummaryResult` тоже получил `provider` — он нужен `/resume` (шаг 5). Код
   `ProviderUnavailableException` — `500003`. Компактор и `ContextMeter` принимают провайдера
   рядом с моделью. Чтобы новые use case'ы связывались в `BeanConfig`, добавлен временный
   `infrastructure/out/routing/ProviderCatalogAdapter`: Ollama доступна с `harness.model`,
   Anthropic недоступен с моделью `claude-opus-5-5` — шаг 3 переводит его на SPI, шаг 4 — на
   настройки. До следующих шагов провайдер везде Ollama: `ReplSession` (шаг 6), команда
   `list` (шаг 6), meta-строка JSONL (`JsonlLineMapper`, шаг 5); Ollama-адаптеры аргумент
   провайдера не проверяют. Фейки ядра: `FakeProviderCatalogPort`, `FakeModelCatalogPort`
   с моделями одного провайдера. Тестов 296 (+10); `clean check`, live (8), native smoke (8)
   и REPL на native-бинарнике (чат, `/list`, `/model`, `/think`, `/context`, `/compact`,
   `/resume`) зелёные.
3. **Маршрутизация в инфраструктуре.** SPI `LlmProviderAdapters` (провайдер, chat, catalog,
   context budget, доступность) — интерфейс в `harness-application/port/out`, без Spring;
   реализацию для Ollama даёт `harness-adapter:ollama`. Маршрутизаторы `RoutingChatModelAdapter`,
   `RoutingModelCatalogAdapter`, `RoutingContextBudgetAdapter`, `ProviderCatalogAdapter` в
   `out/routing/` выбирают адаптер по провайдеру из запроса; `cancelActive` уходит к провайдеру
   текущего стрима. Ollama-адаптеры в `harness-adapter:ollama` переводятся с портов на SPI без
   изменения поведения; `RunningModelsPort` остаётся за Ollama. Маршрутизаторы видят
   провайдеров только через SPI: правило ArchUnit «`..infrastructure..` не зависит от
   `..adapter..`» уже действует.
4. **Настройки.** `HarnessProperties`: `provider` (стартовый, по умолчанию `ollama`).
   `AnthropicProperties` в `harness-adapter:anthropic` (префикс `harness.anthropic`):
   `apiKey` (`${ANTHROPIC_API_KEY:}`), `baseUrl`, `model` (по умолчанию `claude-opus-5-5`),
   `maxTokens` (64000), `effort` (по умолчанию `high`); регистрируется в конфигурации модуля
   вместе с его `RuntimeHintsRegistrar`, как `OllamaProperties`. Серверные `fallbacks` не
   включаются. `harness.model` (`HarnessProperties`) и `harness.ollama-base-url`
   (`OllamaProperties`) остаются настройками Ollama — без поломки `config.yml` пользователей.
   Обновить `application.yml`; ключ не логируется и не попадает в сообщения об ошибках.
5. **Сессии.** `JsonlSessionCatalogAdapter` и `JsonlLine` хранят провайдера; старые файлы без
   поля читаются как `OLLAMA`. `/resume` восстанавливает провайдера и модель сессии, а для
   недоступного провайдера сообщает об ошибке и оставляет текущую сессию.
6. **CLI.** `/provider` без аргумента — список провайдеров с доступностью и отметкой
   активного; `/provider <name>` — `SwitchProviderUseCase`, затем новая сессия
   (`StartSessionUseCase`) и модель по умолчанию в `ReplSession`. Флаг `--provider` у
   `HarnessCommand` и `list`; `/ps` для Anthropic отвечает «не поддерживается». Автодополнение
   в `SlashCommandCompleter`, `/help`, обработчик `ProviderErrorHandler` в `in/cli/handlers/`,
   маппинг в `ReplCliMapper`.
7. **`AnthropicChatAdapter`** (модуль `harness-adapter:anthropic`, пакет
   `dev.localllm.harness.adapter.anthropic`). Стриминг SDK → `ChatChunk` (thinking, text, tool_use с
   `eager_input_streaming` и проверкой input по схеме). Отмена закрывает стрим; обрыв после
   первого чанка — `INTERRUPTED`, как у Ollama. Обработка `stop_reason`: `refusal` →
   `LlmResponseException` с категорией, `max_tokens` — частичный ответ. Thinking на Opus 5.5
   отключить нельзя (`disabled` → 400), поэтому `/think` управляет показом: `adaptive` +
   `display: "summarized"` против `"omitted"`; глубина — `effort` из настроек. Мапперы
   MapStruct в `adapter/anthropic/mapper/` (MapStruct и processor добавить в
   build-скрипт модуля): `ChatMessage` ↔ `MessageParam`, `ToolSpec` → `Tool`,
   usage → `TokenUsage`.
8. **Каталог, бюджет контекста, ошибки Anthropic** (там же). `AnthropicModelCatalogAdapter` —
   `models.list()` / `retrieve()` (404 → пусто), capability `THINKING` и `TOOLS`;
   `AnthropicContextBudgetAdapter` — `max_input_tokens` с кэшем, fallback на `numCtx`;
   `AnthropicErrorTranslator` — цепочка типизированных исключений SDK: 401 → недоступен
   (неверный ключ), 404 → `ModelNotFoundException`, 429/5xx → `LlmResponseException`,
   соединение → `LlmUnavailableException`.
9. **Thinking-блоки в агентном цикле (риск).** API требует вернуть thinking-блоки с подписью в
   ассистентском сообщении с `tool_use`, а `ChatMessage` и JSONL их не хранят; компакция
   переписывает историю, и для аккаунтов после 2026-08-31 это 400 (preserved thinking).
   Решение: непрозрачное поле состояния провайдера в `ChatMessage` и `JsonlLine` (thinking-блоки
   с подписью), которое передаётся обратно без изменений; старые сессии читаются без него.
   Компакция для Claude не редактирует сообщения с этим полем, а заменяет историю целиком
   новой сводкой. Поведение проверить на живом API.
10. **Тесты и проверка.** WireMock IT в модуле `harness-adapter:anthropic`: SSE-стрим, tool_use, отмена, обрыв, `refusal`,
    401/404/429/529 (по образцу `OllamaChatAdapterIT`); unit-тесты маршрутизаторов и мапперов;
    live IT с тегом `live`, только при заданном `ANTHROPIC_API_KEY`. Порог JaCoCo модуля
    Anthropic — 50 %, как у Ollama. `./gradlew clean check` (ArchUnit, JaCoCo-пороги), `-PincludeTags=native`. Ручной сценарий: `/provider anthropic`
    → новая сессия → `/list` показывает только Claude → агентный ход с инструментом →
    `/provider ollama` → `/resume` сессии Claude.
11. **Документация.** ADR-006 (провайдеры в runtime, маршрутизация, SDK вместо Spring AI,
    новая сессия при смене); `docs/usage.md`, `docs/run.md`, справочник опций в README.

## Решённые вопросы

- Серверные `fallbacks` не включаются; `effort` по умолчанию — `high`.
- Изменение `ChatMessage` и `JsonlLine` под состояние провайдера (задача 9) согласовано:
  правка слоя application допустима.
