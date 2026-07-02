# Fase 2 — Fundaciones Backend

> Código en inglés. Proyecto backend en carpeta/repo aparte (decidir monorepo vs separado antes de empezar). Ver [architecture.md](./architecture.md).

## Objetivo

Tener un backend Spring Boot 4 arrancable, con Spring Modulith configurado y verificado, multi-tenancy operativa y el módulo `iam` (auth + clínicas) funcional.

---

## Paquetes (PKG)

### PKG-2.1 — Bootstrap & plataforma _(bloquea al resto)_

- Proyecto **Gradle**, **Java 25**, **Spring Boot 4** con: Web, Security, Data JPA, Validation, Springdoc OpenAPI, **Spring Modulith**, Flyway, MapStruct, Testcontainers.
- **Docker Compose** con PostgreSQL; perfiles `local` / `test` / `prod`.
- Flyway base `V1__init.sql` (extensiones, tablas comunes mínimas).
- Test de arquitectura: `ApplicationModules.of(...).verify()` y generación de docs (PlantUML).
- Manejo global de errores con `ProblemDetail` (RFC 7807).
- **DoD:** `./gradlew build` verde con Testcontainers; app levanta contra Postgres del compose; Swagger UI accesible.

### PKG-2.2 — Módulo `iam` (auth + tenant) _(depende de 2.1)_

- Entidades: `Clinic` (tenant), `User`, `Role`, `Membership` (user↔clinic↔role).
- **Auth JWT** con Spring Security: login, refresh, logout; el JWT incluye `clinic_id` y roles.
- **Onboarding de clínica:** endpoint para registrar una `Clinic` + usuario admin (publica `ClinicCreatedEvent`).
- Invitación de staff (stub o básico).
- Endpoints documentados (Springdoc); DTOs + Bean Validation + MapStruct.
- **DoD:** flujo registro→login→endpoint protegido funciona; tests de integración verdes.

### PKG-2.3 — `shared` & tenancy _(en paralelo con 2.2, depende de 2.1)_

- Value objects comunes: `Money`, identificadores, paginación.
- **Infra de tenancy:** filtro Hibernate (`@FilterDef`/`@Filter`) por `clinic_id` + interceptor de Spring Security que activa el filtro con el `clinic_id` del JWT.
- Infra de eventos Modulith (event-publication-registry / outbox) y contratos de eventos del dominio.
- **Tests de aislamiento:** usuario de clínica A no ve datos de clínica B (obligatorio).
- **DoD:** los repositorios quedan automáticamente acotados al tenant; tests de aislamiento verdes.

## Dependencias

```
2.1 ──► 2.2
   └──► 2.3   (2.2 y 2.3 en paralelo tras 2.1)
```

## Verificación

- `docker compose up` (PostgreSQL) + `./gradlew build` con Testcontainers verde.
- `ApplicationModules.verify()` en verde.
- Swagger UI accesible; smoke test registro/login/tenant isolation.
