package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.LlmResponseException;
import dev.localllm.harness.application.exception.LlmUnavailableException;

/**
 * Describes failures of the model server: unreachable or answering with an error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LlmErrorHandler implements CliExceptionHandler {

  @Override
  public boolean supports(Throwable error) {
    return error instanceof LlmUnavailableException || error instanceof LlmResponseException;
  }

  @Override
  public String describe(Throwable error) {
    if (error instanceof LlmUnavailableException unavailable) {
      return "Ollama недоступна на " + unavailable.getBaseUrl() + ". Запустите: ollama serve";
    }
    String message = String.valueOf(error.getMessage());
    if (message.contains("does not support tools")) {
      return "Модель не поддерживает инструменты: " + message
          + ". Переключитесь на /mode chat, выберите модель с пометкой tools (/model) или "
          + "включите для неё текстовый протокол: harness.tool-call-modes в config.yml";
    }
    return "Ошибка Ollama: " + message;
  }
}
