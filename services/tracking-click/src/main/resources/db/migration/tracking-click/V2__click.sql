-- One row per click (#153, contract 3.6). click_id is the primary key, so the same click (a
-- double-click or a retry of the same link) can only be stored once, also when it arrives twice
-- at the same time. No IP, no cookie, no user id.
CREATE TABLE click (
    click_id   uuid        PRIMARY KEY,
    article_id uuid        NOT NULL,
    offer_id   uuid        NOT NULL,
    clicked_at timestamptz NOT NULL DEFAULT now()
);

-- Clicks are append-only (CLAUDE.md, product rules): a mistake is corrected with a new
-- compensating record, never by changing or removing a click. The database refuses it too.
CREATE FUNCTION refuse_click_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'click rows are append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER click_append_only
    BEFORE UPDATE OR DELETE ON click
    FOR EACH ROW EXECUTE FUNCTION refuse_click_change();

CREATE TRIGGER click_append_only_truncate
    BEFORE TRUNCATE ON click
    FOR EACH STATEMENT EXECUTE FUNCTION refuse_click_change();
