package se.rainmaker.contentingestion.site;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads pages from the publisher's site. Only GET: ingestion is read-only and never writes to the
 * publisher (product rule). Every call has a timeout; there is no retry here, because the next poll
 * is the retry (#147).
 */
public class PublisherSite {

  private final RestClient http;

  /** Creates a reader whose connect and read timeouts are both {@code timeout}. */
  public PublisherSite(Duration timeout) {
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    requests.setReadTimeout(timeout);
    this.http = RestClient.builder().requestFactory(requests).build();
  }

  /**
   * Returns the page's bytes, so the parser can follow the document's own character set.
   *
   * @throws SiteUnavailableException when the site cannot be reached, is too slow or answers with
   *     an error status
   */
  public byte[] read(URI url) {
    try {
      byte[] body = http.get().uri(url).retrieve().body(byte[].class);
      return body == null ? new byte[0] : body;
    } catch (RestClientResponseException errorStatus) {
      // Only the status: the body of an error page is the site's text, not ours to log.
      throw new SiteUnavailableException(
          url + " answered HTTP " + errorStatus.getStatusCode().value(), errorStatus);
    } catch (RestClientException unreachable) {
      throw new SiteUnavailableException(
          url + " could not be read: " + unreachable.getMessage(), unreachable);
    }
  }
}
