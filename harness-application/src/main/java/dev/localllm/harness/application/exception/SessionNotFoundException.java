package dev.localllm.harness.application.exception;

/**
 * No stored session matches the requested identifier.
 */
public class SessionNotFoundException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "300003";

  /**
   * Creates the exception.
   *
   * @param reference identifier or prefix that was requested; empty for "latest"
   */
  public SessionNotFoundException(String reference) {
    super(CODE, reference.isEmpty()
        ? "No stored session in this directory"
        : "No stored session matches " + reference);
  }
}
