# План: провайдеры LLM с выбором в runtime (Ollama + Anthropic)

Архитектурные решения — [ADR-001](adr/ADR-001-architecture.md), native image —
[ADR-002](adr/ADR-002-native-image.md). Общие правила и Definition of Done — как в
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
| Маршрутизация | Порты `port/out` реализуют маршрутизаторы в `out/routing/`; провайдерские адаптеры — внутренний SPI инфраструктуры | Слой application не знает о конкретных провайдерах |
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

Перед шагом 2 выполняется рефакторинг [adapter-modules-plan.md](adapter-modules-plan.md):
адаптеры провайдеров переезжают в модули `harness-adapter:ollama` и
`harness-adapter:anthropic`. После него шаги ниже уточняются.

1. ✅ **Spike SDK в native image.** `anthropic-java` 3.2.0 подключён в
   `gradle/libs.versions.toml` и `harness-infrastructure/build.gradle.kts`. BOM Spring Boot
   поднимает транзитивные Jackson 2 до 2.21.7 и Kotlin до 2.3.21; с Jackson 3 конфликтов нет.
   Временный раннер в native-образе против локального mock-сервера: `models().list()` и
   `retrieve()` (включая `max_input_tokens` и `capabilities`), 404 → `NotFoundException`,
   SSE-стрим с thinking + `signature_delta`, text и `tool_use` с `input_json_delta` через
   `MessageAccumulator`; запрос с adaptive thinking, `effort`, `eager_input_streaming`
   сериализуется верно. Дополнительные reachability hints не понадобились, старт native
   `--version` — 0.05 с, как до изменения. Раннер удалён, в коде только зависимость.
2. **Ядро: провайдер как понятие.** В `harness-application`:
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
3. **Маршрутизация в инфраструктуре.** SPI `LlmProviderAdapters` (провайдер, chat, catalog,
   context budget, доступность). Маршрутизаторы `RoutingChatModelAdapter`,
   `RoutingModelCatalogAdapter`, `RoutingContextBudgetAdapter`, `ProviderCatalogAdapter` в
   `out/routing/` выбирают адаптер по провайдеру из запроса; `cancelActive` уходит к провайдеру
   текущего стрима. Ollama-адаптеры переводятся с портов на SPI без изменения поведения;
   `RunningModelsPort` остаётся за Ollama.
4. **Настройки.** `HarnessProperties`: `provider` (стартовый, по умолчанию `ollama`) и запись
   `Anthropic` — `apiKey` (`${ANTHROPIC_API_KEY:}`), `baseUrl`, `model` (по умолчанию
   `claude-opus-5-5`), `maxTokens` (64000), `effort` (по умолчанию `high`). Серверные
   `fallbacks` не включаются. Существующие `harness.model`
   и `harness.ollama-base-url` остаются настройками Ollama — без поломки `config.yml`
   пользователей. Обновить `application.yml` и `NativeHintsConfig`; ключ не логируется и не
   попадает в сообщения об ошибках.
5. **Сессии.** `JsonlSessionCatalogAdapter` и `JsonlLine` хранят провайдера; старые файлы без
   поля читаются как `OLLAMA`. `/resume` восстанавливает провайдера и модель сессии, а для
   недоступного провайдера сообщает об ошибке и оставляет текущую сессию.
6. **CLI.** `/provider` без аргумента — список провайдеров с доступностью и отметкой
   активного; `/provider <name>` — `SwitchProviderUseCase`, затем новая сессия
   (`StartSessionUseCase`) и модель по умолчанию в `ReplSession`. Флаг `--provider` у
   `HarnessCommand` и `list`; `/ps` для Anthropic отвечает «не поддерживается». Автодополнение
   в `SlashCommandCompleter`, `/help`, обработчик `ProviderErrorHandler` в `in/cli/handlers/`,
   маппинг в `ReplCliMapper`.
7. **`AnthropicChatAdapter`.** Стриминг SDK → `ChatChunk` (thinking, text, tool_use с
   `eager_input_streaming` и проверкой input по схеме). Отмена закрывает стрим; обрыв после
   первого чанка — `INTERRUPTED`, как у Ollama. Обработка `stop_reason`: `refusal` →
   `LlmResponseException` с категорией, `max_tokens` — частичный ответ. Thinking на Opus 5.5
   отключить нельзя (`disabled` → 400), поэтому `/think` управляет показом: `adaptive` +
   `display: "summarized"` против `"omitted"`; глубина — `effort` из настроек. Мапперы
   MapStruct в `out/anthropic/mapper/`: `ChatMessage` ↔ `MessageParam`, `ToolSpec` → `Tool`,
   usage → `TokenUsage`.
8. **Каталог, бюджет контекста, ошибки Anthropic.** `AnthropicModelCatalogAdapter` —
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
10. **Тесты и проверка.** WireMock IT: SSE-стрим, tool_use, отмена, обрыв, `refusal`,
    401/404/429/529 (по образцу `OllamaChatAdapterIT`); unit-тесты маршрутизаторов и мапперов;
    live IT с тегом `live`, только при заданном `ANTHROPIC_API_KEY`. `./gradlew clean check`
    (ArchUnit, JaCoCo-пороги), `-PincludeTags=native`. Ручной сценарий: `/provider anthropic`
    → новая сессия → `/list` показывает только Claude → агентный ход с инструментом →
    `/provider ollama` → `/resume` сессии Claude.
11. **Документация.** ADR-005 (провайдеры в runtime, маршрутизация, SDK вместо Spring AI,
    новая сессия при смене); `docs/usage.md`, `docs/run.md`, справочник опций в README.

## Решённые вопросы

- Серверные `fallbacks` не включаются; `effort` по умолчанию — `high`.
- Изменение `ChatMessage` и `JsonlLine` под состояние провайдера (задача 9) согласовано:
  правка слоя application допустима.
