# Сборка

Как устроена сборка Harness: модули, инструменты, проверки качества и native image. Запуск
готового бинарника — в [run.md](run.md), работа с REPL и агентом — в [usage.md](usage.md).

## Требования

| Что | Зачем |
|---|---|
| JDK 25 | Компиляция, тесты, запуск на JVM |
| GraalVM 25 с `native-image` (Oracle GraalVM или CE) | Только для `nativeCompile` |
| ~7 ГБ свободной памяти | Сборка native image |
| Ollama с моделью | Только для тестов с тегом `live` |

Gradle ставить не нужно — используется wrapper (`./gradlew`, Gradle 9.2.1). JDK и GraalVM Gradle
находит сам как toolchain по версии из `gradle/libs.versions.toml` (`jdk = "25"`); для
`nativeCompile` выбирается toolchain с `native-image` (`nativeImageCapable`). Если toolchain не
находится, укажите путь явно:

```bash
./gradlew -Porg.gradle.java.installations.paths=/Library/Java/JavaVirtualMachines/graalvm-25/Contents/Home \
  :harness-bootstrap:nativeCompile
```

## Основные задачи

| Команда | Что делает |
|---|---|
| `./gradlew build` | Компиляция, тесты, все проверки качества, покрытие, jar-файлы |
| `./gradlew check` | То же без сборки jar |
| `./gradlew test` | Unit-, WireMock- и ArchUnit-тесты, без Ollama |
| `./gradlew test -PincludeTags=live` | Тесты против локальной Ollama с `qwen3.5:27b` |
| `./gradlew :harness-bootstrap:test -PincludeTags=native` | Собирает образ и прогоняет смоук-тест бинарника |
| `./gradlew :harness-bootstrap:bootJar` | `harness-bootstrap/build/libs/harness.jar` для запуска на JVM |
| `./gradlew :harness-bootstrap:nativeCompile` | Бинарник `harness-bootstrap/build/native/nativeCompile/harness` |

Тесты с тегами `live` и `native` по умолчанию исключены; `-PincludeTags=<тег>` запускает
**только** тесты с этим тегом.

## Модули

```text
harness-bootstrap ──► harness-infrastructure ──► harness-application
```

| Модуль | Плагины | Содержимое |
|---|---|---|
| `harness-application` | `java-library`, `java-test-fixtures` | Ядро без фреймворков: модели, порты, use case'ы, MapStruct-мапперы |
| `harness-infrastructure` | `java-library`, `java-test-fixtures` | Адаптеры: CLI (Picocli + JLine), Ollama и MCP (Spring AI), консоль, память, инструменты |
| `harness-bootstrap` | Spring Boot, GraalVM Native Build Tools, Spring AOT | Точка входа, `BeanConfig`, `application.yml`, Log4j2, `bootJar`, `nativeCompile` |

Зависимость направлена только внутрь; границы проверяют ArchUnit-тесты в `harness-bootstrap`.
Test fixtures первых двух модулей переиспользуются тестами модулей выше.

## Версии зависимостей

Все версии — в каталоге `gradle/libs.versions.toml`. Spring-зависимости берут версии из BOM
Spring Boot 4.1 и Spring AI 2.0, которые подключаются корневым `build.gradle.kts` ко всем
модулям. Логирование — Log4j2: `spring-boot-starter-logging` и Logback исключены глобально.

## Общие настройки модулей

Корневой `build.gradle.kts` применяет к каждому модулю:

- **Компиляция:** Java 25 через toolchain, UTF-8, `-parameters`, `-Xlint:all` (кроме
  `processing` и `serial`).
- **Error Prone + NullAway:** NullAway — уровень ошибки для пакетов `dev.localllm`; nullability
  описывается аннотациями JSpecify (`@Nullable`). Для тестов и сгенерированного кода Error Prone
  выключен.
- **MapStruct:** `-Amapstruct.defaultComponentModel=spring` — реализации мапперов становятся
  Spring-бинами.
- **Тесты:** JUnit 5, AssertJ; JVM-флаги `--enable-native-access=ALL-UNNAMED` и
  `-XX:+EnableDynamicAgentLoading`; после тестов строится отчёт JaCoCo.

