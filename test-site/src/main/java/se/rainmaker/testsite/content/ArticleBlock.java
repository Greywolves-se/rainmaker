package se.rainmaker.testsite.content;

import java.util.List;
import java.util.stream.Stream;

/**
 * One building block of an article body. The templates choose how to show a block by its {@link
 * #type()}.
 */
public sealed interface ArticleBlock {

  /** The block type as written in the content files, e.g. "paragraph". */
  String type();

  /** All the text in the block, used to count words for the reading time. */
  List<String> texts();

  /** A normal paragraph of body text. */
  record Paragraph(String text) implements ArticleBlock {
    @Override
    public String type() {
      return "paragraph";
    }

    @Override
    public List<String> texts() {
      return List.of(text);
    }
  }

  /** A sub-heading inside the article. */
  record Subheading(String text) implements ArticleBlock {
    @Override
    public String type() {
      return "subheading";
    }

    @Override
    public List<String> texts() {
      return List.of(text);
    }
  }

  /** A highlighted quote and who said it. */
  record Quote(String text, String source) implements ArticleBlock {
    @Override
    public String type() {
      return "quote";
    }

    @Override
    public List<String> texts() {
      return List.of(text, source);
    }
  }

  /** A fact box ("faktaruta") with a title and a list of points. */
  record Factbox(String title, List<String> items) implements ArticleBlock {

    /** Makes the list unmodifiable. */
    public Factbox {
      items = List.copyOf(items);
    }

    @Override
    public String type() {
      return "factbox";
    }

    @Override
    public List<String> texts() {
      return Stream.concat(Stream.of(title), items.stream()).toList();
    }
  }
}
