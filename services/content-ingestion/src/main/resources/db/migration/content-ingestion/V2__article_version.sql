-- Every version of every article read from the publisher's feed (#147, contract section 3.1).
-- Append-only: an edited article gets a new row with version + 1; rows are never updated.
CREATE TABLE article_version (
    article_id     uuid        NOT NULL,
    version        integer     NOT NULL CHECK (version >= 1),
    url            text        NOT NULL,
    external_id    text,
    headline       text        NOT NULL,
    body_text      text        NOT NULL,
    section        text        NOT NULL,
    language       text        NOT NULL,
    published_at   timestamptz NOT NULL,
    updated_at     timestamptz,
    -- The feed's update time (atom:updated, or the publish time); a newer one means a new version.
    last_modified  timestamptz NOT NULL,
    correlation_id text        NOT NULL,
    stored_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (article_id, version),
    -- The URL is the article's key (decision D-2): one row per URL and version, so a repeated
    -- poll can never store the same version twice.
    UNIQUE (url, version)
);
