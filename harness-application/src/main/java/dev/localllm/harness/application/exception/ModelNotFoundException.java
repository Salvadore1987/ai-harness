package dev.localllm.harness.application.exception;

/**
 * The requested model is not installed on the local model server.
 */
public class ModelNotFoundException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "300001";

  private final String model;

  /**
   * Creates the exception.
   *
   * @param model name of the missing model
   */
  public ModelNotFoundException(String model) {
    super(CODE, "Model " + model + " is not installed");
    this.model = model;
  }

  /**
   * Name of the missing model.
   *
   * @return model name
   */
  public String getModel() {
    return model;
  }
}
