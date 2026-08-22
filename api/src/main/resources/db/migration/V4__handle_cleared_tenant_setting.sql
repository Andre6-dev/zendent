-- PostgreSQL keeps a custom setting as an empty string after a transaction-local value
-- expires. Treat that state like a setting that has never existed so pooled connections
-- continue to fail closed without raising an invalid UUID error.

ALTER POLICY membership_clinic_isolation ON membership
    USING (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid)
    WITH CHECK (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid);

ALTER POLICY refresh_token_clinic_isolation ON refresh_token
    USING (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid)
    WITH CHECK (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid);

ALTER POLICY staff_invitation_clinic_isolation ON staff_invitation
    USING (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid)
    WITH CHECK (clinic_id = NULLIF(current_setting('app.clinic_id', true), '')::uuid);
