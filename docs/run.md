# Запуск скомпилированной версии

Как запускать уже собранный бинарник `harness`. Как его собрать — в [build.md](build.md),
работа с REPL, агентом и сессиями — в [usage.md](usage.md).

## Что нужно для запуска

- Бинарник `harness` — macOS на Apple Silicon (arm64). Это самостоятельный исполняемый файл:
  **JDK и GraalVM для запуска не нужны.**
- Запущенная [Ollama](https://ollama.com) с моделью:

  ```bash
  ollama pull qwen3.5:27b
  ```

## Где лежит бинарник

После сборки — `harness-bootstrap/build/native/nativeCompile/harness`. Запускать можно
напрямую или через скрипт `bin/harness` из репозитория:

```bash
bin/harness --version
```

Скрипт ищет бинарник по пути выше; другой путь задаётся переменной `HARNESS_BINARY`:

```bash
HARNESS_BINARY=/opt/harness/harness bin/harness
```

## Установка в PATH

Вариант 1 — ссылка на скрипт в репозитории (после пересборки подхватится новый бинарник):

```bash
ln -s "$PWD/bin/harness" ~/.local/bin/harness
```

Вариант 2 — скопировать бинарник, тогда репозиторий для запуска не нужен:

```bash
cp harness-bootstrap/build/native/nativeCompile/harness ~/.local/bin/harness
```

Убедитесь, что `~/.local/bin` есть в `PATH`, и проверьте:

```bash
harness --version   # harness 0.1.0
```

### Перенос на другой Mac

Скопируйте один файл `harness` — других файлов не требуется. Если macOS блокирует запуск
скачанного файла, снимите атрибут карантина:

```bash
xattr -d com.apple.quarantine ~/.local/bin/harness
```

Первый запуск нового бинарника занимает около 2 с — система проверяет файл; дальше старт
0.05–0.15 с.

## Запуск

Harness работает с каталогом, из которого запущен (или указанным в `--cwd`):

```bash
cd ~/projects/my-app
harness                                   # интерактивный REPL
harness -p "Объясни, что такое CAP"       # один запрос: ответ в stdout, код выхода 0/1
harness --mode agent                      # REPL в агентном режиме: модель читает и правит файлы
harness --resume                          # продолжить последнюю сессию этого каталога
harness list                              # установленные модели и их возможности
harness ps                                # модели в памяти Ollama: размер, GPU, контекст, выгрузка
```

Подкоманды `list` и `ps` печатают таблицу и сразу выходят (код 0, при ошибке 1), сессию не
открывают. Обе принимают префикс имени: `harness list qwen`.

```text
$ harness ps
МОДЕЛЬ                             РАЗМЕР ПРОЦЕССОР      КОНТЕКСТ  ВЫГРУЗКА
qwen3.5:27b                         20 GB 100% GPU          32768  через 9 мин
```

| Опция | Назначение |
|---|---|
| `--model <тег>` | Модель Ollama вместо `qwen3.5:27b` |
| `--cwd <каталог>` | Рабочая директория вместо текущей |
| `--mode chat\|agent` | Режим на старте, по умолчанию `chat` |
| `--resume [ID]` | Продолжить последнюю сессию каталога или указанную |
| `--no-think` | Начать с выключенным режимом размышлений |
| `--verbose` | Статистика токенов и полный вывод инструментов |
| `-p`, `--print <запрос>` | Один запрос без REPL |
| `-h`, `--help` / `-V`, `--version` | Справка и версия |

Что делать дальше в REPL — в [usage.md](usage.md).

## Настройка без пересборки

Бинарник читает настройки при каждом запуске, пересобирать его не нужно:

1. Переменные окружения `HARNESS_*` — полная таблица в
   [README](../README.md#конфигурация).
2. Файл `~/.harness/config.yml` — те же свойства в YAML.
3. `~/.harness/mcp.json` — stdio MCP-серверы.

```bash
HARNESS_OLLAMA_BASE_URL=http://gpu-box:11434 harness   # Ollama на другой машине
HARNESS_MODEL=qwen3.5:27b HARNESS_NUM_CTX=32768 harness
```

```yaml
# ~/.harness/config.yml
harness:
  model: qwen3.5:27b
  thinking:
    display: summary
```

Ограничение native image: включение и выключение компонентов (`@ConditionalOnProperty`,
например `harness.cli.enabled` или `spring.ai.mcp.client.enabled`) зафиксировано при сборке
и из `config.yml` не меняется. Адреса, таймауты, модели, MCP-подключения и заголовки
меняются как обычно.

## Файлы на диске

Всё хранится в `~/.harness` (другой каталог — `HARNESS_HOME`):

| Путь | Содержимое |
|---|---|
| `config.yml` | Настройки |
| `mcp.json` | stdio MCP-серверы |
| `sessions/<id>.jsonl` | Сохранённые сессии |
| `history` | История ввода REPL |
| `logs/harness.log` | Лог; в консоль логи не пишутся |

## Если не запускается

| Симптом | Что проверить |
|---|---|
| `harness executable not found at …` | Бинарник не собран (`./gradlew :harness-bootstrap:nativeCompile`) или неверный `HARNESS_BINARY` |
| `cannot execute binary file` / `Bad CPU type` | Бинарник собран под другую платформу; соберите на этой машине |
| macOS: «невозможно проверить разработчика» | `xattr -d com.apple.quarantine <путь>` |
| Ошибка соединения с Ollama | Ollama запущена, адрес совпадает с `HARNESS_OLLAMA_BASE_URL` |
| Модель не найдена | `ollama pull <тег>`, тег совпадает с `--model` / `HARNESS_MODEL` |
| Настройка из `config.yml` не действует | Проверьте отступы YAML; условные компоненты не переключаются (см. выше) |

Подробности ошибок — в `~/.harness/logs/harness.log`.
