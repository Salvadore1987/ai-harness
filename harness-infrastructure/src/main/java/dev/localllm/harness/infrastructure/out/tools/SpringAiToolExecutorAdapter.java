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
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.application.port.out.ToolExecutorPort;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.mcp.McpConnector;
import dev.localllm.harness.infrastructure.out.mcp.McpServerTools;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

/**
 * Registry of all Spring AI tool callbacks: the built-in {@code @Tool} methods and the tools of
 * the connected MCP servers. Both tool call modes execute calls here, so tools are never
 * duplicated.
 *
 * <p>The MCP tools join the registry when it is first used, which waits for the servers that
 * connect in the background; built-in tools come first and keep their names. The working
 * directory reaches the tools through the {@link ToolContext} under {@link #WORKING_DIRECTORY}.
 * Failures become error results with the message of the cause; the output is cut to
 * {@code harness.tools.max-output-chars}.
 */
@Component
public class SpringAiToolExecutorAdapter implements ToolExecutorPort {

  /** Tool context key of the working directory, a {@link Path}. */
  public static final String WORKING_DIRECTORY = "workingDirectory";

  private static final Logger log = LoggerFactory.getLogger(SpringAiToolExecutorAdapter.class);
  private static final String ERROR_PREFIX = "Error: ";

  private final SingletonSupplier<Registry> registry;
  private final int maxOutputChars;

  /**
   * Creates the registry; a later tool cannot replace a tool name that is already taken.
   *
   * @param providers built-in tool callback providers in registration order
   * @param mcpConnector tools of the MCP servers
   * @param mapper converts tool definitions into core descriptions
   * @param properties harness settings with the output limit
   */
  public SpringAiToolExecutorAdapter(
      List<ToolCallbackProvider> providers,
      McpConnector mcpConnector,
      ToolSpecSpringAiMapper mapper,
      HarnessProperties properties) {
    this.maxOutputChars = properties.tools().maxOutputChars();
    this.registry = SingletonSupplier.of(() -> {
      Registry tools = new Registry(new LinkedHashMap<>(), new ArrayList<>());
      for (ToolCallbackProvider provider : providers) {
        for (ToolCallback callback : provider.getToolCallbacks()) {
          tools.add(callback,
              mapper.toToolSpec(callback.getToolDefinition(), ToolSource.BUILTIN, null));
        }
      }
      for (McpServerTools server : mcpConnector.servers()) {
        for (ToolCallback callback : server.callbacks()) {
          tools.add(callback,
              mapper.toToolSpec(callback.getToolDefinition(), ToolSource.MCP, server.server()));
        }
      }
      return tools;
    });
  }

  @Override
  public List<ToolSpec> available() {
    return List.copyOf(registry.obtain().specs());
  }

  @Override
  public ToolResult execute(ToolCall call, Path workingDirectory) {
    ToolCallback callback = registry.obtain().callbacks().get(call.name());
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

  /**
   * Tools by name and their descriptions in registration order.
   *
   * @param callbacks callbacks by tool name
   * @param specs tool descriptions
   */
  private record Registry(Map<String, ToolCallback> callbacks, List<ToolSpec> specs) {

    void add(ToolCallback callback, ToolSpec spec) {
      if (callbacks.putIfAbsent(spec.name(), callback) == null) {
        specs.add(spec);
      } else {
        log.error("Tool {} is registered twice; the second registration is ignored", spec.name());
      }
    }
  }
}
