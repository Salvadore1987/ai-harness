# План реализации Harness по этапам

Источник требований — [java-ai-harness.md](java-ai-harness.md), архитектурные решения —
[ADR-001](adr/ADR-001-architecture.md). Номера требований (FR n) ссылаются на раздел
Functional Requirements спецификации. Выполненные задачи отмечены ✅.

## Общие правила для каждого этапа

- Код по стандарту (вариант B): порты в `harness-application`, адаптеры в
  `harness-infrastructure`, wiring в `harness-bootstrap/config/BeanConfig`; Command/Query на
  входе use case'ов; мапперы — MapStruct в пакетах `mapper/`; обработчики ошибок — в `handlers/`;
  без `var`, UUIDv7, `@NullMarked` в каждом пакете.
- **Definition of Done этапа:**
  1. `./gradlew clean check` зелёный (NullAway, Checkstyle, PMD, SpotBugs, ArchUnit, JaCoCo:
     `application` ≥ 80 %, `infrastructure` ≥ 50 %, общий ≥ 60 %);
  2. `./gradlew test -PincludeTags=live` зелёный против локальной `qwen3.5:27b`;
  3. ручной сценарий этапа пройден в настоящем терминале;
  4. спецификация, README и при необходимости ADR обновлены; этап отмечен ✅ в спецификации.

## Сводка

| Этап | Цель | Зависит от | Статус |
|---|---|---|---|
| M1 | Каркас и чат-REPL со стримингом и thinking | — | ✅ |
| M2 | Сессии в JSONL, контекст, компакция, `@`-файлы | M1 | ✅ |
| M3 | Агентный цикл, нативный tool calling, инструменты чтения | M2 | ✅ |
| M4 | Запись файлов, shell, HTTP, запасной текстовый протокол | M3 | |
| M5 | MCP-клиент (stdio + Streamable HTTP) | M3 | |
| M6 | Native image (по ADR с замером) | M1–M5 | |

M4 и M5 независимы друг от друга и могут идти параллельно после M3.

---

## M1 — каркас и чат ✅

**Цель:** работающий REPL поверх Ollama с thinking, отменой и режимом `-p`.

### Задачи

- ✅ ADR-001: вариант B, Gradle, неприменимые пункты чеклиста 5.1, MapStruct в `application`
- ✅ Три Gradle-модуля, `libs.versions.toml`, toolchain 25, Error Prone + NullAway, Checkstyle,
  PMD, SpotBugs, JaCoCo с порогами, `bootJar` только в `harness-bootstrap`
- ✅ Spike Spring AI 2.0.2 по исходникам: `think`, thinking в метаданных чанка, отмена,
  `capabilities`, слияние опций
- ✅ Ядро: модели, порты, команды, результаты, исключения, 6 use case'ов, `TurnApplicationMapper`,
  фейки портов в `testFixtures`
- ✅ Адаптеры: `OllamaChatAdapter`, `OllamaModelCatalogAdapter`, `OllamaErrorTranslator`,
  `InMemoryConversationAdapter`, `ConsoleTurnOutputAdapter`
- ✅ CLI: `HarnessCommand`, `PicocliRunner`, `ReplRunner`, `TurnExecutor`,
  `SlashCommandDispatcher` (`/help /exit /clear /model /think`), обработчики ошибок
- ✅ Bootstrap: `HarnessApplication`, `BeanConfig`, `application.yml` (только `${HARNESS_*}`),
  `log4j2-spring.xml`
- ✅ Тесты: 67 (unit, WireMock, JDK `HttpServer`, ArchUnit, smoke контекста) + 3 live
- ✅ `bin/harness`, README, замеры (20.6 ток/с, старт 1.6 с)

### Перенесено в следующие этапы

- Проверка в настоящем терминале: двойной Ctrl+C, Alt+Enter, `/clear`, вид цветов → ручной
  сценарий M2
- ✅ `num_ctx` не применяется MLX-раннером (см. Open Questions спеки) → решено в M2.1

---

## M2 — сессии и контекст ✅

**Цель:** история переживает перезапуск, контекст не переполняется, файлы подставляются через
`@`. Требования FR 5, 7, 8, 9 (`persist`), команды `/sessions /resume /compact /context`.

### Задачи

1. ✅ **Фактический бюджет контекста.** Выяснено: MLX-раннер применяет `num_ctx` только при
   загрузке модели и не перезагружает её при другом значении. Отдельный порт `ContextBudgetPort`,
   адаптер `OllamaContextBudgetAdapter` читает `context_length` из `GET /api/ps`; если модель не
   загружена или сервер недоступен — `harness.context.num-ctx`.
