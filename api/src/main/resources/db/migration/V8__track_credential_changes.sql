ALTER TABLE app_user
    ADD COLUMN credentials_changed_at timestamptz;

UPDATE app_user
SET credentials_changed_at = created_at;

ALTER TABLE app_user
    ALTER COLUMN credentials_changed_at SET DEFAULT now(),
    ALTER COLUMN credentials_changed_at SET NOT NULL;
