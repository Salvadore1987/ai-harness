package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.application.exception.ThinkingNotSupportedException;

/**
 * Describes failures caused by the selected model: missing or lacking a capability.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ModelErrorHandler implements CliExceptionHandler {

  @Override
  public boolean supports(Throwable error) {
    return error instanceof ModelNotFoundException
        || error instanceof ThinkingNotSupportedException;
  }

  @Override
  public String describe(Throwable error) {
    if (error instanceof ModelNotFoundException notFound) {
      return "Модель " + notFound.getModel() + " не найдена. Выполните: ollama pull "
          + notFound.getModel();
    }
    return "Режим размышлений недоступен: " + error.getMessage();
  }
}
