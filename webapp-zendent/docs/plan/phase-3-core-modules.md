# Fase 3 — Módulos Núcleo

> Código en inglés. Depende de Fase 2. Cada paquete = 1 módulo backend + 1 feature frontend que lo consume. Paralelizables entre sí (un agente por módulo). Ver [architecture.md](./architecture.md) y [multiagent-workflow.md](./multiagent-workflow.md).

## Objetivo

Implementar los módulos clínicos y conectar el frontend a la API real (sustituyendo los mocks de Fase 1).

---

## PKG-3.1 — Staff

- **Backend (`staff`):** `Dentist`, `StaffMember`, `WorkingHours`; CRUD, especialidades, horarios; expone lista de dentistas (consumida por reservations).
- **Frontend:** pantalla **Staff List** (tabla + alta/edición + horarios). Distingue rol clínica vs consultorio.
- **DoD:** CRUD funcional E2E; tests; `verify()` verde.

## PKG-3.2 — Patients (odontograma)

- **Backend (`patients`):** `Patient`, `MedicalRecord`, `ToothCondition`; ficha, hábitos de higiene, historial; modelo de **odontograma** (diente + condición + tratamiento + dentista + estado).
- **Frontend:** lista + detalle con tabs (_Patient Information / Appointment History / Next Treatment / Medical Record_); **odontograma interactivo** (selección de dientes, condiciones, timeline, toggle Medical/Cosmetic); crear/editar.
- **DoD:** odontograma muestra y registra condiciones desde la API; tests.

## PKG-3.3 — Treatments

- **Backend (`treatments`):** `Treatment`, `TreatmentVisit`, `TreatmentComponent`; catálogo, visitas, componentes/costos. Publica `TreatmentPlannedEvent`.
- **Frontend:** catálogo + wizard **"Add a treatment"** (Basic info → Multiple visits → Components → Treatment plan → Summary → Pay Bill). TanStack Form + Zod.
- **DoD:** crear tratamiento con visitas y componentes E2E; evento publicado; tests.

## PKG-3.4 — Reservations (API real)

- **Backend (`reservations`):** `Appointment` (estados REGISTERED/ENCOUNTER/FINISHED/WAITING_PAYMENT), `WaitlistEntry`. Publica `AppointmentFinishedEvent`; escucha `InvoicePaidEvent`.
- **Frontend:** **sustituir el `queryFn` mock de Fase 1 por el cliente HTTP** (sin reescribir componentes); modal de **nueva cita** funcional; **waitlist wizard** (3 pasos).
- **DoD:** calendario muestra citas reales; crear cita persiste; transiciones de estado funcionan; tests de aislamiento por tenant.

## Dependencias

- Todos dependen de Fase 2.
- `reservations` (3.4) consume `staff` (3.1), `patients` (3.2) y `treatments` (3.3) → idealmente 3.1–3.3 antes de cerrar 3.4, pero pueden avanzar en paralelo con contratos (OpenAPI) acordados primero.

## Verificación

- Por módulo: `./gradlew build` + Testcontainers verde, `verify()` verde, smoke test de endpoints, tests de aislamiento de tenant.
- Frontend: la pantalla correspondiente opera contra la API real; `bun run build`/lint verdes.
