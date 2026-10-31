# ADR-001. Архитектура и платформа Harness

- **Статус:** принято
- **Дата:** 2026-10-29
- **Контекст:** спецификация `docs/java-ai-harness.md`, стандарт «Java / Spring Boot: архитектура и код» v3.0

## Контекст

Harness — локальный CLI-агент поверх Ollama. Бизнес-правил мало (оркестрация чата и агентного
цикла), внешних систем много: Ollama, файловая система, shell, MCP-серверы, файлы сессий.
Нужна подменяемость инфраструктуры (две стратегии вызова инструментов, замена памяти в M2)
и тестирование ядра без Spring.

## Решения

### 1. Архитектурный вариант — B (гексагональная без DDD)

По дереву выбора стандарта (1.2): нетривиальных инвариантов нет, внешних систем много → **B**.

| Модуль | Содержимое | Зависимости |
|---|---|---|
| `harness-application` | `model`, `port/in`, `port/out`, `dto/{command,query,result}`, `mapper`, `service`, `exception` | JDK, `java-uuid-generator`, JSpecify, MapStruct (см. п. 4) |
| `harness-infrastructure` | `in/cli` (+ `mapper`, `handlers`), `out/ollama` (+ `mapper`), `out/memory`, `out/console`, `config` | `harness-application`, Spring Boot, Spring AI, JLine, Picocli |
| `harness-bootstrap` | `HarnessApplication`, `config/BeanConfig`, `application.yml`, `log4j2-spring.xml` | все модули; единственный с `bootJar` |

Базовый пакет — `dev.localllm.harness`. Application-сервисы не помечаются Spring-аннотациями и
связываются с портами в `bootstrap/config/BeanConfig`.

*Обновление:* адаптеры LLM-провайдеров вынесены из `harness-infrastructure` в подмодули
`harness-adapter:ollama` (бывший `out/ollama`, пакет `dev.localllm.harness.adapter.ollama`) и
`harness-adapter:anthropic`; они зависят только от `harness-application` — см.
[ADR-005](ADR-005-llm-adapter-modules.md).

### 2. Платформа

Java 25, Spring Boot **4.1.2**, Spring AI **2.0.2** (собрана против Boot 4.1.2, Framework 7.0.10,
Jackson 3.1.7). Логирование — SLF4J + Log4j2, Logback исключён. Null-safety — JSpecify
`@NullMarked` во всех пакетах + NullAway (Error Prone) как ошибка компиляции.

### 3. Сборка — Gradle Kotlin DSL (ДОПУСТИМО вместо Maven)

Правила стандарта переносятся: `bootJar` только в `harness-bootstrap`; jacoco (общий порог 60 %,
`application` 80 %), checkstyle (2 пробела, 100 колонок, без wildcard-импортов), PMD (цикломатическая
сложность ≤ 25), SpotBugs (fail на High). OWASP dependency-check — в CI, не в локальной сборке.

### 4. MapStruct в модуле `application`

Глобальное правило проекта требует MapStruct с `componentModel = "spring"`, стандарт B — модуль
`application` без Spring. Компромисс: `compileOnly("org.springframework:spring-context")` только
ради `@Component` в сгенерированных `*MapperImpl` (аналогично допуску `@Transactional` как
provided-зависимости, 3.2.4). ArchUnit разрешает `org.springframework..` в `application`
исключительно классам `*MapperImpl`.

### 5. Picocli без `picocli-spring-boot-starter`

Совместимость стартера со Spring Boot 4 не гарантирована. Используется голый `picocli`:
`HarnessCommand` — Spring-бин с внедрением через конструктор, `PicocliRunner`
(`CommandLineRunner` + `ExitCodeGenerator`) передаёт готовый экземпляр в `new CommandLine(...)`.
Собственная `IFactory` понадобится только с появлением подкоманд. *Обновление:* подкоманды
`list` и `ps` подключены без неё, готовыми бинами через `addSubcommand` — см.
[ADR-003](ADR-003-model-listing-commands.md).

### 6. Неприменимые пункты чеклиста 5.1 (локальный CLI, не сервис)

| Пункт | Причина |
|---|---|
| Actuator, Micrometer, tracing | Нет HTTP-сервера и окружения мониторинга; наблюдаемость — файловый лог и `/context` |
| Spring Security | Нет входящих сетевых интерфейсов |
| Flyway, JPA | Нет БД; сессии — JSONL-файлы (M2) |
| Профили `dev/staging/prod` | Одна среда — машина пользователя; используется только профиль `test` |
| Dockerfile, K8s | Дистрибуция — jar + скрипт `bin/harness` |
| JSON-логи, `RequestResponseLoggingFilter` | Локальная среда: pattern layout в файл ДОПУСТИМ |
| REST-коды ошибок, `@RestControllerAdvice` | Нет REST. Сохраняется иерархия `HarnessException(code, message)` с диапазонами стандарта: `300xxx` не найдено, `500xxx` внешние системы, `999999` непредвиденная; обработчики — в `in/cli/handlers/`, по одному на тему (`LlmErrorHandler`, `ModelErrorHandler`, `FallbackErrorHandler`) |

Пользовательский файл `~/.harness/config.yml` лежит вне репозитория и может содержать константы
(аналог `.env`); `application.yml` в репозитории — только `${HARNESS_*:default}`.

### 7. GraalVM native image

Не входит в это решение. По правилу 6.16 — отдельный ADR с замером старта JVM + CDS против native
(этап M6): принято в [ADR-002](ADR-002-native-image.md) — только native image.

## Последствия

- Ядро тестируется на фейках портов без Spring; ArchUnit стережёт границы модулей.
- Цена — больше классов и три модуля (с ADR-005 — ещё модули провайдеров) для небольшого CLI; принято ради подменяемости адаптеров
  (text-json / native tool calling, in-memory / JSONL память).