2. ✅ **Модели и порты:** `SessionInfo`, `ContextPolicy`, `ContextUsage`, `CompactionOutcome`;
   `SessionCatalogPort`, `ContextBudgetPort`; `ConversationPort` расширен `compact` и
   `recordThinking`; `TurnOutputPort.onCompaction`; use case'ы `ResumeSessionUseCase`,
   `ListSessionsQuery`, `CompactSessionUseCase`, `GetContextUsageQuery`; `SessionNotFoundException`
   (300003). `/clear` больше не удаляет историю — старая сессия остаётся доступной для `/resume`.
3. ✅ **Хранилище** (`infrastructure/out/memory`): `JsonlChatMemoryRepository` (Spring AI
   `ChatMemoryRepository` + методы для `meta` / `compaction` / `thinking`), `TokenWindowChatMemory`
   (Spring AI `ChatMemory`, окно после последней компакции), `ChatMemoryConversationAdapter`,
   `JsonlSessionCatalogAdapter`, мапперы `JsonlLineMapper` и `ChatMessageMemoryMapper`,
   отдельный бин `jsonlMapper`. `InMemoryConversationAdapter` удалён — для тестов есть фейк в
   `testFixtures`.
4. ✅ **Оценка токенов:** `ContextMeter` (символы / `chars-per-token` + 4 токена на сообщение).
   Калибровка по `prompt_eval_count` отклонена: Ollama считает в нём только токены вне кеша
   промпта.
5. ✅ **Компакция:** `ConversationCompactor` (общий для авто и `/compact`) — сводка старой части
   истории той же моделью без thinking; автоматически после хода при `compact-threshold`;
   если модель не ответила — старые сообщения отбрасываются без сводки.
6. ✅ **CLI:** `--resume [ID]`, `/sessions`, `/resume [id|префикс]`, `/compact`, `/context`,
   `/clear`; `FileMentionExpander` (`@путь` внутри `--cwd`, до 256 КБ, только UTF-8) и
   `FileMentionCompleter`; slash-команды разделены на `SessionSlashCommands` и
   `ModelSlashCommands`; `SessionErrorHandler`.
7. ✅ **Thinking `persist`:** `HARNESS_THINKING_PERSIST=true` пишет строки `thinking` в JSONL,
   в окно они не попадают.

### Тесты

- ✅ Unit ядра: `SendMessageService` (регистрация сессии, thinking, автокомпакция),
  `ConversationCompactor` (сводка, отказ модели, отмена, короткая история), `ContextMeter`,
  `ResumeSessionService` (последняя, префикс, полный id из другой директории, неоднозначный префикс).
- ✅ `@TempDir`: дозапись, атомарная перезапись с сохранением `meta`, битые строки, окно после
  компакции, компакция без сводки не «воскрешает» сообщения, `thinking` только при `persist`,
  список сессий по директории; `FileMentionExpander` (выход за `cwd`, нет файла, > 256 КБ, бинарный).
- ✅ WireMock: `/api/ps` — загруженная модель, не загружена, сервер недоступен. Запрос сводки идёт
  через тот же `OllamaChatAdapter`, `num_ctx` в нём проверен `HarnessApplicationIT`.
- ✅ Smoke контекста: файл сессии создаётся с `meta` и двумя сообщениями.
- ✅ Live (вручную): ход с `@`-файлом → новый процесс с `--resume` → модель помнит имя и вопрос;
  автокомпакция на реальной модели; `--resume` после компакции.

### Ручной сценарий

- ✅ `/sessions`, `/context`, `/clear`, `/resume`, `/compact` в псевдотерминале
- Проверить в настоящем терминале: двойной Ctrl+C, Alt+Enter, Tab-дополнение `@путь`, цвета

### Найдено и перенесено

- Компакция оставляет дословно последние `keep-last-messages` сообщений. Если в них лежит
  большой приложенный файл, `/compact` почти ничего не освобождает (замер: ~11 339 → ~11 324
  токенов). Нужно хранить хвост по бюджету токенов, а большие вложения в старых сообщениях
  заменять ссылкой на файл → задача M3.0.
- Холодный ход с промптом ~12.5k токенов занял ~87 с вместе со сводкой — замерить prefill
  отдельно (сквозная задача про `--verbose`).

---

## M3 — агентный цикл и инструменты чтения ✅

**Цель:** `/mode agent` — модель сама читает код через нативный tool calling `qwen3.5`.
Требования FR 10, 11 (`read_file`, `list_dir`, `glob`, `grep`), 13, 14.

### Задачи

0. ✅ **Компакция по бюджету токенов** (перенесено из M2): `ConversationCompactor` оставляет
   дословно последние сообщения, умещающиеся в `keep-tail-share` окна (0.25), а не фиксированное
   число; хвост никогда не начинается с результата инструмента. В транскрипте для сводки блоки
   `<file path="…">` заменяются ссылкой `@путь`. `harness.context.keep-last-messages` удалён.
