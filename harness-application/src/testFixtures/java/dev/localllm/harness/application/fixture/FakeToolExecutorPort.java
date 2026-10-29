package dev.localllm.harness.application.fixture;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.application.port.out.ToolExecutorPort;

/**
 * Tool registry with fixed tools whose output echoes the call; records every execution.
 */
public final class FakeToolExecutorPort implements ToolExecutorPort {

  private final List<ToolSpec> tools = new ArrayList<>();
  private final List<ToolCall> executed = new ArrayList<>();
  private final List<Path> workingDirectories = new ArrayList<>();
  private Runnable onExecute = () -> { };

  /**
   * Creates the registry with built-in tools of the given names.
   *
   * @param names tool names
   */
  public FakeToolExecutorPort(String... names) {
    for (String name : names) {
      tools.add(new ToolSpec(
          name, "Fake " + name, "{\"type\":\"object\"}", ToolSource.BUILTIN, null));
    }
  }

  /**
   * Adds a tool of an MCP server.
   *
   * @param name tool name
   * @param server server name
   * @return this fake
   */
  public FakeToolExecutorPort withMcpTool(String name, String server) {
    tools.add(new ToolSpec(name, "Fake " + name, "{\"type\":\"object\"}", ToolSource.MCP, server));
    return this;
  }

  /**
   * Runs an action whenever a tool is executed, for example a cancellation.
   *
   * @param action action to run before the result is returned
   * @return this fake
   */
  public FakeToolExecutorPort onExecute(Runnable action) {
    this.onExecute = action;
    return this;
  }

  @Override
  public List<ToolSpec> available() {
    return List.copyOf(tools);
  }

  @Override
  public ToolResult execute(ToolCall call, Path workingDirectory) {
    executed.add(call);
    workingDirectories.add(workingDirectory);
    onExecute.run();
    return new ToolResult(call.id(), call.name(), call.name() + " " + call.argumentsJson(),
        false, false, Duration.ofMillis(5));
  }

  /**
   * Calls executed so far, oldest first.
   *
   * @return executed calls
   */
  public List<ToolCall> executed() {
    return List.copyOf(executed);
  }

  /**
   * Working directories passed with the executed calls.
   *
   * @return working directories
   */
  public List<Path> workingDirectories() {
    return List.copyOf(workingDirectories);
  }
}
