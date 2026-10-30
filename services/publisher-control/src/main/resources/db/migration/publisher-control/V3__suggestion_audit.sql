-- suggestion_audit: one row per change of a suggestion's status: who, when and what (#150,
-- contract 7.6). The editor approves or rejects; G3's sync withdraws, by 'g2', when G2's data says
-- so (contract 5), so an approved link leaving the widget is logged too.
-- audit_id is a UUIDv7 from PostgreSQL 18 itself (contract 3: ids are UUIDv7).
CREATE TABLE suggestion_audit (
    audit_id      uuid PRIMARY KEY DEFAULT uuidv7(),
    suggestion_id uuid NOT NULL REFERENCES suggestion (suggestion_id),
    action        text NOT NULL CHECK (action IN ('approved', 'rejected', 'withdrawn')),
    decided_by    text NOT NULL CHECK (decided_by <> ''),
    decided_at    timestamptz NOT NULL DEFAULT now()
);

-- Audit records are append-only (CLAUDE.md, product rules): a mistake is corrected with a new
-- decision, never by changing or removing a row. The database refuses it too.
CREATE FUNCTION refuse_audit_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'suggestion_audit rows are append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER suggestion_audit_append_only
    BEFORE UPDATE OR DELETE ON suggestion_audit
    FOR EACH ROW EXECUTE FUNCTION refuse_audit_change();

CREATE TRIGGER suggestion_audit_append_only_truncate
    BEFORE TRUNCATE ON suggestion_audit
    FOR EACH STATEMENT EXECUTE FUNCTION refuse_audit_change();
