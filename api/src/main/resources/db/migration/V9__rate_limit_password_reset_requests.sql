CREATE TABLE password_reset_request_limit (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id         uuid NOT NULL REFERENCES clinic (id),
    email_fingerprint varchar(64) NOT NULL,
    window_started_at timestamptz NOT NULL,
    request_count     integer NOT NULL CHECK (request_count > 0),
    UNIQUE (clinic_id, email_fingerprint)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE password_reset_request_limit TO "${applicationRole}";

ALTER TABLE password_reset_request_limit ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_request_limit FORCE ROW LEVEL SECURITY;

CREATE POLICY password_reset_request_limit_clinic_isolation ON password_reset_request_limit
    USING (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid)
    WITH CHECK (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid);