## Проверки качества

Все проверки входят в `check` и роняют сборку при нарушении.

| Инструмент | Конфигурация | Что проверяет |
|---|---|---|
| Checkstyle | `config/checkstyle/checkstyle.xml` | Строка до 100 символов, отступ 2 пробела, без `*`-импортов, обязательные фигурные скобки, Javadoc публичных типов и методов, именование; ноль предупреждений |
| PMD | `config/pmd/ruleset.xml` | Сложность метода: цикломатическая 10, когнитивная 15, NPath 200, вложенность `if` 3; пустые `catch`, неиспользуемые поля, методы и переменные |
| SpotBugs | `config/spotbugs/exclude.xml` | Ошибки уровня HIGH; HTML-отчёт в `build/reports/spotbugs` |
| JaCoCo | `build.gradle.kts` модулей | Покрытие строк: application ≥ 80 %, infrastructure ≥ 50 %, весь проект ≥ 60 % |

Сгенерированный код в проверки не попадает: `*MapperImpl` (MapStruct) исключены из PMD,
SpotBugs и покрытия, код Spring AOT — из Checkstyle, PMD и SpotBugs.

Отчёты лежат в `<модуль>/build/reports/` (`checkstyle`, `pmd`, `spotbugs`, `jacoco`, `tests`).

## Native image

Бинарник собирается в `harness-bootstrap` плагином `org.graalvm.buildtools.native` вместе со
Spring AOT. Почему native и какие замеры легли в основу — в
[ADR-002](adr/ADR-002-native-image.md).

```bash
./gradlew :harness-bootstrap:nativeCompile
```

Около 70 с и до 7 ГБ памяти; результат — `harness-bootstrap/build/native/nativeCompile/harness`
(~155 МБ, macOS arm64; под другую платформу образ собирается на ней же).

Как настроено:

- `processAot` генерирует определения бинов и runtime hints; его классы явно добавлены в
  classpath образа.
- Обычный `jar` модуля включён (классификатор `plain`), иначе классы bootstrap не попадут в
  образ.
- `--enable-native-access=ALL-UNNAMED` — JLine работает с терминалом через FFM.
- Reachability metadata для того, что читается рефлексией, регистрирует
  `harness-infrastructure/.../config/NativeHintsConfig.java`; команды picocli описывает
  `picocli-codegen` при компиляции. Новый `@Tool`-класс, DTO для Jackson или валидируемый
  record настроек нужно добавить в `NativeHintsConfig`.
- Spring AOT фиксирует условия при сборке: `@ConditionalOnProperty` и набор
  автоконфигураций в бинарнике уже не переключаются из `config.yml`, значения свойств
  читаются при запуске как обычно.

Проверка образа — `NativeImageSmokeIT`: бинарник прогоняется против WireMock-Ollama и stdio
MCP-сервера (`--version`, slash-команды, чат через `-p`, агентный ход, `/compact` и
`--resume`, недоступная Ollama):

```bash
./gradlew :harness-bootstrap:test -PincludeTags=native
```

## Запуск на JVM для разработки

Пересобирать образ после каждого изменения не нужно:

```bash
./gradlew :harness-bootstrap:bootJar
java --enable-native-access=ALL-UNNAMED -jar harness-bootstrap/build/libs/harness.jar
```

Старт около 2 с вместо 0.05–0.15 с у бинарника, в остальном поведение то же.

## Частые проблемы

| Симптом | Решение |
|---|---|
| `No matching toolchains found` | Установите JDK 25 / GraalVM 25 или укажите путь через `org.gradle.java.installations.paths` |
| `nativeCompile` падает с нехваткой памяти | Закройте тяжёлые приложения: сборке нужно до 7 ГБ |
| Ошибка NullAway при компиляции | Отметьте поле или параметр `@Nullable` (JSpecify) или уберите возможный `null` |
| PMD: `CognitiveComplexity` / `CyclomaticComplexity` | Разбейте метод на части, порог не повышайте |
| В бинарнике `ClassNotFoundException` или пустые поля после JSON | Класс не зарегистрирован в `NativeHintsConfig` |
