package dev.localllm.harness.adapter.anthropic.toolcall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSpec;

/**
 * Collects the {@code tool_use} blocks of one streamed answer: identifier and name from
 * {@code content_block_start}, input from the {@code input_json_delta} fragments.
 *
 * <p>The input is parsed strictly once the answer is complete. If one call of the answer cannot
 * be read or does not match its schema, no call is returned: the model is told what was wrong and
 * repeats the calls, instead of the harness running part of them.
 */
public final class ToolUseCollector {

  private static final String NO_INPUT = "{}";

  private final Map<String, ToolSpec> tools = new LinkedHashMap<>();
  private final Map<Long, Block> blocks = new LinkedHashMap<>();
  private final ToolInputValidator validator;

  /**
   * Creates a collector for one answer.
   *
   * @param tools tools offered in the request
   * @param validator checks the input against the schema
   */
  public ToolUseCollector(List<ToolSpec> tools, ToolInputValidator validator) {
    tools.forEach(tool -> this.tools.put(tool.name(), tool));
    this.validator = validator;
  }

  /**
   * Registers a {@code tool_use} block.
   *
   * @param index position of the block in the answer
   * @param id identifier the result must reference
   * @param name tool name
   */
  public void start(long index, String id, String name) {
    blocks.put(index, new Block(id, name, new StringBuilder()));
  }

  /**
   * Appends a fragment of the input; fragments of other blocks are ignored.
   *
   * @param index position of the block in the answer
   * @param partialJson next fragment of the input JSON
   */
  public void append(long index, String partialJson) {
    Block block = blocks.get(index);
    if (block != null) {
      block.input().append(partialJson);
    }
  }

  /**
   * Whether the answer contains a {@code tool_use} block.
   *
   * @return {@code true} once a block was started
   */
  public boolean hasCalls() {
    return !blocks.isEmpty();
  }

  /**
   * Reads and checks every collected call.
   *
   * @return the calls, or none with the reason if one of them is invalid
   */
  public CollectedToolCalls finish() {
    List<ToolCall> calls = new ArrayList<>(blocks.size());
    for (Block block : blocks.values()) {
      String input = block.input().isEmpty() ? NO_INPUT : block.input().toString();
      Optional<String> problem = findProblem(block.name(), input);
      if (problem.isPresent()) {
        return new CollectedToolCalls(List.of(),
            "the input of tool " + block.name() + " is invalid: " + problem.get());
      }
      calls.add(new ToolCall(block.id(), block.name(), input));
    }
    return new CollectedToolCalls(calls, null);
  }

  private Optional<String> findProblem(String name, String input) {
    JsonNode parsed;
    try {
      parsed = JsonMapper.shared().readTree(input);
    } catch (JacksonException e) {
      return Optional.of("not valid JSON (" + e.getOriginalMessage() + ")");
    }
    if (parsed == null || !parsed.isObject()) {
      return Optional.of("the input is not a JSON object");
    }
    ToolSpec tool = tools.get(name);
    // An unknown tool is answered by the agent loop, which lists the available ones.
    return tool == null ? Optional.empty() : validator.findProblem(tool, parsed);
  }

  private record Block(String id, String name, StringBuilder input) {
  }
}
