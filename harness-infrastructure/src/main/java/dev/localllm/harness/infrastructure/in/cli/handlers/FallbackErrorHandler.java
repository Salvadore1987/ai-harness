package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Describes any failure no other handler recognised, with the unexpected-error code.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FallbackErrorHandler implements CliExceptionHandler {

  /** Code of an unexpected error. */
  public static final String UNEXPECTED_CODE = "999999";

  @Override
  public boolean supports(Throwable error) {
    return true;
  }

  @Override
  public String describe(Throwable error) {
    return "Непредвиденная ошибка [" + UNEXPECTED_CODE + "]: " + error.getMessage()
        + ". Подробности в ~/.harness/logs/harness.log";
  }
}
