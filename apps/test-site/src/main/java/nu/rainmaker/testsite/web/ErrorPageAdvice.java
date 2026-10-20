package nu.rainmaker.testsite.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns errors into proper pages. The cases are:
 *
 * <ul>
 *   <li>unknown article, section or URL - the 404 page, with the normal layout
 *   <li>other client errors (e.g. POST to a page) - a short text with the right status
 *   <li>anything unexpected - logged with its stack trace, and the 500 page
 * </ul>
 */
@ControllerAdvice
public class ErrorPageAdvice {

  private static final Logger LOG = LoggerFactory.getLogger(ErrorPageAdvice.class);

  private final LayoutAttributes layoutAttributes;
  private final MessageSource messages;

  /** Creates the advice. */
  public ErrorPageAdvice(LayoutAttributes layoutAttributes, MessageSource messages) {
    this.layoutAttributes = layoutAttributes;
    this.messages = messages;
  }

  /** Unknown article, section or address: the 404 page. */
  @ExceptionHandler({PageNotFoundException.class, NoHandlerFoundException.class})
  public ModelAndView notFound(HttpServletRequest request, Locale locale) {
    return statusPage(
        HttpStatus.NOT_FOUND, "notFound.title", "notFound.text", request.getRequestURI(), locale);
  }

  /**
   * A missing file under /static/: a short plain-text 404. A browser loading a stylesheet or script
   * has no use for an HTML page (and Thymeleaf would label it by the requested file extension, e.g.
   * text/css).
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<String> staticFileNotFound(Locale locale) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
        .body(messages.getMessage("notFound.title", null, locale));
  }

  /**
   * Spring MVC's own request errors, such as 405 Method Not Allowed. They all extend
   * ServletException and carry their status and headers (e.g. "Allow: GET, HEAD").
   */
  @ExceptionHandler(ServletException.class)
  public ResponseEntity<String> requestError(ServletException exception, Locale locale) {
    if (!(exception instanceof ErrorResponse error)) {
      LOG.error("Request failed", exception);
      return ResponseEntity.internalServerError()
          .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
          .body(messages.getMessage("error.text", null, locale));
    }
    HttpStatusCode status = error.getStatusCode();
    String text =
        status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()
            ? messages.getMessage("methodNotAllowed.text", null, locale)
            : status.toString();
    return ResponseEntity.status(status)
        .headers(error.getHeaders())
        .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
        .body(text);
  }

  /** Anything unexpected: log it and show the 500 page. */
  @ExceptionHandler(Exception.class)
  public ModelAndView unexpected(Exception exception, HttpServletRequest request, Locale locale) {
    LOG.error("Request failed: {}", request.getRequestURI(), exception);
    return statusPage(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "error.title",
        "error.text",
        request.getRequestURI(),
        locale);
  }

  private ModelAndView statusPage(
      HttpStatus status, String titleKey, String textKey, String path, Locale locale) {
    ModelAndView page = new ModelAndView("status-page", status);
    page.addAllObjects(layoutAttributes.asMap());
    String title = messages.getMessage(titleKey, null, locale);
    String text = messages.getMessage(textKey, null, locale);
    page.addObject("pageTitle", title);
    page.addObject("description", text);
    page.addObject("path", path);
    page.addObject("activeNav", null);
    page.addObject("statusTitle", title);
    page.addObject("statusText", text);
    return page;
  }
}
