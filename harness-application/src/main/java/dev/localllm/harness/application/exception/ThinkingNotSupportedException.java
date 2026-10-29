package dev.localllm.harness.application.exception;

/**
 * Thinking was requested for a model that cannot reason before answering.
 */
public class ThinkingNotSupportedException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "300002";

  /**
   * Creates the exception.
   *
   * @param model name of the model without thinking support
   */
  public ThinkingNotSupportedException(String model) {
    super(CODE, "Model " + model + " does not support thinking");
  }
}
