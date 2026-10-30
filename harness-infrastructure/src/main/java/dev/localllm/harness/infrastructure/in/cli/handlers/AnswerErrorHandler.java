package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.AnswerNotFoundException;

/**
 * Describes a save request in a session without answers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class AnswerErrorHandler implements CliExceptionHandler {

  @Override
  public boolean supports(Throwable error) {
    return error instanceof AnswerNotFoundException;
  }

  @Override
  public String describe(Throwable error) {
    return "Сохранять нечего: в этой сессии ещё нет ответа модели";
  }
}
