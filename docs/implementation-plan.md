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
  2. `./gradlew test -PincludeTags=live` зелёный против локальной `qwen3.5:27b`; с M6 также
     `./gradlew :harness-bootstrap:test -PincludeTags=native` (собирает образ, ADR-002);
  3. ручной сценарий этапа пройден в настоящем терминале;
  4. спецификация, README и при необходимости ADR обновлены; этап отмечен ✅ в спецификации.

## Сводка

| Этап | Цель | Зависит от | Статус |
|---|---|---|---|
| M1 | Каркас и чат-REPL со стримингом и thinking | — | ✅ |
| M2 | Сессии в JSONL, контекст, компакция, `@`-файлы | M1 | ✅ |
| M3 | Агентный цикл, нативный tool calling, инструменты чтения | M2 | ✅ |
| M4 | Запись файлов, shell, HTTP, запасной текстовый протокол | M3 | ✅ |
| M5 | MCP-клиент (stdio + Streamable HTTP) | M3 | ✅ |
| M6 | Native image (по ADR с замером) | M1–M5 | ✅ |
| M7 | Провайдеры LLM с выбором в runtime: Ollama + Anthropic ([план](provider-plan.md)) | M1–M6 | — |

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

## M4 — запись, shell, HTTP и запасной протокол ✅

**Цель:** полный набор встроенных инструментов и поддержка моделей без нативных tools.
Требования FR 11 (`write_file`, `edit_file`, `run_command`, `http_fetch`), 14.

### Задачи

1. ✅ `FileTools`: `write_file` (создаёт недостающие каталоги) и `edit_file` с аргументами
   `oldText` / `newText` (`new` — ключевое слово Java, а Spring AI берёт имя аргумента из имени
   параметра): подстрока должна встречаться ровно один раз, иначе ошибка с числом вхождений.
   Результат — unified diff (`TextDiff`, одна область изменений, 3 строки контекста); консоль
   показывает его целиком до 40 строк, `+` зелёным, `-` красным. Общий `ToolContexts` для
   рабочей директории.
2. ✅ `ShellTools.run_command`: `harness.tools.shell -c` (`/bin/zsh`), stdin — `/dev/null`,
   stdout/stderr читаются виртуальными потоками (не более 1 МБ на поток, остальное дочитывается
   и отбрасывается), `exit code` / `stdout` / `stderr` в ответе; таймаут `timeoutSec` или
   `command-timeout` (120 с) → `destroyForcibly` процесса и всех потомков, модели уходит
   `timeout after Ns` и вывод до этого момента. Ненулевой код выхода — не ошибка инструмента.
3. ✅ `HttpTools.http_fetch`: GET через `RestClient` на JDK `HttpClient` (редиректы, таймаут
   `http-timeout` 30 с), только `http`/`https`, тело не больше 2 МБ, бинарные типы отклоняются,
   статус ≥ 400 — ошибка с началом тела. HTML → текст своим `HtmlText` (без jsoup): скрипты и
   стили удаляются, заголовки и пункты списков остаются в виде Markdown.
4. ✅ Режимы вызова инструментов (`out/ollama/toolcall`): интерфейс `ToolCallMode` (сообщения,
   нативные определения, `ToolCallCollector` на один ответ) с реализациями `NativeToolCallMode` и
   `TextJsonToolCallMode`; `ToolCallModeResolver` — самый длинный префикс из
   `harness.tool-call-modes`, иначе `default-tool-call-mode`. `ToolBlockParser` читает первый
   блок ```` ```tool ```` потоковым парсером Jackson (строки могут содержать скобки и
   ограждения) и сверяет вызов со схемой. История остаётся структурной, в текст она
   превращается при каждом запросе: блок у ассистента, `TOOL_RESULT <tool>:` у пользователя.
5. ✅ Ошибка разбора: `ChatCompletion.toolCallError`; `AgentLoopService` записывает вызов
   `ToolCall.PARSE_ERROR` и результат `TOOL_ERROR: <причина>. Repeat the call in the correct
   format.`, шаг считается в `max-parse-failures`. Нечитаемый блок остаётся в тексте ассистента.
6. ✅ Скрытие блока при стриме — в `TextJsonToolCallMode` (а не в `ConsoleTurnOutputAdapter`,
   как планировалось): консоль не знает о протоколе, а `ChatCompletion.content` сразу без блока.
   Текст, похожий на начало ```` ```tool ````, придерживается до следующего фрагмента.
7. ✅ Каталог моделей добавляет `TOOLS` модели в режиме `text-json`, поэтому для неё доступен
   `/mode agent`; подсказка `LlmErrorHandler` на «does not support tools» предлагает
   `harness.tool-call-modes`.
