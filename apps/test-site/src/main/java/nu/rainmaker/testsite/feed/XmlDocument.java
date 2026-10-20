package nu.rainmaker.testsite.feed;

import java.io.StringWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Small wrapper around the JDK's StAX writer. StAX escapes all text and attributes itself, so the
 * feeds are always well-formed XML, whatever characters the articles contain.
 */
final class XmlDocument {

  /** Writes the content of a document. */
  @FunctionalInterface
  interface Content {
    void write(XmlDocument document) throws XMLStreamException;
  }

  private static final XMLOutputFactory FACTORY = XMLOutputFactory.newFactory();

  private final XMLStreamWriter writer;
  private int depth;

  private XmlDocument(XMLStreamWriter writer) {
    this.writer = writer;
  }

  /** Builds a complete XML document as a string. */
  static String build(Content content) {
    StringWriter output = new StringWriter();
    try {
      XMLStreamWriter writer = FACTORY.createXMLStreamWriter(output);
      writer.writeStartDocument("UTF-8", "1.0");
      content.write(new XmlDocument(writer));
      writer.writeCharacters("\n");
      writer.writeEndDocument();
      writer.close();
    } catch (XMLStreamException exception) {
      throw new IllegalStateException("Could not write XML", exception);
    }
    return output.toString();
  }

  /** Opens an element (on a new, indented line). */
  XmlDocument start(String name) throws XMLStreamException {
    newLine();
    writer.writeStartElement(name);
    depth++;
    return this;
  }

  /** Opens an element in a namespace whose prefix is declared on the root element. */
  XmlDocument start(String prefix, String name, String namespace) throws XMLStreamException {
    newLine();
    writer.writeStartElement(prefix, name, namespace);
    depth++;
    return this;
  }

  /** Declares a namespace prefix on the element just opened. */
  XmlDocument namespace(String prefix, String uri) throws XMLStreamException {
    if (prefix.isEmpty()) {
      writer.writeDefaultNamespace(uri);
    } else {
      writer.writeNamespace(prefix, uri);
    }
    return this;
  }

  /** Adds an attribute to the element just opened. */
  XmlDocument attribute(String name, String value) throws XMLStreamException {
    writer.writeAttribute(name, value);
    return this;
  }

  /** Closes the current element (on a new line if it contained elements). */
  XmlDocument end() throws XMLStreamException {
    depth--;
    newLine();
    writer.writeEndElement();
    return this;
  }

  /** Writes {@code <name>text</name>} on one line. */
  XmlDocument element(String name, String text) throws XMLStreamException {
    newLine();
    writer.writeStartElement(name);
    writer.writeCharacters(text);
    writer.writeEndElement();
    return this;
  }

  /** Writes {@code <prefix:name>text</prefix:name>} on one line. */
  XmlDocument element(String prefix, String name, String namespace, String text)
      throws XMLStreamException {
    newLine();
    writer.writeStartElement(prefix, name, namespace);
    writer.writeCharacters(text);
    writer.writeEndElement();
    return this;
  }

  /** Writes {@code <name attribute="value">text</name>} on one line. */
  XmlDocument elementWithAttribute(
      String name, String attribute, String attributeValue, String text) throws XMLStreamException {
    newLine();
    writer.writeStartElement(name);
    writer.writeAttribute(attribute, attributeValue);
    writer.writeCharacters(text);
    writer.writeEndElement();
    return this;
  }

  /** Writes an empty element with attributes, e.g. {@code <atom:link .../>}. */
  XmlDocument emptyElement(String prefix, String name, String namespace, String... attributes)
      throws XMLStreamException {
    newLine();
    writer.writeEmptyElement(prefix, name, namespace);
    for (int index = 0; index + 1 < attributes.length; index += 2) {
      writer.writeAttribute(attributes[index], attributes[index + 1]);
    }
    return this;
  }

  private void newLine() throws XMLStreamException {
    writer.writeCharacters("\n" + "  ".repeat(depth));
  }
}
