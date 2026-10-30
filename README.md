# Harness

Локальный AI-агент для терминала на Java 25, Spring Boot 4.1 и Spring AI 2.0 поверх Ollama.
Реализованы этапы M1–M6: чат-REPL со стримингом, режимом размышлений, отменой по Ctrl+C,
режимом одиночного запроса, сессиями в JSONL с продолжением, автоматической компакцией истории,
подстановкой файлов через `@путь` и агентным режимом, в котором модель сама читает и правит код,
выполняет команды и скачивает страницы (`read_file`, `list_dir`, `glob`, `grep`, `write_file`,
`edit_file`, `run_command`, `http_fetch`) через нативный tool calling или текстовый протокол для
моделей без него, с инструментами внешних MCP-серверов. Собирается в GraalVM native image и
стартует за 0.05–0.15 с. Документация: [сборка](docs/build.md), [запуск скомпилированной версии](docs/run.md), [использование](docs/usage.md). План этапов — [docs/implementation-plan.md](docs/implementation-plan.md). Спецификация — [docs/java-ai-harness.md](docs/java-ai-harness.md),
архитектурные решения — [ADR-001](docs/adr/ADR-001-architecture.md) (архитектура),
[ADR-002](docs/adr/ADR-002-native-image.md) (native image).

## Требования

- GraalVM 25 (Oracle GraalVM или GraalVM CE) — для сборки бинарника; Gradle находит его как
  toolchain с `native-image`. Для разработки и тестов достаточно любого JDK 25
- Ollama с моделью: `ollama pull qwen3.5:27b`

## Сборка и запуск

```bash
./gradlew build                             # компиляция, NullAway, тесты, Checkstyle/PMD/SpotBugs, JaCoCo
./gradlew :harness-bootstrap:nativeCompile  # бинарник, около 70 с и до 7 ГБ памяти
bin/harness                                 # REPL
bin/harness -p "Объясни, что такое CAP" # один запрос, ответ в stdout, код выхода 0/1
bin/harness --mode agent -p "Что делает Calc.add?" # агент читает файлы сам
bin/harness --mode agent -p "Исправь Calc.add и запусти тесты" # правит файлы и выполняет команды
ln -s "$PWD/bin/harness" ~/.local/bin/harness
```

Для разработки можно запускать на JVM без пересборки образа:
`./gradlew :harness-bootstrap:bootJar && java --enable-native-access=ALL-UNNAMED -jar
harness-bootstrap/build/libs/harness.jar`. Первый запуск свежесобранного бинарника на macOS
дольше (около 2 с) — система проверяет новый исполняемый файл.

Опции: `--model <тег>`, `--cwd <каталог>`, `--resume [ID]` (последняя сессия директории или
указанная), `--mode chat|agent`, `--no-think`, `--verbose` (статистика токенов и полный вывод
инструментов), `-p/--print <запрос>`, `--help`, `--version`.

В REPL: `/help`, `/exit`, `/clear`, `/sessions`, `/resume [id]`, `/compact`, `/context`,
`/model [имя]`, `/think [on|off]`, `/mode [chat|agent]`, `/tools [префикс]`. `@путь` прикладывает файл из рабочей директории (Tab —
автодополнение). Многострочный ввод — `\` в конце строки или Alt+Enter. Ctrl+C во время
генерации останавливает её, дважды на пустой строке — выход.

Сессии хранятся в `~/.harness/sessions/<id>.jsonl`. Когда история занимает 80 % окна модели
(фактическое окно читается из `ollama ps`), старая часть заменяется сводкой; дословно остаются
последние сообщения, умещающиеся в четверть окна.

В режиме `agent` (`/mode agent` или `--mode agent`) каждый вызов инструмента виден как
`⏺ read_file(src/Main.java)` с первыми строками результата; `edit_file` показывает цветной diff.
Инструменты выполняются без подтверждения; относительные пути считаются от `--cwd`.
`run_command` запускает `zsh -c` с пустым stdin и по таймауту (120 с) убивает всё дерево
процессов. Цикл останавливается после 25 запросов к модели без финального ответа. При `-p` с
перенаправленным выводом вызовы инструментов идут в stderr, в stdout — только ответ.

Модели без нативного tool calling работают через текстовый протокол: инструменты описываются в
системном промпте, модель пишет вызов блоком ```` ```tool ```` в конце ответа, harness прячет
его из вывода. Режим выбирается по префиксу имени модели в `~/.harness/config.yml` (ключи с
точкой — в квадратных скобках):

