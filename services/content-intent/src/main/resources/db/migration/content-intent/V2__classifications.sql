-- The classification of each article version (#209, contract 3.2). Stored as 'unclassified' as
-- soon as ArticleStored arrives (6.1), then updated with the classifier's result. A newer result
-- for the same version replaces the old one. Never the article's text.
CREATE TABLE classification (
    article_id      uuid        NOT NULL,
    article_version integer     NOT NULL CHECK (article_version >= 1),
    status          text        NOT NULL CHECK (status IN ('unclassified', 'classified', 'failed')),
    zone            text        CHECK (zone IN ('green', 'yellow', 'red')),
    -- Most relevant first: matching ranks by this order (D-3).
    topics          text[]      NOT NULL DEFAULT '{}',
    reason_sv       text,
    reason_en       text,
    model           text,
    classified_at   timestamptz,
    correlation_id  text        NOT NULL,
    PRIMARY KEY (article_id, article_version),
    -- Only a classified article has a zone, topics and a reason (3.2, D-4).
    CHECK ((status = 'classified') = (zone IS NOT NULL)),
    CHECK ((status = 'classified') = (reason_sv IS NOT NULL AND reason_en IS NOT NULL)),
    CHECK ((status = 'classified') = (cardinality(topics) BETWEEN 1 AND 3))
);