1. ✅ **Модели и порты:** `ToolSpec`, `ToolSource`, `ToolCall`, `ToolResult`, `AgentPolicy`;
   `Role.TOOL_RESULT`; `ChatMessage` несёт `toolCalls` / `toolCallId` / `toolName`;
   `ChatRequest.tools`, `ChatCompletion.toolCalls`; `ToolExecutorPort`
   (`available()`, `execute(ToolCall, Path workingDirectory)` — рабочая директория передаётся
   инструментам через Spring AI `ToolContext`); `TurnOutputPort.onToolCall/onToolResult`;
   `RunAgentTurnUseCase` + `RunAgentTurnCommand`, `ListToolsQuery`;
   `AgentLimitExceededException` (200001).
2. ✅ **`AgentLoopService`:** цикл до ответа без вызовов; лимиты `max-iterations` (25) и
   `max-parse-failures` (2 шага подряд только с несуществующими инструментами); каждый шаг сразу
   пишется в историю (вызовы, затем по результату на вызов); отмена через `TurnCancellation`
   (общий с `CancelTurnService`) между инструментами, пропущенные вызовы получают результат
   «cancelled»; системный промпт с рабочей директорией не хранится и стабилен между ходами;
   сводка компакции вливается в него; проверка компакции между шагами. Общий код с чатом вынесен
   в `SessionRegistrar`, `ChatCompletion.historyContent()`, `ConversationCompactor.compactIfNeeded`.
3. ✅ **Нативный режим** (`out/ollama/toolcall`): в Spring AI 2.0.2 нет
   `internalToolExecutionEnabled` — инструменты исполняет `ToolCallingAdvisor`, который
   `ChatClient` регистрирует сам. `OllamaChatAdapter` отключает его через
   `AdvisorParams.toolCallingAdvisorAutoRegister(false)`, `NativeToolCallMode` передаёт
   определения как `DefinitionOnlyToolCallback` и собирает `tool_calls` из чанков;
   маппер `ToolCallSpringAiMapper` (id генерируется, если Ollama его не прислала).
4. ✅ **Инструменты** (`out/tools`): `FileTools` с `@Tool` (`read_file` с offset/limit и номерами
   строк, `list_dir`, `glob`, `grep`; пропуск `.git`, `.gradle`, `.idea`, `node_modules`; не более
   200 результатов), `PlainTextResultConverter` (строки без JSON-экранирования),
   `SpringAiToolExecutorAdapter` — реестр `ToolCallbackProvider`, обрезка до `max-output-chars`,
   ошибки инструмента → результат с `error=true`; `ToolsConfig`.
5. ✅ **История:** `ChatMessageMemoryMapper`, `JsonlLineMapper` и `JsonlLine` хранят
   `toolCalls` у ассистента и `role TOOL_RESULT` + `callId` / `toolName`; сравнение префикса в
   `JsonlChatMemoryRepository` — по строкам целиком, а не только по тексту.
6. ✅ **CLI:** `/mode chat|agent` (agent недоступен модели без `tools`, при смене на такую модель —
   возврат в chat), `/tools [префикс]`, `--mode`, приглашение `agent›`, режим в баннере;
   `⏺ read_file(path)` + 3 строки результата, `--verbose` — полностью с длительностью
   (`DisplayOptions`); в неинтерактивном терминале вызовы идут в stderr; `ToolErrorHandler`;
   подсказка `LlmErrorHandler` на «does not support tools».
7. ✅ **Конфиг:** `harness.agent.max-iterations`, `harness.agent.max-parse-failures`,
   `harness.tools.max-output-chars`, `harness.context.keep-tail-share`.

### Тесты

- ✅ Unit: `AgentLoopService` на фейках — без вызовов, цепочка вызовов, сумма токенов, лимит
  итераций, лимит неизвестных инструментов и его сброс, отмена во время инструмента и стрима,
  сброс отмены, ошибка первого шага, сводка в системном промпте, thinking, компакция между
  шагами; компактор — хвост по токенам, `@путь` вместо файла, хвост не начинается с результата.
- ✅ WireMock: `message.tool_calls` маппится в `ToolCall` и не исполняется Spring AI, id
  генерируется, в запросе есть `tools`, история с `tool_calls` и `role: tool`.
- ✅ `FileTools` на `@TempDir` через реестр: схемы, offset/limit, ошибки, бинарный файл, обрезка,
  glob с `**/`, grep с фильтром.
- ✅ JSONL: вызовы и результаты переживают запись/чтение; изменённый результат перезаписывает файл.
- ✅ CLI: `/mode`, `/tools`, `--mode agent -p`, лимит итераций; консоль — превью, verbose, stderr.
- ✅ Live (`AgentLiveIT`): «прочитай `Calc.java` и скажи, что делает `add`» → вызван `read_file`.

