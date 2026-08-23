ALTER TABLE password_reset_token
    ADD COLUMN used_at timestamptz;
