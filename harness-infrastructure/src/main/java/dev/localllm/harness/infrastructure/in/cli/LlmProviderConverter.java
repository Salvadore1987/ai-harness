package dev.localllm.harness.infrastructure.in.cli;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import picocli.CommandLine;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * Converts {@code --provider ollama|anthropic} case-insensitively.
 */
public class LlmProviderConverter implements CommandLine.ITypeConverter<LlmProvider> {

  @Override
  public LlmProvider convert(String value) {
    return find(value).orElseThrow(() -> new CommandLine.TypeConversionException(
        "expected " + names(" or ") + ", got '" + value + "'"));
  }

  /**
   * Finds a provider by its name in any case.
   *
   * @param value provider name, for example {@code anthropic}
   * @return the provider, empty if the name is unknown
   */
  static Optional<LlmProvider> find(String value) {
    return Arrays.stream(LlmProvider.values())
        .filter(provider -> provider.name().equalsIgnoreCase(value))
        .findFirst();
  }

  /**
   * Name of a provider as the user types it.
   *
   * @param provider the provider
   * @return lower-case name
   */
  static String displayName(LlmProvider provider) {
    return provider.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Names of all providers as the user types them.
   *
   * @param delimiter text between the names
   * @return joined names in declaration order
   */
  static String names(String delimiter) {
    return Arrays.stream(LlmProvider.values())
        .map(LlmProviderConverter::displayName)
        .collect(Collectors.joining(delimiter));
  }
}
