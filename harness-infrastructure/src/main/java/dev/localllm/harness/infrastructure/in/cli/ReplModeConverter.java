package dev.localllm.harness.infrastructure.in.cli;

import java.util.Locale;

import picocli.CommandLine;

/**
 * Converts {@code --mode chat|agent} case-insensitively.
 */
public class ReplModeConverter implements CommandLine.ITypeConverter<ReplMode> {

  @Override
  public ReplMode convert(String value) {
    try {
      return ReplMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new CommandLine.TypeConversionException(
          "expected chat or agent, got '" + value + "'");
    }
  }
}
