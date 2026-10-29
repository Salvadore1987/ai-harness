package dev.localllm.harness.application.exception;

/**
 * The agent loop hit one of its limits and stopped before the model answered.
 */
public class AgentLimitExceededException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "200001";

  private final Limit limit;
  private final int value;

  /**
   * Creates the exception.
   *
   * @param limit the limit that was hit
   * @param value configured value of the limit
   */
  public AgentLimitExceededException(Limit limit, int value) {
    super(CODE, switch (limit) {
      case ITERATIONS -> "The agent made " + value + " model requests without a final answer";
      case PARSE_FAILURES -> "The model called unknown tools " + value + " times in a row";
    });
    this.limit = limit;
    this.value = value;
  }

  /**
   * The limit that was hit.
   *
   * @return limit
   */
  public Limit getLimit() {
    return limit;
  }

  /**
   * Configured value of the limit.
   *
   * @return limit value
   */
  public int getValue() {
    return value;
  }

  /**
   * Limits of the agent loop.
   */
  public enum Limit {
    /** {@code harness.agent.max-iterations}. */
    ITERATIONS,
    /** {@code harness.agent.max-parse-failures}. */
    PARSE_FAILURES
  }
}
