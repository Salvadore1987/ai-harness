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
| M2 | Сессии в JSONL, контекст, компакция, `@`-файлы | M1 | |
| M3 | Агентный цикл, нативный tool calling, инструменты чтения | M2 | |
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
- `num_ctx` не применяется MLX-раннером (см. Open Questions спеки) → задача M2.1

---

## M2 — сессии и контекст

**Цель:** история переживает перезапуск, контекст не переполняется, файлы подставляются через
`@`. Требования FR 5, 7, 8, 9 (`persist`), команды `/sessions /resume /compact /context`.

### Задачи

1. **Фактический бюджет контекста.** Выяснить, как задать контекст MLX-раннера
   (`OLLAMA_CONTEXT_LENGTH` на сервере), и добавить в `ModelCatalogPort` чтение фактического
   контекста загруженной модели из `/api/ps`. Бюджет компакции считать от минимума
   `HARNESS_NUM_CTX` и фактического значения.
2. **Модели и порты:**
   - `application/model`: `SessionInfo` (id, cwd, model, title, createdAt), `ContextUsage`,
     `CompactionSummary`;
   - `port/out`: `SessionCatalogPort` (create, list по cwd, find), расширить `ConversationPort`
     методами `usage(SessionId)` и `compact(SessionId, summary, upTo)`;
   - `port/in`: `ResumeSessionUseCase`, `ListSessionsQuery`, `CompactSessionUseCase`,
     `GetContextUsageQuery`; команды и результаты к ним.
3. **Хранилище** (`infrastructure/out/memory`):
   - `JsonlChatMemoryRepository` — реализация Spring AI `ChatMemoryRepository` (сверить
     сигнатуры 2.0.2), строки `meta` / `message` / `compaction`, дозапись, атомарная перезапись
     при несовпадении префикса, пропуск битых строк;
   - `TokenWindowChatMemory` — Spring AI `ChatMemory`: system prompt → summary → сообщения после
     компакции, без обрезки на каждом ходе;
   - `JsonlConversationAdapter` + `JsonlSessionCatalogAdapter` реализуют порты поверх них;
     `InMemoryConversationAdapter` остаётся для тестов;
   - мапперы `JsonlLineMapper` (MapStruct), DTO `JsonlLine`.
4. **Оценка токенов:** `TokenEstimator` (символы / `chars-per-token`) с калибровкой по
   `usage.promptTokens` последнего хода.
5. **Компакция:** `CompactSessionService` — суммаризация старой части истории тем же
   `num_ctx`; автоматический запуск при 80 %; запасной вариант — отбросить старые сообщения с
   предупреждением.
6. **CLI:** `--resume [id]`, `/resume [id]`, `/sessions`, `/compact`, `/context`;
   `FileMentionExpander` для `@путь` (только внутри `--cwd`); автодополнение путей после `@`.
7. **Thinking `persist`:** при `harness.thinking.persist=true` писать рассуждения в JSONL, не
   загружая их в окно.

### Тесты

- Unit: `TokenWindowChatMemory` (стабильный префикс, summary, порог 80 %),
  `JsonlChatMemoryRepository` на `@TempDir` (дозапись, перезапись, битые строки),
  `FileMentionExpander` (выход за `cwd` запрещён), сервисы на фейках.
- WireMock: компакция отправляет запрос суммаризации с тем же `num_ctx`.
- Live: два хода → перезапуск с `--resume` → модель помнит контекст.

### Ручной сценарий

Диалог из трёх ходов → `/exit` → `harness --resume` → вопрос о прошлом ходе; `/context`;
`/compact`; `@README.md объясни`; двойной Ctrl+C; Alt+Enter; `/clear`.

### Риски

- Сигнатуры `ChatMemoryRepository` в 2.0.2 могут отличаться от 1.x — сверить по исходникам до
  реализации.
- Без решения задачи 1 порог компакции будет считаться от неверного окна.

---

## M3 — агентный цикл и инструменты чтения

**Цель:** `/mode agent` — модель сама читает код через нативный tool calling `qwen3.5`.
Требования FR 10, 11 (`read_file`, `list_dir`, `glob`, `grep`), 13, 14.

### Задачи

1. **Модели и порты:** `ToolSpec`, `ToolCall`, `ToolResult`; `ToolExecutorPort`
   (`available()`, `execute(ToolCall)`); расширить `ChatModelPort` ответом с `toolCalls`;
   `RunAgentTurnUseCase` + `RunAgentTurnCommand`.
2. **`AgentLoopService`:** цикл до ответа без вызовов; лимиты `max-iterations` (25) и
   `max-parse-failures` (2); результаты инструментов в историю; отмена между шагами.
3. **Нативный режим** (`out/ollama/toolcall`): `NativeToolCallMode` — `toolCallbacks` в
   `OllamaChatOptions` с `internalToolExecutionEnabled(false)`, разбор
   `AssistantMessage.getToolCalls()`, результаты как `ToolResponseMessage`; маппер
   `ToolCallSpringAiMapper`.
4. **Инструменты** (`out/tools`): `FileTools` с `@Tool` (`read_file` с offset/limit и номерами
   строк, `list_dir`, `glob`, `grep`), обрезка вывода до `max-output-chars`;
   `SpringAiToolExecutorAdapter` — реестр `ToolCallback`.
5. **История:** `ConversationPort` и JSONL хранят вызовы и результаты инструментов
   (`role TOOL_RESULT`).
6. **CLI:** `/mode chat|agent`, `/tools`, отображение `⏺ read_file(path)` + первые строки
   результата, `--verbose` для полного вывода; `ToolErrorHandler` в `handlers/`.
7. **Конфиг:** `harness.agent.*`, `harness.tools.max-output-chars`.

### Тесты

- Unit: `AgentLoopService` на фейках (без вызовов, цепочка N вызовов, лимит итераций, отмена).
- WireMock: ответ с `message.tool_calls` маппится в `ToolCall`, в запросе есть `tools`; учесть
  задержку стрима на один чанк (Open Questions спеки).
- `FileTools` на `@TempDir`.
- Live: «прочитай `Calc.java` и скажи, что делает `add`» → был вызван `read_file`.

### Риски

- Агрегация `tool_calls` в стриме при `internalToolExecutionEnabled=false`; запасной вариант —
  блокирующий `call()` на шагах с инструментами.
- Время шага с thinking (~20 ток/с) — замерить и решить, выключать ли thinking в `agent` по
  умолчанию.

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
