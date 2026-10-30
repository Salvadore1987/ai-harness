package dev.localllm.harness.application.exception;

/**
 * The session has no answer that could be saved.
 */
public class AnswerNotFoundException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "300004";

  /**
   * Creates the exception.
   */
  public AnswerNotFoundException() {
    super(CODE, "There is no answer to save in this session yet");
  }
}
