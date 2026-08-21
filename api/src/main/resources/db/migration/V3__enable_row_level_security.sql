-- Every table carrying clinic_id is protected by PostgreSQL in addition to
-- Hibernate's @TenantId filtering by clinic_id. The policies deny access when the transaction
-- has no app.clinic_id setting because current_setting(..., true) returns NULL.

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership FORCE ROW LEVEL SECURITY;

CREATE POLICY membership_clinic_isolation ON membership
    USING (clinic_id = current_setting('app.clinic_id', true)::uuid)
    WITH CHECK (clinic_id = current_setting('app.clinic_id', true)::uuid);

ALTER TABLE refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token FORCE ROW LEVEL SECURITY;

CREATE POLICY refresh_token_clinic_isolation ON refresh_token
    USING (clinic_id = current_setting('app.clinic_id', true)::uuid)
    WITH CHECK (clinic_id = current_setting('app.clinic_id', true)::uuid);

ALTER TABLE staff_invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff_invitation FORCE ROW LEVEL SECURITY;

CREATE POLICY staff_invitation_clinic_isolation ON staff_invitation
    USING (clinic_id = current_setting('app.clinic_id', true)::uuid)
    WITH CHECK (clinic_id = current_setting('app.clinic_id', true)::uuid);
