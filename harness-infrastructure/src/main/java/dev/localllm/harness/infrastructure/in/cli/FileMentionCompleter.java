package dev.localllm.harness.infrastructure.in.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * Completes paths inside the working directory after {@code @}.
 */
public final class FileMentionCompleter implements Completer {

  private final Path root;

  /**
   * Creates the completer.
   *
   * @param workingDirectory directory the paths are relative to
   */
  public FileMentionCompleter(Path workingDirectory) {
    this.root = workingDirectory.toAbsolutePath().normalize();
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    String word = line.word();
    if (!word.startsWith("@")) {
      return;
    }
    String typed = word.substring(1);
    int slash = typed.lastIndexOf('/');
    String parent = slash < 0 ? "" : typed.substring(0, slash + 1);
    String prefix = typed.substring(slash + 1);
    Path directory = root.resolve(parent).normalize();
    if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
      return;
    }
    children(directory).stream()
        .filter(child -> child.getFileName().toString().startsWith(prefix))
        .filter(child -> prefix.startsWith(".")
            || !child.getFileName().toString().startsWith("."))
        .forEach(child -> candidates.add(candidate(parent, child)));
  }

  private static List<Path> children(Path directory) {
    try (Stream<Path> children = Files.list(directory)) {
      return children.sorted().toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static Candidate candidate(String parent, Path child) {
    boolean directory = Files.isDirectory(child);
    String value = "@" + parent + child.getFileName() + (directory ? "/" : "");
    return new Candidate(value, child.getFileName() + (directory ? "/" : ""),
        null, null, null, null, !directory);
  }
}
