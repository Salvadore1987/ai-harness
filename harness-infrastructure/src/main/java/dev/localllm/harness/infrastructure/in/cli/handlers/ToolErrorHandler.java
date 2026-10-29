package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.AgentLimitExceededException;

/**
 * Describes the agent loop stopping at one of its limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class ToolErrorHandler implements CliExceptionHandler {

  @Override
  public boolean supports(Throwable error) {
    return error instanceof AgentLimitExceededException;
  }

  @Override
  public String describe(Throwable error) {
    AgentLimitExceededException limit = (AgentLimitExceededException) error;
    return switch (limit.getLimit()) {
      case ITERATIONS -> "Агент остановлен: " + limit.getValue()
          + " запросов к модели без финального ответа (harness.agent.max-iterations). "
          + "Результаты инструментов сохранены — напишите «продолжай», чтобы продолжить";
      case PARSE_FAILURES -> "Агент остановлен: модель " + limit.getValue()
          + " раза подряд вызвала несуществующие инструменты (harness.agent.max-parse-failures)."
          + " Список инструментов: /tools";
    };
  }
}
