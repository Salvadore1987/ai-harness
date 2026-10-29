# Harness — локальный AI-агент для терминала

## Overview

Harness — CLI-приложение на Java 25, Spring Boot 4 и Spring AI для работы с локальной LLM через Ollama (по умолчанию `qwen3.5:27b`). Первая версия — интерактивный чат-REPL со стримингом и сохранением сессий. Вторая итерация добавляет агентный цикл: модель сама читает и правит файлы, запускает shell-команды и вызывает MCP-инструменты.

Ollama, MCP и описание инструментов (`ToolCallback`) берутся из Spring AI. Вызов инструментов выбирается по модели:
- для моделей с поддержкой tools (основная модель `qwen3.5:27b`) — нативный tool calling Spring AI;
- для моделей без tools (например, `codellama`) — собственный текстовый JSON-протокол как запасной режим.

## Requirements

### Functional Requirements

**Итерация 1 — чат-REPL**

1. Запуск: `harness [--model qwen3.5:27b] [--cwd <dir>] [--resume <sessionId>] [-p "<prompt>"]`.
   - Без `-p` открывается интерактивный REPL.
   - С `-p` выполняется один запрос: ответ печатается в stdout, затем выход (режим для скриптов).
2. REPL на JLine 3:
   - история ввода в `~/.harness/history`;
   - многострочный ввод (`\` в конце строки или Alt+Enter);
   - автодополнение slash-команд и путей после `@`.
3. Ответ модели стримится в терминал по мере генерации токенов (`ChatClient...stream()`).
4. Ctrl+C прерывает текущую генерацию (подписка на `Flux` отменяется), повторный Ctrl+C на пустой строке завершает программу.
5. `@path/to/File.java` в сообщении подставляет содержимое файла в контекст (только внутри `--cwd`).
6. Slash-команды:

   | Команда | Действие |
   |---|---|
   | `/help` | Список команд |
   | `/exit` | Выход |
   | `/clear` | Новая сессия |
   | `/sessions` | Список сохранённых сессий текущей `cwd` |
   | `/resume [id]` | Продолжить сессию (без id — последняя) |
   | `/compact` | Сжать историю: модель суммаризирует старую часть |
   | `/model <name>` | Сменить модель (проверяется по списку локальных моделей Ollama) |
   | `/mode chat\|agent` | Переключить режим (`agent` доступен с итерации 2) |
   | `/tools` | Список доступных инструментов, в том числе MCP |
   | `/context` | Сколько токенов занято и сколько осталось |
   | `/think on\|off` | Включить или выключить режим размышлений модели (без аргумента — показать текущее состояние) |

7. Каждая сессия сохраняется в JSONL через собственный `ChatMemoryRepository`. Её можно продолжить через `--resume` или `/resume`.
8. Контекст управляется автоматически. История в запросе только дописывается и не обрезается на каждом ходе: так Ollama может переиспользовать уже посчитанный префикс промпта (см. «Производительность»). При заполнении `num_ctx` на 80 % выполняется автоматическая компакция с уведомлением в консоли. `/compact` запускает её вручную.
9. Режим размышлений (thinking) для моделей, которые его поддерживают (`qwen3.5`):
   - включён по умолчанию (`harness.thinking.enabled: true`), переключается командой `/think on|off` и флагом `--no-think`; в Ollama передаётся параметр `think`;
   - рассуждения стримятся в консоль приглушённым цветом (ANSI dim/серый) с префиксом `✻`, ответ после них — обычным цветом;
   - при `harness.thinking.display: summary` текст рассуждений не печатается, вместо него одна строка `✻ размышлял 12 с (~840 токенов)`;
   - рассуждения **не попадают в историю**, которая отправляется модели: они занимали бы контекст и мешали бы стабильному префиксу. В агентном цикле в историю идут только текст ответа и `tool_calls`;
   - в JSONL рассуждения записываются только при `harness.thinking.persist: true` (по умолчанию `false`) — для отладки. При `/resume` они не загружаются в контекст;
   - для моделей без поддержки thinking (например, codellama) параметр `think` не передаётся, `/think` сообщает, что модель режим не поддерживает.

**Итерация 2 — агентный режим**

10. Агентный цикл: запрос → ответ модели → извлечение вызова инструмента (текстовый блок или нативный `tool_calls`) → выполнение `ToolCallback` → результат обратно модели → повтор, пока модель не ответит без вызова инструмента.
11. Встроенные инструменты (методы с `@Tool`, регистрируются через `MethodToolCallbackProvider`):

    | Tool | Аргументы | Описание |
    |---|---|---|
    | `read_file` | `path`, `offset?`, `limit?` | Чтение файла с номерами строк |
    | `list_dir` | `path` | Содержимое каталога |
    | `glob` | `pattern` | Поиск файлов по маске |
    | `grep` | `pattern`, `path?`, `glob?` | Поиск по содержимому (regex) |
    | `write_file` | `path`, `content` | Создать или перезаписать файл |
    | `edit_file` | `path`, `oldText`, `newText` | Заменить уникальную подстроку, в терминал выводится diff |
    | `run_command` | `command`, `timeoutSec?` | Выполнить shell-команду (`/bin/zsh -c`), вернуть stdout/stderr/exit code |
    | `http_fetch` | `url` | HTTP GET, вернуть текст (HTML упрощается до текста) |

12. MCP-клиент через `spring-ai-starter-mcp-client`:
    - транспорты stdio и Streamable HTTP;
    - stdio-серверы описываются в `~/.harness/mcp.json` (формат Claude Desktop), Streamable HTTP — в `config.yml` (`spring.ai.mcp.client.streamable-http.connections`), заголовки авторизации — в `harness.mcp.headers.<server>` через плейсхолдеры переменных окружения;
    - инструменты MCP попадают в общий реестр через `SyncMcpToolCallbackProvider` (отдельный на каждый сервер) с префиксом `mcp__<server>__<tool>`;
    - серверы подключаются в фоне после старта; недоступный сервер даёт предупреждение `⚠ MCP-сервер «x» недоступен: …`, остальные инструменты работают.
13. Каждый вызов инструмента отображается компактно (`⏺ read_file(src/Main.java)` + первые строки результата). Подробный вывод — флаг `--verbose`.
14. Ограничений по разрешениям нет: инструменты выполняются без подтверждения. Защита от зацикливания модели остаётся:
    - `maxIterations` агентного цикла (по умолчанию 25);
    - таймаут `run_command` (по умолчанию 120 с);
    - не более 2 подряд неудачных разборов вызова инструмента — после этого цикл останавливается.

### Non-Functional Requirements

- **Старт:** JVM ≤ 2 с (с ленивой инициализацией бинов, `spring.main.lazy-initialization=true`; MCP-клиенты инициализируются асинхронно), в native image ≤ 150 мс.
- **Отзывчивость:** первый токен выводится сразу при получении от Ollama, без буферизации на стороне harness.
- **Чистота вывода:** логи пишутся только в `~/.harness/logs/harness.log`, в консоль идёт только диалог. Баннер Spring выключен.
- **Целевое железо:** Apple M3 Max, 48 ГБ unified memory. Ollama использует Metal; macOS по умолчанию отдаёт GPU около 75 % памяти (~36 ГБ).
- **Производительность (оценка для `qwen3.5:27b` в Q4, уточнить замером на M1):**
  - генерация ~15–20 токенов/с: она упирается в пропускную способность памяти, а веса модели занимают ~17–20 ГБ. **Замер M1:** 20.6 ток/с на установленной `qwen3.5:27b` (NVFP4, MLX-раннер, 19.8 ГБ);
  - обработка промпта (prefill) — сотни токенов/с, поэтому промпт на 30k токенов с нуля считается десятки секунд;
  - отсюда требование: префикс промпта (system prompt + история) между ходами не меняется, чтобы Ollama досчитывала только новые токены. Обрезка истории на каждом ходе запрещена, допустима только компакция по порогу;
  - `num_ctx` одинаков во всех запросах, включая запрос на компакцию: при смене `num_ctx` Ollama перезагружает модель;
  - модель держится в памяти между запросами (`keep-alive: 30m`): холодная загрузка весов ~17 ГБ занимает несколько секунд.
- **Портируемость:** macOS и Linux. Windows вне scope первой версии.
- **Без сети по умолчанию:** в сеть harness ходит только к Ollama, к HTTP MCP-серверам и через `http_fetch`.

## Architecture

### High-Level Design

Гексагональная архитектура, **вариант B** корпоративного стандарта (без DDD): Gradle-модули `harness-application` (ядро), `harness-infrastructure` (адаптеры), `harness-bootstrap` (сборка и wiring). Обоснование и отклонения — в [ADR-001](adr/ADR-001-architecture.md). Spring AI — инфраструктура и живёт только в адаптерах. Ядро не зависит от `org.springframework.ai`, JLine, Jackson и Reactor. Поэтому:
- агентный цикл тестируется без Spring;
- стратегия вызова инструментов меняется без изменения ядра.

```
┌──────────── adapter/in ────────────┐      ┌──────────────── adapter/out ─────────────────────┐
│ cli: Picocli HarnessCommand        │      │ llm:    SpringAiLlmGateway (ChatClient)          │
│      ReplRunner (JLine)            │      │         ├ NativeToolCallMode     (qwen3.5)       │
│      SlashCommandDispatcher        │      │         └ TextJsonToolCallMode   (fallback)      │
│      ConsoleRenderer (TokenSink)   │      │ memory: TokenWindowChatMemory → JsonlChatMemory- │
└───────────────┬────────────────────┘      │         Repository (Spring AI interfaces)        │
                │ port/in (use cases)       │ tools:  SpringAiToolExecutor (ToolCallback)      │
                │                           │         ← @Tool builtin + MCP ToolCallbackProvider│
                │                           └───────────────────▲──────────────────────────────┘
        ┌───────▼───────────────────────────────────────────────┴───────┐
        │ application: ChatService, AgentLoopService, CompactionService │
        │ domain:      Session, Message, ToolCall, ToolResult, ToolSpec │
        └───────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Область | Выбор |
|---|---|
| Язык | Java 25 (records, sealed interfaces, pattern matching, virtual threads) |
| Фреймворк | Spring Boot 4.1.2 (с ней собрана Spring AI 2.0.2), `WebApplicationType.NONE` |
| LLM | Spring AI 2.0.2 (BOM `org.springframework.ai:spring-ai-bom:2.0.2`), `spring-ai-starter-model-ollama`: `OllamaChatModel`, `ChatClient`, стриминг через `Flux<ChatResponse>` |
| Инструменты | Spring AI Tool Calling: `@Tool`, `ToolCallback`, `ToolCallbackProvider`, `ToolCallingManager`; автоматический `ToolCallingAdvisor` отключён (`AdvisorParams.toolCallingAdvisorAutoRegister(false)`) — цикл ведёт harness |
| Память | Интерфейсы Spring AI `ChatMemory` / `ChatMemoryRepository`, собственные реализации на JSONL |
| MCP | `spring-ai-starter-mcp-client` (MCP Java SDK под капотом), stdio + Streamable HTTP |
| CLI | Picocli 4.7 (без стартера: `HarnessCommand` — Spring-бин, `PicocliRunner` запускает его) — аргументы запуска; JLine 3.30 — REPL, история, автодополнение, ANSI |
| JSON | Jackson 3 (`tools.jackson.*`), версия из BOM Boot 4.1.2 |
| Логирование | SLF4J + Log4j2 (`spring-boot-starter-log4j2`), Logback исключён |
| Null-safety | JSpecify `@NullMarked` в каждом пакете + NullAway (Error Prone) как ошибка компиляции |
| Качество | Checkstyle, PMD, SpotBugs, JaCoCo (порог 60 % общий, 80 % `harness-application`) |
| Маппинг | MapStruct (`org.mapstruct:mapstruct` + `mapstruct-processor`) |
| ID | UUIDv7 (`com.fasterxml.uuid:java-uuid-generator`, `Generators.timeBasedEpochGenerator()`) |
| Сборка | Gradle Kotlin DSL, три модуля, fat jar (`bootJar`) только в `harness-bootstrap` + обёртка `bin/harness`; GraalVM native image — отдельным ADR в M6 |
| Тесты | JUnit 5, AssertJ, Mockito, WireMock, ArchUnit |

### Component Overview

```
harness-application   dev.localllm.harness.application
├── model        SessionId, Role, ChatMessage, ChatChunk (Thinking|Content), ChatRequest, ChatCompletion, ModelInfo,
│                ToolCall, ToolResult, ToolSpec (M3)
├── port/in      SendMessageUseCase, CancelTurnUseCase, SwitchModelUseCase, ToggleThinkingUseCase, StartSessionUseCase,
│                ListModelsQuery; ResumeSessionUseCase, CompactSessionUseCase (M2); RunAgentTurnUseCase (M3)
├── port/out     ChatModelPort, ModelCatalogPort, ConversationPort, TurnOutputPort; ToolExecutorPort (M3)
├── dto          command/, query/, result/ (TurnResult, ModelSwitchResult, …)
├── mapper       TurnApplicationMapper (MapStruct)
├── service      SendMessageService, CancelTurnService, SwitchModelService, …; AgentLoopService (M3), CompactionService (M2)
└── exception    HarnessException, LlmUnavailableException, ModelNotFoundException, ThinkingNotSupportedException

harness-infrastructure   dev.localllm.harness.infrastructure
├── in/cli       HarnessCommand, PicocliRunner, ReplRunner, ReplSession, TurnExecutor, SlashCommandDispatcher,
│   │            SlashCommandCompleter;
│   │            FileMentionExpander (M2)
│   ├── mapper   ReplCliMapper (MapStruct)
│   └── handlers CliErrorReporter, LlmErrorHandler, ModelErrorHandler, FallbackErrorHandler,
│                PicocliExceptionHandler (+ ToolErrorHandler в M3)
├── out/ollama   OllamaChatAdapter, OllamaModelCatalogAdapter, OllamaErrorTranslator,
│   │            toolcall/ (ToolCallMode, NativeToolCallMode, TextJsonToolCallMode, ToolBlockParser) (M3–M4)
│   └── mapper   ChatMessageSpringAiMapper, ChatResponseSpringAiMapper, ModelInfoOllamaMapper (MapStruct)
├── out/memory   InMemoryConversationAdapter (M1); TokenWindowChatMemory, JsonlChatMemoryRepository (M2)
├── out/console  ConsoleTurnOutputAdapter
├── out/tools    FileTools, ShellTools, HttpTools, SpringAiToolExecutorAdapter (M3–M4)
├── out/mcp      McpConnector, McpServersFileReader, McpToolNaming, McpTextToolCallback,
│                McpHttpHeaders, McpStartupReporter (M5)
└── config       HarnessProperties (вкл. thinking), TerminalConfig

harness-bootstrap   dev.localllm.harness.bootstrap
├── HarnessApplication
└── config       BeanConfig (связывает application-сервисы с портами)
```

Соответствие имён из разделов ниже именам портов по стандарту: `LlmGateway` → `ChatModelPort`, `ConversationMemory` → `ConversationPort`, `ToolExecutor` → `ToolExecutorPort`, `TokenSink` / `ConsoleRenderer` → `TurnOutputPort` / `ConsoleTurnOutputAdapter`, `ChatService` → `SendMessageService`, `SpringAiLlmGateway` → `OllamaChatAdapter`.

Ответственность компонентов:

- **`ChatService`** — режим `chat`. Сохраняет сообщение пользователя в `ConversationMemory`, берёт окно истории, стримит ответ через `LlmGateway` (без инструментов) в `TokenSink`, сохраняет ответ.
- **`AgentLoopService`** — режим `agent`. В цикле: окно истории → `LlmGateway.turn(...)` с инструментами → если есть `ToolCall`, выполнить через `ToolExecutor`, записать результат в память → повтор. Цикл останавливается, когда в ответе нет вызова, или по лимитам.
- **`SpringAiLlmGateway`** — единственное место, где вызывается `ChatClient`. По модели выбирает `ToolCallMode` через `ToolCallModeResolver`:
  - **`NativeToolCallMode`** (основной режим: qwen3.5, а также qwen2.5-coder, llama3.1+) — передаёт определения инструментов через `ChatClient.tools(...)` (только определения, без исполнения) и отключает автоматический `ToolCallingAdvisor` Spring AI 2.0 (в 2.0.2 нет `internalToolExecutionEnabled`, исполнение перенесено в этот advisor); вызовы берёт из `AssistantMessage.getToolCalls()`; результаты возвращает модели как `ToolResponseMessage`.
  - **`TextJsonToolCallMode`** (запасной режим для моделей без tools, например codellama) — добавляет описание инструментов из `ToolCallback.getToolDefinition()` в system prompt; разбирает блок ```` ```tool ```` из текста ответа через `ToolBlockParser`; результаты возвращает модели как `UserMessage`.
- **`SpringAiToolExecutor`** — общий реестр всех `ToolCallback`: builtin `@Tool` + MCP `ToolCallbackProvider`. MCP-инструменты добавляются при первом обращении к реестру, когда `McpConnector` дождался серверов; встроенные идут первыми и сохраняют свои имена. По имени вызывает `callback.call(argsJson, toolContext)` (в `ToolContext` — рабочая директория для относительных путей), оборачивает ответ в `ToolResult`, обрезает длинный вывод, замеряет длительность. Используется в обоих режимах, так что инструменты не дублируются.
- **`TokenWindowChatMemory`** (реализует Spring AI `ChatMemory`):
  - `add()` дописывает сообщения в репозиторий;
  - `get()` возвращает system prompt → summary последней компакции → все сообщения после неё. Окно не обрезается на каждом ходе, чтобы префикс оставался стабильным для кеша Ollama;
  - при заполнении на `compact-threshold` (80 %) вызывает `CompactionService` до следующего запроса. Если компакция не удалась, срабатывает запасной вариант: отбрасываются самые старые сообщения;
  - встроенный `MessageWindowChatMemory` не подходит, потому что считает сообщения, а не токены.
- **`JsonlChatMemoryRepository`** (реализует Spring AI `ChatMemoryRepository`): файлы `~/.harness/sessions/<conversationId>.jsonl`. `saveAll` сравнивает новый список с файлом и дописывает только новые сообщения; если префикс не совпадает, файл атомарно перезаписывается (temp + move).
- **`CompactionService`** — по `/compact` отправляет старую часть истории модели с просьбой суммаризировать. Результат записывается в JSONL отдельной строкой типа `compaction`.
- **`ConsoleRenderer`** — реализует `TokenSink` (`onThinking(String)`, `onToken(String)`, `onToolCall(ToolCall)`, `onComplete(Usage)`):
  - печатает токены;
  - рассуждения выводит приглушённым цветом с префиксом `✻` или, в режиме `summary`, одной строкой со временем и числом токенов;
  - скрывает сырой блок ```` ```tool ```` и вместо него рисует `⏺ tool(args)`;
  - подсвечивает diff и code-блоки.

Сервисы не содержат маппинга. Все преобразования выполняются MapStruct-мапперами в пакетах `mapper/`:
- domain `Message` ↔ Spring AI `Message` — через `@ObjectFactory` и `@SubclassMapping` для `UserMessage`, `AssistantMessage`, `ToolResponseMessage`, `SystemMessage`;
- Spring AI `Message` ↔ `JsonlLine`.

### Почему не `MessageChatMemoryAdvisor`

Advisor удобен для простого чата. Но harness управляет циклом вызова инструментов сам (отключённый `ToolCallingAdvisor` или текстовый протокол), а документация Spring AI для такого режима рекомендует вести память явно: advisor не видит промежуточных сообщений с результатами инструментов.

Поэтому в обоих режимах память ведёт приложение через порт `ConversationMemory`. Реализация в адаптере — это Spring AI `ChatMemory`, так что её можно заменить на JDBC-репозиторий из Spring AI без изменений в ядре.

### Ключевые интерфейсы (порты ядра)

```java
public interface LlmGateway {
    TurnResponse turn(TurnRequest request, TokenSink sink, CancellationToken cancel);
}

public record TurnRequest(String model, List<Message> window, boolean toolsEnabled) {}
public record TurnResponse(String text, List<ToolCall> toolCalls, Usage usage, boolean cancelled) {}
public record Usage(int promptTokens, int completionTokens) {}

public interface ConversationMemory {
    void append(SessionId id, List<Message> messages);
    List<Message> window(SessionId id);
    ContextUsageDto usage(SessionId id);
    void compact(SessionId id, String summary, MessageId upTo);
}

public interface ToolExecutor {
    List<ToolSpec> available();
    ToolResult execute(ToolCall call, Path workingDirectory);
}

public interface SessionCatalog {
    SessionId create(Path cwd, String model);
    List<Session> list(Path cwd);
}
```

Агентный цикл (упрощённо):

```java
public TurnResult run(SendMessageCommand command) {
    memory.append(command.sessionId(), List.of(Message.user(command.text())));
    int parseFailures = 0;
    for (int i = 0; i < properties.maxIterations(); i++) {
        TurnResponse response = llm.turn(
                new TurnRequest(command.model(), memory.window(command.sessionId()), true),
                command.sink(), command.cancel());
        memory.append(command.sessionId(), List.of(Message.assistant(response.text(), response.toolCalls())));
        if (response.cancelled() || response.toolCalls().isEmpty()) {
            return turnResultMapper.toResult(response, i + 1);
        }
        List<Message> results = response.toolCalls().stream()
                .map(tools::execute)
                .map(Message::toolResult)
                .toList();
        memory.append(command.sessionId(), results);
    }
    throw new IterationLimitExceededException(properties.maxIterations());
}
```

Ошибки разбора в текстовом режиме `TextJsonToolCallMode` возвращает как `ToolCall` специального вида `parse_error`. Его «выполнение» отдаёт модели `TOOL_ERROR` и увеличивает счётчик `parseFailures` (на скетче выше не показано).

### Запасной протокол для моделей без tools (`TextJsonToolCallMode`)

Основная модель `qwen3.5:27b` поддерживает нативный tool calling, и этот режим ей не нужен. Он остаётся для моделей без нативных tools, например codellama: у неё нет tool calling, а её шаблон в Ollama не отображает сообщения с ролью `tool`. Поэтому для таких моделей:

1. В system prompt из `ToolDefinition` (name, description, inputSchema) генерируется описание инструментов и строгий формат вызова — **не более одного вызова на ответ**, в конце сообщения:

   ````
   ```tool
   {"name": "read_file", "args": {"path": "src/main/java/App.java"}}
   ```
   ````

2. `ToolBlockParser` находит в ответе первый ```` ```tool ```` и читает следующий за ним JSON-объект потоковым парсером Jackson — не до ближайшего ```` ``` ````, поэтому строки могут содержать фигурные скобки и ограждения (например, содержимое `write_file`). Текст после блока отбрасывается. Вызов сверяется с `inputSchema` инструмента: инструмент существует, обязательные аргументы есть, лишних нет.
3. Результат возвращается модели как `UserMessage` (признак ошибки виден по тексту `Error: …`, отдельный статус в истории не хранится):

   ```
   TOOL_RESULT read_file:
   <вывод, обрезанный до harness.tools.max-output-chars>
   ```

   В истории вызовы хранятся структурно, как в нативном режиме; в текст (блок у ассистента, `TOOL_RESULT` у пользователя) они превращаются при каждом запросе, поэтому сессию можно продолжить другой моделью в другом режиме.

4. Если разбор не удался (битый JSON, неизвестный инструмент, нет обязательного аргумента), модели отправляется `TOOL_ERROR: <причина>. Повтори вызов в корректном формате`. Два неудачных разбора подряд — цикл останавливается.
5. Генерация обрывается сразу после закрытия блока, чтобы модель не «додумывала» результат инструмента: harness сам прекращает чтение стрима, как только JSON-объект и закрывающее ```` ``` ```` получены, и Ollama останавливает генерацию. Stop-последовательность Ollama для этого не подходит: ```` ``` ```` закрывает и обычные блоки кода, а `TOOL_RESULT` может встретиться в рассуждениях модели. Цена — у шага с вызовом нет счётчиков токенов (они приходят в последнем чанке).
6. Пока ответ стримится, блок скрыт от пользователя: текст, похожий на начало ```` ```tool ````, придерживается до следующего фрагмента. Вместо блока показывается обычная строка `⏺ read_file(…)`.

Режим выбирается в конфиге по префиксу имени модели:

```yaml
harness:
  tool-call-modes:
    "[qwen3.5]": native        # ключи с точкой — в квадратных скобках (правило Spring Boot)
    "[qwen2.5-coder]": native
    "[llama3.1]": native
    "[codellama]": text-json
  default-tool-call-mode: native
```

Побеждает самый длинный подходящий префикс, регистр не учитывается. Модель в режиме `text-json` считается поддерживающей инструменты, даже если Ollama не объявляет у неё `tools`: для неё доступен `/mode agent`.

## Data Model

### Entities

| Сущность | Атрибуты |
|---|---|
| `Session` | `id: UUID (v7)` (= Spring AI `conversationId`), `cwd: Path`, `model: String`, `title: String` (первые 60 символов первого запроса), `createdAt: Instant`, `summary: Optional<String>` |
| `Message` | `id: UUID (v7)`, `role: Role (SYSTEM, USER, ASSISTANT, TOOL_RESULT)`, `content: String`, `toolCalls: List<ToolCall>`, `tokens: int`, `createdAt: Instant` |
| `ToolCall` | `id: String` (UUIDv7 или id из нативного ответа), `name: String`, `argsJson: String` |
| `ToolResult` | `callId: String`, `toolName: String`, `output: String`, `error: boolean`, `truncated: boolean`, `duration: Duration` |
| `ToolSpec` | `name`, `description`, `inputSchema: String` (JSON Schema из `ToolDefinition`), `source: BUILTIN \| MCP`, `server: String?` (имя MCP-сервера) |

### Relationships

- `Session` 1 → N `Message` (упорядочены по UUIDv7 / времени).
- `Message(ASSISTANT)` 0..N → `ToolCall`. `Message(TOOL_RESULT)` ссылается на `ToolCall.id` через `ToolResult.callId`.

### Storage

Файлы JSONL в `~/.harness/sessions/<sessionId>.jsonl`, преимущественно append-only. Индекс для `/sessions` строится сканированием первых строк файлов (`ChatMemoryRepository.findConversationIds()` + строка `meta`).

```jsonl
{"type":"meta","sessionId":"0192...","cwd":"/Users/me/proj","model":"qwen3.5:27b","createdAt":"2026-10-27T10:00:00Z"}
{"type":"message","id":"0192...","role":"USER","content":"Объясни App.java","tokens":12,"createdAt":"..."}
{"type":"message","role":"ASSISTANT","content":"...","toolCalls":[{"id":"...","name":"read_file","argsJson":"{\"path\":\"App.java\"}"}],"createdAt":"..."}
{"type":"message","role":"TOOL_RESULT","callId":"...","toolName":"read_file","content":"...","createdAt":"..."}
{"type":"compaction","summary":"...","replacesUpTo":"0192...","createdAt":"..."}
```

### Конфигурация

`application.yml` в `harness-bootstrap` — по стандарту без констант: каждое значение задаётся как `${HARNESS_*:default}`. Пользовательские переопределения — через `spring.config.import=optional:file:${user.home}/.harness/config.yml` (файл вне репозитория, константы в нём допустимы). Собственные настройки привязываются через `@ConfigurationProperties` (records + `@Validated`), без `@Value`. Свойства чата в Spring AI 2.0.2 плоские: `spring.ai.ollama.chat.model`, `spring.ai.ollama.chat.num-ctx` — без уровня `options`.

```yaml
spring:
  main:
    banner-mode: off
    lazy-initialization: ${HARNESS_LAZY_INIT:true}
  ai:
    ollama:
      base-url: ${HARNESS_OLLAMA_BASE_URL:http://localhost:11434}
      init:
        pull-model-strategy: never      # не скачивать модели молча
      chat:
        model: ${HARNESS_MODEL:qwen3.5:27b}
        num-ctx: ${HARNESS_NUM_CTX:65536}       # M3 Max 48 ГБ: веса ~17 ГБ + KV-кеш; одинаков во всех запросах
        temperature: ${HARNESS_TEMPERATURE:0.2}
        keep-alive: ${HARNESS_KEEP_ALIVE:30m}   # не выгружать модель между запросами
    mcp:
      client:
        name: harness
        type: SYNC
        initialized: false      # McpConnector подключает серверы в фоне
        toolcallback:
          enabled: false        # общий провайдер падает целиком из-за одного сервера
        request-timeout: ${HARNESS_MCP_REQUEST_TIMEOUT:30s}
        # в config.yml:
        # streamable-http:
        #   connections:
        #     remote: {url: https://example.com, endpoint: /mcp}
  config:
    import: optional:file:${user.home}/.harness/config.yml

harness:
  thinking:
    enabled: true           # параметр think для моделей с поддержкой
    display: stream         # stream | summary
    persist: false          # записывать рассуждения в JSONL (только для отладки)
  agent:
    max-iterations: 25
    max-parse-failures: 2
  tools:
    max-output-chars: 8000
    shell: /bin/zsh
    command-timeout: 120s
    http-timeout: 30s
  context:
    num-ctx: 65536            # запрашиваемое окно; фактическое читается из /api/ps
    compact-threshold: 0.8
    keep-tail-share: 0.25     # доля окна под последние сообщения, остающиеся дословно после компакции
    chars-per-token: 3.5      # оценка без калибровки: prompt_eval_count не учитывает кеш
  mcp:
    headers:                  # в config.yml; пустое значение не отправляется
      remote:
        Authorization: "Bearer ${REMOTE_MCP_TOKEN:}"
```

`~/.harness/mcp.json` (формат Claude Desktop). Его читает harness (`McpServersFileReader`), а не `spring.ai.mcp.client.stdio.servers-configuration`: у Spring AI отсутствующий файл роняет старт. Записи без `command` (удалённые серверы) и команды, которых нет в `PATH`, пропускаются с предупреждением:

```json
{
  "mcpServers": {
    "filesystem": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/me/proj"] }
  }
}
```

## API Design

### Взаимодействие с Ollama (через Spring AI)

| Method | Path | Кто вызывает | Назначение |
|---|---|---|---|
| POST | `/api/chat` | `OllamaChatModel.stream()` | Генерация ответа со стримингом. В нативном режиме передаётся поле `tools`, ответ содержит `message.tool_calls` |
| GET | `/api/tags` | `OllamaApi.listModels()` | Health-check при старте, проверка `/model` |
| POST | `/api/show` | `OllamaApi.showModel()` | Метаданные модели (контекст, шаблон) для `/context` и выбора режима |

Стриминг: `chatClient.prompt(prompt).stream().chatResponse()`.
- Каждый чанк передаётся в `TokenSink`.
- Полный ответ собирается через `MessageAggregator`; в нативном режиме из него берутся `toolCalls`.
- Отмена — `Disposable.dispose()` по Ctrl+C.
- Блокирующее ожидание выполняется в виртуальном потоке.

### CLI-интерфейс

| Вызов | Описание |
|---|---|
| `harness` | REPL в текущей директории, новая сессия |
| `harness --resume [id]` | REPL с продолжением сессии |
| `harness -p "prompt" [--mode agent]` | Одиночный запрос, ответ в stdout, код выхода 0/1 |
| `harness --model <name>` | Переопределить модель |
| `harness --cwd <dir>` | Рабочая директория для `@`-упоминаний и инструментов |
| `harness --no-think` | Запуск с выключенным режимом размышлений |
| `harness --verbose` | Подробный вывод вызовов инструментов и статистика токенов |
| `harness --version` / `--help` | Стандартные опции Picocli |

## Authentication & Authorization

- Ollama локальная, аутентификации нет.
- HTTP MCP-серверы: заголовки авторизации — через кастомизатор транспорта Spring AI MCP, значения берутся из переменных окружения. Секреты в файлах не хранятся.
- Модель разрешений намеренно отсутствует (решение пользователя): все инструменты выполняются без подтверждения, путь не ограничивается `cwd`. Ограничение `@`-упоминаний пределами `cwd` остаётся — это защита от опечаток, а не мера безопасности.

## Error Handling

Обработка ошибок на CLI-уровне — в пакете `adapter/in/cli/handlers/`, один класс на тему (`LlmErrorHandler`, `ToolErrorHandler`, `FallbackErrorHandler`). Picocli подключает их через `IExecutionExceptionHandler`, REPL — через общий диспетчер. Адаптер `llm` переводит исключения Spring AI и `WebClientRequestException` в доменные `LlmUnavailableException` / `ModelNotFoundException`.

| Сценарий | Поведение |
|---|---|
| Ollama недоступна (connection refused) | `✗ Ollama недоступна на http://localhost:11434. Запустите: ollama serve`. REPL продолжает работу |
| Модель не найдена | `✗ Модель qwen3.5:27b не найдена. Выполните: ollama pull qwen3.5:27b`. Модели автоматически не скачиваются |
| Модель не поддерживает tools, а выбран режим `native` | Ollama вернёт ошибку → подсказка: переключиться на `text-json` в `harness.tool-call-modes` |
| Таймаут / обрыв стрима | Частичный ответ сохраняется с пометкой `[interrupted]` |
| Ctrl+C во время генерации | Подписка отменяется, частичный ответ сохраняется с пометкой `[cancelled]` |
| Ошибка разбора вызова инструмента | `TOOL_ERROR` возвращается модели; после `max-parse-failures` — остановка цикла и сообщение пользователю |
| Исключение в `ToolCallback` | Отправляется модели как результат с `error=true`, цикл продолжается |
| `run_command` превысил таймаут | Процесс уничтожается (`destroyForcibly` всего дерева), модели уходит `timeout after 120s` и накопленный вывод |
| Ollama завершила ответ без единого токена | Запрос повторяется один раз. Так иногда ведёт себя MLX-раннер Ollama 0.40 при почти полностью закешированном промпте: сразу `done` без текста и `eval_count` |
| Достигнут `max-iterations` | Цикл останавливается: `⚠ Достигнут лимит 25 итераций` |
| MCP-сервер не стартовал | Предупреждение при старте, его инструменты не регистрируются, остальная работа продолжается |
| Повреждённая строка JSONL | Строка пропускается, предупреждение пишется в лог |
| Переполнение контекста | При пороге 80 % выполняется автоматическая компакция. Если она не удалась, отбрасываются самые старые сообщения и выводится предупреждение |

Логирование: SLF4J + Log4j2, файл `~/.harness/logs/harness.log` с ротацией 10 МБ × 5, в консоль логи не пишутся. В коде harness — только уровни INFO и ERROR (стандарт 4.4.2). Для отладки промптов можно включить `SimpleLoggerAdvisor` Spring AI, подняв уровень логгера `org.springframework.ai` в `~/.harness/config.yml`. Вызовы инструментов логируются с длительностью, ошибки MCP — отдельно.

## Testing Plan

Все тесты — по паттерну Arrange-Act-Assert, без `var`.

### Unit Tests

- `ToolBlockParser`:
  - корректный блок;
  - блок с текстом до и после;
  - битый JSON;
  - неизвестный инструмент;
  - отсутствующий обязательный аргумент;
  - несколько блоков (берётся первый);
  - экранированные кавычки и переводы строк в `content`.
- `AgentLoopService` с фейками `LlmGateway`, `ToolExecutor` и in-memory `ConversationMemory` (без Spring):
  - завершение без вызова инструмента;
  - цепочка из N вызовов;
  - лимит итераций;
  - лимит ошибок разбора;
  - отмена.
- `TokenWindowChatMemory`:
  - префикс стабилен между ходами: новый `get()` = прошлый `get()` + новые сообщения;
  - рассуждения не попадают в окно, даже если записаны в JSONL при `persist: true`;
  - при пороге 80 % вызывается компакция, если она не удалась — отбрасываются старые сообщения;
  - system prompt и summary сохраняются всегда;
  - обрезка длинных результатов инструментов.
- `JsonlChatMemoryRepository`:
  - `saveAll` дописывает только новые сообщения;
  - при несовпадении префикса файл перезаписывается;
  - пропуск битых строк;
  - `findConversationIds`.
- `@Tool`-инструменты на `@TempDir`:
  - `read_file` с offset/limit;
  - `edit_file`: неуникальная или отсутствующая подстрока → ошибка;
  - `glob`/`grep`;
  - `run_command`: код выхода, stderr, таймаут.
- `ToolCallModeResolver`, `SlashCommandDispatcher` (включая `/think`), `FileMentionExpander`.
- `ConsoleRenderer`: режимы `stream` и `summary` для рассуждений — на фейковом терминале JLine (`DumbTerminal` над `ByteArrayOutputStream`).
- MapStruct-мапперы: domain ↔ Spring AI `Message`, Spring AI `Message` ↔ `JsonlLine`.

### Integration Tests

- `SpringAiLlmGateway` + `OllamaChatModel` против WireMock (`spring.ai.ollama.base-url` на порт WireMock):
  - стрим NDJSON из нескольких чанков;
  - текстовый режим: блок ```` ```tool ```` разбирается в `ToolCall`;
  - нативный режим: ответ с `message.tool_calls` маппится в `ToolCall`, в запросе есть поле `tools`;
  - 404 по модели;
  - connection refused;
  - обрыв посреди стрима;
  - отмена.
- MCP: тестовый stdio-сервер (маленький Java main на MCP SDK в test sources) — инструменты появляются в `SpringAiToolExecutor` с префиксом и вызываются.
- ArchUnit:
  - `domain` и `application` не зависят от `org.springframework.ai`, `adapter`, JLine, Jackson, Reactor;
  - мапперы лежат только в пакетах `mapper`;
  - обработчики ошибок — только в пакетах `handlers`.

### End-to-End Tests

- `@Tag("live")` (запуск: `./gradlew test -PincludeTags=live`) против реальной локальной Ollama:
  - чат на `qwen3.5:27b` возвращает непустой ответ;
  - агентный режим на fixture-проекте («прочитай `Calc.java` и скажи, что делает метод `add`») прогоняется на `qwen3.5:27b` (native): проверяется вызов `read_file`;
  - тот же сценарий в режиме text-json (на `qwen3.5:27b` с принудительным `text-json` или на `codellama`, если она установлена) — для проверки запасного протокола.
- Smoke-тест fat jar: `java -jar harness.jar -p "say hi"` при поднятом WireMock-стабе Ollama.

## Deployment

- **Цель:** локальная машина разработчика — Apple M3 Max, 48 ГБ (основная). Linux поддерживается, но не является целевой платформой.
- **Настройка Ollama под M3 Max 48 ГБ** (переменные окружения сервера Ollama, например через `launchctl setenv` для Ollama.app):
  - `OLLAMA_FLASH_ATTENTION=1` — снижает расход памяти на attention;
  - `OLLAMA_KV_CACHE_TYPE=q8_0` — примерно вдвое уменьшает KV-кеш при минимальной потере качества;
  - `OLLAMA_NUM_PARALLEL=1` — один слот: параллельные слоты умножают KV-кеш, а harness всё равно работает в одном потоке;
  - `OLLAMA_MAX_LOADED_MODELS=1` — чтобы вторая модель (например, для тестов text-json) не вытеснила основную в CPU;
  - при нехватке памяти под GPU можно поднять лимит: `sudo sysctl iogpu.wired_limit_mb=40960` (сбрасывается после перезагрузки). Оставшихся ~8 ГБ хватит macOS, IDE и JVM harness.
- **Сборка:**
  - `./gradlew bootJar` → `build/libs/harness.jar`;
  - скрипт `bin/harness` (`exec java -XX:TieredStopAtLevel=1 -jar .../harness.jar "$@"`), установка через симлинк в `~/.local/bin`.
- **Native (итерация 3):** `./gradlew nativeCompile`. Spring AI и Boot 4 поддерживают AOT. Дополнительно потребуются reachability metadata для JLine (FFM/JNI terminal provider) и проверка `@Tool`-методов (reflection hints) и MCP stdio. Проверка через `nativeTest`.
- **CI (опционально):** GitHub Actions — `./gradlew check` на Temurin 25 (без live-тестов) и матрица native-сборки для macOS/Linux.
- **Конфигурация:**
  - `application.yml` в jar;
  - `~/.harness/config.yml`, `~/.harness/mcp.json`;
  - переменные окружения (`SPRING_AI_OLLAMA_BASE_URL`, `HARNESS_AGENT_MAX_ITERATIONS`, …).
- **Наблюдаемость:** файловый лог и команда `/context`. При `--verbose` после каждого ответа выводится статистика: `usage.promptTokens`, `usage.completionTokens`, токены в секунду.

## Этапы реализации

1. ✅ **M1 — каркас и чат:** три Gradle-модуля, Spring Boot 4.1.2 + Spring AI 2.0.2, Picocli + JLine REPL, `OllamaChatAdapter` (стриминг без tools), `SendMessageService`, режим размышлений (требование 9, без `persist`), Ctrl+C, режим `-p`, slash-команды `/help /exit /clear /model /think`, история в памяти процесса.
2. ✅ **M2 — сессии и контекст:** `JsonlChatMemoryRepository`, `TokenWindowChatMemory`, `--resume`/`/resume`/`/sessions`, `/compact`, `/context`, `@`-упоминания файлов.
3. ✅ **M3 — агентный цикл, чтение:** `@Tool` `FileTools` (read/list/glob/grep), `SpringAiToolExecutor`, `NativeToolCallMode`, `AgentLoopService`, `/mode agent`.
4. ✅ **M4 — запись, shell, запасной режим:** `write_file`, `edit_file` с diff, `run_command`, `http_fetch`, `TextJsonToolCallMode` + `ToolCallModeResolver`.
5. ✅ **M5 — MCP:** `spring-ai-starter-mcp-client`, stdio из `mcp.json` + Streamable HTTP из `config.yml` с заголовками, фоновое подключение `McpConnector`, префиксы `mcp__<server>__<tool>`, `/tools` с именем сервера.
6. **M6 — native image.**

## Open Questions

- ✅ **Версия Spring AI** — решено: **2.0.2**, собрана против Spring Boot **4.1.2** (Framework 7.0.10, Jackson 3.1.7). Проверено по исходникам на M1:
  - `think` задаётся через `OllamaChatOptions.builder().enableThinking()` / `disableThinking()` (есть и уровни для моделей с `ThinkLevel`);
  - в стриме рассуждения каждого чанка лежат в метаданных `AssistantMessage` под ключом `thinking`, обход через `OllamaApi` не нужен;
  - стрим идёт через `WebClient.bodyToFlux`, `Disposable.dispose()` обрывает HTTP-обмен;
  - `OllamaApi.showModel()` возвращает `capabilities` (`thinking`, `tools`, …) — по ним определяется поддержка thinking и выбор режима вызова инструментов;
  - свойства чата плоские (`spring.ai.ollama.chat.model`, `num-ctx`, `keep-alive`, `think`).
  Сигнатуры `ToolCallingManager`, `ChatMemoryRepository` и свойства MCP-клиента сверяются на M2–M5.
- ✅ **Модель** — решено: **`qwen3.5:27b`** с нативным tool calling, режим `native` по умолчанию. Codellama и текстовый протокол остаются запасным вариантом для моделей без tools.
- ✅ **Железо** — решено: Apple M3 Max, 48 ГБ. Стартовое значение `num_ctx` — 65536, настройки Ollama описаны в разделе Deployment.
- ✅ **`num_ctx` на MLX-раннере** — решено в M2. Раннер применяет `num_ctx` только при загрузке модели и не перезагружает её, если позже приходит другое значение (`32768` в M1 — модель была загружена раньше с другим окном; после перезагрузки `/api/ps` показывает `context_length: 65536`). Harness берёт бюджет компакции из `/api/ps` (`OllamaContextBudgetAdapter`), `HARNESS_NUM_CTX` — только запасное значение. Чтобы сменить окно, модель нужно выгрузить: `ollama stop qwen3.5:27b`.
- ✅ **Компакция по бюджету токенов** — решено на M3: дословно остаются последние сообщения в пределах `keep-tail-share` окна (хвост не начинается с результата инструмента), блоки `<file>` в сжимаемой части уходят в сводку как `@путь`.
- **Задержка стрима на один чанк.** `OllamaApi.streamingChat` группирует чанки через `windowUntil` и отдаёт каждый только с приходом следующего; при обрыве стрима последний чанк теряется. На отзывчивость при живом стриме не влияет (проверено `OllamaLiveIT`). Для `tool_calls` не мешает: Spring AI держит окно открытым до `done` и отдаёт вызов одним чанком (проверено WireMock и `AgentLiveIT`).
- **Холодный старт CLI** — 1.6 с (`harness --version`, JVM без CDS); требование ≤ 2 с выполнено. Ход с прогретой моделью без размышлений — около 0.3–0.5 с поверх старта.
- ✅ **Режим размышлений** — решено (требование 9): включён по умолчанию, рассуждения выводятся приглушённым цветом, в историю не попадают, переключение — `/think on|off`. На M1 проверить, как Spring AI 2.0.2 передаёт параметр `think` в `OllamaChatOptions` и где отдаёт текст рассуждений в стриме (отдельное поле или метаданные `AssistantMessage`). Если Spring AI не прокидывает его в стрим, в адаптере `llm` понадобится обход через `OllamaApi`.
- **Стоимость рассуждений.** Рассуждения генерируются с той же скоростью (~15–20 токенов/с) и могут на сотни токенов задерживать ответ и каждый шаг агентного цикла. Ollama не позволяет ограничить их длину для qwen3.5, есть только вкл/выкл. Замер M3 (`AgentLiveIT`, «прочитай файл и ответь», прогретая модель): с thinking 8.5 с, без — 7.0 с. Решено: thinking в `agent` остаётся включённым по умолчанию, выключается `/think off` или `--no-think`.
- **Оценка токенов.** Формула «символы / коэффициент» приблизительна. `JTokkitTokenCountEstimator` из Spring AI считает токены токенизатором OpenAI, а не Qwen, поэтому тоже даёт неточность. Калибровать по `usage.promptTokens`.
- ✅ **Стриминг в нативном режиме** — решено на M3: стрим работает, блокирующий `call()` не нужен. В Spring AI 2.0.2 инструменты исполняет `ToolCallingAdvisor`, который `ChatClient` регистрирует сам; harness отключает его параметром `AdvisorParams.toolCallingAdvisorAutoRegister(false)`.
- ✅ **MCP-клиент Spring AI 2.0.2** — сверено на M5 (MCP Java SDK 2.1.0): автоконфигурация по умолчанию инициализирует клиентов синхронно при создании бина, отсутствующий `servers-configuration` роняет контекст, а `SyncMcpToolCallbackProvider` на всех клиентов падает, если недоступен один. Поэтому клиенты создаются с `initialized: false`, общий провайдер выключен, `McpConnector` инициализирует каждый сервер на своём виртуальном потоке и строит провайдер на сервер. Без серверов стартер не меняет время старта (2.8 с на запуск и выход REPL и до, и после), с двумя stdio-серверами +0.2 с.
- **Отсутствие разрешений** — сознательное решение. Если позже понадобится защита, `ToolExecutor` можно обернуть декоратором `PermissionGate` без изменения ядра.
- **Отложено:** RAG по коду (Spring AI `VectorStore` + `nomic-embed-text`), несколько вызовов инструментов за один ответ в текстовом режиме, Windows, TUI.
