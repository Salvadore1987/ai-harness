package dev.localllm.harness.infrastructure.out.tools;

import java.util.List;
import java.util.Locale;

/**
 * Unified diff of a single changed region, enough for {@code edit_file}, which replaces one
 * substring: lines before the first and after the last difference are shared context.
 */
final class TextDiff {

  /** Prefix of the hunk header line. */
  static final String HUNK_PREFIX = "@@ ";

  private static final int CONTEXT_LINES = 3;

  private TextDiff() {
  }

  /**
   * Builds the diff of two versions of a text.
   *
   * @param before old text
   * @param after new text
   * @return one hunk with {@code -}, {@code +} and context lines, empty if the texts are equal
   */
  static String unified(String before, String after) {
    List<String> oldLines = before.lines().toList();
    List<String> newLines = after.lines().toList();
    int prefix = commonPrefix(oldLines, newLines);
    if (prefix == oldLines.size() && prefix == newLines.size()) {
      return "";
    }
    int suffix = commonSuffix(oldLines, newLines, prefix);
    int oldChangeEnd = oldLines.size() - suffix;
    int newChangeEnd = newLines.size() - suffix;
    int start = Math.max(0, prefix - CONTEXT_LINES);
    int oldEnd = Math.min(oldLines.size(), oldChangeEnd + CONTEXT_LINES);
    int newEnd = Math.min(newLines.size(), newChangeEnd + CONTEXT_LINES);
    StringBuilder diff = new StringBuilder(String.format(Locale.ROOT, "%s-%d,%d +%d,%d @@%n",
        HUNK_PREFIX, start + 1, oldEnd - start, start + 1, newEnd - start));
    appendLines(diff, ' ', oldLines, start, prefix);
    appendLines(diff, '-', oldLines, prefix, oldChangeEnd);
    appendLines(diff, '+', newLines, prefix, newChangeEnd);
    appendLines(diff, ' ', oldLines, oldChangeEnd, oldEnd);
    return diff.toString();
  }

  private static int commonPrefix(List<String> oldLines, List<String> newLines) {
    int shorter = Math.min(oldLines.size(), newLines.size());
    int prefix = 0;
    while (prefix < shorter && oldLines.get(prefix).equals(newLines.get(prefix))) {
      prefix++;
    }
    return prefix;
  }

  private static int commonSuffix(List<String> oldLines, List<String> newLines, int prefix) {
    int limit = Math.min(oldLines.size(), newLines.size()) - prefix;
    int suffix = 0;
    while (suffix < limit
        && oldLines.get(oldLines.size() - 1 - suffix)
            .equals(newLines.get(newLines.size() - 1 - suffix))) {
      suffix++;
    }
    return suffix;
  }

  private static void appendLines(StringBuilder diff, char marker, List<String> lines,
      int from, int to) {
    for (int i = from; i < to; i++) {
      diff.append(marker).append(lines.get(i)).append('\n');
    }
  }
}
