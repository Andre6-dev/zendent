-- Roles are provisioned outside Flyway because managed PostgreSQL does not
-- grant CREATEROLE to migration users. This migration only grants the runtime
-- privileges needed by the application.
GRANT USAGE ON SCHEMA public TO "${applicationRole}";

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    clinic,
    app_user,
    role,
    membership,
    refresh_token,
    staff_invitation,
    event_publication
TO "${applicationRole}";
