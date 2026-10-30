package dev.localllm.harness.application.port.out;

import java.nio.file.Path;
import java.util.List;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * Registry of the tools the model may call, built-in and external.
 */
public interface ToolExecutorPort {

  /**
   * Lists the tools offered to the model.
   *
   * @return tool descriptions in registration order
   */
  List<ToolSpec> available();

  /**
   * Executes a tool call. Failures of the tool are returned as an error result, never thrown.
   *
   * @param call the call requested by the model
   * @param sessionId session the call belongs to
   * @param workingDirectory directory relative paths are resolved against
   * @return outcome of the call, with the output cut to the configured size
   */
  ToolResult execute(ToolCall call, SessionId sessionId, Path workingDirectory);
}
