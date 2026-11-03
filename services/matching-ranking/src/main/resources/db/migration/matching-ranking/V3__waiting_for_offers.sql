-- Green article versions that couldn't be matched because G3's offers API was down (#248).
-- waiting_topics: the newest version's topics while it waits for offers, NULL otherwise. Any later
-- result for that version or a newer one (matched, nothing fits, not green) clears it, so only an
-- outage is retried and a replaced version never is.
-- attempted_at: when matching last tried; the retry takes articles not tried for a while, oldest
-- first.
ALTER TABLE matched_article
    ADD COLUMN waiting_topics text[],
    ADD COLUMN waiting_correlation_id text,
    ADD COLUMN attempted_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX matched_article_waiting ON matched_article (attempted_at)
    WHERE waiting_topics IS NOT NULL;
