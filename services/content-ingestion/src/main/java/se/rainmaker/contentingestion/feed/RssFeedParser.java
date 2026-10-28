package se.rainmaker.contentingestion.feed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads an RSS 2.0 feed (#147). The update time comes from {@code <atom:updated>}, which the test
 * site adds per item (#159); without it, the publish time counts as the last change.
 *
 * <p>An item without a link, a title or a valid date is skipped and logged; the rest of the feed is
 * still read. Document type declarations are refused, so the feed can never pull in external
 * entities (XXE).
 */
public class RssFeedParser {

  private static final Logger LOG = LoggerFactory.getLogger(RssFeedParser.class);
  private static final String ATOM = "http://www.w3.org/2005/Atom";
  private static final String DISALLOW_DOCTYPE =
      "http://apache.org/xml/features/disallow-doctype-decl";

  /**
   * Parses the feed's bytes; the XML declaration decides the character set.
   *
   * @throws InvalidFeedException when the bytes are not an RSS feed with a channel and a language
   */
  public Feed parse(byte[] xml) {
    Element channel = channel(document(xml));
    String language = childText(channel, "language");
    if (language == null) {
      throw new InvalidFeedException("The feed's channel has no language");
    }
    List<FeedItem> items = new ArrayList<>();
    NodeList itemNodes = channel.getElementsByTagName("item");
    for (int index = 0; index < itemNodes.getLength(); index++) {
      Element item = (Element) itemNodes.item(index);
      try {
        items.add(item(item));
      } catch (InvalidFeedException | DateTimeParseException invalid) {
        LOG.warn("Skipped feed item {}: {}", index + 1, invalid.getMessage());
      }
    }
    return new Feed(language, items);
  }

  private static FeedItem item(Element item) {
    String url = required(item, "link");
    String headline = required(item, "title");
    Instant publishedAt =
        ZonedDateTime.parse(required(item, "pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant();
    String updated = childText(item, ATOM, "updated");
    Instant lastModified =
        updated == null ? publishedAt : OffsetDateTime.parse(updated).toInstant();
    return new FeedItem(url, childText(item, "guid"), headline, publishedAt, lastModified);
  }

  private static Document document(byte[] xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature(DISALLOW_DOCTYPE, true);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      // The default handler prints parse errors to stderr; the exception is enough.
      builder.setErrorHandler(new DefaultHandler());
      return builder.parse(new ByteArrayInputStream(xml));
    } catch (SAXException | IOException notXml) {
      throw new InvalidFeedException("The feed is not valid XML: " + notXml.getMessage(), notXml);
    } catch (ParserConfigurationException unsupported) {
      throw new IllegalStateException("The XML parser cannot be made safe", unsupported);
    }
  }

  private static Element channel(Document document) {
    Element root = document.getDocumentElement();
    Element channel = "rss".equals(root.getLocalName()) ? firstChild(root, null, "channel") : null;
    if (channel == null) {
      throw new InvalidFeedException("The feed has no RSS channel");
    }
    return channel;
  }

  private static String required(Element parent, String name) {
    String text = childText(parent, name);
    if (text == null) {
      throw new InvalidFeedException("no <" + name + ">");
    }
    return text;
  }

  private static String childText(Element parent, String name) {
    return childText(parent, null, name);
  }

  /** The trimmed text of the first direct child with this name, or {@code null} if empty. */
  private static String childText(Element parent, String namespace, String name) {
    Element child = firstChild(parent, namespace, name);
    if (child == null) {
      return null;
    }
    String text = child.getTextContent().strip();
    return text.isEmpty() ? null : text;
  }

  private static Element firstChild(Element parent, String namespace, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element
          && name.equals(element.getLocalName())
          && (namespace == null
              ? element.getNamespaceURI() == null
              : namespace.equals(element.getNamespaceURI()))) {
        return element;
      }
    }
    return null;
  }
}
