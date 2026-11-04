-- How many tries in a row ended failed for each version (#258). The retry waits interval × 2^(n − 1)
-- after the n-th failed try, at most max-wait, so a version Claude always refuses costs about one
-- call a day instead of one every interval. Only a failed try counts: an unclassified one made no
-- call (call limits, token cap, open breaker). A classified result sets it back to 0; a new version
-- is a new row and starts at 0.
ALTER TABLE classification ADD COLUMN failed_attempts integer NOT NULL DEFAULT 0
    CHECK (failed_attempts >= 0);

-- Every version that is failed now failed at least once.
UPDATE classification SET failed_attempts = 1 WHERE status = 'failed';
