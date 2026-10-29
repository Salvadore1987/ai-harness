package dev.localllm.harness.infrastructure.out.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * HTTP tool {@code http_fetch}: a GET request whose body is returned as text.
 *
 * <p>Redirects are followed, HTML is simplified by {@link HtmlText}, binary content is refused.
 * At most {@link #MAX_BODY_BYTES} of the body are read; the registry cuts the text further to
 * {@code harness.tools.max-output-chars}. A status of 400 or more is an error result that still
 * carries the beginning of the body.
 */
@Component
public class HttpTools {

  static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

  private static final Set<String> SCHEMES = Set.of("http", "https");
  private static final int ERROR_BODY_CHARS = 1000;
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; harness)";
  private static final String ACCEPT =
      "text/html, text/plain, text/markdown, application/json, application/xml;q=0.9, */*;q=0.5";

  private final RestClient restClient;

  /**
   * Creates the tool.
   *
   * @param properties harness settings with the HTTP timeout
   */
  public HttpTools(HarnessProperties properties) {
    Duration timeout = properties.tools().httpTimeout();
    HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(timeout)
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    this.restClient = RestClient.builder()
        .requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
        .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
        .build();
  }

  /**
   * Fetches a URL.
   *
   * @param url absolute http or https URL
   * @return status line, content type and the body as text
   */
  @Tool(name = "http_fetch", resultConverter = PlainTextResultConverter.class, description = """
      Fetch a URL with HTTP GET and return the response body as text. HTML pages are \
      simplified to plain text; binary content such as images is not supported.""")
  public String httpFetch(
      @ToolParam(description = "Absolute http or https URL") String url) {
    URI uri = parse(url);
    return restClient.get().uri(uri).exchange((request, response) -> {
      int status = response.getStatusCode().value();
      MediaType type = response.getHeaders().getContentType();
      if (type != null && isBinary(type)) {
        throw new IllegalArgumentException("HTTP " + status + ": binary content " + type
            + " is not supported");
      }
      String body = new String(readLimited(response.getBody()), charset(type));
      String text = isHtml(type, body) ? HtmlText.toText(body) : body;
      if (status >= 400) {
        throw new IllegalStateException("HTTP " + status + " from " + uri + "\n"
            + (text.length() <= ERROR_BODY_CHARS ? text : text.substring(0, ERROR_BODY_CHARS)));
      }
      return "HTTP " + status + (type == null ? "" : " " + type) + "\n\n" + text;
    });
  }

  private static URI parse(String url) {
    URI uri;
    try {
      uri = URI.create(url.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid URL: " + url, e);
    }
    String scheme = uri.getScheme();
    if (scheme == null || !SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
        || uri.getHost() == null) {
      throw new IllegalArgumentException("Only absolute http and https URLs are supported: "
          + url);
    }
    return uri;
  }

  private static byte[] readLimited(InputStream body) throws IOException {
    try (body) {
      return body.readNBytes(MAX_BODY_BYTES);
    }
  }

  private static Charset charset(@Nullable MediaType type) {
    Charset charset = type == null ? null : type.getCharset();
    return charset == null ? StandardCharsets.UTF_8 : charset;
  }

  private static boolean isHtml(@Nullable MediaType type, String body) {
    if (type != null) {
      return type.isCompatibleWith(MediaType.TEXT_HTML)
          || type.isCompatibleWith(MediaType.APPLICATION_XHTML_XML);
    }
    String start = body.stripLeading().toLowerCase(Locale.ROOT);
    return start.startsWith("<!doctype html") || start.startsWith("<html");
  }

  private static boolean isBinary(MediaType type) {
    String main = type.getType().toLowerCase(Locale.ROOT);
    String sub = type.getSubtype().toLowerCase(Locale.ROOT);
    return switch (main) {
      case "image", "audio", "video", "font" -> true;
      case "application" -> sub.equals("octet-stream") || sub.equals("pdf")
          || sub.equals("zip") || sub.equals("gzip");
      default -> false;
    };
  }
}
