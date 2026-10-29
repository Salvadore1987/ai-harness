# ADR-002. GraalVM native image

- **Статус:** принято
- **Дата:** 2026-10-29
- **Контекст:** ADR-001 (п. 7), стандарт 6.16 (п. 6–7), требование «Старт» спецификации, этап M6

## Контекст

Harness — CLI: его запускают много раз в день, в том числе из скриптов с `-p`. Требование
спецификации — старт JVM ≤ 2 с, native ≤ 150 мс. Стандарт разрешает native только по ADR с
замером и рекомендует сначала CDS/AOT-кэш.

## Замеры

Apple M3 Max, macOS, JDK 25.0.1 (Homebrew) и Oracle GraalVM 25.0.4. Медиана 10 запусков после
прогрева файлового кэша: `--version` и REPL до `/exit` (stdin — канал, `HARNESS_HOME` без
MCP-серверов). Флаги JVM — из прежнего `bin/harness`: `-XX:TieredStopAtLevel=1
-XX:+UseSerialGC`. Кэши CDS и AOT обучены на прогоне REPL `/help`, `/tools`, `/exit`.

| Вариант | `--version` | REPL `/exit` | RSS |
|---|---|---|---|
| fat jar (`java -jar harness.jar`) | 1.83 с | 2.49 с | — |
| распакованный jar (`-Djarmode=tools extract`) | 1.40 с | 1.86 с | — |
| + CDS (`-XX:ArchiveClassesAtExit`) | 0.99 с | 1.37 с | — |
| + AOT-кэш JDK 25 (`-XX:AOTCacheOutput`) | 0.88 с | 1.23 с | 251 МБ |
| native image | 0.05 с | 0.07 с | 105 МБ |

Под псевдотерминалом (`script`, настоящий системный терминал JLine) native REPL до `/exit` —
0.15 с. Первый запуск только что собранного бинарника — около 1.7 с: macOS проверяет новый
исполняемый файл; со второго запуска цифры как в таблице.

Попутно найдено: JLine открывал терминал через провайдер `exec` (процессы `stty`), это ~300 мс
в native и ~350 мс на JVM. До исправления fat jar стартовал за 2.27 с и не укладывался в
требование. Теперь `TerminalConfig` задаёт один провайдер — FFM; список провайдеров
(`providers("ffm,exec")`) в native всё равно доходит до `exec`.

Также проверены и отклонены:

- **Spring AOT на JVM** (`-Dspring.aot.enabled=true` + AOT-кэш): 1.13 с вместо 1.25 с до
  исправления JLine — 10 % ценой тех же ограничений Spring AOT, что и у native (ниже).

## Решение

**Native image — единственный способ запуска.** `bin/harness` запускает
`harness-bootstrap/build/native/nativeCompile/harness`; JVM-запуск
(`java --enable-native-access=ALL-UNNAMED -jar harness.jar`) остаётся для разработки и тестов, без
CDS и AOT-кэша.

Сборка:

- `org.graalvm.buildtools.native` 1.1.14 (версия из BOM Spring Boot 4.1.2) и
  `org.springframework.boot.aot` в `harness-bootstrap`; GraalVM 25 через toolchain
  (`nativeImageCapable`);
- классы `processAot` добавляются в classpath образа явно: Boot-плагин этого не сделал;
  `jar` модуля включён (классификатор `plain`), иначе в образе нет классов bootstrap;
- `--enable-native-access=ALL-UNNAMED` для FFM JLine; Checkstyle, PMD и SpotBugs не проверяют
  сгенерированный AOT-код.

Reachability metadata:

| Что | Почему | Где |
|---|---|---|
| компоненты `HarnessProperties` и вложенных records | Hibernate Validator читает поля рефлексией | `NativeHintsConfig` |
| `FileTools`, `ShellTools`, `HttpTools`, `PlainTextResultConverter` | Spring AI вызывает `@Tool`-методы и создаёт конвертер рефлексией | `NativeHintsConfig` |
| `JsonlLine`, `JsonlToolCall`, `OllamaRunningModels`, `McpServersFile` | читает и пишет Jackson | `NativeHintsConfig` (`BindingReflectionHintsRegistrar`) |
| команды и конвертеры picocli | picocli читает аннотации рефлексией | `picocli-codegen` при компиляции |

MapStruct-реализации, бины и `@ConfigurationProperties` Spring AOT регистрирует сам; JLine,
Netty, MCP SDK и JSON Schema validator приносят свою metadata в jar.

Проверка — `NativeImageSmokeIT` (тег `native`, `./gradlew :harness-bootstrap:test
-PincludeTags=native` собирает образ): бинарник против WireMock-Ollama и stdio MCP-сервера —
`--version`, slash-команды REPL, чат в `-p`, агентный ход с `read_file` и MCP-инструментом,
`text-json`, `/compact` + `--resume`, недоступная Ollama. Весь набор — несколько секунд. Полный
`nativeTest` не используется: Mockito и WireMock внутри образа требуют собственной metadata, а
ядро и адаптеры уже покрыты тестами на JVM.

## Последствия

- Старт 0.05–0.15 с вместо 1.8–2.5 с, требование native ≤ 150 мс выполнено; память процесса
  меньше в 2.4 раза.
- Сборка образа — около 70 с и до 7 ГБ RAM; бинарник 155 МБ, только macOS arm64 (сборка на
  каждой целевой платформе отдельно).
- Каждый новый `@Tool`-класс, DTO для Jackson или валидируемый record настроек нужно добавить в
  `NativeHintsConfig`; пропуск ловит `NativeImageSmokeIT`, если сценарий его задевает. Прогон
  смоук-теста входит в Definition of Done этапа.
- Spring AOT фиксирует условия при сборке: `@ConditionalOnProperty`
  (`harness.cli.enabled`, `spring.ai.mcp.client.enabled`/`type`, `toolcallback.enabled`) и
  набор автоконфигураций больше не переключаются из `config.yml`. Значения свойств (адреса,
  таймауты, MCP-подключения, заголовки) читаются при запуске, как раньше.
- CI (сквозная задача) должен собирать образ на GraalVM 25 и запускать тег `native`.
