package dev.localllm.harness.application.exception;

/**
 * The model server rejected a request or returned an unexpected error.
 */
public class LlmResponseException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "500002";

  /**
   * Creates the exception.
   *
   * @param message description returned by the server
   * @param cause underlying failure
   */
  public LlmResponseException(String message, Throwable cause) {
    super(CODE, message, cause);
  }
}
