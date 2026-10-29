-- Matching's suggestions (#149; contract 3.5, 5 and 6.2).
-- matched_article: the newest article version matching has seen. An event for an older version
-- is ignored; a newer version replaces the whole suggestion set (decision D-5).
CREATE TABLE matched_article (
    article_id      uuid PRIMARY KEY,
    article_version integer NOT NULL CHECK (article_version >= 1),
    matched_at      timestamptz NOT NULL DEFAULT now()
);

-- suggestion: the complete current set per article, for its newest version only (at most 3).
-- An empty set (no rows) means no suggestions: the article isn't green, or nothing fits.
-- The editor's decisions are kept in G3 (publisher-control), not here.
CREATE TABLE suggestion (
    suggestion_id   uuid PRIMARY KEY,
    article_id      uuid NOT NULL REFERENCES matched_article (article_id),
    article_version integer NOT NULL CHECK (article_version >= 1),
    offer_id        uuid NOT NULL,
    rank            integer NOT NULL CHECK (rank BETWEEN 1 AND 3),
    matched_topic   text NOT NULL CHECK (matched_topic <> ''),
    UNIQUE (article_id, rank),
    UNIQUE (article_id, offer_id)
);
