package se.rainmaker.testsite.content;

/**
 * The articles and the front page, read from the same version of the content files.
 *
 * @param articles every article
 * @param frontPage the editorial front page, pointing to those articles
 */
public record SiteContent(ArticleRepository articles, FrontPage frontPage) {}
