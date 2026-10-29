package dev.localllm.harness.infrastructure.out.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP tool whose result reaches the model as plain text.
 *
 * <p>Spring AI returns the MCP content list as JSON, {@code [{"text":"..."}]}, without the
 * {@code type} of the items. A list of text items becomes their texts separated by blank lines,
 * which is shorter for the model and readable in the console; a list with images, resources or
 * anything else is passed through unchanged.
 *
 * <p>An error result of the tool fails the call with the {@code toString()} of the SDK content
 * records; the texts are taken out of it the same way.
 */
public class McpTextToolCallback implements ToolCallback {

  private static final JsonMapper JSON = JsonMapper.shared();
  private static final String ERROR_PREFIX = "Error calling tool: ";
  private static final Pattern ERROR_TEXT =
      Pattern.compile("TextContent\\[annotations=[^,]*, text=(.*?), meta=[^\\]]*]", Pattern.DOTALL);

  private final ToolCallback delegate;

  /**
   * Wraps an MCP tool callback.
   *
   * @param delegate callback created by Spring AI
   */
  public McpTextToolCallback(ToolCallback delegate) {
    this.delegate = delegate;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  @Override
  public String call(String toolInput) {
    return call(toolInput, null);
  }

  @Override
  public String call(String toolInput, @Nullable ToolContext toolContext) {
    try {
      return toText(delegate.call(toolInput, toolContext));
    } catch (ToolExecutionException e) {
      Throwable cause = e.getCause();
      String message = cause == null ? null : cause.getMessage();
      if (message == null || !message.startsWith(ERROR_PREFIX)) {
        throw e;
      }
      throw new ToolExecutionException(getToolDefinition(),
          new IllegalStateException(errorText(message.substring(ERROR_PREFIX.length()))));
    }
  }

  /**
   * Texts of the content records in an error message.
   *
   * @param content {@code toString()} of the content list
   * @return the texts, or the content unchanged if it has none
   */
  static String errorText(String content) {
    List<String> texts = new ArrayList<>();
    Matcher matcher = ERROR_TEXT.matcher(content);
    while (matcher.find()) {
      texts.add(matcher.group(1));
    }
    return texts.isEmpty() ? content : String.join("\n\n", texts);
  }

  /**
   * Joins the texts of an MCP content list.
   *
   * @param content JSON written by Spring AI
   * @return the texts, or the content unchanged if it is not a list of text items
   */
  static String toText(String content) {
    JsonNode items;
    try {
      items = JSON.readTree(content);
    } catch (JacksonException e) {
      return content;
    }
    if (!items.isArray() || items.isEmpty()) {
      return content;
    }
    List<String> texts = new ArrayList<>();
    for (JsonNode item : items) {
      JsonNode text = item.get("text");
      if (text == null || !text.isString() || !"text".equals(item.path("type").asString("text"))) {
        return content;
      }
      texts.add(text.asString());
    }
    return String.join("\n\n", texts);
  }
}
