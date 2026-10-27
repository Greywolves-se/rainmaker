-- The editor's side of the suggestions (#150; contract 3.3, 5 and 7.1).
-- article: G3's copy of the latest version of each article G2 has, from G2's articles API.
CREATE TABLE article (
    article_id   uuid PRIMARY KEY,
    version      integer NOT NULL CHECK (version >= 1),
    url          text NOT NULL CHECK (url <> ''),
    headline     text NOT NULL,
    published_at timestamptz,
    status       text NOT NULL CHECK (status IN ('classified', 'unclassified', 'failed')),
    -- Only a classified article has a zone (D-4).
    zone         text CHECK (zone IN ('green', 'yellow', 'red')),
    reason_sv    text,
    reason_en    text,
    changed_at   timestamptz NOT NULL,
    CHECK ((status = 'classified') = (zone IS NOT NULL))
);

-- suggestion: every suggestion G2 has sent, with the editor's decision (contract 5). A withdrawn
-- suggestion is kept, so the editor's earlier decisions still point to it.
CREATE TABLE suggestion (
    suggestion_id   uuid PRIMARY KEY,
    article_id      uuid NOT NULL REFERENCES article (article_id),
    article_version integer NOT NULL CHECK (article_version >= 1),
    offer_id        uuid NOT NULL,
    rank            integer NOT NULL CHECK (rank BETWEEN 1 AND 3),
    matched_topics  text[] NOT NULL,
    status          text NOT NULL DEFAULT 'suggested'
        CHECK (status IN ('suggested', 'approved', 'rejected', 'withdrawn'))
);

CREATE INDEX suggestion_by_article ON suggestion (article_id);

-- How far G3 has read G2's changes: the nextSince of the last answer (contract 7.1). One row; the
-- first poll asks for everything.
CREATE TABLE sync_position (
    source text PRIMARY KEY,
    since  timestamptz NOT NULL
);

INSERT INTO sync_position (source, since) VALUES ('g2-articles', '1970-01-01T00:00:00Z');
