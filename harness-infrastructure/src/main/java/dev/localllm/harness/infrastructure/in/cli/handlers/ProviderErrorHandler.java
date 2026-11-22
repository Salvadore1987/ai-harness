package dev.localllm.harness.infrastructure.in.cli.handlers;

import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.ProviderUnavailableException;
import dev.localllm.harness.application.model.LlmProvider;

/**
 * Describes a provider that cannot be used in this run and what makes it available.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ProviderErrorHandler implements CliExceptionHandler {

  @Override
  public boolean supports(Throwable error) {
    return error instanceof ProviderUnavailableException;
  }

  @Override
  public String describe(Throwable error) {
    LlmProvider provider = ((ProviderUnavailableException) error).getProvider();
    String name = provider.name().toLowerCase(Locale.ROOT);
    return switch (provider) {
      case ANTHROPIC -> "Провайдер " + name + " недоступен: задайте ANTHROPIC_API_KEY";
      case OLLAMA -> "Провайдер " + name + " недоступен";
    };
  }
}
