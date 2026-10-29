package dev.localllm.harness.infrastructure.out.tools;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.application.port.out.ToolExecutorPort;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

/**
 * Registry of all Spring AI tool callbacks: the built-in {@code @Tool} methods and, later, MCP
 * tools. Both tool call modes execute calls here, so tools are never duplicated.
 *
 * <p>The working directory reaches the tools through the {@link ToolContext} under
 * {@link #WORKING_DIRECTORY}. Failures become error results with the message of the cause; the
 * output is cut to {@code harness.tools.max-output-chars}.
 */
@Component
public class SpringAiToolExecutorAdapter implements ToolExecutorPort {

  /** Tool context key of the working directory, a {@link Path}. */
  public static final String WORKING_DIRECTORY = "workingDirectory";

  private static final Logger log = LoggerFactory.getLogger(SpringAiToolExecutorAdapter.class);
  private static final String ERROR_PREFIX = "Error: ";

  private final Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
  private final List<ToolSpec> specs = new ArrayList<>();
  private final int maxOutputChars;

  /**
   * Creates the registry; a later provider cannot replace a tool name that is already taken.
   *
   * @param providers tool callback providers in registration order
   * @param mapper converts tool definitions into core descriptions
   * @param properties harness settings with the output limit
   */
  public SpringAiToolExecutorAdapter(
      List<ToolCallbackProvider> providers,
      ToolSpecSpringAiMapper mapper,
      HarnessProperties properties) {
    this.maxOutputChars = properties.tools().maxOutputChars();
    for (ToolCallbackProvider provider : providers) {
      ToolSource source = provider instanceof MethodToolCallbackProvider
          ? ToolSource.BUILTIN : ToolSource.MCP;
      for (ToolCallback callback : provider.getToolCallbacks()) {
        String name = callback.getToolDefinition().name();
        if (callbacks.putIfAbsent(name, callback) == null) {
          specs.add(mapper.toToolSpec(callback.getToolDefinition(), source));
        } else {
          log.error("Tool {} is registered twice; the second registration is ignored", name);
        }
      }
    }
  }

  @Override
  public List<ToolSpec> available() {
    return List.copyOf(specs);
  }

  @Override
  public ToolResult execute(ToolCall call, Path workingDirectory) {
    ToolCallback callback = callbacks.get(call.name());
    if (callback == null) {
      return ToolResult.rejected(call, ERROR_PREFIX + "unknown tool " + call.name());
    }
    long startedAt = System.nanoTime();
    String output;
    boolean error = false;
    try {
      output = callback.call(call.argumentsJson(),
          new ToolContext(Map.of(WORKING_DIRECTORY, workingDirectory)));
    } catch (ToolExecutionException e) {
      error = true;
      output = ERROR_PREFIX + describe(e.getCause() == null ? e : e.getCause());
    } catch (RuntimeException e) {
      error = true;
      output = ERROR_PREFIX + describe(e);
    }
    Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
    log.info("Tool {} finished in {} ms{}", call.name(), duration.toMillis(),
        error ? " with an error: " + output : "");
    boolean truncated = output.length() > maxOutputChars;
    if (truncated) {
      output = output.substring(0, maxOutputChars) + "\n[output truncated to "
          + maxOutputChars + " of " + output.length() + " characters]";
    }
    return new ToolResult(call.id(), call.name(), output, error, truncated, duration);
  }

  private static String describe(Throwable error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }
}
