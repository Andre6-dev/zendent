-- An invitation grants access to a Clinic's health data, so the database must
-- not hold anything that can be presented as one. Only the hash is kept, as
-- refresh_token already does; the plaintext exists once, in the response to the
-- administrator who issued it.
ALTER TABLE staff_invitation RENAME COLUMN token TO token_hash;
ALTER INDEX idx_staff_invitation_token RENAME TO idx_staff_invitation_token_hash;
