-- V1__init.sql
-- Baseline schema (design D4): pgcrypto extension, iam tables (clinic,
-- app_user, role, membership, refresh_token, staff_invitation), seeded
-- role catalog, and the Spring Modulith event-publication registry table
-- (design D6 — Flyway owns this schema; Modulith's own JDBC/JPA schema
-- auto-initialization is disabled in application.yaml).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Tenant root. No clinic_id column: this table IS the tenant, looked up
-- globally by slug before any tenant context exists.
CREATE TABLE clinic (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       varchar(255) NOT NULL,
    slug       varchar(255) NOT NULL UNIQUE,
    status     varchar(50)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- Global identity (email is unique across all clinics). Named app_user
-- because "user" is a reserved word in PostgreSQL.
CREATE TABLE app_user (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    full_name     varchar(255) NOT NULL,
    status        varchar(50)  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

-- Global role catalog (seeded below).
CREATE TABLE role (
    id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(50)  NOT NULL UNIQUE,
    name varchar(100) NOT NULL
);

-- Tenant-scoped user<->clinic<->role join. clinic_id is the Hibernate
-- @TenantId discriminator (design D2) once the iam entities (2.2.1) map
-- this table — every query against this table must resolve through it.
CREATE TABLE membership (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id  uuid NOT NULL REFERENCES clinic (id),
    user_id    uuid NOT NULL REFERENCES app_user (id),
    role_id    uuid NOT NULL REFERENCES role (id),
    status     varchar(50) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_membership_clinic_user UNIQUE (clinic_id, user_id)
);

CREATE INDEX idx_membership_clinic_id ON membership (clinic_id);
CREATE INDEX idx_membership_user_id ON membership (user_id);

-- Revocable refresh-token store (design D3): rotation + reuse-detection
-- lineage via rotated_from.
CREATE TABLE refresh_token (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES app_user (id),
    clinic_id    uuid NOT NULL REFERENCES clinic (id),
    token_hash   varchar(255) NOT NULL UNIQUE,
    jti          varchar(255) NOT NULL,
    issued_at    timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    rotated_from uuid REFERENCES refresh_token (id)
);

CREATE INDEX idx_refresh_token_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);

-- Tenant-scoped staff invitation.
CREATE TABLE staff_invitation (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id   uuid NOT NULL REFERENCES clinic (id),
    email       varchar(255) NOT NULL,
    role_id     uuid NOT NULL REFERENCES role (id),
    token       varchar(255) NOT NULL UNIQUE,
    status      varchar(50) NOT NULL,
    invited_by  uuid NOT NULL REFERENCES app_user (id),
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    accepted_at timestamptz
);

CREATE INDEX idx_staff_invitation_clinic_id ON staff_invitation (clinic_id);
CREATE INDEX idx_staff_invitation_token ON staff_invitation (token);

-- Seed the global role catalog (design D4).
INSERT INTO role (id, code, name) VALUES
    (gen_random_uuid(), 'ADMIN', 'Administrator'),
    (gen_random_uuid(), 'DENTIST', 'Dentist'),
    (gen_random_uuid(), 'STAFF', 'Staff');

-- Spring Modulith event-publication registry (design D6). Schema owned by
-- Flyway: spring.modulith.events.jdbc.schema-initialization.enabled=false
-- in application.yaml. This DDL matches EXACTLY what Hibernate generates
-- for org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication
-- (spring-modulith-events-jpa 2.0.7, table EVENT_PUBLICATION) — verified by
-- capturing a live `ddl-auto=create` boot against Postgres 17 and diffing
-- the resulting `create table` statement. This exact match is required for
-- `spring.jpa.hibernate.ddl-auto=validate` to pass.
--
-- KNOWN LIMITATION (flagged, not silently fixed): serialized_event is
-- varchar(255) because that is Hibernate's default JPA column length for a
-- String property with no explicit @Column(length=...) override on the
-- library's own entity. Real serialized event JSON payloads can exceed 255
-- characters and would fail at insert time with "value too long for
-- varchar(255)". Widening this column (e.g. to text) would make the schema
-- diverge from what Hibernate validates against, so it is NOT changed here.
-- Track this as a follow-up (e.g. a Modulith config/version upgrade, or a
-- later V2 migration once a fix is confirmed compatible with validate).
CREATE TABLE event_publication (
    id                     uuid PRIMARY KEY,
    completion_attempts    integer NOT NULL,
    completion_date        timestamp(6) with time zone,
    last_resubmission_date timestamp(6) with time zone,
    publication_date       timestamp(6) with time zone NOT NULL,
    event_type             varchar(255) NOT NULL,
    listener_id            varchar(255) NOT NULL,
    serialized_event       varchar(255) NOT NULL,
    status                 varchar(255) CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);
