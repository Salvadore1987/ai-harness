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
    int prefix = 0;
    int shorter = Math.min(oldLines.size(), newLines.size());
    while (prefix < shorter && oldLines.get(prefix).equals(newLines.get(prefix))) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < shorter - prefix
        && oldLines.get(oldLines.size() - 1 - suffix)
            .equals(newLines.get(newLines.size() - 1 - suffix))) {
      suffix++;
    }
    if (prefix == oldLines.size() && prefix == newLines.size()) {
      return "";
    }
    int start = Math.max(0, prefix - CONTEXT_LINES);
    int oldEnd = Math.min(oldLines.size(), oldLines.size() - suffix + CONTEXT_LINES);
    int newEnd = Math.min(newLines.size(), newLines.size() - suffix + CONTEXT_LINES);
    StringBuilder diff = new StringBuilder(String.format(Locale.ROOT, "%s-%d,%d +%d,%d @@%n",
        HUNK_PREFIX, start + 1, oldEnd - start, start + 1, newEnd - start));
    for (int i = start; i < prefix; i++) {
      diff.append(' ').append(oldLines.get(i)).append('\n');
    }
    for (int i = prefix; i < oldLines.size() - suffix; i++) {
      diff.append('-').append(oldLines.get(i)).append('\n');
    }
    for (int i = prefix; i < newLines.size() - suffix; i++) {
      diff.append('+').append(newLines.get(i)).append('\n');
    }
    for (int i = oldLines.size() - suffix; i < oldEnd; i++) {
      diff.append(' ').append(oldLines.get(i)).append('\n');
    }
    return diff.toString();
  }
}