8. ✅ Системный промпт агента упоминает запись файлов, команды и HTTP, просит `edit_file` вместо
   `write_file` для существующих файлов.
9. ✅ Конфиг: `harness.tools.shell`, `command-timeout`, `http-timeout`,
   `harness.tool-call-modes` (`"[codellama]": text-json`), `harness.default-tool-call-mode`.

### Тесты

- ✅ `ToolBlockParser`: валидный блок, текст вокруг, битый JSON, не объект, неизвестный
  инструмент, нет `name`, нет обязательного аргумента, лишний аргумент, `args` не объект,
  несколько блоков, экранирование (кавычки, `\n`, ограждения и скобки в строках), закрытие блока
  при стриме.
- ✅ `TextJsonToolCallMode`: промпт с инструментами, вызовы и результаты как текст, `parse_error`
  без блока, блок, разрезанный по фрагментам, придержанный хвост, режим без инструментов;
  `ToolCallModeResolver`: самый длинный префикс, регистр, умолчание.
- ✅ WireMock: в `text-json` запрос без `tools`, история как текст; блок скрыт, вызов прочитан,
  стрим оборван после блока; ошибка разбора; повтор пустого ответа.
- ✅ `run_command`: код выхода, stderr, рабочая директория, пустой stdin, таймаут с убийством
  дочернего процесса; `edit_file`: неуникальная, отсутствующая и пустая подстрока, diff;
  `write_file`; `TextDiff`; `http_fetch` на WireMock: HTML → текст, JSON, редирект, 404,
  картинка, `file://`.
- ✅ Ядро: `TOOL_ERROR` в истории и лимит подряд идущих ошибок разбора; консоль — цветной diff;
  `HarnessApplicationIT` — привязка `tool-call-modes` с точкой в ключе.
- ✅ Live (`AgentLiveIT`): сценарий M3 в режиме `text-json` на `qwen3.5:27b` (вызван
  `read_file`, блок не попал в ответ); «исправь `add`» → `edit_file`, в файле `a + b`.

### Ручной сценарий

- ✅ `bin/harness --mode agent --no-think -p …`: `read_file` → `edit_file` с diff →
  `run_command(cat Calc.java | wc -l)`, файл исправлен; `http_fetch(https://example.com)` в
  режиме `text-json` через `~/.harness/config.yml`.
- Проверить в настоящем терминале: цвета diff, Ctrl+C во время долгого `run_command`.

### Замеры и решения

