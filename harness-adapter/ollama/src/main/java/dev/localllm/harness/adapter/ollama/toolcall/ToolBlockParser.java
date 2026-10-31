package dev.localllm.harness.adapter.ollama.toolcall;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * Reads the tool call a model wrote as a block at the end of its answer:
 *
 * <pre>
 * ```tool
 * {"name": "read_file", "args": {"path": "src/App.java"}}
 * ```
 * </pre>
 *
 * <p>The JSON object is read with a streaming parser rather than up to the next fence, so
 * strings may contain braces and fences, for example the content of {@code write_file}. Only the
 * first block counts; text after it is dropped. The call is checked against the input schema
 * of the tool: the tool must exist, required arguments must be present and unknown ones absent.
 */
@Component
public class ToolBlockParser {

  /** Line that opens a tool block. */
  public static final String OPENING_FENCE = "```tool";

  private static final String CLOSING_FENCE = "```";
  private static final String NAME = "name";
  private static final String ARGS = "args";

  // Text after the JSON object is expected: the closing fence and whatever the model added.
  private final JsonMapper jsonMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .build();

  /**
   * Reads the first tool block of an answer.
   *
   * @param content full answer text
   * @param tools tools offered to the model
   * @return the answer without the block and the call, or the reason the block is invalid
   */
  public ToolBlock parse(String content, List<ToolSpec> tools) {
    int opening = content.indexOf(OPENING_FENCE);
    if (opening < 0) {
      return new ToolBlock(content, null, null);
    }
    try {
      JsonNode node = readObject(content, opening)
          .orElseThrow(() -> new InvalidBlockException(
              "the tool block must contain one JSON object"))
          .node();
      ToolCall call = toCall(node, tools);
      return new ToolBlock(content.substring(0, opening).stripTrailing(), call, null);
    } catch (InvalidBlockException e) {
      return new ToolBlock(content, null, e.getMessage());
    }
  }

  /**
   * Whether the answer already contains a complete block: a JSON object followed by the closing
   * fence. Anything the model writes after that is dropped, so the stream can be stopped.
   *
   * @param content answer text streamed so far
   * @return {@code true} if a block is complete
   */
  public boolean isClosed(String content) {
    int opening = content.indexOf(OPENING_FENCE);
    if (opening < 0 || content.indexOf(CLOSING_FENCE, opening + OPENING_FENCE.length()) < 0) {
      return false;
    }
    try {
      return readObject(content, opening)
          .map(json -> content.substring(json.end()).stripLeading().startsWith(CLOSING_FENCE))
          .orElse(false);
    } catch (InvalidBlockException e) {
      return false;
    }
  }

  private Optional<JsonObject> readObject(String content, int opening) {
    int start = opening + OPENING_FENCE.length();
    int brace = content.indexOf('{', start);
    if (brace < 0 || !content.substring(start, brace).isBlank()) {
      return Optional.empty();
    }
    try (JsonParser parser = jsonMapper.createParser(content.substring(brace))) {
      JsonNode node = jsonMapper.readTree(parser);
      int end = brace + (int) parser.currentLocation().getCharOffset();
      return Optional.of(new JsonObject(node, end));
    } catch (JacksonException e) {
      throw new InvalidBlockException("invalid JSON in the tool block: " + e.getOriginalMessage());
    }
  }

  private ToolCall toCall(JsonNode node, List<ToolSpec> tools) {
    if (!node.path(NAME).isString() || node.path(NAME).asString().isBlank()) {
      throw new InvalidBlockException("the tool block has no \"name\"");
    }
    String name = node.path(NAME).asString();
    ToolSpec tool = tools.stream().filter(spec -> spec.name().equals(name)).findFirst()
        .orElseThrow(() -> new InvalidBlockException("unknown tool " + name
            + "; available tools: " + tools.stream().map(ToolSpec::name).sorted()
            .collect(Collectors.joining(", "))));
    JsonNode args = node.path(ARGS);
    if (args.isMissingNode() || args.isNull()) {
      args = jsonMapper.createObjectNode();
    }
    if (!(args instanceof ObjectNode arguments)) {
      throw new InvalidBlockException("\"args\" of " + name + " must be a JSON object");
    }
    checkSchema(tool, arguments);
    return new ToolCall(ToolCall.newId(), name, arguments.toString());
  }

  private void checkSchema(ToolSpec tool, ObjectNode arguments) {
    JsonNode schema;
    try {
      schema = jsonMapper.readTree(tool.inputSchema());
    } catch (JacksonException e) {
      return;
    }
    checkRequired(tool, schema, arguments);
    checkKnown(tool, schema, arguments);
  }

  private static void checkRequired(ToolSpec tool, JsonNode schema, ObjectNode arguments) {
    for (JsonNode required : schema.path("required")) {
      JsonNode value = arguments.get(required.asString());
      if (value == null || value.isNull()) {
        throw new InvalidBlockException("missing required argument \"" + required.asString()
            + "\" of " + tool.name());
      }
    }
  }

  private static void checkKnown(ToolSpec tool, JsonNode schema, ObjectNode arguments) {
    JsonNode properties = schema.path("properties");
    if (!properties.isObject()) {
      return;
    }
    Set<String> known = new HashSet<>(properties.propertyNames());
    for (String argument : arguments.propertyNames()) {
      if (!known.contains(argument)) {
        throw new InvalidBlockException("unknown argument \"" + argument + "\" of "
            + tool.name() + "; arguments: " + String.join(", ", properties.propertyNames()));
      }
    }
  }

  private record JsonObject(JsonNode node, int end) {
  }

  /**
   * A block that cannot be turned into a call; the message is sent back to the model.
   */
  private static final class InvalidBlockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private InvalidBlockException(String message) {
      super(message);
    }
  }
}
