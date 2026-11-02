-- When a version was last tried (#223): stored as unclassified when ArticleStored arrived, or picked
-- up again by the retry. A failed or unclassified version is tried again once this is an interval
-- old, so a version being classified right now is never picked up twice, and a lasting error
-- isn't tried on every run. Existing rows count as tried now.
ALTER TABLE classification ADD COLUMN attempted_at timestamptz NOT NULL DEFAULT now();

-- The retry reads the failed and unclassified versions tried longest ago.
CREATE INDEX classification_retry ON classification (attempted_at)
    WHERE status IN ('failed', 'unclassified');
