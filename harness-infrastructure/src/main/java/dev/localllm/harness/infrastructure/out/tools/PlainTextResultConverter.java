package dev.localllm.harness.infrastructure.out.tools;

import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * Returns the text produced by a tool as is. The default converter of Spring AI serializes
 * strings to JSON, which would escape every line break of a file.
 */
public class PlainTextResultConverter implements ToolCallResultConverter {

  @Override
  public String convert(@Nullable Object result, @Nullable Type returnType) {
    return result == null ? "" : result.toString();
  }
}
