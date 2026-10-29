package dev.localllm.harness.infrastructure.out.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;

/**
 * Formats tool calls and results compactly: {@code read_file(src/Main.java)} and the first lines
 * of the output.
 */
final class ToolCallFormatter {

  static final int PREVIEW_LINES = 3;
  static final int DIFF_PREVIEW_LINES = 40;
  static final String EDIT_TOOL = "edit_file";
  private static final int MAX_ARGUMENTS_LENGTH = 100;
  private static final int MAX_LINE_LENGTH = 160;
  private static final String ELLIPSIS = "…";
  private static final String TAB_REPLACEMENT = "  ";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  /**
   * Formats a call as the tool name with its argument values.
   *
   * @param call the call
   * @return for example {@code grep(TODO, src)}
   */
  String call(ToolCall call) {
    return call.name() + "(" + shorten(arguments(call.argumentsJson()), MAX_ARGUMENTS_LENGTH)
        + ")";
  }

  /**
   * Selects the output lines to show.
   *
   * @param result outcome of the call
   * @param verbose whether all lines are shown
   * @return lines to print, each shortened; the last one tells how many lines were hidden
   */
  List<String> outputLines(ToolResult result, boolean verbose) {
    String output = result.output().stripTrailing();
    if (output.isBlank()) {
      return List.of("(пустой вывод)");
    }
    List<String> all = output.lines().map(line -> line.replace("\t", TAB_REPLACEMENT)).toList();
    if (verbose) {
      return all;
    }
    int limit = isDiff(result) ? DIFF_PREVIEW_LINES : PREVIEW_LINES;
    List<String> shown = new ArrayList<>(limit + 1);
    for (int i = 0; i < Math.min(limit, all.size()); i++) {
      shown.add(shorten(all.get(i), MAX_LINE_LENGTH));
    }
    if (all.size() > limit) {
      shown.add(ELLIPSIS + " ещё " + (all.size() - limit) + " строк (--verbose — полностью)");
    }
    return shown;
  }

  /**
   * Whether the output is the diff of a successful {@code edit_file}, shown in full and colored.
   *
   * @param result outcome of the call
   * @return {@code true} for a diff
   */
  static boolean isDiff(ToolResult result) {
    return !result.error() && EDIT_TOOL.equals(result.toolName());
  }

  private String arguments(String argumentsJson) {
    JsonNode node;
    try {
      node = jsonMapper.readTree(argumentsJson);
    } catch (JacksonException e) {
      return argumentsJson;
    }
    if (!node.isObject()) {
      return argumentsJson;
    }
    return node.properties().stream()
        .map(Map.Entry::getValue)
        .filter(value -> !value.isNull())
        .map(value -> value.isString() ? value.asString() : value.toString())
        .map(value -> value.replace('\n', ' '))
        .collect(Collectors.joining(", "));
  }

  private static String shorten(String text, int maxLength) {
    return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + ELLIPSIS;
  }
}
