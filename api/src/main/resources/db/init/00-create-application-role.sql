-- Local and test environments only. Production provisions the equivalent
-- role in the managed-PostgreSQL console with an environment-owned password.
CREATE ROLE zendent_app
    LOGIN
    PASSWORD 'zendent_app'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    NOBYPASSRLS;