- Stop-последовательность Ollama не используется: harness сам обрывает стрим, как только блок
  закрыт. ```` ``` ```` закрывает и обычные блоки кода, а `TOOL_RESULT` встречается в
  рассуждениях. Цена — у шага с вызовом в `text-json` нет счётчиков токенов.
- `text-json` на `qwen3.5:27b` без thinking: 11–13 с на «прочитай файл и ответь» против
  10–11 с в нативном режиме.
- MLX-раннер Ollama 0.40.11 примерно в 1 из 8 запросов с почти полностью закешированным
  промптом (903 из 904 токенов) сразу отвечает `done` без токенов и без `eval_count` — из-за
  этого агент молча заканчивал ход пустым ответом (флак `AgentLiveIT` с thinking).
  `OllamaChatAdapter` повторяет такой запрос один раз; после этого 5 прогонов из 5 зелёные.

### Найдено и перенесено

- Ctrl+C во время `run_command` не прерывает команду: отмена проверяется между инструментами,
  команда доживает до конца или до таймаута. Нужен `ToolExecutorPort.cancelActive()` с
  убийством дерева процессов.
- `http_fetch` упрощает HTML регулярными выражениями: страницы, собранные скриптами, дают
  почти пустой текст.
- В `text-json` модель может вызвать только один инструмент за ответ (по спецификации).

---

## M5 — MCP ✅

**Цель:** инструменты внешних MCP-серверов в общем реестре. Требование FR 12.

### Задачи

1. ✅ `spring-ai-starter-mcp-client` (MCP Java SDK 2.1.0). Свойства 2.0.2 сверены по исходникам:
   по умолчанию клиенты инициализируются синхронно прямо в бине `mcpSyncClients`, а общий
   `SyncMcpToolCallbackProvider` падает целиком, если не ответил один сервер. Поэтому
   `initialized: false`, `toolcallback.enabled: false`, `request-timeout` 30 с, имя клиента
   `harness`.
2. ✅ stdio-серверы читает `McpServersFileReader` из `<harness.home>/mcp.json` в формате Claude
   Desktop и отдаёт бином `List<NamedClientMcpTransport>` — автоконфигурация создаёт клиентов
   для всех таких списков. `servers-configuration` Spring AI не используется: отсутствующий файл
   роняет старт. Записи без `command` и команды, которых нет в `PATH`, пропускаются с
   предупреждением; неизвестные поля игнорируются. Streamable HTTP — штатно,
   `spring.ai.mcp.client.streamable-http.connections` в `config.yml`; заголовки
   `harness.mcp.headers.<server>.<имя>` добавляет `McpHttpHeaders`
   (`McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>`), пустые не отправляются.
3. ✅ `McpToolNaming` (`McpToolNamePrefixGenerator`) — `mcp__<server>__<tool>`, сервер берётся из
   `title` клиента (Spring AI кладёт туда имя подключения), символы чистит `McpToolUtils.format`.
   `SpringAiToolExecutorAdapter` строит реестр при первом обращении: встроенные, затем MCP;
   `ToolSpec` получил поле `server`. Ответ MCP Spring AI отдаёт JSON-списком контента —
   `McpTextToolCallback` превращает список текстов в текст, ошибку инструмента — в её текст.
4. ✅ Асинхронный старт и отчёт разделены (в плане был один `McpStartupReporter`):
   `McpConnector` (`@Lazy(false)`) по `ApplicationStartedEvent` подключает каждый сервер на
   виртуальном потоке и строит провайдер на сервер, `servers()` ждёт их один раз;
   `McpStartupReporter` копит предупреждения и печатает их на потоке, который ждёт инструменты
   (первый агентный ход или `/tools`), чтобы не ломать строку ввода JLine. Закрытие —
   `closeGracefully` в `destroy()`.
5. ✅ `/tools`: колонка источника — `builtin` или имя сервера, ширина колонок по самому длинному
   имени.

### Тесты

- ✅ `EchoMcpServer` (stdio-сервер на MCP SDK в test sources, запускается отдельной JVM с тестовым
  classpath): инструменты `mcp__echo__echo` / `mcp__echo__fail` появляются после встроенных с
  `server = echo`, вызываются через реестр, ответ — текст, ошибка — `Error: broken on purpose`.
- ✅ Недоступный сервер (нет процесса, таймаут инициализации 1 с) → одно предупреждение, остальные
  инструменты работают; `mcp.json`: нет файла, смешанные записи, битый JSON, нет `mcpServers`,
  поиск команды в `PATH`; `McpToolNaming`, `McpTextToolCallback`, заголовки на WireMock.
- ✅ `HarnessApplicationIT`: битый stdio-сервер в `mcp.json` и HTTP-сервер на WireMock —
  контекст поднимается, встроенные инструменты на месте, заголовок `Authorization` из
  `harness.mcp.headers` дошёл до сервера.
- ✅ Live (`AgentLiveIT`): «вызови инструмент echo MCP-сервера» → `mcp__echo__echo`, 9.6 с.

### Ручной сценарий

- ✅ `bin/harness` с `HARNESS_HOME` во временном каталоге, в `mcp.json` — `EchoMcpServer` и
  несуществующая команда: `/tools` показывает `mcp__echo__*` с источником `echo` и предупреждение
  про `broken`; `--mode agent -p` вызывает `mcp__echo__echo` в `native` и `text-json`, ошибка
  `mcp__echo__fail` доходит до модели текстом.
- Проверить в настоящем терминале с реальными серверами (`@modelcontextprotocol/server-filesystem`
  через `npx`, удалённый Streamable HTTP с токеном): в этой сессии скачивание npm-пакета было
  запрещено.

### Замеры и решения

- Запуск и выход REPL: 2.8 с без MCP — как на `main` до подключения стартера; с двумя
  stdio-серверами 3.0 с (старт JVM сервера идёт в фоне, на выходе — `TERM` и ожидание процесса).
- Команда, которой нет, раньше держала первый `/tools` 20 с: `StdioClientTransport` теряет ошибку
  запуска процесса, и `initialize` ждёт `initializationTimeout` (20 с, `request-timeout` на него не
  влияет). Теперь такая команда отсекается при чтении `mcp.json`. Сервер, который запустился, но
  молчит, по-прежнему ждёт 20 с — предупреждение «нет ответа на инициализацию».

### Найдено и перенесено

- Список инструментов фиксируется при первом обращении: сервер, подключившийся позже (или
  сменивший список, `tools/list_changed`), до перезапуска не виден. Так префикс промпта не
  меняется посреди сессии.
- Ресурсы, промпты, sampling и elicitation MCP не поддерживаются — только инструменты.
- Ошибка MCP-инструмента разбирается из `toString()` записей SDK (`SyncMcpToolCallback` не отдаёт
  сам результат); при смене формата сообщение вернётся как есть.

---

## M6 — native image ✅

**Цель:** решить на цифрах, нужен ли native, и при положительном решении собрать его.

### Задачи

1. ✅ Замер старта (медиана 10 запусков, `--version` / REPL до `/exit`): fat jar 1.83 / 2.49 с,
   распакованный jar 1.40 / 1.86 с, + CDS 0.99 / 1.37 с, + AOT-кэш JDK 25 0.88 / 1.23 с, native
   0.05 / 0.07 с; RSS 251 МБ против 105 МБ. [ADR-002](adr/ADR-002-native-image.md): **только
   native**, JVM-запуск — для разработки без CDS и AOT-кэша.
2. ✅ `org.graalvm.buildtools.native` 1.1.14 (из BOM Boot) и `org.springframework.boot.aot`,
   GraalVM 25.0.4 через toolchain `nativeImageCapable`. Boot-плагин не добавил результат
   `processAot` в classpath образа и не нашёл классы bootstrap при выключенном `jar` — оба
   прописаны явно (`jar` с классификатором `plain`). Checkstyle/PMD/SpotBugs не проверяют
   сгенерированный AOT-код.
3. ✅ Reachability metadata: `NativeHintsConfig` (records `HarnessProperties` для Hibernate
   Validator, `@Tool`-классы и `PlainTextResultConverter`, DTO Jackson через
   `BindingReflectionHintsRegistrar`) и `picocli-codegen` для команд picocli. Хинты для
   MapStruct и MCP stdio не понадобились: `*MapperImpl` — бины, их обрабатывает Spring AOT, MCP SDK
   и JLine несут свою metadata. `--enable-native-access=ALL-UNNAMED` — аргумент сборки.
4. ✅ JLine: провайдер по умолчанию в native (и явный список провайдеров) доходит до `exec`, а тот
   запускает `stty` — ~300 мс. `TerminalConfig` задаёт один провайдер FFM; это ускорило и JVM
   (fat jar ≈2.2 → 1.83 с).
5. ✅ `bin/harness` запускает бинарник (`HARNESS_BINARY` переопределяет путь).
6. ✅ Вместо `nativeTest` — `NativeImageSmokeIT` (тег `native`, сборка образа — зависимость
   задачи): Mockito и WireMock внутри образа потребовали бы своей metadata.

### Тесты

- ✅ `NativeImageSmokeIT` (7 сценариев, ~3 с на готовом образе): `--version` быстрее секунды
  (после прогревочного запуска), slash-команды REPL с MCP-инструментами в `/tools`, чат в `-p` с
  записью сессии, агентный ход с `read_file` и `mcp__echo__echo` (результаты уходят модели),
  `text-json`, `/compact` + `--resume`, недоступная Ollama → `✗` и ненулевой код; в выводе нет
  `Missing*Registration`, `UnsupportedFeatureError` и исключений.
- ✅ `EchoMcpServer` перенесён в test fixtures `harness-infrastructure` — его запускает и
  смоук-тест.

### Ручной сценарий

- ✅ Native против живой `qwen3.5:27b`: чат, агент (`read_file` → `edit_file` → `run_command`,
  файл исправлен), `--resume`, `mcp__echo__echo`, `http_fetch(https://example.com)`; REPL под
  псевдотерминалом (`script`) — интерактивный режим JLine, 0.15 с до приглашения.
- Проверить в настоящем терминале: цвета, Ctrl+C во время генерации, Alt+Enter, Tab-дополнение.

### Замеры и решения

- Сборка образа — 70 с, пик RSS 7 ГБ; бинарник 155 МБ.
- Первый запуск свежесобранного бинарника — около 1.7 с: macOS проверяет новый исполняемый файл.
- Spring AOT на JVM (`-Dspring.aot.enabled=true` + AOT-кэш) давал 10 % и отклонён; в native его
  ограничения приняты: `@ConditionalOnProperty` фиксируются при сборке (ADR-002).

### Найдено и перенесено

- Каждый новый `@Tool`-класс, DTO Jackson и валидируемый record настроек — в `NativeHintsConfig`;
  смоук-тест — часть Definition of Done.
- CI: сборка образа на GraalVM 25 и тег `native` (сквозная задача).
- Образ собирается только под платформу сборки (сейчас macOS arm64).

---

## Сквозные задачи (без привязки к этапу)

- CI (GitHub Actions): `./gradlew check` на Temurin 25 без live-тестов; сборка native image на
  GraalVM 25 и `-PincludeTags=native`; OWASP dependency-check.
- Ревизия зависимостей раз в квартал (`./gradlew dependencies`).
- Наблюдаемость: при `--verbose` выводить prefill отдельно от генерации (нужны `prompt_eval_duration`
  и `eval_duration` из ответа Ollama).
- Сохранение ответов сверх `/save` и `save_answer` ([ADR-004](adr/ADR-004-saving-answers.md)):
  `/export [файл]` — вся сессия в markdown из JSONL; `/record [файл|off]` и `--output` — запись
  потока в файл во время генерации вторым адаптером `TurnOutputPort`.
