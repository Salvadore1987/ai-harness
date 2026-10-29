package dev.localllm.harness.infrastructure.out.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * File tools: {@code read_file}, {@code list_dir}, {@code glob}, {@code grep},
 * {@code write_file} and {@code edit_file}.
 *
 * <p>Relative paths are resolved against the working directory passed in the
 * {@link ToolContext}; absolute paths are accepted as they are. Failures are thrown with a
 * message meant for the model, the registry returns them as error results.
 */
@Component
public class FileTools {

  static final int DEFAULT_READ_LIMIT = 2000;
  static final int MAX_RESULTS = 200;
  static final long MAX_READ_BYTES = 10L * 1024 * 1024;
  static final long MAX_GREP_FILE_BYTES = 1024L * 1024;

  private static final int MAX_MATCH_LINE_LENGTH = 300;
  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of(".git", ".gradle", ".idea", "node_modules");
  private static final String RECURSIVE_PREFIX = "**/";

  /**
   * Reads a text file with line numbers.
   *
   * @param path file path
   * @param offset first line to return, 1-based
   * @param limit maximum number of lines
   * @param context tool context with the working directory
   * @return numbered lines and a note if more lines exist
   */
  @Tool(name = "read_file", resultConverter = PlainTextResultConverter.class, description = """
      Read a UTF-8 text file. Every line is prefixed with its 1-based number and a tab. \
      Large files are returned in parts: use offset and limit to read further.""")
  public String readFile(
      @ToolParam(description = "File path, relative to the working directory or absolute")
      String path,
      @ToolParam(required = false, description = "1-based line to start from, default 1")
      @Nullable Integer offset,
      @ToolParam(required = false, description = "Maximum number of lines, default 2000")
      @Nullable Integer limit,
      ToolContext context) {
    Path file = ToolContexts.resolve(context, path);
    if (Files.isDirectory(file)) {
      throw new IllegalArgumentException(path + " is a directory, use list_dir");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File not found: " + path);
    }
    if (size(file) > MAX_READ_BYTES) {
      throw new IllegalArgumentException("File is larger than 10 MB: " + path);
    }
    List<String> lines = readLines(file, path);
    int first = Math.max(1, offset == null ? 1 : offset);
    int count = Math.max(1, limit == null ? DEFAULT_READ_LIMIT : limit);
    if (lines.isEmpty()) {
      return "(empty file)";
    }
    if (first > lines.size()) {
      throw new IllegalArgumentException(
          "Offset " + first + " is past the end: the file has " + lines.size() + " lines");
    }
    int last = Math.min(lines.size(), first + count - 1);
    StringBuilder text = new StringBuilder();
    for (int number = first; number <= last; number++) {
      text.append(String.format(Locale.ROOT, "%6d\t", number))
          .append(lines.get(number - 1)).append('\n');
    }
    if (first > 1 || last < lines.size()) {
      text.append(String.format(Locale.ROOT,
          "[lines %d-%d of %d; use offset to read more]%n", first, last, lines.size()));
    }
    return text.toString();
  }

