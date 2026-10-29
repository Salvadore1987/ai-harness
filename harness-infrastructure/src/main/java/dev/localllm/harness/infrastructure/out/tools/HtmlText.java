package dev.localllm.harness.infrastructure.out.tools;

import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an HTML page into readable plain text for the model: scripts, styles and markup are
 * removed, block elements become line breaks, headings and list items keep a Markdown marker.
 * It is a simplification, not a parser: good enough to read documentation and articles.
 */
final class HtmlText {

  private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
  private static final Pattern HIDDEN_ELEMENT = Pattern.compile(
      "(?is)<(script|style|noscript|svg|template|iframe)\\b[^>]*>.*?</\\1\\s*>");
  private static final Pattern TITLE = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>");
  private static final Pattern HEAD = Pattern.compile("(?is)<head\\b[^>]*>.*?</head\\s*>");
  private static final Pattern HEADING = Pattern.compile("(?i)<h([1-6])\\b[^>]*>");
  private static final Pattern LIST_ITEM = Pattern.compile("(?i)<li\\b[^>]*>");
  private static final Pattern BLOCK_BREAK = Pattern.compile(
      "(?i)<(br|hr|/?p|/?div|/h[1-6]|/?ul|/?ol|/?tr|/?table|/?pre|/?blockquote|/?section"
          + "|/?article|/?header|/?footer|/?nav|/?main|/?dl|/?dt|/?dd|/?form)\\b[^>]*>");
  private static final Pattern CELL = Pattern.compile("(?i)</t[dh]\\s*>");
  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
  private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");
  private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
  private static final Map<String, String> NAMED_ENTITIES = Map.of(
      "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ",
      "mdash", "—", "ndash", "–", "hellip", "…", "copy", "©");

  private HtmlText() {
  }

  /**
   * Converts a page to text.
   *
   * @param html page source
   * @return plain text with the page title on the first line, if the page has one
   */
  static String toText(String html) {
    String text = COMMENT.matcher(html).replaceAll("");
    text = HIDDEN_ELEMENT.matcher(text).replaceAll("");
    Matcher title = TITLE.matcher(text);
    String heading = title.find() ? "# " + clean(title.group(1)).strip() + "\n\n" : "";
    text = HEAD.matcher(text).replaceAll("");
    text = HEADING.matcher(text).replaceAll(match ->
        "\n" + "#".repeat(Integer.parseInt(match.group(1))) + " ");
    text = LIST_ITEM.matcher(text).replaceAll("\n- ");
    text = CELL.matcher(text).replaceAll(" | ");
    text = BLOCK_BREAK.matcher(text).replaceAll("\n");
    String body = clean(text);
    StringBuilder lines = new StringBuilder(heading);
    body.lines().map(String::strip).forEach(line -> lines.append(line).append('\n'));
    return BLANK_LINES.matcher(lines.toString()).replaceAll("\n\n").strip() + "\n";
  }

  private static String clean(String fragment) {
    String text = TAG.matcher(fragment).replaceAll("");
    text = ENTITY.matcher(text).replaceAll(match -> Matcher.quoteReplacement(decode(match)));
    return SPACES.matcher(text).replaceAll(" ");
  }

  private static String decode(MatchResult match) {
    String entity = match.group(1);
    try {
      if (entity.startsWith("#x")) {
        return Character.toString(Integer.parseInt(entity.substring(2), 16));
      }
      if (entity.startsWith("#")) {
        return Character.toString(Integer.parseInt(entity.substring(1)));
      }
    } catch (IllegalArgumentException e) {
      return match.group();
    }
    return NAMED_ENTITIES.getOrDefault(entity.toLowerCase(Locale.ROOT), match.group());
  }
}