```yaml
harness:
  tool-call-modes:
    "[codellama]": text-json
    "[qwen3.5]": native
  default-tool-call-mode: native
```

### MCP

Инструменты внешних MCP-серверов доступны агенту под именами `mcp__<сервер>__<инструмент>`, в
`/tools` во второй колонке — имя сервера. stdio-серверы описываются в `~/.harness/mcp.json` в
формате Claude Desktop:

```json
{
  "mcpServers": {
    "filesystem": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/me/proj"] }
  }
}
```

Streamable HTTP-серверы и их заголовки — в `~/.harness/config.yml`; значения заголовков лучше
брать из переменных окружения, пустое значение не отправляется:

```yaml
spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            remote: { url: https://mcp.example.com, endpoint: /mcp }
harness:
  mcp:
    headers:
      remote:
        Authorization: "Bearer ${REMOTE_MCP_TOKEN:}"
```

Серверы подключаются в фоне после запуска, REPL их не ждёт; первый агентный ход или `/tools`
дожидается подключения. Сервер, который не запустился или не ответил, пропускается с
предупреждением `⚠ MCP-сервер «…» недоступен: …`, остальные инструменты работают. Список
инструментов фиксируется на всю сессию.

## Конфигурация

Все значения задаются переменными окружения (или в `~/.harness/config.yml`):

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `HARNESS_MODEL` | `qwen3.5:27b` | Модель на старте |
| `HARNESS_OLLAMA_BASE_URL` | `http://localhost:11434` | Адрес Ollama |
| `HARNESS_NUM_CTX` | `65536` | Окно контекста (`num_ctx`); применяется при загрузке модели |
| `HARNESS_COMPACT_THRESHOLD` | `0.8` | Доля окна, при которой история сжимается |
| `HARNESS_KEEP_TAIL_SHARE` | `0.25` | Доля окна под последние сообщения, которые компакция оставляет дословно |
| `HARNESS_CHARS_PER_TOKEN` | `3.5` | Символов на токен для оценки |
| `HARNESS_KEEP_ALIVE` | `30m` | Сколько Ollama держит модель в памяти |
| `HARNESS_TEMPERATURE` | `0.2` | Температура |
| `HARNESS_THINKING_ENABLED` | `true` | Режим размышлений на старте |
| `HARNESS_THINKING_DISPLAY` | `stream` | `stream` — текст рассуждений, `summary` — одна строка |
| `HARNESS_THINKING_PERSIST` | `false` | Писать рассуждения в файл сессии (для отладки) |
| `HARNESS_AGENT_MAX_ITERATIONS` | `25` | Запросов к модели в одном агентном ходе |
| `HARNESS_AGENT_MAX_PARSE_FAILURES` | `2` | Шагов подряд с нечитаемым вызовом или несуществующим инструментом до остановки |
| `HARNESS_TOOLS_MAX_OUTPUT_CHARS` | `8000` | До скольких символов обрезается вывод инструмента для модели |
| `HARNESS_TOOLS_SHELL` | `/bin/zsh` | Оболочка `run_command` (`<shell> -c`) |
| `HARNESS_TOOLS_COMMAND_TIMEOUT` | `120s` | Таймаут `run_command` по умолчанию |
| `HARNESS_TOOLS_HTTP_TIMEOUT` | `30s` | Таймаут соединения и чтения `http_fetch` |
| `HARNESS_DEFAULT_TOOL_CALL_MODE` | `native` | Режим вызова инструментов для моделей без префикса в `tool-call-modes` |
| `HARNESS_MCP_REQUEST_TIMEOUT` | `30s` | Таймаут запроса к MCP-серверу |
| `HARNESS_HOME` | `~/.harness` | Сессии, история ввода, логи, `config.yml`, `mcp.json` |

Логи — только в `~/.harness/logs/harness.log`, консоль занята диалогом.

## Тесты

```bash
./gradlew test                    # unit + WireMock + ArchUnit, без Ollama
./gradlew test -PincludeTags=live # против локальной Ollama с qwen3.5:27b
./gradlew :harness-bootstrap:test -PincludeTags=native # собирает образ и проверяет бинарник
```

## Модули

| Модуль | Содержимое |
|---|---|
| `harness-application` | Ядро без фреймворков: модели, порты, use case'ы, MapStruct-маппер |
| `harness-infrastructure` | Адаптеры: CLI (Picocli + JLine), Ollama (Spring AI), консоль, память, инструменты, MCP-клиент |
| `harness-bootstrap` | `HarnessApplication`, `BeanConfig`, `application.yml`, Log4j2, native image (`nativeCompile`), `bootJar` |