  /**
   * Lists a directory.
   *
   * @param path directory path, the working directory if empty
   * @param context tool context with the working directory
   * @return one entry per line, directories end with a slash
   */
  @Tool(name = "list_dir", resultConverter = PlainTextResultConverter.class, description = """
      List the entries of a directory, sorted by name. Directories end with a slash.""")
  public String listDir(
      @ToolParam(required = false,
          description = "Directory path, relative to the working directory or absolute; "
              + "default is the working directory")
      @Nullable String path,
      ToolContext context) {
    String requested = path == null || path.isBlank() ? "." : path;
    Path directory = ToolContexts.resolve(context, requested);
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("Directory not found: " + requested);
    }
    List<String> entries;
    try (Stream<Path> children = Files.list(directory)) {
      entries = children
          .map(child -> child.getFileName() + (Files.isDirectory(child) ? "/" : ""))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot list " + requested + ": " + e.getMessage(), e);
    }
    return entries.isEmpty() ? "(empty directory)" : limited(entries, entries.size());
  }

  /**
   * Finds files by a glob pattern.
   *
   * @param pattern glob relative to the working directory
   * @param context tool context with the working directory
   * @return matching paths relative to the working directory, sorted
   */
  @Tool(name = "glob", resultConverter = PlainTextResultConverter.class, description = """
      Find files whose path relative to the working directory matches a glob pattern, \
      for example **/*.java or src/main/**/Calc*.java. Returns paths sorted by name.""")
  public String glob(
      @ToolParam(description = "Glob pattern relative to the working directory") String pattern,
      ToolContext context) {
    Path root = ToolContexts.workingDirectory(context);
    Predicate<Path> matcher = globMatcher(pattern);
    List<String> matches = new ArrayList<>();
    walk(root, file -> {
      Path relative = root.relativize(file);
      if (matcher.test(relative)) {
        matches.add(relative.toString());
      }
    });
    if (matches.isEmpty()) {
      return "No files match " + pattern;
    }
    List<String> sorted = matches.stream().sorted().toList();
    return limited(sorted, sorted.size());
  }

  /**
   * Searches file contents with a regular expression.
   *
   * @param pattern Java regular expression
   * @param path file or directory to search, the working directory if empty
   * @param glob glob filter for the searched files
   * @param context tool context with the working directory
   * @return matches as {@code path:line: text}
   */
  @Tool(name = "grep", resultConverter = PlainTextResultConverter.class, description = """
      Search UTF-8 text files for lines matching a Java regular expression. Returns \
      path:line: text for every match. Binary files and files over 1 MB are skipped.""")
  public String grep(
      @ToolParam(description = "Java regular expression") String pattern,
      @ToolParam(required = false,
          description = "File or directory to search; default is the working directory")
      @Nullable String path,
      @ToolParam(required = false,
          description = "Glob filter for file paths, for example *.java or src/**/*.kt")
      @Nullable String glob,
      ToolContext context) {
    Path root = ToolContexts.workingDirectory(context);
    Pattern regex = Pattern.compile(pattern);
    String requested = path == null || path.isBlank() ? "." : path;
    Path start = ToolContexts.resolve(context, requested);
    if (!Files.exists(start)) {
      throw new IllegalArgumentException("Path not found: " + requested);
    }
    Predicate<Path> filter = glob == null || glob.isBlank() ? file -> true : grepFilter(glob);
    List<String> matches = new ArrayList<>();
    int[] total = {0};
    walk(start, file -> {
      Path shown = file.startsWith(root) ? root.relativize(file) : file;
      if (!filter.test(shown) || size(file) > MAX_GREP_FILE_BYTES) {
        return;
      }
      List<String> lines = readLinesOrEmpty(file);
      for (int i = 0; i < lines.size(); i++) {
        if (regex.matcher(lines.get(i)).find()) {
          total[0]++;
          if (matches.size() < MAX_RESULTS) {
            matches.add(shown + ":" + (i + 1) + ": " + shorten(lines.get(i).strip()));
          }
        }
      }
    });
    if (matches.isEmpty()) {
      return "No matches for " + pattern;
    }
    return limited(matches, total[0]);
  }

  /**
   * Creates or overwrites a text file.
   *
   * @param path file path
   * @param content full new content
   * @param context tool context with the working directory
   * @return what was written
   */
  @Tool(name = "write_file", resultConverter = PlainTextResultConverter.class, description = """
      Create a UTF-8 text file or overwrite it completely; missing parent directories are \
      created. For changes to an existing file prefer edit_file.""")
  public String writeFile(
      @ToolParam(description = "File path, relative to the working directory or absolute")
      String path,
      @ToolParam(description = "Full content of the file") String content,
      ToolContext context) {
    Path file = ToolContexts.resolve(context, path);
    if (Files.isDirectory(file)) {
      throw new IllegalArgumentException(path + " is a directory");
    }
    boolean existed = Files.exists(file);
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + path + ": " + e.getMessage(), e);
    }
    return String.format(Locale.ROOT, "%s %s (%d lines)",
        existed ? "Overwrote" : "Created", path, content.lines().count());
  }

  /**
   * Replaces a unique substring of a file.
   *
   * @param path file path
   * @param oldText text to replace, must occur exactly once
   * @param newText replacement
   * @param context tool context with the working directory
   * @return unified diff of the change
   */
  @Tool(name = "edit_file", resultConverter = PlainTextResultConverter.class, description = """
      Replace text in a UTF-8 file. oldText must occur in the file exactly once, including \
      whitespace and indentation: add surrounding lines to make it unique. Returns the diff.""")
  public String editFile(
      @ToolParam(description = "File path, relative to the working directory or absolute")
      String path,
      @ToolParam(description = "Exact text to replace, unique in the file") String oldText,
      @ToolParam(description = "Replacement text, may be empty") String newText,
      ToolContext context) {
    if (oldText.isEmpty()) {
      throw new IllegalArgumentException("oldText is empty; use write_file to create a file");
    }
    if (oldText.equals(newText)) {
      throw new IllegalArgumentException("oldText and newText are the same");
    }
    Path file = ToolContexts.resolve(context, path);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File not found: " + path);
    }
    String before;
    try {
      before = Files.readString(file, StandardCharsets.UTF_8);
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("Not a UTF-8 text file: " + path, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + path + ": " + e.getMessage(), e);
    }
    int first = before.indexOf(oldText);
    if (first < 0) {
      throw new IllegalArgumentException("oldText not found in " + path
          + "; read the file and copy the text exactly");
    }
    if (before.indexOf(oldText, first + 1) >= 0) {
      throw new IllegalArgumentException("oldText occurs " + occurrences(before, oldText)
          + " times in " + path + "; add surrounding lines to make it unique");
    }
    String after = before.substring(0, first) + newText
        + before.substring(first + oldText.length());
    try {
      Files.writeString(file, after, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + path + ": " + e.getMessage(), e);
    }
    return "Edited " + path + "\n" + TextDiff.unified(before, after);
  }


  private static Predicate<Path> globMatcher(String pattern) {
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    if (!pattern.startsWith(RECURSIVE_PREFIX)) {
      return matcher::matches;
    }
    PathMatcher topLevel = FileSystems.getDefault()
        .getPathMatcher("glob:" + pattern.substring(RECURSIVE_PREFIX.length()));
    return relative -> matcher.matches(relative) || topLevel.matches(relative);
  }

  private static Predicate<Path> grepFilter(String glob) {
    if (glob.contains("/")) {
      return globMatcher(glob);
    }
    PathMatcher nameMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
    return file -> file.getFileName() != null && nameMatcher.matches(file.getFileName());
  }

  private static void walk(Path start, Consumer<Path> visitor) {
    try {
      Files.walkFileTree(start, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
          Path name = dir.getFileName();
          return !dir.equals(start) && name != null
              && SKIPPED_DIRECTORIES.contains(name.toString())
              ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
          if (attributes.isRegularFile()) {
            visitor.accept(file);
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot walk " + start + ": " + e.getMessage(), e);
    }
  }

  private static List<String> readLines(Path file, String path) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("Not a UTF-8 text file: " + path, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + path + ": " + e.getMessage(), e);
    }
  }

  private static List<String> readLinesOrEmpty(Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return List.of();
    }
  }

  private static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      return Long.MAX_VALUE;
    }
  }

  private static String limited(List<String> lines, int total) {
    StringBuilder text = new StringBuilder();
    lines.stream().limit(MAX_RESULTS).forEach(line -> text.append(line).append('\n'));
    if (total > MAX_RESULTS) {
      text.append("[showing ").append(MAX_RESULTS).append(" of ").append(total)
          .append("; narrow the search]\n");
    }
    return text.toString();
  }

  private static int occurrences(String text, String part) {
    int count = 0;
    for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + 1)) {
      count++;
    }
    return count;
  }

  private static String shorten(String line) {
    return line.length() <= MAX_MATCH_LINE_LENGTH
        ? line : line.substring(0, MAX_MATCH_LINE_LENGTH) + "…";
  }
}
