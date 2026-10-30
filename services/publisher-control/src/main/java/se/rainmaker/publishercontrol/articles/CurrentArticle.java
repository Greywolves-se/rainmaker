package se.rainmaker.publishercontrol.articles;

import java.util.UUID;

/**
 * An article as G2 has it at this moment (contract 7.1), for the approved links API.
 *
 * @param articleId the article's id
 * @param version its latest version
 * @param url its canonical address
 * @param green whether this version is classified and green (contract 3.3)
 */
public record CurrentArticle(UUID articleId, int version, String url, boolean green) {}
