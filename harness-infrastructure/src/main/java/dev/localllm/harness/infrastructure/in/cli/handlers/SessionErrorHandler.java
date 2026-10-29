package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.SessionNotFoundException;

/**
 * Describes failures to find a stored session.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SessionErrorHandler implements CliExceptionHandler {

  @Override
  public boolean supports(Throwable error) {
    return error instanceof SessionNotFoundException;
  }

  @Override
  public String describe(Throwable error) {
    return "Сессия не найдена: " + error.getMessage() + ". Список сессий: /sessions";
  }
}
