-- G3 polls the most recently stored articles every few seconds (#202, #232). This index lets that
-- query walk the versions newest first and stop after the count asked for, instead of reading and
-- sorting every article. The article id breaks a tie in stored_at, as in the query.
CREATE INDEX article_version_recent ON article_version (stored_at DESC, article_id DESC);
