# Harness

Локальный AI-агент для терминала на Java 25, Spring Boot 4.1 и Spring AI 2.0 поверх Ollama.
Сейчас реализован этап M1: чат-REPL со стримингом, режимом размышлений, отменой по Ctrl+C и
режимом одиночного запроса. Спецификация — [docs/java-ai-harness.md](docs/java-ai-harness.md),
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

Опции: `--model <тег>`, `--cwd <каталог>`, `--no-think`, `--verbose` (статистика токенов),
`-p/--print <запрос>`, `--help`, `--version`.

В REPL: `/help`, `/exit`, `/clear`, `/model [имя]`, `/think [on|off]`. Многострочный ввод —
`\` в конце строки или Alt+Enter. Ctrl+C во время генерации останавливает её, дважды на пустой
строке — выход.

## Конфигурация

Все значения задаются переменными окружения (или в `~/.harness/config.yml`):

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `HARNESS_MODEL` | `qwen3.5:27b` | Модель на старте |
| `HARNESS_OLLAMA_BASE_URL` | `http://localhost:11434` | Адрес Ollama |
| `HARNESS_NUM_CTX` | `65536` | Окно контекста (`num_ctx`) |
| `HARNESS_KEEP_ALIVE` | `30m` | Сколько Ollama держит модель в памяти |
| `HARNESS_TEMPERATURE` | `0.2` | Температура |
| `HARNESS_THINKING_ENABLED` | `true` | Режим размышлений на старте |
| `HARNESS_THINKING_DISPLAY` | `stream` | `stream` — текст рассуждений, `summary` — одна строка |
| `HARNESS_HOME` | `~/.harness` | История ввода, логи, `config.yml` |

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
