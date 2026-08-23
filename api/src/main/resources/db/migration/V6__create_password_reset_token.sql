CREATE TABLE password_reset_token (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id  uuid NOT NULL REFERENCES clinic (id),
    user_id    uuid NOT NULL REFERENCES app_user (id),
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_token_clinic_id ON password_reset_token (clinic_id);
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE password_reset_token TO "${applicationRole}";

ALTER TABLE password_reset_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_token FORCE ROW LEVEL SECURITY;

CREATE POLICY password_reset_token_clinic_isolation ON password_reset_token
    USING (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid)
    WITH CHECK (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid);
