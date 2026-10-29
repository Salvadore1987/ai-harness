package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatMessageSpringAiMapper;

/**
 * Tool calling for models without native tools: the tools and the call format are described in
 * the system prompt, the model writes one {@code ```tool} block at the end of its answer.
 *
 * <p>The history keeps calls structured, so it is rendered as text on every request: an
 * assistant message gets its call back as a block, a tool result becomes a user message
 * {@code TOOL_RESULT <tool>:} with the output. A call that could not be read stays in the
 * assistant text as the model wrote it, and its result is the {@code TOOL_ERROR} message.
 *
 * <p>The block is hidden from the user while the answer streams. As soon as it is closed, the
 * stream is stopped: whatever the model writes after it, typically an invented result, is
 * dropped. A stop sequence of Ollama is not used for this, because the closing fence also ends
 * ordinary code blocks and the marker could appear in the reasoning.
 */
@Component
public class TextJsonToolCallMode implements ToolCallMode {

  private static final String TOOL_RESULT_PREFIX = "TOOL_RESULT ";
  private static final String PROMPT_HEADER = """


      # Tools
      You can call tools. To call a tool, end your message with exactly one block in this \
      format and write nothing after it:
      ```tool
      {"name": "<tool name>", "args": {<arguments as JSON>}}
      ```
      Call at most one tool per message. The result comes back in the next message, which \
      starts with "TOOL_RESULT <tool name>:". Never write TOOL_RESULT yourself. When you have \
      enough information, answer without a tool block.

      Available tools:
      """;

  private final ChatMessageSpringAiMapper messageMapper;
  private final ToolBlockParser parser;
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  /**
   * Creates the mode.
   *
   * @param messageMapper converts plain messages into Spring AI messages
   * @param parser reads the tool block of an answer
   */
  public TextJsonToolCallMode(ChatMessageSpringAiMapper messageMapper, ToolBlockParser parser) {
    this.messageMapper = messageMapper;
    this.parser = parser;
  }

  @Override
  public List<Message> toMessages(ChatRequest request) {
    String toolPrompt = toolPrompt(request.tools());
    List<Message> messages = new ArrayList<>(request.messages().size() + 1);
    boolean promptAdded = toolPrompt.isEmpty();
    for (ChatMessage message : request.messages()) {
      switch (message.role()) {
        case SYSTEM -> {
          messages.add(new SystemMessage(
              promptAdded ? message.content() : message.content() + toolPrompt));
          promptAdded = true;
        }
        case ASSISTANT -> messages.add(
            AssistantMessage.builder().content(withBlocks(message)).build());
        case TOOL_RESULT -> messages.add(new UserMessage(toolResult(message)));
        case USER -> messages.add(messageMapper.toSpringAiMessage(message));
      }
    }
    if (!promptAdded) {
      messages.addFirst(new SystemMessage(toolPrompt.strip()));
    }
    return messages;
  }

  @Override
  public List<ToolCallback> toolCallbacks(List<ToolSpec> tools) {
    return List.of();
  }

  @Override
  public ToolCallCollector newCollector(List<ToolSpec> tools) {
    return new TextCollector(tools);
  }

  private String toolPrompt(List<ToolSpec> tools) {
    if (tools.isEmpty()) {
      return "";
    }
    StringBuilder prompt = new StringBuilder(PROMPT_HEADER);
    for (ToolSpec tool : tools) {
      prompt.append("- ").append(tool.name()).append(": ")
          .append(tool.description().strip().replace('\n', ' ')).append('\n')
          .append("  arguments (JSON Schema): ").append(compact(tool.inputSchema()))
          .append('\n');
    }
    return prompt.toString();
  }

  private String withBlocks(ChatMessage message) {
    StringBuilder text = new StringBuilder(message.content());
    for (ToolCall call : message.toolCalls()) {
      if (ToolCall.PARSE_ERROR.equals(call.name())) {
        continue;
      }
      if (!text.isEmpty()) {
        text.append("\n\n");
      }
      ObjectNode block = jsonMapper.createObjectNode();
      block.put("name", call.name());
      block.set("args", arguments(call.argumentsJson()));
      text.append(ToolBlockParser.OPENING_FENCE).append('\n').append(block).append("\n```");
    }
    return text.toString();
  }

  private static String toolResult(ChatMessage message) {
    if (ToolCall.PARSE_ERROR.equals(message.toolName())) {
      return message.content();
    }
    return TOOL_RESULT_PREFIX + message.toolName() + ":\n" + message.content();
  }

  private JsonNode arguments(String argumentsJson) {
    try {
      JsonNode node = jsonMapper.readTree(argumentsJson);
      return node.isObject() ? node : jsonMapper.createObjectNode();
    } catch (JacksonException e) {
      return jsonMapper.createObjectNode();
    }
  }

  private String compact(String schema) {
    try {
      return jsonMapper.readTree(schema).toString();
    } catch (JacksonException e) {
      return schema.replace('\n', ' ');
    }
  }

  /**
   * Hides the tool block while the answer streams and reads it when the answer is complete.
   * Without offered tools the answer is passed through unchanged.
   */
  private final class TextCollector implements ToolCallCollector {

    private final List<ToolSpec> tools;
    private final StringBuilder content = new StringBuilder();
    private int shown;
    private boolean hidden;
    private boolean complete;

    private TextCollector(List<ToolSpec> tools) {
      this.tools = tools;
    }

    @Override
    public String visible(String fragment) {
      if (tools.isEmpty()) {
        return fragment;
      }
      content.append(fragment);
      if (!hidden) {
        int opening = content.indexOf(ToolBlockParser.OPENING_FENCE, shown);
        int end = opening >= 0 ? opening : content.length() - heldBack();
        String text = content.substring(shown, end);
        shown = end;
        hidden = opening >= 0;
        if (!hidden) {
          return text;
        }
        complete = parser.isClosed(content.toString());
        return text;
      }
      if (fragment.indexOf('`') >= 0) {
        complete = parser.isClosed(content.toString());
      }
      return "";
    }

    @Override
    public boolean accept(ChatResponse response) {
      return false;
    }

    @Override
    public boolean isComplete() {
      return complete;
    }

    @Override
    public String flush() {
      if (hidden) {
        return "";
      }
      String text = content.substring(shown);
      shown = content.length();
      return text;
    }

    @Override
    public CollectedToolCalls finish(String answer) {
      if (tools.isEmpty()) {
        return new CollectedToolCalls(answer, List.of(), null);
      }
      ToolBlock block = parser.parse(answer, tools);
      ToolCall call = block.call();
      return new CollectedToolCalls(block.text(), call == null ? List.of() : List.of(call),
          block.error());
    }

    /**
     * Length of the longest end of the text that may be the start of an opening fence.
     */
    private int heldBack() {
      String fence = ToolBlockParser.OPENING_FENCE;
      for (int length = Math.min(fence.length() - 1, content.length() - shown); length > 0;
          length--) {
        if (fence.startsWith(content.substring(content.length() - length))) {
          return length;
        }
      }
      return 0;
    }
  }
}
