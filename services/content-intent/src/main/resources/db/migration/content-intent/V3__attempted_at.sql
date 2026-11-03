-- When content-intent last tried to classify each version (#223): when the version was stored, when
-- its result was stored, and when the retry took it. The retry takes failed and unclassified
-- versions not tried for a while, oldest first, so a version being classified right now is left
-- alone and a long outage is worked through in turn.
ALTER TABLE classification ADD COLUMN attempted_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX classification_to_retry ON classification (attempted_at)
    WHERE status <> 'classified';