### Ручной сценарий

- ✅ `bin/harness --mode agent -p …` (вызовы в stderr, ответ в stdout), `--resume` с историей
  инструментов, в псевдотерминале — `/mode`, `/tools`, агентный ход, Ctrl+C посреди шага.
- Проверить в настоящем терминале: цвета `⏺`/`⎿`, Ctrl+C во время долгого `grep`.

### Замеры и решения

- Стрим с `tool_calls` работает: Spring AI склеивает чанки вызова до `done`, блокирующий `call()`
  не понадобился.
- Время агентного хода «прочитай файл и ответь» на прогретой модели: с thinking 8.5 с, без —
  7.0 с (холодный старт: 21.3 с против 9.7 с). Thinking в `agent` остаётся включённым по
  умолчанию, выключается `/think off` или `--no-think`.

### Найдено и перенесено

- `/context` считает только историю: системный промпт агента и схемы инструментов (несколько
  сотен токенов) в оценку не входят.
- Компакция выполняется между шагами агента только по порогу; одиночный результат инструмента
  ограничен `max-output-chars`, поэтому окно им не переполняется.

---

## M4 — запись, shell, HTTP и запасной протокол

**Цель:** полный набор встроенных инструментов и поддержка моделей без нативных tools.
Требования FR 11 (`write_file`, `edit_file`, `run_command`, `http_fetch`), 14.

### Задачи

1. `FileTools`: `write_file`, `edit_file` (уникальная подстрока, иначе ошибка) с выводом diff в
   консоль.
2. `ShellTools.run_command`: `/bin/zsh -c`, stdout/stderr/exit code, таймаут
   `command-timeout` (120 с) с `destroyForcibly` всего дерева процессов.
3. `HttpTools.http_fetch`: GET через `RestClient`, HTML → текст, обрезка вывода.
4. `TextJsonToolCallMode` + `ToolBlockParser` (блок ```` ```tool ````, сверка со схемой,
   `TOOL_ERROR` при ошибке разбора, stop-маркер) и `ToolCallModeResolver` по
   `harness.tool-call-modes`.
5. `ConsoleTurnOutputAdapter` скрывает сырой блок ```` ```tool ````.

### Тесты

- `ToolBlockParser`: валидный блок, текст вокруг, битый JSON, неизвестный инструмент, нет
  аргумента, несколько блоков, экранирование.
- `run_command`: код выхода, stderr, таймаут; `edit_file`: неуникальная и отсутствующая подстрока.
- Live: агентный сценарий M3 в режиме `text-json` на `qwen3.5:27b`.

---

## M5 — MCP

**Цель:** инструменты внешних MCP-серверов в общем реестре. Требование FR 12.

### Задачи

1. Подключить `spring-ai-starter-mcp-client`, сверить свойства `spring.ai.mcp.client.*` в 2.0.2.
2. stdio-серверы из `~/.harness/mcp.json` (формат Claude Desktop), Streamable HTTP из
   `config.yml`; заголовки авторизации из переменных окружения через кастомизатор транспорта.
3. `McpToolNaming` — префикс `mcp__<server>__<tool>`; регистрация в
   `SpringAiToolExecutorAdapter` рядом с встроенными.
4. `McpStartupReporter` — асинхронный старт, предупреждение о недоступном сервере без падения.
5. `/tools` показывает источник инструмента (builtin / имя сервера).

### Тесты

- Тестовый stdio MCP-сервер (Java main на MCP SDK в test sources): инструменты появляются с
  префиксом и вызываются.
- Недоступный сервер → предупреждение, остальные инструменты работают.

---

## M6 — native image

**Цель:** решить на цифрах, нужен ли native, и при положительном решении собрать его.

### Задачи

1. Замер старта: JVM, JVM + CDS/AOT-кэш, native. Оформить ADR-002 (правило 6.16 стандарта).
2. При решении «да»: `org.graalvm.buildtools.native`, GraalVM 25, `RuntimeHints` для JLine (FFM),
   MapStruct `*MapperImpl`, `@Tool`-методов, MCP stdio; `nativeTest` в CI.
3. При решении «нет»: включить CDS в `bin/harness` и закрыть этап.

---

## Сквозные задачи (без привязки к этапу)

- CI (GitHub Actions): `./gradlew check` на Temurin 25 без live-тестов; OWASP dependency-check.
- Ревизия зависимостей раз в квартал (`./gradlew dependencies`).
- Наблюдаемость: при `--verbose` выводить prefill отдельно от генерации (нужны `prompt_eval_duration`
  и `eval_duration` из ответа Ollama).
