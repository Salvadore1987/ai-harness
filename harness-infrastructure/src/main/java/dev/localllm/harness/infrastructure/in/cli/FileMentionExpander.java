package dev.localllm.harness.infrastructure.in.cli;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Appends the files mentioned as {@code @path} to the user message.
 *
 * <p>Only regular text files inside the working directory are attached, each at most
 * {@value #MAX_FILE_BYTES} bytes. The mention itself stays in the text so the model knows what
 * the user referred to; problems are reported back instead of failing the turn.
 */
@Component
public class FileMentionExpander {

  static final int MAX_FILE_BYTES = 256 * 1024;

  private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([^\\s@]+)");

  /**
   * Expanded message and the mentions that could not be attached.
   *
   * @param text message with the attached files
   * @param problems one human-readable line per skipped mention
   */
  public record Expansion(String text, List<String> problems) {
  }

  /**
   * Attaches the mentioned files.
   *
   * @param text user message
   * @param workingDirectory directory mentions are resolved against
   * @return expanded message and problems
   */
  public Expansion expand(String text, Path workingDirectory) {
    Set<String> mentions = new LinkedHashSet<>();
    Matcher matcher = MENTION.matcher(text);
    while (matcher.find()) {
      mentions.add(matcher.group(1));
    }
    if (mentions.isEmpty()) {
      return new Expansion(text, List.of());
    }
    StringBuilder expanded = new StringBuilder(text);
    List<String> problems = new ArrayList<>();
    Path root = workingDirectory.toAbsolutePath().normalize();
    for (String mention : mentions) {
      Path file = root.resolve(mention).normalize();
      String problem = check(file, root);
      if (problem != null) {
        problems.add("@" + mention + ": " + problem);
        continue;
      }
      try {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        expanded.append("\n\n<file path=\"").append(root.relativize(file)).append("\">\n")
            .append(content);
        if (!content.endsWith("\n")) {
          expanded.append('\n');
        }
        expanded.append("</file>");
      } catch (MalformedInputException e) {
        problems.add("@" + mention + ": не текстовый файл (не UTF-8)");
      } catch (IOException e) {
        problems.add("@" + mention + ": не удалось прочитать (" + e.getMessage() + ")");
      }
    }
    return new Expansion(expanded.toString(), problems);
  }

  private static @Nullable String check(Path file, Path root) {
    if (!file.startsWith(root)) {
      return "файл вне рабочей директории " + root;
    }
    if (!Files.isRegularFile(file)) {
      return "файл не найден";
    }
    try {
      if (Files.size(file) > MAX_FILE_BYTES) {
        return "файл больше " + MAX_FILE_BYTES / 1024 + " КБ";
      }
    } catch (IOException e) {
      return "не удалось прочитать размер (" + e.getMessage() + ")";
    }
    return null;
  }
}
