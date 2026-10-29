package dev.localllm.harness.application.exception;

import org.jspecify.annotations.Nullable;

/**
 * Root of the harness exception hierarchy.
 *
 * <p>Codes follow the ranges of the coding standard: {@code 300xxx} not found, {@code 500xxx}
 * external systems, {@code 999999} unexpected error.
 */
public class HarnessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * Creates an exception with a code and a user-facing message.
   *
   * @param code six-digit error code
   * @param message user-facing message
   */
  public HarnessException(String code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * Creates an exception with a code, a user-facing message and a cause.
   *
   * @param code six-digit error code
   * @param message user-facing message
   * @param cause underlying failure
   */
  public HarnessException(String code, String message, @Nullable Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /**
   * Six-digit error code.
   *
   * @return error code
   */
  public String getCode() {
    return code;
  }
}
