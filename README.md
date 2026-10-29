# Harness

Локальный AI-агент для терминала на Java 25, Spring Boot 4.1 и Spring AI 2.0 поверх Ollama.
Реализованы этапы M1–M2: чат-REPL со стримингом, режимом размышлений, отменой по Ctrl+C,
режимом одиночного запроса, сессиями в JSONL с продолжением, автоматической компакцией истории и
подстановкой файлов через `@путь`. План этапов — [docs/implementation-plan.md](docs/implementation-plan.md). Спецификация — [docs/java-ai-harness.md](docs/java-ai-harness.md),
архитектурные решения — [docs/adr/ADR-001-architecture.md](docs/adr/ADR-001-architecture.md).

## Требования

- JDK 25 (`/usr/libexec/java_home -v 25` находит Homebrew-сборку)
- Ollama с моделью: `ollama pull qwen3.5:27b`

## Сборка и запуск

```bash
./gradlew build                       # компиляция, NullAway, тесты, Checkstyle/PMD/SpotBugs, JaCoCo
./gradlew :harness-bootstrap:bootJar  # build/libs/harness.jar
bin/harness                           # REPL
bin/harness -p "Объясни, что такое CAP" # один запрос, ответ в stdout, код выхода 0/1
ln -s "$PWD/bin/harness" ~/.local/bin/harness
```

Опции: `--model <тег>`, `--cwd <каталог>`, `--resume [ID]` (последняя сессия директории или
указанная), `--no-think`, `--verbose` (статистика токенов), `-p/--print <запрос>`, `--help`,
`--version`.

В REPL: `/help`, `/exit`, `/clear`, `/sessions`, `/resume [id]`, `/compact`, `/context`,
`/model [имя]`, `/think [on|off]`. `@путь` прикладывает файл из рабочей директории (Tab —
автодополнение). Многострочный ввод — `\` в конце строки или Alt+Enter. Ctrl+C во время
генерации останавливает её, дважды на пустой строке — выход.

Сессии хранятся в `~/.harness/sessions/<id>.jsonl`. Когда история занимает 80 % окна модели
(фактическое окно читается из `ollama ps`), старая часть заменяется сводкой.

## Конфигурация

Все значения задаются переменными окружения (или в `~/.harness/config.yml`):

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `HARNESS_MODEL` | `qwen3.5:27b` | Модель на старте |
| `HARNESS_OLLAMA_BASE_URL` | `http://localhost:11434` | Адрес Ollama |
| `HARNESS_NUM_CTX` | `65536` | Окно контекста (`num_ctx`); применяется при загрузке модели |
| `HARNESS_COMPACT_THRESHOLD` | `0.8` | Доля окна, при которой история сжимается |
| `HARNESS_KEEP_LAST_MESSAGES` | `4` | Сколько последних сообщений остаётся дословно |
| `HARNESS_CHARS_PER_TOKEN` | `3.5` | Символов на токен для оценки |
| `HARNESS_KEEP_ALIVE` | `30m` | Сколько Ollama держит модель в памяти |
| `HARNESS_TEMPERATURE` | `0.2` | Температура |
| `HARNESS_THINKING_ENABLED` | `true` | Режим размышлений на старте |
| `HARNESS_THINKING_DISPLAY` | `stream` | `stream` — текст рассуждений, `summary` — одна строка |
| `HARNESS_THINKING_PERSIST` | `false` | Писать рассуждения в файл сессии (для отладки) |
| `HARNESS_HOME` | `~/.harness` | Сессии, история ввода, логи, `config.yml` |

Логи — только в `~/.harness/logs/harness.log`, консоль занята диалогом.

## Тесты

```bash
./gradlew test                    # unit + WireMock + ArchUnit, без Ollama
./gradlew test -PincludeTags=live # против локальной Ollama с qwen3.5:27b
```

## Модули

| Модуль | Содержимое |
|---|---|
| `harness-application` | Ядро без фреймворков: модели, порты, use case'ы, MapStruct-маппер |
| `harness-infrastructure` | Адаптеры: CLI (Picocli + JLine), Ollama (Spring AI), консоль, память |
| `harness-bootstrap` | `HarnessApplication`, `BeanConfig`, `application.yml`, Log4j2, `bootJar` |
